# 侧键：接管点、手势来源与判定

## 二、真正的接管点（框架侧）

实现在 **`com.android.server.policy.StrategyActionButtonKeyLaunchApp`**，位于 `oplus-services.jar`（**不在**
`com.oplus.server.policy`，也**不在** `com.android.server.policy` 的 AOSP 部分）。

### 入口方法（**只挂外层那一个**）

> ⚠️ 这一节原写「两种，都要挂」，与 `bridge/Framework.kt`
> 的实现和 §14.4 的结论相反（2026-10-15 审计 #39）。照那行做会重造幽灵长按 —— 正确口径是**只挂外层**
> `actionInterceptKeyBeforeQueueing`；`$2` 是外层方法的**纯转发**（12 条指令、 `preCondition`
> 恒 true），挂它 0 收益，只会让同一次按键被回调两次。

```
com.android.server.policy.StrategyActionButtonKeyLaunchApp
    # actionInterceptKeyBeforeQueueing(Landroid/view/KeyEvent;IIZZ)I   ← 只挂这一个

com.android.server.policy.StrategyActionButtonKeyLaunchApp$2
    # interceptKeyBeforeQueueing(Landroid/view/KeyEvent;IIZZ)I
    # preCondition()Z                                                  ← 不挂（见上）
```

`$2` 是一个内部回调类（`onChange` 观察者 +
`interceptKeyBeforeQueueing`），说明这套实现是**通过 ContentObserver 监听配置变化**再决定拦截行为的。

### 其余已确认成员（可作降级/诊断依据）

```
getInstance()Lcom/android/server/policy/StrategyActionButtonKeyLaunchApp;
initActionButton()V
checkInterceptConditions(Z)Ljava/lang/Integer;
interceptActionKeyDown(Landroid/view/KeyEvent;)V
interceptActionKeyUp()V
injectActionButtonPressKeyEvent(Landroid/view/KeyEvent;)V
readActionButtonSwitchState(I)V
readActionButtonSceneName()V
readActionButtonGamePreventValue(I)V
updateActionKeyStatistics(ZZ)V
initInBootCompleted()V
initStrategy()V
getLongPressTimeout()J
isNotSupportWake()Z
isNotSupportedPocketMisTouch()Z
longPressStartVibrate()V
pokeUserActivity()V
releaseWakeLock()V
wakeup()V
getStartAppInScreenOffState()Z
setStartAppInScreenOffState(Z)V
uploadCollectData(Ljava/lang/String;)V
```

关键字段（反射时可读）：

```
mActionButtonSwitchObserver
mHasAiSmartKeyFeature
mPowerManager
mLastPokeUserActivityTime
mHasInjectLongPressDownEvent
```

关键字符串常量：

```
ACTION_BUTTON_CLICK
ACTION_BUTTON_CONTENT_URI
OPLUS_ACTION_BUTTON_SWITCH_STATE_URI
GAME_ACTION_BUTTON_PREVENT_STATE_URI
OPLUS_ACTION_BUTTON_SWITCH_STATE
GAME_ACTION_BUTTON_PREVENT_STATE
```

Handler 消息（长按内部流程，说明**长按是系统自己判的**）：

```
MSG_ACTION_BUTTON_LONG_PRESS_DOWN
MSG_ACTION_BUTTON_LONG_PRESS_UP
```

功能开关属性：

```
ro.oplus.key.actionbutton
OPLUS_FEATURE_ACTION_BUTTON
SUPPORT_ACTION_BUTTON_GAMEMODE_PREVENT
SUPPORT_GAME_PREVENT
```

## 四、「设置 → 快捷键」入口

```
Action : "com.oplus.gesture.StartActionButtonSettingsActivity"
Activity: com.oplus.gesture/.actionbuttonkey.ActionButtonSettingsActivity
```

同包内的兄弟入口（可作降级候选）：

```
com.oplus.gesture/.guide.GestureMainActivity
com.oplus.gesture/.sprucekey.SpruceSettingsActivity   (action: com.oplus.gesture.StartSpruceSettingsActivity)
com.oplus.gesture/.aon.AONGestureMenuActivity
com.oplus.gesture/.custom.AllScreenOffGestureActivity
```

`ActionButtonSettingsActivity` 的 `onCreate` 受 `oplus.permission.settings.LAUNCH_FOR_EXPORT` 保护 —— 已验证 adb
shell 直接 `am start` 会被拒：

```
SecurityException: ... requires oplus.permission.settings.LAUNCH_FOR_EXPORT
```

## 五、系统支持的侧键动作（从资源名读出）

设置页里可选的侧键功能（`action_button_*_title` / `_oval_*`）：

```
非操作 non_operation        相机 camera
手电筒 torch                截屏 screenshot
录音 recorder               免打扰 ringer_mode / not_disturb
闪念 flash_memory           翻译 translate
OCR 扫描 ocrscanner         控制中心 control_center
启动应用 launch_app         流体云 fluid（ring / long_press / vibrate / silent）
```

⚠️ **`control_center` 只是资源名，没有可照抄的调用目标**：场景表（`actions/system.md` §17.4 那份实测全列）里**没有** 这一行 —— 拿不到
`scene_owner` / `*_action_info`。模块侧的做法是自己试候选，**已真机定死**（2026-10-14 22:20， `PLK110`，重启后）：

| 项            | 结论                                                                                                                                                                                                                    |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 命中          | **`IStatusBar.animateExpandSettingsPanel(String)`**（子面板传 `null`），经 `StatusBarManagerService.mBar` —— 那条 binder 通 SystemUI，**这一层没有权限检查**（同一个服务上 `togglePanel()` 一直是通的，就是同一个原因） |
| 怎么调        | 在 system_server 里 `ServiceManager.getService("statusbar")` 拿到服务实例 → 反射读它的 `mBar` 字段 → 在这个对象上调 `animateExpandSettingsPanel(null)`                                                                  |
| 判据行（`w`） | `已在本机调 IStatusBar.animateExpandSettingsPanel（控制中心）`                                                                                                                                                          |
| 备用候选      | 服务那层的 `expandSettingsPanel(...)`（AOSP 在那里有 `enforceExpandStatusBar()`）—— **本次没用上**，留着当兜底；两条都落空时代码会把带 `Expand` / `Panel` 的方法名打进日志                                              |
| 通知中心      | 同服务上的 **`IStatusBarService.togglePanel()`**（0 参，展开 / 收起），两条动作共用一条跨进程通道                                                                                                                       |

代码在 `bridge/StatusBarBridge.expandControlCenterLocal`；⚠️ 注入侧 ⇒ 改它要整机重启。

## 七、由此确定的实现方案

> **实测状态（2026-10-10，PLK110 / Android
> 17）**：链路**端到端跑通**。开机 5/5 接管点全挂上；单击 / 双击 / 长按三种手势实测判定正确、动作分发正确。

1. **Hook 面**：`StrategyActionButtonKeyLaunchApp#actionInterceptKeyBeforeQueueing`
2. **配置面**：配置只放**宿主 App 私有目录**（`/data/user_de/0/com.oplus.gesture/files/`），由宿主进程（判定 + 执行）读，**热加载**、改完立即生效、**无需重启**。注入侧**不读配置**（它读不到，见 `lsposed.md` 六之七）；它唯一需要的主开关布尔走广播。系统侧键映射由**宿主进程**用
   `ContentResolver` 读 `content://uri.gesturemanagerprovider/action_button`（见 `lsposed.md` 六之五 / 六之七）。
3. **降级**：任一环失败只记日志，绝不抛给 system_server。provider 读失败（本机必然，见 `lsposed.md` 六之五）与 `shell:`
   不可用（SELinux）都属已知限制， **不影响按键接管**。
4. **动作验证注意**：ColorOS 包名与 AOSP 不同（相机 `com.oplus.camera`），配错只报 `解析不到界面`，不是 Hook 问题。

## 九、侧键手势的真实来源：框架判「单击 / 长按」，宿主数「双击」（实测）

> 结论先行：**这个侧键本来就有单击 / 双击 / 长按这套事件**，由「框架 + 宿主」一起判好，模块该做的是**在宿主进程里接住它们**，而不是在
> `system_server` 里自己计时。于是「设置页写配置 →
> system_server 读配置」这条跨域死路也一并绕开了（宿主与设置页同 uid）。

### 9.1 框架侧：自己判单击 / 长按（`oplus-services.jar` 反编译）

`com.android.server.policy.StrategyActionButtonKeyLaunchApp`（父类 `InputStrategy`）里的常量：

| 常量                                | 值                                            | 含义                         |
| ----------------------------------- | --------------------------------------------- | ---------------------------- |
| `SINGLE_TAP`                        | `"singleTap"`                                 | 手势类型：单击               |
| `LONG_PRESS`                        | `"longPress"`                                 | 手势类型：长按               |
| `LONG_PRESS_TIME`                   | `495`                                         | **框架自己的长按阈值（ms）** |
| `MSG_ACTION_BUTTON_LONG_PRESS_DOWN` | `2`                                           | 长按内部消息                 |
| `MSG_ACTION_BUTTON_LONG_PRESS_UP`   | `3`                                           | 长按内部消息                 |
| `ACTION_KEY_TYPE`                   | `"ActionType"`                                | 派发时携带手势类型的键名     |
| `SECOND_MENU_NAME`                  | `"second_menu_name"`                          | 二级菜单                     |
| `TAG`                               | `"STKeyLog_StrategyActionButtonKeyLaunchApp"` | 它自己的日志 TAG             |

- 它**没有**任何 `launch` / `startApp` 之类的「执行动作」方法 —— 执行是**按场景派发给场景宿主**
  （`scene_owner`），派发点看不到，说明走的是 `InputStrategy` 那条通用链路。
- 场景表里每种场景各有 `click_action_info` / `click_action_type` 与 `long_press_action_info` /
  `long_press_action_type`、`second_menu_activity_info`、 `long_press_has_own_vibrator`（`content query` 实测）—— ⇒
  **框架的一等手势只有「单击」和「长按」，没有双击**。
- 当前生效的场景存在 `Settings.Global` 的 **`oplus_action_button_switch_state`** （本机实测为 `null`
  ⇒ 侧键什么都不做；设成 `start_app` 后框架立刻去 bind 宿主服务）。

### 9.2 宿主侧：双击是宿主自己数的（`reference/gesture.apk`）

宿主 `com.oplus.gesture` 里能看到这些**日志字面量**（方法名被混淆，只有日志字符串可读）：

```
onSingleTap: owner is null or empty
hyperKeyDoubleClick: currentScene: … / game In Foreground / pkgName is scene type
longPressStartApp currentScene = … / longPressStartCamera … / longPressStartScreenShot …
KeyStartCamera init, mDoubleClickCount= …      ← 宿主自己维护双击计数
```

⇒ 框架发 `singleTap` 给宿主，**宿主自己按 `mDoubleClickCount` 判双击**。

### 9.3 框架 → 宿主的通道：bind 服务 + `Messenger`

宿主两个相关服务**都实现 `onBind`（手势消息走这条）**（dex 实测）：

> ⚠️ 这一节原写「两个服务都**没有** `onStartCommand`」——**错的**：两个都有。
> `StartAppService.onStartCommand` 只打一行日志就返回；`ActionButtonKeyService.onStartCommand`
> 有副作用（会打开侧键设置页，见 §40）。所以「唤醒 / 保活」用 `startService` 打这个服务时会把
> 模块页一起拉出来。

```
com.oplus.gesture.actionbuttonkey.quicklaunch.StartAppService   ← 「打开应用」场景
  a: android.os.Messenger                 ← 跨进程就靠它
  b: StartAppService$a extends Handler    ← 手势消息落点
com.oplus.gesture.server.ActionButtonKeyService                 ← 其余场景
  c: ActionButtonKeyService$a extends Handler
```

两个服务都在**宿主主进程**（清单里没有 `android:process`）⇒ 手势劫持要装在 `com.oplus.gesture` 主进程，不是 `.ui`。

```
I OplusKey: 宿主服务 onCreate：StartAppService
I OplusKey: 宿主服务 onBind：StartAppService | intent=action=null
            component=ComponentInfo{com.oplus.gesture/…quicklaunch.StartAppService}
```

⇒ 框架**确实在 bind** 这个服务，通道成立。

在 system_server 里读设置页写的文件是**必然失败**的：

```
D OplusKey: 读配置失败（忽略）：/data/data/com.oplus.gesture/files/opluskey_config.conf
            | open failed: EACCES (Permission denied)
```

文件权限位是 `0666`、路径也对 —— 但它的 SELinux 标签是
`u:object_r:app_data_file:s0:c512,c768`（**带 MCS 分类**），`system_server` 域进不去。结果是**一直回落到
`/data/system/OPlusKey/config.conf` 里的旧配置** ⇒

**改到宿主进程之后这条通道自然就通了**：设置页（`com.oplus.gesture.ui`）与宿主主进程（`com.oplus.gesture`）**同一个 uid**，读同一个文件不需要任何跨域特权。

> 另外两个「注入侧读得到」的位置也一并记下：`/data/system/*`（`system_data_file`）可读； `Settings.Global` 在 Android
> 16+ 落盘成 **ABX 二进制 XML** （`/data/system/users/0/settings_global.xml` 开头是
> `ABX\0`），**不是纯文本，别去解析它**。

框架每次按键都会先判「当前场景」，**只有开关开着**它才把手势派发出去。开关关着时它只打一行：

```
W/STKeyLog_StrategyActionButtonKeyLaunchApp: action button switch close
D/KEYLOG_Notify: INTERCEPT Queueing down, interceptor = …StrategyActionButtonKeyLaunchApp$2…
```

—— 键被**吞掉**、但什么都不做（宿主一条消息都收不到）。而**模块的换页界面正好替换掉了唯一能打开它的那个界面**（系统原版「快捷键」页），所以表现就是「模块装了、按了没反应」。

打开它（在系统原版页面上走一遍即可，模块自带 `opluskey_bypass` 旁路能把原版页面放出来）：

1. 点底部动作图标选一个动作 → 底部按钮从灰的「当前使用」变成可点的**「设定」**；
2. 点「设定」→ 框架立刻推来 `btn_switch_status=true`，并记下新场景：

```
D/STKeyLog_StrategyActionButtonKeyLaunchApp: readActionButtonSwitchState, mActionSwitchState: camera
D/ActionButtonKeyUtils(504=宿主主进程): readActionButtonSwitchState, actionButtonSwitchState: camera,
    userId=0 DEFAULT_ACTION_SWITCH_STATE=flash_memory
D/STKeyLog_StrategyActionButtonKeyLaunchApp: onChange mActionSceneOwner = com.oplus.camera,
    mActionSwitchStat=camera mSecondMenuName=camera_key_magic_photo
```

两个要点：

- `mActionSwitchState` **就是「当前场景名」**（不是布尔开关）；`DEFAULT_ACTION_SWITCH_STATE=flash_memory`。
- 场景是**按 `scene_owner` 派发**的 —— 场景表里 `scene_owner = com.oplus.gesture` 的只有 **`start_app`** 与
  **`quick_function`**。**只有当前场景属于这两个之一，框架才会来找宿主**，手势才可能落到我们手里；选成 `camera` /
  `translate` / `screen_shot` 时框架找的是 `com.oplus.camera` / `com.coloros.translate` /
  `com.oplus.screenshot`，模块在宿主里等不到任何东西。

### 9.6 通道是 `Messenger`（不是 provider）

```
I/OplusKey: 宿主服务 onBind：StartAppService | binder=android.os.Handler$MessengerImpl
W/OplusKey: 侧键手势消息：StartAppService$a#handleMessage | what=3 arg1=0 arg2=0
            data={btn_switch_status=true}
```

⇒ 框架 → 宿主是 **Message**（`what=3` = 开关 / 场景状态通知），宿主 → 框架是
`msg.replyTo.send(...)`。**要改「当前场景」就该在这一层动手** —— `Messenger.send` 与宿主 Handler 的 `handleMessage`
模块都已挂上，两个方向都看得到。

顺带否掉两条路（都实测过）：

- **不是** `Settings.Global`：`oplus_action_button_switch_state` 设成 `1/true/open/on/start_app`
  都不影响框架判定（它读的是场景名，且来自宿主侧）；
- **不是** provider 写接口：把 `ActionButtonProvider` 的 `insert/update/delete/call/bulkInsert/applyBatch`
  全挂上，走一遍「选动作 + 设定」， **一条写入都没有** —— 场景不是从这条路存进去的。

### 9.7 侧键有**三个** keyCode：735 + ColorOS 内部注入码 780 / 782（实测）

```
D/OplusKey: 按键（策略类）：keyCode=780 不是 侧键，放行给系统
D/OplusKey: 按键（策略类）：keyCode=25  不是 侧键，放行给系统   ← 音量-（真的不是侧键）
```

⇒ 两个结论：

1. **策略类那条路上，侧键报的是 ColorOS 自己注入的内部码 780 /
   782**，不是原始上报码 735。只认 735 会把侧键整个放行掉 ——表现就是「模块装了、侧键完全不被接管」。（735 是**原始**上报码，`injectActionButtonPressKeyEvent`
   之后才变成 780/782。）
2. **音量键 / 电源键也会走进同一个策略回调**（日志里的 25）——所以「能进来就是侧键」这个老假设是错的，会吃掉音量键。

⇒ 正确写法是一份**白名单**：`KeyId.acceptedKeyCodes`（侧键 =
`{735, 780, 781, 782}`，781 为 §14.4 补齐），策略类路径按它认领；PM 兜底路径**只认原始码**（735），否则同一次按键会被策略类和 PM 两条路各判一次。

### 9.7.1 侧键的**原始**硬件码 = 735，来自设备自己的输入描述符（实测，2026-10-13）

```
$ getevent -pl | grep -A3 gpio-keys
add device 12: /dev/input/event0
  name:     "gpio-keys"
    KEY (0001): BTN_TRIGGER_HAPPY32
```

`BTN_TRIGGER_HAPPY1 = 0x2c0 = 704` ⇒
`BTN_TRIGGER_HAPPY32 = 704 + 31 = **735**`。即**侧键在驱动层就报 735**，`KeyId.keyCode = 735` 是正确的原始码。侧键独占
`event0`（另一路 `gpio-keys` 的 `event1` 是音量-、`event2` 是电源）。

## 十四、宿主侧键分发的**权威调用链**（dexdump 指令级，2026-10-13）

> 起因：要用 REA 把宿主「看透」，把 hook 的实现路径压到最短、最不易错。这一节是 `oplus-services.jar`（含
> `classes2.dex`）的 `dexdump -d` 指令级结论，不是猜。结论推翻了之前两处实现假设 —— 见 §14.4。

### 14.1 完整链路（从原始输入到侧键动作）

```
InputReader/InputDispatcher
  → InputPolicy.interceptKeyBeforeQueueing(KeyEvent, down, keycode, policyFlags, interactive)   // 2e7f30
      → InputKeyEventNotifyService.interceptKeyBeforeQueueing(...)                              // 2e7158
          ├─ notifyObserversBeforeQueueing(...)          // 观察者（只读，不改语义）
          └─ notifyInterceptorBeforeQueueing(...)        // 2e71a4 ← 拦截器链
               对 mQueueingInterceptorsCopy 里每个 KeyInterceptor:
                 if (!preCondition()) continue;
                 ret = interceptor.interceptKeyBeforeQueueing(event, keycode, policyFlags, down, interactive)
                 if (ret == 0) intercept = false            // 有人拦下 ⇒ 返回 0
               return intercept ? 1 : 0
      → 返回 0 = 系统吞掉这次按键；返回 1 = 继续往下派发
```

`StrategyActionButtonKeyLaunchApp$2` 在 `initStrategy()` 里经 `InputKeyEventNotifyService.registerKeyInterceptor(...)`
注册进 `mQueueingInterceptorsCopy`（注册后 `Collections.sort`）—— **这就是它被调用的唯一途径**。

### 14.2 `StrategyActionButtonKeyLaunchApp$2` 是**纯转发**（12 条指令）

```
[3058a0] iget-object v0, v6, ...$2;.this$0:...StrategyActionButtonKeyLaunchApp;
[3058a4] move-object v1, v7 / move v2..v5, v8..v11      ← 原样搬 5 个参数
[3058ae] invoke-virtual {v0..v5}, ...StrategyActionButtonKeyLaunchApp;.actionInterceptKeyBeforeQueueing(...)I
[3058b4] move-result v7
[3058b6] return v7
```

`preCondition()` 更短：`const/4 v0, #1; return v0`（恒真）。⇒ **挂 `$2` 与挂外层 `actionInterceptKeyBeforeQueueing`
落在同一条代码路径上，能力完全相同**；两个都挂只会让同一次按键被回调两次。这是 §14.4 的一处优化点。

### 14.3 `actionInterceptKeyBeforeQueueing` 的 keyCode 分发树（335 指令，指令级）

| 指令地址 | 比较                                      | 去向                                              |
| -------- | ----------------------------------------- | ------------------------------------------------- |
| `0000`   | `const/16 v0, #780`                       | —                                                 |
| `0008`   | `if-ne v10(=keyCode), 780`                | 不等 ⇒ 跳 `00be`                                  |
| `000d`   | `getRepeatCount()==0`                     | 780 主分支（拦截/放行判定）                       |
| `00be`   | `const/16 v0, #782`                       | —                                                 |
| `00c2`   | `if-ne keyCode, 782`                      | 不等 ⇒ 跳 `0122`                                  |
| `00c4`   | `getRepeatCount()==0`                     | 782 分支（长按：`longPressStartVibrate`）         |
| `0122`   | `const/16 v0, #781`                       | —                                                 |
| `0124`   | `if-ne keyCode, 781`                      | 不等 ⇒ 跳 `0140`                                  |
| `0126`   | `getRepeatCount()==0`                     | 781 分支（`pokeUserActivity` + `singleTap` 统计） |
| `0140`   | `const/16 v0, #765`                       | —                                                 |
| `0142`   | `if-lt keyCode, 765`                      | 小于 ⇒ 跳 `0148`                                  |
| `0144`   | `const/16 v0, #772`                       | —                                                 |
| `0146`   | `if-le keyCode, 772`                      | 大于 ⇒ 跳 `014e`                                  |
| `0148`   | `mOplusPowerKeyHandler.removeMessages(2)` | 765..772 归**电源键**处理                         |
| `014e`   | `return v1`（v1 = 1）                     | 公共尾：**未接管 ⇒ 返回 1（继续派发）**           |

780 主分支内部（`000d`–`00bc`）： `mActionSwitchState` 为空则该读 `readActionButtonSwitchState(userId)`；
`"nothing".equals(mActionSwitchState)` ⇒ 打 `switch_nothing` 统计并 **`return v4`（0，吞掉）**；否则
`checkInterceptConditions(down)`：非 null ⇒ **`return interceptResult.intValue()`**；再按 `getAction()` 分
`interceptActionKeyDown(event)` / `interceptActionKeyUp()`，两者后 **`return 0`**。

⇒ **框架原生的 780 分支在「开关=nothing」时返回 0（吞键）**，与模块的「接管 ⇒ 返回 null」语义一致（详见 §14.5）。

### 14.4 由此得到的 **hook 实现路径优化**（本次改动）

1. **`Framework.kt` 不再挂 `...$2`**：`$2` 是纯转发（§14.2），挂它 0 收益、且与外层 `actionInterceptKeyBeforeQueueing`
   重复回调。删掉 `STRATEGY_CALLBACK_CANDIDATES` 与对应循环，接管点从「4 个候选」收敛为「1 个外层类 + PM 兜底」。
2. **`KeyId.acceptedKeyCodes` 补齐 781**：dexdump 证明 780/781/**782** 三个码都进这条方法（781 走
   `pokeUserActivity` + 单击统计）。旧集 `{735, 780, 782}`
   **漏了 781**，若某场景它作为原始码出现会被误判「不是侧键」而放行。
3. **接管点去重**：`findSideKeyLayers` 用 `LinkedHashSet` 按 `owner` + `Method` 对象 + `kind`
   去重（`InterceptionPoint` 是 data class，相等即这三项相同），防止候选类名重复 / 同方法多入口
   导致的重复安装。同名的**不同** `Method`（重载，或父子类各声明一个）不在这条去重里：
   策略族只有一个名字、没有这个问题；`PM_EXT_ENTRIES` 那一族是「覆盖写法 + 父类实现」两个名字，
   同一个类上同时命中时会在 `collect` 里做一次**同层互斥**（只留靠前的那个，其余打 W），
   否则一次按压会被判定器喂两遍（双击被吃成两次单击、长按枪反复作废）。
   `AMS_BROADCAST_ENTRIES`（`broadcastIntent` 的多个重载）**必须个个都装**，不能按名字去砍 ——
   漏一个就等于收不到配置广播。

### 14.5 返回值语义（模块 `onIntercept` 的 `true/false` 对接点）

- 框架侧 `KeyInterceptor.interceptKeyBeforeQueueing` 返回 **0 = 吞掉**（⇒ `notifyInterceptorBeforeQueueing` 置
  `intercept=false` ⇒ 上层返回 0 ⇒ 系统不派发）； **非 0 = 放行**。
- 模块 `SideKeyInterceptor.handle` 返回 `true=接管`，`KeyHooks.onIntercept` 把 `true` 映射为**返回 `null`**（Xposed 的
  `null` = 吞掉原方法返回值 / 不执行原方法）。两者语义对齐：接管 ⇒ 系统看不到这次按键。

### 14.6 顺带确认：框架**自己**也判「长按 / 单击」，但只有 `singleTap`/`longPress` 两个一等手势

`checkInterceptConditions(Z)`（190 指令）依次挡：`isUserSetupComplete()`、
`isWsnCompleted()`、`getGameStateValue()`+`isKeyguardLocked()`+`mGamePreventSwitch`（游戏防误触 ⇒ 返回 0）、
`getIsNearState()`+`isNotSupportedPocketMisTouch()`（口袋防误触 ⇒ 返回 0）、
`getUnderWaterCameraStatus()`（水下相机）。任何一项命中都 `return 0`（吞键、不派发）。⇒
**框架会在这些场景主动吞掉侧键**，模块 hook 在**更外层**，拿到的是「框架还没判过」的原始事件 ——这正是模块把判定器放在
`system_server` 侧的合理性来源（§9 已述）。

## 十六、「放行」的两条坏路：空转与自持循环（实测，2026-10-13，PLK110）

> ⚠️ **2026-10-14：「放行」（`native` / `passthrough`）这一档已从模块里整条删掉**
> —— 判定手势必须先吞掉 DOWN，做不到「只交还某一个手势」，用户要的是「整键给系统」，而那正是主开关的语义。本节保留的是**当时**的实测记录（重注入、场景派发、自持循环都在这一档上踩过），不代表现在的实现；现状与取舍见
> `docs/runtime/key-to-gesture.md` 第三节。

「放行」（`native` / `passthrough`）的实现是「吞掉原始键 + 宿主侧重注入一对 735」。真机实测暴露了两条坏路：

**① 空转**（`single=native`
时的旧表现）：重注入的 735 不会被本模块认领（见下），它只走原生管线 ——而原生动作的载体是框架派给宿主的 `what=1`
场景消息，宿主侧钩子**无条件吞掉**它 ⇒ 模块吞了原键、原生动作也没了 ⇒ 按下去什么都不发生。

**② 自持循环**（本次实测复现）：物理按压 → 策略类钩子认领并吞掉原方法 → 判出单击 → 注入侧转发 `passthrough`
→ 宿主重注入 735 →
**重注入的 735 又一次进到策略类钩子**（只按 keyCode 认领，认不出是合成事件）→ 再判一次单击 → 再放行一次 ⇒ 每 ~110ms 一轮，日志三行一组连续刷：

```
(com.oplus.gesture) 收到 system_server 转来的手势：key=plus phase=passthrough repeat=false
(com.oplus.gesture) 侧键：放行一次（重新注入 keyCode=735）
(com.oplus.gesture) 场景派发放行（本次按键判为「放行」⇒ 执行原生动作）
```

实测窗口：22:47:56.579 → 22:47:58.140（`modules_*.log`，约 16 轮 /
1.6s）。**次生现象**：每轮的场景动作把侧键设置页重新拉起，宿主 `Activity` 是 `singleInstance` ⇒ 走 `onNewIntent`
⇒ 模块的「重进即清栈回首页」被反复触发（用户看到的是「进其他页面自动退回主页」）。停掉宿主进程（`am force-stop com.oplus.gesture`）即断环 ——环需要宿主侧的重注入才能维持。

**❌ 曾用错的判别手段**：`adb shell input keyevent 735` **不会**触发本模块的钩子（注入后 `(system)`
侧零转发行，设备也无任何原生动作），不能用它代替物理按压做「放行」实验 —— `input` 与
`InputManager.injectInputEvent`（`KeyInjector`：`deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD`(-1)、
`scancode = 0`、`source = SOURCE_KEYBOARD`）的 deviceId / source 组合不同。

**修法（2026-10-13）**：

- 注入侧按
  `keyEvent.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD`（-1）识别**合成事件**、不认领（放行给系统）⇒ 断掉 ② 的环；
- 宿主侧的场景派发钩子改为「本次按压判为放行 ⇒ 放行**第一条** `what=1`（原生动作的载体），其余仍吞」⇒ 救回 ①；
- 宿主侧另加兜底闸：1s 内收到 ≥3 次放行判定即判为环，暂停注入 3s（防个别 ROM 判不出合成事件）。

**⚠️ 判定的到达顺序是竞态的（实测）**：宿主主线程忙时（原生动作自己就会占住它），框架那条 `what=1`
会**排在「放行」广播前面**到达（实测 23:02:22 / 23:02:24：`what=1` 早 1ms，判据扑空、被误吞，随后补发的判定已无 `what=1`
可放 ⇒ 原生动作丢失）。所以宿主侧的钩子不能只看「判定到没到」这一瞬间：

- 判定已到（配额有余）⇒ 立即放行（正常路径）；
- 判定未到 ⇒ **扣住**这条 `what=1`，700ms 内判定若到达就补投（补投必须**复制**一条新 `Message` ——原实例在本次
  `handleMessage` 返回后已被 Looper 回收进复用池）；
- 一次「放行」只放一条（配额），同一次按压的「按压自己那条」与「重注入那条」不会各放一次。

实测两条路都跑通（23:04:51 正常路径 / 23:04:53 竞态路径走延迟补投），每次按压各只放一条。

## 二十五、真实侧键事件的 deviceId 可以是 -1（实测，2026-10-18，PLQ110 / ColorOS 17.0.0）

一份 PLQ110 的模块日志（`1.0.0-release`）里，策略回调收到的**真实**按压是这样的：

```
按键（…StrategyActionButtonKeyLaunchApp#actionInterceptKeyBeforeQueueing）：合成事件（deviceId=-1），放行给系统
按键（…）：合成事件（deviceId=-1），放行给系统          ← 一次按压的 DOWN + UP，两条都是 -1
侧键（…）：UP keyCode=780                              ← 偶尔从另一条路径漏进来一条真 deviceId 的抬手
侧键：只收到抬手（没见到按下）⇒ 按孤立 UP 处理（补判一次单击）
```

- 同一版代码在 OnePlus 15（PLK110）上，真实事件的 `deviceId` 是正常值，DOWN 能直接认领。
- 所以**不能拿 `deviceId < 0` 当「我们自己注入的合成事件」的判据**：`KeyInjector` 下发的确实是
  `deviceId = -1`（`KeyCharacterMap.VIRTUAL_KEYBOARD`），但有的 ROM 自己的按键事件也带 -1。
- 判据改成「`deviceId < 0` **且** keyCode 不在认领白名单里」：`KeyInjector` 只会下发动作里的
  那几颗码（主屏幕 / 返回 / 音量 / 媒体 / 截图 / 通知），侧键的 735 / 780 / 781 / 782 从不由我们自己注入，
  于是这几个码即便 -1 也是真按键。
- 症状对得上用户反馈：单独按侧键几乎无反应，某些按法（比如电源键 + 侧键改走另一条派发路径）凑出
  一个孤立抬手才「偶尔触发一次」。

## 三十九、熄屏按键：`uptimeMillis` 在深度睡眠里不走（实测，2026-10-31，PKX110 / ColorOS 17）

用户机日志（`1.2.0-release`，`立即单击=false`、双击窗口 300ms）：

```
03:11:54.945 侧键 DOWN
03:11:55.055 侧键 UP（按住 108ms）
03:11:59.695 侧键 DOWN → 立刻判「双击」        ← 真实间隔 4.6 秒
03:12:04.523 侧键 DOWN → 宿主丢弃（age=4824ms）
```

- 4.6 秒的间隔能落进 300ms 的双击窗口 ⇒ 判定用的时钟在两次按下之间只走了不到 300ms
  ⇒ 那段时间设备在**深度睡眠**（`uptimeMillis()` 不含睡眠，`elapsedRealtime()` 才含）。
- 抬手后排的那一枪（`postDelayed`，按 uptime 排）在睡眠里不会到点；下一次按下先到，
  `onKeyDown` 判成双击 → `reset()` 把那一枪摘掉 ⇒ **第一下的单击永远不派发**，用户看到的就是
  「熄屏按下没反应」。
- 结论：手势窗口一律用 `elapsedRealtime()`；熄屏**不再**「抬手即判单击」，改为排窗期间持一段
  带超时的 `PARTIAL_WAKE_LOCK` 把窗口顶到点（见下一条）。
  实现见 `inject/input/GestureDetector`（类注释里那两段）与 `KeyHooks.holdAwake`。
- 后续（1.3.3）：抬手即判单击那一档把**双击整档**废掉了（熄屏下每一下都算单击），所以改成
  `PowerManager.newWakeLock(PARTIAL_WAKE_LOCK)` + `acquire(窗口 + 500ms)`：CPU 在窗口里继续走，
  窗口那一枪才到点。代价是熄屏单击晚一个双击窗口才执行；取不到 `PowerManager` 时静默降级回
  「熄屏判不出双击」。

## 四十、宿主服务的 `onStartCommand` 会打开侧键设置页（反编译 `reference/gesture.apk`，2026-11-12）

用户机反馈：「重启后在服务还没起来的那段时间里，按被接管的按键会把模块页拉起来」。根因不在框架那条
派发上，而在**模块自己调的那一次 `startService`**：

```
com.oplus.gesture.server.ActionButtonKeyService.onStartCommand(Intent, int, int)   // dex 有这个方法
    if (!GestureUtil.isSupportActionButtonKey()) return 2
    if (lb.d.a().d()) d()            // ← 条件成立才开页面
    return 2

ActionButtonKeyService.d()V
    c.obtainMessage(1).sendToTarget()          // what = 1

ActionButtonKeyService$a.handleMessage(Message)
    what == 1 → ActionButtonKeyService.a(服务实例) → e()
    what == 2 → c(arg1)                        // arg1 == 0 时同样落到 d()

ActionButtonKeyService.e()V
    Intent → setClass(ActionButtonSettingsActivity) → remote.transition.d.m(ctx, intent, 2, false)
    ⇒ 起的就是**被模块替换成模块页的那一页**
```

`lb.d.d()` 是「当前有任一拦截条件成立」，六项任一为真即可（逐项反编译，读的都是系统设置）：
`user_setup_complete` 未完成 / 免打扰（`op_breath_mode_status`）/ `isKeyguardLocked()` /
游戏模式（`debug_gamemode_value`）/ `isScreenOff()` / 超级省电（`super_powersave_mode_state`）。

⇒ **锁屏、熄屏、游戏里 `startService(ActionButtonKeyService)` 就会把设置页带到前台。**
模块有两条路打这个服务、都是为了「唤醒 / 保活」（`inject/input/GestureForwarder.wakeHost` 与
`host/GestureReceiver.keepProcessAwake`），于是：

- 开机后那一段设备正锁屏 —— 按一下被接管的侧键 ⇒ 注入侧转发手势 ⇒ 唤醒宿主 ⇒ 设置页弹出来；
- 宿主自己冷启动时 `keepProcessAwake` 那一次 `startService` 同样会弹（`1.4.0` 撤掉的
  `HostWarmup` 其实也是从这条路弹的，与「预热」本身无关）。

**修法**：宿主侧掐掉 `ActionButtonKeyService.onStartCommand` 的正文（`host/SideKeyGestureHooks.hookKeyServiceStart`）。
唤醒有 `bindService`（`BIND_AUTO_CREATE` 就会创建进程）兜着，保活要的是「持有一个已启动的服务」
这件事本身，正文对进程级别没有贡献；框架找宿主走的是 bind + `Messenger`，与 `onStartCommand` 无关。
判据行：`宿主服务 onStartCommand：启动它只为唤醒 / 保活，不再顺带把设置页带到前台`（`w`）。

**不要**把这条当成「`what=1` 是框架的场景派发」：框架那份 `oplus-services.jar` 里**没有**
`Messenger->send` 的调用点，`what=1` 只有本服务的 `onStartCommand`（以及 `what=2 → c(0)`）会产生。
`host/PressTrace` 那套「认领了才吞」照旧留着 —— 它管的是另一件事（停用的键那次原生派发该不该放行）。
