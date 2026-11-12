# 音量与响铃

## 12.2 `AudioService` 的两条入口（`/system/framework/services.jar` → `classes.dex`）

```java
// AudioService.java:8108
public void setRingerModeExternal(int ringerMode, String caller) {      // ← AudioManager.setRingerMode 走这条
    logDiscouragedApiUsage(...);
    if (mHardeningEnforcer.blockVolumeMethod(200, pkg, uid)) return;
    if (isAndroidNPlus(caller) && wouldToggleZenMode(ringerMode)
            && !mNm.isNotificationPolicyAccessGrantedForPackage(caller)) {
        throw new SecurityException("Not allowed to change Do Not Disturb state");
    }
    setRingerMode(ringerMode, caller, true);                            // 第 3 参 = external
}

// AudioService.java:8126
public void setRingerModeInternal(int ringerMode, String caller) {      // ← SystemUI 走这条
    enforceVolumeController("setRingerModeInternal");
    setRingerMode(ringerMode, caller, false);
}
```

`setRingerMode(int, String, boolean)` 本体（`AudioService.java:8120` 起）**按 `external` 分两支**：

| `external`          | 问不问 delegate                                                                    | 日志前缀                                                                    |
| ------------------- | ---------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `true`（公共 API）  | **问** —— `mRingerModeDelegate.onSetRingerModeExternal(...)`，**返回值当最终档位** | `setRingerMode external to <请求值>, …, delegateModified=<delegate 返回值>` |
| `false`（内部通道） | 也调 `onSetRingerModeInternal(...)`，但 ColorOS 的 delegate 在这支里**原样返回**   | `setRingerMode internal to <请求值>, …, delegateModified=<同上>`            |

⇒ **ColorOS 的 ringer
delegate 只改写「外部」请求**：把静音改写成振动。（静音档在这台 ROM 上由音量面板 / 三段式开关独占。）

## 12.3 内部入口的门槛：`STATUS_BAR_SERVICE` —— 宿主刚好有

```java
// AudioService.java:18158
private void enforceVolumeController(String action) {
    mContext.enforceCallingOrSelfPermission(
        "android.permission.STATUS_BAR_SERVICE", "Only SystemUI can " + action);
}
```

```
android.permission.STATUS_BAR_SERVICE: granted=true      ← 与 SystemUI 同款
android.permission.STATUS_BAR: granted=true
```

⇒ 宿主调 `setRingerModeInternal`
**过得去**，本动作因此**不需要**再绕注入侧（与 11.1 的「通知栏 / 最近任务」不同 —— 那两条宿主确实没有权限）。

## 12.4 客户端方法存在，签名是 `(I)V`

`/system/framework/framework.jar` → `classes3.dex`：

```java
// AudioManager.java:8834
public void setRingerModeInternal(int ringerMode) {
    getService().setRingerModeInternal(ringerMode, getContext().getOpPackageName());
}
```

`@hide` ⇒ 只能反射调（宿主进程里隐藏 API 反射可用，同 `bridge/host/KeyInjector` 调 `InputManager.injectInputEvent`
那条先例）。

| 做法                                                        | 结果                                                                                       |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| 宿主 uid(10277) 调公共 `setRingerMode(SILENT)`              | 被 delegate 改写成 **VIBRATE**（`delegateModified=1`）                                     |
| 宿主 uid(10277) 跑 `cmd audio set-ringer-mode SILENT`       | **挂死**（`timeout` 退出码 124；ringer 历史里一条记录都没有）；`cmd audio help` 同样零输出 |
| shell(2000) / root(0) 跑 `cmd audio set-ringer-mode SILENT` | `mode=SILENT`、`zen=ZEN_MODE_OFF`                                                          |
| `settings put global mode_ringer 0`                         | AudioService 不采纳（档位不变）                                                            |

⇒ 「宿主身份跑 `cmd` 不行、shell/root 行」这条也解释了 `ShellRunner` 注释里那句「`cmd audio set-ringer-mode`
以 App 身份跑会挂住」。

## 12.6 与勿扰（zen）**没有关系**

- 与 zen 的联动（`wouldToggleZenMode()`）**只在 `external` 那一支**；内部通道整支不经过。
- 所以旧版「切完档把勿扰压回关闭」（`ensureZenOff`）是**给公共 API 那条路准备的**；是
  `null`，容易让人以为非得授权），也随之一并删掉。
- **勘误（2026-10-13 真机反馈）**：「与 zen 无关」只对 SecurityException 那道门成立，对「切档是否生效」**不成立**。zen 开启时 AudioService 把**外部档位视图**压成静音（`getRingerMode()`
  恒读静音），内部档位另算；旧代码按外部视图推「下一档」，每次都读出静音 → 每次都写「响铃」，表现为「响铃状态命令怎么按都切不动」。内部通道的写入本身
  **不被 zen 拦、也不联动关 zen**。修法（用户定案「不要关免打扰」）：zen 开启时改读内部档位
  `AudioService.getRingerModeInternal()`（`AudioManager.getService()` hidden 静态 →
  binder，反射），循环照常走内部通道写内部档位；公共接口那条降级在 zen 下切响铃/振动会触发 zen 联动检查（`wouldToggleZenMode`），宿主没有「勿扰访问」授权必被
  `SecurityException: Not allowed to change Do Not Disturb state` 拒 —— 留着做兜底，但别指望它。

---

## 二十七、响铃档位切换那一下振动的效果号（反编译 SystemUI + oplus-services，2026-10-20，ColorOS 17）

**三条路，三张表，不要互相推**：Action Button 侧键、三段式滑块、控制中心开关各走各的。
弄混过一次：把滑块的 `setDown`（静音 `308`）当成侧键，于是模块切静音放得比系统重得多。

**一、Action Button 侧键**（模块跟的是这条；`oplus-services.jar` → `classes.dex`，
`com.android.server.audio.AudioServiceExtImpl.vibrateMM`）

```
侧键按键 → 改档 → AudioServiceExtImpl.vibrateMM(新档位, caller)
  effectId = (新档位 == 2) ? 308 : (新档位 == 1) ? 430 : 0      // 0 = 静音档
  OplusFeatureConfigManager.hasFeature("oplus.software.vibrator_lmvibrator")
    → LinearmotorVibrator.createWaveformEffect(effectId, -1, -1)
      → LinearmotorVibrator.vibrate(Process.myUid(), ctx.getOpPackageName(), effect, tag,
                                    VibrationAttributes.createForUsage(18))
```

| 档位              | 传给 `createWaveformEffect` | 真机 dumpsys 落到                                    | 听感           |
| ----------------- | --------------------------- | ---------------------------------------------------- | -------------- |
| `NORMAL`（响铃）  | 308                         | `mEffectId=308, mDuration=330, mEffectStrength=2000` | 低沉那一下     |
| `VIBRATE`（振动） | 430                         | `mEffectId=430, mDuration=260, mEffectStrength=2000` | 两下           |
| `SILENT`（静音）  | **0**                       | `mEffectId=1, mDuration=45, mEffectStrength=2400`    | **很轻的一下** |

静音那档的 `0` 是入口值、不是「不振」——`createWaveformEffect` 把它解析成 prebaked 1 号（45ms）。
**强度靠那两个 `-1` 与 attributes 落到默认档**（2000）；换用 `vibrate(WaveformEffect)` 这条不带
attributes 的重载会落到最大档（2400），同号听感就比系统重 -- 模块一开始就是这么写的，用户当场听出来了。

**二、三段式滑块**（`com.android.server.audio.AlertSliderVibratorPolicy`，同一个 dex）

| 滑块位置     | 方法                               | 效果号  | 备注                                                                         |
| ------------ | ---------------------------------- | ------- | ---------------------------------------------------------------------------- |
| 中档（振动） | `setMiddle` → `vibrateIdForMiddle` | **430** | `mIsNewSliderVibratorSupport` 才用 430，否则 69；新滑块那条 170ms 后再放一下 |
| 底档（静音） | `setDown`                          | **308** | `createWaveformEffect(308, -1, -1)`；无线性马达时 `createOneShot(260ms)`     |
| 顶档（响铃） | ——                                 | 不振    | 这个策略类里没有对应方法                                                     |

`setDown` 前面有一道闸：`Settings.System "vibrate_when_ringing" == 1`（默认 0）。
**滑块与侧键不是同一条**：滑块静音是 308、侧键静音是 0，别拿一个推另一个。

**三、控制中心开关**（`com.android.systemui` 的 dex，`/system_ext/priv-app/SystemUI/SystemUI.apk`，
9 个 dex；不在 `SystemUIPlugin` 里 —— 后者一个 `Ringer` 字样都没有）

```
com.oplus.systemui.qs.tiles.RingerModeTile.handleClick(Expandable)
  → audioManager.setRingerModeInternal(新档位)
  → OplusRMChangeVibrateHelper.vibrateOnRMChanged(新档位, TAG)     // 类在 classes4.dex
      → OplusVibrateHelper.vibrate(tag, linearMotorEffectId, strength, nomalEffectId)
```

`vibrateOnRMChanged` 里是「`== 1` 取 430、`== 2` 取 308、其余置 0 后直接 return」——
**静音档这条直接 return，不振**。

**这三张表是拿真机纠正过的，别按 smali 偏移硬读**：第一版按偏移读成「静音 430、振动 308」，
真机表现为「切到振动是普通一下、切到静音反而是两下」；第二版又按 `handleClick` 里那条
`vibrateOnRMChanged(0)` 推断「响铃不振」，真机表现为「切到响铃没动静，而控制中心有」。
`const/16` 占两个 code unit、`invoke-static` 占三个，数错一位结论就整个反过来。

**dumpsys 怎么用**：`Recent vibrations` 的 `played: OplusPrebakedSegment{mEffectId=…, mDuration=…,
mEffectStrength=…}` 加上 `usage` / uid 一栏，能分清「谁放的、哪个号、多强」。档位读不到时**按时序对齐**：
一次只操作一下、把 `mEffectId` 与刚才切到哪一档对上（`settings get global mode_ringer` 比内部档位
慢一拍，别拿它当时间轴）。

**再往下怎么振**（`OplusVibrateHelper`，classes3.dex）

```
vibrate$default → vibrate(tag, linearMotorEffectId, strength = -1, nomalEffectId = 5)
  FeatureOption.isLinearVibratorSupport() == true
      → LinearmotorVibrator lmv = context.getSystemService("linearmotor")
         （类型 com.oplus.os.LinearmotorVibrator，服务名就是字面量 "linearmotor"）
        前面还有两道闸：lmv != null、HapticSettingObserver.mSystemHapticEnabled
        new WaveformEffect.Builder().setAsynchronous(true).setEffectType(效果号).build()
        lmv.vibrate(effect)
  else
      → Vibrator.vibrate(VibrationEffect.get(5), sonification 的 AudioAttributes)
```

`strength < 0` 时**不**走 `setStrengthSettingEnabled/setEffectStrength` 那条（那是 luxun 马达的分支）。

**`LinearmotorVibrator` 有两条放法，强度不一样**（`oplus-framework.jar` 的方法表）：

| 调用                                                                                       | 强度落到           | 用它的地方                                                                 |
| ------------------------------------------------------------------------------------------ | ------------------ | -------------------------------------------------------------------------- |
| `vibrate(WaveformEffect)`                                                                  | **2400**（最大档） | `OplusVibrateHelper`（控制中心那条）                                       |
| `createWaveformEffect(号, -1, -1)` + `vibrate(uid, 包名, effect, tag, createForUsage(18))` | **2000**（默认档） | `AudioServiceExtImpl.vibrateMM`、`AlertSliderVibratorPolicy`（侧键与滑块） |

`createWaveformEffect` 是 `(int effectId, int strength, int duration)`，两个 `-1` = 让服务端取默认值。

**从模块里怎么复现**：`com.oplus.os.WaveformEffect` / `LinearmotorVibrator` 在 boot classpath 的
oplus-framework 里，不是 hidden API，普通进程反射就能用；SystemUI 那两个类反而加载不到。
模块走的是**第二行**（与侧键同形），实现见 `app/src/main/java/com/lookie/opluskey/bridge/host/RingerHaptics.kt`。

**以后核这类「手感对不对」不用猜**：`dumpsys vibrator_manager` 的 `Recent vibrations` 会逐条列出
`OplusPrebakedSegment{mEffectId=…, mWaveformId=…, mDuration=…, mEffectStrength=…}` 以及发起方
uid / usage —— 模块和系统各自放的是哪个号、多强，一比就知道。

## 三十三、把音量设成某个值只有 `cmd audio set-volume` 这一条路（实测，2026-10-24，PLK110 / ColorOS 17.0.0）

用户想用「自定义命令」把音量直接设成 0（不是按一下减一格）。真机上把三条候选逐个试了一遍：

| 写法 | 退出码 | 结果 |
| ---- | ------ | ---- |
| `media volume --stream 3 --set 0` | 127 | `media: inaccessible or not found` —— 这条老命令在 ColorOS 17 上**已经没了** |
| `cmd media_session volume --stream 3 --set 0` | 0 | **报成功、音量没变**（`get-stream-volume 3` 仍是原值）—— 它走 MediaSession，没有活动会话时静默空转 |
| `cmd audio set-volume 3 0` | 0 | 生效，`get-stream-volume 3` 当场读回 0 |

所以自定义命令写 `shell:cmd audio set-volume 3 0`（`shell:` 前缀不能省，见 `Action.parse`）。
流编号与 `AudioManager.STREAM_*` 一致：`0` 通话 / `1` 系统 / `2` 铃声 / `3` 媒体 / `4` 闹钟 /
`5` 通知。第三条参数是**档位下标**（这台机器 STREAM_MUSIC 是 `0..160`，不是 `0..15`），
`0` 即静音；`cmd audio adj-volume 3 LOWER` 才是「减一格」。

`cmd audio` 的副作用是**不弹音量条**（`setStreamVolume(…, flags=0)`），正是按键场景想要的。

### 31.1 要弹音量条：`--show` 不管用，只能再补一次音量键

| 写法 | 音量条 |
| ---- | ------ |
| `cmd media_session volume --stream 3 --set 0 --show` | **不弹**（`--show` 传下去的 `FLAG_SHOW_UI` 在 ColorOS 17 上被吞了，截图确认屏幕上什么都没有） |
| `cmd audio set-volume 3 0` | 不弹（flags=0） |
| `cmd audio set-volume 3 0 && input keyevent 25` | **弹**，且停在 0 |

所以「设 0 且让用户看见」写成一条：

```
shell:cmd audio set-volume 3 0 && input keyevent 25
```

`input keyevent 25` 在音量已经是最小时**不会再改值**，只把 SystemUI 那条竖着的音量条叫出来（截图确认：右侧一条半透明竖胶囊，底部是划掉的喇叭图标）。

两点注意：

- **顺序必须靠 `&&` 串在同一条 shell 里**，不能拆成「多条命令」的两步：多条命令在 `stepDelayMs = 0` 时是把两步**同步派发**的，而 `shell:` 那一步是扔进线程池 fork/exec `su`（首次还要等授权弹窗），音量键很可能先跑完 —— 弹出来的是**旧音量**的条，随后音量才掉到 0。
- `input` 由 `su` 跑得起来（`rc=0`，root 过 `INJECT_EVENTS`）。它每次要起一个 `app_process`（约几百毫秒），可接受。

顺带量到的一条：这台机器 STREAM_MUSIC 是 `0..160`，**一次音量键走 10 格**（实测 `40 → 30`），
也就是界面上那 16 级。所以「按一下减一格」在数值上是减 10，`--set` 用的才是原始下标。

## 三十四、宿主进程的 `setStreamVolume` 被整条丢弃，只有注入音量键能改（实测，2026-11-11，PLK110 / ColorOS 17.0.1）

同一个动作 `volume:-10`（目标 41，当前 57）走两条路，读数分别是：

```
音量：volume:-10 → 档 41/160（当前 57）
音量：这次没写进去（flags=1，写 41，读回 57；固定音量=false，静音=false）
音量：这次没写进去（flags=0，写 41，读回 57；固定音量=false，静音=false）
```

- **带 `FLAG_SHOW_UI` 与不带，结果一样**：写下去、读回来还是旧值，不抛异常、没有任何回执。
  所以「写完读回」是这条路上唯一的判据 —— 调用成功不代表音量变了。
- **不是多应用音量**：`settings put system multi_app_volume_switch 0` 关掉之后照样如此。
- **不是前后台**：宿主在模块页里（前台）同样写不进去。
- **`getStreamVolume` 与 `dumpsys audio` 的口径会对不上**：同一时刻 `dumpsys` 里
  `streamVolume` 与 `Current: 2 (speaker)` 是两个不同的值，排查时别拿后者当「当前音量」判据。
- **只有注入音量键能落下去**：宿主用 `InputManager.injectInputEvent` 注入
  `KEYCODE_VOLUME_UP` / `VOLUME_DOWN`（虚拟键盘，`deviceId = -1`）之后音量才真的变 ——
  这条路与物理键完全等价，`isSynthetic` 会把它放行给系统，不会被模块自己再认领一次。
- 注入的算法是**一颗一颗按、每颗按完读回**（`VolumeAction.injectUntilReached`）：一颗走几格、
  还剩多少，全按实测差值现算，连注入被吞掉一颗也只会让下一轮多补一颗。**不要**退回「按三十三节
  那个 10 格折算一次算好次数再连发」—— 那颗键的档距只是当时的实测值，而且连发时落地颗数不受
  我们控制，一旦少落一颗就整段偏掉（步进比设的小、要按十几次才到顶）。
- 方向在进这条路时定死，**越过目标就停、绝不反向补一颗**：一颗键是 10 格（6.25%），`±1%`
  这种比一格还小的步进按下去必然越过，反补一颗就正好回原处 —— 表现是「减了又被弹回来」。
- 比一颗键还小的步进**一颗都不按**（`±1%` 走成 6.25% 就是这条路硬按出来的），只把差值带回去
  攒着，够一整颗了再真的动；差额两个方向都带（没走够 / 走过头），欠账上限一颗键。
- 直写被丢掉之前先隔 40ms **静默重写两次**：丢掉不是每次都一样（面板在场时更容易），
  重试能落地就还是精确档位 —— 精确档位只可能来自直写，注入这条路永远只有整格。

## 三十五、精确档位改由注入侧设：`system_server` 的身份不被那条策略挡（2026-11-11）

三十四那一节的结论是「宿主进程写不进去」，那么宿主任内唯一能给精确档位的路就断了，只剩注入音量键
（一格 = 量程的 1/16）。`±1%` 这种细步进于是只有两种结果：硬按一下走 6.25%，或者一下都不动。

改成**请注入侧替宿主设**：宿主发一条定向广播（`ModuleWire.ACTION_SET_VOLUME`，带流与下标），
注入侧在 `system_server` 里用 `ActivityThread.getSystemContext()` 拿 `AudioManager`，
`setStreamVolume(stream, index, FLAG_SHOW_UI)`。身份是系统本身，所以那条把宿主写丢掉的东西对
它不生效；`FLAG_SHOW_UI` 与宿主自己写时同一个口径，改完照样弹那一路流的音量条。

- 收方与「截图注入」「替宿主弹提示」同一条通道、同一个入口（`inject/KeyHooks` 的 AMS 广播入口），
  来源按宿主 uid 校验；发送侧 `setPackage(宿主包名)` 定向。
- **这不是把执行器往回挪**（五之二、6）：整条动作链仍在宿主，只有「宿主自己做不到的那一件事」
  交给注入侧做 —— 与截图那条先例同一个形状。
- 落地判据是**宿主读回**：注入侧未装 / 是老版本不认识这条广播时，读回不变，宿主自动退到下一档
  （注入音量键），不会卡在中途。
- `AudioManager` 是平台公开类，不属于 ROM 私有类型（红线 1 不管它）；整段兜 `Throwable`，
  跑在 worker 线程上（`postGuarded`），不会把异常漏回 AMS 的广播路径。

## 三十六、档位没变的那一档：读回判不出「写成功」还是「被丢掉」（用户机反馈，2026-11-12）

现象：**音量已经是 0** 时再执行「设为 0」（`volume:0`）或「减少」（`volume:-10`），
**模块页不在前台**时屏幕上什么都不弹（前台时能弹）。

原因在判据本身，不在写入那条路：

- 这一档 `target == current`，写进去值也不会变 ⇒ 宿主那次写被 ColorOS 丢掉之后，
  读回**照样**等于目标值（§34 唯一能用的判据在这里退化成恒真）；
- 于是 `VolumeAction` 把这一次当成「已经落下去」，根本不会退到注入侧那一档 ——
  而真机上能弹面板的正是注入侧（系统身份 + `FLAG_SHOW_UI`，§35）；
- 前台之所以看得见，是因为宿主那次写没被丢（值虽不变，但 `FLAG_SHOW_UI` 照样把面板发了出去）。

修法（第一版，不够）：`VolumeAction.Job.write` 在 `target == current` 时**不看读回**，走 `showOnly(target)`，
宿主自己那次带 UI 的写与注入侧的请求**两条都发**。用户机复测仍然不弹 ⇒ 说明 `setStreamVolume`
在「值不变」这一档上**根本不发面板**（宿主与注入侧两边都一样），瓶颈不在身份，而在**用了哪个 API**。
真正的修法见 §37：注入侧改用 `adjustStreamVolume(ADJUST_SAME, FLAG_SHOW_UI)` 去叫面板。

判据行（`w`）：`媒体音量：档位没变（0）⇒ 已请注入侧把音量条叫出来`。

**不要**把这一档改成「注入一颗音量键把面板叫出来」：值在边界上（0 / 满格）时才不会变，
中间档位上一颗键就是 1/16 量程，用户要的是「设为 50」，不该被按到 56。

## 三十七、音量写入改由注入侧执行；值不变那一档用 `adjustStreamVolume` 叫面板（2026-11-12）

用户口径：宿主执行音量一直有问题，**改成在注入侧执行**。落下来的结构：

```
宿主 VolumeAction（只算目标档位 / 零头 / 兜底）
  └─ ① 注入侧 setStreamVolume(stream, index, FLAG_SHOW_UI)      ← 正常路径，唯一能写进去的
     └─ index == 当前 ⇒ adjustStreamVolume(stream, ADJUST_SAME, FLAG_SHOW_UI)  ← 只叫面板，不改值
  └─ ② 宿主自己那几档写（带 UI → 静默 → 静默重试）              ← 只在注入侧没接上时兜底
  └─ ③ 注入音量键（媒体流）                                      ← 最后兜底，粒度一颗键
```

- **②③ 不再是正常路径**：宿主那次写被 ColorOS 整条丢掉（§34），「写完读回」判不出「成功」与
  「被丢」，所以正常路径必须是 ①。
- **值不变那一档只能用 `adjustStreamVolume`**：`setStreamVolume` 同值不发面板 —— 这一点宿主与
  `system_server` 两边实测一致（§36 的第一版就是按 `setStreamVolume` 改的，用户机复测不弹）。
  而物理音量键按到底时值同样不变、面板照样弹，走的就是 adjust 那条路
  （§31.1 的 `input keyevent 25` 同一条链路）。`ADJUST_SAME` 不改值；面板是**当前活跃流**那一条。
- 判据行：`注入侧设音量：路 3 已经是 0 ⇒ 值不动，只把音量条叫出来`（`w`）；
  值真的变了那条仍是 `注入侧设音量：路 3 40 → 0（目标 0）`。
- **这是「执行器」上的一处登记例外**（AGENTS 五之二、6 的通则是执行留在宿主）：理由与本文件 §35
  同一条 —— 宿主做不到的那一件事才交给注入侧，而音量写入从 §34 起就属于「宿主做不到」。
