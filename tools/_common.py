"""tools/ 下几个脚本共用的四件小事：仓库根、GitHub Actions 注解、子进程、退出码。

## 为什么要有这一份

  这些脚本原先一半是 shell：`cd "$(dirname "$0")/.."` 各写一遍、`echo "::error::…"` 各写一遍、
  `$GITHUB_OUTPUT` 各拼一遍。换成 Python 之后如果照抄，同样的重复会跟着搬过来 ——
  所以先抽这一层，脚本里只剩「这条闸判什么」。

## 约定

  · 一律**只用标准库**：CI 是裸的 ubuntu + python3，本地是 Windows 上的 Python，
    没有 `pip install` 这一步（`dexdump.py` 那种明确声明依赖 `androguard` 的除外）。
  · 路径一律相对**仓库根**（`ROOT`），脚本从哪个目录被调用都一样。
  · GitHub Actions 只认 `::error::` 这个**前缀**；本地跑就是普通一行字，不必分支。
"""

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

# 仓库根 —— 本文件在 `tools/` 下，往上一级就是。
ROOT = Path(__file__).resolve().parent.parent

# Windows 上 `print` 默认写 CRLF，而 CI 是 Linux（LF）—— 统一成 LF，
# 本地跑出来的日志才能与 CI 的日志逐字对拍（否则每一行都「不一样」）。
try:
    sys.stdout.reconfigure(newline='\n')
except (AttributeError, ValueError):  # 被重定向成非常规流时就算了，不值得为它失败
    pass


# ── GitHub Actions 注解 ────────────────────────────────────────────────────────

def err(msg):
    """一行 `::error::`。CI 会把它标成红色注解，本地就是普通输出。"""
    print(f'::error::{msg}')


def warn(msg):
    print(f'::warning::{msg}')


def notice(msg):
    print(f'::notice::{msg}')


# ── GITHUB_OUTPUT ─────────────────────────────────────────────────────────────

def set_output(pairs):
    """把 `k=v` 追加进 `$GITHUB_OUTPUT`（没设这个变量时什么都不做）。

    返回是否写进去了 —— 本地跑时是 `False`，调用方据此决定要不要额外打一行摘要。
    """
    path = os.environ.get('GITHUB_OUTPUT')
    if not path:
        return False
    with open(path, 'a', encoding='utf-8', newline='\n') as f:
        for k, v in pairs.items():
            f.write(f'{k}={v}\n')
    return True


# ── 子进程 ────────────────────────────────────────────────────────────────────

def which(name):
    return shutil.which(name)


def gradlew():
    """`gradlew` 的可执行文件 —— Windows 上是 `gradlew.bat`。"""
    return ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')


def run(args, *, cwd=None, capture=True, check=False):
    """跑一条命令。`capture=False` 时输出直接接到当前终端（长任务用，省内存也看得见进度）。"""
    argv = [str(a) for a in args]
    proc = subprocess.run(
        argv,
        cwd=str(cwd or ROOT),
        text=True,
        encoding='utf-8',
        errors='replace',
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )
    if check and proc.returncode != 0:
        err(f'命令失败（退出码 {proc.returncode}）：{" ".join(argv)}')
        raise SystemExit(1)
    return proc


def java_major(java_bin):
    """`java -version` 打的是 stderr，取主版本号；取不到返回 `None`。"""
    proc = run([java_bin, '-version'])
    m = re.search(r'version "(\d+)', proc.stdout or '')
    return m.group(1) if m else None


# ── 判据累加器 ────────────────────────────────────────────────────────────────

class Checks:
    """「一堆逐项判据 + 最后给一个退出码」的常见形状。

    每条判据要么 `ok`（打一行、不影响退出码），要么 `bad`（打 `::error::`、把整趟判成失败）。
    `finish()` 打印收尾那行并返回退出码，脚本结尾 `raise SystemExit(checks.finish())`。
    """

    def __init__(self, title):
        self.title = title
        self.failed = False

    def ok(self, msg):
        print(f'  ok  {msg}')

    def bad(self, msg):
        err(msg)
        self.failed = True

    def finish(self, pass_msg=None):
        if self.failed:
            err(f'{self.title}未通过 —— 见上面的 ::error:: 条目')
            return 1
        print(pass_msg or f'{self.title}全部通过')
        return 0


def main(func):
    """把 `SystemExit` 之外的异常也变成一行 `::error::` —— 崩了要能一眼看出崩在哪。"""
    try:
        raise SystemExit(func())
    except SystemExit:
        raise
    except Exception as e:  # noqa: BLE001 —— 这一层就是兜底的，脚本本身不该抛
        err(f'脚本崩了：{type(e).__name__}: {e}')
        raise SystemExit(1)


if __name__ == '__main__':
    # 自检：把仓库根打出来，顺手确认它确实是个仓库
    print(ROOT)
    sys.exit(0 if (ROOT / 'settings.gradle.kts').is_file() else 1)
