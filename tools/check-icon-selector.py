#!/usr/bin/env python3
r"""图标选择器审计 —— 源码里用到的字形，必须都能在选择器里点到。

## 为什么要有这一份

`FluidIcons.ALL` 是胶囊图标选择器的唯一清单：用户能点到的就是它列出来的那些。加一颗字形
是**两件事** —— 在别处引用它（编译期成立），以及往 `ALL` 里插一条（运行期才可见）。
只做前一半时编译照过、界面照跑，只是选择器里少一颗；这种漏法 `spotless` / `detekt` /
`lint` 一道都拦不住，它们都不看这份清单。

反向的漏法同样存在：`DrawnIcons` 里画好了一颗、忘了往 `ALL` 里加，那颗字形就只活在代码里，
谁也点不到（连「有这个图标」都看不出来）。

## 判据（五条，任一不满足即 exit 1）

1. 源码里出现的 `TablerIcons.Outline.X` / `TablerIcons.Filled.X`（含 `import` 之后用短名
   引用的那种写法）都能在 `ALL` 里找到**同一个字形**；
2. 源码里出现的 `DrawnIcons.X` 都能在 `ALL` 里找到；
3. `DrawnIcons` 里声明的每一颗字形都进了 `ALL`（哪怕没有任何地方引用它）；
4. `ALL` 的 id 不重复（重复的 id 会让后一颗永远点不到，`LazyVerticalGrid` 的 key 也会撞）；
5. `ALL` 每一条的右值都是认得的三种写法之一（`TablerIcons.Outline.*` / `TablerIcons.Filled.*`
   / `DrawnIcons.*`）—— 认不出的写法会被静默跳过，等于漏判。

比的是**字形**而不是 id：id 的拼法（小写、filled 加 `-filled` 后缀）是 `FluidIcons` 自己的
约定，这里只认「同一个字形在不在清单里」，改 id 不会让这道闸红。

用法（仓库根目录）：`python3 tools/check-icon-selector.py`
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
ICON_DIR = REPO / "app/src/main/java/com/lookie/opluskey/ui/model/icon"
FLUID = ICON_DIR / "FluidIcons.kt"
DRAWN = ICON_DIR / "DrawnIcons.kt"
SCAN_ROOTS = (REPO / "app/src", REPO / "core/src")

# `"flashlight" to DrawnIcons.Flashlight,` —— 清单里的一条
ENTRY = re.compile(r'"([^"]+)"\s+to\s+([A-Za-z_][\w.]*)')
# 合格引用：`TablerIcons.Outline.X` / `TablerIcons.Filled.X`
QUALIFIED = re.compile(r"\bTablerIcons\.(Outline|Filled)\.([A-Za-z_]\w*)")
# `import io.github.lyxnx.compose.ui.tablericons.outline.X` —— 短名引用的那一半
IMPORT = re.compile(
    r"^\s*import\s+io\.github\.lyxnx\.compose\.ui\.tablericons\.(outline|filled)\.([A-Za-z_]\w*)\s*$",
    re.M,
)
DRAWN_REF = re.compile(r"\bDrawnIcons\.([A-Za-z_]\w*)")
# `DrawnIcons` 里的字形声明（对象体内 4 空格缩进那一档；`private` 的是内部零件，不算字形）
DRAWN_DECL = re.compile(r"^\s{4}(?:internal\s+)?val\s+([A-Za-z_]\w*)\s*:\s*ImageVector", re.M)

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    """去掉注释 —— 注释里提到某个字形不算「用到」（那份类注释就成段地写着字形名）。"""
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def glyph_of(rhs: str) -> str | None:
    """右值 → 字形键。认不出返回 `None`（由判据 5 报出来）。"""
    parts = rhs.split(".")
    if len(parts) == 3 and parts[0] == "TablerIcons" and parts[1] in ("Outline", "Filled"):
        return f"tabler-{parts[1].lower()}:{parts[2]}"
    if len(parts) == 2 and parts[0] == "DrawnIcons":
        return f"drawn:{parts[1]}"
    return None


def line_of(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def rel(path: Path) -> str:
    return path.relative_to(REPO).as_posix()


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def collect_selector(problems: list[str]) -> tuple[set[str], int]:
    """`FluidIcons.ALL` → 字形键集合 + 条目数。"""
    text = read(FLUID)
    start = text.find("val ALL")
    if start < 0:
        problems.append(f"{rel(FLUID)} 里找不到 `val ALL` —— 清单被改名了？")
        return set(), 0
    # 清单在 `        )` 那一行收尾（缩进对齐 `listOf(` 的右括号）
    end = text.find("\n        )", start)
    if end < 0:
        problems.append(f"{rel(FLUID)} 的 `ALL` 找不到收尾的 `)`")
        return set(), 0

    block = text[start:end]
    base = line_of(text, start)
    keys: set[str] = set()
    ids: dict[str, int] = {}
    for m in ENTRY.finditer(block):
        icon_id, rhs = m.group(1), m.group(2)
        line = base + line_of(block, m.start()) - 1
        key = glyph_of(rhs)
        if key is None:
            problems.append(f"{rel(FLUID)}:{line} 认不出这一条：{rhs}（判据 5）")
            continue
        if icon_id in ids:
            problems.append(
                f"{rel(FLUID)}:{line} id 重复：\"{icon_id}\"（第 {ids[icon_id]} 行已经用过，判据 4）"
            )
        ids[icon_id] = line
        keys.add(key)
    return keys, len(ids)


def collect_drawn_decls(problems: list[str]) -> dict[str, int]:
    """`DrawnIcons` 里声明的字形 → 行号。"""
    text = read(DRAWN)
    stripped = strip_comments(text)
    return {m.group(1): line_of(text, m.start()) for m in DRAWN_DECL.finditer(stripped)}


def collect_usages() -> dict[str, list[str]]:
    """源码里用到的字形 → `文件:行` 列表。"""
    used: dict[str, list[str]] = {}
    for root in SCAN_ROOTS:
        for path in sorted(root.rglob("*.kt")):
            text = strip_comments(read(path))
            where = rel(path)

            def hit(key: str, line: int) -> None:
                used.setdefault(key, []).append(f"{where}:{line}")

            for m in QUALIFIED.finditer(text):
                hit(f"tabler-{m.group(1).lower()}:{m.group(2)}", line_of(text, m.start()))

            # 短名引用：`import ...outline.X` 之后正文里写 `X`。两处不算：
            # import 行本身（否则一个没人用的 import 也会被当成「用到了」），
            # 以及 `某枚举.Repeat` 这种同名成员访问（前面那个 `.` 说明它指的是别人的字段）。
            body = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith("import "))
            for m in IMPORT.finditer(text):
                name = m.group(2)
                if re.search(rf"(?<![\w.]){name}\b", body):
                    hit(f"tabler-{m.group(1)}:{name}", line_of(text, m.start()))

            for m in DRAWN_REF.finditer(text):
                hit(f"drawn:{m.group(1)}", line_of(text, m.start()))
    return used


def describe(key: str) -> str:
    kind, _, name = key.partition(":")
    if kind == "drawn":
        return f"DrawnIcons.{name}"
    side = "Outline" if kind == "tabler-outline" else "Filled"
    return f"TablerIcons.{side}.{name}"


def main() -> int:
    for path in (FLUID, DRAWN):
        if not path.is_file():
            # 路径写错 / 文件改名时**报错退出**：静默当成空集合会让五条判据全部「通过」
            sys.exit(f"图标选择器审计：文件不存在：{path}")

    problems: list[str] = []
    selected, count = collect_selector(problems)
    decls = collect_drawn_decls(problems)
    used = collect_usages()

    # 判据 1 / 2：用到的都得在清单里
    for key in sorted(used):
        if key not in selected:
            places = "、".join(sorted(set(used[key]))[:3])
            more = "" if len(set(used[key])) <= 3 else f" 等 {len(set(used[key]))} 处"
            problems.append(f"{describe(key)} 没进选择器（{places}{more}，判据 1/2）")

    # 判据 3：画好的也得在清单里
    for name, line in sorted(decls.items()):
        if f"drawn:{name}" not in selected:
            problems.append(f"DrawnIcons.{name} 画好了没进选择器（{rel(DRAWN)}:{line}，判据 3）")

    tabler_used = sum(1 for k in used if k.startswith("tabler-"))
    print(f"选择器 {count} 颗；源码用到 Tabler {tabler_used} 种、手绘 {len(decls)} 颗")
    if problems:
        print(f"\n[不达标] {len(problems)} 条：")
        for line in problems:
            print(f"  - {line}")
        return 1
    print("\n[达标] 源码里用到的字形与画好的字形，全都在选择器里。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
