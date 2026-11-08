# 键码：压感拍照键与自定义键

## 二十八、压感拍照键**不产生「按住」这一段**，长按在这颗键上做不到（实测，2026-10-20，PKU110 / ColorOS 17）

PKU110（Find X8 Ultra 卫星通信版）机身侧面的拍照键是**压感键**（force sensor，没有按下/抬起的触点）。
用 1.1.0-log 包在真机上量到的三组事实如下 —— 它们一起把「用时长判长按」这条路彻底封死。

**一、事件流里没有时长，只有一对瞬时事件**

```
拍照键：DOWN keyCode=767   deviceId=3 source=257 repeat=0 flags=8
拍照键：UP   keyCode=767            ← 与 DOWN 相隔 1~7ms
拍照键：按住 3ms（长按阈值 500ms）
```

- 按住 **3000ms** 不放，事件流仍然是这一对，间隔 **1ms**，期间**没有任何** `repeat`、没有任何周期性事件。
- 人手一次轻点最短也在 30ms 以上 ⇒ 这个 1~7ms **不是**用户按了多久，而是 ROM 把压力值翻译成
  「按了一下」之后合成的瞬时对。
- 同一颗键还有第二个码：**134**（`KEYCODE_F4`，不是标准的 `KEYCODE_CAMERA`(27)）。按住时上报
  134、轻点到底时上报 767，两者同样是瞬时对。134 不是「长按码」——它在按下的瞬间就来，
  与按了多久无关。

**二、`InputManager.getKeyCodeState()` 对这颗键**恒**返回按下**

`getKeyCodeState(deviceId=3, sourceMask=257, keyCode=767)` 在每次按压后 +50ms 起读到
`ACTION_DOWN`，一直读到 **+4000ms** 仍然是 `ACTION_DOWN`，从不出现 `ACTION_UP`；`134` 同样。
`deviceId` 是有效的（无效时 AOSP 返回 `-1`，实测拿到的是 `0`）。

⇒ ColorOS 在**上层**合成了那对瞬时 DOWN/UP 给应用，但**没有回写** InputReader 的按键状态表
（压感键的 evdev 只在上报压力越阈时发 down，从不发 release）。这张表因此永远停在「按下」，
拿它测时长会得到「用户按了无限久」的假结论。

**判据：这条必须靠「空白对照」才发现。** 只看 `+700ms 还按着` 会误以为「状态表能测时长」——
真正的证据是**用户已经松手、读数仍然是 down**。

复核方法（不依赖任何临时代码）：
`IInputManager.getKeyCodeState(deviceId, sourceMask, keyCode)`（system_server 里走
`ServiceManager.getService("input")`，本地 Binder、无 IPC），在一次按压的 **+50ms / +700ms**
各查一次、**并在松手之后 2 秒再查一次**。三次都是 `ACTION_DOWN`(0) ⇒ 这张表对这颗键不可用；
若松开后回到 `ACTION_UP`(1)，它才是可用的时长来源。

**三、结论与边界**

模块能碰到的三条信息源全部没有时长：事件流（合成瞬时对）、按键状态表（恒 down）、evdev
（要 root 且要自己解析 `EV_ABS`）。因此：

- **这颗键不可能支持长按**。靠时间判长按（`GestureDetector` 的 500ms 枪）在这里先天作废：
  抬手在 3ms 就到，枪在那一刻被摘掉。
- **也不要「自己造事件」**：注入 repeat 的 767 会自持循环（`SideKeyInterceptor.isSynthetic` 认合成事件
  的那张表是 `KeyId.INJECTABLE_CODES`，里没有 767），而且造出来的流仍然不知道用户什么时候松手。
- 能给的只有**单击**（ROM 每次按压就报一对，模块当场判）和**双击**（两次按压落在
  `doubleClickMs` 内）。双击**不需要额外开关**：`GestureDetector.onKeyUp` 那条「抬手当场判单击」
  的条件是 `instantSingle` **且** 双击为无操作（界面文案本身写的就是「双击无动作时…」），
  给双击绑上动作它就自动改走双击窗口。代价是单击要晚一个双击窗口（默认 100ms）—— 那是双击
  语义的固有代价，不是配置问题。
- 侧键不受影响：它的 735 / 780 / 781 / 782 **有**真实时长与 repeat，永远走事件路径；
  780/781/782 是 ROM 已判好的三档手势各一个码，对它们做轮询反而会把三档压成一档。

**顺带一条与长按无关、但同一颗键上的实测**：按键会触发宿主场景派发
（`ActionButtonKeyService$a#handleMessage what=1`），而这条消息**不带键的身份**
（`data` 是空的、`arg1`/`arg2` 恒 0）—— 宿主侧没法从消息本身看出「这一次派发对应哪颗键」。
两个方向的判据都错过：按某一颗键自己的开关判（曾经是 `cfg.isLive(KeyId.PLUS)`）时，
「接管了侧键」会连带吞掉拍照键的系统原生动作，「停用了侧键」则是每一次按键都放行一发 `proceed()`；
改成只看总闸之后，停用的侧键连自己的原生动作也没了。现在的判据是
`host/PressTrace`：宿主侧记「刚刚有没有被模块认领过一下」（判定经广播到达），
认领过 ⇒ 吞（这一下归模块）；没认领 ⇒ 放行（这是停用的键带出来的原生动作）。

## 三十一、压感拍照键（SPRUCE）的**整族键码**：手势由 ROM 判好（反编译 `android.view.OplusKeyEvent` + 用户机日志，2026-10-21）

第二十八节只看到这颗键的 134 / 767 两个码，于是当时把结论下成「只能给单击和双击」。
用户机（PKU110）的日志把另一半补上了：**双击这颗键时 ROM 只发一个 769，不再发两次 767**，
而模块只认收集时拿到的那一个码 ⇒ 769 漏给系统，由系统执行它自己的默认动作（打开相机）。

### 29.1 键码表

`oplus-framework.jar` 的 `classes.dex` 里 `Landroid/view/OplusKeyEvent;` 的
`PUBLIC STATIC FINAL int`（`dexdump -d` 读出来的静态值）：

| 值 | 常量 | 含义 |
| --- | --- | --- |
| 765 | `SPRUCE_PRESS_LIGHT_KEYCODE` | 轻按 |
| 766 | `SPRUCE_PRESS_HARD_KEYCODE` | 重按 |
| **767** | `SPRUCE_PRESS_SHORT_KEYCODE` | **短按（单击）** |
| **768** | `SPRUCE_PRESS_LONG_KEYCODE` | **长按** |
| **769** | `SPRUCE_DOUBLE_CLICK_KEYCODE` | **双击** |
| 770 / 771 | `SPRUCE_SWIP_UP/DOWN_KEYCODE` | 上滑 / 下滑 |
| 772 | `SPRUCE_REAL_FINGER_ACTION_KEYCODE` | 真实手指动作 |
| 773 | `SPRUCE_START_CAMERA` | 启动相机 |

另外 134（`KEYCODE_F4`）是这颗键在按键布局表里的**原始码**（按住时上报，见第二十八节）。

### 29.2 日志证据（用户机，`1.1.2-debug`）

```
18:19:26  设置页写入：double → oplus:screen_record          ← 把「双击」绑成录屏
18:19:28  按键（…StrategyActionButtonKeyLaunchApp#…）：keyCode=769 不在认领白名单，放行给系统
18:19:40  按键（…）：keyCode=769 不在认领白名单，放行给系统
```

同一份日志里 767 一次都没出现（认领的码会打 `拍照键（…）：DOWN keyCode=…`，一行都没有）
⇒ 双击那一下**只**发了 769。用户看到的就是「双击设了录屏，结果打开相机」。

### 29.3 结论

- 这颗键是**一个物理键、一族码**：手势由 ROM 判好，模块拿到的已经是结论，**不需要也不该**
  再用时长去判 —— 判成双击时那两次 767 根本不存在。
- 收集到族里任何一个码，认领集合就要扩成**整族**（`KeyId.custom` 里的 `familyOf`）。
  只认一个码的后果就是这次这个 bug：其余手势的码全部漏给系统。
- 767 / 768 / 769 三个码按「ROM 判好的手势」直接映射成单击 / 长按 / 双击
  （`RomKeyFamilies.gestureOf`），注入侧走 `Decision.RomGesture`、抬手时派发；
  其余族内码（134 / 765 / 766 / 770~773）**认下但吞掉**，不派发 —— 它们只是同一次按压的中间形态。
- **768 只是「框架里有这个常量」，不等于这颗键会发它**。第二十八节实测过：按住 3 秒也只有
  一对间隔 1~7ms 的 DOWN/UP、没有任何 repeat，用户机日志里也一次都没出现过 768。
  所以认下 768 是**防御性**的（万一某个 ROM 版本真的发，就按长按处理），
  **不代表长按在这颗键上可用了** —— 现状仍然是「长按做不到」，与第二十八节一致。
  真要确认，得让用户按住 2 秒导一份日志，看有没有 768。

### 29.4 同族清单：`OplusKeyEvent` 里一共五族（同一份 dex，2026-10-21）

「一个物理键、好几个码」不是拍照键独有的形状。`Landroid/view/OplusKeyEvent;` 的 33 个常量
按前缀分成五族 —— 记在这里是为了下次有人报「某某键不生效」时，能立刻知道该去查哪一族：

| 族 | 码 | 常量 | 备注 |
| --- | --- | --- | --- |
| 侧键（`ACTION_BUTTON_*`） | 780 / 781 / 782 / 783 | CLICK / SINGLE_TAP / LONG_PRESS / START_APP | 见下面那条纠正 |
| 压感拍照键（`SPRUCE_*`） | 765~773 | 见 29.1 | 本次适配的那一族 |
| 灵犀触控（`LINGXI_*`） | 670~673、680~694 | SHORT_TAP / HEAVY_TAP / DOUBLE_TAP / 分区点按 | 后盖 / 边框敲击，按区域各一个码 |
| 手柄（`KEYCODE_GIMBAL_*`） | 706 / 717 | SWITCH_CAMERA / POWER | |
| 肩键（`KEYCODE_SHOULDER_*`） | 760 / 761 | DOWN / UP | 只有按下 / 抬起，没有手势 |
| 其它 | 774 | `KEYCODE_AI` | |
| （标志位，不是键码） | 8192 | `FLAG_DISPATCH_HOMEPRESSED` | |

**对旧结论的纠正**：`780/781/782` 并不是「ROM 判好的三档手势」——
780 是 `ACTION_BUTTON_CLICK`、**781 是 `ACTION_BUTTON_SINGLE_TAP`、782 是 `ACTION_BUTTON_LONG_PRESS`**、
783 是 `ACTION_BUTTON_START_APP`。也就是说侧键**也有** ROM 判好的长按码（782）。

现状是实测里只出现过 780（侧键有真实时长与 repeat，走时长判定，见第二十八节末段），
所以这条纠正暂时不改行为。**但有个潜在坑**：`KeyId.PLUS.acceptedKeyCodes` 把 781/782 也认下了，
而认下之后它们是**喂给时长判定器**的 —— 哪天某个 ROM 版本真的把长按改发 782，
模块会把它当成「又一次按下」，判出单击而不是长按。真要撞上，按拍照键这套
（`RomKeyFamilies.gestureOf` + `Decision.RomGesture`）把 782 映射成长按即可。

## 四十二、压感拍照键的中间态码在 ROM 里是**放给前台**的（反编译 `oplus-services.jar`，2026-11-04，PLK110 / ColorOS 17.0.0）

用户机（PLG110）报「双击拍照键进相机，过几秒卡死、几秒后退回桌面」。日志里唯一与「相机起来之后」
相关的现象是：进相机一次就有 1515 条族码被模块吞掉（765 轻按 7 / 766 重按 18 / 772 真实手指 48 /
770 上滑 589 / 771 下滑 853）。顺着这条线反编译接管点，结论推翻了「这族码 ROM 自己会执行动作」。

### 42.1 接管点对 765~772 只做两件事

`StrategyActionButtonKeyLaunchApp#actionInterceptKeyBeforeQueueing(KeyEvent, int, int, boolean, boolean)`
按 keyCode 分了几段，782 / 781 / 780 各走侧键那一套（长按枪、单击、唤醒），最后一段是：

```
const/16 v0, 765
if-lt v10, v0, ...          # keyCode >= 765
const/16 v0, 772
if-le v10, v0, ...          # keyCode <= 772
# 命中 765..772：
invoke-virtual mOplusPowerKeyHandler.removeMessages(2)   # 只撤掉长按枪
return v1                                                # v1 = 1
```

**它不派发任何动作、也不碰相机**。也就是说这族码在 ROM 的设计里是**放给前台的**：轻按对焦、
重按快门、上滑下滑变焦，都由前台那个相机 App 收到码之后自己做。模块把这族码整族吞掉，
相机在前台时就再也收不到按键 —— 与用户口述的「相机里按键没反应」一致。

### 42.2 `mActionSwitchState` / `mActionSceneOwner` 是设置值，不是前台状态

两个字段都是 `Ljava/lang/String;`，刷新路径是 `readActionButtonSwitchState(userId)`（懒加载）
与 `ActionButtonProvider` 上的 ContentObserver —— 用户在系统按键页里选过相机，它就一直是
`camera`。**拿它当「此刻前台是谁」用会变成永远成立**，这也是「无条件放行 ⇒ ROM 自己去拍照」
那条旧结论的来源。

### 42.3 前台怎么读（这一档**没有采用**，留作记录）

读法与代价都记在这里，免得下次有人想「按前台分流」时从头再反编译一遍：

| 步骤 | 反射目标 |
| --- | --- |
| 服务实例 | `android.app.ActivityTaskManager.getService()` —— system_server 里 `ServiceManager` 拿回的是本地 binder，`asInterface` 的 `queryLocalInterface` 返回的就是 `ActivityTaskManagerService` 本人 |
| 首选取值 | 字段 `ActivityTaskManagerService.mLastResumedActivity`（`ActivityRecord`） |
| 退路 | 字段 `mRootWindowContainer` → `RootWindowContainer.getTopResumedActivity()`（内部沿 `getTopDisplayFocusedRootTask()` 走两层虚调用，同样不取锁） |
| 包名 | 字段 `ActivityRecord.packageName` |

代价是一次字段读，落在按键热路径上也能接受（实现里再按 500ms 缓存）。

### 42.4 处置：模块优先，**不放行**

- 这颗键一旦被模块接管，整族码就都归模块：765~772 一律吞，**不因为「前台是相机」破例**。键交给了
  模块，就该按模块的配置走，不能出现「有时候模块说了算、有时候相机说了算」。
- 代价是相机里这颗键没有原生快门与变焦 —— 这是接管的应有之义，不算缺陷。要恢复，把键从模块里
  摘掉即可。
- 想让相机里也能用，正确做法不是放行，而是给中间态码也设计出可配的动作（另议）。**不能靠注入族码
  实现**：族码不在 `KeyId.INJECTABLE_CODES` 里，注入出去会再走回认领路径成环
  （见 `RomKeyFamilies.isFamilyCode`）。
