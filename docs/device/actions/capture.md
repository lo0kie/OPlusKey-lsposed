# 截图、录屏与录音

## 三十、录屏只有 `MainActivity` 一个入口管用（实测，2026-10-21，PLK110 / ColorOS 17.0.0）

`com.oplus.screenrecorder` 对外有三个入口，逐个试过：

| 入口 | 需要 | 实测 |
| --- | --- | --- |
| `.MainActivity`（MAIN） | 无 | **起来就录**：状态栏出红点、通知栏「屏幕录制 正在录屏…」、`dumpsys media_projection` 有会话 |
| `.seedlingcard.JumpActivity` + `oplus.intent.action.SCREEN_RECORDER_JUMP` | `com.oplus.permission.safe.ASSISTANT`（宿主已授予） | **空转**：Activity 真的起来了（`START`/`ActivityRecord` 都在），但既不出界面也不开录，`media_projection` 仍是 `null` |
| `.floatwindow.services.CommandRecorderService` + `oplus.intent.action.COMMAND_RECORDER_SERVICE` | `com.oplus.permission.safe.PICTURE` | 宿主**没申请**这一条，起必被拦 |

落点登记在 `RomProfile` 的 `NAME_SCREEN_RECORD`，用的是第一条。`action` 留空、只按组件起。

**判据**：按一下之后 `dumpsys media_projection` 有会话 / 状态栏出红点 = 起来了；
只有 `START` 日志、`media_projection` 为 `null` = 起成了 JumpActivity（空转）。

**停录**：发广播 `com.oplus.screenrecorder.ACTION_STOP_SCREEN_RECORDING`（带包名）即可 ——
真机验过：发之前 `dumpsys media_projection` 是 `Projection:`，发之后立刻变 `null`，文件正常收尾。
所以这一档做成**开关**：起过一次、没发过停止 ⇒ 下一次按是关（记账在宿主进程内，见
`AppLauncher.toggledOn`；宿主没有 `MANAGE_MEDIA_PROJECTION`，查不到系统那边的会话）。

**别用 `force-stop` 停**：那是硬杀，正在写的文件会留下半截。

## 三十五、截图：宿主注入无效，得由注入侧（system_server）注入；`InputManager.getInstance()` 在那侧返回 null（实测，2026-10-25，3B15BR005FN00000 / ColorOS）

**结论**

| 谁注入 `KEYCODE_SYSRQ` | 结果 |
|---|---|
| 宿主进程（普通 app uid） | `injectInputEvent` 返回成功，屏幕无反应 |
| 注入侧（`system_server`，uid 1000） | 正常截图 |

所以「截图」这个内置动作**不能让宿主自己注入**，也不能退回 `su` 跑 `service call color_screenshot`
（内置动作不该为截一张图要 root）。做法是宿主发一条
`ModuleWire.ACTION_INJECT_KEY`（带键码），注入侧在 `system_server` 里注入 —— 见
`host/action/ActionRunner.requestSystemInject` 与 `inject/KeyInject`。

**判据**

- `adb shell input keyevent 120` 能截出图（`/sdcard/Pictures/Screenshots/` 多一张）⇒ SYSRQ 这个码
  ColorOS 照样处理，问题不在键码；
- 宿主注入时日志只有「截图：注入…」一行、**没有任何「被拒」**——事件发出去了但不被处理；
- 注入侧的注入模式要 `INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH`（**`2`**），与 `input` 命令一致；
  宿主那份用的是 `0`（ASYNC）。三个值是 `0` = ASYNC、`1` = WAIT_FOR_RESULT、`2` = WAIT_FOR_FINISH ——
  这里**曾经写成 `1`**（WAIT_FOR_RESULT，等「分发到目标窗口」）：截图这类被 policy 吞掉的事件没有
  窗口消费，必然拿到 `INPUT_EVENT_INJECTION_FAILED`。
- `injectInputEvent` 的返回值是 `InputEventInjectionResult`：**`0` = 成功**、`1` = 失败、
  `-1` = 无权限。早先判据写反了（把 `0` 当失败），于是真正失败的那一次（返回 `1`）一行日志都不打。

**取 `InputManager` 的入口（这条 ROM 上的实测）**

`InputManager.getInstance()` 在 `system_server` 里**返回 null** —— `Reflect.callStatic` 拿到 null、
不抛异常，于是整条注入静默失效（日志「`InputManager.getInstance()` 拿不到实例」）。
可用的是 **`InputManagerGlobal.getInstance()`**（Android 14 起静态方法搬到了这里，
`injectInputEvent` 同名同签名）。

代码里只试**两条**：`InputManagerGlobal.getInstance()` → `InputManager.getInstance()`，第一个非 null
的记住（只在第一次注入时试，之后零反射）。留两条而不是一条的原因：Android 13 上没有
`InputManagerGlobal`，14 之后 `InputManager.getInstance()` 又变成了包装（本机直接返回 null）——
单写任一条都会让一半机器静默失效。命中时打一行「注入侧注入：入口用 …」，日志里看得出走哪条
（本机是第一条）。

## 三十六、个别机型（PLG110）注入 `KEYCODE_SYSRQ` 不截，得改走 `ScreenshotHelper`（实测，2026-10-28，PLG110 / ColorOS）

三十三节那条「注入侧注入正常截图」**不是每台机器都成立**。同一份代码在 PLG110 上的日志：

```
[host]   截图：请注入侧注入 KEYCODE_SYSRQ
[system] 按键（…StrategyActionButtonKeyLaunchApp#actionInterceptKeyBeforeQueueing）：合成事件（deviceId=-1 keyCode=120），放行给系统   ← DOWN
[system] 同上                                                                                                                  ← UP
```

也就是说：`injectInputEvent` **返回成功**（`0`）、事件**确实进了** policy 的
`interceptKeyBeforeQueueing`、我们这边也按「合成事件」放行了两次 —— 屏幕就是不截。
不是参数问题：补 `FLAG_FROM_SYSTEM`、换注入模式都试过，都不行；而 `input keyevent 120` 在这台机器上
照样能截。所以这是该 ROM 对「非 shell 注入」的口径，主路径不必为它改（多数机器上注入是好的）。

**能用的那条：直接调系统的 `ScreenshotHelper`**

`system_server` 自己截图用的就是它，与按键完全无关。这台机器上的形状（反射打出来的签名）：

| 项 | 值 |
| --- | --- |
| 构造器 | `ScreenshotHelper(Context)` |
| 方法 | `takeScreenshot(int, Handler, Consumer<Uri>)` 与 `takeScreenshot(ScreenshotRequest, Handler, Consumer<Uri>)` —— **3 参** |
| 常量 | 只有 `SCREENSHOT_MSG_PROCESS_COMPLETE=2`、`SCREENSHOT_MSG_URI=1`、`SCREENSHOT_TIMEOUT_MS=-1` —— **没有** `SCREENSHOT_GLOBAL_ACTION` |
| 有效调用 | `takeScreenshot(1, Handler(mainLooper), Consumer)` ⇒ 截图成功 |

要点：

- **不要按 AOSP 14 的 5 参签名找**（那份多 `hasStatus` / `hasNav`），这台机器上不存在；
- `type` 传 `1` 有效，但常量表里没有 `SCREENSHOT_GLOBAL_ACTION` 这个名字 —— 只能写死 `1`，
  并在日志里把整张常量表打出来，换 ROM 时好对；
- `Context` 用注入侧借来的那份 **Application Context 就够**（这条路只要 binder 与 Looper，
  不碰 `ContentResolver`）；
- `Consumer` 用动态代理实现，`accept` 那一行是「系统到底截没截」的最终判据（这台机器上发起成功
  但回调没来，截图仍然出了 —— 所以**不能**用「没回调」判失败）。

实现见 `inject/ScreenshotViaHelper`：截图先试它，没成再退回注入（两条都在日志里留判据）。

**纪律**

- 注入**不要放在 `system_server` 主线程上**：`WAIT_FOR_FINISH` 会等事件被处理完，那一下等于把整机挂住；
- 全程不抛：调用点在 AMS 的广播入口，漏一个异常出去就是 `system_server` 崩。

## 17.7 录音的三态：切换在意图里、停止只有 AIDL（2026-11-08，PLK110 / ColorOS 17）

`oplus:recording` 原来只是一条意图（`oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON` →
`TransparentActivity`）。要拆出「只开始 / 只结束」，先得回答「谁能让它停」。反编译
`com.coloros.soundrecorder`（`/data/app/…/com.coloros.soundrecorder_17008008.apk`，4 个 dex）逐条核过：

| 三态 | 通道 | 判据 |
| --- | --- | --- |
| 切换 | **同一条意图** | 它自己就是开关：`TransparentActivity.handleFromCubeOrLockScreenBefore` → `saveRecordInfo(true)`，条件 `K()`（`RecorderService != null`）+ `manager.s.c()`（状态 1/2）+ `rk.a.z()` 成立时调 `d6.l.E(vm, 6)` = `RecorderViewModel.i0(6)` → `RecorderService.q(…, true, 6)` 停录保存 |
| 只结束 | `RecordStateService` 的 AIDL | 见下表；**必须先查状态**，没在录时发它会弹出一张录音流体云（见下面那条 ⚠️） |
| 只开始 | **没有独立入口** | 上面那条意图在录音中会停，所以只能先查状态再决定发不发 |

AIDL 通道（`ISoundRecordInterface`，事务号逐条对着 `$Stub$Proxy` 核的，不是按 `onTransact` 的块序猜的）：

```
服务   com.coloros.soundrecorder/oplus.multimedia.soundrecorder.RecordStateService
       清单: exported=true, android:permission="oplus.permission.OPLUS_COMPONENT_SAFE"
描述符 oplus.multimedia.soundrecorder.ISoundRecordInterface
1 queryStatus   回包 = writeNoException + 存在位 + 4 个 int
                (currentRecordState / otherState / errorCode / extField)
                1 正在录、2 已暂停，其余（含 -1「还没上报过」）按空闲 —— 与 `manager.s.c()` 同口径
2 registerCallback / 3 unRegisterCallback
4 resumeRecord / 5 pauseRecord
6 stopRecord    → RecorderService.t() + q("", "", TRUE, 1)
```

- 每个事务在 `SoundRecordBinder` 里都先 `enforceCallingPermission("oplus.permission.OPLUS_COMPONENT_SAFE")`；
  宿主 `dumpsys package com.oplus.gesture` 里那条是 `granted=true`（与它能起 `TransparentActivity`
  同一个权限），所以这条通道在宿主进程直接走得通，不用注入侧。
- ⚠️ `stopRecord` 内部 `checkAndWaitBindRecordService()` 是 `mCondition.await()` —— **没有超时**，
  而跨进程调用本身没有超时参数。所以调用方必须把它放到独立线程上发、自己等一个上限
  （模块侧 `bridge/host/RecorderStateBridge`：等连接与等事务各一道 `TIMEOUT_MS`）。
- **别拿通知那条当命令**：`RecorderService.onStartCommand` 认 `oplus.intent.action.SAVE_CHANGED_ACTION`
  （→ `manager.n.b` → `RecorderViewModel.i0(5|10)` 停录保存），但它先要 `notification_type`
  与当前通知类型**逐值相等**，还有防连点守卫 —— 那是它自己通知栏的按钮，不是外部入口。
- ⚠️ **没在录的时候发 `stopRecord`，真机会当场弹出一张「录音」流体云**（2026-11-08，PLK110，模块第一版）：
  `stopRecord` 内部先 `checkAndWaitBindRecordService()`，那一步会**把 `RecorderService` 拉起来**
  （它的 `onCreate` 建 recorder 内核、`TimeSetUtils` 与收尾那套），于是**什么都没录也弹卡**。
  而 `queryStatus` 只 bind `RecordStateService`（`onCreate` 只 `registerBroadCastReceiver`），是惰性的
  —— 所以三档都得先查状态，「只结束」也不例外：没在录就什么都不做。
- 三条死路：`RecorderStateProvider`（`content://com.multimedia.record.state.provider`，
  `call("method_record_state")` 回 `record_state`）的 `checkPackageAuth` **只放行**
  `com.heytap.speechassist` / `com.oplus.ai.assistant`，别的包直接 `throw RuntimeException("package name not match")`；
  `KeyMagicMessengerService` 的 `what=1/2` 不碰录音机（17.5 已记）；全 dex 跟录音有关的 action
  只有三条 `START_*`（`_FROM_CUBE_BUTTON` / `_FROM_AI` / `_FROM_BREENO`），**没有任何停止广播**。

模块侧：`RomProfile.NAME_RECORDING` / `_START` / `_STOP` 三档 + `RomProfile.AidlControl`
（只记描述符与事务号，**不引用目标的任何类型**），分派在 `host/action/AppLauncher` 的
`runAidlFeature` / `aidlDecision`。
界面照录屏那一套：`ActionCatalog.RECORD_TILE`（`ai:recording`，永不写进配置）+ `RECORDING_MODES`。
