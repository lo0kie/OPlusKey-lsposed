# 实现原理

原版 `OPlusKey` 的按键接管是「独占输入设备 + uinput 转发」：

```
/dev/input/event*  →  daemon 独占 grab  →  自己判定单击/双击/长按
                                        →  uinput 转发不拦截的事件
```

这条路能做，但有三个天生的问题：

1. **需要 root daemon**，还要一层 supervisor 看护循环（`service.sh`）保证它不被杀；
2. **独占设备会漏事件**：触摸屏如果声明了 `KEY_POWER` 也被 grab 掉，`EV_ABS` 收不到，触摸就失灵 ——
   daemon 里得专门写一条「跳过触摸设备」的规则；
3. **要维护一套 C 代码 + NDK 交叉编译**。

本版换的那条路：

```
system_server 收到按键
  →  ColorOS 专为侧键写的策略类 StrategyActionButtonKeyLaunchApp 被调用
     （系统设置 → 侧键 背后的实现）
     →  我们 hook 这个方法
        →  启用了 → 记按下/抬起 → 判定手势 → 执行动作
```

接管点是**按候选表逐层降级**挑出来的（`inject/Framework.kt`）：

```
1. StrategyActionButtonKeyLaunchApp      ← ColorOS 专为侧键写的策略类，首选
2. PhoneWindowManagerExtImpl             ← OPPO/一加对 PhoneWindowManager 的扩展
3. PhoneWindowManager                    ← AOSP 兜底 interceptKeyBeforeQueueing
```

因此：

- 不需要 root（模块跑在 system_server 里，本来就是 system uid）；
- 不需要 uinput，不碰 `/dev/input`，不会影响触摸；
- 不需要 C 代码与 NDK。

代价是**崩溃范围从「输入法起不来」变成「整个系统起不来」**（开机循环），所以注入侧的纪律（AGENTS.md 五之二）比原版严格得多。

### 只管侧键

`KeyId` 只有 `PLUS` 一个分量。原版还要接管电源键与音量键，本版**不接管**：

- 电源键要保系统电源菜单与紧急呼叫；
- 音量键要保组合键（上下同按 = 截屏）与长按连调。

侧键 keyCode 只在 `core/model/KeyId` 里定义：`KeyId.keyCode` = 735（`KEYCODE_ACTION_BUTTON_CLICK`，Linux 侧是
`BTN_TRIGGER_HAPPY32`），另有一份白名单 `KeyId.acceptedKeyCodes`（=
`{735, 780, 781, 782}`，含 ColorOS 策略类路径的内部注入码，781 的补齐记录见 `device-findings.md` §14.4）。

第一层 `StrategyActionButtonKeyLaunchApp` 确实存在，落在 `oplus-services.jar`，入口是外层
`actionInterceptKeyBeforeQueueing` —— **只挂外层**：内部回调 `$2`
是它的纯转发（§14.2），挂它只会让同一次按键被回调两次（2026-10-13 起 `Framework.kt` 不再挂），

（`ActionButtonProvider` + SQLite）。**完整的类名 / 方法签名 / URI / 表结构记录在
[`docs/device-findings.md`](device-findings.md)** —— 改 Hook 面之前先读它，不要再靠猜。

## 二、按键是怎么变成手势的

daemon 的判定是 `poll()`
驱动：它自己收原始事件，超时醒来就知道「这段时间没有新事件」，所以能直接判「长按到点了」「双击窗口过了」。

LSPosed 这边拿到的只是「framework 调了一次侧键策略方法」，所以判定必须**改成定时器驱动**（`inject/input/GestureDetector.kt`）：

```
按下 → 起一个「长按计时器」(longPressMs)
     同时清掉双击窗口

长按计时器先到 → 判长按，抬手不再触发
抬手 → 起一个「双击窗口计时器」(doubleClickMs)
窗口内又来一次按下 → 判双击
窗口到了还没有第二次 → 判单击
```

定时器全跑在 system_server 的 `Handler` 上，**回调里绝不阻塞**
（一次按键处理必须立刻返回，否则整个系统的按键都会变卡）。

## 三、「交还系统」为什么不提供（2026-10-14 删除）

模块曾经有一档动作叫**「交还系统」**（`native` / `passthrough`）：选了它，按下去就不拦这个键、让系统那套照常跑。
**这一档已经整条删掉**，因为它要的语义在原理上做不到，而且失败方式很难看。

**做不到的原因：判定手势必须先把 DOWN 吞掉。**

侧键的单击 / 双击 / 长按是**我们自己在拦截点里数出来的**（见第二节：DOWN 之后排计时器、UP 之后排双击窗口）。也就是说：只要还想让双击 / 长按这两档生效，DOWN 就必须被吃掉。而「交还」要的是**这一次按键原样给系统**
——两者不可能同时成立：

- 真放行（`return false`，原事件给系统）⇒ 这次按压整键都归系统，**双击 / 长按上绑的命令一起失效**；
- 吞下来再补一次注入（旧实现）⇒ 补出去的是**合成事件**（`deviceId = -1`），ROM 那侧不一定认它，用户看到的是「按下去没反应」，而且它会再次触发框架那条
  `what=1` 场景派发。

用户报的现象正是这两条各占一半：**「交还系统无效，且直接拉回模块页」**——无效来自合成事件不被认，拉回模块页来自那条被放行的场景派发（框架给宿主的那条的效果是「宿主打开设置页」，而那一页被我们替换掉了）。

**为什么不保留「整键交还」这一档：** 它只能表达「这把键我不要了」，而那正是**主开关**的语义（`KeyConfig.enabled = false`
⇒ 拦截路径整条不生效、行为与原生完全一致，见第一节）。再留一档动作表达同一件事，只会让人以为「可以只交还单击」，然后在双击 / 长按上踩空。

**删除范围**（不留兼容，配置格式还没发布）：

- 动作类型 `Action.Native` / `Action.Passthrough`，以及网格里那一格（`ActionCatalog`）；
- 判定侧的放行回调（`GestureDetector.onPassthrough` → `GestureForwarder` 的 `PHASE_PASSTHROUGH` 广播）；
- 宿主侧的「重注入一次按键」（`KeyInjector.injectForForward`）与自持循环兜底闸（`GestureReceiver`）；
- 宿主侧「放行时把场景派发放过去」那一整套配额 / 扣留补投机器（`SideKeyGestureHooks`）——现在那条场景派发**一律吞掉**，唯一例外是主开关关着时（模块完全让位）。

**副作用（也是修好的两件事）：**

- 那条 `what=1` 场景派发不再有条件放行 ⇒ 按侧键不会再莫名其妙把设置页拉起来；
- 单击那档的默认值从 `native` 变成 `none`（无操作）。想「回到原生」用主开关，不要用动作档。

## 四、配置从哪来、怎么到注入侧

配置的唯一生效来源是**宿主私有目录里的 JSON 文件**（`/data/user_de/0/com.oplus.gesture/files/opluskey_config.conf`，读写走
`core/config/ConfigStore`，格式见 `core/config/ConfigCodec`）：

- 位置取宿主私有目录的 **DE（设备加密）** 那一份，**不是 CE**：CE 首次解锁前不可见，开机到解锁之间只读得到默认值（见
  `docs/device-findings.md` 9.9）。
- **宿主进程**（设置页、手势执行）每次按键 / 保存都现读文件 —— 同 uid 直接可读，改完即时生效。
- **注入侧（system_server）读不到那份文件**（EACCES），它只信宿主广播过来的完整配置（`core/wire/ConfigBroadcast` →
  `inject/ConfigChannel`），启动 / 解锁 / 写盘时各发一次。
- 系统「设置 → 快捷键」的 provider（`content://uri.gesturemanagerprovider/action_button`）只是**只读诊断项**（`inject/system/SystemActionButtonConfig`，在宿主进程里读），不参与配置链路。

不能用 `SharedPreferences`：它每个进程各缓存一份，写进去的值别的进程看不到。

## 五、注入侧引用的红线

`inject/` 下的一切都会随注入进入 system_server，因此：

| 允许                                                                            | 禁止                                                                                    |
| ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Android framework **公开** API（`KeyEvent` / `Context` / `Handler` / `Cursor`） | `com.android.server.*` / `android.hardware.input.*` 的**直接引用**                      |
| 按字符串名反射找类（`Reflect.findClass`）                                       | `import androidx.*` / Compose / COUI（判据是「这个类会不会在 system_server 里被执行」） |
| `core/`（model/log/reflect/wire）+ `KeyModule`（只依赖公开 API）                | `inject/` 下引用 `androidx.*` / COUI，或引用 `core/config` / `host/` / `ui/`            |
| 候选类名只写在 `inject/Framework.kt` 的候选表里                                 | 在 `inject/KeyHooks.kt` 里就地写类名字符串                                              |
| 系统配置只经 `inject/system/SystemActionButtonConfig.kt`                        | 在别处自己拼 `content://` 或硬编码列名                                                  |

framework 私有类**一律按字符串名解析**：它们会被 ROM 改名，写死引用等于「换一个 ROM 版本就 `NoClassDefFoundError`
→ 开机循环」。 `StrategyActionButtonKeyLaunchApp` 就是这一类 —— 它在 `com.android.server.policy` 与
`com.oplus.server.policy` 两个包里都出现过，所以两个包名都要试。

`app/build.gradle.kts` 里**故意不声明**这些依赖 —— 这样误加的直接引用会变成**编译错误**，

**`androidx` / Compose / COUI 是进包的**（2026-10-11 起）：`ui/` 那一层用 Compose + COUI 渲染设置页。这不违反红线 ——
`ui/` 的类只在宿主设置页进程里执行，system_server 里从不执行，而 ART 的类解析是惰性的。要守住的判据是
**「这个类会不会在 system_server 里被执行」**：`inject/` 不许出现 `import androidx.*` 或
`io.github.suqi8.coui.*`（复核命令见 AGENTS.md 二、1.2）。

## 六、降级设计

- 每个 Hook 单独装，装不上只记日志（`XLog.guard` / `hookGuarded`），不影响其他 Hook；
- 候选接管点**一个都找不到**（ROM 改了类名）→ 模块完全不生效，但系统照常；
- 找到了策略类但方法签名对不上（ColorOS 版本差异）→ 退到下一层候选，三层全落空才放弃；
- 配置读不到 → 退回「侧键不接管」；
- 每个动作执行失败 → 只记日志，不影响这次按键的其他环节。

## 七、「自定义命令」页的两条界面取舍（2026-10-14）

### 7.1 「放弃未保存的命令」问在**退出**时，不是进页时

- 触发点：`SettingsPage.goBack()` —— 当前页是「自定义命令」且 `draft.dirty` 为真时**拦住不退** （置
  `confirmLeaveCommands` 并返回 `true`），由该页自己画出 `ConfirmDialog`；确认 ⇒ `draft.reset()` +
  `popBackStack()`（**绕开 `goBack()` 的拦截**，否则会在原地再弹一次自己），取消 ⇒ 关掉窗、留在页里继续编。
- 为什么挪：原来那一问在「按键绑定」页那颗 FAB 上（进页前），配套的是「进页即
  `draft.reset()`」。两条路都别扭 —— 进页被拦一下，而真正**丢掉东西**的那一下（退出）反倒没有提示，且「进页即清」本身是一条**静默丢草稿**的路。现在进页永远直接进，草稿只在用户确认放弃时清。
- ⚠️ **判据是 `draft.dirty`，不是 `hasContent`**：`dirty` 与 `load(cmd)`
  时记下的那份**快照**逐项比。挪到退出之后这一问会被问得频繁得多（每次返回都过一遍），拿 `hasContent`
  会让「点开一条已保存的命令看一眼、原样返回」也挨一句「放弃未保存的命令」—— 那句话是假的。保存成功后 `reset()`
  会把快照一起清掉，所以「改完存好再返回」也不会多问。
- 进页**不再清草稿**：唯一会留下旧草稿的路是「清栈回首页」（⋮ 菜单的「默认界面」/ 从系统设置重进，见
  `onHostReentered`），那条路上不丢用户的东西、下次进来还能接着改，比静默清空好。

### 7.2 「支持的命令格式」只列**没有内置入口**的格式

那张卡（`COMMAND_FORMAT_LINES`）讲「命令输入框里能写什么」，带前缀的行点一下会把前缀**替换**进输入框（不是追加 —— 拼在已有内容前面得到的是一条跑不通的命令）。收录规则：

| 格式                                                      | 在图里                                                          |
| --------------------------------------------------------- | --------------------------------------------------------------- |
| `shell:` / `keyevent:` / `intent:` / `shortcut:` / `cmd:` | ✅ 有（没有图形入口，只能手写）                                 |
| `app:` / `window:`                                        | ❌ 无 —— 内置「打开应用」那一格就是它（小窗是那格菜单里的开关） |
| `zoom:mini` / `zoom:mini:collapse`                        | ❌ 无 —— 内置「系统小窗」那两档就是它                           |

判据一句话：**同一个能力已经有图形入口的，这张卡不再重复教手写**
—— 两个入口并存时，手写那条更容易写错（前缀拼错、包名写错都只会得到一条跑不通或行为不同的命令）。
