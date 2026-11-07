#!/usr/bin/env bash
# 在局域网里把设备找出来并连上 —— 不用知道它的 IP。
#
# 用法（在仓库根目录）：
#   bash tools/adb-find.sh            # 找出来就连上，成功后打印 host:port
#   bash tools/adb-find.sh 5555 30000 # 只找这两个端口
#   eval "$(bash tools/adb-find.sh --export)"   # 顺手设好 ANDROID_SERIAL
#
# 三条路，从便宜到贵：
#   1. **缓存**：上一次连成的 `host:port` 在 `build/.adb-last`，先试它（同一台机器换 IP 时必然落空，
#      但最常见的情况是「IP 没变」，这一条一击即中）；
#   2. **mDNS**：`adb mdns services` 里 `_adb-tls-connect._tcp` 那几条 —— 手机的「无线调试」
#      开着时会广播，**端口变了也照样找得到**（android 11+）；
#   3. **扫网段**：本机 /24 上逐个试端口（默认只有 5555）。这条只为「`adb tcpip 5555` 那套」兜底：
#      那个模式下 adbd 不广播 mDNS，IP 变了就只剩扫描这一条路。
#
# 为什么不用 `adb connect` 硬扫 30000-50000：无线调试的端口是**随机**的，扫一个 /24 的 2 万个端口
# 要几分钟；那种场景直接开「无线调试」用 mDNS，配合 `ADB_MDNS_AUTO_CONNECT='*'` 连脚本都不用跑。
set -u

#  缓存路径是相对仓库根的，所以先把位置钉死（从任何目录调用都一样）
cd "$(dirname "$0")/.." || exit 1

command -v adb >/dev/null 2>&1 || {
  printf '找不到 adb —— 先把 platform-tools 放进 PATH（或在仓库里用 tools/… 那套约定）\n' >&2
  exit 1
}

PORT_DEFAULT=5555
CACHE_DIR="build"
CACHE_FILE="$CACHE_DIR/.adb-last"
SCAN_WORKERS=32

say() { printf '%s\n' "$*" >&2; }

# 成功连上的那几条（`adb devices` 里状态为 device 的序列号 / host:port）
live_targets() { adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'; }

is_live() { live_targets | grep -qx "$1"; }

# 试一个 `host:port`：连上了返回 0。已经连着的不重复连（省一次 3 秒）。
try_target() {
  local target=$1
  is_live "$target" && return 0
  #  `timeout` 在 Git Bash 里通常有；没有就直接连（最坏多等一两秒）
  if command -v timeout >/dev/null 2>&1; then
    timeout 3 adb connect "$target" >/dev/null 2>&1 || true
  else
    adb connect "$target" >/dev/null 2>&1 || true
  fi
  is_live "$target"
}

# 本机在局域网里的地址（用来推 /24）
#
#  两个坑都在真机上踩过：
#   · Windows 中文系统的 `ipconfig` 是 **CP936**，直接 `grep/awk` 会被当成二进制（一行都读不到）——
#     先 `iconv` 转一道；
#   · 机器上常有 169.254.x 的 APIPA 地址（虚拟网卡 / 没拿到 DHCP），它是**最先出现**的那一条，
#     取第一条就会去扫一个空网段（实测白扫三分钟）。判据取「有默认网关且不是 169.254」。
local_ip() {
  if command -v ip >/dev/null 2>&1; then
    ip -4 route get 1.1.1.1 2>/dev/null |
      awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }'
    return
  fi
  local raw
  raw=$(ipconfig 2>/dev/null)
  #  `iconv` 不在就原样往下走（英文系统的 ipconfig 本来就是 ASCII）
  if command -v iconv >/dev/null 2>&1; then
    raw=$(printf '%s' "$raw" | iconv -f CP936 -t UTF-8 2>/dev/null || printf '%s' "$raw")
  fi
  printf '%s\n' "$raw" | tr -d '\r' | awk '
    /IPv4/ { ip = $NF }
    /网关|Gateway/ { if ($NF ~ /^[0-9]+\./) gw = 1 }
    /^[[:space:]]*$/ {
      if (ip != "" && gw && ip !~ /^169\.254\./) { print ip; exit }
      ip = ""; gw = 0
    }
  '
}

# 端口通不通 —— **先用它筛一遍，再 `adb connect`**。
#
#  直接对 254 个地址挨个 `adb connect` 要三分钟（连不上时每条都要等超时）；
#  而 TCP 探测对关闭端口是立刻回 RST 的，整个 /24 一两秒就筛完。
port_open() {
  local host=$1 port=$2
  if command -v timeout >/dev/null 2>&1; then
    timeout 0.6 bash -c "exec 3<>/dev/tcp/$host/$port" 2>/dev/null
  else
    (exec 3<>/dev/tcp/"$host"/"$port") 2>/dev/null
  fi
}

succeed() {
  local target=$1
  mkdir -p "$CACHE_DIR" 2>/dev/null || true
  printf '%s\n' "$target" >"$CACHE_FILE" 2>/dev/null || true
  if [ "${1:-}" = "--export" ] || [ "${EXPORT:-0}" = "1" ]; then
    printf 'export ANDROID_SERIAL=%s\n' "$target"
  fi
  say "已连上：$target"
  printf '%s\n' "$target"
  exit 0
}

export_mode=0
[ "${1:-}" = "--export" ] && { export_mode=1; shift; }
EXPORT=$export_mode

ports="${*:-}"
if [ -z "$ports" ]; then ports="$PORT_DEFAULT"; fi

# ── 1. 缓存 ────────────────────────────────────────────────────────────────
if [ -s "$CACHE_FILE" ]; then
  cached=$(tr -d '\r\n' <"$CACHE_FILE")
  if [ -n "$cached" ] && try_target "$cached"; then succeed "$cached"; fi
  say "缓存（$cached）连不上，继续找"
fi

# ── 2. mDNS ───────────────────────────────────────────────────────────────
if adb mdns check >/dev/null 2>&1; then
  #  形如：`adb-XXXX-YYYY._adb-tls-connect._tcp   192.168.0.102:37123`
  while read -r name hp; do
    [ -n "${hp:-}" ] || continue
    case "$name" in
      *_adb-tls-connect._tcp*)
        say "mDNS 发现：$name → $hp"
        try_target "$hp" && succeed "$hp"
        ;;
    esac
  done <<EOF
$(adb mdns services 2>/dev/null)
EOF
else
  say "本机 adb 的 mDNS 没起来（`adb mdns check` 失败），跳过这一路"
fi

# ── 3. 扫本机 /24 ──────────────────────────────────────────────────────────
ip=$(local_ip)
if [ -z "$ip" ]; then
  say "取不到本机局域网地址 —— 用 `bash tools/adb-find.sh <端口…>` 也不行时，手动 `adb connect <IP>:5555`"
  exit 1
fi
prefix=${ip%.*}
say "扫 $prefix.0/24，端口：$ports（并发 $SCAN_WORKERS）"

found=""
#  扫一个 IP 的那一下要并发调，所以单独抽成函数并 `export -f` 给子 bash 用
try_ip() {
  local host=$1 port=$2 target="$1:$2"
  #  先探端口，再 connect：反过来会把整段扫描拖成分钟级（见 [port_open]）
  port_open "$host" "$port" || return 0
  adb connect "$target" >/dev/null 2>&1
  live_targets | grep -qx "$target" && printf '%s\n' "$target"
}
export -f try_ip port_open live_targets

for port in $ports; do
  #  命中就把 `host:port` 打到一个临时文件
  hit_file=$(mktemp 2>/dev/null || echo "$CACHE_DIR/.adb-hit.$$")
  #  `xargs -P` 是 GNU 扩展：macOS 上没有就退回串行（慢，但能用）
  if xargs -P 1 true </dev/null >/dev/null 2>&1; then
    seq 1 254 | xargs -P "$SCAN_WORKERS" -I{} bash -c "try_ip '$prefix.{}' '$port'" >>"$hit_file" 2>/dev/null
  else
    say "这个环境没有 xargs -P，退回串行扫描（会很慢）"
    for n in $(seq 1 254); do
      try_ip "$prefix.$n" "$port" >>"$hit_file" 2>/dev/null
    done
  fi
  if [ -s "$hit_file" ]; then
    found=$(head -1 "$hit_file" | tr -d '\r')
    rm -f "$hit_file" 2>/dev/null
    [ -n "$found" ] && succeed "$found"
  fi
  rm -f "$hit_file" 2>/dev/null
done

say "没找到设备。检查：手机与电脑是否同一网段、手机是否开着「无线调试」或执行过 `adb tcpip 5555`"
exit 1
