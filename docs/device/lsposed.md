# LSPosed 与注入环境

## 六、其它已确认事实

- 设备上**还有别的 LSPosed 模块**在跑：`io.github.chimio.inxlocker` （hook 了
  `ActivityStarter`）、`com.coderstory.toolkit`（CorePatch）等 ——排查时注意别把它们的日志算到本模块头上。
- `com.coloros.shortcuts` 存在（「小布指令」）。
- 长按由系统内部 Handler 消息驱动（见上面 `MSG_ACTION_BUTTON_LONG_PRESS_*`），所以接管后**长按语义会与系统原生长按冲突**
  —— 必须靠挂掉系统的拦截来独占。

### 六之一、LSPosed 作用域：注入 system_server 必须用 `system`（实测）

这是**花了最久才定位的一个坑**，务必记牢：

| 写法                   | 结果                                                                        |
| ---------------------- | --------------------------------------------------------------------------- |
| `scope.list = android` | 模块已注册、已启用（库里 `enabled=1`、state 全绿），但 **日志里一行都没有** |
| `scope.list = system`  | 正常注入 `system_server`                                                    |

- LSPosed 库里 `android` 与 `system`
  两个标识**都存在**（`select distinct app_pkg_name from scope`），所以「写成 android」不会报错，只是**静默不生效**。
- 判据：`cat /data/adb/lspd/log/modules_*.log | grep '(system)\['` —— 同一份日志里能看到
  `(system)[com.coderstory.toolkit, ...]` 这类行，说明 LSPosed
  **确实注入了 system 进程**；此时若没有本模块那行，就是 scope 选错了，而不是模块加载失败。
- 对照记录：`com.coderstory.toolkit` 的 scope 是 `system` → 注入成功；
- **改 `scope.list`
  后光重装无效**：LSPosed 库里那条 scope 记录是**启用模块时写死**的，需在管理器里重新勾选作用域，或直接改 `scope`
  表该行，**然后重启**。

### 六之二、模块入口与 LSPosed 右下角 FAB 的关系（实测）

LSPosed 管理器模块列表右下角那颗「打开模块」的 FAB 由 `AppHelper.getSettingsIntent` 决定，查找顺序：

1. `ACTION_MAIN` + category `de.robv.android.xposed.category.MODULE_SETTINGS`
2. `ACTION_MAIN` + `CATEGORY_INFO`
3. `ACTION_MAIN` + `CATEGORY_LAUNCHER`

**只要命中任一条就显示那颗按钮。**「让按钮消失」的唯一办法是一条都不声明；模块能否被加载与它无关 ——
LSPosed 只读 `META-INF/xposed/` 元数据。

**本模块的选择（2026-10-18 改）**：只声明**一个** `EntryActivity`（`MODULE_SETTINGS`；透明主题、
`noHistory`、不渲染任何东西），命中第 1 条 ⇒ 那颗 FAB 出现，点了由它 `su -c am start` 打开宿主的
`com.oplus.gesture/.actionbuttonkey.ActionButtonSettingsActivity`。

- 为什么不能直接 `startActivity`：那个 Activity 在宿主清单里带
  `android:permission="oplus.permission.OPLUS_COMPONENT_SAFE"`（`aapt2 dump xmltree` 核过）——
  privapp 级权限，普通 app 拿不到，直接起必是 `SecurityException`（`am start` 报的就是这一条）。
- 怎么绕过去：跳板发 `ModuleWire.ACTION_OPEN_SETTINGS` 给宿主主进程，由它起**自己包**的页面
  （同包自起动不查那道权限）。那条接收器是 `RECEIVER_EXPORTED`（发送方是另一个 uid 的普通 app），
  只做「调到前台」一件事，不读配置不改状态 —— 整条链路不碰 root。
- 之前的口径是「一条都不声明、让 FAB 消失」，代价是用户装完找不到入口（真机反馈：第一反应就是点那颗 FAB）。
- 判据落在 CI 第 (4) 项：`MODULE_SETTINGS` / `activity` / `activity-alias` / `LAUNCHER·INFO` / 跳板类名
  必须恰好是 `1/1/0/0/1`，多一个少一个都红。

### 六之三、重装后开机不加载：库里 `apk_path` 过期（实测）

- **Android 每次重装 APK 都会分配新的 `/data/app/~~随机==/` 目录**，目录名与 APK 内容无关，纯随机。
- LSPosed 在 `modules_config.db` 的 `modules` 表里缓存 `apk_path`。若还指向旧目录，开机加载时就是：

    ```
    W/ziparchive : Unable to open '/data/app/~~旧==/.../base.apk': No such file or directory
    E/app_process: Failed to open APK '...': I/O error
    W/LSPosedService: Failure to parse package archive ...
        PackageParserException → IOException: Failed to load asset path ...
    ```

    结果：**本次开机跳过该模块**（之后 LSPosed 会把库刷新成新路径，但这一轮已错过）。

- 判据：库里 `select apk_path from modules where module_pkg_name='io.github.lo0kie.opluskey'` 与
  `adb shell pm path io.github.lo0kie.opluskey` 对比。
  ⚠️ 拷这个库要**连 `-wal` 一起拷**（`modules_config.db` + `modules_config.db-wal`）：最近一次写入还在 WAL 里，
  只拷主库会读到**旧路径**，实测能得出「库 ≠ 真机」的假警报 —— 带上 `-wal` 再查就一致了。
- 修法：把库里该行改成 `pm path` 的当前值，再重启。
- **装机时序（实测，2026-10-20）**：这一档最常见的来路不是「库写错了」，而是**装包那一刻 daemon 没在跑**。
  先前用 `lspctl stop` 停过 daemon（它不会自启），此后装包没有任何进程去收包变更、库自然还停在旧路径；
  紧接着重启，开机那一轮 `LSPosedService` 拿旧路径解析失败 ⇒ `system` 整条跳过，而开机更晚的
  `com.oplus.gesture` / `com.oplus.gesture.ui` 正常 —— 表现就是「设置页能开、自检 `宿主=true 注入=false`」。
  这一轮过去之后 lspd 自己会把库刷成新路径（`modules.apk_path` 与 `pm path` 逐字相同），
  所以**此时只需再整机重启一次**，不必手工改库。
- **正常只需一轮重启**：决定「一轮还是两轮」的唯一判据是**按下重启那一刻库里的 `apk_path` 是否已等于 `pm path`**。
  相等 ⇒ 一轮就注入；不等 ⇒ 这一轮整条跳过，只能再来一轮。所以装配顺序是
  `lspctl status` 通 → 装包 → **用 `tools/device-state.py` 核对路径** → 重启：
  路径一致才重启（一轮）；不一致说明 daemon 当时没处理包事件（见上一条），
  **确认 `lspctl status` 通之后重装一次同一个包**（比手工改库安全，lspd 会自己把行刷成新路径），
  再核对一次才重启。**核对放在重启之前，两轮重启就不必发生。**
  反过来说：daemon 停着的时候别装包 —— 要么先把 daemon 救回来（重启）再装，要么就接受多付一轮。
- 「daemon 在跑就会刷新路径」这条不是猜的：六之三之一 实测过「卸载重装后 `enabled` 与作用域被
  lspd 自己写回库里」—— 那只可能来自它对包增删事件的处理，装包时的 `apk_path` 走同一条路。

### 六之三之一、同样的报错还有第二种来路：增量安装（实测）

- 报错三行与六之三**一字不差**（`Failed to open APK … I/O error` → `Failure to parse package archive`），
  但库里 `apk_path` 是**对的**（刚装完，LSPosed 已自己刷成新路径）。这时别再去比路径 —— 看**安装方式**：

    ```
    adb install -r app.apk                    → Performing Incremental Install   ← 就是这个
    adb install --no-incremental -r app.apk   → Performing Streamed Install
    ```

- 增量安装（Android 12+ 的 `--incremental`）把 APK 留在增量会话里**按需取页**，开机早期 LSPosed 读不到整份文件，
  于是**那个进程整条跳过**。实测表现很误导：`system_server` 侧一行日志都没有（连「模块已加载」都没有），
  而开机更晚的 `com.oplus.gesture` 正常注入 —— 看起来像「只装了半边」，其实是启动时序 + 读不到包。
- 判据：`dumpsys package <pkg>` 拿到 `codePath` 后 `wc -c < base.apk`，大小必须**等于本地产物**；
  不等就是还没落盘。
- 修法：`adb install --no-incremental -r <apk>` 重装一次（新路径同样由 LSPosed 自己刷进库）。
- 同一次重装还实测到：**卸载重装不需要重勾作用域** —— LSPosed 会按包里的
  `META-INF/xposed/scope.list` 把 `enabled=1` 与作用域（`system` / `com.oplus.gesture`）写回库里；
  只有六之三那一档（路径过期）才需要手工改库。

### 六之三之二、怎么判「哪一侧还在跑旧包」（模块自己判，2026-10-20）

旧版靠 `ModuleInfo.BUILD_ID` 当尺子，问题是要**人手维护**：忘改一次就量不出差别
（10-20 的两个包都还挂着 `2026-10-18-0004`，换了包从模块侧看不出任何不同）。现在的主尺子改成时间戳：

- **判据**：`某进程的启动时刻 < 包的 lastUpdateTime` ⇒ 这个进程起的比包还早，它加载的是上一版代码。
  进程里的类只在进程启动那一刻从 APK 加载，之后盘上怎么换都不影响它。
- **换算**：`ProcClock.startWall() = currentTimeMillis - (elapsedRealtime - Process.getStartElapsedRealtime())`。
  两端必须同基准（都用 `elapsedRealtime` 那一族）；拿 `getStartUptimeMillis` 去减 `elapsedRealtime`
  会在深睡之后差出整段时间。三项都只读本进程状态，system_server 里也安全（六之五）。
- **三处上报**：注入侧 `ACTION_INJECT_READY` 带 `EXTRA_INJECT_PROC_START`；宿主
  `ACTION_HOST_SERVICE_UP` 带 `EXTRA_HOST_PROC_START`；设置页在**建页的 IO 线程上**问
  `PackageManager` 要 `lastUpdateTime`（查的是 `ModuleInfo.PACKAGE`，不是 `context.packageName`）。
- **余量**：比大小时留 5 秒。正常换包到开机/开页相差是分钟级；只有「包刚更新完、进程几乎同时起来」
  这种边界会落到几秒内，那时不该判过期 —— 冤枉一次就得让人多重启一次手机。
- **两侧的处置不同**：注入侧过期只能整机重启（一、11）；宿主侧过期用「重启服务」即可，
  宿主是普通应用进程，重开就换新代码（五之一、6）。
- **开机指纹**：`ProcClock.bootWall() = (currentTimeMillis - elapsedRealtime) / 1000 * 1000`，
  同一轮开机内各进程算出来一致、跨一轮必不同。配合 `opluskey_ui.conf` 里的
  `inject_ever_alive` / `inject_alive_boot` 两项，把「没回话」分成三档：从没起来过 / 装好等重启 / 本轮掉线。
- **仍然分不开的一档**：「作用域漏了 `system`」与「勾了但从没重启过」现象完全一样（都是没回话、
  也没有任何历史）。作用域只存在 `/data/adb/lspd/config` 的库里，宿主进程与 system_server 都读不到（六之六），
  `lspctl` 也只有 `status` / `stop` —— 所以这一档的文案只能同时说两件事：先去勾，再重启。

### 六之四、本机 LSPosed 版本与可用工具

- **框架 2.2.1 (7912)、API 102**（`lspctl status` 报的；管理器 APK 显示 2.0.2 属正常，两者版本号体系不同）。
- `lspctl`：`/data/adb/modules/zygisk_lsposed/lspctl`，支持 `status` / `stop`。用法
  `su -c 'sh /data/adb/modules/zygisk_lsposed/lspctl status'`。**没有查作用域的子命令**
  （`Developer mode: no` 时连 `--json` 也只对交互会话有效），作用域只能查库。
- 一条命令拿全：`python tools/device-state.py --port 5038` —— 时间线（`boot_count` / uptime / 开机时刻）、
  意图（库里的 `enabled` / `scope` / `apk_path`）、事实（本轮 `modules_*.log` 里各进程的行数）一起打，
  并把结论映射到六之三 / 六之三之一。设备上没有 `sqlite3`，它是把 db + `-wal` + `-shm` 一次 `tar` 回本地查的。
- **`lspctl stop`（即 `emulated-soft-reboot.sh`）不要用来「软重启」**：实测它只停掉 lspd
  daemon，**不会自动重新拉起**，之后 `status` 报 `Connection refused`，框架停在「zygote 在跑但无 hook 引擎」的坏状态，
- 设备 IP / adb 端口**每次重启都会变**（实测 44029 → 41031 → 44865），且同一台机常同时存在两条连接：`192.168.x.x:port`
  与 `adb-XXXX._adb-tls-connect._tcp`。

**这是本项目排查成本最高的一次错误，必须记下来。**

开发机上同时连着**两台手机**：目标机经 WiFi ADB 连接，另一台经 USB 连接。排查时 `adb -s`
用错了端点，**所有证据都采自那台无关设备**，于是得到一整套逻辑自洽但完全错误的结论：

| 错误结论                                                 | 真相                                                                                                            |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| 「`pm path io.github.lo0kie.opluskey` 为空，模块没装上」 | 目标机上模块**装得好好的**（那台 USB 机上确实没装）                                                             |
| 「LSPosed `modules` 表里没有 opluskey，模块从未被识别」  | 目标机库里 `modules` 表**有** opluskey（且该库 `-wal` 里有 416KB 未 checkpoint 数据，只拉主库看到的还是旧快照） |
| 「`modules_state` / `scope` 表里没有它，模块从未被启用」 | 目标机上模块**已启用、scope 已设为 `system`**，开机日志 5/5 接管点全挂上                                        |
| 「`adb reboot` 没生效，uptime 还有 13.3 小时」           | 那台 USB 机确实没重启；目标机**重启正常**（uptime 22 分钟，日志时间戳吻合）                                     |

期间还差点去手改 LSPosed 数据库（那台机器的库），全是无用功。

**纪律**（已写入 AGENTS.md 五之一）：

1. 动手前先 `getprop ro.product.model` + `ro.build.id` 核对机型，确认是目标设备。
2. 重启后先看 `cat /proc/uptime`，秒数小（几百）才算真重启；再核对 `/data/adb/lspd/log/modules_<时间戳>.log`
   的文件名时间戳是否是刚才。
3. 拉 `modules_config.db` **必须连 `-wal` / `-shm` 一起**，否则读到旧快照。
4. 设备上没有 `sqlite3`，一律拉回本地用 Python 解析。

### 六之五、`ContentResolver` 在 system_server 里**不可用**（实测）

**现象**：在 `onSystemServerStarting` 里
`contentResolver.query(content://uri.gesturemanagerprovider/action_button, ...)`，无论 Context 从哪来，都 NPE：

```
java.lang.NullPointerException: Attempt to invoke interface method
  'android.app.ContentProviderHolder android.app.IActivityManager.getContentProvider(
     android.app.IApplicationThread, java.lang.String, java.lang.String, int, boolean)'
  on a null object reference
    at android.app.ActivityThread.acquireProvider(ActivityThread.java:9601)
    at android.app.ContextImpl$ApplicationContentResolver.acquireUnstableProvider(ContextImpl.java:4209)
    at android.content.ContentResolver.acquireUnstableProvider(ContentResolver.java:2708)
    at android.content.ContentResolver.query(ContentResolver.java:1298)
```

**根因**：`ActivityThread.acquireProvider` 需要一个非 null 的 `IApplicationThread` （`ActivityThread.mAppThread`）。而
**system_server 的 `ActivityThread.mAppThread` 永远是 null** —— system_server 从不走 `attachApplication`
这条普通 App 的绑定流程。

原因：NPE 里的 `mAppThread` 取自 **`ContextImpl.mMainThread`**，而 `mMainThread` 是 `ContextImpl` **自己的字段**
—— 在 system_server 里借来的任何一个 Context，它的 `mMainThread` 仍然指向 system_server 的那个
`ActivityThread`。所以「换个 Context」解决不了这个问题：**变的是 `mAppContext`，不是 `mMainThread`**。

**第二条死路：`IActivityManager.getContentProvider` 也不行**（用 `app_process` 独立验证过）：

```
getContentProvider(IApplicationThread, String, String, int, boolean)   ← 只有这一个重载，5 参
传 null caller → SecurityException: null IApplicationThread when getting content provider
```

两点记下来免得重走：这个接口**只有一个 5 参重载**（不是 4 参，名字 × 两个 String 那个）；它**显式拒绝 null
caller**，所以「caller 传 null」的思路从根上不成立。

从 AMS 自己的 provider 表里捞。provider 一旦被任何进程拉起，AMS 的 `mCpHelper`（`ContentProviderHelper`）→
`mProviderMap`（`ProviderMap`）→ `mSingletonByName` 里就存着一个可用的
`IContentProvider`（`ContentProviderProxy`），走它**不经过 `getContentProvider`**，也就没有那个 `IApplicationThread`
检查：

```
ActivityManagerService.mCpHelper            : ContentProviderHelper
  → ContentProviderHelper.mProviderMap      : ProviderMap
    → ProviderMap.mSingletonByName          : HashMap<String, ContentProviderRecord>
    → ProviderMap.getProviderByName(name)   : ContentProviderRecord
      → ContentProviderRecord.provider      : IContentProvider
```

`mProviderMap` **不在 AMS 上**（Android 14+ 把 provider 管理抽到 `ContentProviderHelper`）。另外
`IContentProvider.query` 的签名随 API 变过，要按参数个数试：API
30+ 是 5 参（`attribution, uri, projection, Bundle queryArgs, signal`），更早是 6 参（`…, selection, selectionArgs, signal`）。

**拿 AMS 本体**比拿 provider 更难 —— 本机这四条逐条实测过：

| 路                                                                         | 结果                                                                                                                                                             |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ActivityManagerService.sSelf` / `sInstance` / `IActivityManagerSingleton` | **都不存在**（本机 AMS 没有静态单例）                                                                                                                            |
| `ActivityTaskManagerService.mActivityManagerService`                       | 拿不到 ATMS 本体（`ServiceManager` 给的是 proxy）                                                                                                                |
| `WindowManagerService.mActivityManager` / `mAm`                            | 同上                                                                                                                                                             |
| **`android.app.ActivityManager.getService()`**                             | **只有这条通** —— system_server 里 `Stub.asInterface` 对本地 binder 走 `queryLocalInterface`，直接给本地 AMS 对象；别的进程只会拿到 proxy（那种情况应返回 null） |

**推论**（对所有注入 system_server 的模块都成立）：

|---|---| | `Context.getSystemService` | （不碰 `mAppThread`） | |
`Context.getContentResolver().query/insert/update/delete` | 必 NPE | | `Context.startActivity` | 需带
`FLAG_ACTIVITY_NEW_TASK`，且受后台启动限制 | | `Context.sendBroadcast` | 一般可用 | |
`ActivityThread.getSystemContext()` | 只能用于 `getSystemService` |

### 六之六、`/data/adb` 对 system_server **不可读**（SELinux，实测）

模块的配置一开始放 `/data/adb/OPlusKey/config.conf`（沿用 OPlusKey daemon 路径）。 `plus_enabled=1`：

```
$ ls -Z /data/adb/OPlusKey/config.conf
-rw------- 1 root root u:object_r:adb_data_file:s0  config.conf
```

`/data/adb` 的 SELinux 类型是 **`adb_data_file`**，而注入代码跑在 **`u:r:system_server:s0`** 域 —— 该域对
`adb_data_file`
**没有读权限**。文件内容、权限位都没问题，是**内核在 SELinux 层直接拒绝**，表现成「读到空文件」→ 解析出空 map
→ 全走默认值。

**system_server 读得到的位置**（实测）：`/data/system/OPlusKey/config.conf` —— 该目录类型是
**`system_data_file`**，system_server 域可正常读写。目录 `0755`、文件
**`0644`**（**不能 0600**，否则跨 uid 读取仍会被拒）。

> **但「读得到」不等于「能当共享配置用」**（2026-10-11 补正）：这个位置 **设置页写不进去**（域 `u:r:platform_app:s0` 对
> `system_data_file`
> EACCES），而设置页才是真正改配置的那一侧 ⇒ 放这里只会让注入侧读到**永不更新的陈值**，比读不到更糟（日志会显示「接管=true」，看不出是通道没通）。两边都能碰的位置实测只有**宿主 App 的私有目录**，见 `settings-page.md` 8.7 与六之七。

> 一般结论：**注入 system_server 的模块，配置不能放 `/data/adb`**，要放 `system_data_file` 类的位置（`/data/system/`
> 下）。

### 六之七、跨进程通道：四条里只有广播通，其余已删（实测，2026-10-11）

「设置页（宿主 `.ui` 进程）写 → `system_server` 读」这件事，一共试过四条通道：

|---|---|---| | 宿主私有目录 `/data/data/com.oplus.gesture/files/…` | 注入侧读不了 | 标签 `app_data_file:s0:c512,c768`
带 **MCS 分类**，`system_server` 跑在 `s0`，SELinux 直接拒（`open failed: EACCES`） | |
`/data/system/OPlusKey/config.conf` | 设置页写不进去 | `root:root 0644`、目录 `0755`（见六之六的补正） | |
`Settings.Global`（`opluskey_plus_enabled`） | 注入侧读不到 | 设置页写得进（宿主持有
`WRITE_SECURE_SETTINGS`，`dumpsys package` 实测 `granted=true`），但注入侧要靠 provider 读，而 `settings`
那条 provider 记录在 system_server 里拿不到实例（见六之五） | | **广播**（宿主 `sendBroadcast` → 注入侧挂 AMS 的
`broadcastIntentWithFeature` 入口） | **通了** | 不依赖文件、不依赖 provider、不依赖权限 |

**由此定下的架构（2026-10-11 收窄，不要再往回加）**：

1. **注入侧不读任何配置文件**。它唯一需要知道的是「这个键接管了没有」这一个布尔，「广播 → `Settings.Global`
   → 外部媒体文件 → 文件兜底」四级回落链、以及 `CONFIG_CACHE_MS` 那套配置缓存，**全部删除**。
2. **时间参数与动作一律由宿主进程读**（同 uid，直接读宿主私有目录）。
3. **`hook/ProviderAccess.kt` 与 `hook/SystemActionButtonConfig.kt` 的 AMS 反射路径删除**。它俩的唯一用途是「读
   `Settings.Global`」（通道已死）与「读系统侧键映射」（README 自认的已知限制，本来就读不到）。**想读系统侧键映射就在宿主进程里读**
   ——宿主就是 `ActionButtonProvider` 的宿主，`ContentResolver` 在那里完全可用。
4. 保留 `bridge/inject/SystemContext.kt`：注入侧发广播 / `startService`
   仍要一个可用的 Context（`ActivityThread.getSystemContext()` 的那个 `mAppThread` 是 null，`sendBroadcast`
   内部要它，所以必须借一个真实 App 进程的 Application Context）。

## 十五、诊断提示：system_server 侧的 `XLog.i` **不进** LSPosed 日志页（实测，2026-10-13）

排查 hook 安装时踩到的坑，记下来省得下次又怀疑「hook 没装上」：

```
$ grep 'io.github.lo0kie.opluskey,OplusKey' modules_*.log | grep '(system)' | grep -oE '[VWIDEF]/' | sort | uniq -c
    258 W/          ← system_server 侧：只有 W，零 I/D

$ grep 'io.github.lo0kie.opluskey,OplusKey' modules_*.log | grep 'I/LSPosedFramework' | head
... (com.oplus.gesture)    模块已加载：process=com.oplus.gesture → 无关进程
... (com.oplus.gesture.ui) 设置页换页 Hook 安装完成：换页 1/1 ...
$  ← (system) 一条 I/ 都没有
```

⇒ **同一份 debug 包**（`VERBOSE_LOG=true`、`PageDebugChannel` 类在包里 ⇒ 确认是 debug）， `XLog.i` 在
`com.oplus.gesture*` 侧正常落盘，在 `(system)` 侧**被 LSPosed 的进程级日志级别挡掉**。

**危害**：`KeyHooks.install` 里那句「已 Hook 侧键接管点：…」是
`XLog.i`，在 system_server 侧看不到 ——看起来像「一个 hook 都没装上」，实际 `XLog.w`
的「已把侧键手势转给宿主」在持续刷，**hook 是好的**。

**对策**：判断 hook 是否装上，**认 `XLog.w` 的运行时行为日志，不要认安装期的 `XLog.i`**。若确需在 `(system)`
侧留安装痕迹，把那几行改成 `XLog.w`（或查 LSPosed 设置里的日志级别）。

## 四十一、注入侧能不能联网：**能，但必须先绑默认网络再解析 DNS**（实测，2026-11-02，PLK110 / ColorOS 17）

「检查更新」这类功能要不要放注入侧，取决于 system_server 这一侧到底能不能连出去。三件事一次验完
（探针：调试通道的 `net` op，见 `../runtime/debug-channel.md`；同一条广播里连打三步）：

```
probe/net：uid=1000 activeNetwork=103
probe/net：裸 TCP 223.5.5.5:443 通了
probe/net：DNS api.github.com → api.github.com/20.205.243.168
probe/net：HTTP 200（…/releases/latest，DNS 拿到 1 个地址）
```

- **权限层**：`dumpsys package android` 里 `android.permission.INTERNET: granted=true` —— uid 1000
  （system）持有 `INTERNET`，这一层从来不是问题。
- **SELinux 层**：`system_server` 域**允许** inet socket —— 字面 IP 的裸 TCP 直接连通，
  没有 `avc: denied`。⚠️ 这一条**只能在这个域里真跑一次才算数**：域里 `exec` 外部程序会被拒
  （`runcon u:r:system_server:s0 toybox nc …` 报 `exec … Permission denied`，rc=126），
  而 `plat_sepolicy.cil` 也不是全貌（连 `untrusted_app` 的 inet 规则都不在里面）——
  两条「看策略推结论」的路都走过，都不可靠。
- **DNS 层才是坑**：直接 `HttpURLConnection` 打开 URL 会
  `java.net.UnknownHostException: Unable to resolve host "api.github.com": No address associated with hostname`。
  不是权限拒绝，是 **system_server 的 socket 没有「进程默认网络」**（app 那份默认网络由
  `ConnectivityManager` 绑在进程上，system_server 没有）——裸 `InetAddress.getByName` 没有网络可查。
  正确姿势：先取 `ConnectivityManager.activeNetwork`，再用 `Network#getAllByName` 解析
  （它把这次查询绑在那一张网络上），拿到地址后照常连。
- 对照：`dumpsys network_time_update_service` 里 NTP 也「成功」，但它连的是
  `127.0.0.1:123`（ROM 自己的本地代理）—— **loopback 通不代表能出网**，别拿它当证据。

## 11.1 两台「状态栏」不是一回事

| 方法                                          | 在哪                                             | 谁在用                                         | 权限                                                                      |
| --------------------------------------------- | ------------------------------------------------ | ---------------------------------------------- | ------------------------------------------------------------------------- |
| `IStatusBarService.togglePanel()`             | `StatusBarManagerService`（system_server）       | `PhoneWindowManager.toggleNotificationPanel()` | `STATUS_BAR` **或** `EXPAND_STATUS_BAR`（见 `checkCanCollapseStatusBar`） |
| `StatusBarManagerInternal.toggleRecentApps()` | 同上的**内部接口**（`LocalServices`）            | `PhoneWindowManager.toggleRecentApps()`        | **无权限校验**（内部接口）                                                |
| `expandNotificationsPanel()`                  | `StatusBarManagerService`                        | App / 无障碍                                   | `EXPAND_STATUS_BAR`（**宿主没持有**，会抛 SecurityException）             |
| `toggleRecentApps()`                          | **这台 ROM 的 `StatusBarManagerService` 上没有** | ——                                             | ——                                                                        |

三条铁律：

1. **不要用 `expandNotificationsPanel()`** —— 宿主没 `EXPAND_STATUS_BAR`，
2. **`toggleRecentApps` 不在 `IStatusBarService` 上** —— 它是 `StatusBarManagerInternal`
   上的方法，AIDL 那边没有对应事务，`ServiceManager.getService("statusbar")` 拿到的对象上也找不到它。要走
   `LocalServices.getService(StatusBarManagerInternal.class)`。
3. **别把 `IStatusBarService` / `framework.jar` 里的类往宿主进程搬** —— App 侧隐藏 API 限制， `Class.forName` 拿不到
   `IStatusBarService$Stub`；这两条都只能从 **system_server 侧**做（所以走 `ACTION_STATUSBAR` 广播转交）。

## 11.2 `clearCallingIdentity` 在这里是把双刃剑（两条路相反）

> **2026-10-19 复核：本节原来那句前提「hook 跑在宿主的 binder 线程上」不成立。**
> `KeyHooks.dispatchAction` 对 `ACTION_STATUSBAR` 是 `handler.post { … }`，handler 绑的是
> `Looper.getMainLooper()` ⇒ 真正执行时 `Binder.getCallingUid()` 已经是 system_server 自己的
> 1000，`asSystem` 那次 clear / restore 是个**空操作**。

实测（PLQ110 / ColorOS 17，`1.0.0-debug`）——用宿主 uid（10277）发广播，走的是与产品完全相同的
那条路（`am broadcast` 以 10277 身份发出 → `KeyHooks` 校验通过 → 主 looper 执行）：

| 指令                 | 日志                                                           | 屏幕                 |
| -------------------- | -------------------------------------------------------------- | -------------------- |
| `cmd=notifications`  | `已在本机调 IStatusBarService.togglePanel（notifications）`    | 通知中心**拉下来了** |
| `cmd=control_center` | `已在本机调 IStatusBar.animateExpandSettingsPanel（控制中心）` | 控制中心展开         |

所以 `togglePanel()` 这条**保持现状**：`asSystem` 不是失败点，删了行为也不会变 ——
留着只为「哪天真从 binder 线程直调」那一档兜底。原来那句「清了反而判丢」是**推断、未实测**。

- `StatusBarManagerInternal.toggleRecentApps()`：**要清**。它没有权限校验，但下游（SystemUI
  → 启动器代理）看到的调用方应该是 system（uid 1000），与 `PhoneWindowManager` 自己调时一致。
  （这一条同样**未经实测**：`asSystem` 在主 looper 上本来就是 1000，测不出差别。）

- 两个码在 `PhoneWindowManager` 里都只在 **`handleKeyGestureEvent(KeyGestureEvent, IBinder)`**
  这一支被处理 ——那是 Android 16 的**按键手势**入口（`KeyGestureEvent` 由输入子系统在手势场景下产生），模块下发的普通
  `KeyEvent` 进不去那一支。
- 对照实验：`input keyevent 83` **能**展开通知栏、`input keyevent 187` **不能**唤起概览（用启动器的
  `LRV ... toState=Overview` 日志判定，手势 `input motionevent` 上滑悬停可以）。
- 结论：这两个码不要靠注入，走 11.1 那两条原生调用。

## 11.4 `KeyEvent` 构造器的坑：6 参的第 6 个参数是 **metaState**

`android.view.KeyEvent`（在 `framework.jar` 的 **classes4.dex**）：

```
(JJIII)V     = (downTime, eventTime, action, code, repeat)
(JJIIII)V    = (…, repeat, metaState)     ← 「6 参」是它，第 6 个是 metaState！
(JJIIIIIIII)V= (…, metaState, deviceId, scancode, flags, source)   ← 要用这个
```

（`0x101 & META_SHIFT_ON != 0`）—— HOME /
BACK 这些不看 metaState 的键照常生效，所以「注入是通的」这个结论站得住，掩盖了问题。**现在优先用 10 参构造器、metaState 显式给 0。**

补充：`source` 其实**不影响生效与否** —— `input keyevent -s 257 83` 与 `input keyevent 83`
都能展开通知栏。别再把「注入不生效」归到 source 上。

```bash
# 直接驱动宿主走完一条动作链（走的就是 system_server 转发那条路）
UP=$(adb shell cat /proc/uptime | tr -d '\r' | cut -d' ' -f1 | cut -d. -f1)
adb shell "su -c 'am broadcast -a com.lookie.opluskey.GESTURE -p com.oplus.gesture \
  --es key plus --es phase single --el sent_at $((UP*1000))'"
```

- `sent_at` 必须带（`SystemClock.elapsedRealtime()` 毫秒，≈ `/proc/uptime`
  秒×1000），否则会被宿主的「过期手势」判定丢掉（>2s 丢）；
- 宿主的接收器是 `RECEIVER_NOT_EXPORTED`，**只有 root（uid 0）能投递**， `shell`（uid 2000）投不进去、`am broadcast`
  会显示 `result=0`。

步骤的落地时间差三个数量级：`keyevent:` 注入**同毫秒**生效；「最近任务 / 通知栏」跨四个进程（几百毫秒）；`shell:`
另起进程、完全异步。 `test`（HOME → 小布识屏 → 最近任务 → BACK → 通知栏）5 步曾在 **27ms** 内全部派发完，

- 实现：`host/action/ActionRunner.runSteps` 用 `handler.postDelayed` 串成链（**绝不能 `Thread.sleep`**
  —— 这条链路在宿主主线程上，见 AGENTS.md 的 execShell 前车之鉴）；
- 间隔**每条命令可设**：`Command.stepDelayMs`（默认 400ms、0 = 不等），命令库文件第 4 列，设置页「命令」卡最后一行滑杆。

> 结论：**宿主调错了入口**。公共 API 走的那一支会被 ColorOS 的 ringer
> delegate 改写成振动，必须走 SystemUI 那条内部入口 —— 而宿主**刚好有**那条入口要的权限。

`dumpsys audio` 的 ringer 历史（`com.oplus.gesture` = 宿主 uid 10277）：

```
10-12 09:38:38:606 setRingerMode external to 1, caller=com.oplus.gesture, delegateModified=1   ← 要振动(1)，得到 1
10-12 09:38:41:496 setRingerMode external to 0, caller=com.oplus.gesture, delegateModified=1   ← 要静音(0)，被改成 1
```

`delegateModified`
就是 delegate 的**返回值**，也就是最终落下去的档位（见 `actions/audio.md` 12.2 的日志构造）。⇒ 请求**送进去了**，是被 framework 内部改写的；**不是权限被拒**
—— 被拒会抛 `SecurityException: Not allowed to change Do Not Disturb state`，日志里没有这一条。

**外部的静音请求还会把系统送进免打扰**（10-20 15:03 实测）：同一条 `setRingerMode external to 0` 之后，
`dumpsys notification` 的 `manualRule` 变成 `zenMode=ZEN_MODE_IMPORTANT_INTERRUPTIONS`、`state=STATE_TRUE`，
而 `enabler` 记的是 **`com.oplus.gesture`**（宿主包名），时间戳与那条写入同一毫秒
（`lastActivation=07:03:21.604Z` ↔ `external to 0` 15:03:21.606）。也就是说用户按一次键会看到
「免打扰被打开」。同一时段 external 0 也有 `delegateModified=0`（真的落成静音）的记录 ——
**结果取决于当时 zen 状态，不稳定**。

⇒ 结论：**落档一律走内部通道**（`setRingerModeInternal`），公共接口只作退路。「使用 ColorOS 接口」
那个开关管的是切完那一下的触觉与流体云，**不决定走哪条通道**。
