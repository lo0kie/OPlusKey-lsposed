# 三十八、亮度百分比 = `screen_brightness` 在量程上的位置；`get-brightness --unit percentage` 报的是另一套 HLG 值（实测，2026-10-29，PLK110 / ColorOS 17）

「亮度百分比」有**两个不同的数**，混用就会「加 10% 加了一大截」：

| 数 | 怎么取 | 本机在 raw 2037 时 |
| --- | --- | --- |
| **亮度条的位置**（用户看到的那把尺子） | `(raw − min) / (max − min)` | **50%** |
| 框架报的感知百分比 | `cmd display get-brightness --unit percentage` | **87%** |

**判据（量一次就够）**：让亮度条拖到**大约中间**，读 `settings get system screen_brightness`
—— 实测写进去的是 **2037**，正好是 `2..4095` 的一半 ⇒ 条的位置就是量程的线性刻度。
`get-brightness --unit percentage` 在同一时刻报 87%，**它不是条上的位置**。

**量程**

- `settings get system screen_brightness_range` = `"2,4095"`（与 `screen_brightness` 同量纲）；
- `dumpsys display` 里 `mScreenBrightnessRangeMinimum=2.0`、`mScreenBrightnessNormalMaximum=4095.0`
  —— `mScreenBrightnessRangeMaximum=4674.0` 是激发态那一档，普通写值用不到；
- 框架资源 `config_screenBrightnessSettingMaximum` 是 **AOSP 默认的 255**，与本机量程差一个数量级，
  `config_screenBrightnessNits` / `config_screenBrightnessBacklight` 两个数组在这台机器上是**空的**；
- 内核 `/sys/class/backlight/panel0-backlight/max_brightness` = 4094，与 raw 同一把尺子。

**框架那个感知百分比是什么**（逐点 `settings put` 后读 `cmd display get-brightness --unit percentage`）

| raw | 报的 % | raw | 报的 % |
| --- | --- | --- | --- |
| 2 | 0.00 | 500 | 58.88 |
| 50 | 18.76 | 800 | 68.87 |
| 100 | 26.80 | 1200 | 76.95 |
| 200 | 38.10 | 2037 | 87.06 |
| 300 | 46.74 | 3000 | 94.27 |
| 2500 | 90.89 | 4095 | 100.00 |

这条曲线是 **HLG 的 OETF**（`x` = 原始值在量程上的比例、`g` = 报的百分比 / 100）：

```
x ≤ 1/12 :  g = √(3x)
x > 1/12 :  g = a·ln(12x − b) + c      a = 0.17883277  b = 0.28466892  c = 0.55991073
```

三个常数与 AOSP `BrightnessUtils` 同值，逐点误差小于 0.01 个百分点。它只是**框架对外的感知读数**
（`get-brightness` 用），亮度条并不按它画 —— 按它算 `+10%`，条上会走三十多格。

**面板的真实亮度**（`logcat` 里 `CasManager: DBV changed …` 与 `OplusGammaRemappingDbv2Nit`）

- 背光 DBV 与 raw 基本 1:1：raw 4000 → DBV 3413~3515，raw 250 → DBV 182；
- DBV→nit 是 **gamma 2.2**：DBV 3515 = 800 nit，所以 DBV 182 ≈ **1.5 nit**。
  低亮度处屏幕本来就很暗，那是面板行为，不是写错了值。

**结论**

- 要按「亮度条上的位置」换算，就得在**量程**上算：`+10%` = 条上走 10 格、`−10%` = 退 10 格、
  `设为 X%` = 落到条上的 X% 位置。
- **别用 `cmd display get-brightness --unit percentage` 当尺子**（踩过：按它算 `+10%` 在条上走了
  35 格，真机口径「加了一大截」）。它只用来查「框架的感知百分比」，不是条的位置。
- `real_screen_brightness`（本机一直是 2403）**不驱动显示**：写它不改 `Display Brightness`；
  SystemUI 与 Settings 的 dex 里也都只有 `screen_brightness`，没有这个键 —— 它不是亮度 UI 的输入。
- 核对一次不用重启、也不用界面：拖条到中间读 `screen_brightness`（≈ 2048），
  或 `settings put` 几个点看 `Display Brightness` 是否跟上。

**这一族动作已整条移除**（2026-10-29，用户口径）：自动亮度开着时 `screen_brightness` 会被 ROM
持续改写，做不到稳定的「加 10%」。上面这些结论仍然有效，留给以后重做时用。

## 38.1 自动亮度开着时，写 `screen_brightness` 会被 ROM 改掉（实测，2026-10-29）

- 自动亮度开着（`screen_brightness_mode = 1`）时，`DisplayPowerController` 走的是
  `AutomaticBrightnessStrategy`，`dumpsys display` 里能看到
  `OplusAutomaticBrightnessController`、`mAutoStrategyEnable`、`BRIGHTNESS_DRAG_RESET_INTERVAL`
  这些状态。
- 往 `screen_brightness` 写一个值之后，**约 1 秒**会被它改成自己算的值：
  `DpcExtImpl: Animating brightness: target=248.0->3983.0`，随后 `OplusBrightnessAmbientAdapter`
  再动一次。真机现象就是「设为 6% 直接最高亮度」。
- **用 shell（uid 2000）写同一个值不会被改** —— 所以区分的是「谁写的」，不是值本身。
- `DisplayManager.setBrightness(displayId, value, unit)` 两条路都走不通：
  `unit = 0` → `IllegalArgumentException`；`unit = BRIGHTNESS_UNIT_PERCENTAGE` → `SecurityException`
  （宿主有 `DEVICE_POWER` 也不够）。
- 宿主进程读 `Settings.System` 的值**有几秒延迟**（同一时刻 shell 读到 1589、宿主读到 249），
  所以「按当前值算目标值」这一步本身就可能基于旧值。

## 38.2 小布助手怎么读写亮度百分比（反编译 `com.heytap.speechassist` 13.3.5 + 真机，2026-11-04，PLK110 / ColorOS 17）

指令入口 `skill/device/operation/BrightnessOperation`：`process()` 按 header 分派
`SystemScreen.AdjustBrightness` → `adjustBrightness()`、`SystemScreen.SwitchAutoAdjustBrightness` → `setAutoBrightness()`。

**载荷**：`AdjustBrightness{action, value, type}` 三个字符串；`action` 只有 `Add` / `Reduce` / `Fixed` /
`Max` / `Min`，`value` 是**整数百分比**（`Add` / `Reduce` 缺省 10），`type` 不参与计算。

**尺子**（`j40.j`）：`min = g() = 1`、`max = e()`、`changeSize = max − min`；本机 `max = 4095` ⇒ `changeSize = 4094`。

- `Fixed`：`v = pct*changeSize/100`；
- `Add` / `Reduce`：`v = 当前值 ± pct*changeSize/100`；
- `Max` / `Min`：`v = changeSize` / `v = 0`；
- 结果夹到 `[0, changeSize]`（`clampBrightnessValue`），写入时再 `+min`。

**读**：`j40.j.b()` 只读 `Settings.System` 的 `screen_brightness`（multibits 机型走 `getString` 再 `parseInt`，
否则 `getInt`）；不读 `screen_brightness_float`（本机为 null），也不用 `cmd display get-brightness`。
回复里的百分比是 `v*100/changeSize`（整数除法）。

**写**（`j40.j.r(v)`，顺序固定）：

1. `raw = v + 1`；
2. `adj = raw*(realMax+1)/(max+1)`（整数除法；本机 `realMax` 与 `max` 同为 4095 ⇒ `adj = raw`）；
3. `q()` 为真：先 `OplusosDisplayManager.setTemporaryBrightness(0, (int)adj)` +
   `setTemporaryAutoBrightnessAdjustment(adj)`；
4. `screen_brightness_mode == 1`：`q()` 真 → `putFloat("screen_auto_brightness_adj", adj)`；
   否则（ColorOS）→ 把 `screen_brightness_mode` 写成 0（等于关掉自动亮度）；
5. 最后写 `screen_brightness = raw`（multibits 走 `putString`，否则 `putInt`）。

**本机这组值怎么来**：`min` 恒为 1；`max` 先看 `sys.oppo.multibrightness`（未设）→ `sys.oplus.multibrightness`
（=4095），再由 `oplus.software.display.multibits_dimming_support` 决定是否采用；`q()` 看
`oplus.software.display.autobrightctl_animation_support`。两个特性都在
`/my_product/etc/permissions/oplus.product.display_features.xml` 里；`sec_max_brightness_rm` 不在，
所以 600 nit 那一档限幅不生效。

**真机核对**（自动亮度开着，`screen_brightness_mode=1`）：

```
settings put system screen_auto_brightness_adj 1800.0
# 10ms 后 DpcExtImpl: Animating brightness: target=1242.0->1800.0 …, byUser=false
# Display Brightness=1800.0，20 秒以上不回退
settings put system screen_brightness 1500      # Display Brightness 仍是 1800.0，没动
```

⇒ 自动亮度下 **`screen_auto_brightness_adj` 才是目标亮度**，单位就是 raw（不是 -1..1 的偏移）；
`screen_brightness` 在自动模式下不驱动显示，它存的是「关掉自动后要回到的值」。只写 `screen_brightness`
的现象就是「写了没反应」，不是值算错了。

**三条边界**

- `screen_auto_brightness_adj` 在 `Settings.System.PRIVATE_SETTINGS` 里。`Settings.System.putFloat` 本身
  没有 float 键白名单（framework 里它就是 `putStringForUser(..., Float.toString(v), ...)`），拦的是
  SettingsProvider 的 `enforceRestrictedSystemSettingsMutationForCallingPackage`：`appId` 为 0 / 1000 /
  2000 直接放行，其余包要求是 **privileged**（`privateFlags & PRIVATE_FLAG_PRIVILEGED`）或在 OPPO 的
  白名单里，否则落到 `warnOrThrowForUndesiredSecureSettingsMutationForTargetSdk` —— `PRIVATE_SETTINGS`
  里的键抛 `IllegalArgumentException: You cannot change private secure settings.`
  真机实测：宿主 `com.oplus.gesture`（`SYSTEM_EXT`、**非** PRIVILEGED、装在 `/system_ext/app/`）写 adj
  抛这句；同一段代码写 `screen_brightness`（`PUBLIC_SETTINGS`）成功。小布 `com.heytap.speechassist`
  的 privateFlags 里有 `PRIVILEGED`（`/product/priv-app`）⇒ 它写得进去，不是用了什么特殊 API。
  `settings` CLI 走 shell（uid 2000）也不受限。
- 因此自动亮度那一档只有两条路：**走 root（uid 0）把两个键写下去**，或先关掉自动亮度再写
  `screen_brightness`。注入侧接不了这一棒 —— system_server 里没有可用的 `ContentResolver`（六之五）。
- SettingsProvider 的 `SettingsFilter` 只改写 `screen_off_timeout`，别的键原样放行 —— 写
  `screen_brightness` 不会被换值，也不会被丢（仍要 `WRITE_SETTINGS`）。

另有一组 `j40.j.c()` / `s(float)` 走 `screen_brightness_float`（`getFloatForUser` / `putFloatForUser`），
不属于指令那条路；本机该键为空。

## 38.3 控制中心亮度条为什么不动：它等的是 display 事件，不是写库（反编译 SystemUI + services，2026-11-04）

**现象**：自动亮度开着时按键改亮度，屏幕确实变了，但控制中心那根亮度条停在旧位置；把控制中心关掉
再拉开就对了。

**那根条的数据源**（`com.android.systemui.qs.deprecated.brightness.BrightnessController`）：

- 值：multibits 机型走 `OplusBrightnessControllerExImpl.getBrightness()` →
  `QsSettingsValueProxy.getBrightness()` → `Settings.System.getInt("screen_brightness")`；
- 刷新入口两条：
  1. `BrightnessController$1 implements DisplayTracker.Callback`：`onDisplayChanged(displayId)` →
     `post(mUpdateSliderRunnable)`；
  2. `BrightnessObserver extends ContentObserver`：`screen_brightness` / `screen_brightness_mode` /
     `screen_brightness_range` 三个 uri。

**`onDisplayChanged` 的上游**（`com.android.server.display.DisplayManagerService`）：

```
DisplayPowerController.saveBrightnessInfo(...) 返回 true（这块屏的背光真的变了）
  → mOnBrightnessChangeRunnable
  → DisplayManagerService.handleBrightnessChange(display)
  → sendDisplayEventsIfEnabledLocked(display, 128)      // DisplayManager 的 display 变化事件
  → DisplayManager.DisplayListener.onDisplayChanged
```

⇒ 事件只在「实际背光变了」时发。自动亮度下 `screen_brightness` 只是「关掉自动后回到的值」，
DPC 不拿它算背光（38.1 实测：写它背光不动）。所以只写 `screen_brightness`（连同 adj）时，
`saveBrightnessInfo` 不一定判成变化 ⇒ 没有 display 事件 ⇒ 条不动。关掉重开控制中心会重新读一次
settings，读到的正是写进去的新值 ⇒ 那一刻才对上（现象与这条链一致）。

**小布为什么能实时刷新**：它在写库**之前**先调
`OplusosDisplayManager.setTemporaryBrightness(0, (int) adj)` + `setTemporaryAutoBrightnessAdjustment(adj)`
（38.2 的第 3 步）。`DisplayPowerController.setTemporaryBrightness()` 把背光**强行推到目标值**，
`saveBrightnessInfo` 因此必然判成变化 ⇒ display 事件发出去 ⇒ 条跟着走；写库那两步只是把值存下来。

**权限账**：`IDisplayManager.setTemporaryBrightness` / `setBrightness` 都
`enforceCallingOrSelfPermission("android.permission.CONTROL_DISPLAY_BRIGHTNESS")`（signature|privileged）。
宿主 `com.oplus.gesture` 只有 `WRITE_SETTINGS` 与 `DEVICE_POWER` ⇒ 调不了；小布带 `PRIVILEGED` ⇒ 能调。
system_server（uid 1000）能调，模块已有跨进程桥（`StatusBarBridge` + `asSystem`）。

**现在的做法（2026-11-04 实现）**：宿主算好 raw 之后先发一条
`StatusBarBridge.CMD_BRIGHTNESS`（`brightness` extra = raw），注入侧 `BrightnessBridge.apply()`
在 system_server 里以自身身份（`asSystem`）调 `setTemporaryBrightness(0, raw)` +
`setTemporaryAutoBrightnessAdjustment(raw)`，然后再由宿主写库 —— 顺序与小布一致。

写库仍留在宿主：自动亮度下要写的 `screen_auto_brightness_adj` 是 `PRIVATE_SETTINGS`，宿主只能走
root；而 system_server 侧**没有可用的 `ContentResolver`**（六之五），写库这条它接不了。所以现在
是「注入侧负责让条刷新、宿主负责把值存下来」，root 只在自动亮度那一档用。

## 38.4 「有时候还是不更新」：自动亮度下 `screen_brightness` 的 onChange 被 SystemUI 丢掉（反编译 SystemUI + services + 真机，2026-11-04，PLK110 / ColorOS 17）

**38.3 那个修法只能保证「有时候」** —— display 事件本身不是必发的。两条路各有一道闸。

**闸一（SystemUI）**：`BrightnessObserver.onChange` 把活转给 `BrightnessController$$ExternalSyntheticLambda5`：

```java
mAutomatic = Settings.System.getIntForUser(cr, KEY_BRIGHTNESS_MODE, 0, userId) != 0;
if (SCREEN_BRIGHTNESS_DURATION_URI.equals(uri) && mAutomatic) {
    mUpdateSliderAnimationRunnable.run();          // 只有这个键能过
} else {
    if (mAutomatic && mIsScreenBrightnessAnimationSupport) return;   // 直接丢掉
    mUpdateSliderRunnable.run();
}
```

`mIsScreenBrightnessAnimationSupport = SystemProperties.getInt("ro.display.brightness.new.animation", 0) == 1`，
本机该 prop 就是 `1` ⇒ **自动亮度开着时，`screen_brightness` 自己的变化一律不刷新滑杆**
（`updateSlider` 本身是无条件的 `setValue`，问题不在值比较，而在根本走不到那儿）。

**闸二（DPC）**：`saveBrightnessInfo()` 判成「背光变了」之后还有一道：

```java
z = (mBrightnessReasonTemp.getReason() != 7
        || mAutomaticBrightnessStrategy.isTemporaryAutoBrightnessAdjustmentApplied());
...
if (zSaveBrightnessInfo && !z19) lambda$new$4();   // z == true 就不 post
```

⇒ 亮度原因不是 7、或「临时自动亮度调整已应用」时，即便背光真的变了也**不**发 display 通知。
`setTemporaryBrightness` 换来的那下推送因此是看状态碰运气的 —— 这就是「有时候动、有时候不动」。

**唯一稳定的口令是 `screen_brightness_duration`**。真机（PLK110）实测：

```
settings put system screen_brightness_duration "[2000,200,429740,2000,91.7,7]"
→ I/SystemUi--QuickSettings: onChange uri: content://settings/system/screen_brightness_duration mAutomatic: true
→ I/SystemUi--QuickSettings: mUpdateSliderAnimationRunnable sliderValue: 1398
     screenBrightnessDuration: 200.0 startValue: 2000.0 startTime: 429740.0
```

`sliderValue` = `getBrightness() − getBrightnessMin()`，也就是**当场重读 `screen_brightness`** ⇒
顺序必须是「先写值、再写 duration」，反了就会把旧值刷上去。

值格式（`getScreenBrightnessArray()` 逐字解析）：`[?, duration, startTime, startValue, ?, ?]`，
只有第 1 / 2 / 3 项被读走；系统自己写的是 6 项，例如 `[1530,4500,210196,1497,91.7,7]`。
`startTime` 用 `SystemClock.uptimeMillis()` 并且**要取在过去**：`(now − start) / duration > 1`
时滑杆一步落到目标位置；取在将来会停在 `startValue` 不动 —— 正好是要避免的那个现象。

`services.jar` 里搜 `screen_brightness_duration` 命中 0 次，只有 SystemUI 用（3 处）⇒ 写它没有别的副作用。

**现在的做法**：`BrightnessAction.nudgeSlider()` —— 值落库之后写一次
`screen_brightness_duration`（`[target, 200, now − 500, 旧值, 上次的速率/模式]`）。手动亮度那一档
也照写：那边本来就会因为 `screen_brightness` 的 onChange 刷新，多这一下只是再确认一次，无害。
