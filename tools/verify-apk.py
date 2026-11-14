#!/usr/bin/env python3
"""发布包的验收底线（AGENTS.md 二、1.2）—— 三个工作流共用：`ci.yml` 在构建后验自己刚出的包，
`release.yml` / `publish.yml` 在发版前验要发出去的那几个包（它们可能是从 CI 那条 run 取来的）。

用法（仓库根）：`python3 tools/verify-apk.py [目录]`，目录默认 `dist`，里面放 `*-debug.apk`
与 `*-release*.apk`。路径相对仓库根 —— 下面有几项要回头读源码（`HostApp.PACKAGE`、
`applicationId`、字典文件）。

每个 `::error::` 是给 GitHub Actions 的注解，本地跑就是普通一行字。退出码 0 = 全过。

⚠️ 小节编号按**类别**排（包结构 / 注入侧依赖 / 入口类与包名 / 字典 / 封存），不按执行顺序 ——
   这是从原 shell 版本沿用下来的，`docs/architecture/decoupling.md` 会引用「(5) 注入侧」那一段。
"""

import fnmatch
import os
import re
import sys
import zipfile
from pathlib import Path

import _common as c

XPOSED = 'META-INF/xposed/'
HOSTAPP = 'core/src/main/java/com/lookie/opluskey/core/model/HostApp.kt'
MODULE_INFO = 'core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt'
BUILD_GRADLE = 'app/build.gradle.kts'
KEY_MODULE = 'app/src/main/java/com/lookie/opluskey/KeyModule.kt'
STRINGS_KT = 'core/src/main/java/com/lookie/opluskey/core/i18n/Strings.kt'
ZH = 'app/src/main/assets/locales/zh-CN.json'
EN = 'app/src/main/assets/locales/en.json'

# 注入侧不得引用的那几族：Compose / COUI / 图标库 / 界面层。
# 判据是「这个类会不会在 system_server 里被执行」：`inject/` 是注入侧本体，`core/` 与
# `bridge/`（不含 `bridge/host/`）是它依赖的共享层，同样在注入目标进程里执行，所以一并拦。
INJECTION_IMPORT = re.compile(
    r'^import (androidx|io\.github\.suqi8|io\.github\.lyxnx|com\.composables'
    r'|com\.lookie\.opluskey\.ui\b|com\.lookie\.opluskey\.ui\.)'
)
# `KeyModule.kt` 单列一档：它在注入侧会被加载，但它**允许** import `ui/`（界面 hook 只在设置页
# 那个进程分支里装，见它的类注释）—— 直接并进上面那圈扫描会误报。这里只禁「一加载就崩」的三类。
KEY_MODULE_IMPORT = re.compile(r'^import (androidx|io\.github\.suqi8|io\.github\.lyxnx|com\.composables)')

INJECTION_DIRS = [
    'app/src/main/java/com/lookie/opluskey/inject',
    'app/src/main/java/com/lookie/opluskey/bridge',
    'core/src/main/java/com/lookie/opluskey',
    'core/src/debug/java/com/lookie/opluskey',
    'core/src/releaseLog/java/com/lookie/opluskey',
    'core/src/release/java/com/lookie/opluskey',
]
# 只排除 `bridge/host/` 这一条路径：按目录名排除（`--exclude-dir=host`）会把将来任何同名目录
# （例如 `core/.../host/`）一起静默漏掉 —— 那些目录仍在注入侧执行。
BRIDGE_HOST = 'app/src/main/java/com/lookie/opluskey/bridge/host'


def disp(path):
    """打日志用的路径：在仓库里就给相对路径（与 CI 里的 `dist/xxx.apk` 一致），
    仓库外就按调用方给的原样打（不做 resolve —— 那会把它变成 Windows 绝对路径）。"""
    p = Path(path)
    try:
        return p.resolve().relative_to(c.ROOT).as_posix()
    except ValueError:
        return p.as_posix()


def read_text(path):
    return (c.ROOT / path).read_text(encoding='utf-8')


def first_capture(text, pattern):
    m = re.search(pattern, text)
    return m.group(1) if m else ''


def version_key(name):
    """`sort -V` 的替身：把 `36.0.0` 拆成 (36, 0, 0) 再比。"""
    return tuple(int(x) for x in re.findall(r'\d+', name))


def find_aapt2():
    """按 环境变量 → PATH → SDK build-tools 三档找 aapt2，取 build-tools 里**最高**那个版本。"""
    env = (os.environ.get('AAPT2') or '').strip()
    if env:
        return env
    found = c.which('aapt2')
    if found:
        return found
    for var in ('ANDROID_HOME', 'ANDROID_SDK_ROOT'):
        sdk = os.environ.get(var)
        if not sdk:
            continue
        build_tools = Path(sdk) / 'build-tools'
        if not build_tools.is_dir():
            continue
        cands = []
        for d in build_tools.iterdir():
            if not d.is_dir():
                continue
            for name in ('aapt2', 'aapt2.exe'):
                p = d / name
                if p.is_file():
                    cands.append(p)
        if cands:
            return str(max(cands, key=lambda p: version_key(p.parent.name)))
    return None


# ── (1)(2)(3)(4)(6)(7)(8)(9) 逐个 APK 的包结构 / 清单 / 包名 ───────────────────

def check_apk(apk, checks):
    print(f'校验 {disp(apk)}')
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()

        def entry(name):
            return z.read(name).decode('utf-8', 'replace')

        # (1) META-INF/xposed 三件套齐全（LSPosed 的识别与上架校验都靠它们）
        for f in ('java_init.list', 'scope.list', 'module.prop'):
            full = XPOSED + f
            if full in names:
                checks.ok(full)
            else:
                checks.bad(f'{disp(apk)} 缺 {full}')

        # (2) scope.list 首行必须是 system —— 写成 android 的话模块装了也不注入 system_server
        #   缺条目时先记一条「缺」再跳过：直接 `entry()` 会在 zip 里找不到这个名字时抛
        #   KeyError，整条校验**崩掉**（后面的条目一条都没跑，退出码也不是可读的失败原因）。
        if XPOSED + 'scope.list' not in names:
            checks.bad(f'{disp(apk)} 缺 {XPOSED}scope.list（跳过首行校验）')
            scope_lines = ['']
            scope_head = ''
        else:
            scope_lines = entry(XPOSED + 'scope.list').replace('\r', '').split('\n')
            scope_head = scope_lines[0]
            if scope_head == 'system':
                checks.ok('scope.list 首行 = system')
            else:
                checks.bad(f"{disp(apk)} 的 scope.list 首行须为 system（实际 '{scope_head}'）")

        # (3) module.prop：两条 API 档位要有值、是整数，且 target ≥ min
        #   **这个文件只带 LSPosed 那几项**（min / target / staticScope）—— `id` / `name` /
        #   `version` / `versionCode` 在根 `module.prop` 里，别在这里要求它们。
        #   只查「键在不在」时，`minApiVersion=`（空值）照样全绿，而 LSPosed 读到空值会拒绝加载。
        prop = entry(XPOSED + 'module.prop').replace('\r', '')
        prop_map = {}
        for line in prop.split('\n'):
            if '=' in line:
                k, v = line.split('=', 1)
                prop_map[k.strip()] = v.strip()
        api = {}
        for k in ('minApiVersion', 'targetApiVersion'):
            raw = prop_map.get(k, '')
            if raw.isdigit():
                api[k] = int(raw)
                checks.ok(f'module.prop 的 {k}={raw}')
            else:
                checks.bad(f'{disp(apk)} 的 module.prop 里 {k}= 缺值或不是整数（实际 {raw!r}）')
        #  target < min 时管理器会认为这版模块在本机框架上跑不了（装上去直接不加载）。
        if len(api) == 2 and api['targetApiVersion'] < api['minApiVersion']:
            checks.bad(
                f'{disp(apk)} 的 module.prop 里 targetApiVersion='
                f"{api['targetApiVersion']} < minApiVersion={api['minApiVersion']}",
            )

        # (4) 清单里的界面入口**只放行那一个跳板**：`EntryActivity`（无界面，点了转宿主设置页）。
        #    多一个就是「模块自带界面」或多一条入口；少一个则管理器那颗 FAB 不显示，
        #    而用户装完第一反应就是点它，没反应等于找不到入口。
        #    只能查 APK 里的二进制清单：源清单注释会被 merger 抄进合并清单，按字面查会误报。
        #    **必须数元素、不能数字符串**：AXML 的字符串池里同一个字符串只存一份 ——
        #    计数天生封顶为 1，再加一个 `<activity>` 照样全绿。`aapt2 dump xmltree` 打的是
        #    **元素树**（每个节点一行 `E: activity (line=…)`），这才是判据。
        aapt2 = find_aapt2()
        if not aapt2:
            # 查不了必须报错而非静默放过 —— 这道闸存在的意义就是「多一个 <activity> 会被拦」
            checks.bad('runner 上没有 aapt2（Android SDK build-tools），清单入口这项没法校验')
        else:
            dump = c.run([aapt2, 'dump', 'xmltree', '--file', 'AndroidManifest.xml', apk])
            xmltree = dump.stdout if dump.returncode == 0 else ''
            lines = xmltree.split('\n')

            def count(pattern):
                rx = re.compile(pattern)
                return sum(1 for line in lines if rx.search(line))

            entry_n = count(r'MODULE_SETTINGS')
            activities = count(r'^ *E: activity \(')
            aliases = count(r'^ *E: activity-alias \(')
            launchers = count(r'category\.(LAUNCHER|INFO)')
            jumper = count(r'opluskey\.EntryActivity')

            # 跳板自己那几条属性：只在 EntryActivity 这个节点附近取，避免别处同名属性凑数。
            # 前 3 行是为了带上前面的 `theme`（aapt2 按属性名排序输出，theme 排在 name 之前）。
            block = []
            for i, line in enumerate(lines):
                if re.search(r'opluskey\.EntryActivity', line):
                    block.extend(lines[max(0, i - 3):i + 9])
            block_text = '\n'.join(block)

            def count_block(pattern):
                return len(re.findall(pattern, block_text))

            exported = count_block(r':exported\(0x01010010\)=true')
            nohistory = count_block(r':noHistory\(0x0101022d\)=true')
            theme = count_block(r':theme\(0x01010000\)=@0x01030010')

            if (entry_n, activities, aliases, launchers, jumper,
                    exported, nohistory, theme) == (1, 1, 0, 0, 1, 1, 1, 1):
                checks.ok('清单入口只有 EntryActivity 这一个跳板'
                          '（MODULE_SETTINGS×1 / activity×1 / alias×0 / LAUNCHER·INFO×0 / '
                          'exported×1 / noHistory×1 / 透明主题×1）')
            else:
                checks.bad(
                    f'{disp(apk)} 的清单入口不合规：MODULE_SETTINGS={entry_n} activity={activities} '
                    f'alias={aliases} LAUNCHER/INFO={launchers} EntryActivity={jumper} '
                    f'exported={exported} noHistory={nohistory} theme={theme}（允许 1/1/0/0/1/1/1/1）'
                )

        # (6) java_init.list 声明的入口类必须**真的在 dex 里**：
        #    类改名 / 挪包之后 CI 原来照样全绿发版，而框架按名字实例化会失败 ——
        #    现象是「模块已启用、日志一行都没有」。
        entry_cls = entry(XPOSED + 'java_init.list').replace('\r', '').split('\n')[0]
        if not entry_cls:
            checks.bad(f'{disp(apk)} 的 java_init.list 是空的')
        else:
            desc = 'L' + entry_cls.replace('.', '/') + ';'
            #  直接在字节流里找（类名是纯 ASCII）：`strings` 是 binutils 的东西，精简镜像与
            #  Windows 的 Git Bash 上不一定有 —— 缺了它这条会变成**假红灯**。
            dexes = [n for n in names if fnmatch.fnmatch(n, 'classes*.dex')]
            blob = b''.join(z.read(n) for n in dexes)
            if desc.encode() in blob:
                checks.ok(f'java_init.list 的 {entry_cls} 在 dex 里')
            else:
                checks.bad(f'{disp(apk)} 的 java_init.list 声明了 {entry_cls}，但 dex 里找不到 {desc}')

        # (7) 作用域必须是构建期写死的那一档（`staticScope=true`）—— 「作用域写错 = 模块全绿但
        #     日志一行都没有」正是它守的东西。
        if prop_map.get('staticScope') == 'true':
            checks.ok('module.prop 有 staticScope=true')
        else:
            checks.bad(f'{disp(apk)} 的 module.prop 缺 staticScope=true'
                       '（作用域没写死 ⇒ 由用户在管理器里选）')

        # (8) scope.list 里的宿主包名必须与 `core/model/HostApp.kt` 的 `PACKAGE` 一致。
        #    scope.list 是资源文件，读不到 Kotlin 常量（那边用 `HostApp` 一处定义、代码全引它），
        #    所以只能在这里对一次账：两处不一致时模块装了、启用了、状态全绿，而设置页那一段
        #    **一行日志都没有**，从日志上看不出漏在哪一层。
        scope_host = scope_lines[1] if len(scope_lines) > 1 else ''
        if (c.ROOT / HOSTAPP).is_file():
            src_host = first_capture(read_text(HOSTAPP), r'const val PACKAGE = "([^"]+)"')
            if not src_host:
                checks.bad(f'从 {HOSTAPP} 里没读到 const val PACKAGE（这条校验会失效）')
            elif scope_host == src_host:
                checks.ok(f'scope.list 的宿主包名 = HostApp.PACKAGE（{src_host}）')
            else:
                checks.bad(f"{disp(apk)} 的 scope.list 第 2 行 '{scope_host}' ≠ "
                           f"HostApp.PACKAGE '{src_host}'")
        else:
            checks.bad(f'找不到 {HOSTAPP}（包名挪过？这条校验会失效）')

        # (9) 模块自己的包名也是两处硬编码：`ModuleInfo.PACKAGE` 与 `applicationId`。
        #    写错的表现是「按包名查自己的 APK」落空 —— 版本行空着、字典退回键名，
        #    而两处互不相识、编译期不会报错。
        appid = first_capture(read_text(BUILD_GRADLE), r'applicationId = "([^"]+)"')
        if (c.ROOT / MODULE_INFO).is_file():
            mi_pkg = first_capture(read_text(MODULE_INFO), r'const val PACKAGE = "([^"]+)"')
            if not appid or not mi_pkg:
                checks.bad(f"没读到 applicationId（'{appid}'）或 ModuleInfo.PACKAGE（'{mi_pkg}'），"
                           '这条校验会失效')
            elif appid == mi_pkg:
                checks.ok(f'ModuleInfo.PACKAGE = applicationId（{appid}）')
            else:
                checks.bad(f"ModuleInfo.PACKAGE '{mi_pkg}' ≠ applicationId '{appid}'")
        else:
            checks.bad(f'找不到 {MODULE_INFO}（包名挪过？这条校验会失效）')


# ── (5) 注入侧不得引用 androidx / COUI / 图标库 / 界面层 ──────────────────────

def check_injection_imports(checks):
    dirs = []
    for rel in INJECTION_DIRS:
        path = c.ROOT / rel
        if path.is_dir():
            dirs.append(path)
        else:
            checks.bad(f'找不到注入侧目录 {rel}（包名挪过？这条校验会失效）')

    if not (c.ROOT / BRIDGE_HOST).is_dir():
        checks.bad(f'找不到 {BRIDGE_HOST}/（进程归属分过档？那条精确排除会失效）')

    files = []
    for base in dirs:
        for path in base.rglob('*'):
            if not path.is_file() or path.suffix not in ('.kt', '.java'):
                continue
            if disp(path).startswith(BRIDGE_HOST + '/'):
                continue
            files.append(path)

    hits = []
    for path in files:
        for n, line in enumerate(path.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
            if INJECTION_IMPORT.match(line):
                hits.append(f'{disp(path)}:{n}:{line}')

    if hits:
        print('\n'.join(hits))
        checks.bad('注入侧（inject/ bridge/ core/，不含 bridge/host/）引用了 androidx / COUI —— '
                   '见 AGENTS.md 一、1')
    else:
        checks.ok('注入侧无 androidx / COUI import')

    # (5)' `KeyModule.kt` 单列一档（理由见上面那个正则的注释）。
    km = c.ROOT / KEY_MODULE
    if not km.is_file():
        checks.bad(f'找不到 {KEY_MODULE}（包名挪过？这条校验会失效）')
        return
    km_hits = []
    for n, line in enumerate(km.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        if KEY_MODULE_IMPORT.match(line):
            km_hits.append(f'{KEY_MODULE}:{n}:{line}')
    if km_hits:
        print('\n'.join(km_hits))
        checks.bad('KeyModule.kt 引用了 androidx / COUI / 图标库 —— 它在 system_server 里会被加载')
    else:
        checks.ok('KeyModule.kt 无 androidx / COUI / 图标库 import')


# ── (10) 字典覆盖 ─────────────────────────────────────────────────────────────

KEY_IN_SOURCE = re.compile(r'"([a-z0-9_.]+)"')
KEY_IN_JSON = re.compile(r'"([a-z0-9_.]+)":')


def json_keys(text):
    return {m.group(1) for m in KEY_IN_JSON.finditer(text)}


def placeholders(text):
    """每行的「键 → `%s` / `%d` 的出现顺序」。比的是**顺序 + 种类**：`%s…%d` 与 `%d…%s`
    数量相同也不能互换（运行期 `String.format` 会抛 `MissingFormatArgumentException`）。"""
    out = {}
    for line in text.splitlines():
        m = KEY_IN_JSON.search(line)
        if not m:
            continue
        #  **位置写法（`%1$s` / `%2$d`）也要认**：只认 `%s` 时，用位置参数的那条会被算成
        #  「这一行没有占位符」，与另一本字典里对应的那条对不上（真缺参时也放过去）。
        out[m.group(1)] = ''.join(re.findall(r'%(?:\d+\$)?[sd]', line))
    return out


def check_dictionaries(checks):
    for rel in (STRINGS_KT, ZH, EN):
        if not (c.ROOT / rel).is_file():
            checks.bad(f'找不到字典文件 {rel}（路径挪过？这条校验会失效）')
            return

    src_text, zh_text, en_text = (read_text(p) for p in (STRINGS_KT, ZH, EN))
    k_src = {m.group(1) for m in KEY_IN_SOURCE.finditer(src_text)}
    k_zh, k_en = json_keys(zh_text), json_keys(en_text)

    miss_zh, miss_en = sorted(k_src - k_zh), sorted(k_src - k_en)
    #  两份字典都要查「多出来的键」：只查 zh 的话，en 里多一条旧键（改名后留下的）永远没人发现
    #  —— 它不会显示，但下一次「按 zh 抄一遍」时会被当成有效键带回来。
    extra_zh, extra_en = sorted(k_zh - k_src), sorted(k_en - k_src)

    if miss_zh or miss_en or extra_zh or extra_en:
        if miss_zh:
            print('zh-CN.json 缺键：\n' + '\n'.join(miss_zh))
        if miss_en:
            print('en.json 缺键：\n' + '\n'.join(miss_en))
        if extra_zh:
            print('zh-CN.json 里有而 Strings.kt 没有的键：\n' + '\n'.join(extra_zh))
        if extra_en:
            print('en.json 里有而 Strings.kt 没有的键：\n' + '\n'.join(extra_en))
        checks.bad('字典与 Strings.kt 对不上（界面会直接显示键名）')
        return

    ph_zh, ph_en = placeholders(zh_text), placeholders(en_text)
    diffs = []
    for key in sorted(set(ph_zh) | set(ph_en)):
        if ph_zh.get(key) != ph_en.get(key):
            diffs.append(f'{key}: zh={ph_zh.get(key, "（缺）")!r} en={ph_en.get(key, "（缺）")!r}')
    if diffs:
        print('占位符不一致（zh-CN 与 en）：\n' + '\n'.join(diffs))
        checks.bad('两边的 %s / %d 数量或顺序对不上（运行期 String.format 会抛）')
    else:
        checks.ok(f'字典覆盖：Strings.kt / zh-CN.json / en.json 三边键一致（{len(k_src)} 条），'
                  '占位符也一致')


# ── (11) 封存 ────────────────────────────────────────────────────────────────

def check_seal(dist, checks):
    # release 是「发布形态」—— 不许出现明文收款码 / 名单，且必须有容器；debug 反过来。
    # 守的是「封存任务没跑起来 / 被绕过」—— 那种包照样能出、能装，只有把包解开才看得出来。
    # 通配 `*-release*.apk`：`releaseLog`（同一份正式包、只多开日志）也是发布形态。
    for apk in sorted(dist.glob('*-release*.apk')):
        print(f'校验封存 {disp(apk)}')
        with zipfile.ZipFile(apk) as z:
            names = z.namelist()
        leaked = [n for n in names if n.startswith('assets/donate/') or n == 'assets/donors.json']
        if leaked:
            print('\n'.join(leaked))
            checks.bad(f'{disp(apk)} 里有明文收款码 / 名单 —— 封存没生效')
        else:
            checks.ok('发布包里没有明文收款码 / 名单')
        if 'assets/sealed.bin' in names:
            checks.ok('发布包里有 assets/sealed.bin')
        else:
            checks.bad(f'{disp(apk)} 缺 assets/sealed.bin（封存容器没生成）')

    for apk in sorted(dist.glob('*-debug.apk')):
        with zipfile.ZipFile(apk) as z:
            names = z.namelist()
        if 'assets/donate/wechat.webp' in names:
            checks.ok('debug 包里是明文收款码（core/seal 那条资产目录接上了）')
        else:
            checks.bad(f'{disp(apk)} 里没有 assets/donate/wechat.webp —— debug 的明文资产没接上')


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else 'dist'
    dist = Path(arg)
    if not dist.is_absolute():
        dist = c.ROOT / dist

    checks = c.Checks('验收底线')
    apks = sorted(dist.glob('*.apk'))
    if not apks:
        # 原来 glob 不命中时 `unzip` 会拿字面量去开、然后每一条都报缺 —— 结果一样是红，
        # 但原因看不出来。这里直接说清。
        checks.bad(f'{disp(dist)} 里没有 .apk —— 这道闸会空转')
        return checks.finish()

    for apk in apks:
        check_apk(apk, checks)
    check_injection_imports(checks)
    check_dictionaries(checks)
    check_seal(dist, checks)
    return checks.finish()


if __name__ == '__main__':
    c.main(main)
