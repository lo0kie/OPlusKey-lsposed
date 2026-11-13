# 执行提示的通道

## 十九、「按了没反应，可它还是振了一下」：转发那一步卡在 **system_server 主 looper** 上（实测，2026-10-14，PLK110）

### 19.1 现象

用户报「整体服务没起来时按键不要振动，不要会误以为功能能用」。查日志发现不是「服务没起来」，而是**手势被宿主当成过期包丢掉了**，可注入侧**早就振过**了：

```
15:14:55.523 (system) 侧键：单击                      ← 判出手势（InputReader 线程）
15:14:58.413 (system) 已把侧键手势转给宿主：single      ← 2.89s 之后才真发出去
15:14:58.695 (host)   收到 system_server 转来的手势：key=plus phase=single
15:14:58.696 (host)   侧键 的 single 已过期（发出于 3161ms 前），丢弃不执行
```

宿主 `STALE_MS = 2000`，3161 > 2000 ⇒ 丢弃。宿主那条「执行」日志一次都没出现，**动作没跑**。

### 19.2 根因：`sentAt` 打点在前、`sendBroadcast` 却排在主 looper 后面

- `GestureForwarder.forward()` 取
  `sentAt = SystemClock.elapsedRealtime()`（这一步在**判出手势那一刻**、InputReader 线程上同步跑），随后用
  `handler.postDelayed` 投递真正那条 `sendBroadcast`；
- 它用的 `handler` 是 `KeyHooks.handler = Handler(Looper.getMainLooper())` ——
  **system_server 的主 looper**。主 looper 被占住多久，广播就晚多久；而 `sentAt` 早就在那之前定死了。
- 于是宿主算出来的 `age = 收到时刻 - sentAt` **= 真实发-收延迟 + 主 looper 的迟滞**。实测这一次：真实「发→收」只有
  **282ms**，主 looper 迟滞 **2879ms**，合计 3161ms。
- 因为「已把侧键手势转给宿主」这行**是在 runnable 里打**的（= 真发出的时刻），所以注入侧日志看起来一切正常 —— 单看它发现不了问题，必须拿宿主那笔
  `age` 去对。

### 19.3 判据（可复用）

**同一次按压的「注入侧发出」与「宿主收到」两个时间戳之差，必须约等于宿主报出来的 `age`。**
两者不等（这里差 2879ms），就说明卡在中间某一跳上，而不是链路真的慢。

### 19.4 影响面与待办

- 这次按压的**动作没执行**（用户读到的就是「按了没反应」）；旧代码还在注入侧振了一下 ⇒「振了却什么都没发生」，正是用户说的「会误以为功能能用」。
- 触觉反馈已经挪回宿主侧（见 changelog 同日那节）⇒ **这类被丢弃的包再也不会振** ✓。
- ✅ **已修（2026-10-19）**：`GestureForwarder` 自带一条 `HandlerThread`（`opluskey-gesture-forward`），
  「立刻发」与「300ms 补发」两条 `sendBroadcast` 全在这条线程上排队；`sent_at` 仍在**判定那一刻**打，
  取 Context（跑 `SystemContext` 的反射）也一并挪进去。`GestureDetector` 的计时器线程没动。
  单线程顺序执行顺带保证「手势 → 松手」不乱序。
  ⚠️ 属注入侧改动：**这次改动要整机重启才生效**（装包本身不生效，模块 dex 在进程启动那一刻才加载）。

## 四十、Toast 的两个系统侧硬约束：**每包限流**与**显示中不推进队列**（反编译 NMS + 真机，2026-11-02，3B15BR005FN00000 / ColorOS 17）

用户报「桌面连按侧键，只有一两下弹提示」。侧键单击绑的是一条 `cmd:` 命令，命令里 `echo 'tip: …'`
让模块弹提示（`ShellRunner.TIP_PREFIX`）。

判据在**系统侧**的 logcat（不是本模块发的）：

```
W NotificationService: Package com.oplus.gesture is above allowed toast quota, the following toast
  was blocked and discarded: TextToastRecord{… text=屏幕超时 · 30 秒 duration=0}
```

同一批按压里模块侧 `shell 提示：…` **每按一次都有一行**、`Toast.show()` 也确实调到了
⇒ 提示生成这一侧没问题。**排查「提示没弹」先看这一行**，别再往「tip 没生成 / 去重吞了」的方向找。

反编译 `services.jar`（`com.android.server.notification.NotificationManagerService`）得到两条规则：

1. **每包限流**（`<clinit>` 里那张 `TOAST_RATE_LIMITS`，`showNextToastLocked` → `tryShowToast` 判）：
   **3 条 / 20 秒、5 条 / 42 秒、6 条 / 68 秒**，任一条超了这条 toast 就被 `blocked and discarded`。
   计数（`noteEvent`）只在实际显示、且包在前台时记 —— 也就是说**连按超过这个密度必然有几下没有提示**，
   这是系统行为，改不了。
   - 豁免只有一条路：`isExemptFromRateLimiting` 查包有没有 `android.permission.UNLIMITED_TOASTS`。
     普通应用拿不到；`cmd notification` 在这个 ROM 上也**没有** `set_toast_rate_limit` 子命令
     （`cmd notification` 的子命令表里只有 listener / dnd / bubbles 那几档）。
2. **正在显示时不推进队列**：`showNextToastLocked` 一进来就 `if (mIsCurrentToastShown) return`，
   新记录只能等前一条走完（`LENGTH_SHORT` 2 秒）—— 连按时看到的是两秒前的旧内容，
   严重时表现为「按了没弹」。所以弹新的之前必须先 `cancel()` 上一条。

处置（`host/action/CommandFailure.FailureNotice`）：全模块共用**一个** `Toast` 实例，每次
`cancel()` → `setText()` → `show()`：队列里始终只有这一条、内容就是当前这一次的。
注意**复用同一实例并不会让 NMS 就地更新那一条**（真机上仍然看到 `Show next toast` 走了排队分支），
所以「只换文字」省不了配额，只是省掉新建实例。

### 40.1 提示改由**注入侧**弹：包名 `android` 天然免限流

先在注入侧试过「把宿主 uid 塞进 `NotificationManagerService.mToastRateLimitingDisabledUids`」，
**这条路走不通**：`ServiceManager.getService("notification")` 拿到的不是实现类，反射走类链到
`NotificationManagerService$Stub` 就断了（真机日志：`找不到字段：…NotificationManagerService$Stub#mToastRateLimitingDisabledUids`）。

改成**换发话人**：提示不再由宿主弹，而是宿主发一条 [ACTION_TOAST] 给注入侧，由注入侧用
`ActivityThread.getSystemContext()`（包名 `android`）弹 —— `android` 持有 `UNLIMITED_TOASTS`，
`isExemptFromRateLimiting` 直接放行，`noteEvent` 也不记，所以**连按几下都弹得出来**。

- 实现：`inject/SystemToast`（主线程 + 共用一个 `Toast` 实例 + `cancel()` 后再 `setText`/`show`），
  宿主侧出口是 `host/action/ActionRunner.requestSystemToast`，线格式在 `core/wire/ModuleWire`。
- 这条通道只在**注入侧是新包**时存在：换了包没重启，注入侧还是旧代码，提示会没有（重启即恢复）。
- 失败提示（`FailureNotice`）仍由宿主自己弹：它不受「连按」影响，而换成注入侧弹的话，
  注入侧没起来时连失败都看不见。

## 四十一、`notify:` —— 以**宿主应用的身份**往通知中心发一条通知（2026-11-12）

与上面那两节不是一回事：`ACTION_TOAST` 那条是**模块自己的反馈**（执行时提示、失败提示），
屏幕上闪一下就没了；`notify:正文` 是**用户主动要的通知**，落在通知中心里能留着。

```
notify:<正文>
```

- **发话人是宿主**（`com.oplus.gesture`）：动作在宿主进程里执行，`NotificationManager` 用哪个
  Context 发，通知就挂在哪个包名下 —— 这里拿到的正是宿主那一份，所以通知的标题是**宿主应用名**
  （`PackageManager` 取，已由宿主本地化），正文是 token 里那一段。模块自己那个包在宿主进程里
  既没有通知渠道也没有界面。
- **渠道** `opluskey_notice`（`IMPORTANCE_DEFAULT`，有提示音、不弹浮窗），名字同样用宿主应用名：
  它只在系统设置里露一次，自造一句文案就得跟着翻译。
- **小图标**用宿主的 `applicationInfo.icon`。**不能**用模块自己的 `R.drawable.*`：那串 id 是按
  宿主的资源表解析的，落在那里是另一个资源 —— 通知栏会拿到一张错的图。
- **同一段正文 = 同一个通知 id**（`hashCode`）：再按一次是替换那一条，不会堆一串一样的。
  正文空着也发得出去（只有标题），不算「只有前缀」的坏配置（`Action.isBarePrefix`）。
- **两道闸，都在宿主身上**（模块管不了）：Android 13 起的 `POST_NOTIFICATIONS`，以及「用户在系统
  设置里把它的通知关掉了」（`areNotificationsEnabled`）—— 两道分开判，日志才答得出是谁关的。
  真机实测（PLK110 / ColorOS 17.0.0.105）：`com.oplus.gesture` 的 `POST_NOTIFICATIONS` 是
  `granted=true, flags=[SYSTEM_FIXED|GRANTED_BY_DEFAULT]`（`dumpsys package com.oplus.gesture`）。
  任一道不过时这一档只记日志 + 报一句失败：用户至少知道「键生效了、只是系统不让发通知」。
  判据行（`w`）：`已按宿主身份发通知：<包名>｜<标题>｜<正文>`；发不出去那条是
  `发通知跳过：宿主（<包名>）不持有 POST_NOTIFICATIONS，或它的通知被关掉了`。
- **Android Lint 的 `NotificationPermission`** 只看本模块清单里有没有声明这个权限，认不出「发通知的
  是宿主」；实测在调用点先跑 `checkSelfPermission` 再返回，它照样红（不认运行时判据）。所以那一处
  是带理由的 `@SuppressLint`（见 `NotifyAction.post` 的注释）；模块清单里声明这个它自己永不放的
  权限只是把红灯糊过去。

实现：`host/action/NotifyAction`（执行器）、`core/model/Action.Notify`（取值与解析）、
`ui/model/PageCopy` 的「支持的命令格式」卡里那一行（`notify:<正文>`，只作说明 —— 这一档没有网格入口，
和 `shell:` / `intent:` 一样靠命令输入框手写）。

## 四十二、`fluid:` —— 流体云只能借宿主的 Seedling SDK 发（反编译 + 实测，2026-11-12）

需求是「弹一条自定义文字的流体云」。先把两条通道都验了一遍，结论：**SystemUI 那条发不了自定义文字，
只剩宿主自己那只 SDK**。

### 42.1 SystemUI 的 `SEEDLING_SERVICE`：只认四类内置胶囊

我们现成的 `SeedlingClient` 走的就是这条（`ringModeEvent` 弹响铃胶囊）。服务端在
`SystemUI.apk`（`/system_ext/priv-app/SystemUI`，9 个 dex，代码在 `classes4.dex`）：

```
BaseService$ServiceHandler.handleMessage
  what=1 → EventReceiver.onReceive(bundle) → EventParse.parseBundle   ← 只读一个 key：seedling_event
          → EventDispatch.dispatch(event) → 该事件码上注册的 EventListener
  what=3 → 键盘锁变化（isKeyguardShowing / isKeyguardOccluded）
  what=4 → StateManager
```

监听者只有四类 `NormalTip` 子类：`RingModeTip`（响铃）、`DndTip`（免打扰）、`FlashlightTip`（手电筒）、
`MediaControlTip`（媒体）。`RingModeTip.onEvent` 只读 `ringModeType`，随后用 **SystemUI 自己的**
`R$string`（`volume_footer_slient` 等）与 `assets/images/op_silence.json` **覆盖** `description` /
`imagePath` —— 调用方塞的文案被丢掉。**没有「任意文字胶囊」这一档。**

顺带否掉两个看着像口子的字符串：`fluid_cloud_text` / `only_show_fluid` 在 SystemUI 里只出现在
`KeyDndController` / `KeyFlashlightController` 的 `handleActionButtonClick`，那是**往框架回话**
（把文案交回 framework）用的，不是收文案的。

### 42.2 可行的那条：宿主自己的 Pantanal Seedling SDK

宿主（`com.oplus.gesture` 8.6.0）自己弹响铃 / 截屏胶囊走的就是它：

```
i9.e0.k(context)                                  ← 取实例（宿主自己的那一个）
  i9.e0.q(String logoPath, String text, boolean showA)   ← 纯发送，无闸门（屏幕 / 锁屏 / AOD 的判据在外层 v）
      SeedlingCardOptions: grade=4, milestone=true,
        showHostMap / lockScreenShowHostMap = { StatusBar: true },
        extensibleActionMap = { "service_from": 1 }, remindType=11
      businessData = {"initData": {"logo": <宿主 assets 里的 lottie>, "title": <文字>, "showA": <bool>}}
      SeedlingIntent(now, "pantanal.intent.business.app.system.ACTION_KEY",
                     SeedlingIntentFlagEnum.START, data=null, businessData, options)
      SeedlingTool.sendSeedling(ctx, intent, IIntentResultCallBack) : int
```

- **文字能换、模板换不了**：卡片的业务 action 固定，外观由系统侧那份模板决定。
- `logo` 是**卡片资源包里的**素材名（见 42.3），可选的只有五个；我们取 `precise_notification_icon.png`。
- 卡片几秒后自己收起，**通知中心里不留记录** —— 与 `notify:`（§41）是两件事。
- 系统侧有个总闸（SDK 的 `isSupportFluidCloud` 读 `is_fluid_cloud_support`），关着就不显示。
- 发送结果**在宿主的日志里**：`ActionKeyFluidCloudManager: showSendling onIntentResultCodeCallBack--:…
  i:<码>`（`i` 非 0 = 卡服务拒绝）。我们这侧只能记「已经请宿主发了」。

实现：`host/action/FluidAction`（候选表 `i9.e0` + `q`，缺哪一环记一行 W 并报失败 —— 这几个都是
混淆名，换 ROM 会整体改名）。判据行：`已请宿主弹流体云：<正文>`；失败是
`弹流体云失败：宿主里没找到 …` 或 `… 换 ROM 之后这一档改名了`。

### 42.3 图标是哪来的：卡片模板里的 `{{logo}}`

「流体云」的外观不是系统写死的，是**卡片资源包**里的一份模板（宿主 APK 的
`assets/ActionKeyTip.upk`，一个 zip）：

```
config.json            identifier.url = com.oplus.gesture.precise，intent.action = pantanal.intent.business.app.system.ACTION_KEY
assets/card-config.json card.support = notification_sm，host = com.oplus.gesture/ActionkeyCardWidgetProvider
pages/index.json       ← 版式在这里
```

`pages/index.json` 里左边那颗图标**两个槽互斥，取的都同一个变量**：

```json
{ "type": "lottie", "attr": { "src": "{{logo}}" }, "shown": "{{showA}}" }        ← showA=true 走这条（只认 .json）
{ "type": "image",  "attr": { "src": "{{logo}}" }, "shown": "{{!(showA)}}" }     ← showA=false 走这条（png / svg）
{ "type": "text",   "attr": { "value": "{{title}}" } }                            ← 右边那段字
```

模板的默认 data 就是 `logo = "precise_notification_icon.png"`、`showA = false`、`title = ""`；
包里一共五个素材：

```
op_ring.json / op_silence.json / op_vibrate.json   三条 lottie（响铃 / 静音 / 振动）
op_ic_silence.svg                                  静音那道斜杠
precise_notification_icon.png                      通用的「通知」小图（模板自带的默认）
```

⇒ **图标完全由调用方那个 `logo` 决定**，值是**包内相对路径** —— 要写全 `assets/images/xxx.png`。
宿主自己那三条 lottie 传的就是 `assets/images/op_ring.json`。**只写裸文件名（`xxx.png`）查不到资源表，
胶囊左边会是空的，且不报任何错** —— 2026-11-13 真机上栽过：这一档从上线起就没有图标，谁都没发现，
因为「空着」和「图标是深色的」在状态栏上不好分辨。我们第一版抄了响铃那条（`op_ring.json` + `showA=true`），
所以胶囊左边是一颗铃声图标 —— 那不是模板默认，是我们自己选的。现在用
`assets/images/precise_notification_icon.png` + `showA=false`：通用小图 + 静态槽。
**别再传 `.json` 配 `showA=false`**（image 槽读不了 lottie，会空），反之 png 配 `showA=true` 也一样。

### 42.4 为什么展不开：这份卡片只有折叠态

「展开」不是调用方的开关，是**卡片版式里有节点**才有的行为。三份资源包一比就清楚：

| 包 | 版式节点 | 能不能展开 |
| --- | --- | --- |
| SystemUI `assets/RingModeTip.upk` | `card-template` / `compact` / `leading` / `trailing` / `lottie` / `image` / `text` | 不能（响铃胶囊本来就只有折叠态） |
| SystemUI `assets/Flashlight.upk` | 上面那些 + **`expanded`** + `button` + `voice` + `center` | 能 |
| 宿主 `assets/ActionKeyTip.upk`（我们这条） | `card-template` / `compact` / `leading` / `trailing` / `lottie` / `image` / `text` | **不能** |

我们这份还多两条证据：`pages/index.json` 的 `actions` 是**空对象**（点它没有任何绑定），
`config.json` 的 `runtime.seedling-type = "immediate"`（即时型：亮一下就走，本来就没有面板语义）。

⇒ 走「宿主的 `system.ACTION_KEY` 业务」这条路**做不到展开**：要展开需要一份带 `expanded`
（+ `button` / `actions`）的资源包并与业务 action 绑定，而这两类包分别在 SystemUI 与宿主 APK 的
只读资产里 —— 模块既改不了系统应用的资产，也不具备卡片宿主身份（`card-config.json` 的
`host.componentName` 必须是宿主自己的 `ActionkeyCardWidgetProvider`）。

### 42.5 换成我们自己的图标：`logo` 也可以是 `content://`（2026-11-13 真机验证）

**先修的是一个更基础的错**：`logo` 之前传的是裸文件名 `precise_notification_icon.png`，
而渲染端拿这个值**当包内路径查资源表** —— 裸名查不到，于是这一档从上线起**胶囊左边就一直是空的**，
且不报错。宿主自己传的是 `assets/images/op_silence.json`，这才是对的写法。改正之后图才第一次显示出来。

**结论**：`logo` 不限于包内文件，**也可以是 `content://`** —— 系统自己就这么用。宿主弹相机胶囊那次打的是：

```
sendSeeding logoPath:content://com.oplus.gesture.FileShareProvider/share_images/2131232270 text:相机 showA:false
```

那个 provider 是 `androidx.core.content.FileProvider` 的子类
（`com.oplus.pantanal.seedling.file.provider.FileShareProvider`），`share_images` 映射到宿主的
`files/share_images/`，**uri 最后一段就是文件名**（目录里那些 `2131232270` / `magic_key_dnd_on_icon`
就是导出的 png）。而 `fluid:` 的代码本就跑在宿主进程里，有那个目录的写权限 ——
把自己的 png 写进去、`logo` 给对应的 uri，图标就是自己的。**不改包、不 hook、不要 root。**

两条必须注意：

- FileProvider **未导出**（跨 uid 直接读是 `Permission Denial … not exported from UID`），
  必须 `grantUriPermission(pkg, uri, FLAG_GRANT_READ_URI_PERMISSION)` 给 `com.android.systemui`
  —— 画胶囊的是它的 `PluginSeedling` 插件；
- 目录里如果被谁（root）塞过**同名但属主不对**的文件，宿主改不动也删不掉（`EACCES`），换个文件名即可。

**三条被排除的路**（都试过，别再走）：

| 走过的路 | 结果 |
| --- | --- |
| 改宿主 APK 的 `assets/ActionKeyTip.upk`、改卡服务 `files/store/<hash>_<ts>.upk` | 图不动（前者是源，后者是缓存） |
| hook `AssetManager#open` / `FileInputStream` / `ZipFile` / `ContentResolver#openInputStream` | 包确实被换掉了（日志 `9303 → 8442`），但 SystemUI 比对包名里的内容指纹后丢弃 |
| 覆盖 SystemUI 解压目录 `files/upk/<hash>/<ver>/assets/images/…png` | 它每次重新解包就盖回去 |

阅读这段时还要知道两件事：**胶囊是 SystemUI 的 `PluginSeedling` 插件画的**（状态栏那些 `CapsuleCard`
日志的 pid 就是 `com.android.systemui`，它进程的 fd 里同时开着
`/system_ext/app/OplusGestureUI/OplusGestureUI.apk` 与 `seedlingsdk_plugin/SeedlingSdk.apk`）；
包内素材解压后在 `SystemUI/files/upk/<hash>/<versionCode>/`，而卡服务自己也有一份
`files/store/` 的副本 —— 但它们都不是渲染的入口。

实现的两头：**图由界面层画**（`ui/model/icon/FluidCardArt`），**uri 由宿主侧拼**
（`host/action/FluidAction#iconUri`）。

## 四十三、执行时提示的两个落点：Toast 与流体云（2026-11-13）

「执行时提示」（§40）原先只有 Toast 一条路。这一版多一档**用流体云提示**：同一句文案改由 §42
那条宿主 Seedling SDK 发，左边那颗图标取**这一次执行的动作**。

- **通道完全复用 §42**：`FluidAction.runText(text, logoFile)` 走的就是 `q(logo, text, showA)`
  那一个方法 —— 执行提示不另开接口，也不碰 SystemUI 那条 `SEEDLING_SERVICE`。
- **文案不换**：仍是 `ToastNotice.render` 拼出来的那一句（`$key` / `$gesture` / `$action`）。
  换落点不换说法，所以「提示文案」那一行在两种落点下都读得通。
- **图标为什么必须在界面层画**：动作图标是 Compose 的 `ImageVector`，而执行提示发生在宿主
  主进程 —— 那边没有 Compose 运行时。界面层把配置里出现过的动作（`Config.actionTokens`，
  与命令改名 / 删命令共用同一份遍历）画成 36×36 透明底、纯白描边的 png，落进
  `files/share_images/`；文件名由 token 的 `hashCode` 决定（`FluidCard.actionNameOf`），
  宿主按 token 用同一个算法找回来。
- **缓存按文件名判**：文件已在就不重画（预热挂在设置页根上，配置每次写盘补画新出现的动作）。
  `cmd:` 改了命令类型（单条 ↔ 多条）时 token 不变，图会停在旧形状，直到换绑或清数据 ——
  不影响提示弹出来。
- **文件不在时退通用图**：从没进过设置页 / 手工清过共享目录时，`logo` 退回模板自带那张
  `precise_notification_icon.png`（`FluidAction.LOGO`），而不是空着。与 `fluid:` 那档不同的
  是：执行提示**总会**给一个文件名（动作一定存在），所以不会出现「左边空着 = 用户没选」。
- **失败不再回头弹 Toast**：`runText` 的失败回调传空 —— 再弹一条就是「提示失败的提示」，
  而用户要的那一句照样没出现；日志里仍有一行 W 说清断在哪（AGENTS 五、2）。
- 判据行两条连着看：`执行时提示：<整句>`（宿主执行器）→ `已请宿主弹流体云：<整句>（图标=…）`
  （`FluidAction`）。只有前者没有后者 ⇒ 分流没走到流体云（看这一档的 `toastFluid` 生效值：
  公共那份还是这一颗键自己那份）。
- 配置位：`GlobalDto.toast_fluid` + `KeyDto` / `ComboDto` 的同名覆盖，`ConfigCodec.VERSION` 57。
  「独立设置」里那一位与「执行时提示」同一个总开关门控。

- 图源是模块**已经在用**的 Tabler 字形（`ImageVector`），落成 `36×36`、**透明底、纯白描边**
  的 png，文件名 `okey-fluid-<id>.png`（`|` 前面记的就是这个 id）。之所以分给界面层画：宿主
  进程没有 Compose 运行时，在那边引 `ImageVector` 就是 `NoClassDefFoundError`。
- 落点在宿主的 `files/share_images/`（设置页跑在宿主自己的 `:ui` 子进程里，`filesDir`
  就是宿主那份）；宿主侧只认文件，文件名由 `FluidCard.nameOf(icon)` 一处决定。
- **没选图标（`fluid:正文`）就不画图标**：`logo` 传空串，模板那一格空着。以前有一条
  「默认图标 + 宿主兜底」的链路（界面层预渲染 `okey-fluid.png`、宿主缺文件时塞
  `assets/fluid/card_icon.png`），于是「选过默认那颗」与「压根没选」长得一模一样、
  也没法让它干脆不带图 —— 2026-11-13 按用户口径整条拆掉，那张 png 已随之下线。

