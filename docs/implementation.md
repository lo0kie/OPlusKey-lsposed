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

接管点是**按候选表逐层降级**挑出来的（`bridge/Framework.kt`）：

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

配置的唯一生效来源是**宿主私有目录里的一个文件**（`/data/user_de/0/com.oplus.gesture/files/opluskey_config.conf`，读写走
`core/config/ConfigStore`，内容格式见 `core/config/ConfigCodec`）：

- **磁盘上不是裸 JSON**：外面套了一层混淆外皮（`core/config/FileObfuscation`）—— 首行魔数 `OPlusKey-Runtime-1`，
  之后是正文经**两层**变换压成的一行：先「异或 + Base64」，再按一张替身字母表逐字符换写、按位置错开。
  命令库（`opluskey_commands.conf`）同一层皮。这不是加密（密钥与字母表都写在代码里），
  只是挡住「随手打开顺手改坏」；导出包（`.bin`）用的是同一种皮、魔数不同名。
  所以**手改配置文件要连着两层一起改**（解两层、改完再包两层，配方见 `FileObfuscation` 类注释）。
  正文顶层另有 `md5` 字段（`ConfigCodec`）：它是**把这一格摘掉之后**那份 JSON 的 MD5，
  改内容不重算就判读不懂（只把缩进 / 换行弄乱则照样认）。
  首行魔数分两档（运行时 `OPlusKey-Runtime-1` / 导出包 `OPlusKey-Config-1`）；**不做旧格式兼容**：
  首行对不上、或正文认不出（加第二层之前的旧文件）就是读不懂。开发期残留的文件删掉即可 ——
  不退回「当它没写过」，那会把用户配置整份换成默认值（见 `ConfigStore.Load` 的三态）。
- 位置取宿主私有目录的 **DE（设备加密）** 那一份，**不是 CE**：CE 首次解锁前不可见，开机到解锁之间只读得到默认值（见
  `docs/device-findings.md` 9.9）。
- **宿主进程**（设置页、手势执行）每次按键 / 保存都读一遍配置 —— 同 uid 直接可读，改完即时生效。
  读是**按文件指纹缓存**的（`core/config/ConfigStore.load`：比对修改时间 + 长度）：同一份文件在
  一次按键里可能被读好几次（手势执行一次、顺带那次报活再一次），不缓存就要重复「读盘 + 剥外皮 +
  JSON 解析」。文件一被写，指纹就变，下一次读立刻重解析；本进程自己的写盘另外主动失效。
- **注入侧（system_server）读不到那份文件**（EACCES），它只信宿主广播过来的完整配置（`core/wire/ConfigBroadcast` →
  `inject/ConfigChannel`），启动 / 解锁 / 写盘时各发一次。
- 系统「设置 → 快捷键」的 provider（`content://uri.gesturemanagerprovider/action_button`）只是**只读诊断项**
  （`bridge/host/SystemActionButtonConfig`，在宿主进程里读），不参与配置链路。两条纪律：
  **列名一律先读 Cursor 拿**（随 ROM 版本变过，不许硬编码）；**只能在宿主进程里调** ——
  注入侧用它必 NPE（见 `device-findings.md` 六之五）。写接口**已删**（零调用方），真要用的时候直接
  `ContentResolver.update/insert` 三行就够；不要再往回加绕开 `ContentResolver` 的重反射。

不能用 `SharedPreferences`：它每个进程各缓存一份，写进去的值别的进程看不到。

## 五、注入侧引用的红线

`inject/` 下的一切都会随注入进入 system_server，因此：

| 允许                                                                            | 禁止                                                                                    |
| ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Android framework **公开** API（`KeyEvent` / `Context` / `Handler` / `Cursor`） | `com.android.server.*` / `android.hardware.input.*` 的**直接引用**                      |
| 按字符串名反射找类（`Reflect.findClass`）                                       | `import androidx.*` / Compose / COUI（判据是「这个类会不会在 system_server 里被执行」） |
| `core/`（model/log/reflect/wire）+ `KeyModule`（只依赖公开 API）                | `inject/` 下引用 `androidx.*` / COUI，或引用 `core/config` / `host/` / `ui/`            |
| 候选类名只写在 `bridge/Framework.kt` 的候选表里                                 | 在 `inject/KeyHooks.kt` 里就地写类名字符串                                              |
| 系统配置只经 `bridge/host/SystemActionButtonConfig.kt`                          | 在别处自己拼 `content://` 或硬编码列名                                                  |

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

## 八、适配清单：机型 + 系统版本

清单只回答「这一组验过没有」，**不拦任何东西**。清单本体是 `core/model/DeviceSupport` 的 `ADAPTED`，
判定是一个**纯函数**：只吃 `model` / `android` / `rom` 三个字符串，`Build` 那三个值由各调用点自己传。

- **ROM 版本从 `Build.DISPLAY` 取**，不读 `ro.build.version.oplusrom` —— 后者是 hidden 接口，
  界面进程会被非 SDK 限制拦下（与 `ro.vendor.oplus.market.enname` 同一类）。
- 机型名照抄真机属性 `ro.vendor.oplus.market.enname`（`OnePlus 15`），同样不在运行时读。
- 当前清单只有一档：`PLK110` + `Android 17` / `ColorOS 17.0.0`。
- **任何机型、任何系统版本都照常注入**：system_server 里的按键接管、宿主侧的手势劫持与接收器、
  设置页替换全都装。清单外只意味着「没验过」，不是「不能用」。
- 提示只有一处：**「更多选项」页那张卡里的「本机」行**。三档措辞分别是「已适配」/
  「未验证过这一版系统，可能不可用」/「未在适配清单里，可能不可用」。首页那条常驻提示**不再**
  有这两档（页面也不会因为机型整页禁用）。
- 判据行是 `本机机型：…；系统：…；允许运行=…`；清单外时紧随其后一句 `… ⇒ 照常注入（清单外的机器可能不生效）`。
- **要加机型或加一版 ROM**：加一行 + **在真机上把接管点重新验一遍**。那些类与方法都是 ROM 私有的，
  换机型 / 换 ROM 版本会整体换名换形，那种情况下模块会「拦下来但什么都不做」，比直接不支持更难排查
  （见 `device-findings.md` 十九）。

### 构建档位

| 档      | 落点允许猜 | 调试通道 | 详细日志 | 防篡改 | 签名 |
| ------- | ---------- | -------- | -------- | ------ | ---- |
| debug   | 开         | 开       | 开       | 无     | 调试 |
| release | 关         | 关       | 关       | 有     | 发布 |

判据不是「运行时判断」而是**源集文件是否存在**：`core/src/{debug,release}/java/com/lookie/opluskey/core/BuildFlags.kt`
各一份同名常量，打包时只有一份进产物；`SealPin` 由 `:core` 的 `SealReleaseAssets` 为 `release` 生成
（`core/src/debug` 那份是 `SEAL_ENABLED=false` 的明文直通）。
复核：`ls core/src/{debug,release}/java/com/lookie/opluskey/core/BuildFlags.kt` 两份都在；
两档区分是否真的生效，`python tools/check_variants.py` 一次跑完（它按「该串只应出现在哪一档」逐项判定）。

- **CI 出 debug + release**（`.github/workflows/build.yml`），发到模块仓库的只有 `release`：
  `versionName` 带 `-` 的按预发布发，包体不变。没配签名密钥时只出 debug —— 封存层依赖签名派生密钥，
  没有明文回退。

## 八之二、多版本适配：档位表

ROM 私有的东西分三类，**各自按 ROM 系列（`17.0.0` 这种）分档**，「适配一版新 ROM」= 加档。
键与口径都取 `DeviceSupport.currentRomSeries`（一处读 `Build.DISPLAY`，见「八、适配清单」）。

| 那一类                                                                        | 表在哪                                    | 加一档加什么   |
| ----------------------------------------------------------------------------- | ----------------------------------------- | -------------- |
| 接管点候选（Hook 面类名 / 方法名 / 宿主侧服务与 Activity）                    | `bridge/Framework.kt` 的 `HookSet`        | 一个 `HookSet` |
| 执行落点 + 反射面（内置命令落到哪个组件、ColorOS 私有类名 / 方法名 / 字段名） | `core/model/RomProfile` 的 `Profile`      | 一个 `Profile` |
| 小窗几何（展开档的落点矩形与缩放）                                            | `core/model/MiniWindowGeometry` 的 `Slot` | 一个 `Slot`    |

**前两张表的键是 ROM 系列、第三张的键是机型**，别并成一张：落点由 ROM 决定（同一版 ROM 在不同机型上相同），而几何由**屏幕尺寸与 dpi** 决定（换一块屏就得重新量）。

一、7 要求接管点候选只写 `bridge/Framework.kt`，所以那张表留在那边；`RomProfile` 里另有一个
`Reflection` 装执行侧要反射的名字。两张表**都以 `romSeries` 为键**，加档时一起加。

- **按 ROM 系列分档，不按机型**：这些名字由 ROM 决定，机型只在适配清单里起作用。
- **加档之后要在真机上把每一层重新验一遍**：那些类与方法都是 ROM 私有的，换版本会整体换名换形。
  每档带一个 `verified` 字段（机型 + 日期）记「这一档是谁在哪台机器上验的」，安装期判据行会打出来。

- **没验过的 ROM 照「基准实现」跑**（`RomProfile` 的类注释里也写了）：`PROFILES` 末尾那一档就是基准，
  本机 ROM 不在表里时，内置命令落点与接管点候选 / 反射面**都照它试**，各打一行 `w` 说清照哪一档跑。
  跑不通由执行侧收尾 —— 起不来会 toast 一句「这次没执行成功」（`CommandFailure.UNKNOWN`），
  反射落空则记 `w` 后逐处跳过。
- **界面上不再锁住任何一条内置命令**：网格格子与「添加命令」的选择行都可点，跑不起来交给执行侧的
  toast。要让某一版 ROM 被「钉住」，就往 `RomProfile.PROFILES` 里补那一版的一档（加完照例在真机上验一遍）。
- 安装期在机型 / 系统那两行旁边多打一行 `适配档位：内置命令=…；小窗几何=…`：
  适配清单管「这一组验过没有」、档位表管「注入之后那些落点认不认得」，真机报「按了没反应」时两句一起看
  就知道断在哪一环。
- **同一层的多个接管点候选「第一个命中就停」**：那一层的几个候选是同一个类的两种包名写法
  （AOSP / OPLUS），两个都存在时各装一个接管点，会把一次按压喂判定器两遍（双击变「双击 + 单击」）。
  层与层之间本来就是逐层降级，同层这道闸原来漏了。
- **没进档的东西**：小窗 bundle 的 key 与枚举值（`extra_window_mode` / `WINDOWING_MODE_ZOOM` /
  `mZoomLaunchFlags`）—— 它们是协议值，改了要在真机上重新验，留在实现里并在注释里写明。

## 九、提交前检查（三道闸 + 一道锁定校验）

| 闸       | 命令                      | 规则落在哪                                           |
| -------- | ------------------------- | ---------------------------------------------------- |
| 格式     | `./gradlew spotlessApply` | 根 `build.gradle.kts` 的 `spotless` 段（ktlint）     |
| 静态检查 | `./gradlew detekt`        | `config/detekt/detekt.yml`                           |
| Lint     | `./gradlew lintDebug`     | 根 `build.gradle.kts` 的 `Lint.applyProjectPolicy()` |

三道闸一起跑用 `./gradlew gate`（根脚本注册的聚合任务，两个模块 × 三个任务）。

- **`gate` 与打包分开跑**：`assemble*` 会带两遍 R8（release 与 beta 各一遍）。把它们串在一条命令里，
  构建时间就是三道闸的好几倍 —— 而绝大多数「改完看一眼」根本不需要包。

- **三个闸的配置都在根脚本里，一处定义、两个模块共用**（`:app` 与 `:core` 的 `build.gradle.kts`
  **不再各写一份**）。重复的规则等于没有规则：改一边另一边不会红，只会静默地跑在两套标准上 ——
  拆模块那一轮就踩过（detekt 阈值只在 `:app` 调过，`:core` 仍是默认值）。
  挂法是 `subprojects { plugins.withId("…") { extensions.configure<…> } }`：`subprojects` 段在根脚本
  求值时先跑，那时子项目的 `plugins { }` 还没执行，直接 configure 会当场失败。
- 历史存量进的是 detekt 的 `config/detekt/baseline.xml` 与 lint 的 `@Suppress` 注解（**没有** lint
  baseline 文件 —— 那个名字并不存在）。基线是**两个模块共用一份**：detekt 按「本模块扫到的文件」
  去匹配，匹配不上就静默忽略，所以共用不会互相污染。
- **新代码不许再往基线里加条目**；加了要在提交信息里说明为什么。拆得动的存量要顺手**减**条目
  —— `PageRoot.kt` 那两条（`LongMethod` / `CyclomaticComplexMethod`）是拆完整页之后删掉的，
  这类「基线变短」的 diff 比「挪签名」有价值得多。
- 行长上限的值写在根 `build.gradle.kts` 的 `ktlintRules` 里。
- **死 import 由 ktlint 的 `no-unused-imports` 管**（`ktlintRules` 里显式 `enabled` —— ktlint 1.8 默认
  不跑它，实测「完全无引用的 import」也原地不动）。它按 PSI 引用判，比 detekt 的 `UnusedImports` 准；
  detekt 那条留着兜底。
- **两道闸都判不了「同名遮蔽」**：`val key = cfg.key(...)` 这种，短名在文件里出现过，两个工具都当成
  「还在用」。`tools/find_shadowed_imports.py` 只把「import 短名与文件内声明同名」的文件列出来
  （十来行，人工扫一眼即可）；它**不做判定**，因为文本层分不清 `Modifier.padding()`（扩展调用，import
  有用）与 `cfg.key()`（成员调用，import 无用）。
- 「注入侧不得引用界面层依赖」的复核命令见 `architecture-refactor.md` §六。

### 依赖锁定（`app/gradle.lockfile`）

- 开关在根目录 `build.gradle.kts` 的 `subprojects { dependencyLocking { lockAllConfigurations() } }`；
  锁文件是 `app/gradle.lockfile`（**入库**，258~414 条，一条 = 一个 `group:artifact:version` + 用到它的配置）。
- 改了 `dependencies { }` 之后：`./gradlew :app:dependencies --write-locks`，把锁文件的 diff 一起提交
  —— 那份 diff 就是「这次升了什么」的清单。
- **忘了锁的那次本地照样编得过**：Gradle 对「没进锁文件的配置」是宽松的（按解析结果走，只有 STRICT
  模式才当场失败）。所以 CI 里有一道「校验依赖锁定」：重写一遍锁文件再和库里那份比对，变了就红灯。
- 插件（AGP / Kotlin / detekt / spotless）与它们的工具链**不在锁定范围**：那些版本写在 `plugins { }`
  与根脚本的 `ktlintVersion` / detekt 插件版本里，改版本本来就得动手改文件。
- 生成锁文件要在**联网**状态下跑：`--offline` 时解析不到的配置会被标 `(n)`、不进锁文件，
  于是本地锁出来的那份比 CI 里的小一圈，比对必然对不上。

### 配置缓存（`gradle.properties` 的 `org.gradle.configuration-cache`）

- 开着。收益是**每次调用**省掉配置阶段 —— 本项目两个模块 × 三档变体 = 6 套任务图，
  实测 `./gradlew gate` 从 41 秒降到 3 秒（第二次起）。
- **它有一个前提，不是加一行开关就行**：配置阶段不许出现「只在这 一次成立」的取值。
  这条以前不成立 —— `build_id` 原本是 `resValue("string", "build_id", 格式化(当前时间))`，
  取的是**配置期**的时钟，一开缓存就被冻成第一次那个时刻（一天里出十个包，版本行都是同一个时）。
  现在改成 `GenerateBuildId` 任务的产物（执行期取时间、`SOURCE_DATE_EPOCH` 走 `@Input`）。
- `GenerateBuildId` 还写了 `outputs.upToDateWhen { false }`：**每次构建都重新取时间**。
  因为「现在几点」不是任务输入，默认的增量判定会让它在源码变了、包也变了的情况下仍停在更早那一次
  —— 实测踩到：改一行注释重新出包，包里还是 6 分钟前那个时刻。代价只是资源重新合并 + 重新打包，
  R8 不受影响（字符串资源不进混淆）。
- **它没有降级成警告**（不设 `configuration-cache.problems=warn`）：那条会让「缓存不可用」
  悄悄退回普通构建，性能白丢而 CI 仍然绿。现在有人在配置期引入读时钟 / 读随机数，
  Gradle 会当场报错并指出行号。
- 要临时关掉：`./gradlew --no-configuration-cache <task>`，或删掉 `gradle.properties` 那一行。
