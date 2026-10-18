# ColorOS 小窗（Flexible / Zoom Window）：完整实现笔记

> 2026-10-14 ~ 10-15，PLK110 / 1272×2772 / 560dpi /
> ColorOS。本文是模块里「小窗」这一块的**总文档**：两条命令的现行实现、所有入口的配方与判据、动画方案的完整推导、反射坑清单、代码位置。过程性取证（逐条 dex 反汇编、日志原文）在
> `device-findings.md` §17.6.1~17.6.5 与附二~附六，本文只留结论与可直接照抄的配方。

---

## 一、总览：这个体系里有哪些东西

| 层         | 类 / 接口                                                                                            | 所在                                              | 权限                                               |
| ---------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------- | -------------------------------------------------- |
| 应用侧门面 | `android.app.OplusActivityManager`                                                                   | `oplus-framework.jar`（boot classpath，反射可及） | 宿主持有 `oplus.permission.OPLUS_COMPONENT_SAFE`   |
| AIDL 门面  | `android.app.OplusActivityTaskManager`                                                               | 同上                                              | 同上（`notifyFlexibleTaskEvent` 这支**无权限闸**） |
| 服务端核心 | `FlexibleWindowManagerService` / `FlexibleTaskController` / `FlexibleWindowAnimator` / `TaskExtImpl` | `oplus-services`（system_server）                 | 仅注入侧可达                                       |
| SDK 常量   | `com.oplus.zoomwindow.OplusZoomWindowManager`                                                        | `ofw/classes2.dex`                                | —                                                  |

**窗口模式值**：`1` = 全屏、`100` = 小窗（`WINDOWING_MODE_ZOOM`）、`101` = 小窗转全屏、`6` = 旧档。
**判断「有没有真成小窗」只看 `getAllTopAppInfos()` 里的 `windowingMode`** —— 返回码和截图都会骗人。

**前台信息怎么取**（要 `OPLUS_COMPONENT_SAFE`，宿主持有）：

- `OplusActivityManager#getAllTopAppInfos(): List<OplusAppInfo>`
  —— 按「最前面」排序；小窗在场时列表里同时有「小窗那份（100）」与「全屏那份（1）」。`OplusAppInfo` 字段：`taskId` /
  `topActivity` / `windowingMode` / `displayId` / `appBounds`。
- `OplusActivityManager#getTopActivityComponentName(): ComponentName`
  —— 「最前面那个」；有小窗在场时返回的是**小窗里**那个应用。

---

## 二、模块的两条小窗命令：现行实现与降级链

### 2.1 `window:<包名>` —— 用小窗打开一个应用

`MiniWindowLauncher.launchAppInWindow(target)`，按目标当时的状态分三档：

| 目标当时的状态       | 走法                                                | 结果                      |
| -------------------- | --------------------------------------------------- | ------------------------- |
| 还没跑过             | `startZoomWindow`（只 `NEW_TASK`）                  | 新起一份、落小窗          |
| 有现成任务（非前台） | 同上，服务端**复用**那份任务，1 → 100               | **taskId 不变**，无新实例 |
| 已是小窗             | 同上，返回 `0`/`2`，无第三份                        | 不变                      |
| **就在前台**         | 会被栈顶实例消化（返 `3`）→ 改走 recents 正门原地收 | 不变                      |

降级链：`startZoomWindow`
反射路不通 → 自由窗口（`ActivityOptions`）→ 普通打开 —— 宁可开成普通界面，也不能按下去什么都不发生。`FLAG_ACTIVITY_MULTIPLE_TASK`
**已整个删掉**（§四）。

### 2.2 `zoom:mini` —— 把前台应用收成小窗

`MiniWindowLauncher.putForegroundInMiniWindow(collapsed)`，两个尺寸档走不同配方：

«展开档»（默认，`zoom:mini`）：

```
① 应用侧 recents 正门（startActivityFromRecentsForFlexible，flags=2）← **唯一一条**
   `XLog: 走 recents 正门（原地收、无动画）`
     ↓ 服务端受理、设置里「前台转小窗时使用动画」开着、且当前是竖屏
② 原生缩小动画：宿主发 `CMD_MINI_ANIM` → 注入侧调 `startFlexibleSpringAnimation`（§十二）
     转换是同步的 binder 调用，返回时任务已是柔性任务 ⇒ 立刻播，不必先露出一个静止的小窗
     ↓ 700ms（MINI_VERIFY_MS）后 windowingMode != 100，或 ① 那一步服务端没受理
③ **什么都不做 + Toast「前台应用不支持小窗」**   ← 2026-10-14 用户口径
```

«收起档»同理：`flags = 32` 收不成就 Toast，**不再**另想办法。

⚠️ **③ 以前不是这样，是「退 launch 配方 → 再退按包名另起一份实例」—— 两条都删了**
（2026-10-14 用户口径：「有些应用不支持小窗，你应该什么都不做，toast 提示，现在会返回桌面重开」）。被删掉的那条链有两个毛病，真机日志都留了证：

| 毛病                                                                                        | 日志证据（`com.heytap.market`，不支持小窗）                                       |
| ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| **回桌面重开**：先把它挪出栈顶（回桌面）再重新起一份                                        | `window:com.heytap.market 的界面占着前台，先回桌面再起小窗` → `startZoomWindow=0` |
| **重复转换**：`fallbackByPackage` → `launchAppInWindow` 看到目标在前台 → 又调了一次 recents | 连续两条 `从 recents 把任务 #178 原地收成小窗（flags=2，返回 2）`                 |

⇒ 现在的判据只有一条：**服务端受理了（返回 `2`）却没出窗口，或压根没受理 ⇒ 这个应用收不起小窗**
（`CommandFailure.APP_NO_MINI_WINDOW`），只提示、不动屏幕。

⚠️ **历史顺序（别照着回退）**：2026-10-14 反复调过 —— 曾经让 `launch 配方`
当主路去修「比屏幕宽」，结果横屏**进不去小窗**。判据定成**「进得去」优先于「尺寸对」与「有动画」**，见 §13.1。

⚠️ **曾经把 ① 写成 system_server 的「伪造态 +
switchState」**（为了拿原生动画，§七）。2026-10-14 真机验完否掉了当主路：那条开出来的窗口是**半成品**
——几何与档位都对，但**没圆角、没操作条 /
⋮ 菜单、点一下会被当成拖动**，内容还叠两份（根因是框架的「柔性任务注册」那步没做，见 `device-findings.md`
附八）。现由宿主侧开关 `MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE`
控制、默认**关**：想要动画、或哪天把注册那步补上，再打开试。

«收起档»（`zoom:mini:collapse`）只有一支 —— 收起是 recents 正门的 `fullToMini` 分支， `switchState` 只切
`setUserPositionInfo` 那档（展开），没有收起档入口：

```
① 应用侧 recents 正门（flags=32）→ 服务端 fullToMini → getMiniFlexibleTaskInfo 那套几何
     ↓ 700ms（`MINI_VERIFY_MS`）后 windowingMode != 100，或服务端没受理
② **什么都不做 + Toast「前台应用不支持小窗」**（同展开档 ③）
```

---

## 三、`startZoomWindow` 配方（§17.6.1 定稿）

```java
// 反射：android.app.OplusActivityManager#startZoomWindow(Intent, Bundle, int userId, String callPkg)
Intent i = pm.getLaunchIntentForPackage(pkg);          // 自带 NEW_TASK
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);             // ★ 只加这个；MULTIPLE_TASK 见 §四
Bundle b = new Bundle();
b.putInt("extra_window_mode", 100);                    // WINDOWING_MODE_ZOOM，服务端只认这个 key
int code = startZoomWindow(i, b, userId, ctx.getPackageName());
```

**返回码语义（真机逐组实测，关键认知）**：

| 返回             | 含义                                              | 屏幕表现                     |
| ---------------- | ------------------------------------------------- | ---------------------------- |
| `0`              | `START_SUCCESS` —— **已发起**（不管起没起成小窗） | 取决于 bundle 有没有窗口模式 |
| `2`              | `START_TASK_TO_FRONT` —— 已有任务被提到前台       | 原地转成小窗 ✓               |
| `3`              | `START_DELIVERED_TO_TOP` —— 交给栈顶实例消化      | **不变**（目标就在前台）     |
| 负数（如 `-92`） | 错误码                                            | 不变                         |

- 空 `Bundle` → 服务端当普通启动，应用全屏打开，**照样返回 0**。所以「0 = 失败」的降级判据是错的，只有**负数**才降级。
- 「返 3 = 目标就在前台」时先发 `CATEGORY_HOME` 回桌面、400ms 后再起一次（兜底，本身也是原地转、不新建）。
- 服务端在 home 过渡期间会**延后执行并立刻返回 0**（"wait for HomeTransitionFinish"），调用方不用 sleep。

---

## 四、多开实例问题与修法（§17.6.5 定稿）

**根因**：`FLAG_ACTIVITY_MULTIPLE_TASK`
强制新建任务 —— 同一个应用按几次就多几份小窗实例（真机连按三次实测：每按一次多一份）。

**修法**：`window:` 删掉 `MULTIPLE_TASK`、只加
`NEW_TASK`；「目标就在前台」的场景归 recents 正门（§五）接管。「返 3 回桌面再起」的兜底保留当保险。

**修后服务端行为**（真机实证，task `#25535` 全屏 → 回桌面 → `window:` 起法 → **还是 `#25535`**、1 →
100；连按三次 dump 只剩一份小窗 + 桌面）。

---

## 五、recents 正门：把现有任务原地收成小窗（§17.6.3 定稿）

```
android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int taskId, Bundle options)
  → enforceCallingPermission("com.oplus.permission.safe.WINDOW")   // 宿主持有 ✅
  → FlexibleTaskController#onStartActivityFromRecents
       isLaunchFromZoomMode(options) = getLaunchWindowingMode(options) == 100
       → startFreeFormFromRecent → FlexibleWindowManagerService#toggleToFlexibleTask(task, record, options, info)
```

**options 三个 key（真机逐项试出）**：

| key                                 | 值    | 作用                                                                                                                   |
| ----------------------------------- | ----- | ---------------------------------------------------------------------------------------------------------------------- |
| `android.activity.windowingMode`    | `100` | **必须**。`ActivityOptions#fromBundle` 只认**点号**这个 key；`extra_window_mode`（下划线）在这条路上服务端**根本不读** |
| `android:activity.mZoomLaunchFlags` | `2`   | 尺寸档：**`2` = 展开的普通小窗；`32` = 收起的最小化窗**（右上角一条）                                                  |
| `extra_window_mode`                 | `100` | 带上无害（只有 `startZoomWindow` 那条读它）                                                                            |

**真机效果**：task `#25503`（`设置`，前台全屏）→ 调用返回 `2` → **还是 `#25503`**，`windowingMode` 1 →
100。taskId 不变 = 同一份任务原地改窗口模式：无第二份实例、界面状态全留、不用先回桌面。第三方应用（`mark.via`）同样通过。

**尺寸档对照**（`OplusZoomWindowInfo`，判「展开/收起」看 `windowType`：1 = 展开，2 = 收起）：

| `mZoomLaunchFlags` | 服务端分支                               | `windowType` | 屏幕落点 / scale                     | 观感             | 模块对应动作                   |
| ------------------ | ---------------------------------------- | ------------ | ------------------------------------ | ---------------- | ------------------------------ |
| `2`                | `setUserPositionInfo`                    | `1`          | `Rect(126,319-1146,2131)` / `0.8017` | 展开的普通小窗 ✓ | `zoom:mini`（展开档）          |
| `32`               | `fullToMini` → `getMiniFlexibleTaskInfo` | `2`          | `Rect(898,168-1203,710)` / `0.2397`  | 收起的最小化窗   | `zoom:mini:collapse`（收起档） |

> ⚠️ **表里是「屏幕落点」，不是可以直接喂 API 的参数。** 两个小窗 API 的 `Rect` 语义是**反的**：
> `getMiniFlexibleTaskInfo` 给屏幕矩形，而 `setRememberPosInfo`
> 要的是**应用配置矩形**，差值正好一个 scale。照抄这张表去调 `setRememberPosInfo`
> 会「小一圈」（2026-10-14 真机踩过，详见 §7.4 与 `docs/device-findings.md` **附七**）。
>
> 另一条同源的结论：**小窗的逻辑尺寸恒为 `1272 × 2261`，两档共用**，屏幕上多大只由 scale 决定（展开 `1020 × 1812`、收起
> `305 × 542`）。所以 `OplusAppInfo.appBounds` 两档读出来一样， **不能用它分档位**。
>
> ⚠️ 还有一处**同名字段**要当心：上表这个 `windowType` 是 `OplusZoomWindowInfo` 上的；而 `dumpsys activity activities`
> 里 ActivityRecord 也有个 `windowType`，**展开档 / 收起档 / 连桌面都是
> `2`**，两者只是名字撞车。别拿 dumpsys 里那个判档位（2026-10-14 实测，见 `device-findings.md` 附八）。

**这条的致命缺陷**：**没有窗口动画**（§六），所以它现在是 `zoom:mini` 的**降级路**（②），不是主路。

---

## 六、动画探索全记录（为什么走到 §七）

**判据**：logcat 里 `animation-leash` 条数（真有窗户动画才会刷它）+ taskId 是否变化。

### 6.1 四条路对比（§17.6.4）

| 变体                                          | 原地 | `animation-leash` |
| --------------------------------------------- | ---- | ----------------- |
| `startZoomWindow` + `NEW_TASK\|MULTIPLE_TASK` | ✗    | 2                 |
| recents 正门 `flags=2`                        | ✓    | **0**             |
| recents 正门 `flags=32`                       | ✓    | **0**             |
| 挪出栈顶 → `startZoomWindow`（只 `NEW_TASK`） | ✓    | **2**             |

**in-place 那族结构上不可能有动画**（附二定论）：它在 binder 调用里**同步**改完窗口模式，框架事后建的过渡里一个可动画的 root 都没有 ——
`No transition roots in (#127) … so abort`。实测前台跑 `leash=0`， **把任务放后台再跑也是
`leash=0`**。有动画的走法必须让任务先离开栈顶（多回桌面一步，用户否掉）。

> 📌 2026-10-14 补测（`device-findings.md` **附九**）：清干净状态后复测，**前台全屏任务走 recents 正门仍然
> `No transition roots → abort`**，与本节一致。而**桌面**那条「近期任务浮窗」（非前台任务）却建出了真过渡并跑了
> `MultiSpringAnimation` ——两者 `zoomLaunchFlags` 相同（都是 `2`），差别是桌面那次日志里多一条
> `skipCurrentOrAdjustChange … launchFrom=2`（调用方身份）。
> **顺带纠正一处归属**：圆角与操作条是**那条过渡**装的（`adjustSurfaceForTransitionIfNeed(cornerRadius=91)` /
> `finishTransition will update task's caption visible`），不是「柔性任务注册」装的 ——所以伪造态缺壳子更可能只是过渡 abort 的后果。
>
> 📌 **2026-10-14 再补测（`device-findings.md`
> 附十一，A 方案已证伪）**：把「调用方身份」这个猜想直接试了 —— 用 debug 通道的 `zoom` op
> **从桌面进程**发起、target 是**前台**任务，结果仍然 `No transition roots → abort`。**所以 `launchFrom=2`
> 不是由调用方身份决定的。**
>
> 真正的变量是**目标任务的呈现状态**（等价于「过渡收不收得到 root」）：
>
> | 目标状态                     | root                      | 结果                      |
> | ---------------------------- | ------------------------- | ------------------------- |
> | 前台、全屏                   | 无（`TO_FRONT` 是空操作） | abort ❌                  |
> | 在最近任务总览里、卡片在屏上 | 有（`r=[0@Point(0,0)]`）  | `MultiSpringAnimation` ✅ |
>
> ⚠️ 还有一处认知订正：原生的那条动画是 **`scale 0 → 0.8016529` / `alpha 0 → 1` 的弹入**，
> **不是**「从全屏缩到小窗」。所以前台场景缺的不是「缩小的起点」，是框架根本不打算开过渡。⇒ 想在前台拿到同观感的动画，最实际的是**自己放这段弹入**（见附十一的 D1）。

### 6.2 动画入口全表（附三定论，找过一圈）

| 入口                                                            | 原地 | 动画 | 卡在哪                                                              |
| --------------------------------------------------------------- | ---- | ---- | ------------------------------------------------------------------- |
| `startZoomWindow`（真启动）                                     | ✗    | ✓    | 目标在栈顶时被吞，必须先挪出栈顶                                    |
| `startActivityFromRecentsForFlexible`                           | ✓    | ✗    | 过渡无 root → abort                                                 |
| 公开 `ATS#startActivityFromRecents(taskId, opts)`               | ✓    | ✗    | 同上                                                                |
| `notifyFlexibleTaskEvent(taskId, 2002)`                         | ✓    | —    | 只对**已是柔性**的任务；普通全屏任务只打 `exitTo:5`                 |
| `toggleFlexibleWindow(toType)` / `OplusFlexibleAnimationRunner` | —    | ✓    | `ToggleTaskRunnable` 取的是 `embeddedTasks` —— 只对已存在的柔性任务 |
| `notifyFlexibleTaskEvent(taskId, 2004)`「交换」                 | ✓    | ✓    | 要求另一份**不同包**的小窗在场（§八）                               |
| 拖拽手势（`onInputEvent` → `initializeDragZoomAnimation`）      | ✓    | ✓    | 驱动方是桌面/SystemUI 的输入接收器，应用侧没有一步到位的调法        |
| **`switchState(task, 1, reason)`（system_server）**             | ✓    | ✓    | 前置 `flexibleZoomState == 2` —— **可以伪造**（§七）                |

侧边栏官方配方也核过：`extra_window_mode=100` +
`NEW_TASK|RESET_TASK_IF_NEEDED`，目标在前台时直接放弃（`isAlreadyOpenedInTop`）—— 与我们的结论一致，没有新入口。（⚠️
2026-10-14 订正：原先这里写的是 `CLEAR_TASK`，拉 `SmartSideBar.apk` 反编译看是
`RESET_TASK_IF_NEEDED`（`0x00200000`），见 §十三。）

---

## 七、system_server 伪造态 + `switchState`（原生动画）—— ⚠️ **当前已停用**

> **本章描述的路当前不是主路**（主路见 §2.2：应用侧 recents 正门）。2026-10-14 真机验完把它从主路撤下来了：它确实能拿到原生动画，但开出来的窗口是**半成品**
> ——没圆角、没操作条 / ⋮ 菜单、点一下会被当成拖动、内容叠两份（`device-findings.md`
> 附八）。代码与开关都还在（`MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE`，默认
> `false`），下面记的机制与坑全部有效，是「将来要拿回动画」的起点。
>
> ⛔ **2026-10-14 复测：本章这条路已被判死，不要再打开那个开关**（`device-findings.md`
> 附十二）。根因不是「少补几个调用」，而是 **`switchState` 根本没转换任务** —— 真机日志里
> `onTaskInfoChanged … mWindowingMode=fullscreen` 自始至终是全屏，只是把 surface 缩到 `scale=0.8014`
> 又画了条操作条：**一个穿着小窗外衣的全屏任务**。于是 `handleButtonRegion` 恒为
> `Rect(0,0-0,0)`，框架退化成「整个窗口都是拖拽把手」→点一下就成拖动、一拖就
> `exitFlexibleTaskWindowInnerLocked exitTo:6` 把小窗甩掉退回全屏。圆角 / 操作条可以用调试通道自己补（`corner` /
> `updateCaptionViewMode`），**但补不了"它不是真小窗"**。
>
> **⇒ 与 §6.1 的 recents 正门合起来看：「前台应用 → 小窗 + 框架原生动画」在这个 ROM 上拿不到。**
> 唯一剩下的路是**自己放动画**（`device-findings.md` 附十一 D1）：稳定地走 recents 正门拿真小窗，再在它的
> `SurfaceControl` 上复刻原生同形的弹入（`alpha 0→1`、`scale 0→0.8016529`）。
>
> ⏭ **但"自己画动画"已经被用户否掉（太脆弱）。而原生的那个动画器其实可以直接调** ——框架自己算插值 / 矩阵 / apply /
> spring，我们只交起止参数。**见 §十二**（离线读 dex 得到的定论）。

### 7.1 机制

框架原生「全屏 → 小窗」动画的唯一正门：

```
FlexibleTaskController#switchState(Task task, int nextState, String reason)   // 1=小窗, 2=全屏
  └ FlexibleWindowAnimator#startFlexibleTaskToMiniAnimations(ArrayMap<Task, FlexibleAnimationInfo>, Handler, Transaction, callback, reason)
```

前置 `isFlexibleTaskAndHasCaption(task)` + `getFlexibleZoomState() == 2`。**`flexibleZoomState` 语义**（真机实测）：`0`
= 非柔性任务、`1` = 柔性任务在小窗态、`2` = 柔性任务最大化到全屏。普通全屏任务是
`0`，从 0 到 2 应用侧跳不过去（必须经过 1，中间态可见）。

**解法：TaskExt 上的三个前置字段直接写**（`mScenario=1`、`mHasCaption=true`、`mFlexibleZoomState=2`），再调
`switchState(task, 1, reason)`。它是真正的状态机迁移：自己完成窗口几何切换 + 原生动画，
**不需要先做应用侧转换、没有可见中间态**；转完后任务真的是柔性任务，字段与事实一致。

### 7.2 跨进程通道

动作执行在宿主 `com.oplus.gesture`，`FlexibleTaskController` 只在 system_server —— 新增
`bridge/FlexibleWindowBridge.kt`（与 `StatusBarBridge` 同款：宿主发限定包名广播
`com.lookie.opluskey.FLEXIBLE`，注入侧在 AMS 广播入口 hook 接住、本机执行）。

宿主侧：先发广播（system_server 优先）→ 700ms（`MINI_VERIFY_MS`）核 `windowingMode==100`
→ 不行才退 recents 正门 → 再不行按包名另起。

### 7.3 `switchToMiniLocal` 的完整步骤

```kotlin
// 1. 找任务：RootWindowContainer#anyTaskForId(int,int)，matchMode 0/1 逐档试（见 §九；不用 2 = AND_RESTORE）
// 2. 预置落点几何（修「太长」，7.4）：
//    ⚠️ 喂**展开档**的几何，绝不能拿 getMiniFlexibleTaskInfo 的值 —— 那是收起档的
//    ⚠️ 也别喂屏幕矩形 —— 这个 API 要的是**应用配置矩形**（见 7.4）
//    → ext.setRememberPosInfo(EXPANDED_BOUNDS, 0.8017f)   // = Rect(126,319-1398,2580)，1272×2261
//      屏幕落点 = 配置矩形 × scale = Rect(126,319-1146,2131)，1020×1812
// 3. 喂能力配置（修「没有操作条」，7.5）：
ext.updateFlexibleTaskParams(bundle, ActivityOptions.makeBasic(), record)
//    bundle：androidx.activity.HasCaption=true
//            androidx.activity.CaptionName = service.getCaptionName(1)   ★
//            LaunchCornerRadius=91 / LaunchShadowRadius=200 / LaunchScenario=1 /
//            LaunchScale=0.8016529 / flexibleTaskCaptionViewMaxScale=0.8083333 / Alpha=1.0 …
// 4. 伪造柔性全屏态（字段直写）：mScenario=1, mHasCaption=true, mFlexibleZoomState=2
// 5. controller.switchState(task, 1, "OplusKey")   ← 原生「全屏 → 小窗」动画
```

### 7.4 缺陷一「落点太长」：根因与修法

`switchState` 的动画几何来自 `getAnimationInfo(task, 1)`：读 `TaskExtImpl#getRememberPosInfo()`，
**不成立**时落到「按应用比例算默认」分支 —— 用**全屏宽高比**缩放，窗口被拉得很长。修法：`switchState` 前调
`ext.setRememberPosInfo(bounds, scale)` 预置落点。

**⚠️ 这里踩过一个坑（2026-10-14 真机确认）**：预置值**不能**取 `controller.getMiniFlexibleTaskInfo(task, record)`
—— 那个函数返回的是**收起档**几何（右上角一条，真机 ≈ `Rect(898,168-1203,710)` /
`0.2397`），喂进去等于把「展开」的动画落到了收起的位置，真机表现就是**「按一下直接变成右上角那条最小化窗」**。展开档要用
`setUserPositionInfo` 那条路的标准值（同 §五 尺寸档表里 `flags=2` 那一行）。

**⚠️ 第二个坑（2026-10-14 真机确认，`docs/device-findings.md` 附七）**：**别照抄上表那个屏幕矩形**。
`setRememberPosInfo` 的 `Rect` 是**应用配置矩形**，系统把它的尺寸**再乘一次 `scale`** 才是屏幕大小：

```
屏幕尺寸 = Rect.size × scale
```

正确值 `Rect(126,319-1398,2580)`（= 1272 × 2261）→ `× 0.8017` → 屏幕 `1020 × 1812` ✓。把屏幕矩形 `(126,319-1146,2131)`
直接喂进去，会被再缩一次：`1020 × 0.8017 = 817`
——真机表现是「**小窗是对的，但比 ColorOS 原生的整整小一圈**」（宽占比 0.6423 而不是 0.8019）。 `Rect.left/top`
则是直接照搬屏幕坐标、不含缩放（所以两版左上角都是 `(126,319)`）。

⚠️ 这组数是 PLK110（1272×2772 @560dpi）的实测值，与机型允许表绑定，加机型要重测。⚠️ `setRememberPosInfo(Rect, float)`
含原始类型参数，必须显式拿 `Method`（§九）。

### 7.5 缺陷二「没有操作条」：根因与修法

操作条视图由 `updateFlexibleTaskParams` **内部**负责：

```
mHasCaption 分支
  → FlexibleWindowManagerService#createFlexibleCaptionView(task, captionName)
     → Class.forName(captionName)   ← captionName 是视图实现类的全名！
        → 反射 new FlexibleTaskCaptionView(Context, FlexibleTaskController, Task, ATMS)  // 包私有，setAccessible 可过
        → initOrUpdateFlexibleCaptionView（挂到任务上，动画期间 GONE，转完 VISIBLE）
```

`captionName` 来自系统策略配置 `FlexibleWindowManagerService#getCaptionName(scenario)`（mini = 1）。bundle 里不放
`androidx.activity.CaptionName` → `mCaptionName=null` → `Class.forName(null)` 抛异常被吞 → **视图建不出来**。修法：反射
`getCaptionName(1)` 拿真名（本机 = `com.android.server.wm.FlexibleTaskCaptionView`）塞进 bundle。

---

## 八、「交换应用」事件码备查（§17.6.2，模块里没用）

`com.oplus.flexiblewindow.FlexibleWindowManager`（boot classpath，反射可及）→
`notifyFlexibleTaskEvent(taskId, event, bundle{notify_event_async:true})` —— 这层**无权限闸**。

| event       | 动作                                                                    |
| ----------- | ----------------------------------------------------------------------- |
| 2001        | 小窗 → 全屏                                                             |
| 2002        | → 小窗（**只对已是柔性的任务**）                                        |
| 2003        | 关闭                                                                    |
| 2004        | **交换**：小窗 ⇄ 其下压着的全屏任务（要求不同包、非桌面，真机复现成功） |
| 2011 / 2012 | 菜单开合同步                                                            |

「交换」抄不进
`zoom:mini`：语义是两份**不同包**任务互换，服务端明确拒绝同包，且要求先有另一个应用的小窗在场（陪跑完还得关掉）。

---

## 九、反射坑清单（全部实测踩过）

1. **装箱匹配**：按「参数运行时类」找方法时，Kotlin `Int` 装箱成 `Integer`，而 `anyTaskForId(int,int)` /
   `switchState(Task,int,String)` / `setRememberPosInfo(Rect,float)` / `getCaptionName(int)` 的签名是**原始类型** ——
   `getDeclaredMethod` 永远匹配不上、**静默返回 null** （连日志都不打）。对原始参数一律
   `Reflect.method(cls, name, Integer.TYPE, Float.TYPE, …)` 拿 `Method` 再调。
2. **matchMode**：`anyTaskForId(taskId, matchMode)` 的 `0`
   只在根任务里找；ColorOS 把任务嵌在根任务底下，要 0/1 逐档试（ROM 常量 `MATCH_ATTACHED_TASK_ONLY=0` /
   `…OR_RECENT_TASKS=1` / `…AND_RESTORE=2`）。
   **`2` 不用**：那个 `AND_RESTORE` 是真恢复 —— 任务已销毁、只剩 recents 里一条记录时，它会把任务
   **重新建出来**。生产路径（`switchToMiniLocal` / `CMD_MINI_ANIM`）以为自己在「查一个任务」，
   实际会顺手把一个已经关掉的应用复活再收成小窗（「按一下侧键，我刚关掉的应用以小窗弹出来」）。
   探针跟生产**同一份档位**：两边不一致时探针给出的就是与事实相反的结论。
3. **private 可调**：`Reflect.method` 走 `getDeclaredMethod` + `isAccessible=true`，
   `getMiniFlexibleTaskInfo`（private）可用。
4. **模块日志在 LSPosed 文件里**：system*server / 宿主侧 `XLog`
   输出在 `/data/adb/lspd/log/modules*\*.log`，**不在 logcat**。注入侧 i/d 级在日志页整档看不到，判据行必须 W 级。
5. **换包后路径变化**：重装后安装路径变了 → 本次开机模块整体不加载（`device-findings.md` 六之三）。

---

## 十、代码位置与状态

| 文件                                    | 职责                                                                                                                                                                                                                                                          |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bridge/FlexibleWindowBridge.kt` | 小窗指令通道（注入侧执行器）：`handle()` 派发 + `switchToMiniLocal()` 全流程；落点几何由宿主经 extra 下发，文件内那份只是兜底（为什么这么放见 §十一）                                                                                                         |
| `inject/KeyHooks.kt`                    | AMS 广播入口 hook，把 `ACTION` 分派给 `FlexibleWindowBridge.handle`                                                                                                                                                                                           |
| `host/action/AppLauncher.kt`            | `launchAppInWindow` / `putForegroundInMiniWindow(collapsed)`（宿主侧发广播 + 验收 + 尺寸档分流 + 降级）                                                                                                                                                       |
| `bridge/MiniWindowSupport.kt`           | **「这个应用支不支持小窗」的唯一判据**：反射框架 `OplusZoomWindowManager#isSupportZoomMode`，执行侧与界面共用；三态，判不出来时界面不提示也不拦。⚠️ 它**自带系统 toast**（判 `false` 时框架弹「此应用尚未支持浮窗」）⇒ 只能「按下那一刻问一次」，见附十三之四 |
| `core/model/Action.kt`                  | `Action.MiniWindow(collapsed)`：两档的 token 与解析（收起档要排在展开档前）                                                                                                                                                                                   |
| `ui/model/ActionCatalog.kt`             | 「系统小窗」入口格 + `MINI_MODES` 两行菜单                                                                                                                                                                                                                    |
| `ui/screen/AppPicker.kt`                | 界面侧那把尺子的**两个触发点** + 两张拦人的脸                                                                                                                                                                                                                 |
| `ui/component/PageHeader.kt`            | 菜单里那项「以小窗口打开」（临时档，每次进页按正在编辑的动作重算）                                                                                                                                                                                            |

**选择应用页那半边（2026-10-14，两次返工后的落法）**：

1. **问的时机：三处都要**（用户口径）—— 切应用 / 点菜单里那一项 / 点 ✓。前两处钩在「勾选态 + 选中的应用」这个**组合**上（`LaunchedEffect(smallWindow.value, pending, apps)`），后一处在那颗 ✓ 的
   `onClick` 里（第三道是**兜底**：前面判过 ≠ 提交那一刻还是那个结论）。

   前两处的分账（少一处就漏）：
   - 先选应用、再点开「以小窗口打开」⇒ 靠「开关变了」这一下问；
   - 先点开开关、再换应用 ⇒ 靠「选中的应用变了」这一下问。

   合起来才盖住每一条会被写成 `window:<包名>` 的路径。**进页那一次不问**（那是「本来就在编辑
   `window:`」，不是用户刚按下去的手）；关掉开关清空、再点开重新问；同一个包问过不重复问。

2. **判 `false`
   时的处置**：弹「不支持以小窗口打开 / 是否取消「应用名」以小窗口打开？」——确认 ⇒ 把菜单那一项拨回关；**其中从点 ✓ 触发的那一道，确认还顺手按
   `app:<包名>` 写下去并离开本页** （用户最初要的「取消勾选保存」）；取消 ⇒ 什么都不做（开关保持打开）。
3. **点 ✓ 的另一道**：挑中的是**拉不起来**的应用（`LaunchableApp.launchable == false`）⇒ 弹「此应用无法打开」，**没有正文**，确定与取消都只是关掉这句、回本页，什么都不写。
4. ⚠️ **绝不能整表预判**：那个问法自带系统提示 —— 曾经在 `entry()`
   里对 598 个应用各问一次，结果每进一次页面就排出一串系统 toast「此应用尚未支持浮窗」（用户报的「一直弹」就是它）。定因、对照实验与「框架侧没有静的替代判据」的取证见
   `docs/device-findings.md` 附十三之四。行尾那枚「不支持小窗」标签因此**已撤掉**（它只能是整表预判的产物）。
5. ⚠️ 每次询问都可能顺带弹一条系统 toast（判 `false`
   时）—— 那是框架的行为，不去压它。所以同一档（同一个包、同一个勾选态）只问一次；真正提交那一下再问一次，多出来的那条 toast 是「用户正按下 ✓」时该说的话。

**⚠️ 收口状态（`zoom:mini` 展开档）**：主路 = **recents 正门原地收**；设置里「前台转小窗时使用动画」
打开时，收成之后再播框架自己的那一段（§十二，**仅竖屏**）。真机用户口径：**进得去小窗**，尺寸「**稍大了一点，但还能接受**」。

残留量与它的来源（都可直接核对）：

```
配置矩形 = 2240 × 1260        ← 目标当时是「横屏全屏任务」，服务端取了这一套
scale    = 0.5875             ← 该应用记住的缩放
屏幕矩形 = 1316 × 741         ← 2240 × 0.5875；屏宽 1272 ⇒ 右侧多出 ≈44px（左侧 141 起，右沿到 1457）
```

即**宽方向多约 3.5%**。这是服务端 `calcDefaultLaunchBounds` → 「current is fullscreen
task」分支 + 记忆缩放共同算出来的，**宿主侧改不了**（那条分支读的是 intent 与显示帧，不读我们给的东西）。要再收紧只有两条注入侧的落点：`TaskExtImpl#setRememberPosEmpty`（先把记忆的落点清掉，让框架回到「按应用比例算默认」）或
`setRememberPosInfo(Rect,float)` 预置一组放得下的值 ——
**两处都要整机重启**，且要先核实它们是否被这条分支读到（§13.1 已经踩过一次「以为能读、其实不读」）。⏭️ 用户已判定**可接受**，所以没动。

**⚠️ 排查第一格：先确认这一枪到底有没有走 `zoom:mini`。** 看日志里有没有 `zoom:mini →` 开头的行：

```
侧键·单击 → zoom:mini
zoom:mini → 走 recents 正门（原地收、无动画）
```

**没有** `侧键·单击 → zoom:mini` 就说明这一下压根没派到小窗动作上 —— 先去设置页把 **侧键 → 单击 → 前台转小窗**
确认一遍（另一档常见原因是主开关关着，那一档模块整条让位、行为与原生一致）。**别按「小窗坏了」的方向查**。判据行见
`device-findings.md` §十五。

> ⚠️ 本节原来教人去找 `passthrough` / 「放行一次」那两行 —— 那是**已删掉的**「交还系统」（`native` /
> `passthrough`）动作的日志，现在模块里**根本产生不出这两行**
> （2026-10-15 审计 #40）。照着旧步骤排查只会得出「日志对不上 ⇒ 模块没生效」的错误结论。

**已完成**：通道打通、`switchState` 触发原生动画（`animation-leash > 0`
真机确认）、多实例修复工作正常、「太长」「没有操作条」两根因已修（`built = installed = 95928330`）；**收起档（`zoom:mini:collapse`）已接进动作契约与执行侧**（`Action.MiniWindow(collapsed)`，配方见 §2.2 与 §五的尺寸档表）。

**待办**：§7.4 的两个坑都已修 ——（a）不能取 `getMiniFlexibleTaskInfo` 的收起几何；（b）不能把**屏幕矩形**当参数喂
`setRememberPosInfo`，要喂**配置矩形** `Rect(126,319-1398,2580)`。完整取证与推算见 `docs/device-findings.md`
附七。两处都在注入侧 ⇒ ⏭️ **待整机重启验证**。

验收判据（坐标已定死，可照抄，不用目测）：

- 注入侧日志：`已预置展开档几何（宿主下发）配置矩形=Rect(126, 319 - 1398, 2580) scale=0.8017 → 屏幕落点应为 Rect(126, 319 - 1145, 2131)`
  （`1145` 是 `126 + int(1272 × 0.8017)` 算出来的，别记成 `1146`）（括号里是**宿主兜底默认**
  = 宿主没带参数，说明宿主是旧版；正常应显示「宿主下发」）
- 截图量出的窗口屏幕框应是 `(126,319)-(1145,2130)` = **1020 × 1812**（宽占比 `0.8019`）
- 宿主侧日志：`已是小窗、预置几何被吃住（配置宽占比 ≈1.0）` ——
  ⚠️ 这里的比值**只验几何有没有被吃住，不验档位**（两档配置矩形相同，见 §五表下的警告）

若落点仍差，剩余可调项是 bundle 里 `MaxScale` / `MinScale` / `SupportRatios` / `CaptionName`
的取值与 §7.4 那组落点常数（结构不用再动）。

**收起档待真机看落点**（`windowType=2` / 右上角一条）—— 它只走 recents 正门、不碰注入侧， `kill`
宿主主进程即生效，不需要整机重启。

**注入侧纪律**：整包 try/catch 只降级（`switchState`
对任何状态都不乱动窗口，最坏是没反应）；system_server 异常自救 = 进 LSPosed 停用本模块再重启。

---

## 十一、调参放哪边：改哪边就得重启谁

**机制**：LSPosed 只在**进程启动那一刻**注入模块 dex。所以

| 改在哪                        | 生效方式                                                     | 一轮试错成本 |
| ----------------------------- | ------------------------------------------------------------ | ------------ |
| 宿主侧（`com.oplus.gesture`） | `kill` 掉宿主进程，LSPosed 下次启动重新注入                  | **几秒钟**   |
| 注入侧（`system_server`）     | **整机重启**（system_server 是长驻进程，不重启就还是旧 dex） | 几分钟       |

**由此得到一条排参纪律**：任何**需要反复试**的数值，都不要写死在注入侧。

落点几何就吃过这个亏 —— 它本来写死在 `FlexibleWindowBridge`
里，于是每调一次都要整机重启，而当时那组值是错的，等于「一次重启换一个仍然错的结果」。现在改成**宿主侧下发**：

```
core/model/MiniWindowGeometry（按机型标定，一台机器一个 Slot）   ← 真值只有这一份
        ├─ MiniWindowLauncher.EXPANDED_CONFIG_BOUNDS / EXPANDED_SCALE  ← 读它，宿主侧下发（不用重启）
        └─ FlexibleWindowBridge.EXPANDED_BOUNDS / EXPANDED_SCALE       ← 读它，只是「宿主没带」时的兜底
```

（这两个数原来在宿主侧与注入侧各写一份**同一组值**：改一处漏一处时，表现是「下发的与兜底的对不上」，
而兜底那条路只在宿主没带参数时才走得到 —— 平时完全看不出来。现在两侧都从那张表取。）

几何经 4 个 int + 1 个 float 传（不传 `Rect`）：跨进程传 Parcelable 要管类加载器，广播这条路上多一事不如少一事。

⚠️ 同样的道理适用于 §7.5 那袋 bundle 参数（`MaxScale` / `MinScale` / `SupportRatios` /
`CaptionName`）—— 它们现在还在注入侧，**如果它们也要反复调，就照这个模式挪到宿主侧**。

⚠️ 新增注入侧代码仍然要重启一次才加载（协议本身变了），所以「把参数挪出来」这件事本身也要挑时机：**和别的注入侧改动并到一个版本里**，不要为它单独重启一轮。

### 11.1 免重启测试钩子：用 root 发「假手势」

宿主那个手势接收器是 `Context.RECEIVER_NOT_EXPORTED`，但**系统 / root 身份（uid < 10000）能穿过** 这道闸（`adb shell`
的 uid 2000 不行，必须 `su`）。所以可以完全不碰侧键、不重启，直接触发任意动作：

```bash
MS=$(adb shell "awk '{print int(\$1*1000)}' /proc/uptime" | tr -d '\r')
adb shell "su -c 'am broadcast -a com.lookie.opluskey.GESTURE -p com.oplus.gesture \
  --es key plus --es phase single --ez repeat false --el sent_at $MS'"
```

两个要点：

- **`sent_at` 必须是当前时刻**（`SystemClock.elapsedRealtime()` 量级）。传 `0` 会被 `GestureReceiver.handle`
  的「过期丢弃」直接拦掉（`age = now - 0` 远超 `STALE_MS`）。
- `phase` 取 `single` / `double` / `long`；`key` 只有 `plus` 一个。

配合「宿主侧改动 `kill` 进程即生效」，调试一轮的闭环变成： **改宿主代码 → 装机 →
kill 宿主进程 → 发假手势 → 截图 / 看日志** —— 全程不用重启。

> 📌 更强的同类工具是 **debug 通道**（`docs/debug-channel.md`，`DEBUG_PROBE` 闸门）：它能在 `system_server`
> / 桌面进程里跑任意反射（`dump` 拿真实签名、`call` 试调用、 `corner`
> 设圆角）——**注入侧的实验连编译都不用**。上面这个假手势钩子解决「触发」，它解决「执行」；两者配合，注入侧一轮试错从几分钟降到几秒。

⚠️ 注入侧的改动仍然逃不掉重启（见上表），所以**任何能在宿主侧决定的逻辑都别放注入侧**。

### 11.2 一条同源的经验：让「路由选择」也留在宿主侧

`zoom:mini` 走哪条路（recents 正门 / 伪造态）是**宿主侧的一个常量**
（`MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE`）。所以 2026-10-14 从伪造态切回正门那次，只改了宿主代码、`kill`
一下宿主进程就验完了 —— 一次重启都没用。要是把这个分支写在注入侧，那次又得重启一轮。

---

## 十二、原生「全屏 → 小窗」动画器：**可以直调**（2026-10-14 离线读 dex 定论）

背景：§六/§七/附十一/附十二 把三条路走完，结论是**「前台应用 → 小窗 + 框架动画」拿不到**。用户随后定了判据：**自己画动画太脆弱（不要）；如果调不动原生接口，就接受无动画。**
本章回答「调不调得动」——**答案是可以**，而且它不是"手搓动画"：

> 插值、矩阵、`apply()`、以及那条原生 spring **全是框架自己跑的**。我们只**交参数**
> （起点 rect/scale/alpha + 终点 rect/scale/alpha）。手搓的是 iOS 那种「每帧自己算、自己画」——这里一帧都不是我们算的。

### 12.1 三个事实（从 `oplus-services.jar` 的 `classes3.dex` 读出来的，不是猜）

| #   | 事实                                               | 证据                                                                                                                                                                                                                                                                                                                                                      |
| --- | -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **回调是接口**，能用 `Proxy.newProxyInstance` 实现 | `com.android.server.wm.floatwindow.FlexibleAnimationCallback`：**0 字段、0 内部类、无 `<init>`**，5 个方法全是抽象（`onAnimationStart/Update/End/Cancel`、`onAnimationCancel(…,float)`）                                                                                                                                                                  |
| 2   | **起止状态是纯数据**，且**带 Builder**             | `com.android.server.wm.floatwindow.FlexibleAnimationInfo` 字段：`startRect/finishRect`、`startScale/finishScale`、`startAlpha/finishAlpha`、`startPositionX/Y`、`finishPositionX/Y`、`duration`、`interpolator`、`mAnimationLeash`、`type`；`FlexibleAnimationInfo$Builder` 有 **`setAnimationLeash`** 等一整套 setter                                    |
| 3   | **每帧就是一次线性插值 + 三个事务调用**            | `FlexibleWindowAnimator#updateAnimationLayer(Task, info, tx, leash, value, callback)` 的 smali 只用到 `getStartRect/getFinishRect/getStartScale/getFinishScale/getStartAlpha/getFinishAlpha`，然后 `setPosition(leash, lerp x, lerp y)` → `setMatrix(leash, s,0,0,s)` → `setAlpha(leash, lerp a)` → 在 `controller.getTaskTransactionLock()` 里 `apply()` |

⇒ 于是「**缩小变形**」被表达成一组参数，而不是一段我们写的动画：

```
startRect  = 全屏位置      startScale  = 1.0      startAlpha  = 1.0
finishRect = 小窗位置      finishScale = 0.8017   finishAlpha = 1.0
```

### 12.2 要调的签名

```
// 动画器（oplus-services.jar / system_server 内）
com.android.server.wm.FlexibleWindowAnimator
  public void startFlexibleTaskToMiniAnimations(
      ArrayMap<Task, FlexibleAnimationInfo> animationMap,   // value 就是上面的参数对象
      Handler surfaceAnimHandler,
      SurfaceControl.Transaction transaction,               // 自己 new 一个
      FlexibleAnimationCallback callback,                   // Proxy 实现
      String reason)
  // ★ 首选（见 12.3 ③ —— 它没有 isInTransition 过滤，能转换完立刻播）：
  public void startFlexibleSpringAnimation(
      FlexibleAnimationInfo info, Rect bounds, Task task,
      Handler handler, SurfaceControl.Transaction transaction,
      FlexibleAnimationCallback callback, String reason)
  // 另一条同族（有 isInTransition 过滤，只在过渡之外可用）：
  //   startFlexibleAnimation(...) 的参数与上面完全一致

// 参数对象
com.android.server.wm.floatwindow.FlexibleAnimationInfo$Builder  →  build()

// 取「框架自己会怎么算这个任务的动画参数」——现成的，不用自己拼
com.android.server.wm.FlexibleTaskController#getAnimationInfo(Task, int) / (Task, int, ArrayList)
```

leash 的处理是可选的：`startFlexibleTaskToMiniAnimations` 里写的是
`info.getAnimationLeash() != null ? info.getAnimationLeash() : task.getSurfaceControl()`
—— 不传 leash 就**直接对任务自己的 surface** 做变换。

### 12.3 实机验到的三道坎（2026-10-14，两轮，逐个定死）

**① `endAnimation(205)` 那道闸 —— 无害（已核）。** `createSpringAnimation` **只创建、不 `.start()`**，所以
`addSpringAnimation` 之后 `mSpringToMiniAnimationSet.isAnimRunning()` 恒为 false ⇒ `cancelSpringToMiniAnimation`
第一句就早返回。真正的启动在它后面的 `lambda15/16`（`mSpringToMiniAnimationSet.start()`）。

**② 每帧的守卫 `isFlexibleTaskAndHasCaption(task)` ⇒ 「先动画后转换」结构上不成立。** 动画器
`startFlexibleTaskToMiniAnimations` 走通之后（真机日志确认真播了：
`onAnimationStart --Surface(name=Task=127)…FlexibleAnimationInfo{type=205, startScale=1.0, finishScale=0.8017, startRect=Rect(0,0-1272,2772), finishRect=Rect(126,319-1398,2580), leash=null}`），
**每一帧都打了：**

```
W/FlexibleWindowAnimator: updateAnimationLayer task is not flexible task, task=Task{#127}
```

即 `updateAnimationLayer` 第一句 `if (!FlexibleWindowUtils.isFlexibleTaskAndHasCaption(task)) return;`
把普通全屏任务**每帧挡回去** ⇒ spring 在跑、画面纹丝不动。
**所以顺序只能是「先真转换、再播动画」（`CONVERT_THEN_ANIM`）** —— `ANIM_THEN_CONVERT` 留档，不要用。

**③ `startFlexibleTaskToMiniAnimations` 开头有一道 `isInTransition` 过滤 ⇒ 转换后立刻调会被静默剔掉。** 真机日志：

```
D/FlexibleWindowAnimator: startFlexibleTaskToMiniAnimations current task is in transition:Task{#128}
```

被剔掉**没有任何错误日志**，只有那句 `current task is in transition`（或 map 空了之后的一句
`no animation or leash.`），画面什么都不发生。而转换刚做完那一瞬任务**正好还在过渡里** （同一轮里
`SWITCH_TO_DEFAULT_END onAnimationEnd` 在转换后约 400ms 才到）。

⇒ **改用同族的
`startFlexibleSpringAnimation(info, Rect, Task, Handler, Transaction, callback, String)`**：离线 smali 核实它**没有这道过滤**（只做 null 检查 → 建
`FlexibleSpringAnimationSet` → 取 leash
→ 开跑），所以能在转换之后立刻启动，不必等过渡收完（等的话用户会先看到一个静止的小窗）。它自带的 spring 是
`0.87 / 261`（`…ToMiniAnimations` 是 `0.86 / 250`）。

**仍然存在的一条几何妥协**：全屏 `1272×2772`（0.459）与小窗 `1020×1812`（0.563）比例不同，动画器只做**等比**
`setMatrix(s,0,0,s)` ⇒ 起点与落点**不可能同时精确**。现行取舍是保落点（`CONVERT_THEN_ANIM` + `startScale=1.0`）。

### 12.4 结论：**已成立**（2026-10-14 真机，用户亲眼确认）

**动画可见且可用**
—— 全屏应用向屏幕中心缩成小窗。至此「前台应用 → 小窗 + 动画」这条从 §六 追到现在的目标**做出来了**，而且是**框架自己的动画器**在跑（不是手搓）。

两个关键事实是这轮才钉死的：

1. **每帧的 transform 真的作用在画面那层 surface 上。** 判法是一次诊断实验：把 `MINI_ANIM_START_SCALE` 临时改成
   `0.2f`，用户看到窗口**从左上角冒出来再移到屏幕中间**。缩放绕的是 surface 自己的原点，所以缩小版停在左上角 —— 这恰好证明改的就是画面那块 ⇒
   **不需要 `Builder.setAnimationLeash` 自己建 leash**（本来把它列为待验风险之一）。
2. **起点参数就是「全屏外观」**：`startScale=1.0` 配 `startRect=(0,0)`
   是恒等变换，起来那一帧与用户按下去前看到的画面一致。`startScale` 是**观感旋钮**：
   - `1.0`（现行）：mini 内容以原始比例铺开，底部会露出约 511px 的底（因为 mini 逻辑高是 2261，不是 2772）；
   - `1.226`（= `2772/2261`）：铺满整屏、无底部露底，但左右各裁约 144px。两个都在**宿主侧**，改完 `kill` 宿主即生效。

⚠️ 一条**取证教训**（这轮我误判过一次，值得记）：用 `screenrecord` 验动画时， **别用「录像启动后 sleep
N 秒再触发」来推算动画在视频里的时间点** —— `screenrecord`
的启动延迟与后台任务的调度延迟会让实际起点偏出十几秒，于是抠帧窗口整个错开，看起来就像「画面纹丝不动」。
**正确的锚点是画面里的状态栏时钟**：先抠一帧读它的时间，再和 `logcat` 里动画那几行的时间对齐。

### 12.5 读框架代码的离线姿势（本轮新加的省成本手段）

以前要看框架实现只能靠 `probe dump`（只能拿签名）或者翻整个 jar。现在可以离线读：

```bash
# 1) 拉 jar → 解出 dex（androguard 在 oplus-services.jar 的 dex 上能读）
adb pull /system/framework/oplus-services.jar .temp/fw/
cd .temp/fw && mkdir -p x && cd x && unzip -o ../oplus-services.jar 'classes*.dex'

# 2) 读类与方法体（要方法体才判得了「能不能调」）
python tools/dexdump.py .temp/fw/x/classes3.dex class  com.android.server.wm.FlexibleWindowAnimator
python tools/dexdump.py .temp/fw/x/classes3.dex method com.android.server.wm.FlexibleWindowAnimator updateAnimationLayer

# 3) 反查用法（2026-10-14 补的两档）：全 dex 扫，只打命中那一行
python tools/dexdump.py .temp/fw/x/classes3.dex uses "android:activity.mZoomLaunchFlags"
python tools/dexdump.py .temp/fw/x/classes3.dex refs calcDefaultLaunchBounds
```

`tools/dexdump.py` 五档：`class`（列字段+方法）、`method`（打 smali）、`find`（按类名子串找类）、
**`uses`（按字符串常量找用法）**、**`refs`（按被调方法名找调用点）**。装依赖：`pip install androguard`。

**为什么值得**：一次 `probe dump`
只能给签名；**方法体才是判「能不能调」的依据**。而这轮正是靠读方法体，把「能不能调」从"要试好几次"变成"离线就有答案"。

**`uses` / `refs` 为什么必须补**（本轮的真实收获）：判「谁在用这个 key
/ 谁在调这个方法」，看一个类的成员列表是看不出来的。这两档一上，就把三条链路一次扫清：

| 扫法                                                                       | 收获                                                                                                                                                                                                                                                                                        |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `uses "android:activity.mZoomLaunchFlags"`                                 | `FlexibleTaskController#calcFlexibleTaskBounds`（通知那条路）、`lambda$fullscreenFlexibleTask$51`、`lambda$requestPendingFullscreenToMiniTransition$53`，**以及手工翻查漏掉的 `FlexibleTaskTransitionController#skipCurrentOrAdjustChange`**（就是附九/附十一追过的 `launchFrom` 那个判定） |
| `refs calcDefaultLaunchBounds`                                             | 只有 `collectFlexibleTaskInfo` 调它 ⇒ 展开档的几何**只**从这一条算出来（§十三 的根因链由此闭合）                                                                                                                                                                                            |
| `uses extra_window_mode start_zoom_at_other_side`（对 `SmartSideBar.apk`） | 侧边栏的官方配方落在 `PageRoutUtils$startActivityAsZoomWindow$3`（§十三 已抄录）                                                                                                                                                                                                            |

⚠️ **同一条思路对第三方/系统 APK 一样有效**：`adb pull` 下来 `unzip classes*.dex`
直接扫，不需要装、不需要跑（§十三 就是这么把侧边栏的配方挖出来的）：

```bash
adb shell pm path com.coloros.smartsidebar            # 拿路径
adb pull /product/app/SmartSideBar/SmartSideBar.apk .temp/sidebar/
cd .temp/sidebar && mkdir -p x && cd x && unzip -o ../SmartSideBar.apk 'classes*.dex'
python tools/dexdump.py .temp/sidebar/x/classes2.dex uses extra_window_mode start_zoom_at_other_side
python tools/dexdump.py .temp/sidebar/x/classes2.dex method \
  'com.oplus.smartsidebar.panelview.edgepanel.utils.PageRoutUtils$startActivityAsZoomWindow$3' invoke
```

⚠️ **androguard 有读不了的 dex**：`oplus-framework.jar` 的 dex 带新的 hiddenapi 标志， `DEX(...)` 会在
`HiddenApiClassDataItem` 上抛异常。读它改用 jadx（本轮走的是 MCP 的
`inspect_android_class`，能拿到字段/方法签名与内部类清单，但**拿不到方法体**）。所以「要方法体」的活优先找
`oplus-services.jar`（能读）里的那份实现。

⚠️ **§十二 那套动画现在由设置里的开关驱动**（「前台转小窗时使用动画」，`GlobalConfig.miniWindowAnim`）：
开着时 `zoom:mini` 走「recents 正门收 + 立刻播这一段」，关着时只有原地收。落地顺序只有一种 ——
**先真转换、再播**（§12.3 ②：动画器每帧的守卫要求任务已是柔性任务）；原先的 A 档（先动画后转换）
与 `MINI_ANIM_MODE` 那个档位常量**已删**，它们只留档在本章。回调与几何参数仍由宿主侧下发。

---

## 十三、横屏应用：收小窗后**窗口比屏幕还宽** —— 定因（2026-10-14 真机）

> **读这一章的顺序**：本章是**定因过程**，里面那版修法（把主路换成 launch 配方）**已经被回退**
> —— 它确实修掉了溢出，但横屏**进不去小窗**。**现行状态与残留量看 §十**，
> **两条路的取舍看 §13.1**。本章保留原样，是为了留住「为什么会撑出屏幕」的证据链。

**症状**：把横屏应用（如 bilibili 的视频页）收成小窗，窗口横向撑出屏幕。

**硬数据**（PLK110 / 1272×2772 / ROTATION_90 / `tv.danmaku.bili` `#153` 全屏 2240×1260）：

```
配置矩形 = 2240 × 1260        ← 横屏全屏任务那套（dumpsys: Requested w=2240 h=1260）
scale    = 0.5875             ← 为竖屏那套配置矩形标定的缩放（1272 × 0.5875 = 747）
屏幕矩形 = 1316 × 741          ← 2240 × 0.5875；而屏宽 1272 ⇒ 右侧溢出 185px
```

`zoomRect = Rect(141, 266 - 1457, 1007)` 经**截图像素级边缘检测**核实（左 `x=141`、下 `y=1006`
双边吻合）就是**屏幕矩形** —— 判「窗口多大 / 装不装得下」只能用它，不能用
`OplusAppInfo.appBounds`（那是配置矩形，不含缩放）。完整取证见 `docs/device-findings.md` **附十三**。

**根因**：**只出在 recents 正门那条路，且只在目标本身是「横屏全屏任务」时。** 展开档（`flags = 2`）在服务端走
`startFreeFormFromRecent` → `collectFlexibleTaskInfo(intent, …)` →
`calcDefaultLaunchBounds(…, isFixedOrientationLandscape, …)` → `OplusFlexibleTaskLayoutPolicy#calcLaunchBounds` →
`getTaskRealSize(横屏?, ratio)`，其中有一支**「current is fullscreen task」**（真机日志
`OplusFlexibleTaskLayoutPolicy: current is fullscreen task, screenWidth:2772, screenHeight:1272`）把**全屏任务那套尺寸**当成了小窗的配置矩形，再乘一份**竖屏档**缩放 ⇒ 横向撑出屏幕。
**「配置矩形」与「缩放」这一对没配对**，是溢出的直接原因。

**修法：展开档的主路换成官方那条 launch 配方。** `window:` 用的就是它，所以没新增任何 API —— 只是把 `zoom:mini`
也走上去：

```
OplusActivityManager#startZoomWindow(intent, bundle{extra_window_mode=100}, userId, callPkg)
  intent 先加 FLAG_ACTIVITY_NEW_TASK；目标占着栈顶时**先回桌面**再起（否则被栈顶实例消化、返回 3）
```

**真机复验（2026-10-14，宿主侧改动，不用重启）**：

| 场景                     | 走哪条                       | 屏幕矩形                                    | 结论                                                           |
| ------------------------ | ---------------------------- | ------------------------------------------- | -------------------------------------------------------------- |
| 横屏 bilibili 全屏在前台 | launch 配方                  | `Rect(126,319 - 1146,2131)` = **1020×1812** | ✓ 标准展开档、完全在屏内，**taskId 仍是 #153**（复用、没多开） |
| 竖屏 bilibili 全屏在前台 | 有动画（B 档）+ recents 正门 | `Rect(126,319 - 1146,2131)` = **1020×1812** | ✓ 未动、也没坏                                                 |
| 横屏 bilibili（改前）    | recents 正门                 | `Rect(141,266 - 1457,1007)` = 1316×741      | ✗ 溢出 185px                                                   |

⇒ **竖屏完全没被改到**（它原本就是对的），只换了横屏那一支 —— 因为横屏的动画守卫本来就会跳过 ①，落点正好。

> ⚠️
> **上面这张表是当时那版（launch 配方当主路）的读数，那版已回退**（横屏进不去小窗）。表的**证据价值**在于：同一台机器、同一个应用，两条路算出来的几何确实差一个「帧」——
> `launch 配方` 是竖屏帧的 `1020×1812`，`recents 正门` 是横屏帧但撑出屏幕的 `1316×741`。

### 13.1 ⚠️ 未收敛：横屏小窗该是**横屏比例**还是竖屏比例（2026-10-14 用户口径）

上表那次「修好了」只解掉了**溢出**，用户随后指出一个更本质的问题：

> 「这个版本前台横屏小窗是从竖屏态的比例往横屏态的比例收的」

真机把时序抓全了，根因是**「回桌面」这一步**：

```
触发前      ROTATION_90                             ← 横屏应用全屏在前台
发 HOME 后  ROTATION_0                              ← 桌面是竖屏 ⇒ 设备被拽回去
            Rect(126,319-1146,2131) s=0.8016529     ← 小窗按**竖屏比例**生成（1020×1812）
```

⇒ 横屏内容被塞进**竖屏比例**的框里。而**小窗几何是按「起那一刻的显示帧」算的**，所以「回桌面 ⇒ 竖屏 ⇒ 竖屏比例」这条因果链是通的。

**但「不回桌面直接起」这条路没走通**（试过、实测否掉）：

| 试法                                                                                      | 结果                                                                     |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| intent 加 `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`（侧边栏配方 `0x10200000`）后**原地直接起** | 仍返回 **`3`**（`START_DELIVERED_TO_TOP`，被栈顶实例消化）⇒ 还是得回桌面 |

侧边栏能用这条是因为它**只对不在栈顶的应用**起小窗（目标在栈顶时直接放弃，`isAlreadyOpenedInTop`）——所以它的配方里没有「怎么处理前台目标」这一项可抄。

**于是两条路各对一半，还没合起来：**

| 路                               | 方向（比例） | 几何                           | 为什么                                             |
| -------------------------------- | ------------ | ------------------------------ | -------------------------------------------------- |
| launch 配方（**已删**，见下）    | ✗ 竖屏比例   | ✓ 进屏（1020×1812）            | 回桌面 ⇒ 设备转竖屏 ⇒ 按竖屏帧算                   |
| recents 正门（**现行唯一一条**） | ✓ 横屏比例   | ✗ 溢出（1316×741 > 屏宽 1272） | 原地收、**不改方向**，但配置矩形取成了横屏全屏那套 |

⇒ **要的是「横屏帧 + 放得下的缩放」。** 剩下的候选旋钮（都还没试）：服务端
`Intent#getStringExtra("calculate_bounds") == "mini"`（只有我们自己构造的 intent 可控）、 `start_zoom_at_other_side` /
`splashScreenStyle` 两个侧边栏 extra、以及 inject 侧的 `setRememberPosInfo`
预置（要整机重启，且 smali 看它未必被这条读）。

⚠️ 复测时注意：**这台机上的单个「单击」动作会被用户随时改**（实测撞到过 `none` / `keyevent:24` /
`native`），借配置做实验要用「一条命令块内存-改-还原」的方式，别留下改动。

**后续处置（2026-10-14 同日定了下来）**：把主路换回 **recents 正门**
（= 用户记忆里「进得去小窗、所有状态都对，只是没动画」的那版）。对调 ②/③ 那次实测**横屏进不去小窗** ——
**「进得去」优先**。随后连 `launch 配方`
本身也**删了**：它的两个失败分支都通到「回桌面重开」，而用户口径是**收不起来就什么都不做**（§2.2）。想再拿回来，配方与坑都在 §十三 里留着。

⇒ **§13.1 这个取舍（横屏该是哪种比例）仍然是开着的题**：`recents 正门` 比例对但可能撑出屏幕（真机 `1316×741`
vs 屏宽 1272，宽方向多约 3.5%），而**能进屏的那条已经被否掉**（横屏进不去）。

⇒ **动画这一半已经收口**：转场那一段现在只在**竖屏**播（终点几何是竖屏标定值，横屏照样只做原地收）——
所以「转换与动画叠在一起导致横屏收不成小窗」这条不再会发生。要横屏也有动画，得把终点几何换成框架按当前
方向算好的那套（`FlexibleTaskController#getAnimationInfo(task, 1)`，注入侧，见 §12.4）。

**侧边栏的官方配方**（`com.coloros.smartsidebar` →
`PageRoutUtils$startActivityAsZoomWindow$3`，2026-10-14 拉 APK 反编译）：我们这条与它**同一支
`startZoomWindow`**，差别只在两个 extra：

```java
Bundle b = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle();  // 不是裸 Bundle()
b.putInt("extra_window_mode", 100);                  // ← 我们也有 ✓（尺寸与它无关）
b.putInt("android.activity.splashScreenStyle", 1);   // ← 我们没带
b.putInt("start_zoom_at_other_side", 13/14/31);      // ← 我们没带（13=左 14=右 31=第三档）
OplusZoomWindowManager.getInstance().startZoomWindow(intent, b, userId, pkgName);
```

后两个 extra 是**方向 / 位置**语义，与尺寸无关 —— 实测不带也已经拿到标准几何，所以先没加（`start_zoom_at_other_side`
的 31 那档含义还没查）。真要调小窗落在屏幕哪一侧时再动它。

**顺带修掉的假绿灯**：模块原来的验收判据用
`appBounds.width() / Resources.getSystem().displayMetrics.widthPixels`，分母不随旋转变（竖屏恒 1272）⇒ 横屏读到 `1.76`
仍判「几何正常」。已改成读 `getCurrentZoomWindowState().zoomRect` 与 `Display#getRealSize`
比**是否落在屏幕内**，并把验收从 350ms 挪到 700ms（收小窗会先落一个过渡帧，600ms 后才定成最终几何）。

⏭️ **还剩一条没验**：`window:<包名>` 在**目标占着前台**时走的是 `launchAppInWindow`
里的 recents 正门分支（同一根因、同一落点），本轮只验了「目标在后台」那条。要不要一起改，见
`MiniWindowLauncher.launchAppInWindow` 里那段注释。
