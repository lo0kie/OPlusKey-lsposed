"""性能审计 —— 把「常见性能问题」变成可 grep 的判据。

五道闸，前四道是**硬判据**（有命中就红），第五道只报数（趋势线，不判定）：

1. 主线程阻塞：界面层里那些会做 IO / 跨进程查询 / 等锁的调用，**不许**出现在主线程路径上
   （必须包在 `withContext(Dispatchers.IO)` 之类的后台段里）。
2. 注入侧热路径零分配：按键判定链跑在 `InputReader` 线程上、每次按压都要过一遍 ——
   那里每多一次 `HashSet` / 字符串拼接 / 正则编译，都是按次计的开销。
3. 组合期重算：`@Composable` 函数体里对状态做 `filter / map / sortedBy / toList / buildList`，
   且该函数里没有 `remember` —— 每帧重组都重算。这一档**只报数**：很多是「行表按状态派生」
   的正常写法，强行 remember 反而会拿旧状态。
4. 文件指纹单一实现：读缓存按「修改时间 + 长度 + inode」命中，`Os.stat` 的返回结构里本来就有
   这三项 —— 一次 stat 取齐。三处各写一遍（主配置 / 命令库 / 变量表）时每处都是三次系统调用，
   而 `ConfigStore.read()` 在宿主按键路径上每条按键要被问好几次。判据：`lastModified()` /
   `Os.stat` 只许出现在 `core/config/ConfigStore.kt` 的 `fileStamp` 里。
5. 详细日志必须惰性：`XLog.d` 的 `String` 重载在 release 下**函数体被折掉、实参照算**
   （`VERBOSE_LOG` 是编译期常量，只有 lambda 版本能把拼串一起消掉）。注入侧与按键热路径上
   一律用 `XLog.d { … }`，否则每条事件都白拼一串字符串。

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
    'inject/key/SideKeyInterceptor.kt',
    'inject/input/GestureDetector.kt',
    'inject/input/ComboDetector.kt',
    'inject/input/HeldPresses.kt',
    'inject/wire/GestureForwarder.kt',
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
    'ui/wire/AppNaming.kt': '应用名有进程内缓存（appLabelCache），只有首次未命中才问 PM',
    'ui/chrome/PageControl.kt': '重启页进程那条路：睡眠在独立线程上，/proc 扫描只在重启时跑一次（页面随后就没了）',
    'ui/hook/LogPull.kt': '导出日志是一次性动作，等待在导出线程上',
    'ui/hook/ConfigTransfer.kt': '导入配置是一次性动作，文件在选中之后那一段读',
    'ui/hook/SettingsPageHooks.kt': '一次 File.exists 的 stat，页面首帧判断资产在不在',
    'ui/screen/apppicker/AppList.kt': '整段只在 AppListLoader 的 Dispatchers.IO 里被调用',
    'ui/state/page/PageLoadSnapshot.kt': '页面首帧一次性读三份小文件；异步化会让页面先白一帧，收益不抵',
    'ui/state/page/UiState.kt': '整份界面偏好只有几十字节，拨开关时一次整份写回（低频）',
    'ui/state/host/UpdateState.kt': '每次「检查更新」一次 getApplicationInfo（低频）',
}

#  **已复核的热路径分配**：(文件, 类别) → 理由。只放「确实低频」或「必须拷一份」的那几处。
HOT_ALLOW = {
    ('inject/input/GestureDetector.kt', '序列/集合流水线'): 'reset(null) 只在配置变化时跑，且必须拷快照才敢边遍历边重排',
    ('inject/input/HeldPresses.kt', '序列/集合流水线'): 'drop(null) 同上，只在配置变化 / 组合接管时跑',
    ('inject/wire/GestureForwarder.kt', '字符串模板'): 'guard 的说明文字一次手势拼一条（判据日志）',
    ('inject/input/ComboDetector.kt', '序列/集合流水线'): 'upgradeTo 只在「小组合升级成大组合」那一下跑（三颗以上成立时），不是每次按压；Combo 要持有 List<KeyId> 供后续遍历，必须拷一份',
    ('inject/key/SideKeyInterceptor.kt', '序列/集合流水线'): '只在与组合有关的那本账 reset 时跑（配置变化 / 撤销待定手势），必须拷快照才敢边遍历边重排 —— 与 GestureDetector.reset 同一情形',
    ('inject/input/ComboDetector.kt', '字符串模板'): '`describe` 是自检 / 诊断通道把结论翻成人话的那一支，不在按键路径上（它只是和判定器同处一个文件）',
}

# ── 判据 3：组合期重算（只报数）───────────────────────────────────────────────
RECALC = re.compile(r'\.(filter|map|mapNotNull|sortedBy|sortedWith|groupBy|associate|toList)\s*[{<(]|buildList\s*\{')
COMPOSABLE = re.compile(r'@Composable')

# ── 判据 4：文件指纹单一实现 ─────────────────────────────────────────────────
# 「修改时间 + 长度 + inode」这套指纹只许有一份实现（一次 stat 取齐三项），其余调用点一律
# 走 `fileStamp` / `FileStamp.sameAs`。判据落在「谁直接碰了这几个系统调用」上。
STAMP_FILE = 'core/src/main/java/com/lookie/opluskey/core/config/ConfigStore.kt'
STAMP_CALLS = re.compile(r'\.lastModified\s*\(\s*\)|\bOs\s*\.\s*stat\s*\(')

# ── 判据 5：详细日志必须惰性 ─────────────────────────────────────────────────
# `XLog.d("…$…")` / `XLog.i("…")`：`String` 重载在 release 下只折掉函数体，实参照样求值。
# 注入侧整棵树都算热路径（它跑在 system_server 与桌面进程里），另外再加上那份热路径清单。
LAZY_LOG = re.compile(r'\bXLog\s*\.\s*[di]\s*\(\s*"')
# 正则只许在**顶层常量**里编译一次（`private val X = Regex(…)`）；写在表达式里就是每次重编。
REGEX_CALL = re.compile(r'\bRegex\s*\(')
REGEX_DECL = re.compile(r'^\s*(private\s+|internal\s+)?val\s+\w+[^=]*=\s*Regex\s*\(')
# 声明换行写（`private val X =` 换行再 `Regex(…)`）也算顶层常量：判据看的是「上一行是不是
#  以 `=` 收尾的属性声明」，而不是同一行里有没有 `val`。
REGEX_DECL_HEAD = re.compile(r'^\s*(private\s+|internal\s+)?val\s+\w+[^=]*=\s*$')
SCAN_ROOTS = [
    ('core/src/main/java', None),
    ('app/src/main/java', None),
]


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


def scan_stamp():
    """判据 4：指纹只许在 [STAMP_FILE] 里取。"""
    global SCANNED
    hits = []
    for root, _ in SCAN_ROOTS:
        for dirpath, _, files in os.walk(root):
            for f in sorted(files):
                if not f.endswith('.kt'):
                    continue
                SCANNED += 1
                p = os.path.join(dirpath, f).replace(os.sep, '/')
                if p == STAMP_FILE:
                    continue
                for i, line in enumerate(read(p).split('\n')):
                    if line.lstrip().startswith(('//', '*', '/*')):
                        continue
                    if STAMP_CALLS.search(line):
                        hits.append((p, i + 1, '指纹自己算', line.strip()[:90]))
    return hits


def scan_lazy_log():
    """判据 5：注入侧与热路径清单里的详细日志必须是 lambda 版。"""
    hits = []
    for dirpath, _, files in os.walk(INJECT_ROOT):
        for f in sorted(files):
            if not f.endswith('.kt'):
                continue
            p = os.path.join(dirpath, f)
            for i, line in enumerate(read(p).split('\n')):
                if line.lstrip().startswith(('//', '*', '/*')):
                    continue
                if LAZY_LOG.search(line):
                    hits.append((p, i + 1, '急求值日志', line.strip()[:90]))
    for rel in HOT_FILES:
        p = os.path.join('app/src/main/java/com/lookie/opluskey', rel)
        if not os.path.exists(p):
            continue
        for i, line in enumerate(read(p).split('\n')):
            if line.lstrip().startswith(('//', '*', '/*')):
                continue
            if LAZY_LOG.search(line):
                hits.append((p, i + 1, '急求值日志', line.strip()[:90]))
    return hits


def scan_regex():
    """判据 5 的第二半：正则只许在顶层 `val` 里编译一次。"""
    global SCANNED
    hits = []
    for root, _ in SCAN_ROOTS:
        for dirpath, _, files in os.walk(root):
            for f in sorted(files):
                if not f.endswith('.kt'):
                    continue
                p = os.path.join(dirpath, f)
                lines = read(p).split('\n')
                for i, line in enumerate(lines):
                    if line.lstrip().startswith(('//', '*', '/*')):
                        continue
                    if not REGEX_CALL.search(line):
                        continue
                    if REGEX_DECL.search(line):
                        continue
                    # 声明换行写的形态：往上第一行非空行是 `val X =`
                    prev = next((l for l in reversed(lines[:i]) if l.strip()), '')
                    if REGEX_DECL_HEAD.search(prev):
                        continue
                    hits.append((p, i + 1, '现场编译正则', line.strip()[:90]))
    return hits


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

    stamp = scan_stamp()
    print(f'[4] 文件指纹单一实现（硬判据）：{len(stamp)} 处')
    for p, line, label, snippet in stamp:
        print(f'    {p}:{line}  {label}  —— {snippet}')
    if stamp:
        fail = 1

    lazy = scan_lazy_log()
    print(f'[5] 详细日志惰性（硬判据）：{len(lazy)} 处')
    for p, line, label, snippet in lazy:
        print(f'    {p}:{line}  {label}  —— {snippet}')
    if lazy:
        fail = 1

    regex = scan_regex()
    print(f'[6] 正则只编译一次（硬判据）：{len(regex)} 处')
    for p, line, label, snippet in regex:
        print(f'    {p}:{line}  {label}  —— {snippet}')
    if regex:
        fail = 1

    if fail and not report_only:
        print('\n性能审计未通过 —— 上面的硬判据要就地改掉，别往基线里加。')
        return 1
    if fail:
        #  `--report` 只用来出趋势线，但**硬判据失败仍然要失败**：退出码是这条闸对流水线
        #  说的唯一一句话，返回 0 就等于把「红的」发成绿灯。
        print('\n[--report 模式] 硬判据**未通过**（上面标了处数）：趋势照出，退出码仍是失败。')
        return 1
    if SCANNED == 0:
        print('\n一个 .kt 也没扫到 —— 目录改名了？这条闸已失效，按失败处理。')
        return 1
    print('\n性能审计通过。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
