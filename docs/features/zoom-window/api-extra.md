# 附十三之二：**「回桌面」会把设备拽回竖屏** ⇒ 横屏小窗按竖屏比例生成（2026-10-14 真机）

用户口径：「这个版本前台横屏小窗是从竖屏态的比例往横屏态的比例收的」。真机时序：

```
触发前      ROTATION_90                             ← 横屏应用全屏在前台
发 HOME 后  ROTATION_0                              ← 桌面是竖屏 ⇒ 设备被拽回去
            Rect(126,319-1146,2131) s=0.8016529     ← 小窗按**竖屏比例**生成（1020×1812）
```

⇒
**小窗几何是按「起那一刻的显示帧」算的**。launch 配方要先把目标挪出栈顶才起得来，而「挪出栈顶」在这台机上的唯一手段就是回桌面 ⇒ 必然经过竖屏 ⇒ 横屏应用拿到竖屏比例。

**想不走这条路，试过并否掉：**

- 给 intent 加 `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` 后**原地直接起** ⇒ 仍返回 **`3`**
  （`START_DELIVERED_TO_TOP`）。该 flag 是侧边栏的配方（见下），但它**不改「栈顶实例消化」**这条判定。

**侧边栏官方配方（`com.coloros.smartsidebar` 拉 APK 反编译，`PageRoutUtils`）：**

```java
// startNormalApp：intent = ACTION_MAIN + setPackage + setClassName + CATEGORY_LAUNCHER
b.putInt("extra_window_mode", 100);                 // ← 我们也有 ✓
b.putInt("android.activity.splashScreenStyle", 1);  // ← 我们没带
b.putInt("start_zoom_at_other_side", 13/14/31);     // ← 我们没带（13=左 14=右 31=第三档）
Bundle b = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle();   // 不是裸 Bundle()
OplusZoomWindowManager.getInstance().startZoomWindow(intent, b, userId, pkgName);
// flags：0x10200000 = NEW_TASK|RESET_TASK_IF_NEEDED（常规）
//        0x18000000 = NEW_TASK|MULTIPLE_TASK + putExtra("multi_instance_source", 5)（多实例档）
```

⚠️ **订正一条历史结论**：`notes.md` §6.2 曾记「侧边栏配方 = `extra_window_mode=100` +
`NEW_TASK|CLEAR_TASK`」—— 反编译看是 **`RESET_TASK_IF_NEEDED`（`0x00200000`）**，不是 `CLEAR_TASK`
（`0x00008000`）。`RESET_TASK_IF_NEEDED` 就是「点桌面图标」的语义，**不清后台栈**，比 `CLEAR_TASK`
安全得多。侧边栏能用它，是因为它**只对不在栈顶的应用**起小窗（`isAlreadyOpenedInTop` 时直接放弃）。

**⇒ 两条路各对一半，还没合起来**：launch 配方比例错（竖屏）但进屏；recents 正门比例对（横屏，因为不改方向）但几何溢出（见上）。**要的是「横屏帧 + 放得下的缩放」。**

⚠️ 复测作业纪律：这台机上「单击」动作会被用户**随时改**（本轮实测撞到过
`none`、`keyevent:24`，两次读数之间就被改掉）—— 借配置做实验必须用「一条命令块里 存-改-还原」，且还原值要现取、别用旧备份。

# 附十三之三：**「前台应用不支持小窗」的框架判据**（2026-10-14 真机，PLK110）

用户报「有些应用不支持小窗」时的第一手判据 —— 框架自己会把这行打出来，**不用猜**：

```
I/FlexibleTaskController: current activity unsupport flexible window. Intent {… cmp=com.heytap.market/.activity.MainActivity}
E/FlexibleTaskController: startFreeFormFromRecent invalid flexibleTaskInfo, pls check:
    FlexibleTaskInfo{mResizeMode=0, mRatio=0.0, mScale=0.85, mLaunchBounds=Rect(0, 0 - 0, 0), mFlexibleState=0, …}
```

对照**支持**的应用（同一次实测，`com.android.settings`）：

```
D/FlexibleTaskController: collectFlexibleTaskInfo:FlexibleTaskInfo{mResizeMode=1, mRatio=1.7777778,
    mScale=0.8016529, mLaunchBounds=Rect(126, 319 - 1398, 2580), mFlexibleState=0, …}
```

读法（**这三项是关键**）：

| 字段            | 不支持           | 支持（竖屏标准）          | 含义                                                               |
| --------------- | ---------------- | ------------------------- | ------------------------------------------------------------------ |
| `mResizeMode`   | `0`              | `1`                       | 0 = 不可调尺寸 —— 框架在 `startFreeFormFromRecent` 里**直接 bail** |
| `mRatio`        | `0.0`            | `1.7777778`               | 高/宽（`2261/1272`）—— `0.0` 表示没算出来                          |
| `mLaunchBounds` | `Rect(0,0-0,0)`  | `Rect(126,319-1398,2580)` | 空表示没有可用落点                                                 |
| `mScale`        | `0.85`（没意义） | `0.8016529`               | 与 §五 尺寸档表里展开档的 scale 一致                               |

⚠️ **`ActivityInfo.resizeMode == 0` **不能**单独当判据**（这也是为什么模块里那个开关默认关着）： **游戏**（真机
`com.tencent.tmgp.supercell.clashofclans`）也是 `resizeMode=0`，但它走 **`startZoomWindow`（launch）**那条能起成小窗 ——
`invalid flexibleTaskInfo` 那道闸只在
**`startFreeFormFromRecent`（recents 正门）**里。所以「不支持」要看**具体那条路**的返回。

⇒ 模块侧的处置（2026-10-14 用户口径）：**什么都不做，只弹一条 Toast**。判据取「服务端受理了（`2`）却没收出窗口」或「压根没受理」，见
`notes.md` §2.2。
**不许**再退「按包名另起一份实例」—— 那条会回桌面再起，用户看到的是「一按侧键就回桌面重开」。

# 附十三之四：`isSupportZoomMode` **不是纯查询** —— 判「不支持」时框架会自己弹 toast（2026-10-14 真机，PLK110）

用户报「选择应用里**一直弹**此应用不支持小窗，我还什么都没执行」。屏幕上的原话是：

> 此应用尚未支持浮窗

**这条 toast 不是模块发的**，是框架发的：

| 项             | 实测                                                                                                                        |
| -------------- | --------------------------------------------------------------------------------------------------------------------------- |
| toast 的投稿方 | `pkg = android, uid = 1000`（系统进程，`NotificationService` 的 `OplusNotificationLogger-->TOAST-->showToast` 行）          |
| 文案出处       | `reference/OplusLauncher.apk` 里有 `未支持浮窗`（本模块、`gesture.apk` 里都没有）—— 即 ColorOS 给用户按「浮窗」时的原话     |
| 触发者         | **我们自己那一串 `isSupportZoomMode` 调用**：判 `false` 的那一行后面 ~5ms 就跟着一条 toast，一条一条按 `false` 的顺序排出来 |

对照实验（同一台机、同一份 598 个应用清单，只差模块里有没有那次询问）：

```
预判开着  → 21:29:11 进页，21:29:11~14 每秒 0.7 个 toast（队列在排空）
预判关掉  → 21:30:42 进页、清单照常列出 598 个 → showToast 计数 0
```

**不是 `Bundle` 引起的**：把 `extra_window_mode=100` 换成**空 bundle**
再跑一遍，判据一模一样、**照样弹**（21:32:11.860 判 `false` → 21:32:11.865 弹一条）。

**框架服务里也没有可替代的静默判据**（`probe` 的 `dump` 实测）：

```
probe/dump com.oplus.zoomwindow.OplusZoomWindowManager filter='isSupport' → 2 个方法
  · isSupportZoomMode(String, int, String, Bundle) : boolean   ← 就是这个
  · isSupportZoomWindowMode() : boolean                        ← 设备级开关，不按包判
probe/dump com.android.server.wm.FlexibleWindowManagerService filter='Support' → 只剩
  isSupportResize(int) / isSupportScenario(int) —— 都不按包判
```

⇒ **结论**：这个 API 自带**用户可见副作用**（它就是 launcher 按「浮窗」时用的那一个），
**绝不能拿它当整表轮询的谓词**；只能用成「用户按下那一刻问一次」。模块侧最终落法见 `notes.md`
§十（勾选态或选中项每变一次问一次，两处合起来才盖住每一条会被写成 `window:<包名>` 的路径）。

# 附十三之五：「重启服务」怎么把**页面自己**拉回来（2026-10-14 真机，PLK110）

需求是「点了重启服务，UI 进程也重启、页面重进一遍」。三条路逐一试过，只有第三条通：

**一、杀掉 `:ui` 进程，指望系统重建那个 Activity —— 不行。**

```
23:35:10.252 Process com.oplus.gesture.ui (pid 23276) has died: fg  TOP
23:35:10.253 ActivityTaskManager: Force removing ActivityRecord{...ActionButtonSettingsActivity t271}:
             app died, no saved state
```

进程死了，那条 `ActivityRecord` 是**直接抹掉**的（「没有 saved state」），任务栈塌回下面那个应用（实测前台落到桌面 /
KernelSU 管理器）。**不会有任何东西把页面重新拉起来**，用户被丢在外面。

**二、闹钟挂 `PendingIntent`（`getActivity`）预约自己回来 —— 被后台启动策略拦掉。**

```
23:35:14.784 E/ActivityTaskManager: Background activity launch blocked! goo.gle/android-bal
  [callingPackage: com.oplus.gesture; callingUid: 10277; callingPid: -1;
   isPendingIntent: true; originatingPendingIntent: PendingIntentRecord{363dd4d ... startActivity};
   realCallingUid: 1000; realCallingPid: 4360;              ← 真发起方是系统（闹钟投递）
   balAllowedByPiCreator: BSP.NONE;                         ← 我们（PI 的创建方）没开这档许可
   resultIfPiCreatorAllowsBal: BAL_ALLOW_PERMISSION;         ← 我们一开就是「按权限放行」
   balAllowedByPiSender: BSP.NONE; resultIfPiSenderAllowsBal: BAL_BLOCK;
   realCallerStartMode: MODE_BACKGROUND_ACTIVITY_START_DENIED;]
```

两处「谁允许」都是空的，于是拦掉。注意 `resultIfPiCreatorAllowsBal` 写的是 `BAL_ALLOW_PERMISSION`
—— 我方 uid 本来就**有**后台启动权限（见下），只是这条 PendingIntent 路线还额外要求「创建方声明允许」，而闹钟这一侧我们控制不了发起方的档位。**别再走这条路。**

**三、交给宿主**主**进程去 `startActivity` —— 通。**

本包拿着后台启动的正门权限（`dumpsys package com.oplus.gesture` 实测）：

```
android.permission.START_ACTIVITIES_FROM_BACKGROUND: granted=true
android.permission.SYSTEM_ALERT_WINDOW: granted=true
android.permission.SCHEDULE_EXACT_ALARM: granted=true
```

宿主**主进程自己**调 `startActivity`
是「app 自己的后台启动」，按权限放行；而它本来就活着（收手势的接收器、设置页那句「在不在」都在它身上，「重启服务」的前置条件也正是它在跑）。落法：设置页发一条
`ACTION_PAGE_RELAUNCH` → 宿主主进程等 900ms（**必须等页面进程死透**，否则 `singleInstance`
只会把旧进程那一个拿到前台，一行新代码都没换）→ 拉起设置页 → 再过 500ms 自杀重来。实测一轮（23:40:4x）：

```
+1.0s  :ui = 31823（新进程，页面已回来）      宿主主进程 = 30326（还是旧的）
+2.0s  :ui = 31823（1s）                     宿主主进程 = 31918（新，已重装手势 Hook）
```

全程没有一条 `Background activity launch blocked`（对照：走闹钟那条路每次都有一条）。
