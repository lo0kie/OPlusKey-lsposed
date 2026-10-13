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

接管点是**按候选表逐层降级**挑出来的（`hook/Framework.kt`）：

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

侧键 keyCode 只在 `config/KeyId` 里定义：`KeyId.keyCode` = 735（`KEYCODE_ACTION_BUTTON_CLICK`，Linux 侧是
`BTN_TRIGGER_HAPPY32`），另有一份白名单 `KeyId.acceptedKeyCodes`（= `{735, 780, 781, 782}`，含 ColorOS 策略类
路径的内部注入码，781 的补齐记录见 `device-findings.md` §14.4）。

第一层 `StrategyActionButtonKeyLaunchApp` 确实存在，落在 `oplus-services.jar`，入口是外层
`actionInterceptKeyBeforeQueueing` —— **只挂外层**：内部回调 `$2` 是它的纯转发（§14.2），
挂它只会让同一次按键被回调两次（2026-10-13 起 `Framework.kt` 不再挂），

（`ActionButtonProvider` + SQLite）。**完整的类名 / 方法签名 / URI / 表结构记录在
[`docs/device-findings.md`](device-findings.md)** —— 改 Hook 面之前先读它，不要再靠猜。

## 二、按键是怎么变成手势的

daemon 的判定是 `poll()`
驱动：它自己收原始事件，超时醒来就知道「这段时间没有新事件」，所以能直接判「长按到点了」「双击窗口过了」。

LSPosed 这边拿到的只是「framework 调了一次侧键策略方法」，所以判定必须**改成定时器驱动**（`gesture/GestureDetector.kt`）：

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

## 三、放行（passthrough / native）怎么实现

因为我们 hook 了侧键策略方法，原始按键**已经被我们吃掉了**。所以「放行」不是「不做事」，而是**重新注入一次同一个 keyCode**。

`ActionRunner.injectKeyForForward()` 走的是与 `keyevent:`
完全相同的注入通道（`InputManager.injectInputEvent`），区别只在语义。

例外：拿不到 `KeyEvent` 参数的回调无法校验是不是侧键（吞键会连带吃掉别的键），那一分支**一律放行**，
不存在「回灌给原策略」的原生分支 —— 放行在任何情况下都走重注入。

## 四、配置从哪来、怎么到注入侧

配置的唯一生效来源是**宿主私有目录里的 JSON 文件**（`/data/data/com.oplus.gesture/files/opluskey_config.conf`，
读写走 `config/ConfigStore`，格式见 `config/ConfigCodec`）：

- **宿主进程**（设置页、手势执行）每次按键 / 保存都现读文件 —— 同 uid 直接可读，改完即时生效。
- **注入侧（system_server）读不到那份文件**（EACCES），它只信宿主广播过来的完整配置
  （`config/ConfigBroadcast` → `hook/ConfigChannel`），启动 / 解锁 / 写盘时各发一次。
- 系统「设置 → 快捷键」的 provider（`content://uri.gesturemanagerprovider/action_button`）
  只是**只读诊断项**（`hook/SystemActionButtonConfig`，在宿主进程里读），不参与配置链路。

不能用 `SharedPreferences`：它每个进程各缓存一份，写进去的值别的进程看不到。

## 五、注入侧引用的红线

`hook/` 下的一切都会随注入进入 system_server，因此：

| 允许                                                                            | 禁止                                                                                    |
| ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Android framework **公开** API（`KeyEvent` / `Context` / `Handler` / `Cursor`） | `com.android.server.*` / `android.hardware.input.*` 的**直接引用**                      |
| 按字符串名反射找类（`Reflect.findClass`）                                       | `import androidx.*` / Compose / COUI（判据是「这个类会不会在 system_server 里被执行」） |
| `config/` `Reflect` `XLog` `Hooks`（只依赖公开 API）                            | `hook/` `gesture/` `config/` 下引用 `androidx.*` / `io.github.suqi8.coui.*`             |
| 候选类名只写在 `hook/Framework.kt` 的候选表里                                   | 在 `hook/KeyHooks.kt` 里就地写类名字符串                                                |
| 系统配置只经 `hook/SystemActionButtonConfig.kt`                                 | 在别处自己拼 `content://` 或硬编码列名                                                  |

framework 私有类**一律按字符串名解析**：它们会被 ROM 改名，写死引用等于「换一个 ROM 版本就 `NoClassDefFoundError`
→ 开机循环」。 `StrategyActionButtonKeyLaunchApp` 就是这一类 —— 它在 `com.android.server.policy` 与
`com.oplus.server.policy` 两个包里都出现过，所以两个包名都要试。

`app/build.gradle.kts` 里**故意不声明**这些依赖 —— 这样误加的直接引用会变成**编译错误**，

**`androidx` / Compose / COUI 是进包的**（2026-10-11 起）：`page/` 那一层用 Compose + COUI 渲染设置页。这不违反红线 ——
`page/` 的类只在宿主设置页进程里执行，system_server 里从不执行，而 ART 的类解析是惰性的。要守住的判据是
**「这个类会不会在 system_server 里被执行」**：`hook/` `gesture/` `config/` 三个包不许出现 `import androidx.*` 或
`io.github.suqi8.coui.*`（复核命令见 AGENTS.md 二、1.2）。

## 六、降级设计

- 每个 Hook 单独装，装不上只记日志（`XLog.guard` / `hookGuarded`），不影响其他 Hook；
- 候选接管点**一个都找不到**（ROM 改了类名）→ 模块完全不生效，但系统照常；
- 找到了策略类但方法签名对不上（ColorOS 版本差异）→ 退到下一层候选，三层全落空才放弃；
- 配置读不到 → 退回「侧键不接管」；
- 每个动作执行失败 → 只记日志，不影响这次按键的其他环节。
