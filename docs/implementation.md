# 实现原理

原版 `OPlusKey` 的按键接管是「独占输入设备 + uinput 转发」：

```
/dev/input/event*  →  daemon 独占 grab  →  自己判定单击/双击/长按
                                        →  uinput 转发不拦截的事件
```

这条路能做，但有三个天生的问题：

1. **需要 root daemon**，还要一层 supervisor 看护循环（`service.sh`）保证它不被杀；
2. **独占设备会漏事件**：触摸屏如果声明了 `KEY_POWER` 也被 grab 掉，`EV_ABS` 收不到，触摸就失灵 ——
   daemon 里得专门写一条「跳过触摸设备」的规则；
3. **要维护一套 C 代码 + NDK 交叉编译**。

本版换的那条路：

```
system_server 收到按键
  →  ColorOS 专为侧键写的策略类 StrategyActionButtonKeyLaunchApp 被调用
     （系统设置 → 侧键 背后的实现）
     →  我们 hook 这个方法
        →  启用了 → 记按下/抬起 → 判定手势 → 执行动作
```

接管点是**按候选表逐层降级**挑出来的（`bridge/Framework.kt`）：

```
1. StrategyActionButtonKeyLaunchApp      ← ColorOS 专为侧键写的策略类，首选
2. PhoneWindowManagerExtImpl             ← OPPO/一加对 PhoneWindowManager 的扩展
3. PhoneWindowManager                    ← AOSP 兜底 interceptKeyBeforeQueueing
```

因此：

- 不需要 root（模块跑在 system_server 里，本来就是 system uid）；
- 不需要 uinput，不碰 `/dev/input`，不会影响触摸；
- 不需要 C 代码与 NDK。

代价是**崩溃范围从「输入法起不来」变成「整个系统起不来」**（开机循环），所以注入侧的纪律（AGENTS.md 五之二）比原版严格得多。

### 只管内置的侧键（自定义键另算）

`KeyId` 的**内置**清单只有 `PLUS` 一个分量。原版把电源键与音量键也做成内置键，本版不这么排：

- 电源键要保系统电源菜单与紧急呼叫；
- 音量键要保组合键（上下同按 = 截屏）与长按连调。

但**用户可以自己收集**自定义键（最多 `KeyId.MAX_CUSTOM_KEYS` = 3 颗），音量±与电源在可选范围内
（用户口径）。收下之后那几颗键归模块管、没收集时照常放行给系统；它们注入的那一下仍由
`KeyId.INJECTABLE_CODES` 认出来放行，所以不会形成自持循环 —— 不许收的是
`KeyId.RESERVED_CODES`（主屏幕 / 返回 / 媒体 / 截图 / 通知中心 / 最近任务 / 静音）。

### 两道开关：模块总闸 与 每颗键

接管与否是**两级相与**，判定侧只有一个入口（`inject/ConfigChannel.enabledOf`）：

| 层级         | 字段                   | 界面入口                                   | 关掉的后果                                             |
| ------------ | ---------------------- | ------------------------------------------ | ------------------------------------------------------ |
| **模块总闸** | `GlobalConfig.enabled` | 首页「启用按键绑定」那一行                 | 一颗键都不接管，页面整片灰，常驻提示「按键绑定已关闭」 |
| **这一颗键** | `KeyConfig.enabled`    | 「按键绑定」页 ⋮ 菜单「停用 / 启用这颗键」 | 只这一颗不接管，模块与其它键照常                       |

- 总闸**打开之前**要过免责声明（`Config.disclaimerAccepted`），弹窗只在首页（`HomeDisclaimers`），
  确认后由 `acceptDisclaimerAndEnable` 一次写完「记录 + 总闸」；
- 逐键那一行**每颗键都有**（侧键也有），一步到位、不弹确认 —— 它不丢数据，菜单里一字之差就能改回来；
- 停用的键在菜单清单里名字后面挂一枚 `pointer-cancel` 图标（`MenuEntry.Row.stateIcon`，走 COUI 的
  `hint` 槽，那一槽只有 40dp，所以只能是图标不能是文字）；
- **老配置的迁移**：拆开之前「总闸」就是 `keys.plus.enabled` 那一位。`ConfigDto` 读到没有
  `global.enabled` 的文件时，把总闸取那一位的值、把侧键这一颗写成「开着」——
  否则升级后用户打开总闸会看到「开了没反应」（侧键还是关的）；
- 导出 / 导入：只把总闸写成关、每颗键一律写成开（`ConfigBundle.withShareableKeys`）。

侧键 keyCode 只在 `core/model/KeyId` 里定义：`KeyId.keyCode` = 735（`KEYCODE_ACTION_BUTTON_CLICK`，Linux 侧是
`BTN_TRIGGER_HAPPY32`），另有一份白名单 `KeyId.acceptedKeyCodes`（=
`{735, 780, 781, 782}`，含 ColorOS 策略类路径的内部注入码，781 的补齐记录见 `device-findings.md` §14.4）。

第一层 `StrategyActionButtonKeyLaunchApp` 确实存在，落在 `oplus-services.jar`，入口是外层
`actionInterceptKeyBeforeQueueing` —— **只挂外层**：内部回调 `$2`
是它的纯转发（§14.2），挂它只会让同一次按键被回调两次（2026-10-13 起 `Framework.kt` 不再挂），

（`ActionButtonProvider` + SQLite）。**完整的类名 / 方法签名 / URI / 表结构记录在
[`docs/device-findings.md`](device-findings.md)** —— 改 Hook 面之前先读它，不要再靠猜。

## 二、按键是怎么变成手势的

daemon 的判定是 `poll()`
驱动：它自己收原始事件，超时醒来就知道「这段时间没有新事件」，所以能直接判「长按到点了」「双击窗口过了」。

LSPosed 这边拿到的只是「framework 调了一次侧键策略方法」，所以判定必须**改成定时器驱动**（`inject/input/GestureDetector.kt`）：

```
按下 → 起一个「长按计时器」(longPressMs)
     同时清掉双击窗口

长按计时器先到 → 判长按，抬手不再触发
抬手 → 起一个「双击窗口计时器」(doubleClickMs)
窗口内又来一次按下 → 判双击
窗口到了还没有第二次 → 判单击
```

定时器全跑在 system_server 的 `Handler` 上，**回调里绝不阻塞**
（一次按键处理必须立刻返回，否则整个系统的按键都会变卡）。

**时间基准一律 `elapsedRealtime()`；熄屏排双击窗口时持一段唤醒锁把 CPU 顶住。**
`uptimeMillis()` 在深度睡眠里不走，而抬手之后设备会立刻睡：双击窗口那一枪（`postDelayed`）不到点，
窗口的截止时刻却还停在「刚刚」—— 下一次按下（真实过了几秒）会被判成双击，前一下的单击则永远不派发
（真机：熄屏按一下没反应，4.6 秒后按第二下却判成双击）。所以所有窗口与比较都走 `elapsedRealtime()`；
并且 `PowerManager.isInteractive` 为假时，排窗那一步先要一段**带超时**的 `PARTIAL_WAKE_LOCK`
（窗口 + 500ms 余量，不计引用数、不主动放），让窗口那一枪在 uptime 上真走到点。

- **代价**：熄屏单击要晚一个双击窗口（`doubleClickMs`）才执行。曾经是「熄屏抬手当场判单击」，
  熄屏即时响应但双击整档作废（熄屏下每一下都是单击）—— 双击不能没有，改走顶醒 CPU。
- 取不到 `PowerManager` 时静默降级回「熄屏判不出双击」，按键本身不受影响；判定侧不为了顶醒 CPU
  把异常抛回输入线程。
- 唤醒锁由装配方注入（`KeyHooks.holdAwake` 借 `SystemContext` 取服务），判定器不碰 Context。

### 「松手后再执行」（长按之后松手那一刻的第二个动作）

长按那一档可以再挂**一个独立的动作**（`KeyConfig.longRelease`，由开关 `longReleaseOn` 管着）：
按住到长按时长先执行长按动作（照旧），松手那一刻再执行这一个 —— 两个事件分开、各自执行、各配各的。

```
长按计时器到点 → 派发 long（LongPress，照旧，可连发）
抬手（这一按已判成长按）→ 若 releaseBound（开关开 + 已挑动作），再派发一条 release
```

- `release` 是一档**触发条件**（`GestureTrigger.Release`，id 就是 `release`），不是第四档手势：
  它不参与连击链、不进条件分支（`StateRule` 只认三档），执行侧由 `KeyConfig.actionOf(Release)`
  直接取 `longRelease`。
- **开关与动作是两位**（`longReleaseOn` / `longRelease`）：开着但还没挑动作是合法状态 —— 松手
  什么都不做。合成一位（「有动作 = 开」）的话，用户在开关上点一下就会立刻执行一个他没挑过的动作。
  两处都只认 `KeyConfig.releaseBound`（= 开关开 && 动作非 none && 没开「松手才触发」）。
- 与「长按连续触发」**互斥**：判定侧那一条判据收在 `KeyConfig.longRepeats`（`releaseBound` 时
  不排重复枪），界面把连续触发那行**置灰但不隐藏**、值原样留着。
- 没配松手动作时这条广播根本不发 —— 行为与这一位出现之前逐字一致。
- 组合键那一档**还没有**这一项：判定侧只发「哪一档、短按还是长按」，没有松手那条通道，
  所以组合那张卡上不画这一行（摆一个拨了没反应的开关比没有更糟）。

### 「松手才触发」（长按的时序：到点即触发 / 等抬手才触发）

长按本来是**一到阈值就执行**。`KeyConfig.longFireOnRelease` 换成另一种时序：到点只把这一按
**标成长按**，动作等**抬手那一刻**才执行一次。

```
长按计时器到点 → 只置 longPressFired（不派发、不排连发）
抬手（这一按已判成长按）→ longFireOnRelease ⇒ 派发 long 一次
```

- **三条时序只能占一条**：到点即触发（默认）/ 松手才触发（本条）/ 松手再执行另一个动作
  （上一节）。互斥收在数据层两位属性里，判定侧与界面都只读它们：
  `longOnReleaseTiming = longFireOnRelease || releaseBound`（`longRepeats` 据此不排连发）、
  `releaseBound` 本身也被 `longFireOnRelease` 排掉（两条都要用「松手那一下」，同时开着就是
  同一刻执行两个动作）。
- 只**置状态位、不派发**是关键：计时器那一枪要是不置 `longPressFired`，抬手会把它当短按再补一次
  单击 —— 一次按压执行两个动作。抬手收尾统一走 `GestureDetector.finishLong`，三种时序在那儿分开。
- **UP 丢了怎么办**：这一档把唯一一次执行押在抬手事件上，UP 一丢就等于「这颗键坏了」。所以到点
  之后再排一枪 `RELEASE_FALLBACK_MS`（15s）：还按着就按 UP 已丢处理 —— 就地执行一次，并在
  `Pending.closedWithoutUp` 上把这一次按压**封掉**；那个迟到的 UP 走 `finishLong` 时只清状态，
  不会执行第二次。谁先到谁负责（两侧都在 `synchronized(p)` 里判，先到的赢）。
- **执行侧免检过期**：`release` 走到 `GestureExec.handle` 的过期闸时传 `keepLate = true` ——
  积压的按下可以丢（丢的是「再来一下」），丢一条松手却是「整次按压零响应」。去重那半照旧
  （转发侧的补发 `sent_at` 相同，仍会被挡掉）。
- 拨这一位**不清** `longReleaseOn` / `longRelease`：松手动作让位但值留着，关掉这一位之后原样回来
  —— 切换时序不该顺手把用户挑过的动作扔掉。
- 界面：这一行排在长按那一档的**最上面**（它管「什么时候执行」，下面两族管「执行几次 / 松手还
  做不做别的」）；开着时「松手后再执行」与「长按连续触发」都**置灰不隐藏**，灰着点一下会说明原因
  （`DisabledHint`）。
- 组合键那一档同样没有这一项（`LongPressSpec.releaseSupported = false`），那一页整行不画。

## 三、「交还系统」为什么不提供（2026-10-14 删除）

模块曾经有一档动作叫**「交还系统」**（`native` / `passthrough`）：选了它，按下去就不拦这个键、让系统那套照常跑。
**这一档已经整条删掉**，因为它要的语义在原理上做不到，而且失败方式很难看。

**做不到的原因：判定手势必须先把 DOWN 吞掉。**

侧键的单击 / 双击 / 长按是**我们自己在拦截点里数出来的**（见第二节：DOWN 之后排计时器、UP 之后排双击窗口）。也就是说：只要还想让双击 / 长按这两档生效，DOWN 就必须被吃掉。而「交还」要的是**这一次按键原样给系统**
——两者不可能同时成立：

- 真放行（`return false`，原事件给系统）⇒ 这次按压整键都归系统，**双击 / 长按上绑的命令一起失效**；
- 吞下来再补一次注入（旧实现）⇒ 补出去的是**合成事件**（`deviceId = -1`），ROM 那侧不一定认它，用户看到的是「按下去没反应」，而且它会再次触发框架那条
  `what=1` 场景派发。

用户报的现象正是这两条各占一半：**「交还系统无效，且直接拉回模块页」**——无效来自合成事件不被认，拉回模块页来自那条被放行的场景派发（框架给宿主的那条的效果是「宿主打开设置页」，而那一页被我们替换掉了）。

**为什么不保留「整键交还」这一档：** 它只能表达「这把键我不要了」，而那正是**主开关**的语义（`KeyConfig.enabled = false`
⇒ 拦截路径整条不生效、行为与原生完全一致，见第一节）。再留一档动作表达同一件事，只会让人以为「可以只交还单击」，然后在双击 / 长按上踩空。

**删除范围**（不留兼容，配置格式还没发布）：

- 动作类型 `Action.Native` / `Action.Passthrough`，以及网格里那一格（`ActionCatalog`）；
- 判定侧的放行回调（`GestureDetector.onPassthrough` → `GestureForwarder` 的 `PHASE_PASSTHROUGH` 广播）；
- 宿主侧的「重注入一次按键」（`KeyInjector.injectForForward`）与自持循环兜底闸（`GestureReceiver`）；
- 宿主侧「放行时把场景派发放过去」那一整套配额 / 扣留补投机器（`SideKeyGestureHooks`）——现在那条场景派发**一律吞掉**，唯一例外是主开关关着时（模块完全让位）。

**副作用（也是修好的两件事）：**

- 那条 `what=1` 场景派发不再有条件放行 ⇒ 按侧键不会再莫名其妙把设置页拉起来；
- 单击那档的默认值从 `native` 变成 `none`（无操作）。想「回到原生」用主开关，不要用动作档。

## 四、配置从哪来、怎么到注入侧

配置的唯一生效来源是**宿主私有目录里的一个文件**（`/data/user_de/0/com.oplus.gesture/files/opluskey_config.conf`，读写走
`core/config/ConfigStore`，内容格式见 `core/config/ConfigCodec`）：

- **磁盘上不是裸 JSON**：外面套了一层混淆外皮（`core/config/FileObfuscation`）—— 首行魔数 `OPlusKey-Runtime-1`，
  之后是正文经**两层**变换压成的一行：先「异或 + Base64」，再按一张替身字母表逐字符换写、按位置错开。
  命令库（`opluskey_commands.conf`）同一层皮。这不是加密（密钥与字母表都写在代码里），
  只是挡住「随手打开顺手改坏」；导出包（`.bin`）用的是同一种皮、魔数不同名。
  所以**手改配置文件要连着两层一起改**（解两层、改完再包两层，配方见 `FileObfuscation` 类注释）。
  正文顶层另有 `md5` 字段（`ConfigCodec`）：它是**把这一格摘掉之后**那份 JSON 的 MD5，
  算之前先把 map 段落（`keys` / `app_names`）按键名排序 —— 于是缩进、换行、以及同一段里
  两行的先后都不影响摘要；改内容不重算仍判读不懂。本版之前写出的文件按原始顺序校验，升级不失效。
  首行魔数分两档（运行时 `OPlusKey-Runtime-1` / 导出包 `OPlusKey-Config-1`）；**不做旧格式兼容**：
  首行对不上、或正文认不出（加第二层之前的旧文件）就是读不懂。开发期残留的文件删掉即可 ——
  不退回「当它没写过」，那会把用户配置整份换成默认值（见 `ConfigStore.Load` 的三态）。
- 位置取宿主私有目录的 **DE（设备加密）** 那一份，**不是 CE**：CE 首次解锁前不可见，开机到解锁之间只读得到默认值（见
  `docs/device-findings.md` 9.9）。
- **宿主进程**（设置页、手势执行）每次按键 / 保存都读一遍配置 —— 同 uid 直接可读，改完即时生效。
  读是**按文件指纹缓存**的（`core/config/ConfigStore.load`：比对修改时间 + 长度）：同一份文件在
  一次按键里可能被读好几次（手势执行一次、顺带那次报活再一次），不缓存就要重复「读盘 + 剥外皮 +
  JSON 解析」。文件一被写，指纹就变，下一次读立刻重解析；本进程自己的写盘另外主动失效。
- **注入侧（system_server）读不到那份文件**（EACCES），它只信宿主广播过来的完整配置（`core/wire/ConfigBroadcast` →
  `inject/ConfigChannel`），启动 / 解锁 / 写盘时各发一次。
- 系统「设置 → 快捷键」的 provider（`content://uri.gesturemanagerprovider/action_button`）只是**只读诊断项**
  （`bridge/host/SystemActionButtonConfig`，在宿主进程里读），不参与配置链路。两条纪律：
  **列名一律先读 Cursor 拿**（随 ROM 版本变过，不许硬编码）；**只能在宿主进程里调** ——
  注入侧用它必 NPE（见 `device-findings.md` 六之五）。写接口**已删**（零调用方），真要用的时候直接
  `ContentResolver.update/insert` 三行就够；不要再往回加绕开 `ContentResolver` 的重反射。

不能用 `SharedPreferences`：它每个进程各缓存一份，写进去的值别的进程看不到。

### 注入侧代取网络（`inject/NetChannel`）

界面代码跑在**宿主进程**，而宿主的清单里没有 `INTERNET`（`reference/gesture.apk` 的 49 条权限里
只有 `ACCESS_NETWORK_STATE`）—— 页面自己发请求必然 `SecurityException`。要出网只能请注入侧代取：
system_server 这一侧实测能连（`device-findings.md` 四十一）。

- 线格式（`core/wire/ModuleWire`）：`ACTION_NET_REQUEST` 问、`ACTION_NET_RESULT` 答，两条都带
  `EXTRA_NET_ID` 配对（可能并发几条）。结果里 `EXTRA_NET_STATUS` = HTTP 码（0 = 没连上）、
  `EXTRA_NET_BODY` = 正文、`EXTRA_NET_ERROR` = 原因。
- **只做 GET、只认 https**、正文截到 `NET_BODY_LIMIT`（32 KB，binder 事务上限）、
  连接与读取各 `NET_TIMEOUT_MS`（8 秒）。这条通道能干的**正好**是「读一段公开文本」，
  多一样能力就多一份被别的代码借去干别的事的机会。
- **DNS 必须走 `Network#openConnection`**：system_server 的 socket 没有「进程默认网络」，
  裸 `HttpURLConnection` 会 `UnknownHostException`（四十一）。先 `ConnectivityManager.activeNetwork`
  取网络，再由它开连接 —— 解析与连接都绑在那张网络上。
- 纪律：**另起线程**（这条广播在 AMS 入口上，阻塞 IO 放主线程等于停住广播分发）、整段兜
  `Throwable`、回包 `post` 到主线程再 `sendBroadcast`（不在 hooker 体内重入 AMS）。
- 没有默认网络时**直接回一条「当前没有网络」**，不去等超时。

## 五、注入侧引用的红线

`inject/` 下的一切都会随注入进入 system_server，因此：

| 允许                                                                            | 禁止                                                                                    |
| ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Android framework **公开** API（`KeyEvent` / `Context` / `Handler` / `Cursor`） | `com.android.server.*` / `android.hardware.input.*` 的**直接引用**                      |
| 按字符串名反射找类（`Reflect.findClass`）                                       | `import androidx.*` / Compose / COUI（判据是「这个类会不会在 system_server 里被执行」） |
| `core/`（model/log/reflect/wire）+ `KeyModule`（只依赖公开 API）                | `inject/` 下引用 `androidx.*` / COUI，或引用 `core/config` / `host/` / `ui/`            |
| 候选类名只写在 `bridge/Framework.kt` 的候选表里                                 | 在 `inject/KeyHooks.kt` 里就地写类名字符串                                              |
| 系统配置只经 `bridge/host/SystemActionButtonConfig.kt`                          | 在别处自己拼 `content://` 或硬编码列名                                                  |

framework 私有类**一律按字符串名解析**：它们会被 ROM 改名，写死引用等于「换一个 ROM 版本就 `NoClassDefFoundError`
→ 开机循环」。 `StrategyActionButtonKeyLaunchApp` 就是这一类 —— 它在 `com.android.server.policy` 与
`com.oplus.server.policy` 两个包里都出现过，所以两个包名都要试。

`app/build.gradle.kts` 里**故意不声明**这些依赖 —— 这样误加的直接引用会变成**编译错误**，

**`androidx` / Compose / COUI 是进包的**（2026-10-11 起）：`ui/` 那一层用 Compose + COUI 渲染设置页。这不违反红线 ——
`ui/` 的类只在宿主设置页进程里执行，system_server 里从不执行，而 ART 的类解析是惰性的。要守住的判据是
**「这个类会不会在 system_server 里被执行」**：`inject/` 不许出现 `import androidx.*` 或
`io.github.suqi8.coui.*`（复核命令见 AGENTS.md 二、1.2）。

## 六、降级设计

- 每个 Hook 单独装，装不上只记日志（`XLog.guard` / `hookGuarded`），不影响其他 Hook；
- 候选接管点**一个都找不到**（ROM 改了类名）→ 模块完全不生效，但系统照常；
- 找到了策略类但方法签名对不上（ColorOS 版本差异）→ 退到下一层候选，三层全落空才放弃；
- 配置读不到 → 退回「侧键不接管」；
- 每个动作执行失败 → 只记日志，不影响这次按键的其他环节。

## 七、「自定义命令」页的两条界面取舍（2026-10-14）

### 7.1 「放弃未保存的命令」问在**退出**时，不是进页时

- 触发点：`SettingsPage.goBack()` —— 当前页是「自定义命令」且 `draft.dirty` 为真时**拦住不退** （置
  `confirmLeaveCommands` 并返回 `true`），由该页自己画出 `ConfirmDialog`；确认 ⇒ `draft.reset()` +
  `popBackStack()`（**绕开 `goBack()` 的拦截**，否则会在原地再弹一次自己），取消 ⇒ 关掉窗、留在页里继续编。
- 为什么挪：原来那一问在「按键绑定」页那颗 FAB 上（进页前），配套的是「进页即
  `draft.reset()`」。两条路都别扭 —— 进页被拦一下，而真正**丢掉东西**的那一下（退出）反倒没有提示，且「进页即清」本身是一条**静默丢草稿**的路。现在进页永远直接进，草稿只在用户确认放弃时清。
- ⚠️ **判据是 `draft.dirty`，不是 `hasContent`**：`dirty` 与 `load(cmd)`
  时记下的那份**快照**逐项比。挪到退出之后这一问会被问得频繁得多（每次返回都过一遍），拿 `hasContent`
  会让「点开一条已保存的命令看一眼、原样返回」也挨一句「放弃未保存的命令」—— 那句话是假的。保存成功后 `reset()`
  会把快照一起清掉，所以「改完存好再返回」也不会多问。
- 进页**不再清草稿**：唯一会留下旧草稿的路是「清栈回首页」（⋮ 菜单的「默认界面」/ 从系统设置重进，见
  `onHostReentered`），那条路上不丢用户的东西、下次进来还能接着改，比静默清空好。

### 7.2 「支持的命令格式」只列**没有内置入口**的格式

那张卡（`COMMAND_FORMAT_LINES`）讲「命令输入框里能写什么」，带前缀的行点一下会把前缀填进输入框：输入框里**已经带了支持的格式**时只换开头那一段前缀、正文留着（`shell:pm list` 点 `keyevent:` ⇒ `keyevent:pm list`，`Action.prefixOf` 认那一段），不带格式的别的内容则**整段替换**并先问一句（不是追加 —— 拼在已有内容前面得到的是一条跑不通的命令）。收录规则：

| 格式                                                      | 在图里                                                          |
| --------------------------------------------------------- | --------------------------------------------------------------- |
| `shell:` / `keyevent:` / `intent:` / `shortcut:` / `cmd:` | ✅ 有（没有图形入口，只能手写）                                 |
| `app:` / `window:`                                        | ❌ 无 —— 内置「打开应用」那一格就是它（小窗是那格菜单里的开关） |
| `zoom:mini` / `zoom:mini:collapse`                        | ❌ 无 —— 内置「系统小窗」那两档就是它                           |

判据一句话：**同一个能力已经有图形入口的，这张卡不再重复教手写**
—— 两个入口并存时，手写那条更容易写错（前缀拼错、包名写错都只会得到一条跑不通或行为不同的命令）。

## 八、适配清单：机型 + 系统版本

清单只回答「这一组验过没有」，**不拦任何东西**。清单本体是 `core/model/DeviceSupport` 的 `ADAPTED`，
判定是一个**纯函数**：只吃 `model` / `android` / `rom` 三个字符串，`Build` 那三个值由各调用点自己传。

- **ROM 版本从 `Build.DISPLAY` 取**，不读 `ro.build.version.oplusrom` —— 后者是 hidden 接口，
  界面进程会被非 SDK 限制拦下（与 `ro.vendor.oplus.market.enname` 同一类）。
- 机型名照抄真机属性 `ro.vendor.oplus.market.enname`（`OnePlus 15`），同样不在运行时读。
- 当前清单只有一档：`PLK110` + `Android 17` / `ColorOS 17.0.0`。
- **任何机型、任何系统版本都照常注入**：system_server 里的按键接管、宿主侧的手势劫持与接收器、
  设置页替换全都装。清单外只意味着「没验过」，不是「不能用」。
- 提示只有一处：**「更多选项」页那张卡里的「本机」行**。三档措辞分别是「已适配」/
  「未验证过这一版系统，可能不可用」/「未在适配清单里，可能不可用」。首页那条常驻提示**不再**
  有这两档（页面也不会因为机型整页禁用）。
- 判据行是 `本机机型：…；系统：…；允许运行=…`；清单外时紧随其后一句 `… ⇒ 照常注入（清单外的机器可能不生效）`。
- **要加机型或加一版 ROM**：加一行 + **在真机上把接管点重新验一遍**。那些类与方法都是 ROM 私有的，
  换机型 / 换 ROM 版本会整体换名换形，那种情况下模块会「拦下来但什么都不做」，比直接不支持更难排查
  （见 `device-findings.md` 十九）。

### 构建档位

| 档         | 落点允许猜 | 调试通道 | 详细日志 | 防篡改 | 签名 |
| ---------- | ---------- | -------- | -------- | ------ | ---- |
| debug      | 开         | 开       | 开       | 无     | 调试 |
| release    | 关         | 关       | 关       | 有     | 发布 |
| releaseLog | 关         | 关       | **开**   | 有     | 发布 |

`releaseLog` 是给用户排查用的「正式包 + 详细日志」：与 `release` 只差 `VERBOSE_LOG = true`
（`XLog.d` / `XLog.i` 重新进包，其余一样 —— `DEBUG_PROBE` / `STRICT_UI` 仍为 false）。产物名与
「应用信息」里的版本带 `-log` 后缀。它同样需要 `:core` 声明同名 buildType，否则变体匹配失败。

判据不是「运行时判断」而是**源集文件是否存在**：`core/src/{debug,release,releaseLog}/java/com/lookie/opluskey/core/BuildFlags.kt`
各一份同名常量，打包时只有一份进产物；`SealPin` 由 `:core` 的 `SealReleaseAssets` 为 `release` / `releaseLog` 生成
（`core/src/debug` 那份是 `SEAL_ENABLED=false` 的明文直通）。
复核：`ls core/src/{debug,release,releaseLog}/java/com/lookie/opluskey/core/BuildFlags.kt` 三份都在；
两档区分是否真的生效，`python tools/check_variants.py` 一次跑完（它按「该串只应出现在哪一档」逐项判定）。

- **两条 workflow，各管一段**：`.github/workflows/ci.yml`（推 master / PR）只做「构建 + 验证 +
  留产物」，里面没有任何发布步骤；`.github/workflows/release.yml`（打 tag）只做「取 + 验 + 发」。
    - `release.yml` 按**提交 SHA** 去 `ci.yml` 在 master 上那条 run 取产物（`gh run download`），
      本 job 里再用 `tools/verify-apk.sh` 把两个包解一遍才发 —— 同一份提交不构建两遍。
    - tag 常紧跟 master 推，两条 run 并发，所以取产物前先等：**那条 run 还在跑就一直等**（构建没跑完是常态，
      不该按分钟数掐断）；「没有这条 run」（给 60 秒宽限再判，刚推完 master 时 run 可能还没被创建出来）、
      那条 run 失败、或产物过保留期（release 包留 90 天）—— 一律**红**，不退回自己构建（构建只归 `ci.yml`）。
    - 查询带 `branch=master`：推 tag 本身也是 push 事件，不带它会把别的 ref 的 run 捞进来。
    - `release.yml` 的 `concurrency` 是 `cancel-in-progress: false`：发布半路被掐会留下
      「tag 推了、Release 没建」。
    - 版本号由 `tools/read-version.sh` 读（两条 workflow 共用一份，抓不到就 fail）；取完产物先判
      「必须有本版的 `*-release.apk`」—— 缺了就直接说「多半是 `signed=false`（Secrets 缺
      `KEYSTORE_*`）」，别等到建 Release 那一步才抛一句含糊的 `no files found`。
- 发到模块仓库的只有 `release`：`versionName` 带 `-` 的按预发布发，包体不变。没配签名密钥时只出
  debug —— 封存层依赖签名派生密钥，没有明文回退（这一档 `release.yml` 取不到 release 包，发不出去）。

### 发版：版本号与 tag

版本号只有两处手写，其余全由 CI 读出来：

| 位置                                                    | 写什么                          | 什么时候动                             |
| ------------------------------------------------------- | ------------------------------- | -------------------------------------- |
| `app/build.gradle.kts` 的 `versionCode` / `versionName` | 用户看到的版本 + 单调递增的整数 | 每次发版                               |
| `core/ModuleInfo.kt` 的 `BUILD_ID`                      | `yyyy-MM-dd-NNNN`               | 每次出包（它是「换了包没重启」的尺子） |

**tag 必须是 `<versionCode>-<versionName>`（如 `3-1.0.0`）。** 不是为了好看，是因为管理器要拿它算更新：

`RepoLoader.updateLatestVersion()` 对模块仓库里那条 Release 的 tag 做 `split("-", 2)`，
第一段 `Long.parseLong` 当 **versionCode**、第二段当 **versionName**，再和手机上已装的比
（`upgradable()` 要求**严格大于**）。切不出两段或第一段不是数字时，那一行直接 `continue` ——
**模块能装，但管理器永远不会提示有新版本**。所以 `v1.0.0` / `1.0.0` 这种写法不要用。

- **更新公告是手写的一份文件**：`release-notes/<versionName>.md`（如 `release-notes/1.3.4.md`）。
  钉版（`release.yml`）与上架（`publish.yml`）都只读它，再各自接一段版本行与包清单 ——
  管理器弹「有新版本」时显示的正文就是这段字，本仓库存档那条也用同一份，两处不会有两种说法。
  缺这个文件两条流水线都直接红：公告是发版物的一部分，缺了弹窗里就是一片空白。
  不从提交里拼的理由：提交信息是写给维护者的（「收口」「对齐」），弹窗里要写给用户。
- **推 tag 只「钉版」，不推模块仓库**（`release.yml`，卡 `refs/tags/`）。推上去之前确认
  版本号已经提交：Release 的 `tag_name` 是 CI 从版本号算出来的，与手推的 tag 名字不一致就会分叉。
- **钉版与上架是两条流水线、两个时刻**：
    - `release.yml`（推 tag 触发）只把包存进**本仓库**的 Release —— **历史存档**，两个包都放
      （`release` + `debug` + `releaseLog`），tag 用**这次推上来的那个**（`github.ref_name`），
      不拿版本号重算：否则推 `v1.0.0` 时本仓库会凭空多出一条 `4-1.1.0` 指着同一个提交。
    - `publish.yml`（**手动触发**，填 tag）才把那一版推进
      `Xposed-Modules-Repo/io.github.lo0kie.opluskey` —— **更新通道**，管理器只读它，只放
      `release` + `releaseLog`。包从存档那条 Release 下，**不重新构建**：上架的就是当初钉住的那一份。
    - `release.yml` 存完再**同步一份到 Gitee**（`look1e/opluskey-lsposed`，国内镜像）—— 同一个 tag、
      同一批包，走 `tools/gitee-release.sh`。它**不进更新通道**：管理器不读它，Gitee 那份只是第二个
      下载点。同一个 tag 重跑只更新正文、已传过的附件跳过。
    - 为什么分开：管理器一旦读到模块仓库那条 Release，所有装过的人立刻收到更新提示 —— 那不该跟
      「我打了个版本」绑在同一刻发生。钉完版继续写新功能，择日再上架。
- 前置条件，缺了只是**静默跳过**、日志里不报错：Secrets 里 `KEYSTORE_BASE64` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`（属 `ci.yml`，缺则只出 debug，
  `signed != true` 时 `release.yml` 的「确认拿到的是这一版的正式签名包」直接红）；
  `MODULES_REPO_TOKEN` 对 `Xposed-Modules-Repo/io.github.lo0kie.opluskey` 有 `contents: write`
  （属 `publish.yml`，缺则「检查模块仓库的前置条件」跳过，只发存档）；
  `GITEE_TOKEN`（Gitee 私人令牌，勾 `projects` 权限，属 `release.yml` 最后一步，缺则
  「同步到 Gitee Release」整步跳过、只留一条 notice）。
- **推的 tag 必须与源码里的版本号对上**（`<versionCode>-<versionName>` 或 `v<versionName>`），
  对不上时 `校验 tag 与版本号一致` 这一步直接红。放宽的后果是「Release 挂在另一个名字上 ⇒
  发了新版而管理器不提示更新」，且流水线全绿、看日志看不出来。
- **`versionCode` 只增不减**，预发布也占号（不涨号测试通道收不到更新提示）；`versionName` 带 `-`
  （如 `1.1.0-beta.1`）会被自动标成 prerelease、不抢 latest。
- **tag 推上去就不再移动**：`git tag -f` + 强推会让历史版本指向漂移。打错了删远端 tag 重打
  （`git push origin :refs/tags/<tag>`），并把那两条 Release 一并处理。
- 当前基线（2026-11-02）：`versionName = 1.3.3`、`versionCode = 11`（见 `app/build.gradle.kts`）。
  历史：第一个进仓库的版本是 `1.0.0`（tag `3-1.0.0`），`versionCode` 取 3 —— 1 / 2 已被历史包
  （手动发的 `0.1.0`、本地出的 `0.2.0`）占用。此后每涨一次版本，两边各多一条 Release、一一对应。

## 八之二、多版本适配：档位表

ROM 私有的东西分三类，**各自按 ROM 系列（`17.0.0` 这种）分档**，「适配一版新 ROM」= 加档。
键与口径都取 `DeviceSupport.currentRomSeries`（一处读 `Build.DISPLAY`，见「八、适配清单」）。

| 那一类                                                                        | 表在哪                                    | 加一档加什么   |
| ----------------------------------------------------------------------------- | ----------------------------------------- | -------------- |
| 接管点候选（Hook 面类名 / 方法名 / 宿主侧服务与 Activity）                    | `bridge/Framework.kt` 的 `HookSet`        | 一个 `HookSet` |
| 执行落点 + 反射面（内置命令落到哪个组件、ColorOS 私有类名 / 方法名 / 字段名） | `core/model/RomProfile` 的 `Profile`      | 一个 `Profile` |
| 小窗几何（展开档的落点矩形与缩放）                                            | `core/model/MiniWindowGeometry` 的 `Slot` | 一个 `Slot`    |

**前两张表的键是 ROM 系列、第三张的键是机型**，别并成一张：落点由 ROM 决定（同一版 ROM 在不同机型上相同），而几何由**屏幕尺寸与 dpi** 决定（换一块屏就得重新量）。

一、7 要求接管点候选只写 `bridge/Framework.kt`，所以那张表留在那边；`RomProfile` 里另有一个
`Reflection` 装执行侧要反射的名字。两张表**都以 `romSeries` 为键**，加档时一起加。

- **按 ROM 系列分档，不按机型**：这些名字由 ROM 决定，机型只在适配清单里起作用。
- **加档之后要在真机上把每一层重新验一遍**：那些类与方法都是 ROM 私有的，换版本会整体换名换形。
  每档带一个 `verified` 字段（机型 + 日期）记「这一档是谁在哪台机器上验的」，安装期判据行会打出来。

- **没验过的 ROM 照「基准实现」跑**（`RomProfile` 的类注释里也写了）：`PROFILES` 末尾那一档就是基准，
  本机 ROM 不在表里时，内置命令落点与接管点候选 / 反射面**都照它试**，各打一行 `w` 说清照哪一档跑。
  跑不通由执行侧收尾 —— 起不来会 toast 一句「这次没执行成功」（`CommandFailure.UNKNOWN`），
  反射落空则记 `w` 后逐处跳过。
- **界面上不再锁住任何一条内置命令**：网格格子与「添加命令」的选择行都可点，跑不起来交给执行侧的
  toast。要让某一版 ROM 被「钉住」，就往 `RomProfile.PROFILES` 里补那一版的一档（加完照例在真机上验一遍）。
- 安装期在机型 / 系统那两行旁边多打一行 `适配档位：内置命令=…；小窗几何=…`：
  适配清单管「这一组验过没有」、档位表管「注入之后那些落点认不认得」，真机报「按了没反应」时两句一起看
  就知道断在哪一环。
- **同一层的多个接管点候选「第一个命中就停」**：那一层的几个候选是同一个类的两种包名写法
  （AOSP / OPLUS），两个都存在时各装一个接管点，会把一次按压喂判定器两遍（双击变「双击 + 单击」）。
  层与层之间本来就是逐层降级，同层这道闸原来漏了。
- **没进档的东西**：小窗 bundle 的 key 与枚举值（`extra_window_mode` / `WINDOWING_MODE_ZOOM` /
  `mZoomLaunchFlags`）—— 它们是协议值，改了要在真机上重新验，留在实现里并在注释里写明。

## 九、提交前检查（三道闸 + 一道锁定校验）

| 闸       | 命令                      | 规则落在哪                                           |
| -------- | ------------------------- | ---------------------------------------------------- |
| 格式     | `./gradlew spotlessApply` | 根 `build.gradle.kts` 的 `spotless` 段（ktlint）     |
| 静态检查 | `./gradlew detekt`        | `config/detekt/detekt.yml`                           |
| Lint     | `./gradlew lintDebug`     | 根 `build.gradle.kts` 的 `Lint.applyProjectPolicy()` |

三道闸一起跑用 `./gradlew gate`（根脚本注册的聚合任务，两个模块 × 三个任务）。

- **`gate` 与打包分开跑**：`assemble*` 会带 R8（release 那一遍）。把它们串在一条命令里，
  构建时间就是三道闸的好几倍 —— 而绝大多数「改完看一眼」根本不需要包。

- **三个闸的配置都在根脚本里，一处定义、两个模块共用**（`:app` 与 `:core` 的 `build.gradle.kts`
  **不再各写一份**）。重复的规则等于没有规则：改一边另一边不会红，只会静默地跑在两套标准上 ——
  拆模块那一轮就踩过（detekt 阈值只在 `:app` 调过，`:core` 仍是默认值）。
  挂法是 `subprojects { plugins.withId("…") { extensions.configure<…> } }`：`subprojects` 段在根脚本
  求值时先跑，那时子项目的 `plugins { }` 还没执行，直接 configure 会当场失败。
- 历史存量进的是 detekt 的 `config/detekt/baseline.xml` 与 lint 的 `@Suppress` 注解（**没有** lint
  baseline 文件 —— 那个名字并不存在）。基线是**两个模块共用一份**：detekt 按「本模块扫到的文件」
  去匹配，匹配不上就静默忽略，所以共用不会互相污染。
- **新代码不许再往基线里加条目**；加了要在提交信息里说明为什么。拆得动的存量要顺手**减**条目
  —— `PageRoot.kt` 那两条（`LongMethod` / `CyclomaticComplexMethod`）是拆完整页之后删掉的，
  这类「基线变短」的 diff 比「挪签名」有价值得多。
- 行长上限的值写在根 `build.gradle.kts` 的 `ktlintRules` 里。
- **死 import 由 ktlint 的 `no-unused-imports` 管**（`ktlintRules` 里显式 `enabled` —— ktlint 1.8 默认
  不跑它，实测「完全无引用的 import」也原地不动）。它按 PSI 引用判，比 detekt 的 `UnusedImports` 准；
  detekt 那条留着兜底。
- **`remember` 里缓存字典文案**两道闸都看不见（`AppLanguage` 是运行期状态，lint / detekt 都表达不了）：
  切语言不会重建页面，键里没有语言档的 `remember` 会把旧语言的字符串留在界面上 —— 真机上就是
  「关于」页那两行（`签名` / `调试版本（不校验签名）`）在 English 下还是中文。
  `tools/check-i18n-cache.py` 判这一条：`remember` 的块体**直接读** `I18n.get` / `I18n.format`，
  或调了**本文件里读字典的函数**（折行写法也算），而键里没有 `Lang` / `lang` / `language` ⇒ 命中。
  它**给退出码**（0 通过 / 1 命中）；确实与语言无关的一份在语句行尾加注释 `i18n-locked` 放行。
  ⚠️ 它现在是**手动跑的第四道**，没有并进 `./gradlew gate` —— 并进去要改 AGENTS 二、1.2 的口径。
- **两道闸都判不了「同名遮蔽」**：`val key = cfg.key(...)` 这种，短名在文件里出现过，两个工具都当成
  「还在用」。`tools/find_shadowed_imports.py` 只把「import 短名与文件内声明同名」的文件列出来
  （十来行，人工扫一眼即可）；它**不做判定**，因为文本层分不清 `Modifier.padding()`（扩展调用，import
  有用）与 `cfg.key()`（成员调用，import 无用）。
- 「注入侧不得引用界面层依赖」的复核命令见 `architecture-refactor.md` §六。

### 依赖锁定（`app/gradle.lockfile` + `core/gradle.lockfile`）

- 开关在根目录 `build.gradle.kts` 的 `subprojects { dependencyLocking { lockAllConfigurations() } }`；
  **两个模块各一份锁文件**（都在仓库里，`app/` 与 `core/` 各一，`app` 那份随界面库涨到几百条）。
  `core` 也必须锁：它是注入侧能不能进 androidx 的那道边界（见 AGENTS 二、1.2），只锁 `app` 等于漏了一半。
- 改了 `dependencies { }` 之后：`./gradlew :app:dependencies --write-locks` **和**
  `./gradlew :core:dependencies --write-locks`，把两份 diff 一起提交 —— 那份 diff 就是「这次升了什么」的清单。
- **忘了锁的那次本地照样编得过**：Gradle 对「没进锁文件的配置」是宽松的（按解析结果走，只有 STRICT
  模式才当场失败）。所以 CI 里有一道「校验依赖锁定」：重写一遍锁文件再和库里那份比对，变了就红灯。
- 插件（AGP / Kotlin / detekt / spotless）与它们的工具链**不在锁定范围**：那些版本写在 `plugins { }`
  与根脚本的 `ktlintVersion` / detekt 插件版本里，改版本本来就得动手改文件。
- 生成锁文件要在**联网**状态下跑：`--offline` 时解析不到的配置会被标 `(n)`、不进锁文件，
  于是本地锁出来的那份比 CI 里的小一圈，比对必然对不上。

### 依赖校验（`gradle/verification-metadata.xml`）

- 每个解析到的 artifact 都记 sha256（`verify-metadata=true`），对不上就**在配置期直接红**：
  `Error resolving plugin [id: …] > Dependency verification failed`。
- **插件 marker 的 POM 在两个仓库字节不同**：`mavenCentral` 那份 1434 字节、带 `<dependencies>`，
  `gradlePluginPortal` 那份 705 字节。`settings.gradle.kts` 的仓库顺序
  （`google → mavenCentral → gradlePluginPortal`）决定 CI 从哪一份解析；而本地缓存里常常是
  portal 那份 —— **于是本地全绿、CI 红**，报错里 `from repository MavenRepo` 说的就是这件事。
- 一个 artifact 收多份哈希用 `<also-trust>`（Gradle 自己合并时也这么写）：

    ```xml
    <sha256 value="记录值" origin="Generated by Gradle">
       <also-trust value="中央那份"/>
    </sha256>
    ```

- 升级插件版本后最容易踩：新版本的 marker 只记了缓存那一份。核一遍的办法是把每个
  `*.gradle.plugin` 组件的中央 POM 下下来算 sha256、比对 metadata 里有没有；`com.android.*`
  在 `google()` 里（中央 404），只记 google 那份。
- **平台专属产物要各记一条**：`com.android.tools.build:aapt2` 在 Windows 上是
  `aapt2-<版本>-windows.jar`、Linux 上是 `-linux.jar` —— 文件名与字节都不同，不是同一 artifact
  的两份哈希。本机只下前者，CI 下后者，于是**本地永远发现不了**这一档，只能按 CI 报错去补
  （`sha256sum` 一下 Google maven 上那个平台的 jar 即可）。
- `--write-verification-metadata sha256` **会沿用缓存里那一份**：要补中央哈希得先清掉对应
  `~/.gradle/caches/modules-2/files-2.1/<group>`，再用 `--refresh-dependencies` 跑一遍。

### 配置缓存（`gradle.properties` 的 `org.gradle.configuration-cache`）

- 开着。收益是**每次调用**省掉配置阶段 —— 本项目两个模块 × 三档变体 = 6 套任务图，
  实测 `./gradlew gate` 从 41 秒降到 3 秒（第二次起）。
- **它有一个前提，不是加一行开关就行**：配置阶段不许出现「只在这 一次成立」的取值。
  这条以前不成立 —— `build_id` 原本是 `resValue("string", "build_id", 格式化(当前时间))`，
  取的是**配置期**的时钟，一开缓存就被冻成第一次那个时刻（一天里出十个包，版本行都是同一个时）。
  现在改成 `GenerateBuildId` 任务的产物（执行期取时间、`SOURCE_DATE_EPOCH` 走 `@Input`）。
- `GenerateBuildId` 还写了 `outputs.upToDateWhen { false }`：**每次构建都重新取时间**。
  因为「现在几点」不是任务输入，默认的增量判定会让它在源码变了、包也变了的情况下仍停在更早那一次
  —— 实测踩到：改一行注释重新出包，包里还是 6 分钟前那个时刻。代价只是资源重新合并 + 重新打包，
  R8 不受影响（字符串资源不进混淆）。
- **它没有降级成警告**（不设 `configuration-cache.problems=warn`）：那条会让「缓存不可用」
  悄悄退回普通构建，性能白丢而 CI 仍然绿。现在有人在配置期引入读时钟 / 读随机数，
  Gradle 会当场报错并指出行号。
- 要临时关掉：`./gradlew --no-configuration-cache <task>`，或删掉 `gradle.properties` 那一行。

## 十、应用控制（快捷功能 › 应用控制 + `proc:stop:` 动作）

**一格两档、一张页面**：网格里那一格叫「应用控制」，点它先弹一层菜单（打开应用 / 结束应用），
两档进的是**同一张**「选择应用」页 —— 差别只有一个：确认之后写成 `app:<包名>` 还是 `proc:stop:<包名>`。
页面、搜索、定位、确认键、空态全部是「选择应用」那一份实现，没有第二套。

- **入口那一格**（`ActionCatalog.APP_CONTROL_TILE` = `appctl:`）**永不写进配置**：它是关卡标记，
  那一层菜单的两行（`appctl:open` / `appctl:stop`）也都不是取值。所以 `STEP_GROUPS` /
  `BRANCH_GROUPS` 都把它换成「打开应用」那一步（[APP_STEP]）—— 一步只能是一个取值，
  「结束应用」不进步骤链，只能绑在键上。
- **菜单里的勾**由 `ActionCatalog.isBound` 判：打开那行认 `app:` / `window:` / 快捷方式，
  结束那行认 `proc:stop:`（两行都是标记 token，比的是「绑的是哪一族」）。
- **唯一的动作是 `proc:stop:<包名>`**（`Action.Process`，只存包名）。原来那一档 `proc:start:`
  已删：它与「打开应用」（`app:`）做的是同一件事，两格并排只会让人以为两者有区别。
  老配置里残留的 `proc:start:` 由 `parseProcess` 按「认不出」处理（落 `None`，不凭空执行）。
- **写盘只有一处**：两张页面共用的那颗确认键（`onAppPicked`）。它按 `AppPickerState.stopApp`
  选 token，然后走与其它动作同一条 `writeToken` 路径 —— 「绑到哪颗键的哪一档」仍由入口带下去的
  `BindingTarget` 决定。
- **点行不写盘**：这一页先「选中」，确认键才落盘。结束那一趟不挑界面（`AppPickerState.appOnly`：
  点一行就是选中、没有二级页、菜单里也没有「以小窗口打开」）。
- **结束是三步**，不是一条命令：`am force-stop` → 按 `ps` 第 2 列挑出残留 pid 一律 `kill -9` →
  **再查一遍这批 pid，查不到才算成功**。三个理由都是真机踩出来的：
  · `am force-stop` 对系统应用会被 `system_server` 拦住而**照样退出码 0**（PLK110 上
  `am force-stop com.android.systemui` 返回 0、那一行进程的 `ETIME` 一动不动）—— 原来的判据是
  「`am` 没报错」，于是日志写着「已执行」而屏幕上一个反应都没有；
  · 强杀要用 `ps` 第 2 列挑 pid，**不能 `pkill -f 包名`**：它按整条命令行匹配，而这条命令自己的
  `sh -c …` 里就带着那个包名，一枪先把执行它的 shell 打了，后面那行 `tip:` 永远等不到 ⇒ 成功也报失败；
  · 两处 `sleep` 是给 `system_server` 与内核收尸留时间，否则查表查到的是还活着的旧 pid。
  判据是「这几个 pid 没了」而不是「这个包没有进程了」：常驻应用被杀之后会被立刻重新拉起（新 pid），
  按包名判会把成功报成失败。
  包名拼进命令串之前过一道形状校验（`Action.isPackageName`，与解析入口同一把尺子）。
- **按键那条路必须异步**：`proc:stop:` 在宿主主线程的按键回调里执行，等 `su` 会卡住宿主 UI ——
  所以执行器调的是 `ProcessController.perform`（回调式）。
- **要 root**：宿主跑在 `platform_app` 域，别的应用的进程它既看不见也停不掉，整条命令走 `su`。
- **「显示失效应用」按这一趟分档**（`AppPickerState.stopApp` / `showUnopenable`）：**打开**那一趟
  菜单里有这一项（默认关 = 默认不显示），关着时空态那句话指回它；**结束**那一趟没有这一项、
  也不读它 —— 一律全列（停掉一个没有启动图标的应用正是它的用处）。行尾的「不可打开」标签同样
  **只挂在打开那一趟**：那一趟可能把它们藏起来，看得见时得知道点下去会失败；结束那一趟不挂。
- **两个显示开关都只是过滤，不参与取清单**（`AppPickerUiState.shown`）：清单**一次取全**
  （`loadLaunchableApps`：有启动图标那一批 + 模块 + `getInstalledApplications` 补回的其余应用），
  每一行带两档事实 —— `LaunchableApp.byDefault`（默认档该不该列）与 `launchable`（点不点得开）。
  `显示系统应用` 放 `byDefault == false` 那一批，`显示失效应用` 放 `launchable == false` 那一批，
  结束那一趟两者全放。**拨开关不查盘、不重查、不换缓存**。
  两条判据**各管一维、取交集**（`AppPickerState.includesIconless` / `includesUnopenable`，唯一实现）：
  一行要同时过两关才留下 —— 拨「显示失效应用」不会顺带放出没有启动图标的那一批。
  · 为什么改：上一版把这两个开关做成「取哪一档清单」的输入（`includeAll`），拨开关等于换查询 +
  把清单打回加载态 + 换一份缓存，而这三笔账跨趟残留（`lastMode` / `cachedAppsIncludeAll` / 重查失败
  又一次没人看得见的日志）。任一处对不上，用户看到的就是**开关按了没反应**；结束那一趟更直接 ——
  `stopApp` 没进取法，它拿到的仍是精简那份，要停没有启动图标的应用时清单里压根没有它们。
  · 全量取法付得起这笔账，靠的是「点不点得开」不再逐包 [`AppEntry.resolve`]（几百次 binder），
  而是 `AppEntry.launchablePackages` 一次问全机（三次 bulk 意图查询），判据仍是同一份
  `isLaunchable`，外加那份 `ApplicationInfo` 上的 `FLAG_HAS_CODE`。
  · `AppPickerState.stopApp` 因此**必须是 compose 状态**：`shown` 是 `derivedStateOf`，它读的是
  「这一趟是不是结束」，而这一位是在组合之外改的 —— 普通 `var` 时那次改写不会让缓存失效，
  「结束那一趟全列」的算法会跟着上一趟留下来。
- **两档只认自己那一族**（`SettingsPage.appPickerSelectedTarget`）：键上绑的是 `proc:stop:` 时，
  进「打开应用」那一趟既不点亮那一行、也不滚过去；反过来 `app:` 进「结束」那一趟同理。
- **搜索框的占位带条数**（`搜索应用 (42)`，两页共用 `PickerSearchBand` 的那一个 `count`）：
  只在**搜索框空着**时才加 —— 一有输入，那行字读的是「你在搜什么」，再挂个数字会被当成搜出来的
  条数；`null` / 0 也不加（清单还没读到时不摆一个 `(0)`）。句式收在
  `Strings.COMMON_SEARCH_COUNT_FORMAT` 一处。条数从哪来：选择应用读 `appPickerUi.shown.size`；
  应用组件那一页的清单在**页内容那棵树**里 `remember`，顶栏那条带子读不到，所以由页面在
  清单到手那一刻写到页面对象上（`AppPickerState.activityCount`）。

## 已知限制

- **手改配置里认不出的写法不会执行，且下次保存会归一成 `none`**：`Action.parse` 遇到没有支持前缀的
  写法返回 `None`（并打一条 `w`，写明这条后果）。不原样保留是**有意的** —— 那要新增一个动作类型
  （AGENTS.md 一、4 的动作契约变更），收益只是保住一条本来就执行不了的写法。改配置请走界面，
  或按 `docs/implementation.md` 里列的前缀写。
- **自定义键没有独立的「已确认」类状态**：`enabled` 是唯一的闸门，导入会给所有自定义键置开
  （见上文「只管内置的侧键」）。所以别人给的一份配置里若收了音量键 / 电源键，导入那一刻就会接管。
