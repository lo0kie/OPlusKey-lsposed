#!/usr/bin/env bash
# 依赖一动，这两件事必须一起做完 —— 见 AGENTS.md 二、1.2。
#
#  1) 锁文件：`app/gradle.lockfile` / `core/gradle.lockfile`（dependabot 只改版本号，锁文件不动）；
#  2) 校验元数据：`gradle/verification-metadata.xml` 逐文件记 sha256，新版本一进来就缺条目，
#     CI 会在**第一条**「Dependency verification failed」上红。
#
# 为什么合成一条命令：这两半**缺任何一半 PR 都红**，而漏掉元数据这一半时，报错长在
#   「plugins 解析」上，看起来像别的问题（见本脚本末尾那两行说明）。
#
# 用法：在仓库根目录 `bash tools/relock.sh`，跑完看 `git diff --stat` 把两处 diff 一起提交。
#
# JDK：必须 21。detekt 跑不动 25（它会直接报一句 `25.0.2`），lint 又要求 >= 项目目标 21。
#   本机没有 21 时先 `JAVA_HOME=<jdk21 路径> bash tools/relock.sh`。

set -u

cd "$(dirname "$0")/.." || exit 1

major() {
  "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}

if [ -n "${JAVA_HOME:-}" ]; then
  v=$(major "$JAVA_HOME")
  if [ "$v" != "21" ]; then
    echo "::error::JAVA_HOME 指向的是 JDK $v —— 这个脚本要求 JDK 21（detekt 不认 25、lint 要 >= 21）"
    exit 1
  fi
else
  #  **没设 JAVA_HOME 也要查**：脚本调 `gradlew`，它用的是 PATH 上那个 java。
  #  跳过这道检查的后果是——半小时后 detekt 在 JDK 25 上直接崩（只输出一行版本号），
  #  或者更糟：锁出来的产物与 CI 用的 JDK 不一致，而后者的错误信息指向别处。
  j=$(command -v java) || {
    echo "::error::没设 JAVA_HOME，PATH 上也找不到 java —— 这个脚本要求 JDK 21"
    exit 1
  }
  v=$("$j" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
  if [ "$v" != "21" ]; then
    echo "::error::PATH 上的 java 是 JDK $v —— 要求 JDK 21；把 JAVA_HOME 指到那份 21 上再跑"
    exit 1
  fi
fi

echo "== 1/2 重锁依赖"
./gradlew :app:dependencies :core:dependencies --write-locks -q > /dev/null || {
  echo "::error::重锁失败（先看上面 gradle 的输出）"
  exit 1
}

#  `gate :app:assembleDebug` 不是随手挑的：元数据只记**这一趟真正解析到的**配置 —— 只跑 `help`
#   会漏掉 lint / aapt2 那一批，补完 CI 仍然红（表现是「补了一轮又一轮」）。这三个任务把
#   构建脚本、lint、打包三段的依赖都摸一遍，一次收齐。
echo "== 2/2 补齐依赖校验元数据（gate + assembleDebug 那一整条）"
./gradlew --write-verification-metadata sha256 gate :app:assembleDebug || {
  echo "::error::补元数据这一趟没跑通 —— 先解决上面那条错误（工具链版本不兼容也会卡在这里）"
  exit 1
}

echo
echo "== 改动"
git --no-pager diff --stat app/gradle.lockfile core/gradle.lockfile gradle/verification-metadata.xml
echo
echo "两处 diff 必须一起提交；只提交一半，CI 会在另一半上红。"
