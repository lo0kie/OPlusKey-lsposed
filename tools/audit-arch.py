#!/usr/bin/env python3
"""架构判据（趋势线）：把「一条业务规则有几份实现」变成可打印的数字。

与 `audit-coupling.py` 的分工：那个管**包与包之间**的方向（禁止边 / 环），这个管
**同一条规则在几个地方各写了一遍**。后者结构上看不见 —— 包依赖图完全干净的时候，
一条规则仍然可能有四五份手写实现，而漏改其中一处的表现是「某个路径上行为不一致」。

默认只打印、永远 exit 0（当趋势线看）；加 `--strict` 时把不达标的判据变成 exit 1
（等数值稳定后再把 CI 这一步改成阻断）。

用法（仓库根）：
    python3 tools/audit-arch.py
    python3 tools/audit-arch.py --strict
"""
from __future__ import annotations

import os
import re
import sys

APP = 'app/src/main/java/com/lookie/opluskey'
CORE = 'core/src/main/java/com/lookie/opluskey/core'

BLOCK_COMMENT = re.compile(r'/\*.*?\*/', re.S)
LINE_COMMENT = re.compile(r'//[^\n]*')


def kt_files(*roots: str):
    for root in roots:
        for dirpath, _, names in os.walk(root):
            for name in names:
                if name.endswith('.kt'):
                    yield '/'.join([dirpath.replace(os.sep, '/'), name])


def code_only(path: str) -> str:
    """去掉注释再数 —— 否则「注释里举例说明不该怎么写」会被算成一处违规。"""
    text = open(path, encoding='utf-8', errors='replace').read()
    return LINE_COMMENT.sub('', BLOCK_COMMENT.sub('', text))


def count(pattern: str, *roots: str) -> int:
    rx = re.compile(pattern, re.M)
    return sum(len(rx.findall(code_only(p))) for p in kt_files(*roots))


def file_lines(path: str) -> int:
    return len(open(path, encoding='utf-8', errors='replace').read().splitlines())


def main() -> int:
    strict = '--strict' in sys.argv
    rows: list[tuple[str, int, str, bool]] = []

    def add(name: str, value: int, rule: str, ok: bool):
        rows.append((name, value, rule, ok))

    # 1) 「这颗键现在生效吗」：唯一判据是 `Config.isLive`（两级相与）。
    #    三处决策点里只剩两处用它：**松手**那一支不该再按配置判 —— 这一按到底有没有起过采集，
    #    只有执行侧知道（`ActionRunner.voiceMemoryOn`），而条件执行会让「静态绑定」与
    #    「这一按实际执行的动作」不是一回事。按配置判会把「条件把长按改成语音」那一档漏掉，
    #    于是录音开着一路不收尾。所以这里数的是「还有几处在用同一个判据」，不是「必须三处」。
    add(
        'Config.isLive 的调用点',
        count(r'\bisLive\(', APP),
        '≥ 2（注入认领 / 宿主执行；松手由执行侧状态判）',
        count(r'\bisLive\(', APP) >= 2,
    )
    add(
        '两级闸门就地手写（global.enabled ||）',
        count(r'global\.enabled\s*\|\|', APP),
        '= 0',
        count(r'global\.enabled\s*\|\|', APP) == 0,
    )

    # 2) 「包还在吗 → 报哪一档失败」：唯一实现是 `entryFailure`。
    add(
        'entryFailure 的调用点',
        count(r'\bentryFailure\(', APP),
        '≥ 3（解析不到 / 拉起被拒 / 小窗）',
        count(r'\bentryFailure\(', APP) >= 3,
    )
    #  这条要排除 helper 自己（`entryFailure` 的实现就是那一句）
    inline_failure = count(r'if\s*\(\s*installed\s*\)\s*CommandFailure\.APP_NO_ENTRY', APP) - 1
    add(
        '失败分类就地手写（if (installed) APP_NO_ENTRY）',
        inline_failure,
        '= 0（只许留在 entryFailure 里）',
        inline_failure == 0,
    )

    # 3) 「一个动作怎么念」：唯一实现是 `core/model/actionTitle`（宿主与界面各一处调用）。
    add(
        'actionTitle 的调用点',
        count(r'\bactionTitle\(', APP),
        '= 2（执行时提示 / 步骤行）',
        count(r'\bactionTitle\(', APP) == 2,
    )
    # 另有 `boundNotice` 用清单名取**裸名**（不带「打开应用:」前缀），那是另一条规则，不并。
    add(
        'ActionCatalog.label 的直接调用',
        count(r'ActionCatalog\.label\(', APP, CORE),
        '= 2（actionTitle + 绑定回执的裸名）',
        count(r'ActionCatalog\.label\(', APP, CORE) == 2,
    )

    # 4) 「链路结论」：两处渲染共用一份事实 —— `LinkFacts` 的构造点只有一处。
    add(
        'LinkFacts 的构造点',
        count(r'=\s*LinkFacts\(', APP),
        '= 1',
        count(r'=\s*LinkFacts\(', APP) == 1,
    )

    # 5) 共用层不许认识界面库（本来就是编译期约束，这里做事后核对）。
    add(
        'core 里的界面库 import',
        count(r'^import (?:androidx\.compose|io\.github\.suqi8)', CORE),
        '= 0',
        count(r'^import (?:androidx\.compose|io\.github\.suqi8)', CORE) == 0,
    )

    width = max(len(r[0]) for r in rows)
    print('架构判据（当前值 / 规则）：')
    failed = []
    for name, value, rule, ok in rows:
        mark = '' if ok else '  <-- 未达标'
        if not ok:
            failed.append(name)
        print(f'  {name.ljust(width)}  {value:>5}   / {rule}{mark}')

    # 规模类：只当趋势线，不判达标
    print()
    print('规模（趋势线，不判定）：')
    page_ext = count(r'^(?:internal |private )?(?:fun|val|var) SettingsPage\.', APP)
    print(f'  页面扩展成员（SettingsPage.xxx）  {page_ext:>5}')
    print(f'  host/GestureReceiver.kt 行数      {file_lines(f"{APP}/host/GestureReceiver.kt"):>5}')
    print(f'  host/GestureExec.kt 行数          {file_lines(f"{APP}/host/GestureExec.kt"):>5}')
    print(f'  ui/state/HintCenter.kt 行数       {file_lines(f"{APP}/ui/state/HintCenter.kt"):>5}')

    print()
    if failed:
        print(f'未达标 {len(failed)} 项：' + '、'.join(failed))
        if strict:
            return 1
        print('（这次只打印；等它们稳定后再把本脚本改成阻断）')
    else:
        print('全部达标。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
