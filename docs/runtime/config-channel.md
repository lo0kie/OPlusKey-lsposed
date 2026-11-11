# 四、配置从哪来、怎么到注入侧

配置的唯一生效来源是**宿主私有目录里的一个文件**（`/data/user_de/0/com.oplus.gesture/files/opluskey_config.conf`，读写走
`core/config/ConfigStore`，内容格式见 `core/config/ConfigCodec`）：

- **磁盘上不是裸 JSON**：外面套了一层混淆外皮（`core/config/FileObfuscation`）—— 首行魔数 `OPlusKey-Runtime-1`，
  之后是正文经**两层**变换压成的一行：先「异或 + Base64」，再按一张替身字母表逐字符换写、按位置错开。
  命令库（`opluskey_commands.conf`）同一层皮。这不是加密（密钥与字母表都写在代码里），
  只是挡住「随手打开顺手改坏」；导出包（`.bin`）用的是同一种皮、魔数不同名。
  所以**手改配置文件要连着两层一起改**（解两层、改完再包两层，配方见 `FileObfuscation` 类注释）。
  正文顶层另有 `md5` 字段（`ConfigCodec`）：它是**把这一格摘掉之后**那份 JSON 的 MD5，
  算之前先把 map 段落（`keys` / `app_names`）按键名排序 —— 于是缩进、换行、以及同一段里
  两行的先后都不影响摘要；改内容不重算仍判读不懂。本版之前写出的文件按原始顺序校验，升级不失效。
  首行魔数分两档（运行时 `OPlusKey-Runtime-1` / 导出包 `OPlusKey-Config-1`）；**不做旧格式兼容**：
  首行对不上、或正文认不出（加第二层之前的旧文件）就是读不懂。开发期残留的文件删掉即可 ——
  不退回「当它没写过」，那会把用户配置整份换成默认值（见 `ConfigStore.Load` 的三态）。
- 位置取宿主私有目录的 **DE（设备加密）** 那一份，**不是 CE**：CE 首次解锁前不可见，开机到解锁之间只读得到默认值（见
  `../device/storage.md` 9.9）。
- **宿主进程**（设置页、手势执行）每次按键 / 保存都读一遍配置 —— 同 uid 直接可读，改完即时生效。
  读是**按文件指纹缓存**的（`core/config/ConfigStore.load`：比对修改时间 + 长度）：同一份文件在
  一次按键里可能被读好几次（手势执行一次、顺带那次报活再一次），不缓存就要重复「读盘 + 剥外皮 +
  JSON 解析」。文件一被写，指纹就变，下一次读立刻重解析；本进程自己的写盘另外主动失效。
- **注入侧（system_server）读不到那份文件**（EACCES），它只信宿主广播过来的完整配置（`core/wire/ConfigBroadcast` →
  `inject/ConfigChannel`），启动 / 解锁 / 写盘时各发一次。
- 系统「设置 → 快捷键」的 provider（`content://uri.gesturemanagerprovider/action_button`）只是**只读诊断项**
  （`bridge/host/SystemActionButtonConfig`，在宿主进程里读），不参与配置链路。两条纪律：
  **列名一律先读 Cursor 拿**（随 ROM 版本变过，不许硬编码）；**只能在宿主进程里调** ——
  注入侧用它必 NPE（见 `../device/lsposed.md` 六之五）。写接口**已删**（零调用方），真要用的时候直接
  `ContentResolver.update/insert` 三行就够；不要再往回加绕开 `ContentResolver` 的重反射。

不能用 `SharedPreferences`：它每个进程各缓存一份，写进去的值别的进程看不到。

## 注入侧代取网络（`inject/NetChannel`）

界面代码跑在**宿主进程**，而宿主的清单里没有 `INTERNET`（`reference/gesture.apk` 的 49 条权限里
只有 `ACCESS_NETWORK_STATE`）—— 页面自己发请求必然 `SecurityException`。要出网只能请注入侧代取：
system_server 这一侧实测能连（`../device/lsposed.md` 四十一）。

- 线格式（`core/wire/ModuleWire`）：`ACTION_NET_REQUEST` 问、`ACTION_NET_RESULT` 答，两条都带
  `EXTRA_NET_ID` 配对（可能并发几条）。结果里 `EXTRA_NET_STATUS` = HTTP 码（0 = 没连上）、
  `EXTRA_NET_BODY` = 正文、`EXTRA_NET_ERROR` = 原因。
- **只做 GET、只认 https**、正文截到 `NET_BODY_LIMIT`（32 KB，binder 事务上限）、
  连接与读取各 `NET_TIMEOUT_MS`（8 秒）。这条通道能干的**正好**是「读一段公开文本」，
  多一样能力就多一份被别的代码借去干别的事的机会。
- **DNS 必须走 `Network#openConnection`**：system_server 的 socket 没有「进程默认网络」，
  裸 `HttpURLConnection` 会 `UnknownHostException`（四十一）。先 `ConnectivityManager.activeNetwork`
  取网络，再由它开连接 —— 解析与连接都绑在那张网络上。
- 纪律：**另起线程**（这条广播在 AMS 入口上，阻塞 IO 放主线程等于停住广播分发）、整段兜
  `Throwable`、回包 `post` 到主线程再 `sendBroadcast`（不在 hooker 体内重入 AMS）。
- 没有默认网络时**直接回一条「当前没有网络」**，不去等超时。

# 五、注入侧引用的红线

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

# 六、协议的版本：谁在替「线格式版本号」干活

三条广播通道（配置 / 状态 / 日志）上 extra 的名字与类型都出自 `core/model/ConfigFields`
一处，所以**发送侧与接收侧共用同一份字段表** —— 这不是版本号，但它保证了「同一份源码发出来的
两条广播」不会有名字或类型的偏差。真正会跨版本对不上的是另一种情况：**装完新包，system_server
还跑着旧代码**（注入侧只在开机时加载，AGENTS.md 一、11）。这一档靠三样东西兜住，而不是靠一个
`wire_version` 字段：

1. **`ConfigCodec.VERSION` + md5** —— 落盘那侧的语义变化（DTO 加字段必须抬版本）；
2. **广播指纹**（`ConfigBroadcast.tag`）—— 「设置页发出去的那条」与「注入侧收到的那条」是不是
   同一份，逐 extra 算一遍；对不上就是丢包，不是版本问题；
3. **进程启动时刻 vs 包更新时间** —— 判「注入侧是不是还跑着旧包」。这条判据在
   `ui/LinkCheck` 里是**用户可见**的（首页横幅「需要重启手机」），在日志里另有一行 W。

所以**不要**再加一个 `wire_version`：它能表达的信息已经被上面第 3 条覆盖，而多一个字段就多一处
「两边都要记得抬」的地方 —— 与「加一个动作不该改公共层」同一条纪律（AGENTS.md 一、4）。
反过来，**改 extra 的语义（不是名字、不是类型）时必须想清楚旧侧会怎么读**：旧代码拿不到新字段
就用默认值，这是有意的降级，不是 bug。

# 七、降级设计

- 每个 Hook 单独装，装不上只记日志（`XLog.guard` / `hookGuarded`），不影响其他 Hook；
- 候选接管点**一个都找不到**（ROM 改了类名）→ 模块完全不生效，但系统照常；
- 找到了策略类但方法签名对不上（ColorOS 版本差异）→ 退到下一层候选，三层全落空才放弃；
- 配置读不到 → 退回「侧键不接管」；
- 每个动作执行失败 → 只记日志，不影响这次按键的其他环节。
