#!/usr/bin/env python3
"""依赖一动，这两件事必须一起做完 —— 见 AGENTS.md 二、1.2。

 1) 锁文件：`app/gradle.lockfile` / `core/gradle.lockfile`（dependabot 只改版本号，锁文件不动）；
 2) 校验元数据：`gradle/verification-metadata.xml` 逐文件记 sha256，新版本一进来就缺条目，
    CI 会在**第一条**「Dependency verification failed」上红。

为什么合成一条命令：这两半**缺任何一半 PR 都红**，而漏掉元数据这一半时，报错长在
  「plugins 解析」上，看起来像别的问题。

用法（仓库根）：`python3 tools/relock.py`，跑完看 `git diff --stat` 把两处 diff 一起提交。

JDK：必须 21。detekt 跑不动 25（它会直接报一句 `25.0.2`），lint 又要求 >= 项目目标 21。
"""

import os

import _common as c

REQUIRED_JAVA = '21'


def check_java():
    """返回退出码（0 = 通过）。两档都要查：`JAVA_HOME` 优先，没设就看 PATH。

    **没设 JAVA_HOME 也要查**：脚本调 `gradlew`，它用的是 PATH 上那个 java。
    跳过这道检查的后果是 —— 半小时后 detekt 在 JDK 25 上直接崩（只输出一行版本号），
    或者更糟：锁出来的产物与 CI 用的 JDK 不一致，而后者的错误信息指向别处。
    """
    home = os.environ.get('JAVA_HOME')
    if home:
        java = os.path.join(home, 'bin', 'java')
        major = c.java_major(java)
        if major != REQUIRED_JAVA:
            c.err(f'JAVA_HOME 指向的是 JDK {major} —— 这个脚本要求 JDK {REQUIRED_JAVA}'
                  '（detekt 不认 25、lint 要 >= 21）')
            return 1
        return 0

    java = c.which('java')
    if not java:
        c.err(f'没设 JAVA_HOME，PATH 上也找不到 java —— 这个脚本要求 JDK {REQUIRED_JAVA}')
        return 1
    major = c.java_major(java)
    if major != REQUIRED_JAVA:
        c.err(f'PATH 上的 java 是 JDK {major} —— 要求 JDK {REQUIRED_JAVA}；'
              '把 JAVA_HOME 指到那份 21 上再跑')
        return 1
    return 0


def main():
    rc = check_java()
    if rc:
        return rc

    gradlew = c.gradlew()

    print('== 1/2 重锁依赖')
    #  `-q` 让 gradle 在成功时不刷屏；输出**不捕获**（stderr 直接进终端）—— 失败时要能看见
    #  它自己那句错误，这正是原来那句「先看上面 gradle 的输出」所指的东西。
    step = c.run([gradlew, ':app:dependencies', ':core:dependencies', '--write-locks', '-q'],
                 capture=False)
    if step.returncode != 0:
        c.err('重锁失败（先看上面 gradle 的输出）')
        return 1

    #  `gate :app:assembleDebug` 不是随手挑的：元数据只记**这一趟真正解析到的**配置 —— 只跑
    #    `help` 会漏掉 lint / aapt2 那一批，补完 CI 仍然红（表现是「补了一轮又一轮」）。这三个
    #    任务把构建脚本、lint、打包三段的依赖都摸一遍，一次收齐。
    print('== 2/2 补齐依赖校验元数据（gate + assembleDebug 那一整条）')
    step = c.run([gradlew, '--write-verification-metadata', 'sha256', 'gate', ':app:assembleDebug'],
                 capture=False)
    if step.returncode != 0:
        c.err('补元数据这一趟没跑通 —— 先解决上面那条错误（工具链版本不兼容也会卡在这里）')
        return 1

    print()
    print('== 改动')
    c.run(['git', '--no-pager', 'diff', '--stat',
           'app/gradle.lockfile', 'core/gradle.lockfile', 'gradle/verification-metadata.xml'],
          capture=False)
    print()
    print('两处 diff 必须一起提交；只提交一半，CI 会在另一半上红。')
    return 0


if __name__ == '__main__':
    c.main(main)
