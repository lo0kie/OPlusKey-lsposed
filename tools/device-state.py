"""把「模块到底在不在跑」拆成三层口径，一次全打出来。

三层各自回答一个问题，缺一条就会得出相反结论：

- **时间线**：这台机器这一轮开机是什么时候、中间有没有再重启过。判据用
  `settings get global boot_count`（单调计数，跨重启保留）+ `/proc/uptime`。
  注意 `/proc/uptime` 是 `CLOCK_BOOTTIME`（含休眠），所以「开机时刻 = 设备当前 epoch − uptime」，
  只能取到秒级，别拿它和 `ps` 的 `ETIME` 之外的时钟混用。
- **意图**：用户勾了什么 —— 只存在于 `modules_config.db` 的 `modules` / `scope` 两张表。
  设备上没有 `sqlite3`，只能拉回本地查；**必须 db + `-wal` + `-shm` 一次打包**，
  少一个就会读到上一版的值（详见 `docs/device-findings.md` 六之三）。
- **事实**：这一轮到底注入没有 —— 只看 `/data/adb/lspd/log/modules_<开机会话>.log` 里
  有没有 `(进程名)[<模块包名>` 的行。库里的 `scope` 只代表「勾了」，日志才代表「真跑起来了」。

用法：
    python tools/device-state.py
    python tools/device-state.py --port 5038 --module io.github.lo0kie.opluskey
"""

import argparse
import io
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import tempfile

DEFAULT_MODULE = 'io.github.lo0kie.opluskey'
CONFIG_DIR = '/data/adb/lspd/config'
LOG_DIR = '/data/adb/lspd/log'
LSPCTL = '/data/adb/modules/zygisk_lsposed/lspctl'


def adb(args, port, timeout=25, text=True):
    env = dict(os.environ, ANDROID_ADB_SERVER_PORT=str(port))
    p = subprocess.run(
        ['adb'] + args,
        capture_output=True,
        timeout=timeout,
        env=env,
    )
    if text:
        return (p.returncode, p.stdout.decode('utf-8', 'replace'), p.stderr.decode('utf-8', 'replace'))
    return p.returncode, p.stdout, p.stderr.decode('utf-8', 'replace')


def shell(cmd, port, timeout=25):
    return adb(['shell', cmd], port, timeout)


def su(cmd, port, timeout=25):
    return adb(['shell', 'su', '-c', cmd], port, timeout)


def section(title):
    print()
    print('== ' + title + ' ==')


def read_timeline(port):
    out = {}
    rc, boot_count, _ = shell('settings get global boot_count', port)
    out['boot_count'] = boot_count.strip() if rc == 0 else '?'
    rc, uptime, _ = shell('cat /proc/uptime', port)
    parts = uptime.split()
    out['uptime'] = float(parts[0]) if parts else None
    rc, now, _ = shell('date +%s', port)
    out['now'] = int(now.strip()) if now.strip().isdigit() else None
    rc, booted, _ = shell('getprop sys.boot_completed', port)
    out['boot_completed'] = booted.strip()
    if out['uptime'] is not None and out['now'] is not None:
        out['boot_epoch'] = int(out['now'] - out['uptime'])
        rc, pretty, _ = shell('date -d @%d "+%%Y-%%m-%%d %%H:%%M:%%S"' % out['boot_epoch'], port)
        out['boot_at'] = pretty.strip()
    return out


def read_framework(port):
    out = {}
    rc, text, err = su('sh %s status' % LSPCTL, port)
    out['status_raw'] = (text + err).strip()
    m = re.search(r'System server:\s*(\S+)', out['status_raw'])
    out['system_server'] = m.group(1) if m else '?'
    m = re.search(r'Modules:\s*(\d+) installed,\s*(\d+) enabled', out['status_raw'])
    if m:
        out['modules_installed'] = int(m.group(1))
        out['modules_enabled'] = int(m.group(2))
    rc, ls, _ = su('ls -t %s | head -1' % LOG_DIR, port)
    out['log_name'] = ls.strip()
    m = re.match(r'modules_(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)', out['log_name'])
    out['session'] = m.group(1) if m else '?'
    return out


def read_facts(port, module, log_name):
    """事实层：这一轮开机各进程的模块日志行数。没有行 = 没注入。"""
    if not log_name:
        return {}
    rc, data, err = adb(['exec-out', 'su', '-c', 'cat %s/%s' % (LOG_DIR, log_name)], port, text=False)
    text = data.decode('utf-8', 'replace')
    counts = {}
    first = {}
    for line in text.splitlines():
        m = re.search(r'\(([A-Za-z0-9._]+)\)\[%s' % re.escape(module), line)
        if not m:
            continue
        proc = m.group(1)
        counts[proc] = counts.get(proc, 0) + 1
        if proc not in first:
            # 行首是 `[ 时间 pid: tid: tid 级别/标签 ] (进程)[包名,标签,id,0,1] `，把两段前缀都切掉
            body = re.sub(r'^.*?\)\[[^\]]*\]\s*', '', line)
            first[proc] = body[:110]
    return {'counts': counts, 'first': first}


def read_intent(port, module):
    """意图层：把 db + -wal + -shm 一次打包拉回，再本地查。"""
    rc, data, err = adb(
        [
            'exec-out',
            'su',
            '-c',
            'tar cf - -C %s modules_config.db modules_config.db-wal modules_config.db-shm'
            % CONFIG_DIR,
        ],
        port,
        timeout=40,
        text=False,
    )
    if not data:
        return {'error': '取库失败：' + err.strip()}
    tmp = tempfile.mkdtemp(prefix='lspd-db-')
    try:
        with tarfile.open(fileobj=io.BytesIO(data)) as tar:
            try:
                tar.extractall(tmp, filter='data')
            except TypeError:
                # 3.12 之前没有 filter 参数
                tar.extractall(tmp)
        db = sqlite3.connect(os.path.join(tmp, 'modules_config.db'))
        cur = db.cursor()
        apk_path = cur.execute(
            'select apk_path from modules where module_pkg_name=?', (module,)
        ).fetchone()
        state = cur.execute(
            'select enabled from modules_state where module_pkg_name=?', (module,)
        ).fetchone()
        scope = cur.execute(
            'select app_pkg_name from scope where module_pkg_name=? order by app_pkg_name',
            (module,),
        ).fetchall()
        db.close()
        return {
            'apk_path': apk_path[0] if apk_path else None,
            'enabled': state[0] if state else None,
            'scope': [r[0] for r in scope],
        }
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def read_install(port, module):
    rc, path, _ = shell('pm path %s' % module, port)
    real = path.strip().replace('package:', '') or None
    size = None
    if real:
        rc, out, _ = shell('wc -c < %s' % real, port)
        if out.strip().isdigit():
            size = int(out.strip())
    return {'pm_path': real, 'apk_size': size}


def diagnose(intent, facts, install, framework, timeline):
    """把三层口径对成一个结论。判据与修法都对应 docs/device-findings.md 的既有条目。"""
    notes = []
    if intent.get('error'):
        notes.append(('库读不出来', intent['error']))
        return notes
    if not intent.get('enabled'):
        notes.append(('未启用', '库里 enabled=0（或没有该行）—— 先在框架管理器里启用本模块'))
        return notes
    scope = intent.get('scope') or []
    if 'system' not in scope:
        notes.append(('作用域缺 system', '侧键接管要注入 system_server，必须勾「系统框架」'))

    counts = facts.get('counts') or {}
    for proc, why in (
        ('system', '注入侧（侧键接管、system_server）'),
        ('com.oplus.gesture', '宿主侧（手势劫持与执行）'),
        ('com.oplus.gesture.ui', '设置页替换'),
    ):
        if counts.get(proc):
            notes.append(('OK ' + proc, '%s：本轮 %d 行' % (why, counts[proc])))
        elif proc in scope or proc != 'system':
            notes.append(('缺 ' + proc, '%s：本轮 0 行' % why))

    if install.get('pm_path') and intent.get('apk_path'):
        if install['pm_path'] != intent['apk_path']:
            notes.append(
                (
                    '库里 apk_path 过期',
                    '六之三：库=%s，真机=%s —— 把库改成真机值再重启'
                    % (intent['apk_path'], install['pm_path']),
                )
            )
        else:
            notes.append(('apk_path 一致', '库里与 pm path 逐字相同'))
    #  `read_install()` 返回的键叫 `pm_path`（见上面那条比对）—— 这里原来取的是 `apk_path`，
    #  恒为 None ⇒ 这条结论永远不成立（「路径对的但 system 没注入」在任何情况下都不会出现）。
    if not counts.get('system') and 'system' in scope and install.get('pm_path') == intent.get('apk_path'):
        notes.append(
            (
                '路径对的但 system 没注入',
                '六之三之一：看安装方式（增量安装读不到整份包）；或装包时 daemon 没在跑 ⇒ 库没被刷新，'
                '重启这一轮必然跳过 —— 再整机重启一次',
            )
        )
    if framework.get('system_server') and framework['system_server'] != 'connected':
        notes.append(('框架没连上', 'lspctl status: System server: %s' % framework['system_server']))
    if timeline.get('boot_completed') != '1':
        notes.append(('开机未完成', 'sys.boot_completed=%s' % timeline.get('boot_completed')))
    return notes


def main():
    ap = argparse.ArgumentParser(description='一次拿到 LSPosed 作用域与重启状态（三层口径）')
    ap.add_argument('--port', type=int, default=int(os.environ.get('ANDROID_ADB_SERVER_PORT', '5037')))
    ap.add_argument('--module', default=DEFAULT_MODULE)
    args = ap.parse_args()

    if shutil.which('adb') is None:
        print('找不到 adb')
        return 2
    rc, devices, _ = adb(['devices'], args.port)
    online = [l.split('\t')[0] for l in devices.splitlines()[1:] if l.endswith('\tdevice')]
    print('设备：', ', '.join(online) if online else '（无）')
    if not online:
        print('没有在线设备 —— 先确认 adb 端口（本机被 WSA 占了 5037，用 --port 5038）')
        return 2

    timeline = read_timeline(args.port)
    section('时间线（重启状态）')
    print('boot_count     :', timeline.get('boot_count'), '（单调计数：两次采样不同即中间重启过）')
    if timeline.get('uptime') is not None:
        print('uptime         : %.1f 秒' % timeline['uptime'])
    print('开机时刻       :', timeline.get('boot_at'), '（= 设备当前 epoch − uptime，秒级）')
    print('boot_completed :', timeline.get('boot_completed'))

    framework = read_framework(args.port)
    section('框架')
    print(framework.get('status_raw', '').replace('\n', '\n' + ' ' * 0))
    print('开机会话       :', framework.get('session'), '（lspd 日志文件名的时间戳）')
    # 交叉校验：框架落第一个日志文件的时刻应当略晚于开机时刻（实测约 +10~25 秒）
    try:
        import datetime as _dt

        boot = _dt.datetime.strptime(timeline.get('boot_at'), '%Y-%m-%d %H:%M:%S')
        sess = _dt.datetime.strptime(framework.get('session'), '%Y-%m-%dT%H:%M:%S')
        diff = int((sess - boot).total_seconds())
        if diff < 0:
            print('落盘与开机相差 : %d 秒 —— 这份日志早于开机时刻，不属于本轮' % -diff)
        else:
            print('落盘与开机相差 : +%d 秒（正常约 10~25 秒）' % diff)
    except (TypeError, ValueError):
        pass

    facts = read_facts(args.port, args.module, framework.get('log_name'))
    section('事实（这一轮真正注入的进程）')
    print('日志           :', framework.get('log_name'))
    if not facts.get('counts'):
        print('本模块 0 行 —— 这一轮整条跳过')
    for proc, n in sorted(facts.get('counts', {}).items()):
        print('%-22s %4d 行  首条：%s' % (proc, n, facts['first'].get(proc, '')))

    intent = read_intent(args.port, args.module)
    install = read_install(args.port, args.module)
    section('意图（库里勾了什么）与真机安装')
    print('enabled        :', intent.get('enabled'))
    print('scope          :', intent.get('scope'))
    print('库里 apk_path  :', intent.get('apk_path'))
    print('真机 pm path   :', install.get('pm_path'))
    print('APK 字节数     :', install.get('apk_size'))

    section('结论')
    for head, body in diagnose(intent, facts, install, framework, timeline):
        print('%-24s %s' % (head, body))
    print()
    print('口径：库 = 意图，模块日志 = 事实，boot_count/uptime = 时间线。三者交叉才下结论。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
