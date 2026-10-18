#!/usr/bin/env bash
# 从源码里读出三个版本值（`ci.yml` 与 `release.yml` 共用这一份）。
#
# 为什么必须是字面量：`versionCode = base + 1` 这类表达式正则抓不到。原先的兜底
#   （unknown / 0.0.0 / 0）会让 job 照常跑完，再发出一个 tag 叫 `0-0.0.0` 的 release；
#   而模块仓库的机器人是从 APK 的二进制清单读真值算 tag 的，两边不一致它就再改一次 tag，
#   仓库里凭空多一个 tag，且没人会注意到版本号根本没进包。所以抓不到就 **fail**。
#
# 用法：在**仓库根目录**跑 `bash tools/read-version.sh`（下面几个路径都相对仓库根）。
#   - 设了 `$GITHUB_OUTPUT` 时，把 build_id / version_name / version_code / tag 追加进去；
#   - 无论如何都往 stdout 打一行人读的摘要。

set -u

BUILD_ID=$(sed -n 's/.*BUILD_ID[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' \
  core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt | head -1)
VERSION_NAME=$(sed -n 's/.*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' \
  app/build.gradle.kts | head -1)
VERSION_CODE=$(sed -n 's/.*versionCode[[:space:]]*=[[:space:]]*\([0-9]\+\).*/\1/p' \
  app/build.gradle.kts | head -1)

missing=""
[ -n "$BUILD_ID" ] || missing="${missing}${missing:+、}BUILD_ID（core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt）"
[ -n "$VERSION_NAME" ] || missing="${missing}${missing:+、}versionName（app/build.gradle.kts）"
[ -n "$VERSION_CODE" ] || missing="${missing}${missing:+、}versionCode（app/build.gradle.kts）"
if [ -n "$missing" ]; then
  echo "::error::抓不到 ${missing}；不要把空的版本值发出去（会建成 0-0.0.0 的 tag）"
  exit 1
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "build_id=${BUILD_ID}"
    echo "version_name=${VERSION_NAME}"
    echo "version_code=${VERSION_CODE}"
    echo "tag=${VERSION_CODE}-${VERSION_NAME}"
  } >> "$GITHUB_OUTPUT"
fi

echo "版本 ${VERSION_NAME}（${VERSION_CODE}），构建 ${BUILD_ID}"
