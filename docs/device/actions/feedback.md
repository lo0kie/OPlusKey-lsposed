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
