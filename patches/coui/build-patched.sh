#!/usr/bin/env bash
# 再生成打过 patches/coui/*.patch 的 coui-ui-android，发布到本仓库的 patches/repo（文件型 maven 仓库）。
#
# 与 pnpm patch 同构：补丁文件与产物（AAR + POM + .module）都随仓库走，构建时不依赖网络克隆
# —— 只在「改补丁 / 升库版本」时需要重跑。
#
# 用法：在仓库根目录执行   bash patches/coui/build-patched.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
UPSTREAM="https://github.com/suqi8/coui"
UPSTREAM_TAG="v1.1.0"
CLONE="$ROOT/.temp/coui-repo"
VER_FROM="1.1.0-alphasubtitle2"   # 上游 -Palpha=subtitle2 的产物版本
VER_TO="1.1.0-subtitle3"          # 本仓库对外声明并提交的版本

# 克隆（已存在则复用）
if [ ! -d "$CLONE/.git" ]; then
  git clone --depth 1 --branch "$UPSTREAM_TAG" "$UPSTREAM" "$CLONE"
  cp "$ROOT/local.properties" "$CLONE/"   # sdk.dir
fi
cd "$CLONE"

# 应用补丁（逐份；已应用过就跳过，脚本可重入；打不上则直接退出，不产出没补丁的包）
for p in "$ROOT"/patches/coui/*.patch; do
  name="$(basename "$p")"
  if git apply --check "$p" 2>/dev/null; then
    git apply "$p"
    echo "已应用：$name"
  elif git apply --check --reverse "$p" 2>/dev/null; then
    echo "已在树里，跳过：$name"
  else
    echo "补丁无法应用（上下文对不上）：$name" >&2
    exit 1
  fi
done

# 只构建 android 出版物（KMP 其它目标不碰）
./gradlew.bat :coui-ui:publishAndroidPublicationToLocalRepository -Palpha=subtitle2

# 产物搬进 patches/repo，版本改写为 $VER_TO
SRC="coui-ui/build/repository/local/io/github/suqi8/coui/kmp/coui-ui-android/$VER_FROM"
DST="$ROOT/patches/repo/io/github/suqi8/coui/kmp/coui-ui-android/$VER_TO"
rm -rf "$DST"
mkdir -p "$DST"
for f in "$SRC"/*; do
  base="$(basename "$f")"
  cp "$f" "$DST/${base//$VER_FROM/$VER_TO}"
done
sed -i "s/$VER_FROM/$VER_TO/g" "$DST/coui-ui-android-$VER_TO.pom" "$DST/coui-ui-android-$VER_TO.module"
rm -f "$DST"/*.md5 "$DST"/*.sha1 "$DST"/*.sha256 "$DST"/*.sha512   # 校验和已随版本改写失效

# 内部依赖版本改回上游号
#    `-Palpha=subtitleN` 会把 coui-core / coui-squircle 这些内部依赖也带上后缀，而本仓库
#    只有 coui-ui 的产物（只有它打了补丁）——带后缀的坐标谁都解析不到，构建期直接报
#    `Could not find io.github.suqi8.coui.kmp:coui-core:1.1.0-subtitleN`。
#    这两个模块没有补丁，让它们指回上游 $BASE_VER 即可。
BASE_VER="1.1.0"
sed -i '/"module": "coui-core"/{n;n;s/"requires": "'"$VER_TO"'"/"requires": "'"$BASE_VER"'"/}' \
  "$DST/coui-ui-android-$VER_TO.module"
sed -i '/"module": "coui-squircle"/{n;n;s/"requires": "'"$VER_TO"'"/"requires": "'"$BASE_VER"'"/}' \
  "$DST/coui-ui-android-$VER_TO.module"
sed -i '/<artifactId>coui-core-android<\/artifactId>/{n;s|<version>'"$VER_TO"'</version>|<version>'"$BASE_VER"'</version>|}' \
  "$DST/coui-ui-android-$VER_TO.pom"
sed -i '/<artifactId>coui-squircle-android<\/artifactId>/{n;s|<version>'"$VER_TO"'</version>|<version>'"$BASE_VER"'</version>|}' \
  "$DST/coui-ui-android-$VER_TO.pom"

echo "完成：$DST"
