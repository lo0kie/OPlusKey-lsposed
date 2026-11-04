#!/usr/bin/env bash
# 把钉好的版同步到 Gitee 的 Release（国内镜像）。
#
# 用法：GITEE_TOKEN=<私人令牌> PRERELEASE=true|false \
#         bash tools/gitee-release.sh <owner/repo> <tag> <name> <body-file> <dist-dir> [target]
#
# 为什么直接调 API 而不是引一个现成的 action：这里只要两个调用（Release 本体 + `attach_files`），
#   引第三方 action 还得把它的行为当黑盒再验一遍。Gitee 这套接口与 GitHub 同形，用 curl 写出来
#   能一眼看清发出去的到底是什么。
#
# 幂等：同一个 tag 重跑只更新正文，已经传过的附件跳过。删 tag 重打是允许的（见
#   `docs/implementation.md`「发版：版本号与 tag」），重打之后这条流水线会重跑，那时不该红。
#
# 令牌除「查有没有这条」那一次外都走请求体（`--data-urlencode` / `-F`）；查询接口只认 URL
#   参数，所以出错时的日志里不回显请求地址。

set -euo pipefail

if [ $# -lt 5 ]; then
  echo "用法: gitee-release.sh <owner/repo> <tag> <name> <body-file> <dist-dir> [target]" >&2
  exit 2
fi

repo=$1
tag=$2
name=$3
body_file=$4
dist=$5
target=${6:-}

: "${GITEE_TOKEN:?没有 GITEE_TOKEN}"
prerelease=${PRERELEASE:-false}

api="https://gitee.com/api/v5/repos/${repo}/releases"

# 每个请求都带**硬超时**：脚本跑在 CI 里，curl 默认不设上限 —— 连接一卡住就一直是「运行中」，
#   整条钉版流水线能挂到 job 的 6 小时上限（实测就卡在「同步到 Gitee Release」这一步）。
#   查询 / 建 Release / 改正文都是小请求，60 秒够；上传附件是整包，给 300 秒。
#   `--retry` 只兜「连不上 / 5xx」这类临时问题，两次重试仍不行就当场红 —— 卡着不动比红更难查。
curl_common=(-sS --connect-timeout 15 --retry 2 --retry-delay 3)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

fail() {
  echo "::error::$1"
  exit 1
}

[ -f "$body_file" ] || fail "正文文件不存在：${body_file}"

# 取已有那条。这个接口在「没有」时回 200 + 字面量 `null`，不是 404 ——
#   所以判据是 id 空不空，不是状态码。
code=$(curl "${curl_common[@]}" --max-time 60 -o "$tmp/rel.json" -w '%{http_code}' -G "$api/tags/${tag}" \
  --data-urlencode "access_token=${GITEE_TOKEN}")
[ "$code" = "200" ] || fail "查询 Gitee Release 失败（HTTP ${code}）：$(cat "$tmp/rel.json")"

id=$(jq -r '.id // empty' "$tmp/rel.json")

if [ -z "$id" ]; then
  args=(
    --data-urlencode "access_token=${GITEE_TOKEN}"
    --data-urlencode "tag_name=${tag}"
    --data-urlencode "name=${name}"
    --data-urlencode "body@${body_file}"
  )
  if [ -n "$target" ]; then
    args+=(--data-urlencode "target_commitish=${target}")
  fi
  # 只在是预发布时才带这个字段：`false` 不传就是默认值，传字符串 `false` 有被当成真的风险
  #   （表单参数到了服务端是字符串，非空即真那种解析最坑）。
  if [ "$prerelease" = "true" ]; then
    args+=(--data-urlencode "prerelease=true")
  fi
  code=$(curl "${curl_common[@]}" --max-time 60 -o "$tmp/rel.json" -w '%{http_code}' -X POST "$api" "${args[@]}")
  case "$code" in
    200 | 201) ;;
    *) fail "在 Gitee 建 Release 失败（HTTP ${code}）：$(cat "$tmp/rel.json")" ;;
  esac
  id=$(jq -r '.id // empty' "$tmp/rel.json")
  [ -n "$id" ] || fail "Gitee 建 Release 的响应里没有 id：$(cat "$tmp/rel.json")"
  echo "已在 Gitee 建 Release ${tag}（id ${id}）"
else
  patch=(
    --data-urlencode "access_token=${GITEE_TOKEN}"
    #  `tag_name` 在 PATCH 里也是**必填**：Gitee 的更新接口缺它直接 400 `tag_name is missing`
    #   （实测踩过 —— 只有「这条 release 已存在」那条分支会走到这里，第一次建时看不出来）。
    --data-urlencode "tag_name=${tag}"
    --data-urlencode "name=${name}"
    --data-urlencode "body@${body_file}"
  )
  #  `target_commitish` 只在建的时候生效，PATCH 不带它就永远停在创建时那个 SHA ——
  #   删 tag 重打（tag 指到新提交）之后，Gitee 那条 release 的「对应提交」还是旧的。
  if [ -n "$target" ]; then
    patch+=(--data-urlencode "target_commitish=${target}")
  fi
  if [ "$prerelease" = "true" ]; then
    patch+=(--data-urlencode "prerelease=true")
  fi
  code=$(curl "${curl_common[@]}" --max-time 60 -o "$tmp/rel.json" -w '%{http_code}' -X PATCH "$api/${id}" "${patch[@]}")
  [ "$code" = "200" ] || fail "更新 Gitee Release 失败（HTTP ${code}）：$(cat "$tmp/rel.json")"
  echo "Gitee 上已有 Release ${tag}（id ${id}），正文已更新"
fi

# 国内镜像**只放发布包**：`-debug.apk` 24MB、只是复现用，从 GitHub runner 传到 Gitee
#   实测要十几分钟（release + releaseLog 加起来才 4MB）。本仓库那条存档里三个包都在，
#   镜像少这一个不影响任何人。
shopt -s nullglob
apks=()
for f in "${dist}"/*.apk; do
  case "$f" in
    *-debug.apk) continue ;;
  esac
  apks+=("$f")
done
shopt -u nullglob
[ ${#apks[@]} -gt 0 ] || fail "${dist} 下没有可发的 apk"

for f in "${apks[@]}"; do
  base=$(basename "$f")
  # `index()` 命中 0 号位时返回 0，`jq -e` 只把 false / null 当失败，所以这里判得准。
  if jq -e --arg n "$base" '(.assets // []) | map(.name) | index($n)' "$tmp/rel.json" >/dev/null; then
    echo "跳过（Gitee 上已有）：${base}"
    continue
  fi
  code=$(curl "${curl_common[@]}" --max-time 600 -o "$tmp/asset.json" -w '%{http_code}' -X POST "$api/${id}/attach_files" \
    -F "access_token=${GITEE_TOKEN}" -F "file=@${f}")
  case "$code" in
    200 | 201) echo "已上传：${base}" ;;
    *)
      # 兜底：上面那层是按附件列表判重的，列表字段名若与预期不同就会漏判、直接撞上「已存在」——
      #   那不是失败（正文已经更新过了），跳过即可，日志里留一行看得见。
      if grep -qiE 'exist|已存在|重复' "$tmp/asset.json"; then
        echo "跳过（Gitee 上已有）：${base}"
      else
        fail "上传 ${base} 到 Gitee 失败（HTTP ${code}）：$(cat "$tmp/asset.json")"
      fi
      ;;
  esac
done

echo "Gitee Release：https://gitee.com/${repo}/releases/tag/${tag}"
