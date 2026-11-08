# 17.6 小窗打开应用的接口（`oplus-framework.jar`，2026-10-14，PLK110）

> **汇总定稿见 `notes.md`**
> —— 两条命令的现行实现、所有入口配方、动画方案、反射坑都在那边；本文保留过程性取证（逐条调用链、日志原文、试错记录）。

ColorOS 的「小窗」在框架里叫 **ZoomWindow**（同族还有 `startMiniZoomFromZoom` / `hideZoomWindow` /
`getZoomBoundsByTaskId` / `registerZoomStateListener`）。

- **客户端类 `android.app.OplusActivityManager`**（在 `/system/framework/oplus-framework.jar`，同目录有
  `boot-oplus-framework.vdex`；它内部的 `android.app.OplusActivityTaskManager` 只是那层 AIDL 门面）：
    ```
    startZoomWindow(Intent, Bundle, int userId, String callPkg): int
    startZoomWindowByPendingIntent(PendingIntent, Bundle, int, String): int
    startActivityForFreeform(Intent, Bundle, int, String): int
    hideZoomWindow(int) / getZoomBounds(boolean, int) / isSupportZoomWindowMode()
    ```
    取实例走静态 `getInstance()`。
- **服务端**在 `oplus-services.jar`：`com.android.server.wm.OplusActivityTaskManagerService`（及 `…ServiceEx` /
  `IOplusActivityTaskManager`），另有一支 `OplusFloatingWindowHelper` / `IOplusFloatingWindow`。
- **权限**：宿主 `com.oplus.gesture` 的 `android.permission.MANAGE_ACTIVITY_TASKS: granted=true` （另有
  `START_ANY_ACTIVITY`、`WRITE_SECURE_SETTINGS`，都 granted）⇒ 这族接口从宿主进程调得动。
- **隐藏 API**：宿主 `dumpsys package` 给出
  `hiddenApiEnforcementPolicy=0`（DISABLED）⇒ 反射这些隐藏类与成员不会被拦；清单里的 `usesNonSdkApi=false`
  不是判据，别拿它下结论。
- **另一条退路**（公开 API，但那一对常量 / 方法本身也是隐藏的）：`ActivityOptions.makeBasic()` +
  `setLaunchWindowingMode(WINDOWING_MODE_FREEFORM)`；ColorOS 不认它时就是一次普通打开。
- 模块侧：动作 `window:<包名>`（`Action.AppWindow`）→ `MiniWindowLauncher.launchAppInWindow`，入口解析与 `app:`
  共用（17.5 那套显式组件兜底照用），起法逐层降级 `startZoomWindow` → 自由窗口 → 普通
  `startActivity`，每层都记日志（断在哪一环看日志）。

## 17.6.1 小窗为什么「调了没反应」：`extra_window_mode` 与服务端那个 0（2026-10-14 真机实证）

**现象**：真机日志里 `window:<包名> → startZoomWindow=0`，屏幕毫无变化（`zoom:mini` 也是调完没动静）。

**两条调用链**（dex 反汇编逐条核过，`.temp/rea/dexasm.py`）：

```
android.app.OplusActivityManager#startZoomWindow(Intent, Bundle, int userId, String callPkg): int
   ├─ $Stub$Proxy：writeTypedObject(Intent) → writeTypedObject(Bundle) → writeInt → writeString
   │                → transact(14) → readException → **readInt**（返回值是服务端给的）
   └─ OplusActivityTaskManagerServiceEnhance#startZoomWindow
        ├─ mAmInternal.enforceCallingPermission("oplus.permission.OPLUS_COMPONENT_SAFE", "startZoomWindow")
        └─ FlexibleWindowManagerService.startFlexibleTask(...) → FlexibleTaskController.startFlexibleTask(...)
             ← 返回值一路原样传回客户端
```

- **返回值不是成败标志**（真机探针实测，`.temp/probe/`，app_process + root 直接调）：

    | 调用                                                     | 返回  | 屏幕                                       |
    | -------------------------------------------------------- | ----- | ------------------------------------------ |
    | `startZoomWindow(微云盘, Bundle{})`（不带 extra）        | `0`   | 应用**全屏**打开（服务端自己退成普通启动） |
    | `startZoomWindow(微云盘, Bundle{extra_window_mode=100})` | `0`   | **小窗**出现 ✓                             |
    | `startZoomWindow(设置, Bundle{…=100})`                   | `0`   | **小窗**出现 ✓                             |
    | `startZoomWindow(当前前台那个活动, Bundle{…=100})`       | `3`   | **不变**（前台转小窗走不通）               |
    | 组件名写坏（相对类名拼成双点）                           | `-92` | 不变（错误码）                             |

    结论：**0
    = 已发起**（不管起没起成小窗），**负数 = 错误码**；模块侧只有负数才降级。「带 extra 起了小窗也返回 0」这条是关键 —— 按「0
    = 失败」降级会把好不容易通的路径再破坏掉。

- **根因：Bundle 里得带窗口模式**。空 `Bundle` 时服务端把它当普通启动。能用的调用方是 SystemUI 的
  `com.oplus.systemui.qs.scenario.domain.model.ScenarioAssistantNavigationImpl#startActivityByZoonWindow`：

    ```
    Bundle b = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle();
    b.putInt("extra_window_mode", 100);   // OplusZoomWindowManager.EXTRA_WINDOW_MODE / WINDOWING_MODE_ZOOM
    OplusZoomWindowManager.getInstance().startZoomWindow(intent, b, getCurrentUser(), ctx.getPackageName());
    ```

    常量表（`com.oplus.zoomwindow.OplusZoomWindowManager`，`ofw/classes2.dex`）：
    `EXTRA_WINDOW_MODE = "extra_window_mode"`、`WINDOWING_MODE_ZOOM = 100`、
    `WINDOWING_MODE_ZOOM_TO_FULLSCREEN = 101`、`WINDOWING_MODE_FULLSCREEN = 1`、`WINDOWING_MODE_ZOOM_LEGACY = 6`。

- **`startMiniZoomFromZoom(int)`
  在这台 ROM 上是废接口**：全量扫过 SystemUI（9 个 dex）、桌面（OplusLauncher，7 个 dex）、设置侧拉到的 APK 与
  `oplus-services` —— 除了框架自己的 AIDL 门面与 SDK 封装，**没有任何调用方**；原型是 `void`，没有返回值可判。探针把
  `startWay = 1 / 2 / 7` 各调一遍（当时屏幕上正开着一个小窗），全部无可见变化。
- **`startZoomWindow` 的返回值就是 `ActivityStarter` 的 `START_*` 码**：`0` = `START_SUCCESS`， `3` =
  `START_DELIVERED_TO_TOP`（intent 交给已在栈顶的那个实例消化、屏幕不变），`-92` 一类负数是错误码。服务端
  `FlexibleTaskController.startFlexibleTaskInternal` 里 `extra_window_mode == 0x64` 时才
  `setLaunchWindowingMode(100)`；home 正在过渡时它会**延后执行并立刻返回 0**（`Slog.d` 里有 "wait for
  HomeTransitionFinish startFlexibleTask"）。
- **`FLAG_ACTIVITY_MULTIPLE_TASK`
  才是「前台应用也能起小窗」的那把钥匙（2026-10-14 起模块已不再用它，见 §17.6.5：它会无限多开实例）**（二次真机对照，同一台机、`设置`
  当前正 resume，探针逐组合单调用）：

    | flags                                                | 返回 | 结果                                                  |
    | ---------------------------------------------------- | ---- | ----------------------------------------------------- |
    | `NEW_TASK`（= `getLaunchIntentForPackage` 自带那个） | `0`  | 又开出一份**全屏**实例（`windowingMode=1`），不是小窗 |
    | `NEW_TASK \| MULTIPLE_TASK`（`0x18000000`）          | `0`  | **小窗出现**（`windowingMode=100`，独立 task）✓       |

    `NEW_TASK` 单独一个会被 `ActivityStarter` 判成「交给栈顶那个实例消化」（`3`）或者重开一份全屏实例；加上
    `MULTIPLE_TASK` 才会真的另起一个 task，窗口模式才落得下。早先「`+MULTIPLE_TASK` 也是
    `3`、前台转小窗走不通」那次结论**有误** —— 当时判据只有返回码 + 截图，没看 `getAllTopAppInfos()` 的
    `windowingMode`，把「重开了一份全屏实例」误读成「没起」。

- **「把前台应用收成小窗」的可用配方，已按模块的 intent 形状逐字复现**：
    ```
    Intent i = pm.getLaunchIntentForPackage(pkg);   // flg=0x10000000, cmp=com.android.settings/.Settings
    i.addFlags(NEW_TASK | MULTIPLE_TASK);           // → 0x18000000
    startZoomWindow(i, Bundle{extra_window_mode=100}, userId, ctx.packageName)  → 0
    复核：新 task windowingMode=100（小窗 ✓），原全屏 task 仍在（= 第二份实例）
    ```
    返回 `3` 时先发一个 CATEGORY_HOME 的 intent 回桌面、延迟 400ms 再起一次（`MULTIPLE_TASK`
    在场时正常走不到这一档，留着当兜底）。模块里 `window:<包名>` 与 `zoom:mini` 共用这一套（见
    `MiniWindowLauncher.launchAppInWindow`）。**代价**：小窗里是新起的一份实例，不是把现有那扇窗挪过去。
- **对「刚退到后台的应用」起小窗也成立**：先回桌面、再对 `设置` 调同一支接口，拿到 `0` +
  `windowingMode=100`，且能稳定停住（+1s / +5s / +11s 三张截图核过，小窗一直浮在桌面上）。只是既然 `MULTIPLE_TASK`
  那条在前台就通，`zoom:mini` 不必再绕「先回桌面」这一圈。
- **桌面那条「带几何启动」配方在宿主进程里不生效**（已实测）：取 `getAllTopAppInfos()` 的 taskId/displayId/topActivity →
  intent 带 `androidx.activity.LaunchScenario=1` + `LaunchFlexibleTaskId` →
  `calculateFlexibleWindowBounds(intent,-2,displayId)`（这步真返回 576 字节的 Bundle）→
  `ActivityOptions.setExtraBundle(bundle)` →
  `startActivity`。链路全绿但屏幕不动 ——几何真进了 options（`androidx.flexible.LaunchBounds` /
  `flexible_window_mini_rect` / `ResizeMode` / `LaunchScale` / `LaunchCornerRadius` /
  `android:activity.mZoomLaunchFlags` 逐字核过），服务端不认这个调用方。
- **试错记录**（都不通，别再走）：`startMiniZoomFromZoom(1/2/7)`（无人调用的废接口）、
  `startZoomWindow(前台活动)`（返回 3 无效果，只肯收没在跑的组件）、
  `toggleFlexibleWindow("type_toggle_to_flexible_task" / "…pocket_studio")`（返回 true 无效果，要
  `com.oplus.permission.safe.WINDOW`，宿主持有）、 `startFlexibleWindowForRecentTasks(taskId, rect)`（走的是**分屏**）、
  `WindowContainerTransaction.setWindowingMode(token, 100)(+bounds)`（transaction 成功但窗口不变）。
- **手势那条路本身**（导航手势 → WM shell
  `StageCoordinatorExt`）应用侧够不着；但桌面 quickstep 用的是上面那条「带几何启动」，可复用。
- **task id 怎么取**：`OplusActivityManager#getAllTopAppInfos(): List<OplusAppInfo>`（要
  `oplus.permission.OPLUS_COMPONENT_SAFE`，宿主持有）→ 字段 `taskId` / `topActivity` / `windowMode` / `displayId` /
  `appBounds`。**判断「有没有真起成小窗」只能看 `windowingMode`**（`1` = 全屏，`100`
  = 小窗），返回码和截图都会骗人。小窗在场时这个列表里会同时出现「小窗那份（`100`）」与「全屏那份（`1`）」。
- **取前台活动**：`OplusActivityManager#getTopActivityComponentName(): ComponentName`（arity
  0，同一支类）。有小窗在场时它返回的就是小窗里那个应用 —— 语义是「最前面那个」，不是「全屏那个」。
- **同族的其它入口**（模块里没用，备查）：`hideZoomWindow(int)`（void）、 `toggleFlexibleWindow(String)` /
  `toggleFlexibleWindowByApp(IBinder,int,boolean,boolean)`（均 boolean）、
  `startFlexibleWindowForRecentTasks(int, Rect)`、`startActivityFromRecentsForFlexible(int, Bundle)`、
  `splitScreenForTopApp`。
- **侧键场景表里没有小窗档**（`action_button` 全 13 行核过），所以没有「官方侧键怎么起小窗」可抄。
- **模块侧**：`window:<包名>`（`Action.AppWindow`）→ `MiniWindowLauncher.launchAppInWindow`；`zoom:mini`
  （`Action.MiniWindow`）→ `MiniWindowLauncher.putForegroundInMiniWindow`（取前台包名后复用同一条）。两条都走同一个
  `startZoomWindow` + `zoomWindowBundle()`（`extra_window_mode = 100`）+
  `NEW_TASK | MULTIPLE_TASK`，只在返回**负数**时降级 —— `0` / `2` / `3`
  都不是失败标志。⏭️ 「把**现有**那扇窗原地收成小窗」应用侧仍然做不到：系统自己的「交换应用」走的是「小窗 ⇄ 全屏」两份任务互换，且**明确拒绝同一个包**（见 17.6.2），抄不过来。

## 17.6.2 「交换应用」 = `notifyFlexibleTaskEvent(taskId, 2004)`（2026-10-14，真机实证可复现）

小窗菜单里那一项「交换应用」（前台 ⇄ 小窗互换），整条链已核到**应用侧可调用的那一层**：

```
小窗菜单点「交换应用」
  └─ com.oplus.flexibletask.menu.FlexibleMenuManager#lambda$initListener$2（SystemUI，classes3.dex）
       标题 == R.string.zoom_exchange  →  i2 = FlexibleUtils.FLEXIBLE_TASK_CHANGE_EVENT(= 2004)
       → notifySystemMenuEvent(2004, Bundle{notify_event_async = true})
         → FlexibleUtils.notifyFlexibleTaskEvent(taskId, 2004, bundle)
              └─ com.oplus.flexiblewindow.FlexibleWindowManager.getInstance()      ← 反射可调
                   .notifyFlexibleTaskEvent(int taskId, int event, Bundle)
                   └─ android.app.OplusActivityTaskManager#notifyFlexibleTaskEvent
                        → IOplusActivityTaskManager$Stub$Proxy（Binder）
                          └─ OplusActivityTaskManagerServiceEnhance#notifyFlexibleTaskEvent
                               ── 这一层**没有任何 enforceCallingPermission** ✅
                               └─ FlexibleWindowManagerService#notifyFlexibleTaskEvent
                                    └─ FlexibleTaskController#notifyFlexibleTaskEvent
                                         switch (event) case 2004 →
                                         handleFlexibleExChange(task, "fromMenu")
```

- **`com.oplus.flexiblewindow.FlexibleWindowManager` 定义在 `oplus-framework.jar`（boot
  classpath）**，不是 SystemUI 的私有类 —— 任何进程都 `Class.forName`
  得到（真机核过：`loader = BootClassLoader`）。它是「静态单例 + AIDL 封装」，字段
  `mOAtms = OplusActivityTaskManager.getInstance()` 就是那把 Binder。真机
  `FlexibleWindowManager.getInstance().notifyFlexibleTaskEvent(25447, 2002, bundle)` 调用无异常返回。
- **事件码表**（`com.oplus.flexibletask.utils.FlexibleUtils`，SystemUI `classes3.dex`）：

    | 常量                                               | 值          | 服务端动作                                             |
    | -------------------------------------------------- | ----------- | ------------------------------------------------------ |
    | `FLEXIBLE_TASK_FULL_EVENT`                         | 2001        | `ExitFlexibleTaskToFullStrategy`（小窗 → 全屏）        |
    | `FLEXIBLE_TASK_MINI_EVENT`                         | 2002        | `ExitFlexibleTaskToFloatStrategy`（→ 小窗）            |
    | `FLEXIBLE_TASK_CLOSE_EVENT`                        | 2003        | `ExitFlexibleTaskToBackStrategy`（关闭）               |
    | `FLEXIBLE_TASK_CHANGE_EVENT`                       | 2004        | `handleFlexibleExChange(task, "fromMenu")`（**交换**） |
    | `FLEXIBLE_TASK_MENU_SHOW_EVENT` / `…DISMISS_EVENT` | 2011 / 2012 | 菜单开合的状态同步                                     |

- **`handleFlexibleExChange` 的前置条件**（`FlexibleTaskController` 内逐条读过）：传进去的 `task` 是
  **小窗**那一份，`fullTask = getFullTaskUnderFlexibleTask(task)` 是它下面压着的**全屏**那一份。要求
  `fullTask != null`、**不是桌面**（`isActivityTypeHome` → 直接弹 toast 拒绝）、不是最小化 / canvas、支持小窗、并且
  **`fullTask.getBasePackageName() != task.getBasePackageName()`** （同包 → `fail while package is same` +
  toast）。全过之后：**全屏那份 → 小窗，小窗那份 → 全屏**。
- **调用方只有菜单这一条**：全 `oplus-services` + SystemUI 扫过，`handleFlexibleExChange` 只有 `case 2004`
  那一个调用点（`source = "fromMenu"`）；另一处 `SOURCE_FROM_MINI_DOUBLE_TAP`
  只是它的入参分支，没有第二条可达路径 —— 所以**没有「导航手势版交换」可抄**。
- **真机复现**（探针 `ev <taskId> <event>`，`app_process` + root）：

    | 步骤                                                              | 观察（`getAllTopAppInfos().windowingMode`）                           |
    | ----------------------------------------------------------------- | --------------------------------------------------------------------- |
    | 起点：`设置` 全屏 + `mark.via` 起成小窗                           | task `7` = `设置` `1`；task `25449` = `mark.via` **`100`**            |
    | `notifyFlexibleTaskEvent(25449, 2004, {notify_event_async:true})` | —                                                                     |
    | 截图结果                                                          | `mark.via` 铺满全屏，**`设置` 变成右上角的小窗** ✓ 与手点菜单完全一致 |

    服务端日志同步印证：`FlexibleTaskController: handleFlexibleExChange FlexibleExChange handle change Event Task{…mark.via.gp}`（这行打的是
    **fullTask**）→ `… will exchange to flexible` →
    `FlexibleTaskTransitionHandler: start exchange full to flexibleTask anim success`。

- **为什么「交换应用」抄不进
  `zoom:mini`**：它的语义是「两份**不同包**的任务互换」，既拿不到「把某一份现有窗口原地收成小窗」，也强制要求先有**另一个应用**的小窗在场（那本身就要一次
  `startZoomWindow`），换完还得把陪跑那个应用再关掉（对它发 2003）。为了这么一条路去挑陪跑应用，不如 17.6.1 那条「新起一份实例的小窗」直接。
- **顺带核过**：事件 2002 对**普通全屏任务无效** —— 对 `com.oplus.gesture` 的一份普通全屏任务发 2002，服务端日志只有
  `exitFlexibleTaskWindowInnerLocked exitTo:5` +
  `resetWhenEndExit … fwOptMode-> 0`，屏幕不动。它是「柔性任务」专用收缩，不是「任意任务 → 小窗」。
- **备查（模块里没用）**：同一支接口发 2003 / 2001 可以驱动**已存在的小窗**关闭 / 转全屏； `notifyFlexibleTaskEvent`
  这层无权限闸，是这族里唯一「随便谁都能调」的写接口。

## 17.6.3 「把现有任务原地收成小窗」的正门：`startActivityFromRecentsForFlexible`（2026-10-14 真机实证）

17.6.1 那条配方的代价是**新起一份实例**（`MULTIPLE_TASK`
强制新任务）。要**原地收**（taskId 不变、界面状态全留、不用先按 Home），正门在 recents 那条：

```
android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int taskId, Bundle options)
  └─ OplusActivityTaskManagerServiceEnhance#startActivityFromRecentsForFlexible
       enforceCallingPermission("com.oplus.permission.safe.WINDOW")      ← 宿主持有 ✅
       SafeActivityOptions.fromBundle(options, callingPid, callingUid)
       → TaskSupervisor#startActivityFromRecents(...)
            └─ FlexibleTaskController#onStartActivityFromRecents(Task, ActivityOptions)
                 zoomLaunchFlags   = OplusZoomWindowUtil.getZoomLaunchFlag(options)   // 读 android:activity.mZoomLaunchFlags
                 pendingLaunchZoom = adjustZoomModeIfNeed(options, null, task)
                     ↑ isLaunchFromZoomMode(options)
                       = OplusZoomWindowUtil.getLaunchWindowingMode(options) == 100 || hasLaunchZoomFlag(options)
                 if (pendingLaunchZoom) startFreeFormFromRecent(task, options, zoomLaunchFlags)
                      fullToMini = (zoomLaunchFlags == 32)
                      → FlexibleWindowManagerService#toggleToFlexibleTask(task, record, options, info)
```

**options 里要放什么**（真机逐项试出来的）：

| key                                 | 值    | 作用                                                                                                                       |
| ----------------------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------- |
| `android.activity.windowingMode`    | `100` | **必须**。`ActivityOptions#fromBundle` 只认这个（**点号**）；`extra_window_mode` 那条下划线的 key 在这条路上服务端根本不读 |
| `android:activity.mZoomLaunchFlags` | `2`   | 决定**尺寸档**：`2` = **展开**的普通小窗；`32` = **收起**的最小化窗。见下面那张对照表                                      |
| `extra_window_mode`                 | `100` | 带上无害（只有 `startZoomWindow` 那条读它）                                                                                |

**真机对照**（`设置` / `mark.via` 都**在前台**，直接对前台那份任务调）：

| 步骤                                                         | 观察                                                                                                                                                                                                                                        |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 起点                                                         | task `#25503` = `com.android.settings`，`windowingMode = 1`                                                                                                                                                                                 |
| `startActivityFromRecentsForFlexible(25503, {上面三个 key})` | 返回 `2`（`START_TASK_TO_FRONT`）                                                                                                                                                                                                           |
| 服务端日志                                                   | `onStartActivityFromRecents … pendingLaunchZoom:true` → `startFreeFormFromRecent current pendingLaunchZoom will toggle FlexibleTask` → `toggleToFlexibleTask task:Task{#25503 …}` → `relayoutCaptionView: reason = startFreeFormFromRecent` |
| 复核 `getAllTopAppInfos()`                                   | **还是 task `#25503`，`windowingMode` 由 `1` 变 `100`** ✅                                                                                                                                                                                  |

- **taskId 不变 ⇒ 是同一份任务被原地改了窗口模式**：没有第二份实例、界面状态（滚动位置 / 输入内容 / 返回栈）全保留，也不需要先回桌面。第三方应用（`mark.via`，task
  `#25505`）同样通过。
- **只给 `mZoomLaunchFlags`、不给窗口模式不行**：那次 `pendingLaunchZoom:false`，钩子跑完什么都没做（`windowingMode`
  仍是 `1`）—— 判据是 `getLaunchWindowingMode() == 100`，不是 flags。
- **`mZoomLaunchFlags` 决定的是「展开 / 收起」哪一档**，给错了就会开成右上角那条最小化窗：

    | `mZoomLaunchFlags` | 服务端                                                           | `windowType` | `zoomRect` / `scale`                   | 观感                              |
    | ------------------ | ---------------------------------------------------------------- | ------------ | -------------------------------------- | --------------------------------- |
    | `2`                | `setUserPositionInfo`（沿用用户上次的位置与尺寸）                | `1`          | `Rect(126,319 - 1146,2131)` / `0.8017` | **展开**的普通小窗（居中大面积）✓ |
    | `32`               | `fullToMini = zoomLaunchFlags == 32` → `getMiniFlexibleTaskInfo` | `2`          | `Rect(898,168 - 1203,710)` / `0.2397`  | **收起**的最小化窗（右上角一条）  |

    判「展开还是收起」最省事的是 `OplusZoomWindowInfo#windowType`（`1` = 展开，`2` = 收起），取值走
    `OplusActivityManager#getCurrentZoomWindowState()`，或直接看 `notifyZoomStateChanged` 那行日志。

- **和 17.6.2 的「交换应用」比**：那条要求「另有一份**不同包**的小窗在场」，服务端还明确拒绝同包；这条只要一个 taskId，同包完全没问题。两者都能动现有任务，这条干净得多。
- **模块侧用的就是这一条**（`zoom:mini`）：一步到位、原地收、状态全留。它没有窗口动画，见 17.6.4。

### 附：「伪造应用不在前台」为什么不必做

服务端**确实**有一处前台判定：`OplusZoomNewTaskHelper#isAppAtTop(atms, intent, userId)`，判据是「目标包是不是 top root
task 的 root activity 的包」；它下面紧跟着 `resetActivityOption(activityOptions)`，会把 `setLaunchWindowingMode(0)` /
`setZoomLaunchFlags(-1)` —— 这就是「应用在前台时起不成小窗」的由来。

**但它在真机上是死代码**：`OplusZoomNewTaskHelper#startZoomWindowForNewTaskIfNeed` 第一句就是
`if (FlexibleWindowManagerService.isSupportFlexibleFreeform()) return;`，而
`isSupportFlexibleFreeform() = mIsSupportFlexibleFreeform && !mIsDisableCustomZoomWindow && isZoomSwitchOpened()`，其中
`mIsSupportFlexibleFreeform` 取自特性 `oplus.software.flexible_freeform.support` ——该特性在本机
`/my_stock/etc/extension/com.oplus.oplus-feature.xml` 里**是声明了的**。真机佐证：`com.android.settings` 在前台跑
`startZoomWindow` 时，`OplusZoomNewTaskHelper` 那几行 `Slog.v` 一行都没出现（同一个 logcat 窗口里
`FlexibleTaskController` 的 D 级日志正常在）。

所以在这台 ROM 上，**伪造「不在前台」不会改变任何东西**；真正卡住的是 `ActivityStarter`
的「目标任务是不是当前 top」以及「窗口模式选项只在建任务时生效」这两件事 ——绕开它们不是靠伪装，而是走上面那条 recents 正门（服务端拿到 taskId 后走的是
`toggleToFlexibleTask`，跟「是不是前台」无关）。

## 17.6.4 「原地收 + 有转场动画」的四条路对比（2026-10-14 真机实测）

17.6.3 那条 recents 正门能原地收，但**没有窗口动画**
—— 按下侧键那一瞬窗户直接跳成小窗。把四条路摆一起量过：判据是 logcat 里 `animation-leash`
的条数（真有窗户动画才会刷它，伴随 `SurfaceAnimationRunner: onAnimationStart`），再加 `getAllTopAppInfos()`
里 taskId 有没有变。

| 变体                                                                             | 原地（taskId 不变） | `animation-leash` |
| -------------------------------------------------------------------------------- | ------------------- | ----------------- |
| `startZoomWindow` + `NEW_TASK \| MULTIPLE_TASK`                                  | ✗ 另起一份实例      | 2                 |
| `startActivityFromRecentsForFlexible(taskId, {windowingMode=100, flags=2})`      | ✓                   | **0**             |
| 同上但 `flags=32`（`fullToMini` 档）                                             | ✓                   | **0**             |
| **挪出栈顶 → `startZoomWindow(入口, {extra_window_mode=100})`（只 `NEW_TASK`）** | **✓**               | **2**             |

- **为什么 recents 那条没有动画**：`onStartActivityFromRecents` 走的是 `toggleToFlexibleTask`
  —— 直接改任务的窗口模式与边界，没有「启动」这个动作，也就没有可播的窗户动画（那条路上 SystemUI 的
  `FlexibleTaskTransitionHandler` 只把 transition 收掉，不挂 leash）。
- **挪出栈顶那条为什么有**：它是货真价实的 `ActivityStarter.execute()`
  启动，窗口模式随 options 落下去，于是走标准窗口动画。目标还在栈顶时这条落不下（见 17.6.1），所以先发一个 CATEGORY_HOME 的 intent；服务端
  `startFlexibleTask` 自己会「等 HomeTransitionFinish」，**调用方不用 sleep**，紧接着调就行。
- **只加 `NEW_TASK`，别加 `MULTIPLE_TASK`**：后者会强制新建任务，那就不是原地收了（回到 17.6.1 的代价）。
- **模块侧选的是 recents 那条（无动画）**：`zoom:mini`
  要的是一步到位 —— 挪出栈顶那条虽然既原地又有动画，但要多回桌面一次，权衡后**否掉**了。起完
  `MINI_VERIFY_MS`（当时 350ms； **2026-10-14 改成 700ms**，因为过渡帧约 600ms 才落定，见附十三）再核那份任务的
  `windowingMode` 是不是
  `100`，不是才退回 17.6.1 那条「按包名另起一份实例」。代价：会先回桌面一瞬（服务端要等桌面过渡铺开），换来的是**原地 + 动画**。

## 17.6.5 `window:` 的「多开实例」与修法（2026-10-14 真机实证）

17.6.1 的配方带着 `MULTIPLE_TASK`，副作用是**同一个应用被按几次就多出几份实例**（`window:`
是「打开应用小窗」，对同一个目标反复按很容易触发）。真机量过：对 `com.android.settings` 连按三次，每按一次就多一份
`windowingMode=100` 的小窗。

**根因是 `MULTIPLE_TASK` 强制新建任务**，而它当初是给「目标已在前台」兜底的 —— 那个场景现在归 `zoom:mini` （17.6.3 /
17.6.4），`window:` 不再需要它。

去掉之后（只加 `FLAG_ACTIVITY_NEW_TASK`），服务端的行为正好是想要的：

| 目标当时的状态           | 服务端行为                                                        | 实例数          |
| ------------------------ | ----------------------------------------------------------------- | --------------- |
| 还没跑过                 | 新起一份、落在小窗（`windowingMode=100`）                         | 1（本来就该有） |
| 有现成任务（在前台之外） | **复用那份任务**，`windowingMode` 1 → 100，taskId 不变            | **不变** ✓      |
| 已是小窗                 | 再按返回 `0`/`2`，没有第三份                                      | 不变 ✓          |
| 就在前台                 | 会被栈顶那份实例消化（返 `3`）→ 改走 17.6.3 的 recents 正门原地收 | 不变 ✓          |

- **真机实证**（`设置`）：task `#25535` 全屏 → 回桌面 → 按 `window:` 那套起 → **还是 `#25535`**， `windowingMode` 由 `1`
  变 `100`；连按三次之后 dump 只剩这一份小窗 + 桌面，没有累积。
- **模块侧**：`launchAppInWindow` 改成 ① 目标就在前台 → 直接走 17.6.3 那条原地收（`foregroundTaskFor` 判
  `getAllTopAppInfos()[0]` 是不是它）；② 其余只加 `NEW_TASK` 起 `startZoomWindow`；③ 反射路不通才退自由窗口 / 普通打开。
  `MULTIPLE_TASK`
  整个删掉。原来「返 3 就回桌面再起一次」的兜底保留，但只在 ① 没接住时当保险（它本身也是原地转，不新建）。
- 判断「有没有真成小窗」照旧只看 `windowingMode`（`1` = 全屏，`100` = 小窗）。

### 附二：为什么 in-place 那族**结构上不可能**有动画（2026-10-14 定论）

不是「参数没给对」，是过渡本身被丢掉了。in-place 那族（`startActivityFromRecentsForFlexible` 与公开的
`ActivityStarter#startActivityFromRecents`）是在 binder 调用里**同步**把窗口模式改完的；等框架为这次调用建立过渡时，任务表面已经在新位置了，过渡里**一个可动画的 root 都没有**：

```
WindowManagerShell: No transition roots in (#127) android.os.BinderProxy@… so abort
FlexibleTaskTransitionHandler:  onTransitionConsumed transition:… ,aborted:true
```

实测（`animation-leash` 计数，判据同 17.6.4）：in-place 那条**前台**跑 `leash=0`，**把任务放后台再跑也是 `leash=0`**
—— 与任务在不在前台无关，它就没有过渡可播。

动画只可能来自**一次带 root 的真启动**：`startZoomWindow` →
`ActivityStarter.execute()`，窗口模式随 options 落下去，任务表面要移动 ⇒ 过渡里有 root
⇒ 标准窗口动画开跑（`leash=2`）。注意这条动画**不是**柔性任务那支动画 —— SystemUI 的 `FlexibleTaskTransitionHandler`
对它是 `no need handler this transitionInfo`（`consumedByThisHandler` 返回 false），跑的是普通应用开合动画。

### 附三：动画入口全表（找过一圈，2026-10-14）

| 入口                                                                                                                       | 原地 | 动画                                  | 卡在哪                                                                                                      |
| -------------------------------------------------------------------------------------------------------------------------- | ---- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `startZoomWindow`（真启动）                                                                                                | ✗    | ✓（`leash=2`，标准窗口动画）          | 目标在栈顶时启动被吞 / 窗口模式落不下，必须先挪出栈顶                                                       |
| `startActivityFromRecentsForFlexible(taskId, opts)`                                                                        | ✓    | ✗                                     | 过渡无 root → abort（附二）                                                                                 |
| 公开 `ATS#startActivityFromRecents(taskId, opts)`                                                                          | ✓    | ✗                                     | 同上                                                                                                        |
| `notifyFlexibleTaskEvent(taskId, 2002)`                                                                                    | ✓    | —                                     | 只对**已经是柔性**的任务（普通全屏任务实测只打 `exitTo:5` 就结束）                                          |
| `toggleFlexibleWindow(toType)` / `OplusFlexibleAnimationRunner`                                                            | —    | ✓                                     | 只对**已存在的柔性任务**：`ToggleTaskRunnable` 取的是 `embeddedTasks`，没有柔性任务时返回 true 但什么都不做 |
| `notifyFlexibleTaskEvent(taskId, 2004)`「交换」                                                                            | ✓    | ✓ `performExchangeFullToFlexibleAnim` | 要求另有一份**不同包**的小窗在场，且服务端明确拒绝同包                                                      |
| 拖拽手势（`OplusZoomWindowManager` 的 `onInputEvent` / `initializeDragZoomAnimation` → `finishDragAnimationAndStartZoom`） | ✓    | ✓                                     | 只能靠喂输入事件驱动；调用方是桌面 / SystemUI 的输入接收器，应用侧没有一步到位的调法                        |

- **那支动画是按「过渡类型」选的，不是按调用方**：`FullscreenTaskOperations#handleFullscreenToFreeformAnimation( IBinder transition, TransitionInfo info, Transaction t, Transaction finishT, TransitionFinishedCallback cb)`
  —— 签名里就带着过渡与 finish 回调，**只能在一次真实过渡里跑**，没法对着某个任务单独调用。选型在
  `FullscreenTaskOperations$TransitionHandler`：`transitionInfo.getType() == 1106` 时才走这支（`1107`
  = 交换）。我们的 in-place 那条产生的是普通 `TO_FRONT`，压根不是 1106。
- **推论**：想有动画，得让这次变更落进一次**类型正确的过渡**里。应用侧造不出这种过渡（见附二）；能造的只有框架自己（真启动）或 shell 侧（桌面 /
  SystemUI）。

### 附四：框架自己那条 full→mini 动画的正门（2026-10-14）

原生那条动画的实现是 **`com.android.server.wm.FlexibleTaskController#switchState(Task, int nextState, String reason)`**
（`public`）→
`com.android.server.wm.FlexibleWindowAnimator#startFlexibleTaskToMiniAnimations( ArrayMap<Task, FlexibleAnimationInfo>, Handler surfaceAnimHandler, SurfaceControl.Transaction, callback, reason)`。
`nextState`：`1` = 小窗、`2` = 全屏；每个任务配一个
`FlexibleAnimationInfo`（startRect/finishRect/startScale/finishScale/
alpha/时长/插值器/leash…，**构造器是 public**）。`switchState(task, 1, …)` 就是「全屏 → 小窗」那一档。

`switchState(…, 1, …)` 全 ROM 只有三个调用点，逐条核过判据：

| 调用点                                                                 | 判据                                                                                                                                                        | 应用侧能不能满足                                                                                                                                                                     |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `onStartActivityFromRecents`（reason `onStartActivityFromRecents`）    | `zoomLaunchFlags == 2` **且** `isFlexibleTaskAndHasCaption(task)` **且** `task.getFlexibleZoomState() == 2`（柔性任务、处于**全屏态**）                     | 实测：普通全屏任务走 recents 正门只打 `pendingLaunchZoom:true` 就转 `startFreeFormFromRecent`，**不命中**，`leash=0`；`flags=3` 也造不出「柔性 + 全屏态」（结果仍是 `state=1` 小窗） |
| `getReusableOptionsForFreeForm`（reason `StartMiniFromLauncher`）      | `isStartFromLauncher()` = `startActivity.isLaunchSourceType(2) && mAtms.mHomeProcess.mPid == request.realCallingPid` —— **调用方 pid 必须等于桌面进程 pid** | ✗：这个 pid 是服务端在 `Binder.clearCallingIdentity()` **之前**取的**真实**调用方 pid，参数里伪造不了。要满足只能**从桌面进程里发起**（= 注入桌面）                                  |
| `switchState(task, 2/1, "STATE_CHANGE_TO_MINI"/"STATE_CHANG_TO_ZOOM")` | 内部状态机                                                                                                                                                  | ✗                                                                                                                                                                                    |

- **可复用的原语都已确认可反射**：`FlexibleWindowManagerService.getInstance(null)`（框架自己就到处这么传 null）→
  `.getFlexibleTaskController()` → `switchState(...)`；`Task` 由
  `ActivityTaskManagerService#mRootWindowContainer#anyTaskForId` 拿；动画器与 `FlexibleAnimationInfo` 同上。
- **模块本来就注入 system_server**（侧键接管那一侧），所以上面这些**在模块侧够得着**
  —— 这是最省的「一比一」：用框架自己的动画器，而不是自己拿 leash 画。
- ⚠️ 代价：这是**注入侧**改动，一旦抛异常就是系统进程崩溃 / 开机循环，必须整包 `try/catch` 只降级；且换包后要
  **整机重启**才生效（软重启会把框架停坏，见 `../device/lsposed.md` 六之四）。

### 附五：`flexibleZoomState` 的实测取值（2026-10-14，模块经 system_server 通道读出）

为这条通道新建了注入侧执行器 `bridge/FlexibleWindowBridge.kt`（与 `StatusBarBridge`
同款：宿主发广播、注入侧在 AMS 广播入口接住、本机执行），把一直读不到的 `TaskExtImpl#getFlexibleZoomState()` 读出来了：

- **普通全屏任务（前台）**：`flexibleZoomState = 0`，`bounds = Rect(0,0-1272,2772)`；此时 `switchState(task, 1, …)`
  第一句 `isFlexibleTaskAndHasCaption` 不满足，直接返回 —— 不会播动画。
- **recents 正门转完之后**：同一任务 `flexibleTaskState = 1`（小窗）、`isFlexibleWindow = true`。
- `flexibleZoomState` 的取值语义：`0` = 非柔性任务、`1` = 柔性任务在小窗态、`2` = 柔性任务最大化到全屏。

所以原生动画要的 `state == 2`，从 `0` 出发**必须经过
`1`**（先收成小窗、再最大化到全屏）—— 一次可见的中间态，应用侧跳不过去。这也把「手工复刻」的形态定死了：**不能**走
`switchState`，只能自己构造 `FlexibleAnimationInfo`（start = 全屏 rect/scale 1.0，finish
= 小窗 rect/scale）+ 拿任务的快照 leash，直接调 `FlexibleWindowAnimator#startFlexibleTaskToMiniAnimations`
—— 动画器是框架的，配方是自己的。原语全部 public、全部在 system_server（模块已注入），但快照捕获（`TaskSnapshotController`）与 leash 构建需要另写，是一个 200+ 行、每轮调参都要整机重启的子系统。

- **踩过的坑（记下）**：`Reflect.call(receiver, name, args)` 按**参数运行时类**找方法 —— Kotlin 的 `Int` 会被装箱成
  `Integer`，而 `anyTaskForId(int,int)` / `switchState(Task,int,String)` 的签名是原始类型， `getDeclaredMethod`
  匹配不上且**静默返回 null**。对原始类型参数必须走 `Reflect.method(cls, name, Integer.TYPE, …)` 拿到 `Method` 再调。
  **结论：应用侧不存在「一步到位 + 带动画」的全屏→小窗入口。**
  有动画的那条必须让任务先离开栈顶；一步到位的那条结构上就没有过渡可播。

### 附六：伪造态路线的两个落点缺陷（「太长」/「没有操作条」，2026-10-14 修正）

伪造态 + `switchState` 跑通后剩两个缺陷，逐条挖到根：

1. **落点太长**：`switchState` 收缩的 finishRect 取自 `TaskExtImpl#getRememberPosInfo()` （`getAnimationInfo(task, 1)`
   里 `isRememberInfoValid`
   不成立时落到「按应用比例算默认」分支，用**全屏宽高比**缩放 → 窗口被拉长）。修法：`switchState` 前调
   `ext.setRememberPosInfo(bounds, scale)` 预置落点。注意 `setRememberPosInfo(Rect, float)` 含原始类型参数，必须显式
   `getDeclaredMethod`（装箱坑见附五）。
    - ⚠️ **预置值不能取
      `controller.getMiniFlexibleTaskInfo(task, record)`**（2026-10-14 真机否掉）：它返回的是**收起档**几何（`Rect(898,168-1203,710)`
      / `0.2397`，同 17.6.3 尺寸档表 `flags=32`
      那一行），喂进去就把「展开」的动画落到了收起的位置 —— 真机表现是**「按一下直接变成右上角那条最小化窗」**。
      **展开档要用 `setUserPositionInfo` 那条路的值**（17.6.3 表里 `flags=2` 那一行）。⚠️ 但**别照抄它的屏幕矩形**：喂给
      `setRememberPosInfo` 的必须是**配置矩形** `Rect(126,319-1398,2580)`
      —— 两者差一个 scale，抄错就「小一圈」，**见附七**。本机现在就是这么写死的（`core/model/MiniWindowGeometry`）。⚠️ 这组数是 PLK110（1272×2772
      @560dpi）实测值，与机型允许表绑定，加机型要重测。
    - 📌 **订正**：本小节 2026-10-15 那版把上面这两个值错记成了「`getMiniFlexibleTaskInfo`
      的真机标准值」，两个来源被混为一谈 —— 那正是这次「落点变收起」的根因。现已按真机结果分开。

2. **没有操作条**：操作条视图由 `TaskExtImpl#updateFlexibleTaskParams` **内部**负责（`mHasCaption` 分支 →
   `FlexibleWindowManagerService#createFlexibleCaptionView(task, captionName)` → `Class.forName(captionName)` 反射建
   `FlexibleTaskCaptionView(Context, FlexibleTaskController, Task, ATMS)` → `initOrUpdateFlexibleCaptionView`
   挂到任务上）。**captionName 不是任意字符串，是视图实现类的全名**，来自策略配置
   `FlexibleWindowManagerService#getCaptionName(scenario)`（mini 的 scenario=1）。我们喂的 bundle 里没放
   `androidx.activity.CaptionName` → `mCaptionName=null` → `Class.forName(null)` 抛异常被吞 → 视图建不出来。修法：反射
   `getCaptionName(1)` 拿到真名（本机为 `com.android.server.wm.FlexibleTaskCaptionView`）塞进 bundle 的
   `androidx.activity.CaptionName`。

### 附七：`setRememberPosInfo` 的 `Rect` 是**配置矩形**，不是屏幕矩形（2026-10-14 真机定论，PLK110）

**现象**：附六那两个缺陷都修完之后，`zoom:mini`
打出来的**是小窗、也不是收起档**，但就是「比 ColorOS 原生的小一圈」，用户一眼判「还是不对」。

**取证**（可复用，别靠肉眼）：同一次按键，两条路各取一个数 ——

| 来源                 | 手段                                            | 结果                                                                  |
| -------------------- | ----------------------------------------------- | --------------------------------------------------------------------- |
| 模块产出             | 注入侧日志                                      | 预置 `Rect(126,319-1146,2131)` / `0.8017`                             |
| 模块产出             | 截图逐列扫亮像素（脚本 `.temp/measure_win.py`） | 窗口屏幕框 `(126,319)-(942,1770)` = **817 × 1452**，宽占比 **0.6423** |
| 宿主判定             | `OplusAppInfo.appBounds` 宽 ÷ 屏宽              | **0.8019**（于是打了「已是展开档」的假绿灯）                          |
| 用户手调出的正确形态 | 截图同上                                        | `(126,319)-(1145,2130)` = **1020 × 1812**，宽占比 **0.8019**          |
| 用户手调出的正确形态 | `dumpsys activity activities` → `Task.mBounds`  | `Rect(126,319 - 1398,2580)` = **1272 × 2261**                         |

**根因**：`setRememberPosInfo(Rect, float)` 的 `Rect` 是**应用配置矩形**，系统还会把它的尺寸 **再乘一次 `scale`**
才是屏幕上的实际大小：

```
屏幕尺寸 = Rect.size × scale
```

我当时把**屏幕**矩形 `1020 × 1812` 直接喂了进去，于是又被缩了一次： `1020 × 0.8017 = 817.6` → 真机读到的 `817`；高度
`1812 × 0.8017 = 1452.7` → 读到的 `1452`。 **两个轴都严丝合缝，比值同为 1.2485 = 1/0.8017**，不是巧合。而
`Rect.left/top` 是**直接照搬到屏幕坐标**的、不含缩放 —— 所以两头左上角都是
`(126,319)`：这也是「只有尺寸错、位置没错」的原因。

**正确值**（本机型实测，落在 `core/model/MiniWindowGeometry`）：

|        | 配置矩形（喂 API 的）                     | scale    | 屏幕落点（验收看的）                      |
| ------ | ----------------------------------------- | -------- | ----------------------------------------- |
| 展开档 | `Rect(126,319 - 1398,2580)` = 1272 × 2261 | `0.8017` | `Rect(126,319 - 1145,2131)` = 1020 × 1812 |
| 收起档 | 同左（系统自算）                          | `0.2397` | `Rect(898,168 - 1203,710)` = 305 × 542    |

> 口径说明：上表用**半开区间**（`right/bottom` 不含）。逐像素扫截图得到的是**闭区间**，同一个窗口记作
> `(126,319)-(1145,2130)`，两者等价、尺寸都是 `1020 × 1812`，别当成差 1px 的异常。模块日志里 `screenBoundsOf`
> 打的是半开形式，所以判据字符串应是 `Rect(126,319 - 1145,2131)`。

**由此得到两个可复用结论**：

1. **小窗的逻辑尺寸恒为 `1272 × 2261`，两档共用**，只有 scale 不同。交叉验证：收起档 `542 ÷ 0.2397 = 2261`、展开档
   `1812 ÷ 0.8017 = 2260.4` —— 同一个 2261。所以**配置矩形分不出档位**（两档都是 1272 宽），拿 `appBounds`
   当档位判据必然是假绿灯。
2. **两个 API 的 Rect 语义相反**，这是最容易踩的地方：
    - `getMiniFlexibleTaskInfo` 返回的是**屏幕矩形**（`Rect(898,168-1203,710)`，305 宽）；
    - `setRememberPosInfo` 要的是**配置矩形**（1272 宽）。
    - 一进一出正好差一个 scale，直接互喂就会「小一圈 / 大一圈」。

> 📌 **订正**：附六里写的「展开档 `Rect(126,319-1146,2131)` / `0.8017`」是**屏幕值**，那是**验收口径**，不是能喂给
> `setRememberPosInfo` 的参数 —— 直接照抄进代码就会得到上面那个「小一圈」。喂 API 请用本节的配置矩形
> `Rect(126,319-1398,2580)`。

### 附八：伪造态小窗是**半成品** —— 缺「柔性任务注册」那一步（2026-10-14 真机，PLK110）

附七把几何修对之后，真机（重启后）判据全绿：

```
小窗指令：已预置展开档几何（宿主下发）配置矩形=Rect(126, 319 - 1398, 2580) scale=0.8017 → 屏幕落点应为 Rect(126, 319 - 1145, 2131)
小窗指令：caption 实现类 = com.android.server.wm.FlexibleTaskCaptionView
小窗指令：已喂 updateFlexibleTaskParams（含 HasCaption=true）
小窗指令：冒充柔性全屏态成功（mScenario=1 / mHasCaption=true / mFlexibleZoomState=2）
小窗指令：switchState(#71, 1) 已调用
宿主侧：任务 #71 已是小窗、预置几何被吃住（配置宽占比 1.0）
```

**几何确实对了**（`Requested w=1272 h=2261`、屏幕落点
`1020 × 1812`），**但窗口不能用**。用户原话：「点击被视为拖动，也没有菜单和操作条和圆角」。逐条坐实：

| 症状              | 真机所见                                                              |
| ----------------- | --------------------------------------------------------------------- |
| 没圆角            | 窗口是**直角**矩形，没有柔性窗那种大圆角                              |
| 没操作条 / 没菜单 | 顶部那行 `●●●` 操作条与 `⋮` 菜单**完全不存在**                        |
| 输入被吃掉        | **点一下会被当成拖动**，整个窗口跟着手走                              |
| 内容叠两份        | 微信标题栏 `微信(28) 🔍 ⊕` 在窗口顶部**出现两次**（纵向相差约 160px） |

**排查过程（可复用的两条）**：

1. **窗口列表对比**：拿「用户手调出的正确形态」与「伪造态」两次 `dumpsys window windows` 逐行列， **窗口集合完全同构**
   —— 都没多出操作条窗口。⇒ 操作条**不是独立窗口**，是挂在应用窗口内部的；所以「找不到操作条窗口」不等于「操作条没建」，别顺着这条路找。
2. **`windowType` 不能当档位判据**：`dumpsys activity activities` 里 ActivityRecord 的 `windowType`
   在展开档、伪造态、**连桌面**上都是 `2`。⚠️ 这和附六/`notes.md` §五表里那个
   `OplusZoomWindowInfo.windowType`（1=展开/2=收起） **不是同一个字段** —— 那个是 `OplusZoomWindowInfo`
   上的，这两个名字撞车了，别再混用。

**根因（推断，四条症状同一指向）**：**框架的「柔性任务注册」那一步没做**。操作条视图、圆角、拖拽与输入处理都是那一步配上去的；伪造态只捅了三个状态字段（`mScenario`
/ `mHasCaption` / `mFlexibleZoomState`）+ `switchState`，**状态位对了、外围那层壳子没人装**
——于是几何/档位都对，窗口却是个「只有正确大小与位置的裸窗口」。

**结论与处置**：暂时**不用**伪造态这条开窗，改走 **recents 正门** （`startActivityFromRecentsForFlexible` +
`mZoomLaunchFlags = 2`，§17.6.3，真机实证过的展开档）——它走的是框架自己的柔性窗启动链路，操作条 / 圆角 / 输入都有人配。代价是**没有原生动画**。伪造态那条留在代码里、由宿主侧开关
`MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE` 控制（默认 `false`），哪天把注册那步补上再打开。

**正门真机验证（同日 17:44，未重启整机）**：`kill` 宿主进程后用假手势触发（见 `notes.md` §11.1），日志
`走 recents 正门` → `从 recents 把任务 #72 原地收成小窗（flags=2，返回 2）`，四个症状**全好**：

| 症状            | 正门开出来                                                                             |
| --------------- | -------------------------------------------------------------------------------------- |
| 没圆角          | ✅ 大圆角                                                                              |
| 没操作条 / 菜单 | ✅ 顶部 `●●●`；点它弹出原生菜单（全屏 / 分屏 / 最小化 / 关闭 / 交换应用 / 透明度调节） |
| 点击被当成拖动  | ✅ `input tap` 打操作条：菜单弹出、**窗口 frame 未变**（始终 `[126,319][1398,2580]`）  |
| 内容叠两份      | ✅ 只有一份                                                                            |

几何：截图量出 `(126,319)-(1144,2130)` = 1019 × 1812（目标 1020 × 1812，差 1px 是扫描阈值）。⇒
**「窗口是半成品」确实是伪造态独有的问题，不是设备 / 应用的问题。**

> ⚠️ 正门的落点**不是** `EXPANDED_CONFIG_BOUNDS` 给的，而是 `setUserPositionInfo` 沿用
> **系统记住的位置**（「用户上次的落点」）。那两个常量现在只对伪造态那条有效。

> 待办：要拿回动画，就得找到那条「柔性任务注册」的入口（候选：`FlexibleWindowManagerService` 上那条 `launchFlexibleTask`
> 一类的方法，或 `FlexibleTaskController` 里建完 caption 之后还要调的那一步）。§17.6.4 附三「动画入口全表」可作为起点。

### 附九：「前台 → 小窗」的过渡为什么 abort；壳子其实是**过渡**装的（2026-10-14 三组实测对照）

附八把「伪造态是半成品」的根因推成了「柔性任务注册那步没做」。**本节修正这个推断**：壳子（圆角 + 操作条）是那条**窗口过渡**装的，所以更可能只是「过渡 abort 的后果」。

**三组对照**（同机型、同一天，逐行比 `logcat`）：

|                          | 目标任务状态               | `onStartActivityFromRecents`               | 多出来的一步                                   | 过渡结果                                                                     |
| ------------------------ | -------------------------- | ------------------------------------------ | ---------------------------------------------- | ---------------------------------------------------------------------------- |
| 桌面「**近期任务浮窗**」 | 在最近任务里（**非前台**） | `isFlexibleTask:false` `zoomLaunchFlags:2` | **`skipCurrentOrAdjustChange … launchFrom=2`** | 建出 leash → `FlexibleTaskTransitionHandler` → **`MultiSpringAnimation`** ✅ |
| 模块 recents 正门        | 普通全屏、**前台可见**     | `isFlexibleTask:false` `zoomLaunchFlags:2` | 无                                             | `No transition roots in (#62) … so abort` ❌                                 |
| 模块 recents 正门        | 已是小窗、不可见（脏状态） | `isFlexibleTask:true` `zoomLaunchFlags:2`  | 无                                             | 同上 abort ❌                                                                |

三点结论：

1. **`zoomLaunchFlags=2` 两边一模一样** ⇒ 不是参数问题；**调用方身份**（桌面 vs 模块宿主）才是变量。桌面那次比我们多一条
   `skipCurrentOrAdjustChange … launchFrom=2`，而 `launchFrom` 很可能就是 `ActivityRecord#LaunchSourceType`
   的取值（`isLaunchSourceType` / `determineLaunchSourceType` / `LaunchSourceType` 都在 **`services.jar` 的
   `classes4.dex`** 里，不在前两个 dex）。
2. **壳子是过渡装的**，不是「注册」装的 —— 桌面成功那次有这两句，都只在真过渡里跑：
    ```
    FlexibleTaskTransitionController: adjustSurfaceForTransitionIfNeed type=3, cornerRadius=91, scale=0.8016529
    FlexibleTaskTransitionController: finishTransition will update task's caption visible when open transit.
    ```
    ⚠️ 我们 abort 那次 `finishTransition` 也打了第二句，但那是在 `aborted:true` **之后**跑的，没救回来。
3. **前台任务确实没有过渡 root**，与附二一致 —— 这不是测试脏状态造成的，清干净后复测仍然 abort。

**两条可复用的查法**：

- 桌面那个入口菜单项叫「**近期任务浮窗**」。按「小窗」在桌面 dex 里**一条都搜不到**
  ——找 OEM 入口时别只搜一个词，先把 UI 上看来的原词记下来再搜。
- 圆角 / 操作条的**归属**可以直接从日志认：谁打的 `adjustSurfaceForTransitionIfNeed` /
  `caption visible`，就是谁装的。别从「有没有对应窗口」倒推（附八那条弯路）。

**由此得到两条待试的路**（都还没做）：

- **A · 从桌面进程发起**：赌 `launchFrom=2`
  会让框架**把窗口模式变更延后进过渡**，于是前台任务也有 root。⚠️ 风险：root 也可能只是因为「桌面的画面在那儿、有东西可以从它动画出去」；若是这样，A 对前台场景不成立。
- **C · 保留 `switchState` 的动画、自己装壳**：圆角用 `SurfaceControl.Transaction#setCornerRadius`
  （**公开 API**），操作条用 `FlexibleWindowManagerService#createFlexibleCaptionView(task, captionName)`
  （`notes.md` §7.5 已查到，`captionName` 也已反射拿到）。不赌框架分支，且"点击被当拖动"很可能随操作条一起好 ——拖拽手柄就是操作条。

### 附十：C 的前半已成立（圆角），后半卡在**线程**上（2026-10-14 真机，两轮）

靠 debug 通道（`../../runtime/debug-channel.md`）在真机上试的，**没有为试错编译过**：

- **操作条视图本来就已经建出来了** —— 不是「注册没做」： `TaskExtImpl.getFlexibleCaptionView()` 返回
  `FlexibleCaptionView{taskId=N}`，`mHasCaption=true`、
  `mCaptionName=com.android.server.wm.FlexibleTaskCaptionView`。所以缺的是**挂载 / 显示**，不是创建。
- **真签名（通道 `dump` 出来的，不用再猜）**：
  `FlexibleWindowManagerService.createFlexibleCaptionView(Task, String) : FlexibleCaptionView`、
  `FlexibleCaptionView.initCaptionViewAndAttachToTask(Task, Rect)`、`initCaptionView()`、 `showCaptionViewWithAnim()` /
  `hideCaptionViewWithAnim()`、`updateFlexibleTaskCaptionVisible(Task, boolean)`、
  `TaskExtImpl.getFlexibleCaptionView() / setFlexibleCaptionView(FlexibleCaptionView)`。
- **圆角：成功**。`SurfaceControl` 取自 `WindowContainer#getSurfaceControl()`（0 参那个候选命中），
  `Transaction#setCornerRadius(sc, 91)` + `apply()` 之后**截图四角肉眼可见圆角**（补壳前是直角）。
- ⚠️ **操作条：卡在调用线程**。 `initCaptionView()` 与 `initCaptionViewAndAttachToTask(Task, Rect)` 都抛：
    ```
    android.view.ViewRootImpl$CalledFromWrongThreadException:
      Only the original thread that created a view hierarchy can touch its views.
      Expected: android.ui     Calling: binder:NNNN_1
    ```
    即这两个方法**必须在 `android.ui`
    线程上调**，而我们是从 AMS 广播入口（binder 线程）调的。旁证：`updateFlexibleTaskCaptionVisible(task, true)`
    **不抛**（它内部应该是自己 post 到对线程的），调完 `mCaptionViewSurfaceShow` 读出来是
    `true`，但**截图上操作条仍然没显示**。
- ⚠️ 两处**反射诊断本身的坑**（都踩过、已修）：
    1. `Method.invoke` 抛的是 `InvocationTargetException`，它的 **message 恒为 null** ——只打 `e.message`
       会得到「抛了 InvocationTargetException: null」，真实原因全丢；必须剥到最内层 `cause`。
    2. `getDeclaredField` **只看本类**，而 `mCaptionViewSurfaceShow` 声明在父类 `FlexibleCaptionView` 上 ——字段查找要沿
       `superclass` 往上走。

**第 3 轮（把调用 post 到 `android.ui` 线程后）**：线程问题解决 ——用**视图自己的 `View.getHandler()`**
post 过去，`initCaptionView` / `initCaptionViewAndAttachToTask` / `showCaptionViewWithAnim` /
`updateFlexibleTaskCaptionVisible(true)` **四个调用全部无异常**， `mCaptionViewSurfaceShow = true`。

**但屏幕上仍然看不到操作条**。⚠️ 别把**左边缘那个小圆条**当成它 —— 那是 ColorOS 的**窗口边缘提示**（用户指出），跟操作条无关。所以状态是：视图建出来了、挂上了、`mCaptionViewSurfaceShow=true`、`getVisibility()=0`（VISIBLE），
**但它什么都没画出来**。

**第 4 轮（量它的几何）**：

```
getLeft()=0  getTop()=0  getWidth()=1272  getHeight()=2261   ← 撑成整窗，与任务同尺寸
getVisibility()=0（VISIBLE）
getFlexibleCaptionViewHandleButtonRegion() = Rect(0, 0 - 0, 0)   ← 手柄区是空的
父 0 = com.android.server.wm.FlexibleTaskCaptionView
父 1 = android.view.ViewRootImpl                                  ← 直接就是根视图
```

⇒ **`initCaptionViewAndAttachToTask(Task, Rect)` 的第二个参数就是操作条自己的尺寸**，而我们把**任务 bounds**
传了进去，于是它把自己撑成整窗、`handleButtonRegion` 也没算出来。这不只是「位置不对」，是**尺寸就该是顶部那条窄 rect**。

**第 5 轮（显式 rect + `relayout` + `mode`）**：**操作条露出来了**
—— 真机截图里窗口顶部中央出现那条灰色的圆角把手（`●●●` 在完整小窗里就是它）。⏹ 但它**点不动**：

- `handleButtonRegion` 仍然是 `Rect(0, 0 - 0, 0)` —— **触及区域没算出来**，所以那条把手画得出来、按下去没反应。
- ⚠️ **订正**：`initCaptionViewAndAttachToTask(Task, Rect)` 的第二个参数**不是**视图尺寸 ——传了 `(0,0 - 1272,157)` 之后
  `getWidth/Height` 仍是 `1272 × 2261`。这个视图的设计是**整窗覆盖、只在顶部画把手**，所以它的画面位置本来就是对的。
- 真正缺的是 **`FlexibleCaptionView.updateTouchRegion()`**（还有 `updateTouchRegionInner()`、
  `updateTouchableRegion(Region)`）—— 触及区域由它算。

**⏹ 收敛（2026-10-14 用户拍板）**：C 这条线**暂停**，`USE_FORGED_ANIMATION_ROUTE` 关回 `false`
（默认走 recents 正门 = 完整可用但无动画）。判断依据：

- 伪造态要通，就得**自己把框架「注册柔性任务」那一整套外围动作补齐**（操作条挂载顺序、圆角、触及区域、拖拽处理），而我们只能一样一样试出来，**每样隔一次整机重启**（已用 6 轮）；补完还有「点击被当拖动」「内容叠两份」两处没验。
- 正门给的是**完整可用**的窗口（圆角 / 操作条 / 菜单 / 输入都正常），代价只是**没有动画**（纯观感）。
- 而且伪造态依赖一堆内部状态位，ROM 一变全部重来。

**要接着做的人，从这里起手**（调用面已经完备，不用再为「调某个方法」编译）： `call` op 加了
`target=caption`（任务的操作条视图），且**视图方法会自动 post 到视图自己的线程** ——于是 `updateTouchRegion`
一类可以直接用广播调：

```bash
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op call --es target caption --es name updateTouchRegion --ei task_id N'"
```

剩下没验的两条：**触及区域补齐后能否点击 / 拖动**、**「内容叠两份」是不是也会随之消失**。

### 附十一：**A 方案被证伪**；真正的变量是「过渡有没有 root」（2026-10-14 真机，无重启）

**先说结论：A 方案（换调用方）不成立。** 用 debug 通道的 `zoom` op **从桌面进程**发起、target 是
**前台任务**时，日志仍然是：

```
onStartActivityFromRecents task:#116 … isFlexibleTask:false ,pendingLaunchZoom:true ,zoomLaunchFlags:2
startFreeFormFromRecent current pendingLaunchZoom will toggle FlexibleTask.
toggleToFlexibleTask task:#116
relayoutCaptionView: reason = startFreeFormFromRecent
No transition roots in (#26) … so abort           ← 还是 abort
```

即：附九把「桌面那次多一条 `skipCurrentOrAdjustChange … launchFrom=2`」归因于**调用方身份**，这个归因
**是错的**。换成桌面进程发起，`skipCurrentOrAdjustChange` 并没有出现。

**但同一轮拿到了关键的正结果**：把 `zoom` op 改对之后（见文末的坑），对**在最近任务总览里展示着的任务**
调用，产出的日志与桌面原生「浮窗」**逐行一致**，动画完整跑通：

```
onStartActivityFromRecents … taskForceMaximized:true ,isFlexibleTask:false ,pendingLaunchZoom:true ,zoomLaunchFlags:2
collectFlexibleTaskInfo: mScale=0.8016529, mCornerRadius=91, mLaunchBounds=Rect(126,319-1398,2580)
setVisibility(Token{…}) visible=true defer commitVisibility for transition collecting
skipCurrentOrAdjustChange task=#116 launchFrom=2
onTransitionReady (#31) … r=[0@Point(0, 0)]                     ← 有 root
playTransition → FlexibleTaskTransitionHandler → MultiSpringAnimation
startSpringFlexibleAnimation alpha=0.0 ,scale=0.0  →  alpha=1.0 ,scale=0.8016529
Transition animation finished (aborted=false) ✅
```

两张对照（同机型、同一天、同一个 op、只改目标任务的呈现状态）：

| 目标任务的呈现状态               | `taskForceMaximized` | 过渡 root                    | 结果                             |
| -------------------------------- | -------------------- | ---------------------------- | -------------------------------- |
| 前台、全屏（我们「就在它里面」） | `false`              | **无**                       | `No transition roots → abort` ❌ |
| 在最近任务总览里（卡片在屏上）   | `true`               | **有**（`r=[0@Point(0,0)]`） | `MultiSpringAnimation` ✅        |

⇒ **变量是「目标是不是已经在最前」**，不是「谁发起」。前台任务本来就 top，`m=TO_FRONT`
不构成任何变化，框架收集不到 root，这次过渡直接丢。这与附九第 3 条（前台确实没有过渡 root）完全一致；A 方案的赌注（「`launchFrom=2`
会把窗口模式变更延后进过渡，于是前台任务也有 root」）**没有兑现**。

**另有一处认知订正**：原生动画的形状是 **`scale 0 → 0.8016529`、`alpha 0 → 1` 的「弹入」**，而**不是**
「从全屏缩到小窗」。所以前台场景缺的不是「一个能缩小的起点」，而是**框架压根不打算在这个场景开过渡**。

**顺带修掉一个真坑（值得单记）**：`zoom` 第一版把 options 造成了 `ActivityOptions`，而真实签名是

```
android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int, Bundle)   ← AIDL 门面直接收 Bundle
```

服务端自己 `SafeActivityOptions.fromBundle(options, callingPid, callingUid)`。两个反射陷阱叠在一起：

1. `Reflect.method` 会退化成「**名字 + 参数个数**」匹配 → 把 `Bundle` 形参误配成 `ActivityOptions` 也能"找到"；
2. `Reflect.call` 内部 `runCatching` 把 `argument type mismatch` **吞掉** → 日志上只剩一句干干净净的
   `startActivityFromRecentsForFlexible(#116, flags=2) → null`，看起来像「调了但没反应」。

⇒ 结论：**反射调用要显式打 cause，别用会吞异常的封装**（改用 `getDeclaredMethod`
精确签名 + 自己 try/catch）。修好之后返回值是对的：`2`（`START_TASK_TO_FRONT` = 已受理）。

**这一轮还顺手把迭代成本砍下来了**：注入侧改 `ProbeChannel` 后**不必整机重启** —— `assembleDebug` + `install -r` +
`am force-stop com.android.launcher`，桌面进程重启即加载新 dex（实测一轮约 1 分钟；日志里能看到新的模块指纹）。只有
`system_server` 那半仍需重启。

**下一步的三个选项**（待拍板）：

- **D1 · 自己放动画（最被看好）**：走 recents 正门拿到**完整可用**的窗口（圆角 / 操作条 / 输入都正常），然后在任务自己的
  `SurfaceControl`
  上跑一段与原生同形的弹入（`alpha 0→1`、`scale 0→0.8016529`，圆角 91 已验证能设）。原生动画本就是弹入而非「全屏缩下去」，所以这条**在观感上能做到一致**，且**不依赖过渡 root、也不需要伪造
  `mScenario`**。
- **D2 · 先回桌面再收**：同一次手势里先把桌面 / 总览置顶造出 root，再 `zoom`。代价是视觉上变成「应用先走、小窗弹入」。
- **D3 · 接受无动画**：recents 正门，窗口完整，只付观感的代价。

### 附十二：伪造态**也**判死 —— 它压根没转换任务（2026-10-14 复测，无重启）

附十一把 A 方案判死后，伪造态（`switchState`）成了唯一「有动画」的路，于是复测一次。
**结论：它同样不成立，而且根因比「缺几个调用」更硬 —— 那个任务从来就不是小窗。**

**复测怎么做的**（全程无重启）：把 `MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE` 翻成 `true` （**宿主侧**改动 = 重编 + `kill`
宿主进程即可，几分钟内可来回翻），然后用 `ACTION_GESTURE` 广播直接触发侧键动作 —— 宿主那个接收器是
`NOT_EXPORTED`，**root（uid 0）发得进去**，不需要真的按侧键：

```bash
NOW=$(adb shell "cat /proc/uptime" | awk '{printf "%d", $1*1000}')
adb shell "su -c 'am broadcast -a com.lookie.opluskey.GESTURE -p com.oplus.gesture \
  --es key plus --es phase single --el sent_at $NOW'"
```

（`sent_at` 必须是新鲜的：`GestureReceiver` 2000ms 之外一律当积压丢弃，漏了它动作会静默不执行。）

**伪造态确实跑起来了，而且比历史记录里好**：

```
小窗指令：已喂 updateFlexibleTaskParams（含 HasCaption=true）
小窗指令：冒充柔性全屏态成功（mScenario=1 / mHasCaption=true / mFlexibleZoomState=2）
use memory rect to default:Rect(126, 319 - 1398, 2580)        ← 宿主下发的几何被吃住
switchState will startAdjustFlexibleTask task: 125
registerPointerEventListener success.                          ← 历史「点击被当拖动」的钩子这次装上了
updateFlexibleTaskCaptionVisible … isVisible=true
FlexibleCaptionView.initCaptionViewAndAttachToTask(…)          ← 框架自己把操作条挂上了
FTIWM[Task=125]: isAnimation Running
```

再加调试通道补的 `corner`（`setCornerRadius`）之后，屏幕上确实拿到了：**几何对 + 圆角 + 一条带 `🔍 ＋` 的操作条**（见
`build/shots/forged2.png`）。

**但它根本没变成小窗。** 决定性的一行是这个（`TaskOrganizerController`）：

```
onTaskInfoChanged taskid 125 … mWindowingMode=fullscreen        ← 自始至终是全屏，不是 100
```

`switchState` 只做了一套**视觉**动作（`OplusFlexibleInfoManager#setFlexiblePositionInfo` 一路把 surface 缩到
`scale=0.8014`、挪到小窗位置），**任务本身仍是 `fullscreen`**。也就是：**一个穿着小窗外衣的全屏任务。**

后果与历史记录那三条一一对上，且都有根因：

| 症状                                                                 | 根因                                                                                                                                                                                                                                                                                                                      |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `handleButtonRegion = Rect(0,0-0,0)`，`updateTouchRegion()` 也补不上 | 柔性任务注册那步没做（它才算把手区域）                                                                                                                                                                                                                                                                                    |
| **点一下就成拖动**，一拖小窗就没了                                   | 把手区域空 ⇒ 框架退化成「**整个窗口都是把手**」。真机日志：`isInFlexibleWindowHandleRegion is mini … rawX=703 rawY=1440` → `initMovingFlexibleTaskState` → `beginDrag` → 窗口被拖出屏幕顶部 → `exitFlexibleTaskWindowInnerLocked exitTo:6` → 任务退回全屏（`mScenario=0` / `mHasCaption=false` / `mFlexibleZoomState=0`） |
| 没圆角 / 没 ⋮ 菜单                                                   | 同源；圆角可以用 `setCornerRadius` 自己补，**但补了也改不了"它不是真小窗"**                                                                                                                                                                                                                                               |

**⇒ 三条路到此全部判死**（附二/附九/附十一/附十二）：

| 路                           | 原地 | 窗口完整        | 动画                           | 结论                                                                         |
| ---------------------------- | ---- | --------------- | ------------------------------ | ---------------------------------------------------------------------------- |
| recents 正门                 | ✓    | ✓               | ✗（前台**结构上**无过渡 root） | 可用，但永远没动画                                                           |
| A · 换调用方（桌面进程发起） | ✓    | ✓               | ✗                              | 与调用方身份无关，**附十一证伪**                                             |
| C · 伪造态 + `switchState`   | ✓    | ✗（不是真小窗） | ✓                              | **附十二证伪**：任务始终 `mWindowingMode=fullscreen`，点一下就被当拖动并甩掉 |

**⇒ 「前台应用 → 小窗 + 框架原生过渡动画」在这个 ROM 上拿不到。** 剩下的唯一办法是
**自己放那段动画**（附十一的 D1）：走稳定的 recents 正门拿到真小窗，再在它的 `SurfaceControl`
上复刻原生同形的弹入（`alpha 0→1`、`scale 0→0.8016529`，圆角 91 已验证能设）。原生动画本就是「弹入」而非「全屏缩下去」，所以观感可以对齐，且不依赖过渡 root、不需要伪造状态。

### 附十三：横屏应用的「收小窗」= 配置矩形被当成**横屏全屏任务**那套（2026-10-14 真机，PLK110）

**现象**（用户报「横屏的应用小窗尺寸还是大」）：把横屏应用收成小窗后，窗口**比屏幕还宽**。

**取证过程与硬数据**（设备 `ROTATION_90`，`tv.danmaku.bili` 任务 `#153` `…/UnitedBizDetailsActivity`，全屏
`mBounds=Rect(141,266-2381,1526)` = **2240 × 1260**）：

| #   | 证据                                                          | 值                                                                              |
| --- | ------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 1   | `dumpsys window` 里该窗口的请求尺寸                           | `Requested w=2240 h=1260`（= 横屏全屏任务那套）                                 |
| 2   | 触发 `zoom:mini` 后 `notifyZoomWindowShow` 的最终态           | `zoomRect = Rect(141,266 - 1457,1007)`，`scale=0.5875`                          |
| 3   | **截图像素级边缘检测**（判「`zoomRect` 到底是不是屏幕矩形」） | 左边界 `x=141`、下边界 `y=1006` ⇒ 与 `zoomRect` 的 `left`/`bottom` **双边吻合** |
| 4   | 由 1+2 算                                                     | `2240 × 0.5875 = 1316` = `zoomRect` 宽 ✓                                        |

⇒ **`zoomRect` 就是屏幕矩形**，横屏这次它是 **1316 × 741**，而屏宽只有 **1272** ——
**右侧溢出 185px**（约 14.5% 的窗口在屏幕外）。中途还有一帧 `Rect(119,730-1152,1311)` /
`scale=0.46116072`（1033×581，屏内），600ms 后被最终态覆盖。

**⚠️ 附带一条时序事实（会直接决定验收怎么写）**：收小窗那一下**先落一个过渡帧**，
**约 600ms 后**才定成最终几何。真机两次读数：

```
+0ms    Rect(119, 730 - 1152, 1311)   scale=0.46116072   ← 过渡帧，在屏幕内
+591ms  Rect(141, 266 - 1457, 1007)   scale=0.5875       ← 最终态，右侧溢出
```

按 350ms 去核会读到过渡帧，把「最后会溢出」的窗口报成「落在屏幕内」——模块的 `MINI_VERIFY_MS` 因此从 `350` 改成
`700`（2026-10-14）。 **判据教训**：小窗这类「框架自己会重新布局」的状态，验收时点必须晚于它落定，否则就是在测中间态。

**根因链**（离线读 `oplus-services.jar` → `classes3.dex`）：

```
FlexibleTaskController#startFreeFormFromRecent
  launchFlag == 32 ? getMiniFlexibleTaskInfo(task, record)                    ← 收起档
                   : collectFlexibleTaskInfo(intent, 1, dc, actInfo, 1)      ← 展开档（flags=2 走这条）
      └→ calcDefaultLaunchBounds(…, ActivityInfo.isFixedOrientationLandscape(actInfo), …)
          └→ OplusFlexibleTaskLayoutPolicy#calcLaunchBounds(outBounds, list, 横屏?, info, …)
              └→ getTaskRealSize(横屏?, info.getRatio())      ← 横屏/竖屏分档就在这里
```

`collectFlexibleTaskInfo` 又经 `fillFlexibleTaskInfo(Builder, Rect, Intent, ActivityInfo, 横屏?, DisplayContent)`
取几何，里面两处关键读法：
`intent.getStringExtra("calculate_bounds")`（`FlexibleTaskController.KEY_CALCULATE_BOUNDS`，等于 `"mini"` 时会改用
`OplusZoomDisplay#getDefaultFlexibleScale(scale, true)` 那条默认档）、以及 `OplusZoomDisplay#getDefaultFlexibleScale` /
`getScreenWidth` / `getFlexibleTaskFullScreenRatio`。

⇒ **配置矩形取的是「横屏全屏任务那套 2240×1260」（宽高比 1.778 = 16:9），再乘上一份缩放**：
`2240 × 0.5875 = 1316 > 1272`。而 0.5875 不是为 2240 宽配的 ——
`1272 × 0.5875 = 747`，它显然是**为竖屏那套配置矩形标定的缩放**。
**「配置矩形」与「缩放」这一对没配对**，才是溢出的直接原因。

框架本身有「横屏应用」专用的一整排常量（`com.oplus.zoomwindow.OplusZoomWindowManager`）：
`ZOOM_SCALE_FOR_LANDSCAPE_APP_DEFAULT` / `MINI_ZOOM_SCALE_FOR_LANDSCAPE_DEFAULT` / `ZOOM_LANDSCAPE_APP_RATIO` /
`ZOOM_LANDSCAPE_RATIO`。宿主侧反射可读的入口有 `getZoomBounds(boolean,int)` / `getZoomBoundsByTaskId(int,int)` /
`getCurrentZoomWindowState()`。⇒ 也就是说「横屏小窗该多大」系统自己有定义，我们这条 recents 路**没有对上**它。

**顺带修掉的那条假绿灯**：模块原先的验收判据是
`OplusAppInfo.appBounds.width() / Resources.getSystem().displayMetrics.widthPixels`，分母**不随旋转变**（竖屏恒 1272），横屏读到
`1.76` 却仍然落在「≥0.95 就算好」那一边 ⇒ 把「比屏幕还宽」报成了「几何正常」。现已改为读
`getCurrentZoomWindowState().zoomRect` 与 `Display#getRealSize` 比**是否落在屏幕内**（宿主侧，不用重启）。

**竖屏那条「未定论」已解**（2026-10-14 复测）：竖屏走的不是 recents 的「current is fullscreen task」那支，几何是正常的
`Rect(126,319-1146,2131)` = `1020×1812`、完全在屏内（`tv.danmaku.bili` `#157`
全屏在前台实测）。原先那个「竖屏也溢出」的怀疑来自 `aweme` 那次读数，没有再复现。
