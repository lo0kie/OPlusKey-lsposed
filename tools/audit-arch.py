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
import unicodedata

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


def count_excluding(pattern: str, skip: tuple[str, ...], *roots: str) -> int:
    """数出现次数，但跳过 `skip` 里点名的文件。

    用于「某一处是**定义**（字典的 `const val`、工厂自己的使用），别处出现才算旁路」这类判据 ——
    判据要卡的是**第二份实现**，不是第一份。
    """
    rx = re.compile(pattern, re.M)
    return sum(len(rx.findall(code_only(p))) for p in kt_files(*roots) if not p.endswith(skip))


def file_lines(path: str) -> int:
    return len(open(path, encoding='utf-8', errors='replace').read().splitlines())


def pad(text: str, width: int) -> str:
    """按**显示宽度**补齐（中日韩字符占两列）—— 按 `len` 补的话表会串行。"""
    used = sum(2 if unicodedata.east_asian_width(ch) in 'WF' else 1 for ch in text)
    return text + ' ' * max(0, width - used)


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

    # 3) 「一个动作怎么念」：唯一实现在 `core/present/CommandTitle`。
    #
    #    这里**不数函数名** —— 上一版数的是 `actionTitle(` / `actionFinalTitle(`，那两个名字随着
    #    重构一起消失了，判据于是变成「0 / = 4」这种红在空气上的样子：它会红，但红的原因不是
    #    又长出一份实现，而是脚本还在认旧名字。判据必须长成**会红在实处**的东西，所以改成数
    #    三件结构事实，每条都能直接 grep：
    #
    #      · 拼句子用的两个字典格式键，只在工厂里被读；
    #      · 拼装函数全项目一个（叫 `join` 还是 `compose` 都认）；
    #      · 清单名 `ActionCatalog.label` 只有工厂喊。
    #
    #    三者分别是「分隔符有第二份写法」「语序有第二套」「名字有第二个来源」—— 改名字不影响它们，
    #    只有真的又写一套才会红。`Strings.kt` 除外：格式键的定义在那儿，那是字典的本职。
    fmt_keys = count_excluding(
        r'COMMON_(?:NAME_STATE|PREFIX_NAME)_FORMAT',
        ('i18n/Strings.kt', 'present/CommandTitle.kt'),
        CORE,
        APP,
    )
    add(
        '拼句子的格式键（工厂之外）',
        fmt_keys,
        '= 0（旁路手拼分隔符）',
        fmt_keys == 0,
    )
    #  两个格式键就是两种关系：`%s: %s` 父与子、`%s - %s` 名字与状态。别处自己
    #  `I18n.format(..., " - ")` 或 `joinToString(" - ")` 就是又一套语序 —— 而且它多半只在
    #  一条分支上被写出来，另一条忘了改时，同一个动作在界面上就有两种念法。
    joiners = count(r'\bfun (?:join|compose)\(', CORE)
    add(
        '拼装函数（core）',
        joiners,
        '= 1（唯一拼装）',
        joiners == 1,
    )
    #  两处就是两套语序（谁先拼父、状态粘在谁身上会不一致），所以这里卡死 1。
    #  它必须是 `private` 的，这一点 grep 数不出来，但文件里没有第二个 `fun` 拼句子时也就没处调。
    labels = count(r'ActionCatalog\.label\(', CORE, APP)
    add(
        'ActionCatalog.label 的直接调用',
        labels,
        '= 1（只有工厂取清单名）',
        labels == 1,
    )
    #  外面直接喊 `label` = 又有人自己在念动作。原先这里是 2：`releaseActionTitle` 为了
    #  「无操作」那一档特判了一次 —— 而那个特判的根源在工厂（`forSentence` 对 `None` 返回空串，
    #  把「提示位置不弹」这条位置语义塞进了「怎么念」）。挪回 `GestureExec.noticeIfNeeded`
    #  之后两边都干净：工厂只念句子，提示自己决定弹不弹。

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

    # 6) 四条「同一句话有两份推导」，各自收成了一处。判据数的是**第二份**会不会再长出来。
    #
    #    · 键码归不归模块 —— 唯一答案 `KeyId.claimsBuiltin`。再有人拿 `builtins` 跑一遍
    #      any/flatMap 就是第二份；漏改一处的后果分别是「system_server 里自持循环」与
    #      「用户自己收的键被误拦」，两者都不编译报错。
    key_claims = count_excluding(r'KeyId\.builtins\.(?:any|flatMap|filter|firstOrNull)\s*\{', ('model/KeyId.kt',), CORE, APP)
    add(
        '键码归属就地推（builtins 上跑遍历）',
        key_claims,
        '= 0（用 KeyId.claimsBuiltin）',
        key_claims == 0,
    )
    #    · 有没有绑定 —— 唯一答案 `Action.isBound`。`!is Action.None` / `== Action.None`
    #      这类取反/相等判断一处都不该再有（`when` 里的 `is Action.None ->` 不算：那是结构分派，
    #      不是判「有没有绑」）。
    bound = count(r'(?:!\s*is|==|!=)\s*Action\.None', CORE, APP)
    add(
        '「有没有绑定」就地取反（!is / == Action.None）',
        bound,
        '= 0（用 action.isBound）',
        bound == 0,
    )
    #    · 老配置那个 `|名字` 后缀 —— 只在名字优先级链（`core/model/AppName`）里读。
    #      外面再切一次就是又一条自己的顺序。
    legacy = count_excluding(r'AppTarget\.label\(', ('model/AppName.kt', 'model/AppTarget.kt'), CORE, APP)
    add(
        '老 |名字 后缀在优先级链之外被读',
        legacy,
        '= 0（走 resolveAppName / resolveTargetName）',
        legacy == 0,
    )
    #    · 「点这一格会发生什么」—— 唯一答案 `ActionCatalog.clickKind`。这条是**正向**计数：
    #      界面都在问它，才说明没有第二份判断。原先 `popsSelector` / `opensAppPicker` /
    #      `VARS_TILE` 各自判一遍，同一格在网格与抽屉里长出两种行尾记号。
    click_kinds = count(r'ActionCatalog\.clickKind\(', APP)
    add(
        'clickKind 的界面调用点',
        click_kinds,
        '≥ 3（网格格子 / 抽屉行 / 菜单行）',
        click_kinds >= 3,
    )

    # 7) 图标那一对表**不在这里数**：`Spec.glyph` 现在是必填的（`ActionCatalog.GlyphSource`），
    #    「清单条目漏了图标」当场编译失败 —— 编译器比 grep 更早也更准。
    #    试过一条 `actionIcon(canonicalToken(...))` 的判据，结果命中的是 `stepIcon`（步骤行，
    #    那里的 token 本来就不是 Spec，走兜底是对的）—— 一条会红在合法写法上的判据只能添噪音。
    #
    #    开关种类那一半同样交给编译器（`ActionIcons` 里对 `SwitchKind` 的 `when` 是穷尽的），
    #    但「种类 id 又变成裸字面量」这件事编译管不了 —— 那种写法散开之后，加一条开关就要
    #    满地找 `"rotation"` 这种串。这里数的是它。
    switch_literals = count(r'id\s*==\s*"', CORE, APP)
    add(
        '开关种类 id 裸字面量比较',
        switch_literals,
        '= 0（用 SwitchKind.X.id）',
        switch_literals == 0,
    )

    width = max(
        sum(2 if unicodedata.east_asian_width(ch) in 'WF' else 1 for ch in r[0])
        for r in rows
    )
    print('架构判据（当前值 / 规则）：')
    failed = []
    for name, value, rule, ok in rows:
        mark = '' if ok else '  <-- 未达标'
        if not ok:
            failed.append(name)
        print(f'  {pad(name, width)}  {value:>5}   / {rule}{mark}')

    # 规模类：只当趋势线，不判达标
    print()
    print('规模（趋势线，不判定）：')
    page_ext = count(r'^(?:internal |private )?(?:fun|val|var) SettingsPage\.', APP)
    print(f'  页面扩展成员（SettingsPage.xxx）  {page_ext:>5}')
    print(f'  host/GestureReceiver.kt 行数      {file_lines(f"{APP}/host/GestureReceiver.kt"):>5}')
    print(f'  host/GestureExec.kt 行数          {file_lines(f"{APP}/host/GestureExec.kt"):>5}')
    print(f'  ui/state/signal/HintCenter.kt 行数 {file_lines(f"{APP}/ui/state/signal/HintCenter.kt"):>5}')

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
