#!/usr/bin/env python3
r"""静态表生成器 —— 两张**生成物**：`Strings.kt` 与 `CommonCommandsTable.kt`。

## 两张表、两个源

| 生成物 | 源 | 加一条时要改哪里 |
| --- | --- | --- |
| `core/i18n/Strings.kt` | `app/src/main/assets/locales/zh-CN.json` 的键集合 | 两份字典各加一行 |
| `core/present/CommonCommandsTable.kt` | `core/tables/common-commands.json` | 那张表里加一项 |

`Strings` 是**键名常量**：`zh-CN.json` 里有一个键，这里就有一条 `const val`。改动之前这两份
靠 `tools/check-i18n-keys.py` 事后对账 —— 删一条文案要记得同时删常量与两份字典（本项目就在
「删了常量、忘删字典」上踩过一次）。生成之后这件事变成单向：字典是源，常量是投影。

## 为什么是「生成」而不是「运行时读 JSON」

`core` 会被注入侧（`system_server`）加载。那边读 asset 没有零依赖的路：`DictionaryLoader`
的两条路一条要类加载器的资源、一条要 `PackageManager` 打开本包 APK 当 zip 读。给一张**只要
编译期常量**的表付这份运行时开销、再加一条「读不到就少一半键名」的故障面，不划算。生成物把这些
全部留在编译期：键名仍是常量，写错一个名字当场编译失败。

## 保留什么、覆盖什么

- **键集合与顺序**：字典那边说了算（`zh-CN.json` 的顺序就是给人看的顺序）。
- **常量名**：已在文件里的名字**原样保留**，不重新按键推导 —— 有 41 条历史名字与键不同形
  （`key_settings.click2` → `SETTING_CLICK2`），重新推导等于把调用点全带倒。新键才推导。
- **每条常量上方的注释**：原样带走。注释是这个仓库里最贵的部分，生成器既不删也不造。
- **其余**（对象头、文件末尾）由这里产出。

于是「手工改这个文件」只在一种情况下有意义：**给某条常量补一句注释**，它会被原样保留下来。
改常量名 / 改取值 / 调顺序都会被下一次生成覆盖。

## 用法

    python3 tools/gen-static-tables.py            # 写盘
    python3 tools/gen-static-tables.py --check    # 只比对（CI 用），不一致 exit 1
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass

REPO = Path(__file__).resolve().parent.parent
STRINGS = REPO / "core/src/main/java/com/lookie/opluskey/core/i18n/Strings.kt"
ZH = REPO / "app/src/main/assets/locales/zh-CN.json"
EN = REPO / "app/src/main/assets/locales/en.json"
COMMANDS_SRC = REPO / "core/tables/common-commands.json"
COMMANDS_OUT = REPO / "core/src/main/java/com/lookie/opluskey/core/present/CommonCommandsTable.kt"

CONST = re.compile(r'^    const val (\w+) = "([^"]+)"$')

STRINGS_HEADER = """package com.lookie.opluskey.core.i18n

/**
 * 多语言文案的键常量 —— **生成物，别手改常量表**。
 *
 *  源是 `app/src/main/assets/locales/zh-CN.json`：那边有一个键，这里就有一条常量；键的顺序就是
 *  这个文件的顺序。生成器是 `tools/gen-static-tables.py`（CI 跑的是它的 `--check`，重跑一遍
 *  看工作区有没有变）。加一条文案 = 两份字典各加一行，再跑一次生成器。
 *
 *  ## 能手工改的只有注释
 *
 *  每条常量上方的说明会被生成器**原样带走**，所以补一句「这一条说的是哪一格」是安全的；
 *  改名字、改取值、调顺序都会被下一次生成覆盖 —— 那些属于字典或这里推导出来的那一半。
 *
 *  ## 为什么整块不拆
 *
 *  `LargeClass` 是有意压住的：它是一张「键名 → 字典条目」的表，没有任何行为，行数只会随页面
 *  数量单调长。拆成几个对象（`Strings.Keys` 那种）能把行数分散掉，代价是每个调用点多一层前缀、
 *  而键名字符串的守卫要靠人记 —— 这层前缀不对应任何职责。真正该拆的信号是「这里开始出现逻辑」，
 *  那时单开文件，而不是给常量换个门牌。
 */
@Suppress("LargeClass")
object Strings {
"""

COMMANDS_HEADER = """package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action

/**
 * 「常用命令」那张表 —— **生成物，改 `core/tables/common-commands.json`**。
 *
 *  生成器是 `tools/gen-static-tables.py`。每一项的字段含义见 [CommonCommands.Entry]；
 *  `name` / `summary` 是**字典键**，这里已经翻成 `Strings.X` 常量 —— 键写错当场编译失败，
 *  不会退成界面上那一行显示键名的样子。
 *
 *  兜底文案（`nameFallback` / `summaryFallback`）留在表里是对的：这两个键是**动态键**（存在
 *  表里、编译器看不见它有没有进字典），字典读不到时（宿主主进程那条路见
 *  `DictionaryLoader`）只有它能把这行念出来。
 */
internal val COMMON_COMMAND_ENTRIES: List<CommonCommands.Entry> =
    listOf(
"""

# 动作前缀 → `Action` 里那个常量名。表里写的是**完整 token**，生成时把前缀换成常量，
#  后一半留字面量 —— 这样 SHELL_PREFIX 改了口径，这张表不用跟着改。
PREFIXES = {
    "shell:": "SHELL_PREFIX",
    "keyevent:": "KEYEVENT_PREFIX",
    "intent:": "INTENT_PREFIX",
    "notify:": "NOTIFY_PREFIX",
    "fluid:": "FLUID_PREFIX",
    "toast:": "TOAST_PREFIX",
    "shortcut:": "SHORTCUT_PREFIX",
    "setting:": "SETTING_PREFIX",
    "oplus:": "OPLUS_PREFIX",
    "app:": "APP_PREFIX",
}


class Entry:
    """`Strings.kt` 里的一条常量，连同它**上方的整段原文**（注释与空行）。"""

    def __init__(self, name: str, key: str, block: list[str]) -> None:
        self.name = name
        self.key = key
        self.block = block


def derive_name(key: str) -> str:
    """键 → 常量名（只给**新**键用；已在文件里的名字不重新推导，见文件头）。"""
    name = re.sub(r"[^0-9A-Za-z]+", "_", key).upper()
    if not name or name[0].isdigit():
        name = "K_" + name
    return name


def parse_strings(text: str) -> tuple[list[Entry], list[str]]:
    """切出「文件头 / 每条常量 / 文件尾」。

    一条常量的原文块 = **上一条常量之后、这一条常量之前**的全部行（含空行与注释）。
    于是删掉一条常量 = 它自己那一段原文整体消失，相邻的排版一个字都不动。
    """
    lines = text.splitlines()
    const_positions: list[tuple[int, str, str]] = []
    for index, line in enumerate(lines):
        match = CONST.match(line)
        if match:
            const_positions.append((index, match.group(1), match.group(2)))

    if not const_positions:
        sys.exit(f"静态表：{STRINGS} 里一条 `const val` 都没找到（文件被改坏了？）")

    entries: list[Entry] = []
    start = const_positions[0][0]
    for index, name, key in const_positions:
        entries.append(Entry(name, key, lines[start:index]))
        start = index + 1
    tail = lines[const_positions[-1][0] + 1 :]
    return entries, tail


def read_source(path: Path) -> tuple[str, str]:
    """读成 (正文, 行尾)。行尾取自磁盘上的那份 —— 仓库开着 `core.autocrlf=true`，
    手写文件在 Windows 上就是 CRLF；生成物跟着源走，别把整个文件的行尾换掉（那会变成
    「改了 1 行、diff 却是全文件」）。新文件一律 LF（入库形态）。"""
    if not path.is_file():
        return "", "\n"
    raw = path.read_bytes()
    eol = "\r\n" if b"\r\n" in raw else "\n"
    return raw.decode("utf-8").replace("\r\n", "\n"), eol


def load_json(path: Path) -> object:
    if not path.is_file():
        sys.exit(f"静态表：文件不存在：{path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        sys.exit(f"静态表：{path} 不是合法 JSON：{e}")


def render_strings() -> str:
    zh = load_json(ZH)
    en = load_json(EN)
    if not isinstance(zh, dict) or not isinstance(en, dict):
        sys.exit("静态表：两份字典的顶层都必须是对象（键 → 正文）")

    only_zh = sorted(set(zh) - set(en))
    only_en = sorted(set(en) - set(zh))
    if only_zh or only_en:
        sys.exit(
            "静态表：两份字典的键集合不一致 —— "
            f"en 缺 {only_zh[:5]}；zh 缺 {only_en[:5]}（先对齐再生成）"
        )

    text, _eol = read_source(STRINGS)
    entries, _tail = parse_strings(text)

    seen: dict[str, str] = {}
    for entry in entries:
        if entry.name in seen:
            sys.exit(f"静态表：常量名重复：{entry.name}（{seen[entry.name]} 与 {entry.key}）")
        seen[entry.name] = entry.key

    keys = list(zh)
    kept: list[Entry] = []
    dropped: list[str] = []
    for entry in entries:
        if entry.key in zh:
            kept.append(entry)
        else:
            dropped.append(f"{entry.name}（{entry.key}）")

    kept_keys = {entry.key for entry in kept}
    added = [key for key in keys if key not in kept_keys]

    for key in added:
        name = derive_name(key)
        if name in seen:
            sys.exit(
                f"静态表：新键 {key} 推导出的常量名 {name} 与已有的 {seen[name]} 撞了 —— "
                "手工在 Strings.kt 里给它一个不同的名字，再重新生成"
            )
        seen[name] = key

    out = [STRINGS_HEADER.rstrip("\n")]
    for entry in kept:
        out.extend(entry.block)
        out.append(f'    const val {entry.name} = "{entry.key}"')
    for key in added:
        out.append("")
        out.append(f'    const val {derive_name(key)} = "{key}"')
    out.append("}")
    out.append("")

    if dropped or added:
        print(f"Strings.kt：删 {len(dropped)} 条、加 {len(added)} 条")
        for line in dropped:
            print(f"  - 删 {line}（字典里已经没有这个键）")
        for key in added:
            print(f"  + 加 {derive_name(key)} = \"{key}\"")

    return "\n".join(out)


def render_step(step: str) -> str:
    """`shell:am start …` → `Action.SHELL_PREFIX + "am start …"`（前缀符号化，口径只写一处）。"""
    for prefix, symbol in PREFIXES.items():
        if step.startswith(prefix):
            return f'            step = Action.{symbol} + "{step[len(prefix):]}",'
    return f'            step = "{step}",'


def render_commands() -> str:
    rows = load_json(COMMANDS_SRC)
    if not isinstance(rows, list) or not rows:
        sys.exit(f"静态表：{COMMANDS_SRC} 必须是非空数组")

    # 字典键 → 常量名（键写错时这里会当场报出来，而不是生成一个编译不过的文件）。
    zh = load_json(ZH)
    strings_text, _eol = read_source(STRINGS)
    entries, _tail = parse_strings(strings_text)
    by_key = {entry.key: entry.name for entry in entries}
    by_key.update({key: derive_name(key) for key in zh if key not in by_key})

    out = [COMMANDS_HEADER.rstrip("\n")]
    ids: set[str] = set()
    for row in rows:
        missing = [
            field
            for field in ("id", "brand", "root", "name", "nameFallback", "summary", "summaryFallback", "step")
            if field not in row
        ]
        if missing:
            sys.exit(f"静态表：{COMMANDS_SRC} 的 {row.get('id', '?')} 缺字段 {missing}")
        if row["id"] in ids:
            sys.exit(f"静态表：id 重复：{row['id']}（它同时是写进配置的 `cmd:` 取值）")
        ids.add(row["id"])
        for field in ("name", "summary"):
            if row[field] not in by_key:
                sys.exit(f"静态表：{row['id']} 的 {field} 指向一个字典里没有的键：{row[field]}")

        if row.get("note"):
            out.append(f'        // {row["note"]}')
        out.append("        CommonCommands.Entry(")
        out.append(f'            id = "{row["id"]}",')
        out.append(f'            brand = CommonCommands.Brand.{row["brand"]},')
        out.append(f'            root = {"true" if row["root"] else "false"},')
        out.append(f'            nameKey = Strings.{by_key[row["name"]]},')
        out.append(f'            nameFallback = "{row["nameFallback"]}",')
        out.append(f'            summaryKey = Strings.{by_key[row["summary"]]},')
        out.append(f'            summaryFallback = "{row["summaryFallback"]}",')
        out.append(render_step(row["step"]))
        out.append("        ),")
    out.append("    )")
    out.append("")
    return "\n".join(out)


#  两张生成物里**允许出现**的顶层声明 —— 整份文件重写，多出来的都是别人的代码。
#
#  加这一道闸的来路：2026-11-16 有一次合并把 `ClockCatalog` / `CommonCommands` 等几份并进了
#  `CommonCommandsTable.kt`，跑一次生成器就把它们连内容一起抹掉了 —— 编译当场崩，而生成器
#  自己报的是「已重写」，看起来一切正常。生成物是**单写者**：谁往里塞东西，谁就会被下一次
#  生成吃掉。所以这里宁可拒绝执行，也不写。
EXPECTED_DECLS = {
    STRINGS: {"Strings"},
    COMMANDS_OUT: {"COMMON_COMMAND_ENTRIES"},
}

#  顶格声明（行首无空白）：生成物里的成员都在对象 / 列表里面，缩进着，不会误伤。
TOP_LEVEL = re.compile(
    r"^(?:public |internal |private |protected )?(?:const )?"
    r"(?:val|var|fun|class|object|interface|typealias)\s+(\w+)",
    re.M,
)


def foreign_decls(path: Path, allowed: set[str]) -> list[str]:
    """这份文件里除了生成物自己，还有哪些顶层声明（空表 = 干净 / 文件还不存在）。"""
    if not path.exists():
        return []
    text = read_source(path)[0]
    code = "\n".join(line for line in text.split("\n") if not line.lstrip().startswith(("//", "*", "/*")))
    return sorted({name for name in TOP_LEVEL.findall(code) if name not in allowed})


def main(argv: list[str]) -> int:
    check = "--check" in argv
    if not check and len(argv) > 1:
        sys.exit(f"用法：{Path(sys.argv[0]).name} [--check]")

    targets = [(STRINGS, render_strings()), (COMMANDS_OUT, render_commands())]

    #  **先查「有没有别人的代码」再谈覆盖**：两张表都是整份重写，混进去的声明会被静默抹掉。
    dirty = [(path, foreign_decls(path, EXPECTED_DECLS[path])) for path, _ in targets]
    dirty = [(path, extra) for path, extra in dirty if extra]
    if dirty:
        print("[拒绝] 生成物里有不是它自己的顶层声明 —— 整份重写会把它们抹掉：", file=sys.stderr)
        for path, extra in dirty:
            print(f"  - {path.relative_to(REPO)}：{', '.join(extra)}", file=sys.stderr)
        print(
            "  改法：把那些声明挪到别的文件（生成物是单写者，不能当合并落点 / 存放处）。",
            file=sys.stderr,
        )
        return 1

    stale: list[Path] = []
    for path, content in targets:
        old, eol = read_source(path)
        if old == content:
            continue
        stale.append(path)
        if not check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content.replace("\n", eol).encode("utf-8"))

    if not check:
        print(
            "静态表："
            + ("已重写 " + "、".join(str(p.relative_to(REPO)) for p in stale) if stale else "已是最新，没有改动")
        )
        return 0

    if not stale:
        print("静态表：两张生成物都与源一致。")
        return 0

    print(f"[不达标] {len(stale)} 份生成物过期：")
    for path in stale:
        print(f"  - {path.relative_to(REPO)}")
    print("\n  改法：python3 tools/gen-static-tables.py，把 diff 一起提交。")
    print("  别手改生成物 —— 下一次生成会覆盖（注释例外，见脚本开头）。")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
