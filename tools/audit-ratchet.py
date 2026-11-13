#!/usr/bin/env python3
r"""规模判据 —— 三条**绝对阈值**，越线即红。

## 名字的来历（以及它为什么不再是一套「棘轮」）

这个脚本原先的形态是「把今天实测到的值记成上限、只许降」：上限表里逐个文件记行数，
`--tighten` 收紧，放宽要手改并写理由。2026-11-13 查历史时发现它并没有真的起那个作用：

· **8 次提交改过 8 次上限表，两个指标一次都没有降过**
  （`Strings.kt` 1513 → 1513 → 1514 → 1542 → 1554 → 1587 → 1646；
   `settings_page_ext` 516 → 517 → 520 → 530 → 530 → 534 → 546 → 546）。
  原因是结构性的：`--tighten` 在超限时根本走不到（先 `return 1`），而功能开发期规模只会涨
  ⇒「自动收紧」这条路径实际上不存在，「只许降」于是只剩「每次都要抬一次上限」这一半。
· 它数的是**物理行数，含注释**。本仓库注释占比 50–70%（`Action.kt` 1139 行里只有 436 行
  是代码，`PageIcons.kt` 1027 行里 123 行是单行 `val Xxx: ImageVector`）。于是「把判据写
  清楚」——本仓库最在意的动作——会被它记账，逼着人解释「为什么多这几行」，而理由无一例外
  是「因为加了功能」。

**净效果是一笔过程税**，换来的保护（拦住一次加两百行）在 code review 与
`audit-coupling.py` 的单文件警告里本来就看得见。所以按用户口径改成下面这三条。

## 现在管什么

三条，都是**绝对阈值**（不是「今天多少」）：

· `count.detekt_baseline` = 0 —— **一条豁免都不许有**（AGENTS.md 二、1.2）。这不是趋势线：
  新代码不许往 `config/detekt/baseline.xml` 里加条目，也不许用 `@Suppress` 绕过。
· `count.settings_page_fields` ≤ 45 —— `ui/SettingsPage.kt` 的状态字段数。它是「新功能再往
  页面对象上挂一个字段」的入口，涨到某个量级就该把状态挪出去。
· `count.settings_page_ext` ≤ 700 —— `fun/val/var SettingsPage.*` 的总数：页面对象上挂了多少
  个带接收者的成员，也就是这一层对外暴露的耦合面。

阈值给的是**失控线，不是现状**：正常加功能碰不到，真要一口气挂几十个才红。实测值仍会打印，
所以趋势看得见——只是不该用「每次提交抬一次上限」来表达趋势。

## 千行文件不在这里管了

`count.files_over_1000` 与逐文件的 `lines.*` 已删。它分不清「123 行图标声明」与「1600 行
逻辑」，而且数注释。「单文件多大」这件事仍有 `audit-coupling.py` 的 1500 行那条，那里报的是
「去看一眼」，不是「改基准」。

## 阈值放在哪

`tools/ratchet.txt`（文件名同样是历史遗留）：`键 = 数值`，一列，改阈值只动那张表、不必碰
脚本。脚本会校验表里的键与它度量的键**一一对应**——拼错一个键就等于那条判据没在跑，
那种「全绿」是假的。

用法（仓库根目录）：

    python3 tools/audit-ratchet.py     # 校验，越线 exit 1
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
LIMITS_FILE = REPO / "tools/ratchet.txt"

# 口径与 `decoupling.md` 零节 / `audit-arch.py` 那条逐字相同，历史数字因此可以直接对比
FIELD_RE = re.compile(r"^    (?:internal )?(?:private )?(?:val|var) ", re.M)
EXT_RE = re.compile(r"^(?:internal |private )?(?:fun|val|var) SettingsPage\.", re.M)
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")

# 这三条就是判据全集。表里多一个 / 少一个都算错（拼错的键等于那条判据没在跑）
KEYS = (
    "count.detekt_baseline",
    "count.settings_page_fields",
    "count.settings_page_ext",
)


def code_only(path: Path) -> str:
    """去掉注释再数成员 —— 行结构不变，只是把注释正文抽走。"""
    text = path.read_text(encoding="utf-8", errors="replace")
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def measure() -> dict[str, int]:
    """实测值 —— 键与 `tools/ratchet.txt` 一一对应。"""
    ext = sum(len(EXT_RE.findall(code_only(p))) for p in APP.rglob("*.kt"))
    return {
        "count.detekt_baseline": BASELINE.read_text(encoding="utf-8", errors="replace").count("<ID>"),
        "count.settings_page_fields": len(FIELD_RE.findall(code_only(SETTINGS_PAGE))),
        "count.settings_page_ext": ext,
    }


def read_limits() -> dict[str, int]:
    limits: dict[str, int] = {}
    for line in LIMITS_FILE.read_text(encoding="utf-8").splitlines():
        body = line.split("#", 1)[0].strip()
        if not body or "=" not in body:
            continue
        key, _, value = body.partition("=")
        key, value = key.strip(), value.strip()
        if not value.isdigit():
            sys.exit(f"规模阈值表：{key} 的值不是非负整数（{value}）")
        limits[key] = int(value)
    return limits


def main() -> int:
    # 路径写错 / 文件改名时报错退出，不静默当成 0 —— 那种「全绿」是假绿
    for path in (SETTINGS_PAGE, BASELINE, LIMITS_FILE):
        if not path.is_file():
            sys.exit(f"规模判据：文件不存在：{path}")

    limits = read_limits()
    now = measure()

    missing = [k for k in KEYS if k not in limits]
    extra = [k for k in limits if k not in KEYS]
    if missing or extra:
        sys.exit(
            "规模阈值表与判据对不上："
            + (f"表里缺 {missing}；" if missing else "")
            + (f"表里多 {extra}（拼错？）" if extra else ""),
        )

    over = [(k, now[k], limits[k]) for k in KEYS if now[k] > limits[k]]

    width = max(len(k) for k in KEYS)
    print("规模判据（实测 / 阈值）：")
    for key in KEYS:
        mark = "  <- 越线" if now[key] > limits[key] else ""
        print(f"  {key.ljust(width)}  {now[key]:>5} / {limits[key]:>5}{mark}")

    if over:
        print("\n[越线] 这三条是绝对阈值，不是「今天多少」：")
        for key, seen, limit in over:
            print(f"  - {key}：{seen} > 阈值 {limit}")
        if any(k == "count.detekt_baseline" for k, _, _ in over):
            print("    detekt 基线必须为 0：把豁免改掉，别往基线或 `@Suppress` 里加。")
        print("    页面对象那两条越线时，先把状态 / 成员挪出去；确实该调阈值，改 tools/ratchet.txt 并在提交信息里说明。")
        return 1

    print("\n[达标] 三条判据都在阈值内。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
