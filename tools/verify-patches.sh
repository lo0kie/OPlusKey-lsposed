#!/usr/bin/env bash
# `patches/repo` 里那几份**被 CI 直接解析**的二进制 / 元数据的完整性校验。
#
# 为什么需要：这一个 `coui-ui-android` 的 aar 是打过补丁再重新发布的（见
# `patches/coui/build-patched.sh`），它不在任何上游仓库里，也没有上游签名可对 —— 仓库里那一份
# 就是唯一来源。没有校验和时，被换掉 / 传坏都**看不出来**，而它最终会进用户的包。
#
# 判据：每个 `.aar` / `.pom` / `.module` 旁边必须有一份 `.sha256`，且内容与文件一致。
#   `build-patched.sh` 在版本改写之后会重新生成它们；这里只负责核对。
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0
count=0
while IFS= read -r -d '' f; do
  case "$f" in
    *.aar|*.pom|*.module) ;;
    *) continue ;;
  esac
  count=$((count + 1))
  sum="$f.sha256"
  if [ ! -f "$sum" ]; then
    echo "::error::$f 没有 .sha256（补丁产物必须带校验和）"
    fail=1
    continue
  fi
  # 只比第一段（`sha256sum` 的格式是「哈希 + 两个空格 + 文件名」）
  want=$(cut -d' ' -f1 "$sum")
  got=$(sha256sum "$f" | cut -d' ' -f1)
  if [ "$want" != "$got" ]; then
    echo "::error::$f 的校验和对不上（记录 $want / 实际 $got）—— 文件被改过或没重新生成"
    fail=1
  fi
done < <(find patches/repo -type f -print0)

if [ "$count" -eq 0 ]; then
  echo "::error::patches/repo 里没有产物 —— 这条校验会失效"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "patches/repo：$count 份产物的校验和全部一致"
fi
exit "$fail"
