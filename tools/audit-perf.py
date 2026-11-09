"""性能审计 —— 把「常见性能问题」变成可 grep 的判据。

三道闸，前两道是**硬判据**（有命中就红），第三道只报数（趋势线，不判定）：

1. 主线程阻塞：界面层里那些会做 IO / 跨进程查询 / 等锁的调用，**不许**出现在主线程路径上
   （必须包在 `withContext(Dispatchers.IO)` 之类的后台段里）。
2. 注入侧热路径零分配：按键判定链跑在 `InputReader` 线程上、每次按压都要过一遍 ——
   那里每多一次 `HashSet` / 字符串拼接 / 正则编译，都是按次计的开销。
3. 组合期重算：`@Composable` 函数体里对状态做 `filter / map / sortedBy / toList / buildList`，
   且该函数里没有 `remember` —— 每帧重组都重算。这一档**只报数**：很多是「行表按状态派生」
   的正常写法，强行 remember 反而会拿旧状态。

用法（仓库根目录）：`python3 tools/audit-perf.py`；`--report` 只打印明细，不因第 3 类失败。
"""

import io
import os
import re
import sys

UI_ROOT = 'app/src/main/java/com/lookie/opluskey/ui'
INJECT_ROOT = 'app/src/main/java/com/lookie/opluskey/inject'

# ── 判据 1：主线程阻塞 ───────────────────────────────────────────────────────
# (说明, 正则)。只扫界面层；`withContext(Dispatchers.IO)` 之后的 20 行内算已挪到后台。
BLOCKING = [
    ('包管理器全量查询', r'getInstalledApplications\s*\('),
    ('包管理器意图查询', r'queryIntentActivities\w*\s*\('),
    ('包管理器解析', r'getApplicationInfo\s*\(|resolveActivity\w*\s*\('),
    ('同步读文件', r'\bFile\s*\(|readText\s*\(|readBytes\s*\('),
    ('同步写文件', r'writeText\s*\(|writeBytes\s*\(|FileOutputStream\s*\('),
    ('读配置（碰文件）', r'ConfigStore\.(read|load)\s*\('),
    ('主动休眠', r'Thread\.sleep\s*\('),
]
# 「这一行已经在后台」的判据：往上找最近的这几个记号
BACKGROUND = re.compile(r'withContext\s*\(\s*Dispatchers\.(IO|Default)|coroutineScope|launch\s*\(\s*Dispatchers')

# ── 判据 2：注入侧热路径零分配 ───────────────────────────────────────────────
# 这几个文件是「每次按键都会走」的判定链：里面出现这些构造 = 每次按压都分配一次。
HOT_FILES = [
    'inject/SideKeyInterceptor.kt',
    'inject/input/GestureDetector.kt',
    'inject/input/ComboDetector.kt',
    'inject/input/HeldPresses.kt',
    'inject/input/GestureForwarder.kt',
]
ALLOCATION = [
    ('新建集合', r'\b(HashSet|HashMap|ArrayList|LinkedHashMap|ArrayDeque)\s*[(<]'),
    ('字符串模板', r'"[^"]*\$'),
    ('正则编译', r'Regex\s*\('),
    ('序列/集合流水线', r'\.(map|filter|mapNotNull|sortedBy|toList|groupBy|associate)\s*[{<(]'),
]

#  日志那一行不算：判据日志本来就要拼接、要进日志文件，它的频率是「一次按压一条」而不是
#  「一次事件一条」；算进来只会把这个判据淹掉，真正该看的分配点反而看不见。
LOG_LINE = re.compile(r'\bXLog\.(d|w|e|i)\s*[({\[]')

#  **已复核的主线程命中**：路径 → 理由。加一条之前先确认「确实不在主线程」或「一次动作只跑一遍」，
#  理由写在这儿（而不是悄悄改判据）—— 下一次有人看这份报告，能一眼看出为什么它是白的。
MT_ALLOW = {
    'ui/AppNaming.kt': '应用名有进程内缓存（appLabelCache），只有首次未命中才问 PM',
    'ui/PageControl.kt': '重启页进程那条路：睡眠在独立线程上，/proc 扫描只在重启时跑一次（页面随后就没了）',
    'ui/hook/LogPull.kt': '导出日志是一次性动作，等待在导出线程上',
    'ui/hook/ConfigTransfer.kt': '导入配置是一次性动作，文件在选中之后那一段读',
    'ui/hook/SettingsPageHooks.kt': '一次 File.exists 的 stat，页面首帧判断资产在不在',
    'ui/screen/apppicker/AppList.kt': '整段只在 AppListLoader 的 Dispatchers.IO 里被调用',
    'ui/state/PageLoadSnapshot.kt': '页面首帧一次性读三份小文件；异步化会让页面先白一帧，收益不抵',
    'ui/state/UiState.kt': '整份界面偏好只有几十字节，拨开关时一次整份写回（低频）',
    'ui/state/UpdateState.kt': '每次「检查更新」一次 getApplicationInfo（低频）',
}

#  **已复核的热路径分配**：(文件, 类别) → 理由。只放「确实低频」或「必须拷一份」的那几处。
HOT_ALLOW = {
    ('inject/input/GestureDetector.kt', '序列/集合流水线'): 'reset(null) 只在配置变化时跑，且必须拷快照才敢边遍历边重排',
    ('inject/input/HeldPresses.kt', '序列/集合流水线'): 'drop(null) 同上，只在配置变化 / 组合接管时跑',
    ('inject/input/GestureForwarder.kt', '字符串模板'): 'guard 的说明文字一次手势拼一条（判据日志）',
    ('inject/input/ComboDetector.kt', '序列/集合流水线'): 'upgradeTo 只在「小组合升级成大组合」那一下跑（三颗以上成立时），不是每次按压；Combo 要持有 List<KeyId> 供后续遍历，必须拷一份',
}

# ── 判据 3：组合期重算（只报数）───────────────────────────────────────────────
RECALC = re.compile(r'\.(filter|map|mapNotNull|sortedBy|sortedWith|groupBy|associate|toList)\s*[{<(]|buildList\s*\{')
COMPOSABLE = re.compile(r'@Composable')


def read(path):
    return io.open(path, encoding='utf-8').read()


#  三个扫描各扫了多少个 .kt —— 汇总在 main 里判「这条闸还在不在」。
#  目录被改名 / 挪走时 `os.walk` 静默给空，三道判据全 0 处、照样打「通过」。
SCANNED = 0


def scan_blocking():
    global SCANNED
    hits = []
    for dirpath, _, files in os.walk(UI_ROOT):
        for f in sorted(files):
            if not f.endswith('.kt'):
                continue
            SCANNED += 1
            p = os.path.join(dirpath, f)
            lines = read(p).split('\n')
            rel = p.replace(os.sep, '/').split('com/lookie/opluskey/')[-1]
            if rel in MT_ALLOW:
                continue
            for i, line in enumerate(lines):
                if line.lstrip().startswith(('//', '*', '/*')):
                    continue
                for label, pattern in BLOCKING:
                    if not re.search(pattern, line):
                        continue
                    # 往上 20 行里有没有「已挪到后台」的记号
                    window = '\n'.join(lines[max(0, i - 20): i + 1])
                    if BACKGROUND.search(window):
                        continue
                    hits.append((p, i + 1, label, line.strip()[:90]))
    return hits


def scan_allocations():
    hits = []
    for rel in HOT_FILES:
        p = os.path.join('app/src/main/java/com/lookie/opluskey', rel)
        if not os.path.exists(p):
            hits.append((p, 0, '文件不在了', '热路径清单要跟着改'))
            continue
        lines = read(p).split('\n')
        for i, line in enumerate(lines):
            stripped = line.lstrip()
            # 注释不算：这个仓库的注释里到处是反引号包着的示例代码
            if stripped.startswith(('//', '*', '/*')):
                continue
            if LOG_LINE.search(line):
                continue
            #  续行不算：多行日志 / 多行字符串的第二行起是 `"…${x}…" +`，它本身不分配，
            #  分配发生在整条语句上（上面那一行已经按日志跳过）。
            if stripped.startswith('"'):
                continue
            #  类字段的初始化只跑一次（对象在装配时建好），不算「每次按压分配」
            if re.match(r'\s{4}(private )?(val|var) ', line) and 'fun ' not in line:
                continue
            for label, pattern in ALLOCATION:
                if (rel, label) in HOT_ALLOW:
                    continue
                if re.search(pattern, line):
                    hits.append((p, i + 1, label, stripped[:90]))
    return hits


def scan_recalc():
    """组合期重算：按 @Composable 函数统计（函数体内有重算式、且整段没有 remember）。"""
    global SCANNED
    sites = []
    for dirpath, _, files in os.walk(UI_ROOT):
        for f in sorted(files):
            if not f.endswith('.kt'):
                continue
            SCANNED += 1
            p = os.path.join(dirpath, f)
            lines = read(p).split('\n')
            for i, line in enumerate(lines):
                if not COMPOSABLE.search(line):
                    continue
                # 函数体：从下一个 `{` 开始，按花括号配平取到结尾（最多 200 行）
                body, depth, started = [], 0, False
                for k in range(i, min(i + 200, len(lines))):
                    body.append(lines[k])
                    depth += lines[k].count('{') - lines[k].count('}')
                    if '{' in lines[k]:
                        started = True
                    if started and depth <= 0:
                        break
                text = '\n'.join(body)
                code = '\n'.join(l for l in body if not l.lstrip().startswith(('//', '*', '/*')))
                found = len(RECALC.findall(code))
                if found and 'remember' not in text:
                    name = next(
                        (l.strip() for l in body if 'fun ' in l),
                        '?',
                    )
                    sites.append((p, i + 1, name[:70], found))
    return sites


def main():
    report_only = '--report' in sys.argv
    fail = 0

    blocking = scan_blocking()
    print(f'[1] 主线程阻塞（硬判据）：{len(blocking)} 处')
    for p, line, label, snippet in blocking:
        print(f'    {p}:{line}  {label}  —— {snippet}')
    if blocking:
        fail = 1

    allocs = scan_allocations()
    print(f'[2] 注入侧热路径分配（硬判据）：{len(allocs)} 处')
    for p, line, label, snippet in allocs:
        print(f'    {p}:{line}  {label}  —— {snippet}')
    if allocs:
        fail = 1

    recalc = scan_recalc()
    print(f'[3] 组合期重算（趋势线，不判定）：{len(recalc)} 处')
    for p, line, name, count in recalc:
        print(f'    {p}:{line}  {count} 式  {name}')

    if fail and not report_only:
        print('\n性能审计未通过 —— 上面的硬判据要就地改掉，别往基线里加。')
        return 1
    if fail:
        #  `--report` 只该用来出趋势线，不该让硬判据看起来是绿的 —— 上面那几行是红的，
        #  这里再说一句，免得流水线里被当成「通过」。
        print('\n[--report 模式] 硬判据**未通过**（上面标了处数）：这一份只用于出趋势，不是绿灯。')
        return 0
    if SCANNED == 0:
        print('\n一个 .kt 也没扫到 —— 目录改名了？这条闸已失效，按失败处理。')
        return 1
    print('\n性能审计通过。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
