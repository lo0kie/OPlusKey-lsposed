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

BUILD_ID=$(grep -v '^[[:space:]]*//' core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt |
  sed -n 's/.*BUILD_ID[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
#  **先剔掉注释行再取**：这两个文件里都留着「示例：versionName = "1.0.0"」那种注释，
#  只取第一条匹配就会抓到注释里的旧值 —— 而 version_name 直接决定发版的 tag。
VERSION_NAME=$(grep -v '^[[:space:]]*//' app/build.gradle.kts |
  sed -n 's/.*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
VERSION_CODE=$(grep -v '^[[:space:]]*//' app/build.gradle.kts |
  sed -n 's/.*versionCode[[:space:]]*=[[:space:]]*\([0-9]\+\).*/\1/p' | head -1)

missing=""
[ -n "$BUILD_ID" ] || missing="${missing}${missing:+、}BUILD_ID（core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt）"
[ -n "$VERSION_NAME" ] || missing="${missing}${missing:+、}versionName（app/build.gradle.kts）"
[ -n "$VERSION_CODE" ] || missing="${missing}${missing:+、}versionCode（app/build.gradle.kts）"
if [ -n "$missing" ]; then
  echo "::error::抓不到 ${missing}；不要把空的版本值发出去（会建成 00000-0.0.0 的 tag）"
  exit 1
fi

# tag 直接用 `<versionCode>-<versionName>`，**不补前导零**：模块仓库的机器人会按 APK 二进制
#   清单里的 versionCode 重算 tag 名，补零写的 `00016-1.4.2` 会被它改成 `16-1.4.2`、Release
#   一并挪过去，旧名字留成孤儿 tag。与它写法一致，才不会每次上架都多一个垃圾 tag。
#   管理器拿它算更新时是 `split("-", 2)` + `Long.parseLong`，两种写法都认。
TAG_CODE="$VERSION_CODE"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "build_id=${BUILD_ID}"
    echo "version_name=${VERSION_NAME}"
    echo "version_code=${VERSION_CODE}"
    echo "tag=${TAG_CODE}-${VERSION_NAME}"
  } >> "$GITHUB_OUTPUT"
fi

echo "版本 ${VERSION_NAME}（${VERSION_CODE}），tag ${TAG_CODE}-${VERSION_NAME}，构建 ${BUILD_ID}"
