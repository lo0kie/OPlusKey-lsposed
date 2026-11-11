#!/usr/bin/env python3
r"""i18n 键集合审计 —— 三份列表必须**一一对应**。

## 三份是哪三份

界面上出现一句文案，牵扯三处：

1. `core/i18n/Strings.kt` 的 `const val XXX = "a.b"`（代码里引用的名字 → 字典键）；
2. `app/src/main/assets/locales/zh-CN.json`（中文正文，也是**兜底串**必须逐字相同的那一份）；
3. `app/src/main/assets/locales/en.json`（英文正文）。

其中 1 与 2 之间**没有任何编译期关系**：常量写了个字典里不存在的键，`I18n.get` 会退回兜底串，
界面照常显示，编译照常通过 —— 只有切到另一种语言时才会露出「这一句还是中文」。
2 与 3 之间同理：漏翻译一条，英文界面会整句掉回中文兜底，同样静默。
`tools/check-i18n-fallbacks.py` 管的是「兜底串 = 中文正文」，管不到这三份的**集合**。

## 判据（四条，任一不满足即 exit 1）

1. 每个 `Strings` 常量的**值**都在 `zh-CN.json` 里；
2. `zh-CN.json` 的每个键都有对应的 `Strings` 常量（否则那条只能被硬编码字符串引用）；
3. `zh-CN.json` 与 `en.json` 的键集合完全相同（缺翻译 / 多出孤儿键都算错）；
4. 常量名不重复、两个常量不指向同一个键（后者多半是复制粘贴时忘了改值）。

用法（仓库根目录）：`python3 tools/check-i18n-keys.py`
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

CONST = re.compile(r'^\s*const val (\w+) = "([^"]+)"\s*$', re.M)


def main() -> int:
    for path in (STRINGS, ZH, EN):
        if not path.is_file():
            # 路径写错 / 文件改名时**报错退出**：静默当成空集合会让四条判据全部「通过」
            sys.exit(f"i18n 键集合审计：文件不存在：{path}")

    consts = CONST.findall(STRINGS.read_text(encoding="utf-8"))
    zh = json.loads(ZH.read_text(encoding="utf-8"))
    en = json.loads(EN.read_text(encoding="utf-8"))

    problems: list[str] = []

    # 1) 常量名与取值本身
    by_name: dict[str, str] = {}
    by_value: dict[str, str] = {}
    for name, value in consts:
        if name in by_name:
            problems.append(f"常量名重复：{name}")
        by_name[name] = value
        if value in by_value:
            problems.append(f"两个常量指向同一个键：{by_value[value]} 与 {name} → {value}")
        by_value[value] = name

    # 2) 常量 → 字典
    for value, name in sorted(by_value.items()):
        if value not in zh:
            problems.append(f"{name} = \"{value}\" 在 zh-CN.json 里没有这一条")

    # 3) 字典 → 常量
    for key in sorted(zh):
        if key not in by_value:
            problems.append(f"zh-CN.json 的 \"{key}\" 没有对应的 Strings 常量")

    # 4) 两份字典
    only_zh = sorted(set(zh) - set(en))
    only_en = sorted(set(en) - set(zh))
    for key in only_zh:
        problems.append(f"en.json 缺这一条：{key}")
    for key in only_en:
        problems.append(f"en.json 多出这一条（zh-CN 里没有）：{key}")

    print(
        f"常量 {len(by_name)} 个；zh-CN {len(zh)} 键；en {len(en)} 键；"
        f"两边的键集合{'一致' if not (only_zh or only_en) else '不一致'}"
    )
    if problems:
        print(f"\n[不达标] {len(problems)} 条：")
        for line in problems:
            print(f"  - {line}")
        return 1
    print("\n[达标] 三份列表一一对应。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
