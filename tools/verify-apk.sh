#!/usr/bin/env bash
# 发布包的验收底线（AGENTS.md 二、1.2）—— 两个工作流共用：`ci.yml` 在构建后验自己刚出的包，
# `release.yml` 在发版前验要发出去的那两个包（它们可能是从 CI 那条 run 取来的）。
#
# 用法：在**仓库根目录**跑 `bash tools/verify-apk.sh <目录>`，目录里放 `*-debug.apk` 与
# `*-release.apk`。所以必须在根目录跑 —— 下面有几项要回头读源码（`HostApp.PACKAGE`、
# `applicationId`、字典文件），路径都相对仓库根。
#
# 每个 `::error::` 是给 GitHub Actions 的注解，本地跑就是普通一行字。退出码 0 = 全过。

set -u

DIR="${1:-dist}"
fail=0
bad() { echo "::error::$1"; fail=1; }

for apk in "$DIR"/*.apk; do
  echo "校验 $apk"
  listing=$(unzip -l "$apk")

  # (1) META-INF/xposed 三件套齐全（LSPosed 的识别与上架校验都靠它们）
  for f in java_init.list scope.list module.prop; do
    if printf '%s' "$listing" | grep -q "META-INF/xposed/$f"; then
      echo "  ok  META-INF/xposed/$f"
    else
      bad "$apk 缺 META-INF/xposed/$f"
    fi
  done

  # (2) scope.list 首行必须是 system —— 写成 android 的话模块装了也不注入 system_server
  scope=$(unzip -p "$apk" META-INF/xposed/scope.list | head -1 | tr -d '\r')
  if [ "$scope" = "system" ]; then
    echo "  ok  scope.list 首行 = system"
  else
    bad "$apk 的 scope.list 首行须为 system（实际 '$scope'）"
  fi

  # (3) module.prop 同时含 minApiVersion= 与 targetApiVersion=（上架机器人按这两条校验）
  prop=$(unzip -p "$apk" META-INF/xposed/module.prop | tr -d '\r')
  for k in minApiVersion targetApiVersion; do
    if printf '%s\n' "$prop" | grep -q "^${k}="; then
      echo "  ok  module.prop 有 ${k}"
    else
      bad "$apk 的 module.prop 缺 ${k}="
    fi
  done

  # (4) 清单里的界面入口**只放行那一个跳板**：`EntryActivity`（无界面，点了转宿主设置页）。
  #    多一个就是「模块自带界面」或多一条入口；少一个则管理器那颗 FAB 不显示，
  #    而用户装完第一反应就是点它，没反应等于找不到入口（一、6 现在的口径）。
  #    只能查 APK 里的二进制清单：源清单注释会被 merger 抄进合并清单，按字面查会误报。
  #    判据逐项卡死，允许值写在括号里：
  #      · `MODULE_SETTINGS` ×1 —— 那一个跳板
  #      · 裸 `activity` 元素 ×1（AXML 字符串池按行输出，整行精确匹配，
  #        不会误伤 `MainActivity` 这种以 Activity 结尾的类名）
  #      · `activity-alias` ×0、`CATEGORY_LAUNCHER` / `CATEGORY_INFO` ×0
  #      · 跳板类名 ×1 —— 防止「换个 Activity 名字」蒙混过去
  if command -v strings >/dev/null 2>&1; then
    manifest=$(unzip -p "$apk" AndroidManifest.xml | strings -e l)
    entry=$(printf '%s\n' "$manifest" | grep -c 'MODULE_SETTINGS' || true)
    activities=$(printf '%s\n' "$manifest" | grep -cx 'activity' || true)
    aliases=$(printf '%s\n' "$manifest" | grep -cx 'activity-alias' || true)
    launchers=$(printf '%s\n' "$manifest" | grep -cE 'android\.intent\.category\.LAUNCHER|android\.intent\.category\.INFO' || true)
    jumper=$(printf '%s\n' "$manifest" | grep -c 'opluskey\.EntryActivity' || true)
    if [ "$entry" = "1" ] && [ "$activities" = "1" ] && [ "$aliases" = "0" ] && [ "$launchers" = "0" ] && [ "$jumper" = "1" ]; then
      echo "  ok  清单入口只有 EntryActivity 这一个跳板（MODULE_SETTINGS×1 / activity×1 / alias×0 / LAUNCHER·INFO×0）"
    else
      bad "$apk 的清单入口不合规：MODULE_SETTINGS=${entry} activity=${activities} alias=${aliases} LAUNCHER/INFO=${launchers} EntryActivity=${jumper}（允许 1/1/0/0/1）"
    fi
  else
    # 没有 strings 就查不了，必须报错而非静默放过
    bad "runner 上没有 strings（binutils），清单入口这项没法校验"
  fi

  # (6) java_init.list 声明的入口类必须**真的在 dex 里**：
  #    类改名 / 挪包之后 CI 原来照样全绿发版，而框架按名字实例化会失败 ——
  #    现象是「模块已启用、日志一行都没有」（见 AGENTS.md 五之二、0）。
  #    类名在 dex 里是类型描述符 `Lcom/lookie/opluskey/KeyModule;`；多 dex 时全扫。
  entry_cls=$(unzip -p "$apk" META-INF/xposed/java_init.list | head -1 | tr -d '\r')
  if [ -z "$entry_cls" ]; then
    bad "$apk 的 java_init.list 是空的"
  else
    desc="L$(printf '%s' "$entry_cls" | tr '.' '/');"
    if unzip -p "$apk" 'classes*.dex' 2>/dev/null | strings | grep -qF "$desc"; then
      echo "  ok  java_init.list 的 $entry_cls 在 dex 里"
    else
      bad "$apk 的 java_init.list 声明了 $entry_cls，但 dex 里找不到 $desc"
    fi
  fi

  # (7) 作用域必须是构建期写死的那一档（`staticScope=true`）—— 一、9 说的
  #    「作用域写错 = 模块全绿但日志一行都没有」正是它守的东西。
  if printf '%s\n' "$prop" | grep -q '^staticScope=true$'; then
    echo "  ok  module.prop 有 staticScope=true"
  else
    bad "$apk 的 module.prop 缺 staticScope=true（作用域没写死 ⇒ 由用户在管理器里选）"
  fi

  # (8) scope.list 里的宿主包名必须与 `core/model/HostApp.kt` 的 `PACKAGE` 一致。
  #    scope.list 是资源文件，读不到 Kotlin 常量（那边用 `HostApp` 一处定义、代码全引它），
  #    所以只能在这里对一次账：两处不一致时模块装了、启用了、状态全绿，
  #    而设置页那一段**一行日志都没有**（一、9），从日志上看不出漏在哪一层。
  hostapp="core/src/main/java/com/lookie/opluskey/core/model/HostApp.kt"
  scope_host=$(unzip -p "$apk" META-INF/xposed/scope.list | sed -n '2p' | tr -d '\r')
  if [ -f "$hostapp" ]; then
    src_host=$(grep -oE 'const val PACKAGE = "[^"]+"' "$hostapp" | head -1 | sed 's/.*"\(.*\)"/\1/')
    if [ -z "$src_host" ]; then
      bad "从 $hostapp 里没读到 const val PACKAGE（这条校验会失效）"
    elif [ "$scope_host" = "$src_host" ]; then
      echo "  ok  scope.list 的宿主包名 = HostApp.PACKAGE（$src_host）"
    else
      bad "$apk 的 scope.list 第 2 行 '$scope_host' ≠ HostApp.PACKAGE '$src_host'"
    fi
  else
    bad "找不到 $hostapp（包名挪过？这条校验会失效）"
  fi

  # (9) 模块自己的包名也是两处硬编码：`core/model/ModuleInfo.PACKAGE` 与 `applicationId`。
  #    写错的表现是「按包名查自己的 APK」落空 —— 版本行空着、字典退回键名（宿主侧文案
  #    显示成 `catalog.flashlight` 这种），而两处互不相识、编译期不会报错。
  mi="core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt"
  appid=$(grep -oE 'applicationId = "[^"]+"' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')
  if [ -f "$mi" ]; then
    mi_pkg=$(grep -oE 'const val PACKAGE = "[^"]+"' "$mi" | head -1 | sed 's/.*"\(.*\)"/\1/')
    if [ -z "$appid" ] || [ -z "$mi_pkg" ]; then
      bad "没读到 applicationId（'$appid'）或 ModuleInfo.PACKAGE（'$mi_pkg'），这条校验会失效"
    elif [ "$appid" = "$mi_pkg" ]; then
      echo "  ok  ModuleInfo.PACKAGE = applicationId（$appid）"
    else
      bad "ModuleInfo.PACKAGE '$mi_pkg' ≠ applicationId '$appid'"
    fi
  else
    bad "找不到 $mi（包名挪过？这条校验会失效）"
  fi
done

# (5) 注入侧不得引用 androidx / COUI / 图标库 / 界面层 —— 那些类在 system_server 里
#    执行会在类加载期崩。判据是「这个类会不会在 system_server 里被执行」：inject/ 是
#    注入侧本体，core/ 是它依赖的共享层（同样在注入目标进程里执行），所以一并拦；
#    注意：debug / release 源集里也有 `core/` 的副本（`HostFilePerms`），同样在注入侧执行，
#    一并扫。
#    ui/（Compose + COUI）与图标库（com.composables / io.github.lyxnx）是禁引项。
#    bridge/ 是 2026-10-16 从 inject/ 拆出的「两进程共用的平台层」（系统桥 / 事实表 /
#    按键注入）：两侧共用、也在 system_server 里被加载，所以与 core/ 同待遇，一并拦。
#    ⚠️ 2026-10-17 起 bridge/ 又按**进程归属**分了三档：`bridge/`（两侧共用）、
#    `bridge/host/`（只有宿主侧引用）、`bridge/inject/`（只有注入侧引用）。
#    `bridge/host/` **不在这条扫描范围内** —— 它只在宿主 App 进程里跑，与 `host/` 同待遇
#    （拿它跟 system_server 一起拦会把宿主侧也当成注入侧，判据就错了）。
#    目录先各自确认存在：路径写错时 grep 会返回 2（错误），不加这道守卫会被当成「无命中」放过
injection_dirs=""
for p in \
  app/src/main/java/com/lookie/opluskey/inject \
  app/src/main/java/com/lookie/opluskey/bridge \
  core/src/main/java/com/lookie/opluskey \
  core/src/debug/java/com/lookie/opluskey \
  core/src/release/java/com/lookie/opluskey ; do
  if [ -d "$p" ]; then
    injection_dirs="$injection_dirs $p"
  else
    bad "找不到注入侧目录 $p（包名挪过？这条校验会失效）"
  fi
done
if [ ! -d app/src/main/java/com/lookie/opluskey/bridge/host ]; then
  bad "找不到 bridge/host/（进程归属分过档？这条 --exclude-dir=host 的排除会失效）"
fi
# 正则必须**括号配对**，而且不能拿 `|| true` 把 grep 的「出错」也吞掉：
#   grep 退出码 2 = 正则写坏 / 路径不存在，与「0 命中」（1）是两件事。这条闸原先
#   缺一个 `\)`，grep 报 `Unmatched ( or \(` 后退出码 2 被吞成空结果，于是「注入侧不许
#   引界面层」**一直在空转**、还照样打 ok。所以下面分开判 0 / 1 / 2。
#
# 但也不能就这么裸着赋给变量：这一步的 shell 是 `bash -e`（见日志里的 `-e`），而
#   grep 无命中时返回 1 —— 裸赋值会让整个脚本在那里**直接退出**，后面字典 / 封存两段
#   根本不跑，日志上只剩一句 `exit code 1`、连 ::error:: 都没有。所以用
#   `cmd || status=$?` 这种写法：`||` 列表不受 -e 约束，赋值照样生效，状态也留得下来。
injection_pattern="^import \(androidx\|io\.github\.suqi8\|io\.github\.lyxnx\|com\.composables\|com\.lookie\.opluskey\.ui\b\|com\.lookie\.opluskey\.ui\.\)"
scan_status=0
hits=$(grep -rn --exclude-dir=host "$injection_pattern" $injection_dirs) || scan_status=$?
if [ "$scan_status" -gt 1 ]; then
  bad "注入侧 import 扫描出错（grep 退出码 $scan_status）—— 这条闸不能空转"
elif [ -n "$hits" ]; then
  printf '%s\n' "$hits"
  bad "注入侧（inject/ bridge/ core/，不含 bridge/host/）引用了 androidx / COUI —— 见 AGENTS.md 一、1"
else
  echo "  ok  注入侧无 androidx / COUI import"
fi

# (5)' `KeyModule.kt` 单列一档：它在注入侧会被加载，但它
#    **允许** import `ui/`（界面 hook 只在设置页那个进程分支里装，见它的类注释）
#    —— 直接并进上面那圈扫描会误报。这里只禁「一加载就崩」的那三类。
km="app/src/main/java/com/lookie/opluskey/KeyModule.kt"
if [ -f "$km" ]; then
  kmhits=$(grep -n "^import \(androidx\|io\.github\.suqi8\|io\.github\.lyxnx\|com\.composables\)" "$km" || true)
  if [ -n "$kmhits" ]; then
    printf '%s\n' "$kmhits"
    bad "KeyModule.kt 引用了 androidx / COUI / 图标库 —— 它在 system_server 里会被加载"
  else
    echo "  ok  KeyModule.kt 无 androidx / COUI / 图标库 import"
  fi
else
  bad "找不到 $km（包名挪过？这条校验会失效）"
fi

# (6) 字典覆盖：`Strings.kt` 里的每个键必须在**两份**字典里各有一条，且两份的键集合相同。
#    为什么这条要卡在 CI：界面上的兜底是「查不到就显示键本身」（见 `I18n.get` 与
#    `ActionCatalog.Spec.label`）—— 漏一条键，界面上就是一行 `catalog.xxx`，
#    只在切到那种语言时才看得见；两份字典不一致时，另一种语言下露键名。
strings_kt="core/src/main/java/com/lookie/opluskey/core/i18n/Strings.kt"
zh="app/src/main/assets/locales/zh-CN.json"
en="app/src/main/assets/locales/en.json"
missing_file=""
for f in "$strings_kt" "$zh" "$en"; do
  [ -f "$f" ] || missing_file="$missing_file $f"
done
if [ -n "$missing_file" ]; then
  bad "找不到字典文件：$missing_file（路径挪过？这条校验会失效）"
else
  # 键的形态是 `小写字母 / 数字 / 点 / 下划线`，所以这一条正则只会抽到键，不会抽到 KDoc 里的散文
  k_src=$(grep -oE '"[a-z0-9_.]+"' "$strings_kt" | tr -d '"' | sort -u)
  k_zh=$(grep -oE '"[a-z0-9_.]+":' "$zh" | tr -d '":' | sort -u)
  k_en=$(grep -oE '"[a-z0-9_.]+":' "$en" | tr -d '":' | sort -u)
  miss_zh=$(comm -23 <(printf '%s\n' "$k_src") <(printf '%s\n' "$k_zh") || true)
  miss_en=$(comm -23 <(printf '%s\n' "$k_src") <(printf '%s\n' "$k_en") || true)
  extra_zh=$(comm -13 <(printf '%s\n' "$k_src") <(printf '%s\n' "$k_zh") || true)
  if [ -n "$miss_zh" ] || [ -n "$miss_en" ] || [ -n "$extra_zh" ]; then
    # 这三条不能写成 `[ -n "$x" ] && printf …`：`bash -e` 下「条件不成立」会让整条
    #   `&&` 列表返回 1 ⇒ 脚本在这里退出，于是**该报的那一条反而看不到**。
    if [ -n "$miss_zh" ]; then printf 'zh-CN.json 缺键：\n%s\n' "$miss_zh"; fi
    if [ -n "$miss_en" ]; then printf 'en.json 缺键：\n%s\n' "$miss_en"; fi
    if [ -n "$extra_zh" ]; then printf '字典里有而 Strings.kt 没有的键：\n%s\n' "$extra_zh"; fi
    bad "字典与 Strings.kt 对不上（界面会直接显示键名）"
  else
    echo "  ok  字典覆盖：Strings.kt / zh-CN.json / en.json 三边键一致（$(printf '%s\n' "$k_src" | wc -l | tr -d ' ') 条）"
  fi
fi

# (10) 封存：release 是「发布形态」—— 不许出现明文收款码 / 名单，且必须有容器；
#    debug 反过来（它的图就是明文，靠 `app/seal` 那条 assets 目录进的包）。
#    守的是「封存任务没跑起来 / 被绕过」——那种包照样能出、能装，只有把包解开才看得出来。
for apk in "$DIR"/*-release.apk; do
  [ -f "$apk" ] || continue
  echo "校验封存 $apk"
  listing=$(unzip -l "$apk")
  if printf '%s' "$listing" | grep -qE 'assets/donate/|assets/donors\.json'; then
    printf '%s\n' "$listing" | grep -E 'assets/donate/|assets/donors\.json'
    bad "$apk 里有明文收款码 / 名单 —— 封存没生效"
  else
    echo "  ok  发布包里没有明文收款码 / 名单"
  fi
  if printf '%s' "$listing" | grep -q 'assets/sealed.bin'; then
    echo "  ok  发布包里有 assets/sealed.bin"
  else
    bad "$apk 缺 assets/sealed.bin（封存容器没生成）"
  fi
done

for apk in "$DIR"/*-debug.apk; do
  [ -f "$apk" ] || continue
  if unzip -l "$apk" | grep -q 'assets/donate/wechat.webp'; then
    echo "  ok  debug 包里是明文收款码（app/seal 那条资产目录接上了）"
  else
    bad "$apk 里没有 assets/donate/wechat.webp —— debug 的明文资产没接上"
  fi
done

if [ "$fail" != "0" ]; then
  echo "::error::验收底线未通过 —— 见上面的 ::error:: 条目"
  exit 1
fi
echo "验收底线全部通过"
