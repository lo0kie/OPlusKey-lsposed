#!/usr/bin/env python3
r"""棘轮判据 —— 把「已经改好的规模指标」变成**只许降**的硬闸。

## 它补的是哪一类洞

`audit-coupling.py` 的「单文件 > 1500 行」只是**警告**，`audit-arch.py` 的规模那几行只当**趋势线**，
detekt 的存量靠 `baseline.xml` 兜着 —— 三者都不会因为「又长了一点」而失败。
于是同类问题反复发生：`docs/architecture/decoupling.md` 零节在 2026-10-28 记的是
「全仓无 > 1000 行、`SettingsPage` 24 个状态字段、368 个页面扩展」，两周后实测变成
「4 个千行文件、38 个字段、516 个扩展」—— 每一项单次都只是「加一行」，合起来是判据 3 / 4 整体失效。

所以这里不设「绝对值达标线」（那会把存量一次性判红），而是把**今天**记成上限：
再长就红，改好之后 `--tighten` 自动收紧。**上限只许降** —— 放宽要手改 `ratchet.txt` 并写理由。

## 判据（逐项与 `ratchet.txt` 同名）

1. `count.files_over_1000` + `lines.<相对路径>`：千行文件个数，以及**每个**千行文件的行数上限。
   逐个记住行数是为了防「A 拆出去、B 涨回来」这种此消彼长。
2. `count.settings_page_fields`：`ui/SettingsPage.kt` 里 `internal/private val|var` 的条数。
   口径与 `decoupling.md` 零节那条 grep 逐字相同，所以历史上那个 24 能直接对比。
3. `count.settings_page_ext`：`fun/val/var SettingsPage.*` 的总数，口径与 `audit-arch.py` 相同
   （去掉注释后按多行正则计数）。
4. `count.detekt_baseline`：`config/detekt/baseline.xml` 里 `<ID>` 的条数 ——
   AGENTS.md 二、1.2 写着「新代码不许再往基线里加条目」，这句话以前没有闸。

用法（仓库根目录）：

    python3 tools/audit-ratchet.py            # 校验，超限 exit 1
    python3 tools/audit-ratchet.py --tighten  # 只收紧：把上限改成实测值（不会放宽任何一项）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass

REPO = Path(__file__).resolve().parent.parent
APP = REPO / "app/src/main/java/com/lookie/opluskey"
SETTINGS_PAGE = APP / "ui/SettingsPage.kt"
BASELINE = REPO / "config/detekt/baseline.xml"
RATCHET = REPO / "tools/ratchet.txt"

# 千行的「千」—— 与 audit-coupling 的 1500 那条不同：这里是判据 4 的原值（structure.md 四、判据 4）
BIG_FILE = 1000

FIELD_RE = re.compile(r"^    (?:internal )?(?:private )?(?:val|var) ", re.M)
EXT_RE = re.compile(r"^(?:internal |private )?(?:fun|val|var) SettingsPage\.", re.M)
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")
MAX_HINT = 200


def rel(path: Path) -> str:
    return path.relative_to(REPO).as_posix()


def sources() -> list[Path]:
    return sorted(list((REPO / "app/src").rglob("*.kt")) + list((REPO / "core/src").rglob("*.kt")))


def code_only(path: Path) -> str:
    text = path.read_text(encoding="utf-8", errors="replace")
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def measure() -> dict[str, int]:
    """实测值 —— 键与 `ratchet.txt` 里的键一一对应。"""
    out: dict[str, int] = {}
    big: list[Path] = []
    for path in sources():
        n = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
        if n > BIG_FILE:
            big.append(path)
            out[f"lines.{rel(path)}"] = n
    out["count.files_over_1000"] = len(big)

    out["count.settings_page_fields"] = len(FIELD_RE.findall(code_only(SETTINGS_PAGE)))
    ext = 0
    for path in APP.rglob("*.kt"):
        ext += len(EXT_RE.findall(code_only(path)))
    out["count.settings_page_ext"] = ext

    out["count.detekt_baseline"] = BASELINE.read_text(encoding="utf-8", errors="replace").count("<ID>")
    return out


def read_ratchet() -> tuple[list[str], dict[str, int]]:
    """返回（原始行, 上限表）。注释与空行原样留着，`--tighten` 改写时不能丢理由。"""
    lines = RATCHET.read_text(encoding="utf-8").splitlines()
    limits: dict[str, int] = {}
    for line in lines:
        body = line.split("#", 1)[0].strip()
        if not body or "=" not in body:
            continue
        key, _, value = body.partition("=")
        key = key.strip()
        value = value.strip()
        if not value.lstrip("-").isdigit():
            sys.exit(f"棘轮上限表：{key} 的值不是整数（{value}）")
        limits[key] = int(value)
    return lines, limits


def main(argv: list[str]) -> int:
    tighten = "--tighten" in argv
    for path in (SETTINGS_PAGE, BASELINE, RATCHET):
        if not path.is_file():
            # 路径写错 / 文件改名时**报错退出**，不静默当成 0 —— 那种「全绿」是假绿
            sys.exit(f"棘轮判据：文件不存在：{path}")

    lines, limits = read_ratchet()
    now = measure()
    if not now:
        sys.exit("棘轮判据：一个文件都没扫到（目录结构变了？）")

    over: list[str] = []
    rows: list[tuple[str, int, int | None]] = []
    for key in sorted(set(limits) | set(now)):
        seen, limit = now.get(key, 0), limits.get(key)
        rows.append((key, seen, limit))
        if limit is None:
            # 表里没有的新条目：只在它确实是「新冒出来的千行文件」时算超限 ——
            # 千行文件的集合本身也是判据，不能靠不登记就绕过去
            if key.startswith("lines."):
                over.append(f"{key}（新冒出来的千行文件，{seen} 行）")
            continue
        if seen > limit:
            over.append(f"{key}：{seen} > 上限 {limit}")
        elif key.startswith("lines.") and seen < limit:
            rows[-1] = (key, seen, limit)

    width = max(len(r[0]) for r in rows)
    print("棘轮判据（实测 / 上限）：")
    for key, seen, limit in rows:
        mark = "" if limit is None or seen <= limit else "  <- 超限"
        show = "未登记" if limit is None else str(limit)
        print(f"  {key.ljust(width)}  {seen:>6} / {show:>6}{mark}")

    if over:
        print("\n[超限] 规模指标比上限更差：")
        for line in over:
            print(f"  - {line}")
        print("\n  改回原样，或确实该放宽时手改 tools/ratchet.txt 并在提交信息里说明理由。")
        return 1

    print("\n[达标] 所有规模指标都没超过上限。")

    if tighten:
        # **只收紧**：实测比上限小就改成实测值；实测更大时前面已经 exit 1，走不到这里
        out: list[str] = []
        for line in lines:
            body = line.split("#", 1)[0]
            if "=" in body:
                key = body.split("=", 1)[0].strip()
                if key in now and now[key] < limits.get(key, now[key]):
                    line = f"{key} = {now[key]}"
            out.append(line)
        # 已经掉到 1000 行以下的文件：把它的行数上限删掉（留着等于给将来留个口子）
        out = [
            line
            for line in out
            if not (
                line.strip().startswith("lines.")
                and line.split("=", 1)[0].strip() not in now
            )
        ]
        RATCHET.write_text("\n".join(out) + "\n", encoding="utf-8")
        print("已收紧上限（没有放宽任何一项）。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
