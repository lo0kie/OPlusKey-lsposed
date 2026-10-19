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
VER_FROM="1.1.0-alphasubtitle5"   # 上游 -Palpha=subtitle5 的产物版本
VER_TO="1.1.0-subtitle11"          # 本仓库对外声明并提交的版本

# 克隆（已存在则复用）
if [ ! -d "$CLONE/.git" ]; then
  git clone --depth 1 --branch "$UPSTREAM_TAG" "$UPSTREAM" "$CLONE"
  cp "$ROOT/local.properties" "$CLONE/"   # sdk.dir
fi
cd "$CLONE"

# 先把工作区还原到干净的 tag 上。
#
#   补丁是**顺序叠加**的：同一份文件若被两份补丁先后改过，「反向能打上」这个判据就不成立
#   —— 逆推 0001 的时候 0005 的上下文已经在树里，必然失败。
#   `0001` 与 `0005` 都改 `TopAppBar.kt`，于是原来那句「已应用就跳过」其实一直在误报
#   *「补丁无法应用（上下文对不上）」*，脚本自述的「可重入」并不成立。
#   改成每次从干净树重放：补丁文件才是唯一真相，克隆里的手改一律丢弃（它只是构建脚手架）。
#   用 `reset --hard` 而不是 `checkout -- .`：后者从**索引**恢复，索引里若还留着上一次
#   打过的补丁（脚本中途失败 / 手工 `git add` 过），下一次重放就会「补丁打不上」。
git reset --hard HEAD
git clean -fd coui-ui/src/commonMain/kotlin >/dev/null

# 应用补丁（逐份；打不上则直接退出，不产出没补丁的包）
for p in "$ROOT"/patches/coui/*.patch; do
  name="$(basename "$p")"
  if ! git apply "$p"; then
    echo "补丁无法应用（上下文对不上）：$name" >&2
    exit 1
  fi
  echo "已应用：$name"
done

# 只构建 android 出版物（KMP 其它目标不碰）
#
# 注意：按平台挑 wrapper：原来写死 `./gradlew.bat`（Windows 批处理），
#   非 Windows 环境（含 CI 的 ubuntu runner）根本跑不动 —— 而脚本其余部分（`git clone` /
#   `git apply` / `sed -i`）都是 POSIX 写法，看上去是能跨平台的。
if [ -n "${OS:-}" ] && [ "${OS:-}" = "Windows_NT" ]; then
  GRADLE="./gradlew.bat"
elif [ -x "./gradlew" ]; then
  GRADLE="./gradlew"
else
  GRADLE="./gradlew.bat"
fi
echo "用 $GRADLE 构建（当前目录 $PWD）"
"$GRADLE" :coui-ui:publishAndroidPublicationToLocalRepository -Palpha=subtitle5

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
# 上游那几张校验和随版本改写失效 ⇒ 先删掉，**最后再统一重生成**（见文件末尾）。
#   不留空：`patches/repo` 里的 aar 是被 CI 直接解析的二进制，没有校验和时**被换掉也看不出来**；
#   重生成之后 `tools/verify-patches.sh`（CI 里跑）能发现仓库里那份被人动过 / 传坏了。
rm -f "$DST"/*.md5 "$DST"/*.sha1 "$DST"/*.sha256 "$DST"/*.sha512

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

# 校验和**必须最后算**：上面那几条 `sed -i` 还在改 `.module` / `.pom`，
#   早一步生成就是给「改之前的字节」记了个数 —— `tools/verify-patches.sh` 当场报对不上。
for f in "$DST"/*.aar "$DST"/*.pom "$DST"/*.module; do
  [ -f "$f" ] || continue
  ( cd "$(dirname "$f")" && sha256sum "$(basename "$f")" > "$(basename "$f").sha256" )
done

echo "完成：$DST"
