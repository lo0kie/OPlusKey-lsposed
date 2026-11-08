# 系统级动作入口：电源菜单、场景表与 oplus 动作族

## 十七、电源菜单的直接入口（`services.jar` 反编译，2026-10-13，PLK110）

动作 `power:menu` 走这条，全程在 system_server 内本机调用：

```
ServiceManager.getService("window")  →  WindowManagerService 实例（不是代理）
  field mPolicy : WindowManagerPolicy  →  PhoneWindowManager
    showGlobalActions()                →  mHandler.sendEmptyMessage(10)
      showGlobalActionsInternal()      →  mGlobalActions ??= mGlobalActionsFactory.get()
                                         mGlobalActions.showDialog(keyguardShowing,
                                                                  isDeviceProvisioned())
        LegacyGlobalActions.handleShow() →  mDialog.show()
```

- **不能调 `WindowManagerService.showGlobalActions()`**：它第一句是
  `checkCallingPermission("android.permission.INTERNAL_SYSTEM_WINDOW")`，而 `ContextImpl.checkCallingPermission` 只处理
  `pid != myPid()` 的情况、**同进程调用一律返回 DENIED** ——在 system_server 里本机调必抛
  `SecurityException`。必须往下钻一层到 `mPolicy`，那层没有权限检查。
- **ColorOS 没有自己的电源菜单类**：`oplus-services.jar` 搜 `GlobalActions` 0 命中；`services.jar` 3 个（`GlobalActions`
  / `GlobalActionsProvider` / `LegacyGlobalActions`）。弹窗由 system_server 自渲染（`handleShow` 里直接
  `mDialog.show()`），不转发给 SystemUI。
- 与「长按电源键」的差别：那条要等框架的长按超时（`interceptPowerKeyDown` →
  `powerLongPress`），本机调用是立即的，也不经过输入子系统。

## 17.2 系统侧键场景表：13 个场景的调用目标（真机 `content query`，2026-10-13）

`content://uri.gesturemanagerprovider/action_button`（宿主 `com.oplus.gesture` 的 `ActionButtonProvider`）。它要
`oppo.permission.OPPO_COMPONENT_SAFE`，**shell 身份读不到**（`SecurityException`），用 `su -c 'content query …'`：

| scene_name     | scene_owner                    | click_action_info                                        | type    |
| -------------- | ------------------------------ | -------------------------------------------------------- | ------- |
| ring_mode      | system_server                  | `getRingerMode`                                          | method  |
| flash_light    | com.android.systemui           | `…notification.keymagicservice.KeyFlashlightService`     | service |
| screen_shot    | com.oplus.screenshot           | `showScreenShotFluidCloud`                               | method  |
| no_disturb     | com.android.systemui           | `…notification.keymagicservice.KeyDndService`            | service |
| recording      | com.coloros.soundrecorder      | `com.soundrecorder.common.card.KeyMagicMessengerService` | service |
| camera         | com.oplus.camera               | `com.oplus.camera.feature.keymagic.KeyMagicService`      | service |
| translate      | com.coloros.translate          | `com.coloros.translate.ui.cube.KeyMessengerService`      | service |
| quick_function | com.oplus.gesture              | `getClickActionInfo`                                     | method  |
| start_app      | com.oplus.gesture              | `getClickActionInfo`                                     | method  |
| flash_memory   | com.coloros.colordirectservice | `…flashnotes.business.service.DataCollectService`        | service |
| controlcenter  | com.android.systemui           | `…qs.magickey.QsMagicKeyService`                         | service |
| ocrscanner     | com.coloros.ocrscanner         | `com.oplus.scanner.ui.keymagic.KeyMagicService`          | service |
| test           | com.oplus.exserviceui          | `com.oplusos.accessibilityhelper.KeyMessengerService`    | service |

（`long_press_action_info` / `second_menu_*` 另有值，多数与 click 指向同一个服务；完整行见真机。）

- **`type=service` 就是「起一个服务」**：模块的 `core/model/RomProfile` 只抄其中四行，不多猜。
- **三个目标服务都带签名级权限**（`DataCollectService` → `com.oplus.permission.safe.SECURITY`； `InvokeEntranceService`
  → `oppo.permission.OPPO_COMPONENT_SAFE`），而**宿主 `com.oplus.gesture` 本人就持有** 这些权限（另有
  `WRITE_SECURE_SETTINGS` / `ACCESS_NOTIFICATION_POLICY`(granted) /
  `MODIFY_AUDIO_SETTINGS`）⇒ 从宿主进程直接起即可，不需要 root，也不必绕 system_server。
- **四道权限闸门的真机核对**（`dumpsys package`，2026-10-13，PLK110；四个服务 `exported=true`）：宿主四项全
  `granted=true` —— `oppo.permission.OPPO_COMPONENT_SAFE`（`InvokeEntranceService`）、
  `com.oplus.permission.KEY_MAGIC_SERVICE`（`KeyDndService`，`signature|privileged`，由 SystemUI 定义）、
  `com.oplus.permission.safe.SECURITY`（`DataCollectService`）、`com.oplus.permission.safe.ASSISTANT`
  （`KeyMagicMessengerService`）。**只证明「起得到」，不证明「起了会做那件事」。**
- **诊断坑**：没有 `<intent-filter>` 的服务（`KeyDndService` 就是）**不会出现在** `dumpsys package` 的 Service Resolver
  Table 里 —— 别据此判定「服务不存在」，要去 APK 清单里查。
- **起法按目标清单声明分两种**：声明了 `foregroundServiceType=special_use` 的（`FLASH_MEMORY`，见 17.3）先
  `startForegroundService`，那是它给自己定的起法；没声明的先 `startService` —— 对方不调 `startForeground`
  时，用前台方式起会让**它的进程**抛 `ForegroundServiceDidNotStartInTimeException`。两条都失败才算失败。
- **intent 形式**：有 action 的用
  `Intent(action).setPackage(owner)`（系统自己的写法，走清单 filter）；场景表里只有组件名（无 action）的（`no_disturb` /
  `recording`）才用显式组件 —— 这两个服务 `exported=true`
  且无 filter（`KeyDndService`）或 filter 与场景表 action 不一致（`KeyMagicMessengerService` 的 action 是
  `com.oplus.soundrecorder.keyMagicMessengerService`，场景表没写），显式组件更忠于场景表。

## 17.3 小布识屏：两条入口最终都落在同一个服务上

13 个侧键场景里没有它。入口是
`com.coloros.colordirectservice/.InvokeEntranceService`（`ColorDirectService.apk`）。清单声明：`exported=true`、`android:permission=oppo.permission.OPPO_COMPONENT_SAFE`、
`foregroundServiceType=special_use` +
`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="special_use_for_start_identification">`，intent-filter 共 14 条 action。系统自己用的是其中两条：

- **侧边栏** —— `com.coloros.smartsidebar` 的 `BreenoScreenIdentifyTool.handle()`：
  `Intent("oplus.intent.action.DIRECT_SIDEBAR_SERVICE").setPackage(getValidPkg())`， `getValidPkg()` 直接返回字面量
  `"com.coloros.colordirectservice"`；extra 为
  `extra_entrance_function=full_screen_ocr`、`triggered_app=<自己包名>`，另带可选点击坐标 `first_point_down_x` /
  `first_point_down_y`；最后 `startForegroundService`。
- **导航栏长按** —— `com.android.systemui` 的 `OplusOcrScreenService.getServiceIntent()`：action 用
  `com.coloros.directui.InvokeEntranceService`，同样 `setPackage("com.coloros.colordirectservice")`，extra 多一个
  `skip_gesture_check=true` 和 `StartUpType`（2=手势条 / 1=虚拟键 / 0=其它），但起法是
  `bindServiceAsUser`（`BIND_AUTO_CREATE|BIND_ALMOST_PERCEPTIBLE`）+ 走
  `com.oplus.infocollection.collection.IEntranceServiceInterface` 的 AIDL。**宿主拿不到这个接口，不采用。**

结论：之前从别处推测的 `DIRECT_SIDEBAR_SERVICE` + `extra_entrance_function=full_screen_ocr`
是对的，两条 action 只是同一 filter 里的兄弟项。据此改两处：

- **起法**：`special_use` 说明它就是按前台服务设计的 ⇒ 有 action 的目标改用
  `Intent(action).setPackage(owner)`（系统自己的写法），并按清单声明的类型决定先试
  `startForegroundService`（`FLASH_MEMORY` 的 `DataCollectService` 同样声明了 special-use）。

顺带记一条死路：`AIUNIT_OCR_SERVICE` / `OCR_SERVICE`
看着更对口，但**不在这条 filter 里**，只是该 dex 里出现过的字符串（属它发给别人的）；侧边栏自身也没有独立识屏服务。

## 17.4 场景表 `type=service` 的两种调用方式（2026-10-13，PLK110，实测教训）

场景表**只有**
`scene_name / scene_owner / click_action_info / click_action_type / long_press_* / second_menu_* / long_press_has_own_vibrator`
这些列 —— **没有 extras，也区分不出「起服务」还是「bind 后发 Message」**。光抄表不够，得再反编译目标服务看入口：

- **Messenger 绑定型**（`KeyBaseService` 族：`KeyDndService` / `KeyFlashlightService`； `KeyMagicMessengerService`；估计
  `KeyMessengerService` / `KeyMagicService` 同理）—— **没有 `onStartCommand`**，入口是 `onBind` 返回的
  `Messenger`。协议（`KeyBaseService$messenger$1.handleMessage`）： `what=1` 单击 →
  `handleActionButtonClick()`；`what=2` 长按 → `handleActionButtonLongPress()`；结果经 `message.replyTo.send(...)`
  回给调用方 ⇒ **调用方必须设 `replyTo`，否则服务端空指针**。 `startService`
  对它们是空操作：实测（2026-10-13）`startService` / `startForegroundService`
  都被 AMS 受理、logcat 里目标进程零动静，`settings get global zen_mode` 纹丝不动 —— 连 `su` 下
  `am start-foreground-service` 也一样。这就是「新加的动作全都不触发」的根因。
- **带 extras 的 startService**（`DataCollectService`，一键闪记）—— 有 `onStartCommand`，但它读
  `triggerType`（`TriggerType.MAGIC_KEY` = **1**；UNKNOWN=0 / THREE_FINGERS_UP=2 / CUI=3 / SCREENSHOT=4 / VOICE_RECORD=5
  / SCREENSHOT_NORMAL=6 / SCREENSHOT_LONG=7 / SCREENSHOT_AREA=8 / AUTO_PAYMENT=11 / AUTO_COLLECT=12 /
  AUTO_PAYMENT_NOTIFICATION=14）和 `longPressEventType`（0=单击 / 1=语音 / 2=长按）。不带 `triggerType`
  落到 UNKNOWN 分支，记 `trigger type error`
  后什么都不做。另有前置：AI 内存（闪记）已装、`USER_SETUP_COMPLETE`、开关未关，否则 `stopSelf`。

模块侧结论：`core/model/RomProfile.BuiltIn` 增加 `message`（ Messenger 型发什么码）， `host/action/AppLauncher`
里 bind → `Messenger.send` → 立刻解绑；带 extras 的照常 start。

## 17.5 三个 `oplus:` 动作的真实入口（2026-10-14 复核，PLK110）

**结论：模块侧整条链路是通的，三条命令先前全失效的根因在「取了单击那档」。**
这一族里**单击档经常只回一句提示，「长按」那档才真正干活**。

证据链（`modules.log`，2026-10-14 00:34–00:36）：三条都执行到了目标、都无效果 ——

```
侧键·单击 → oplus:no_disturb（宿主进程执行） → Messenger.send(what=1)(com.android.systemui/KeyDndService)
侧键·单击 → oplus:recording（宿主进程执行）  → Messenger.send(what=1)(com.coloros.soundrecorder/KeyMagicMessengerService)
侧键·单击 → oplus:flash_memory（宿主进程执行）→ startForegroundService(com.coloros.colordirectservice/DataCollectService)
```

场景表实测全列（`su -c 'content query --uri content://uri.gesturemanagerprovider/action_button'`）：

| scene        | click_action_info / type                         | long_press_action_info / type                                      |
| ------------ | ------------------------------------------------ | ------------------------------------------------------------------ |
| no_disturb   | `…keymagicservice.KeyDndService` / service       | **同上** / service                                                 |
| recording    | `…card.KeyMagicMessengerService` / service       | **`oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON` / activity** |
| flash_memory | `…business.service.DataCollectService` / service | 同上 / service（另有 `second_menu_activity_info=tips://web/…`）    |

另有一列 `long_press_has_own_vibrator`（这三个场景都是 `0`）。

- **录音**：`KeyMagicMessengerService`（`NewSoundRecorder.apk`）`<init>` 里
  `mMessenger = Messenger(Handler(mainLooper) { … })`，`onBind` 返回 `mMessenger.binder`； `handleMessage`：`what==1`
  → 组 `side_text` / `app_name` / `fluid_cloud_text` / `icon_res_id` 回给 `msg.replyTo` + 拿一条 wakelock；`what==2`
  → 回 `startResult=reply`。 **两条分支都不碰录音机** ⇒ 对它发 what=1 是死路（`side_text`
  取的就是「长按开始录音」）。真入口是长按档那条界面：清单里
  `<activity name=oplus.multimedia.soundrecorder.slidebar.TransparentActivity permission=oplus.permission.OPLUS_COMPONENT_SAFE exported=True>`，filter 里有
  `oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON`；真机
  `cmd package resolve-activity -a oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON`
  也落它（透明界面，进去就起 / 停录音）。
- **免打扰**：SystemUI 清单声明
  `<service name=com.oplus.systemui.statusbar.notification.keymagicservice.KeyDndService permission=com.oplus.permission.KEY_MAGIC_SERVICE exported=True>`；实现是
  `KeyBaseService` → `KeyBaseController`，`messenger` 的 `handleMessage` 把 `what` 分派给 `handleActionButtonClick()` /
  `handleActionButtonLongPress()`。`KeyDndController` 里 **只有 `handleActionButtonLongPress` 读
  `ZenModeController.getZen()` 并 `setZen(...)`** （外加 `dissmissNotificationCenter`、息屏时延迟 500ms 的 `openDnd`）；
  `handleActionButtonClick` 只判 `isOnKeyGuard()` / `isScreenOn()`、全程不碰 zen
  ⇒ 发 what=1 的表现就是「什么都不发生」。
- ⚠️ **别拿「jadx 摘要里没有 `onBind` / 没有无参构造器」判它「不是 Service」**： `KeyDndService`
  的类摘要只列到构造器，`KeyBaseService$messenger$1.handleMessage` 与 `KeyDndController.handleActionButtonClick`
  jadx 都只给 "no decompiled body"。实测 `bindService` 能连上（`onServiceConnected` 到达且 `send`
  成功）—— 那是**载荷**问题不是**通道**问题。这两条的结论来自本地 dex 指令级 disassembly（`.temp/rea/dexasm.py`，直接读
  `code_item`）。
- **一键闪记**：**表里的 `scene_owner` 过期了 —— `com.coloros.colordirectservice` 那份是死的。**
  实测（01:00:28，模块调它）：进程起来了、`onStartCommand` 也进了，但第一道闸门就把它挡住 ——
  `ColorDirectServiceLog: DataCollectService: Gleaner has Installed or intent is null, stop service`，紧跟
  `onDestroy`，**连 extras 都没读**。对应代码就是 `if (c.c() || intent == null) { stopSelf(); return 2; }`
  —— 它认「Gleaner 已安装 ⇒ 我不干」。真入口是新包
  `com.oplus.gleanerservice`（`/product/app/Gleaner/Gleaner.apk`）里的同名服务：

    ```
    <service name=com.oplus.gleanerservice.flashnotes.business.service.DataCollectService
             permission=com.oplus.permission.safe.SECURITY exported=True
             foregroundServiceType=type=hex:1073741824>          <!-- SPECIAL_USE -->
      <property name=android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE value=special_use_for_ai_memory>
      <intent-filter><action name=oplus.gleanerservice.intent.action.COLLECT_DATA>
    ```

    它的 `onStartCommand` **没有「Gleaner 已安装就退出」那道闸门**，只读 `triggerType` （`MAGIC_KEY`=1 →
    `CollectTaskManager.j(...)` 真正开始采集；`UNKNOWN` / `SCREENSHOT` → 记 `trigger type error`），另有一道
    `USER_SETUP_COMPLETE` / `a.b()` / `a.a()` / `a.c()` 的组合闸门。 **实测可用**：01:02:43 用
    `su -c 'am start-foreground-service -a oplus.gleanerservice.intent.action.COLLECT_DATA --ei triggerType 1 -n …'`
    起它，状态栏随即出现闪记的「已记住」胶囊，服务约 15s 后自己 `onDestroy`。
    - `longPressEventType` 只有 CDS 那份读，Gleaner 这份不读 ⇒ extras 只留 `triggerType=1`。
    - **`uid 0` 会绕过组件权限**（`checkComponentPermission` 对 ROOT / SYSTEM_UID 直接放行），所以这一族都能用
      `su -c 'am …'`
      直接复现，不必真按键 —— 上一轮「起法（前台/普通）导致被回收」的猜测因此作废：**起法是对的，错的是目标**。

- 三个目标都带签名级权限（`oplus.permission.OPLUS_COMPONENT_SAFE`、
  `com.oplus.permission.KEY_MAGIC_SERVICE`、`com.oplus.permission.safe.SECURITY`），宿主 `com.oplus.gesture` 都在
  `requested permissions` 里（`dumpsys package`）。
