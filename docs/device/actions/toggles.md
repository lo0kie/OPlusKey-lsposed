# 开关类动作：手电筒、免打扰、系统开关、NFC

## 六之八、手电筒：`flashlight` 能用，**亮度档位做不了**（实测，2026-10-17）

> 结论先说：**只保留开 / 关（`flashlight`）**。试过「按档位开灯」（`torch:1..4`）并**已撤销** —— 它在真机上
> 做不到「一次按对」，见下面第三条。别再往回加。

**一、这颗 LED 的归属：V4L2 flash 设备，不是 lights HAL**（`adb shell su -c ls /sys/class/leds/`）

```
white:flash-1   ← 手电筒（相机闪光灯）；带 flash_brightness / flash_strobe /
                   flash_timeout / max_flash_brightness / max_flash_timeout
yellow:flash-0、amber:flash-2
red / green / blue ← 通知灯，这套才归 lights HAL（vendor.qti.hardware.lights.service）
```

`flash_strobe` / `flash_timeout` / `max_flash_brightness` 这一组是 **V4L2 flash class** 的属性 ⇒ 闪光灯**归相机驱动管**。
所以两条旁路都不通：

- 走 lights HAL（`android.hardware.light.ILights`）设亮度 —— 它不认这颗灯，只管 `red` / `green` / `blue` / `amber`；
- 直接写 `/sys/class/leds/white:flash-1/brightness` —— 节点是通用 `sysfs` 标签、属主 root；宿主进程在
  `platform_app` 域，注入侧在 `system_server` 域，两边都写不了（真去写还是跟相机抢设备，会让 HAL 以为灯是灭的）。

**二、硬件有 256 档，相机 HAL 只暴露 4 档**（`adb shell dumpsys media.camera`）

```
android.flash.info.available (50000)              → TRUE
android.flash.info.strengthMaximumLevel (50002)   → 4   ← 公开 API 用的就是这个，默认档 2
android.flash.info.strengthDefaultLevel (50003)   → 2
android.flash.singleStrengthMaxLevel (40007)      → 5   ← OPPO / QTI 私有 tag
android.flash.singleStrengthDefaultLevel (40008)  → 5
android.flash.torchStrengthMaxLevel (40009)       → 5
android.flash.torchStrengthDefaultLevel (4000a)   → 5
```

`max_brightness` 是 **255**（硬件层面 0..255），那 4 档是相机 HAL 自己映射的。私有那一路比公开 API 多一档（5 vs 4），
厂商默认值 5 被公开上限钳到 4 —— 这正是「只有第 4 档不用按第二次」的成因（它跟默认值撞上了）。

**三、为什么档位做不了：点亮那一下会把请求的档位丢掉**

`CameraManager.turnOnTorchWithStrengthLevel(cameraId, level)`（**API 34 起**，与 `setTorchMode` 同一条轻量路径，
不用开相机）在**灯本来灭着**时调用，HAL 会先按**默认档**把 LED 点亮、把这一次请求的档位丢掉。真机实测：

- 同一批按压里，2 档、4 档第一次就成，3 档得按第二次（日志里第二次仍是「第 3 档」而不是「关」，说明读回来不是 3）；
- 同一次调用里**连设两次**——无效；
- 隔 300ms 再补一次——**有效**，但肉眼能看到「先亮到默认档再回落」；
- 由 torch 回调报「亮了」时再补——也一样，只是窗口短些。

判「档位到底吃进去没有」用 `getTorchStrengthLevel(cameraId)`（API 34 起）读回来比，这个值**可用**（「同档再按一次 = 关」
那条逻辑就是靠它验证过的）。

**四、系统自己怎么做**（`SystemUI.apk` → `classes7.dex`，用 `tools/dexdump.py refs` 按被调方法名找调用点）

系统用的**也是同一个公开 API**，差别只在调用时机：

- `com.android.systemui.flashlight.data.repository.FlashlightRepositoryImpl.setLevel`：
  先 `connectToCameraLoadFlashlightInfo()`，再 `waitForStateToBecomeAvailableOrPermanentlyUnavailable()`，
  **状态可用了才调**，方法体里还有一条「请求的档位 != 当前档才调」的判断；
- `com.android.systemui.statusbar.policy.FlashlightControllerImpl`：调之前先查 `mCameraId`（`AtomicReference`），
  **为空直接跳过**。

而且系统的交互是「**先把灯点亮，再在亮着的状态下调档**」—— 它从不「灯灭着的时候直接设档」，也就绕开了上面那个现象。
所以系统里**没有**可参考的「一次按对」的做法。

## 二十三、ColorOS 底层接口取证：手电筒、免打扰与响铃模式（实测，2026-10-17）

> 反编译取证目标：OnePlus 15（PLK110 / ColorOS 17 / Android 17）真机上的
> `/system_ext/priv-app/SystemUI/SystemUI.apk`、`/system/framework/oplus-framework.jar`、
> `/system/framework/oplus-services.jar`。

### 23.1 手电筒状态与专属通道

1. **Settings 状态同步**：
   SystemUI 的 `FlashlightControllerImpl` 监听底层的 `TorchCallback` 时，
   会主动同步状态到 `Settings.Secure`：
    - `Settings.Secure.flashlight_enabled`（`"flashlight_enabled"`）：`1` 开启，`0` 关闭。
      可同步读取，无需等待异步相机回调落地。
    - `Settings.Secure.flashlight_available`（`"flashlight_available"`）：`1` 可用，`0` 不可用。
2. **ColorOS 侧键专属入口**：
    - 目标组件：`com.android.systemui/com.oplus.systemui.statusbar.notification.keymagicservice.KeyFlashlightService`。
    - 机制：`KeyBaseService` 族，通过 Messenger IPC 绑定后发 `Message(what=2)`（长按），
      由 `KeyFlashlightController` 调用 `FlashlightControllerImpl` 切换状态。
3. **遗留空桩**：
   `android.os.OplusFlashLightManager`（调用 Binder `additionald`）在 ColorOS 17 上方法体已返回固定假值/空值，已废弃。
4. **读状态的唯一途径**：「系统场景」里那条「手电筒」条件（`ConditionKind.FLASHLIGHT`）读的就是第 1 条那个键
   （`host/action/ConditionChecks` 的 `flashlightMatches`）—— 不注册 `TorchCallback`，也不读
   `flashlight_available`；键不存在时按键判不出处理（两档都不成立）。

### 23.2 免打扰状态与专属通道

1. **Settings 状态位**：
    - `Settings.Global.zen_mode`（`"zen_mode"`）：`0` 关闭，`1` 重要通知，`2` 完全静音，`3` 仅闹钟。
2. **ColorOS 侧键专属入口**：
    - 目标组件：`com.android.systemui/com.oplus.systemui.statusbar.notification.keymagicservice.KeyDndService`。
    - 机制：通过 Messenger IPC 绑定后发 `Message(what=2)`，内部调用 `ZenModeControllerImpl.setZen` 切换。
3. **AOSP 标准接口**：
   `NotificationManager.getCurrentInterruptionFilter()` 读取档位（1=全部通过，2/3/4=免打扰），
   `NotificationManager.setInterruptionFilter(...)` 写入。

### 23.3 响铃模式通道与 ColorOS 拦截陷阱

1. **免打扰压制外部视图**：
   开启免打扰（Zen Mode）时，AudioService 将公共接口 `AudioManager.getRingerMode()` 强行压制为 `0`（静音）。
   读取真实物理档位必须走隐藏 API `AudioManager.getRingerModeInternal()`（或读 `Settings.Global.mode_ringer`）。
2. **ColorOS Ringer Delegate 拦截静音写入**：
   调用公共 API `AudioManager.setRingerMode(0)` 时，ColorOS 的 `mRingerModeDelegate` 会将其拦截并降级改写为 `1`（振动）。
   写入必须走 `@hide` 内部通道 `AudioManager.setRingerModeInternal(int mode)`（宿主进程具备 `STATUS_BAR_SERVICE` 权限）。

## 二十四、系统开关的读写分工（实测，2026-10-18，ColorOS 16）

「系统开关」那十条里，手电筒走 §23 那条专属通道，其余九条是**宿主读状态 + system_server 写状态**：
`Settings` 里那几个键公开可读（宿主 `platform_app` 用 ContentResolver 读得到），而 `setWifiEnabled` /
`setAirplaneMode` 这些要 `NETWORK_SETTINGS` / `MODIFY_PHONE_STATE` 一类特权（宿主没有、system_server
的 uid 1000 有）；反过来 system_server 里借来的 Context 用 ContentResolver **必 NPE**（六之五）——
两边各做一件自己做得来的事，谁都不需要 root。

| 开关     | 读（宿主，`Settings`）             | 写（system_server，binder）                                                                    |
| -------- | ---------------------------------- | ---------------------------------------------------------------------------------------------- |
| WLAN     | `Global.wifi_on`                   | `wifi` · `setWifiEnabled`（`(bool)` / `(String,bool)`）                                        |
| 蓝牙     | `Global.bluetooth_on`              | `bluetooth_manager` · `enable(AttributionSource)` / `disable(AttributionSource, persist=true)` |
| 移动数据 | `Global.mobile_data`               | `phone` · `setDataEnabled`（或 `setDataEnabledForReason`）                                     |
| 飞行模式 | `Global.airplane_mode_on`          | `connectivity` · `setAirplaneMode`                                                             |
| 免打扰   | —— （走既有实现，见 §23）          | `Action.Oplus` → 宿主侧 `AppLauncher`                                                          |
| 暗色模式 | `Secure.ui_night_mode`（`2` = 开） | `uimode` · `setNightMode`                                                                      |
| 省电模式 | `Global.low_power`                 | `power` · `setPowerMode`（1 = 开、0 = 关）                                                     |
| 自动旋转 | —（见下）                          | `window` · `freezeDisplayRotation` / `thawDisplayRotation`                                     |
| NFC      | —（`nfc_on` 读不到，见下）         | `nfc` · `isEnabled` 读、`enable` / `disable` 写                                                |
| 极暗     | —（见下）                          | `color_display` · `isReduceBrightColorsActivated` 读、`setReduceBrightColorsActivated` 写     |

几个容易走错的方向：

1. **自动旋转没有可读的键**：`Settings.System.accelerometer_rotation` 在宿主里读得到，但我们直接调
   `window` 冻结时**不会**写这个键，两边会各说各的 —— 所以这一条的「锁没锁」由 system_server 自己用
   `IWindowManager.isRotationFrozen()` 读、再用 `thawDisplayRotation` / `freezeDisplayRotation` 切。
2. **`clickTile` 对系统磁贴无效**：控制中心里的 `wifi` / `bt` / `nfc` 是 SystemUI 内置的简写 token，
   **没有 `ComponentName`**，`IStatusBarService.clickTile` 与 `cmd statusbar click-tile nfc` 都静默无操作。
   （这条通道已随 `tile:` 动作一起删掉，现在系统开关是 `setting:<id>`。）
3. **`svc nfc` 会被系统杀掉**（`exit=137`）—— 上一版用 shell 时只能走 `cmd nfc enable-nfc`；
   现在 NFC 也按同一张表走 binder（`nfc` 服务的 `enable` / `disable`，与 `cmd nfc` 同一对方法）。
4. **热点没有可用的开关入口**：`cmd wifi start-softap` 存在但必须现给 SSID 与安全类型 / 密码
   （照它开等于把用户配好的热点名改掉）；`TetheringManager.startTethering` 要 `TETHER_PRIVILEGED`
   （宿主是 `platform_app`，只有 `WRITE_SECURE_SETTINGS`）。界面因此不列热点。
5. **`setAirplaneMode` 不在 framework.jar 里**（Android 14+ 把 connectivity 挪进了大模块），
   所以「framework 里 grep 不到」不等于运行时没有 —— 按名字反射仍然打得到，验证要在真机上做。
6. **极暗在颜色显示服务里，而且宿主调不动**（2026-10-29 实测，PLK110 / ColorOS 17.0.0）：
   真开关是 AOSP 的 reduce bright colors，状态看 `dumpsys color_display` 的
   `Reduce bright colors: Activated: ...`。三条错路都试过 ——
   `PowerManager.setReduceBrightColorsActivated` 反射不到（`NoSuchMethodException`，
   `dumpsys display` 的 `mEvenDimmerEnabled` 恒 false、`mNitsToEvenDimmerStrength` 是 null）；
   `Settings.Secure.reduce_brightness` 与 `Settings.System.reduce_brightness` 两个键都**没人维护**
   （写了 `dumpsys` 不变，读它恒为 0 —— 以前「读 Secure、写 System」于是永远判「关」）。
   正确入口是 `color_display` 服务的 `setReduceBrightColorsActivated(boolean)`，但它要
   `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged）：宿主（`com.oplus.gesture`，
   `platform_app`）调会抛 `SecurityException: Access denied, requires: ...`，只能由 system_server 调
   （uid 1000 自带该权限）。LuckyTool 的「极暗」磁贴走的也是这个后台。

## 三十四、NFC 的「关」不会被记住，重启自己开回来（实测，2026-10-24，PLK110 / ColorOS 17.0.0）

用户口径：「侧键绑定切换 NFC，重启会自动打开 NFC」。

### 32.1 重启后的状态从哪儿来

**`/data/user_de/0/com.android.nfc/shared_prefs/NfcServicePrefs.xml` 里的 `nfc_on`。**

注意路径是 **`user_de`**（设备加密区，开机就有），不是 `/data/data/...` —— 后者里没有这个文件，
按老路径去找会得出「没有持久档」的错误结论。

A/B 实验（不用真重启，见 32.3）：

| `nfc_on` | 杀掉 NFC 进程后拉起的状态 |
| -------- | ------------------------- |
| `true`   | `Nfc is enabled`          |
| `false`  | `Nfc is disabled`         |

### 32.2 「开」会写，「关」不写

同一份文件、同一段时间里的 mtime 与取值：

| 操作 | `nfc_on` | 文件 mtime |
| ---- | -------- | ---------- |
| `cmd nfc enable-nfc` | `true` | **变了** |
| `cmd nfc disable-nfc` | 仍 `true` | 没变 |
| `cmd nfc disable-nfc persist` | 仍 `true` | 没变 |

也就是说这个 ROM 上 **只有「开」会落进持久档**，「关」不管带不带 `persist` 都不写。
所以关掉之后一重启，NfcService 读回 `nfc_on=true`，NFC 自己就开回来了 —— 与模块传什么无关，
系统自己的控制中心磁贴也一样（它走的是同一条 `disable`）。

模块这一侧仍然把 `persist` 改成了 `true`（`NfcToggle.fire`，与 `SettingCalls.bluetooth`
那条同一个坑）：语义上本来就该传 true，而且 `cmd nfc disable-nfc persist` 是**ROM 自己的 shell
命令**，它有没有把 `persist` 传下去是另一件事 —— 模块走的是 binder 直调，值得在真机上再验一次
（验法见 32.3，改完要**整机重启**才生效，注入侧只在 system_server 启动时加载）。

### 32.3 「重启才复现」的问题不用真重启

```bash
adb shell "su -c 'kill -9 \$(pidof com.android.nfc)'"
```

`com.android.nfc` 是常驻服务进程，被 kill 之后由系统重新拉起，**读的就是开机时读的那份持久档**
—— 与真重启的差别只在「别的服务还活着」。上表那两组 A/B 就是这么做的。

同一招对 `com.android.bluetooth` / `com.android.phone` 这类常驻进程都成立。
