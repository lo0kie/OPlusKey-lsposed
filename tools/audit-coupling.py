#!/usr/bin/env python3
r"""耦合审计 —— 把 docs/decoupling.md 的判据做成可跑的脚本（第七节）。

只依赖标准库；CI 与本地都直接 ``python3 tools/audit-coupling.py``。

做四件事：

1. **包级导入图**：包 = ``com/lookie/opluskey`` 下的相对目录（``ui/component`` 这类两级也算一档），
   对 ``com.lookie.opluskey.*`` 的 import 记一条边；androidx / COUI / 图标库记成「外部依赖」。
2. **禁止边**：按 docs/decoupling.md 五 的清单逐条比，越界即失败。
   目前三条**已知**的越界挂在 [WHITELIST] 上（都是「通用组件反过来认识具体屏」），
   按七的原则白名单只许缩短、不许把新的越界当既成事实抄进来。
3. **环检测**：包级图上 DFS，有环即失败并给出成环的那几条边。
4. **单元规模**：单文件 > 1500 行只是**警告**（不阻断，先看趋势）。

退出码：0 = 通过（警告不算失败）；1 = 有越界边或环。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
# 两个 Gradle 模块、每个模块的三个源集都要扫：
#   · `:app`  —— 界面 / 宿主侧 / 注入侧（debug / release 里各有一份 ui 的变体副本）；
#   · `:core` —— 两个进程共用的那一层（debug / beta / release 里各有一份 BuildFlags 与
#     HostFilePerms 的副本，同样在注入侧执行）。
#   注意：`:core` 自成一个**编译单元**之后，「它不许引 `ui/`」已经是编译期约束（它的 classpath
#   里没有 `:app`）；这里再扫一遍是因为这条脚本还管着「包级环」与「注入侧禁引 androidx」，
#   而那两条编译期管不了。
SOURCE_ROOTS = [
    REPO / "app/src/main/java/com/lookie/opluskey",
    REPO / "app/src/debug/java/com/lookie/opluskey",
    REPO / "app/src/beta/java/com/lookie/opluskey",
    REPO / "app/src/release/java/com/lookie/opluskey",
    REPO / "core/src/main/java/com/lookie/opluskey",
    REPO / "core/src/debug/java/com/lookie/opluskey",
    REPO / "core/src/beta/java/com/lookie/opluskey",
    REPO / "core/src/release/java/com/lookie/opluskey",
]
PACKAGE_ROOT = "com.lookie.opluskey"
IMPORT_RE = re.compile(r"^import\s+", re.M)

# 依赖分两档：本项目内部、界面库（在 system_server 里加载会崩的那几样）。其它不看。
EXTERNAL_BANNED = re.compile(r"^(androidx\.|io\.github\.suqi8|io\.github\.lyxnx|com\.composables)")

# 会越界的源档位与其禁引清单（docs/decoupling.md 五）。
INJECTION_SIDE_BANNED = (
    r"^(androidx\.|io\.github\.suqi8|io\.github\.lyxnx|com\.composables)",
    r"^com\.lookie\.opluskey\.ui\.",
    r"^com\.lookie\.opluskey\.host\.",
    r"^com\.lookie\.opluskey\.core\.config",
)

RULES: list[tuple[str, tuple[str, ...], str]] = [
    # inject/ 与 bridge/ 都会在 system_server 里被加载，同一份黑名单。
    ("inject", INJECTION_SIDE_BANNED, "注入侧不引界面库 / 界面层 / 宿主侧 / 落盘包（一、1 · CI 5 · decoupling 动作 3）"),
    ("bridge", INJECTION_SIDE_BANNED, "bridge/ 是本层共用（两侧都会加载），与 inject/ 同一待遇（一、1 · CI 5）"),
    ("bridge.inject", INJECTION_SIDE_BANNED, "bridge/inject/ 只在 system_server 跑，同 inject/（一、1）"),
    (
        "bridge.host",
        (r"^com\.lookie\.opluskey\.inject\.", r"^com\.lookie\.opluskey\.ui\.", r"^(androidx\.|io\.github\.suqi8|io\.github\.lyxnx|com\.composables)"),
        "bridge/host/ 只在宿主进程跑：不引注入侧 / 界面层 / 界面库（与 host/ 同待遇）",
    ),
    (
        "host",
        (r"^com\.lookie\.opluskey\.inject\.", r"^com\.lookie\.opluskey\.ui\."),
        "宿主不依赖注入侧与界面层（decoupling 动作 1、2）",
    ),
    ("ui.component", (r"^com\.lookie\.opluskey\.ui\.screen",), "通用组件不许依赖具体屏（decoupling 动作 4）"),
    ("ui.state", (r"^com\.lookie\.opluskey\.ui\.screen",), "状态不许反过来认识屏（decoupling 动作 5）"),
    ("core", (r"^com\.lookie\.opluskey\.(inject|bridge|host|ui)\.",), "core 是最底层，不引任何上层包"),
    # KeyModule 在注入侧被加载，但它**允许** import ui/（界面 hook 只在设置页进程分支里装，
    # 见它的类注释）—— 所以这里只拦「一加载就崩」的那几样，与 CI 的 (5)' 同口径。
    ("root", (r"^(androidx\.|io\.github\.suqi8|io\.github\.lyxnx|com\.composables)",), "KeyModule 不引界面库（CI 5'）"),
]

# 已知的越界边 —— **白名单只许缩短**（docs/decoupling.md 七）。
# 每条都对应一笔明确的欠账；修掉一条就删一行，不许把新的越界当既成事实抄进来。
#
# 2026-10-17：原先挂的三条已全部清掉 —— `component/{Haptics, PageHeader, TileWidgets}`
#   那三处反向 import 其实**都是死 import**（PageHeader 里那两处 `[HomeScreen]` 写在 `//`
#   行注释里，KDoc 根本不解析），死代码删掉即可，不必搬文件。
#
# 「审计项 2：component/ 名不副实」的**实体部分**同样是脚本看不见的那一档（包级图上没有边，
#   但目录语义错了），只能靠人记 —— 2026-10-17 已按「只服务一屏的跟着那一屏走」清完：
#   - `PageHeader.kt` 里那两块业务菜单搬去 `ui/screen/{home,apppicker}/`，顶栏改成 `actions` 槽位
#     （`HeaderMenu` 枚举随之删掉，「顶栏不认识页面」现在是代码事实）；
#   - `CommandMenus` / `IconInput` / `DonateDialog` 同上；`MenuPopup` 留 `component/`
#     （它服务五个屏，是真正的共用件）；
#   - `SwitchRow.kt` 那个没用过的接收者已去掉。
#   判据留在这里备用：**`ui/component/` 里只许放跨屏共用件** —— 只服务一屏的，跟着那一屏走。
WHITELIST: set[tuple[str, str]] = set()


def bucket_of(rel: str) -> str:
    """一个文件在包结构里的**细分档位** —— 禁止边按它判。"""
    if rel == "KeyModule.kt":
        return "root"
    parts = rel.split("/")
    # bridge/ 按**进程归属**再分两档（2026-10-17 拆的）：`bridge/` 是本层共用的平台层
    # （两侧都会加载 ⇒ 与 inject/ 同待遇），`bridge/host/` 只有宿主侧用、`bridge/inject/` 只有
    # 注入侧用 —— 拿错档位判「这段会不会在 system_server 里跑」就会判错。
    if parts[0] == "bridge" and len(parts) >= 2 and parts[1] in ("host", "inject"):
        return "bridge." + parts[1]
    if parts[0] == "ui" and len(parts) >= 2 and parts[1] in ("component", "state", "screen"):
        return "ui." + parts[1]
    return parts[0]


def layer_of(bucket: str) -> str:
    """细分档位 → **分层** —— 环检测按它判。

    `ui/` 的四个档位（`ui` / `ui.component` / `ui.state` / `ui.screen`）折成同一层：
    `SettingsPage` 是个 **DSL 接收者**（页面构件写成它的扩展函数，与 Compose 的
    `ColumnScope` 同一种做法），所以「`SettingsPage.kt` 用构件」与「构件是它的扩展」
    本来就互相 import —— 那是这套 UI 写法的固有形状，不是耦合缺陷。

    同理 `bridge/` 的三个档位（`bridge` / `bridge.host` / `bridge.inject`）也折成一层：
    它们互相调用是正常的（分开是为了**说明进程归属**，不是为了禁止互相引用）。

    于是两类判据**用两种粒度**（docs/decoupling.md 五 的设计也一样）：
      - **禁止边**看细分档位：`ui/component` 不许认识 `ui/screen`，`bridge/host` 不许认识 `ui`；
      - **环检测**看分层：`ui/` 内部、`bridge/` 内部互相引用不算环，跨层（`host → inject` 之类）才算。
    """
    if bucket.startswith("ui") or bucket.startswith("bridge"):
        return bucket.split(".")[0]
    return bucket


def bucket_of_target(target: str) -> str:
    """import 的目标 FQN → 细分档位。``com.lookie.opluskey.core.model.KeyId`` → ``core``。"""
    return bucket_of(target.removeprefix(PACKAGE_ROOT + ".").replace(".", "/") + ".kt")


def source_files() -> list[Path]:
    out: list[Path] = []
    for root in SOURCE_ROOTS:
        if root.is_dir():
            out.extend(sorted(root.rglob("*.kt")))
    return out


def rel_of(path: Path) -> str:
    for root in SOURCE_ROOTS:
        if path.is_relative_to(root):
            return path.relative_to(root).as_posix()
    raise AssertionError(path)


def find_cycle(graph: dict[str, set[str]]) -> list[str] | None:
    """DFS 找一条包级环；返回 ``[a, b, …, a]``，无环返回 None。"""
    state: dict[str, int] = {n: 0 for n in graph}  # 0 未访 1 在栈上 2 已定
    parent: dict[str, str | None] = {}

    def visit(u: str) -> list[str] | None:
        state[u] = 1
        for v in sorted(graph.get(u, ())):
            if state.get(v, 0) == 1:
                loop = [u]
                while loop[-1] != v:
                    loop.append(parent[loop[-1]] or "")
                return list(reversed(loop)) + [v]
            if state.get(v, 0) == 0:
                parent[v] = u
                found = visit(v)
                if found:
                    return found
        state[u] = 2
        return None

    for node in sorted(graph):
        if state.get(node, 0) == 0:
            parent[node] = None
            found = visit(node)
            if found:
                return found
    return None


def main() -> int:
    files = source_files()
    if not files:
        print(f"找不到源码：{[str(p) for p in SOURCE_ROOTS]}", file=sys.stderr)
        return 1

    violations: list[str] = []
    edges: dict[tuple[str, str], list[str]] = {}
    oversized: list[str] = []

    for path in files:
        rel = rel_of(path)
        src = path.read_text(encoding="utf-8", errors="replace")
        here = bucket_of(rel)

        if len(src.splitlines()) > 1500:
            oversized.append(f"  {rel}（{len(src.splitlines())} 行）")

        for match in IMPORT_RE.finditer(src):
            line_no = src.count("\n", 0, match.start()) + 1
            target = src[match.end() :].split("\n", 1)[0].strip()
            if target.startswith(PACKAGE_ROOT + "."):
                edges.setdefault((here, target), []).append(f"{rel}:{line_no}")
            elif EXTERNAL_BANNED.match(target):
                edges.setdefault((here, target), []).append(f"{rel}:{line_no}")

            for src_bucket, banned, reason in RULES:
                if here != src_bucket:
                    continue
                if any(re.search(pat, target) for pat in banned):
                    if (rel, target) in WHITELIST:
                        continue
                    violations.append(f"{rel}:{line_no}  {here} → {target}\n    理由：{reason}")

    # ---- 环检测（在**分层**图上做；见 [layer_of] 为什么不用细分档位） ----
    graph: dict[str, set[str]] = {}
    for src_bucket, target in edges:
        if not target.startswith(PACKAGE_ROOT + "."):
            continue
        src_layer, dst_layer = layer_of(src_bucket), layer_of(bucket_of_target(target))
        if dst_layer != src_layer:
            graph.setdefault(src_layer, set()).add(dst_layer)

    cycle = find_cycle(graph)
    if cycle:
        pairs = list(zip(cycle, cycle[1:]))
        hits = [
            f"  {f}"
            for (src_bucket, target), fs in sorted(edges.items())
            if target.startswith(PACKAGE_ROOT + ".")
            for pair in pairs
            if layer_of(src_bucket) == pair[0] and layer_of(bucket_of_target(target)) == pair[1]
            for f in fs
        ][:8]
        violations.append(
            "包级依赖成环：" + " → ".join(cycle) + ("\n" + "\n".join(hits) if hits else "")
        )

    # ---- 报告 ----
    print(f"扫了 {len(files)} 个 Kotlin 文件")
    if violations:
        print(f"\n✗ 越界 {len(violations)} 条：")
        for v in violations:
            print(f"  {v}")
    else:
        print("✓ 禁止边零越界，包级依赖无环")

    if oversized:
        print(f"\n⚠ 超过 1500 行的文件 {len(oversized)} 个（只警告，不阻断）：")
        for line in oversized:
            print(line)

    if WHITELIST:
        print(f"\n白名单里还挂着 {len(WHITELIST)} 条已知欠账（只许缩短）：")
        for rel, target in sorted(WHITELIST):
            print(f"  {rel} → {target}")

    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
