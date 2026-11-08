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
