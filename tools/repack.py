"""按职责把平铺目录下的文件搬进子包。

搬家 = `git mv` + 改 `package` 行。**改 import 不在这里做** —— 搬家之后旧路径全失效，
统一交给 `fiximports.py` 按「符号 → 当前包」的索引规范化，比按文件名猜可靠得多
（顶层函数/常量散在与文件名无关的文件里）。

跑法：python tools/repack.py
"""

import io
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = "app/src/main/java/com/lookie/opluskey/"

# (相对 app 源根的目录, 该目录的包名, {子目录: [文件名（不带 .kt）]})
MOVE_PLAN = [
    # ── app/ui：原本 72 个文件平铺，按「这块东西是干什么的」分 ──
    ("ui", "com.lookie.opluskey.ui", {
        "chrome": [
            "PageChrome", "PageScaffold", "PageViewTree", "PageOverlays", "PageHooks",
            "PageTabBand", "PageHeaderWidget", "PageFab", "PageLoad", "PageUpdate",
            "PageControl", "PageText", "SettingsPageDebug",
        ],
        "dialog": [
            "ConfirmDialog", "PageConfirmDialog", "PageNameDialog", "PageSheet",
            "PagePicker", "PercentDialog", "MenuPopup", "PageMenuPopup",
            "ModeMenuPopups", "ModeMenuTarget",
        ],
        "row": [
            "CardRow", "ExpandableRow", "ExpandableRowSpec", "SwitchRow", "SliderRow",
            "SliderSpec", "PageSlider", "SectionTitle", "ChipLabel", "TileWidgets",
            "SpacedItems", "ScenarioEntryRow", "TitleCounter", "LongPressSpec",
            "OwnSettingsCard",
        ],
        "write": [
            "ConfigWrite", "GlobalWrite", "KeyParamsWrite", "CommandsSave",
            "ConfigReset", "BindingTarget", "StepActions",
        ],
        "wire": [
            "HostLinkActions", "NetBridge", "Receivers", "LinkCheck", "GestureProbe",
            "AppNaming",
        ],
        "style": [
            "AppCardColors", "AppTextStyles", "IconTint", "DrawablePainter",
            "TextMetrics", "EmphasisText", "Frosted", "Visibility", "EmptyArea",
            "PageTheme",
        ],
        "feedback": ["DisabledHint", "Feedback", "Haptics", "PageHaptics", "PageFeedback"],
        "input": ["AppInputField", "IconInput", "Ime", "MarkdownText"],
    }),
    # ── app/ui/model：30 个纯数据 / 文案 / 图标 / 主题 ──
    ("ui/model", "com.lookie.opluskey.ui.model", {
        "icon": [
            "ActionIcons", "CommonCommandIcons", "ConditionIcons", "DrawnIconBadge",
            "DrawnIconCanvas", "DrawnIcons", "IconTint", "PageIcons",
        ],
        "text": [
            "CommandConditionText", "CommandText", "ConditionValueText",
            "KeySwitchTexts", "PageCopy", "TileSubtitle", "ActionRowView",
        ],
        "theme": [
            "AppTheme", "PageColors", "PagePalette", "PageMotion", "ThemeColors",
            "PageHostTuning",
        ],
        "page": ["PageMetrics", "PageLinks", "TextMetrics"],
        "target": [
            "AuthorTarget", "DonateTarget", "Donor", "FeedbackTarget",
            "LaunchableActivity", "LaunchableApp",
        ],
    }),
    # ── app/ui/state：27 个状态持有者，按「管哪一块界面」分 ──
    ("ui/state", "com.lookie.opluskey.ui.state", {
        "page": [
            "PageState", "PageLoadSnapshot", "ConfigState", "DeviceGate", "UiState",
            "SettingsTargetState", "HostLinkState",
        ],
        "command": [
            "CommandDraftState", "CommandListEditState", "CommandUiState",
            "StepRowDisplay", "TestRunnerCache", "CommandsSave",
        ],
        "binding": [
            "KeysEditState", "KeyWatchState", "ComboEditState", "KeysAsk",
            "BranchDraftState",
        ],
        "signal": ["BandHeight", "VarListSignal", "HintCenter", "HintQueue"],
        "host": ["I18nBridge", "LinkCheckState", "UpdateState"],
        "picker": ["AppPickerState", "AppPickerUiState"],
    }),
    # ── app/host/action：24 个执行器，按「动的是哪一类东西」分 ──
    ("host/action", "com.lookie.opluskey.host.action", {
        "shell": ["ShellRunner", "ShellLimits", "ProcessAction", "RootGrant"],
        "app": [
            "AppLauncher", "AppLaunchAsUser", "OriginalLaunch", "ForegroundApp",
            "MiniWindowLauncher", "MiniWindowFacts", "MiniWindowVerify", "ShortcutStarter",
        ],
        "condition": ["ConditionProbe", "ConditionServices", "ConditionTimeCheck"],
        "setting": ["BrightnessAction", "VolumeAction", "SettingSwitch"],
        "notice": ["ActionNotice", "NotifyAction", "FluidAction"],
    }),
    # ── app/inject：15 个注入侧入口，按「接的是哪条通道」分 ──
    ("inject", "com.lookie.opluskey.inject", {
        "key": [
            "KeyHooks", "KeyInject", "KeyObserverHooks", "KeyWatch", "ChordGuard",
            "SideKeyInterceptor", "ScreenshotViaHelper",
        ],
        "wire": ["ConfigChannel", "NetChannel", "ProbeCall", "ProbeChannel", "ProbeOps"],
        "system": ["SystemToast", "SystemVolume", "ShortcutRead"],
    }),
]


def read(path):
    return io.open(path, encoding="utf-8", newline="").read().replace("\r\n", "\n")


def write(path, text):
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)


def git_mv(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(["git", "mv", src, dst], cwd=ROOT, check=True)


def repack(rel_dir, base_pkg, plan):
    base = os.path.join(ROOT, APP, rel_dir)
    present = sorted(f[:-3] for f in os.listdir(base) if f.endswith(".kt"))
    planned = [n for names in plan.values() for n in names]
    dup = {n for n in planned if planned.count(n) > 1}
    if dup:
        sys.exit("%s 的 PLAN 有重复: %s" % (rel_dir, dup))
    missing = [
        n for n in planned
        if n not in present
        and not os.path.exists(os.path.join(base, plan_sub(plan, n), n + ".kt"))
    ]
    if missing:
        sys.exit("%s 的 PLAN 有不存在的文件: %s" % (rel_dir, missing))
    keep = [n for n in present if n not in planned]

    moved = skipped = 0
    for sub, names in plan.items():
        for name in names:
            src = os.path.join(base, name + ".kt")
            dst = os.path.join(base, sub, name + ".kt")
            if not os.path.exists(src):
                skipped += 1
                continue
            git_mv(src, dst)
            text = read(dst)
            old = "package %s\n" % base_pkg
            if not text.startswith(old):
                sys.exit("package 行不对: %s -> %r" % (dst, text[:60]))
            write(dst, text.replace(old, "package %s.%s\n" % (base_pkg, sub), 1))
            moved += 1

    print("%-14s 搬走 %2d 个（已搬 %d）-> %s；原地留 %d 个 %s"
          % (rel_dir, moved, skipped, "/".join(sorted(plan)), len(keep), keep))


def plan_sub(plan, name):
    for sub, names in plan.items():
        if name in names:
            return sub
    raise KeyError(name)


def rewrite_refs():
    """把全仓的 `com.lookie.opluskey.<旧包>.<Name>` 改成新路径。

    只按 import 行改是不够的：还有两种写法 —— `import pkg.Obj.member`（成员导入）与
    代码里直接写全限定名。这里对**每一处文本出现**做替换，边界用「后面不是单词字符」
    判断，于是 `...MiniWindowFacts.ROM` 这种带成员的也能一起换掉。
    """
    pairs = []
    for rel_dir, base_pkg, plan in MOVE_PLAN:
        for sub, names in plan.items():
            for name in names:
                pairs.append((base_pkg, name, "%s.%s" % (base_pkg, sub)))
    # 长前缀优先，避免 `ui` 抢掉 `ui.model` 的匹配
    pairs.sort(key=lambda p: -len(p[0]))

    patterns = [
        (re.compile(re.escape("%s.%s" % (old, name)) + r"(?!\w)"), "%s.%s" % (new, name))
        for old, name, new in pairs
    ]

    touched = hits = 0
    for root in (os.path.join(ROOT, "app/src/main/java"), os.path.join(ROOT, "core/src/main/java")):
        for dirpath, _dirs, files in os.walk(root):
            for f in files:
                if not f.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, f)
                text = read(path)
                new_text = text
                for pat, repl in patterns:
                    new_text = pat.sub(repl, new_text)
                if new_text != text:
                    write(path, new_text)
                    touched += 1
                    hits += 1
    print("全限定引用：改了 %d 个文件" % touched)


def main():
    for rel_dir, base_pkg, plan in MOVE_PLAN:
        repack(rel_dir, base_pkg, plan)
    rewrite_refs()


if __name__ == "__main__":
    main()
