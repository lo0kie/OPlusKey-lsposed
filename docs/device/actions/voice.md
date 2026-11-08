# 语音、识屏与 AI 记忆

## 三十二、唤醒小布：ROM「长按电源键」的启动配方（反编译 `oplus-services.jar` + 真机核对，2026-10-24）

动作 `oplus:voice_assist` 走这一节。**界面上的名字是「小布助手」**（英文名 `Breeno`；收在
「小布 AI」那一格里，与「小布识屏 / 一键闪记 / 灵感语音」并列）；代码、日志与本节沿用
`voice_assist` /「唤醒小布」这两个旧称 —— 下面的取证是照当时那句日志写的，改名不改取值。取证材料是本机留存的
`.temp/oplus-services.jar`（`classes2.dex`），工具 `tools/dexdump.py`（androguard，指令级）。
**已真机核对**（PLK110 / ColorOS 17 / 小布助手 13.3.2）：配方本身对了，但 `start_type` 的取值
第一轮读错（见 30.5），现行实现用的是验过的那个值。

### 30.1 调用链（框架侧）

```
PhoneWindowManager 判出「长按电源」（interceptPowerKeyDown → 长按超时）
  → PhoneWindowManagerExtImpl：往 OplusSpeechHandler 发 MSG_POWER_LONG_PRESS_FOR_SPEECH（1011）
    → OplusSpeechHandler.handleMessage → startSpeech(0x80000000, eventTime)
      → 两参重载补一个 1024 → startSpeech(int, int, long)        // 见 30.2
```

`startSpeech(I I J)` 两个 int 的角色：`p1` 是调用侧带下来的标志（电源这条是 `0x80000000`），
`p2` 才是**来源**。`p2` 的具体数值**不要从框架的指令里反推** —— 第一轮就是这么读错的
（把 `hasMessages(1015)` 边上那个 `1024` 当成了来源值，见 30.5）。来源值以**语音助手自己的映射表**
为准，见 30.2。

### 30.2 `startSpeech` 的配方（逐条取值都来自指令）

```java
Intent it = new Intent();
it.addFlags(0x10000000);                                   // FLAG_ACTIVITY_NEW_TASK
it.putExtra("caller_package", "android");
it.putExtra("start_type", src);                              // 电源键 = 512，见 30.5
if (!keyguardOn) {                                          // → 服务那支
    it.setAction("heytap.intent.action.ACTIVATE_SPEECH_ASSIST");
    it.setComponent(new ComponentName(<pkg>, "com.heytap.speechassist.core.SpeechService"));
    mContext.startForegroundServiceAsUser(it, UserHandle.CURRENT);
} else {                                                    // → 锁屏那支
    it.setAction("heytap.speechassist.action.LOCK_SCREEN_UI");
    it.setComponent(new ComponentName(<pkg>,
        "com.heytap.speechassist.business.lockscreen.FloatSpeechActivity"));
    mContext.startActivityAsUser(it, UserHandle.CURRENT);
}
```

- `<pkg>` 是 `mBreenoPkg`：新包 `com.heytap.speechassist`，旧包 `com.oppo.speechassist`
  （`OLD_SPEECH_PACKAGE_NAME`）。运行期挑**装着的那个**，别写死。
- `start_type` 的取值以语音助手自己的映射函数为准（`Loq/a;->b(I)`，sparse-switch）：
  `64 assistant / 80 health_app / 96 conversation_sdk / 112 software_store / 128 title_service /
  144 set_entetr / 512 power_key / 1024 headset / 2048 mini_note / 32768 calendar / …`。
  **电源键那一档是 `512`**（模块用的就是它）。`1024` 是 `headset`（耳机唤醒），拿它起服务
  只有 FGS 起来、界面不出来 —— 真机上就是这么表现的第一版。
- 三道前置闸：`isInConfineMode()`（机内模式）、`isUserSetupComplete()`（未完成开机设置）为真时
  直接 `return`；`mShutdownByPowerEnabled`（「长按电源键」被设成关机菜单）为真时不走语音这一支，
  转去弹「去设置里开启」的引导页。模块**不复刻这三道闸**：它们是「当前设置想不想用语音」的判据，
  而用户在这里明确绑了一个「唤醒小布」的动作。
- 锁屏那支用 `startActivityAsUser`，在 system_server 里借来的 Context 上起界面必失败
  （`mAppThread` 恒 null，见 `../lsposed.md` 六之五）⇒ **模块没做锁屏那一支**，见 30.4。

### 30.3 为什么这一档不在宿主起（`oplus:` 里只有它走 system_server）

ROM 那一行是 `startForegroundServiceAsUser`，调用方是 **system_server 自己**（uid 1000）；
`ActivityManager.checkComponentPermission` 对 `ROOT_UID` / `SYSTEM_UID` 直接放行 —— 也就是说
**这条配方能成立，靠的是调用方身份**，而不是目标服务真的对第三方开放。宿主 `com.oplus.gesture`
是普通系统应用（uid 10xxx），既没有 `system` 的旁路，也没有证据表明它持有语音助手的签名级权限；
另外 `start_type` 边上那句 `caller_package=android` 也可能被目标侧用来核「谁叫的」。
所以：`oplus:` 的其它档（闪记 / 录音 / 识屏 / 录屏）照旧由宿主起，**只有这一档转给注入侧**
（`StatusBarBridge` 的 `voice_assist` 指令），在 system_server 里用 `Binder.clearCallingIdentity()`
之后的身份起 —— 与「通知中心 / 控制中心 / 电源菜单」同一条通道、同一个理由。

### 30.4 现状与没做的部分

- 已经做了：服务那一支（非锁屏），包名运行期在两个候选里挑装着的那个。
- 没做：锁屏那一支（理由见 30.2 末条）；`isUserSetupComplete` 之类的闸（理由见 30.2）。
- 需要 `system` 作用域；只有 `com.oplus.gesture` 作用域时这一格会**静默不生效**
  （与电源菜单 / 控制中心一致，横幅不会为此单独提示）。

### 30.5 `start_type` 的真机核对（2026-10-24，PLK110 / 小布助手 13.3.2）

第一版按框架指令里那个 `1024` 传下去，现象是：**该有的日志都有，界面没有。**

```
模块日志  (system) 已在本机 startForegroundService(com.heytap.speechassist/…SpeechService，start_type=1024)（唤醒小布）
服务表    * ServiceRecord{… com.heytap.speechassist/.core.SpeechService c:com.android.shell}
通知栏    StatusBarNotification(pkg=com.heytap.speechassist … channel=SpeechServiceForegroundNotification … FOREGROUND_SERVICE)
窗口      0 条含 speech 的窗口        ← 卡在这一步
```

也就是说**服务和 FGS 都起来了，语音界面没起来** —— 所以问题不在权限、不在通道、不在包名，在载荷。

定位方法：把同一条意图用 root 直接打，逐个取值验，**每轮先 `input keyevent 4` + `3` 把界面关掉**
（不关的话上一轮那个窗口会一直算数，第一轮就被这个假阳性骗过一次）：

```bash
adb shell su -c 'am start-foreground-service \
  -a heytap.intent.action.ACTIVATE_SPEECH_ASSIST \
  -n com.heytap.speechassist/com.heytap.speechassist.core.SpeechService \
  --es caller_package android --ei start_type <值>'
adb shell 'dumpsys window windows | grep -c speech'      # 0 = 没起界面
```

| `start_type` | `dumpsys window` 里含 `speech` 的窗口 | 结论 |
| ------------ | ------------------------------------ | ---- |
| `1024`       | 0                                    | 只有 FGS，无界面 |
| `512`        | 4（`Window{… u0 com.heytap.speechassist}` 拿到 `mCurrentFocus` / `mHoldScreenWindow`） | 小布界面到前台，可正常返回退出 |

交叉印证：语音助手自己的映射函数 `Loq/a;->b(I)`（sparse-switch）里
`512 = power_key`、`1024 = headset` —— 和实测完全一致。所以框架侧那个 `1024` 是
`MSG_POWER_LONG_PRESS_FOR_SPEECH` 那条消息号（`hasMessages(1015)` 附近那个常量），
**不是来源值**；正确来源值是 `512`。

教训（下次读 ROM 时省一轮）：框架侧的整数字面量只能证明「这里有个 1024」，**不能证明它就是语义**；
目标 APK 自己有一张映射表时，以那张表为准，再用真机一验就定死了。

顺带确认的一条：`SpeechService` 在语音助手的清单里**没有声明 `android:permission`**
（同一个 Service Resolver 表里 `KeepLiveService` / `PreloadService` 都带
`oplus.permission.OPLUS_COMPONENT_SAFE`），所以从宿主起大概率也能起。模块仍然留在 system_server 起
（理由见 30.3），但这条意味着「宿主起不通」并不是死路，真出问题时可以退到那条。

## 三十七、屏幕翻译（侧边栏那一项）的启动配方：起翻译包的前台服务（反编译 `com.coloros.smartsidebar` + 真机，2026-10-29，PLK110 / ColorOS 17）

侧边栏里的「屏幕翻译」不是一个界面，而是一条**前台服务**：`com.coloros.translate` 的
`ScreenTranslationService`（清单里带 `com.oplus.permission.safe.AI_APP`、special-use 前台类型，
`<meta-data name="support_screen_translation" value="1">`）。

**配方原文**（`com.coloros.smartsidebar` 的
`panelview/edgepanel/data/entrybeans/models/tools/GlobalTranslationTool.handle()`）：

```java
Intent i = new Intent("oplus.intent.action.GLOBAL_TRANSLATION");
i.setPackage(mValidPkg);                              // = com.coloros.translate
i.putExtra("extra_from_package", context.getPackageName());
context.startForegroundService(i);                    // 只捕获 ServiceNotFoundException，记一行日志
```

**要点**

- `extra_from_package` **只进统计**：服务里只把它塞进 `source_appname`，再按值映射来源
  （`com.coloros.smartsidebar` → `source=sidebar`、`com.coloros.colordirectservice` → `recog_screen`、
  `com.coloros.gallery3d` → `photo`），**不参与任何放行判断** —— 报真实调用方即可，不必冒充侧边栏；
- 权限 `com.oplus.permission.safe.AI_APP` 宿主持有（`dumpsys package com.oplus.gesture` 里
  `granted=true`），所以从宿主进程直接起就行，不用绕 system_server；
- **它是「起一次」不是「开关」**：退出走它自己胶囊上那颗关闭。`oplus.intent.action.GLOBAL_TRANSLATION_EXIT`
  那条广播在真机上发出去没反应（`result=0`、悬浮窗仍在），`am stop-service` 才真的收掉 ——
  所以宿主**不要**代它停（会话状态在它手里，硬停会把界面留在半截）。

**真机核对方式**（root 起一次即可，不需要重启）：

```
adb shell su -c 'am start-foreground-service -a oplus.intent.action.GLOBAL_TRANSLATION   -p com.coloros.translate --es extra_from_package com.coloros.smartsidebar'
# dumpsys window windows | grep ScreenTranslation  ⇒ ScreenTranslationToolCapsule / ScreenTranslationFullImageHolder
```

实现见 `core/model/RomProfile` 的 `NAME_SCREEN_TRANSLATE` / `SCREEN_TRANSLATE`。

## 四十六、灵感语音（AI 记忆）的采集服务：**只有「开始 / 结束」两个事件**，重入的开始会被忽略（反编译 `com.oplus.aimemory` + 真机，2026-11-06，PLK110 / ColorOS 17.0.0）

动作 `oplus:voice_memory` / `oplus:voice_memory_stop` 的落点是 `com.oplus.aimemory/.voicecollect.service.DataCollectService`
（action `oplus.aimemory.intent.action.COLLECT_DATA`；服务类在 `base.apk` 的 `classes4.dex`）。
`longPressEventType` **只有 1 和 2 两个取值**，没有第三种；重复发「开始」也不会被当成结束。

### 46.1 `onStartCommand` 的完整分支

```
intent == null                ⇒ return START_NOT_STICKY
handler.removeMessages(1001)                      // 上一次排的自停
triggerType    = getIntExtra("triggerType", 0)
type           = TriggerType.Companion.b(intent)
longPressType  = getIntExtra("longPressEventType", 0)

type == UNKNOWN               ⇒ "trigger type error" → 结束（什么都不做）
USER_SETUP_COMPLETE == 0
  或 !c()（AI 记忆装没装 / 总开关） ⇒ "ai memory is not installed or user setup not complete, or disabled" + stopSelf
!type.isVoiceCollect()        ⇒ "unknow trigger type" → 结束

longPressType == 1            ⇒ 新建 VoiceCollectTask 并 addTask    // 开始采集
longPressType == 2            ⇒ 找已有的 VoiceCollectTask：
                                  没有 ⇒ "no task to execute" / "VoiceCollectTask not exist" → stopSelf
                                  有   ⇒ 交给它收尾
其余（0 / 3 / …）             ⇒ E "longPressType is not valid !"    // 状态不变
```

两条判据的取值表（都在这份 dex 里，别按名字猜）：

- `TriggerType`：`UNKNOWN=0` / `MAGIC_KEY=1` / `VOICE_RECORD=5` / `AI_KEY_DOUBLE_CLICK=15` /
  `AI_KEY_LONG_PRESS=16` / `NO_KEY_VOICE_RECORD=19`。
- `TriggerType$a.b(Intent)`：`triggerType == 1` **且** `longPressEventType > 0` ⇒ 归成 `VOICE_RECORD`；
  否则按 `triggerType` 查表。这条就是模块发 `triggerType=1 + longPressEventType=1` 能通的原因。
- `isVoiceCollect()`：只有 `VOICE_RECORD` 与 `AI_KEY_LONG_PRESS` 为真 —— 单发 `triggerType=1`
   而 `longPressEventType=0` 会被归成 `MAGIC_KEY`，落到「unknow trigger type」。

### 46.2 真机实测（`am start-foreground-service`，uid 0 绕开 `com.oplus.permission.safe.SECURITY`）

| 发送 | 现象 |
| --- | --- |
| `1`（开始） | FGS 类型 `0x40000000 → 0x40000080`（`SPECIAL_USE \| MICROPHONE`）；`AiM-DataCollectManager: startCollect`；拿到 `USAGE_VOICE_COMMUNICATION` 音频焦点 |
| 采集过程中再发 `1` | **仍在采**：类型保持 `0x40000080`，日志 `startCollect` 紧跟 `W reentrant collect.` —— 重入被忽略，**不视为结束** |
| `2`（结束） | 类型回 `0x40000000`；`setCollectOver` / `remove collectDeadline`；随后服务 `onDestroy`，并 `startActivity(VIEW_DETAIL → MemoryDetailActivity)` 把刚记的那条打开 |
| 没在采时发 `2` | `no task to execute` / `VoiceCollectTask not exist` / `stopSelf` / `onDestroy` —— 干净空操作，不弹任何界面 |
| `3` | `E longPressType is not valid !`，采集状态不变 |
| `0`（配 `triggerType=1`） | `E unknow trigger type`，服务随后被自停 |

### 46.3 对模块的含义

- **没有第三个事件可拆**：想给用户「切换 / 开 / 关」三档，只能拿这两个值拼 —— 切换的判据是模块自己的
  状态，不是 ROM 的能力。所以模块把它做成 `oplus:voice_memory` / `oplus:voice_memory_stop`
  两个平级动作（松手不再替用户收尾），「切换」不提供。
- 重复「开始」在 ROM 侧是 no-op：`ActionRunner` 那 3 秒连按窗口只省一次 `startCollect`，
  并不会像旧注释说的那样「把正在录的那一段掐断重来」。
- 「结束」在**没有录制时**是干净的（不弹界面），所以误发一次不会额外制造一个界面；
  但**真的结束一段**会拉起 `MemoryDetailActivity`，这是 ROM 自己的行为，模块没碰。

### 46.4 复现用命令

```
adb pull /data/app/…/com.oplus.aimemory-…/base.apk
python tools/dexdump.py classes4.dex method com.oplus.aimemory.voicecollect.service.DataCollectService onStartCommand
python tools/dexdump.py classes4.dex method 'com.oplus.aimemory.voicecollect.service.TriggerType$a' b
adb shell su -c 'am start-foreground-service -n com.oplus.aimemory/com.oplus.aimemory.voicecollect.service.DataCollectService -a oplus.aimemory.intent.action.COLLECT_DATA --ei triggerType 1 --ei longPressEventType 1'
adb shell "dumpsys activity services com.oplus.aimemory | grep isForeground"   # 看 types 里的 0x80
```
