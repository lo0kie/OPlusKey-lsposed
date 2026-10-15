> 系统镜像里读出来的。实现必须对齐本文件；本文件与代码冲突时以本文件为准，并回头修代码。

> ⚠️ **本文是按时间累积的实测记录。** 2026-10-14 做过一次包结构重构与配置位置迁移，早于该日的章节里包名（`hook/` `page/`
> `gesture/` `config/`）与配置路径（CE）按**当时**的写法保留 —— 今天的对应关系见 `docs/architecture-refactor.md`
> 的映射表，配置位置见 9.9；指向**现存文件**的引用已更新为新路径。

## 一、目标设备

| 项         | 值                                                                                  |
| ---------- | ----------------------------------------------------------------------------------- |
| 机型       | OnePlus `PLK110`                                                                    |
| 系统       | Android 17 / `PLK110_17.0.0.105(CN01)`                                              |
| adb        | over network `192.168.0.104:44029`                                                  |
| 侧键存在   | `ro.oplus.key.actionbutton = 1`                                                     |
| 设置包     | **`com.android.settings`**（不是 `com.oplus.settings`）                             |
| 侧键实现包 | **`com.oplus.gesture`**（APK：`/system_ext/app/OplusGestureUI/OplusGestureUI.apk`） |
| 框架侧实现 | **`oplus-services.jar`**（`/system/framework/oplus-services.jar`）                  |

## 二、真正的接管点（框架侧）

实现在 **`com.android.server.policy.StrategyActionButtonKeyLaunchApp`**，位于 `oplus-services.jar`（**不在**
`com.oplus.server.policy`，也**不在** `com.android.server.policy` 的 AOSP 部分）。

### 入口方法（**只挂外层那一个**）

> ⚠️ 这一节原写「两种，都要挂」，与 `inject/Framework.kt`
> 的实现和 §14.4 的结论相反（2026-10-15 审计 #39）。照那行做会重造幽灵长按 —— 正确口径是**只挂外层**
> `actionInterceptKeyBeforeQueueing`；`$2` 是外层方法的**纯转发**（12 条指令、 `preCondition`
> 恒 true），挂它 0 收益，只会让同一次按键被回调两次。

```
com.android.server.policy.StrategyActionButtonKeyLaunchApp
    # actionInterceptKeyBeforeQueueing(Landroid/view/KeyEvent;IIZZ)I   ← 只挂这一个

com.android.server.policy.StrategyActionButtonKeyLaunchApp$2
    # interceptKeyBeforeQueueing(Landroid/view/KeyEvent;IIZZ)I
    # preCondition()Z                                                  ← 不挂（见上）
```

`$2` 是一个内部回调类（`onChange` 观察者 +
`interceptKeyBeforeQueueing`），说明这套实现是**通过 ContentObserver 监听配置变化**再决定拦截行为的。

### 其余已确认成员（可作降级/诊断依据）

```
getInstance()Lcom/android/server/policy/StrategyActionButtonKeyLaunchApp;
initActionButton()V
checkInterceptConditions(Z)Ljava/lang/Integer;
interceptActionKeyDown(Landroid/view/KeyEvent;)V
interceptActionKeyUp()V
injectActionButtonPressKeyEvent(Landroid/view/KeyEvent;)V
readActionButtonSwitchState(I)V
readActionButtonSceneName()V
readActionButtonGamePreventValue(I)V
updateActionKeyStatistics(ZZ)V
initInBootCompleted()V
initStrategy()V
getLongPressTimeout()J
isNotSupportWake()Z
isNotSupportedPocketMisTouch()Z
longPressStartVibrate()V
pokeUserActivity()V
releaseWakeLock()V
wakeup()V
getStartAppInScreenOffState()Z
setStartAppInScreenOffState(Z)V
uploadCollectData(Ljava/lang/String;)V
```

关键字段（反射时可读）：

```
mActionButtonSwitchObserver
mHasAiSmartKeyFeature
mPowerManager
mLastPokeUserActivityTime
mHasInjectLongPressDownEvent
```

关键字符串常量：

```
ACTION_BUTTON_CLICK
ACTION_BUTTON_CONTENT_URI
OPLUS_ACTION_BUTTON_SWITCH_STATE_URI
GAME_ACTION_BUTTON_PREVENT_STATE_URI
OPLUS_ACTION_BUTTON_SWITCH_STATE
GAME_ACTION_BUTTON_PREVENT_STATE
```

Handler 消息（长按内部流程，说明**长按是系统自己判的**）：

```
MSG_ACTION_BUTTON_LONG_PRESS_DOWN
MSG_ACTION_BUTTON_LONG_PRESS_UP
```

功能开关属性：

```
ro.oplus.key.actionbutton
OPLUS_FEATURE_ACTION_BUTTON
SUPPORT_ACTION_BUTTON_GAMEMODE_PREVENT
SUPPORT_GAME_PREVENT
```

## 三、配置存储（设置侧）

配置**不在** SharedPreferences、**不在** SettingsProvider，而在 `com.oplus.gesture` 自己的 **ContentProvider +
SQLite**：

```
authority : uri.gesturemanagerprovider
path      : action_button
URI       : content://uri.gesturemanagerprovider/action_button

provider  : com.oplus.gesture/.actionbuttonkey.database.ActionButtonProvider
database  : actionbuttonDatebase.db   (version = 1)
table     : action_button
已知列    : scene_name   （另有 scene_id / scene_owner / scene）
```

**权限**：`oplus.permission.OPLUS_COMPONENT_SAFE` —— `adb shell content query` 会被拒（已验证）：

```
SecurityException: Permission Denial: opening provider
com.oplus.gesture.actionbuttonkey.database.ActionButtonProvider
from (null) (pid=29374, uid=2000) requires oplus.permission.OPLUS_COMPONENT_SAFE
```

`ActionButtonProvider` 的完整方法表（它是标准 ContentProvider）：

```
bulkInsert(Uri, ContentValues[])I
delete(Uri, String, String[])I
insert(Uri, ContentValues)Uri
query(Uri, String[], String, String[], String)Cursor
update(Uri, ContentValues, String, String[])I
getType(Uri)String
onCreate()Z
```

> 结论：**在 system_server 里读它权限上没问题**（system uid 不受 `OPLUS_COMPONENT_SAFE` 约束）。 但**不能走
> `ContentResolver`** ——那条路在 system_server 里必 NPE，见「六之五」。

## 四、「设置 → 快捷键」入口

```
Action : "com.oplus.gesture.StartActionButtonSettingsActivity"
Activity: com.oplus.gesture/.actionbuttonkey.ActionButtonSettingsActivity
```

同包内的兄弟入口（可作降级候选）：

```
com.oplus.gesture/.guide.GestureMainActivity
com.oplus.gesture/.sprucekey.SpruceSettingsActivity   (action: com.oplus.gesture.StartSpruceSettingsActivity)
com.oplus.gesture/.aon.AONGestureMenuActivity
com.oplus.gesture/.custom.AllScreenOffGestureActivity
```

`ActionButtonSettingsActivity` 的 `onCreate` 受 `oplus.permission.settings.LAUNCH_FOR_EXPORT` 保护 —— 已验证 adb
shell 直接 `am start` 会被拒：

```
SecurityException: ... requires oplus.permission.settings.LAUNCH_FOR_EXPORT
```

## 五、系统支持的侧键动作（从资源名读出）

设置页里可选的侧键功能（`action_button_*_title` / `_oval_*`）：

```
非操作 non_operation        相机 camera
手电筒 torch                截屏 screenshot
录音 recorder               免打扰 ringer_mode / not_disturb
闪念 flash_memory           翻译 translate
OCR 扫描 ocrscanner         控制中心 control_center
启动应用 launch_app         流体云 fluid（ring / long_press / vibrate / silent）
```

⚠️ **`control_center` 只是资源名，没有可照抄的调用目标**：场景表（§17.4 那份实测全列）里**没有** 这一行 —— 拿不到
`scene_owner` / `*_action_info`。模块侧的做法是自己试候选，**已真机定死**（2026-10-14 22:20， `PLK110`，重启后）：

| 项            | 结论                                                                                                                                                                                                                    |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 命中          | **`IStatusBar.animateExpandSettingsPanel(String)`**（子面板传 `null`），经 `StatusBarManagerService.mBar` —— 那条 binder 通 SystemUI，**这一层没有权限检查**（同一个服务上 `togglePanel()` 一直是通的，就是同一个原因） |
| 怎么调        | 在 system_server 里 `ServiceManager.getService("statusbar")` 拿到服务实例 → 反射读它的 `mBar` 字段 → 在这个对象上调 `animateExpandSettingsPanel(null)`                                                                  |
| 判据行（`w`） | `已在本机调 IStatusBar.animateExpandSettingsPanel（控制中心）`                                                                                                                                                          |
| 备用候选      | 服务那层的 `expandSettingsPanel(...)`（AOSP 在那里有 `enforceExpandStatusBar()`）—— **本次没用上**，留着当兜底；两条都落空时代码会把带 `Expand` / `Panel` 的方法名打进日志                                              |
| 通知中心      | 同服务上的 **`IStatusBarService.togglePanel()`**（0 参，展开 / 收起），两条动作共用一条跨进程通道                                                                                                                       |

代码在 `inject/system/StatusBarBridge.expandControlCenterLocal`；⚠️ 注入侧 ⇒ 改它要整机重启。

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

**只要命中任一条就显示那颗按钮。** 因此「让按钮消失」的唯一办法是这与模块能否被加载无关 —— LSPosed 只读
`META-INF/xposed/` 元数据。

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

### 六之四、本机 LSPosed 版本与可用工具

- **框架 2.2.1 (7912)、API 102**（`lspctl status` 报的；管理器 APK 显示 2.0.2 属正常，两者版本号体系不同）。
- `lspctl`：`/data/adb/modules/zygisk_lsposed/lspctl`，支持 `status` / `stop`。用法
  `su -c 'sh /data/adb/modules/zygisk_lsposed/lspctl status'`。
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
> EACCES），而设置页才是真正改配置的那一侧 ⇒ 放这里只会让注入侧读到**永不更新的陈值**，比读不到更糟（日志会显示「接管=true」，看不出是通道没通）。两边都能碰的位置实测只有**宿主 App 的私有目录**，见 8.7 与六之七。

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
4. 保留 `inject/system/SystemContext.kt`：注入侧发广播 / `startService`
   仍要一个可用的 Context（`ActivityThread.getSystemContext()` 的那个 `mAppThread` 是 null，`sendBroadcast`
   内部要它，所以必须借一个真实 App 进程的 Application Context）。

## 七、由此确定的实现方案

> **实测状态（2026-10-10，PLK110 / Android
> 17）**：链路**端到端跑通**。开机 5/5 接管点全挂上；单击 / 双击 / 长按三种手势实测判定正确、动作分发正确。

1. **Hook 面**：`StrategyActionButtonKeyLaunchApp#actionInterceptKeyBeforeQueueing`
2. **配置面**：配置只放**宿主 App 私有目录**（`/data/user_de/0/com.oplus.gesture/files/`），由宿主进程（判定 + 执行）读，**热加载**、改完立即生效、**无需重启**。注入侧**不读配置**（它读不到，见六之七）；它唯一需要的主开关布尔走广播。系统侧键映射由**宿主进程**用
   `ContentResolver` 读 `content://uri.gesturemanagerprovider/action_button`（见六之五 / 六之七）。
3. **降级**：任一环失败只记日志，绝不抛给 system_server。provider 读失败（本机必然，见 六之五）与 `shell:`
   不可用（SELinux）都属已知限制， **不影响按键接管**。
4. **动作验证注意**：ColorOS 包名与 AOSP 不同（相机 `com.oplus.camera`），配错只报 `解析不到界面`，不是 Hook 问题。

## 八、侧键设置页（换页劫持用，实测）

### 8.1 类结构

```
com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity   (11 个内部类 $a~$k，混淆严重)
  └ extends BaseActionActivity                                  ← 页面骨架在这里
      └ extends com.oplus.gesture.BaseActivity
```

`BaseActionActivity` 关键成员：

```
字段  F : com.coui.appcompat.toolbar.COUIToolbar     ← 宿主工具栏
方法  onCreate(Bundle)                                ← 只有 70 个 16-bit code units
      onConfigurationChanged(Configuration)
      onDestroy()
```

`onCreate` 的全部动作（`dexdump -d` 读出）：

```
invoke-super BaseActivity.onCreate(savedInstanceState)
getWindow().getDecorView().setForceDarkAllowed(false)
getWindow().setNavigationBarContrastEnforced(false)
getWindow().setNavigationBarColor(0)
setContentView(R.layout.activity_base_action)          ← 整页只有一个布局
f0(); g0(); e0()                                       ← 空壳钩子，由子类覆写
```

**`ActionButtonSettingsActivity` 自己只覆写 `onResume`/`onPause`/`onStop`/`onDestroy`/ `onConfigurationChanged`，没有
`onCreate`** —— 所以换页 hook 要挂在**基类**上。

### 8.2 布局 `activity_base_action`（换页落点）

```xml
<androidx.constraintlayout.widget.ConstraintLayout>   <!-- 占满 -->
    <FrameLayout android:id="@+id/container"          <!-- 0x7f0900e9 内容容器 -->
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
    <include android:id="@+id/appbar_layout"          <!-- 0x7f09008d 顶部栏 -->
        layout="@layout/appbar_with_divider_layout"
        app:layout_constraintTop_toTopOf="parent"/>
</androidx.constraintlayout.widget.ConstraintLayout>
```

- `container` = **内容容器**（换页落点），`appbar_layout` = 顶部栏（含 `COUIToolbar`，**保留**）
- 主题：`style/ActionButtonThemeAppNoTitle`，**parent = `style/Theme.COUI.Main.Dark`** → 在 `container` 里建的 View
  **天然继承 COUI 配色/字体**（宿主样式免费拿到）
- Activity 声明：`android:process="com.oplus.gesture.ui"`（**独立进程**）启动 action：`com.oplus.gesture.StartActionButtonSettingsActivity`，受
  `oplus.permission.OPLUS_COMPONENT_SAFE` 保护（**adb shell 直接 `am start` 会被拒**，但 `su -c am start` 可以）

### 8.3 坑一：不能清空 `container`

最初的实现是 `container.removeAllViews()` 再塞自己的 View —— **直接崩**：

```
FATAL EXCEPTION: main
java.lang.RuntimeException: Unable to resume activity {…ActionButtonSettingsActivity}
	at ActionButtonSettingsActivity.W2(SourceFile:14)
	at ActionButtonSettingsActivity.onResume(SourceFile:127)
```

`W2()` 的字节码：

```
iget-object v0, …Activity.j0:Landroid/view/View;      // 缓存的 View
if-nez v0, +0a                                        // 缓存为空才查
  sget v0, L…/x;.transparent_view:I
  findViewById(v0) → v0
  iput-object v0, …Activity.j0                          // 写入缓存（可能是 null！）
iget-object v0, …Activity.j0
invoke-virtual {v0}, Landroid/view/View;.getLayoutParams:()   // ← 不判空
```

**`transparent_view` 就在 `container` 里。** 清空后 `findViewById` 返回 null → 下一行 `getLayoutParams()` → NPE → 崩在
`onResume`，页面直接退出。

**正确做法：不要动宿主的 View 树，改为「覆盖」。** 把模块页面作为 `container`
的**最后一个子 View**（FrameLayout 后加的画在上面）

- 不透明背景；同时模块根 View 要 `isClickable = true` **吃掉触摸** ——否则点在空白处会穿透到底下的
  `transparent_view`，它的点击链 `t2 → W1() + finish()` 会把页面关掉。

> 一般结论：**换页劫持不要删宿主 View**。宿主的 `findViewById`
> 结果往往被缓存进字段并在后续生命周期里直接解引用（不判空），删掉就会在 onResume 崩。

### 8.4 坑二：设置页读不了 `/data/system`

```
java.io.FileNotFoundException: /data/system/OPlusKey/config.conf: open failed: EACCES (Permission denied)
```

**不是权限位问题**：`/data/system` 是 `drwxrwxr-x system system`（0775）、文件是 `0644 root root` —— DAC 完全允许。

真正原因：设置页跑在宿主域 **`u:r:platform_app:s0`**，该域读不了 **`system_data_file`** （`/data/system`
下全是这个类型）。

**解法：用 LSPosed 官方 `RemotePreferences` 做跨进程共享存储。** 选它的关键理由 —— 它的实现**只走 binder**
（`RemotePreferences` → `IXposedService.requestRemotePreferences`）， **完全不用 `ContentResolver`**，因此在
`system_server` 里也能用（`ContentResolver` 那条路在 system_server 里必 NPE，见 六之五）。

```
XposedServiceHelper.registerListener(object : OnServiceListener {
    override fun onServiceBind(service: XposedService) {
        prefs = service.getRemotePreferences("opluskey_config")
    }
})
```

binder 由框架异步下发，**拿不到时退回读本地文件**（文件通道只有 system_server 用得了）。

宿主 UI 全用 COUI（`COUIRecyclerView` / `COUILinearLayoutManager` / `COUIButton` / `COUIToolbar` /
`ActionButtonOvalDrawable`），但**方法名被混淆**成 `a()` / `b()` / `c()`：

```
ActionButtonOvalDrawable  (extends Drawable)
  a()V  b()V  c()V  d()V  e(F)V  f(F)V  g(F)V  h(F)V  i(FF)V  j(RectF)V  k(F)V  l(FFF)V …
ActionButtonImageView     (extends AppCompatImageView)
  <init>(Context, AttributeSet)  ← 构造器强制要 AttributeSet
  d(FF)V  e(FFF)V  f(IF)V  g(FFFF)V  h(II)V  i(FFFF)V   ← 语义不可知
```

**靠反射调这些等于在猜**，且 ROM 一更新就碎。 **改为用宿主主题属性取色**（属性名是字符串，稳定）：

```
couiColorPrimary / couiColorPrimaryText / couiColorSecondary
couiColorCardBackground / couiColorDivider / couiColorPrimaryNeutral
couiRoundCornerLRadius / couiRoundCornerMRadius
couiTabBackground / couiColorPrimaryNeutral …
```

配合 `Theme.resolveAttribute()` /
`obtainStyledAttributes()`，拿到的是**和系统原页面逐字节相同的配色**（同一个主题），且不依赖任何混淆类。

> 记录这段是因为**猜不出来**：第一版手绘，第二版照抄原版布局，都被否了。

#### 列表行：抄 COUI 自己的单选行 `layout/coui_select_dialog_singlechoice`（系统「设置」APK）

```
com.coui.appcompat.checklayout.CheckableLayout
  minHeight        = dimen/coui_delete_alert_dialog_button_height = 48dp
  paddingStart/End = alert_dialog_single_list_padding_left/right = 24dp（对话框版）
  ImageView（行首图标，默认 gone）marginEnd = coui_dialog_layout_margin_horizontal(16dp)
  FrameLayout(alignParentEnd) → RadioButton   background=@null、clickable=false、focusable=false
  LinearLayout(文字区) toStartOf=radio、toEndOf=icon
      TextView 标题  textAppearance = ?attr/couiTextAppearanceHeadline6
      TextView 副标题（可选）
  ImageView 分隔线  visibility=gone、height = coui_list_divider_height(0.33dp)
```

⇒ 行 = **图标 + 标题 + `RadioButton`**，**没有副标题、没有分隔线**（分隔线在 layout 里就是 `gone`）。标题字重来自
`style/couiTextAppearanceHeadline6`：**16sp + `sans-serif-medium`**（不是 bold）。

| 项         | 实测                                    |
| ---------- | --------------------------------------- |
| 行距       | 169px = **48.3dp**（行间无分隔线）      |
| 卡片       | x 56..1215 ⇒ 左右各 16dp、白底圆角 16dp |
| 标题左边界 | 距卡片左边缘 17.1dp（≈16dp 内边距）     |
| 单选圈     | 宽 69px ≈ **20dp**，右边缘距卡片右 18dp |

宿主那套行首图标（`ac_*`）是**给深色页面画的白色图**，放浅色卡片上就是白底白图，

**宿主 APK 里也有这张 layout，但别直接充气**：宿主那份是**对话框版** （左右内边距 24dp、行首图标
`layout_marginVertical=20dp` 且**没有固定尺寸**），直接塞进 48dp 的列表行后，图标会按 drawable 固有尺寸撑开 ⇒
**整行实测 ≈590dp**
（一屏放不下一行）。要用得把内边距/图标尺寸/图标纵向边距三样全改掉。列表行改用「同结构 + 列表尺寸」手搭，行背景取 COUI 那张 layout 里同款的主题 drawable
`?android:attr/selectableItemBackground`。

参照页是 `com.oplus.securitypermission/…PrivacySecuritySettingsDashboardActivity` （**不是** `com.android.settings` 的
`PrivacySettingsActivity`，那个要解锁图案）。它顶部那个「隐私 / 安全」是 COUI 的**分段控件**（胶囊轨道 + 圆角滑块），取值来自
`SecurityPermission.apk` 自带的那份 COUI：

```
style/SegmentButton : layout_height=40dp、android:padding=dimen/coui_segment_padding(4dp)
dimen/segment_button_height               = 32dp     ← 滑块高 = 40 - 4×2
color/coui_color_segment_button_background = #0f000000(浅) / #1fffffff(深)   ← 轨道
color/coui_color_segment_button_indicator  = #b2ffffff(浅) / #33ffffff(深)   ← 滑块
dimen/coui_segment_min_height = 28dp、coui_segment_min_width = 52dp
```

两个颜色都是**半透明覆盖色**：`0f000000` 压在页面底色 #f0f1f2 上 = **#e2e3e4**，

滑块 32dp 高、距轨道左/上/下各 4dp、宽 = (轨道宽 - 8) / 项数；文字字形高 ≈12.6dp ⇒ **14sp**。

**这个控件没法直接 new / 充气**：宿主进程那份 COUI（811 个类）里 **没有** `COUIChipTab*` / `COUITabLayout`
/ 任何分段控件类（它们只在 `SecurityPermission.apk` / `Settings.apk` 那份更新的 COUI 里），连
`attr/couiSegmentButtonStyle` 都没有 —— 宿主只有 `dimen/segment_button_height`
这几个尺寸。所以按它自己的尺寸与颜色搭出来。

> 历史记录：中间一版做的是「文字 + 底部指示线」，依据是宿主 `style/COUILargestTabLayoutStyle` 与
> `COUISlidingTabStrip#updateIndicatorPosition` （指示线取 tab 内 `TextView` 的
> `getLeft/getRight`、`coui_tablayout_indicator_padding`=0
> ⇒ 宽度=文字宽、画在条最底部）。**那套 tab 在这个页面里根本不存在**，

#### 动效：按下态可以用「按住不放再截图」量出来

`input tap` 太快，截图抓不到按下态。用 **`input motionevent DOWN x y`** 卡住不放，截图后再
`UP`，就能量到真实的按下反馈：

| 控件                    | 按下时       | 量到的值                                                                          |
| ----------------------- | ------------ | --------------------------------------------------------------------------------- |
| 分段控件（隐私 / 安全） | 整条轨道缩放 | 轨道宽 1160→1126px、高 140→136px，中心不动 ⇒ **scale 0.97**                       |
| 列表行（经典铃声）      | 整行叠灰     | 行底色 白(255,255,255) → (224,224,224) ⇒ **叠 12% 黑**（#1f000000）；**行不缩放** |

分段控件的滑动动效参数（`SecurityPermission.apk` 的资源）：

```
dimen/coui_segment_regular_extra_width = 7dp   ← 滑动中滑块额外拉长
dimen/coui_segment_over_distance       = 4dp   ← 滑动时越过目标的量
dimen/coui_segment_switch_distance     = 5dp   ← 拖动判定为「换 tab」的位移
dimen/coui_segment_shadow_radius       = 1dp
dimen/coui_segment_shadow_offset       = 2dp
```

> **交叉验证（开源库）**：`suqi8/coui`（Apache-2.0，Compose Multiplatform）的 `TabRow` 文档自述「完整复刻 ColorOS
> 16 的分段按钮 `COUISegmentButtonLayout`」，其常量与上面每段最小宽
> **52dp**（宽度按文字自然宽算、至少 52dp 后铺满整行）。它的 `MarkPreference`（对应 COUI 的
> `COUIMarkPreference`）说明标记用 `coui_btn_check_mark` → radio 矢量
> **24dp**（选中=主色圆盘+白心、未选中=灰描边圆环），也与本模块的 24dp 单选圈一致。该库依赖 Compose
> Multiplatform，**不能引进本模块**（红线：不得引入 androidx / Compose，且验收要求 dex 里 `Landroidx/`
> 出现次数为 0；何况本页面是塞进宿主 Activity 的，没有 Compose 宿主）—— 只能当规格参照。

COUI 里**没有**给分段控件滑动时长的 dimen（那套 `touchanimation` 类是混淆的， `COUITouchMotionAnimator` 的方法名是
`A/B/C/D`、字段 `j/k/l`，靠反射调等于猜 ——宿主进程里虽然有这个类，但**没用它**）。本模块自己定 260ms +
Decelerate 落位、110ms 做按下缩放，其余数值全部照上表。

**列表行的按压反馈会被「重建列表」掐掉**：点行时若 `removeAllViews()` 重建，只改勾选态、不重建，反馈才出得来。

#### 主题：宿主把这一页写死成深色，且**运行配置是 night**

- Activity 清单主题 = `ActionButtonThemeAppNoTitle`，parent 写死 **`Theme.COUI.Main.Dark`**
  ⇒ 系统开着浅色，这一页（含顶栏）也是全黑。而**应用级配置**（`applicationContext`）一直是浅色。
- 后果：`coui_color_label_primary` / `coui_color_card_background` 这类 **DayNight 颜色资源**
  按配置取到夜间值 ⇒ 只换主题会得到「白底 + 白字 + 深色卡片」的混合体。
- 解法（两条并用）：
  1. `onCreate` **之前** `setTheme("AppNoTitleTheme")`（宿主自带、浅色、`windowActionBar=false`）；不能用
     `Theme.COUI.Main.Light`：它 `windowActionBar=true`，宿主 `BaseActionActivity#f0()` 的 `setSupportActionBar` 会抛
     `IllegalStateException` ⇒ 页面打不开；
  2. 页面用**强制浅色的 `ContextThemeWrapper`** 来建（Activity 上 `applyOverrideConfiguration` 已太晚，抛
     `getResources() or getAssets() has already been called`）。
- 顶栏标题要在浅色下重新上色：顶栏是宿主在 night 配置下建的（标题取到夜间白字），而顶栏底色 `couiColorBackground`
  **没有 night 变体、恒为 #ffffffff** ⇒ 白底白字。

#### 配色：属性名对应表（写错就是整页亮蓝或白底白字）

|---|---|---| | 页面背景 | **`couiColorBackgroundWithCard`**（浅色 #f0f1f2 / 深色 #ffffff） | 用
`couiColorBackground`：那支**没有 night 变体、恒为 #ffffffff**，白卡片浮不起来； 用
`couiColorPrimary`（品牌强调色）→ 整页亮蓝 | | 卡片背景 | `couiColorCardBackground`（浅 #ffffffff
/ 深 #1affffff）／宿主列表那张 drawable 是 浅 #ffffffff / 深 #ff242424 | | | 主文字 |
`couiColorLabelPrimary`（浅 #e6000000 / 深 #e6ffffff） | | | 描边/分隔线 | `couiColorDivider`（浅 #1f000000
/ 深 #33ffffff） | | | **tab 选中** | **`couiColorPrimaryNeutral`**（浅 #e6000000 / 深 #e6ffffff） | 用了
`couiColorPrimary`（品牌蓝）→ 整条 tab 是 Material 味 | | **tab 未选中** | **`couiColorSecondNeutral`**（浅 #8a000000
/ 深 #8affffff） | 用了 `couiColorLabelSecondary` |

上表里带深浅两值的都是 **DayNight 颜色资源**（`@color/coui_color_*`，资源表里有 `(night)` 变体）——
**它们跟着「运行配置」走，不跟着主题走**：配置是 night 时，浅色主题也会取到夜间值。

依据是 `color/coui_tab_default_color` 这条 selector：选中/按下 → `?attr/couiColorPrimaryNeutral`，正常 →
`?attr/couiColorSecondNeutral`，禁用 → `?attr/couiColorDisabledNeutral`。即 **COUI 的 tab 是中性灰白，不是品牌蓝**。

COUI 是**随各 App 打包**的（`oplus-framework.jar` 里没有
`com/coui/`），而这个进程里能加载的只有宿主 APK 的那份 —— 它的控件**方法名与枚举常量都被混淆**：

```
COUIButton            extends AppCompatCheckBox…  方法全是 a() b() c()…
COUIButton$ButtonType  枚举常量 a / b / c / d / e / f / g
COUIStepperView       是「数字步进器」(getCurStep/setMaximum)，不是分段控件
COUIPageIndicator     是页码圆点，不是 tab
```

且**没有** `COUITabLayout` / `COUIRadioButton`。⇒ 只能走**主题属性名 / drawable 名字符串**这两条稳的路。

#### 图标三套名字不统一（拼前缀会错）

```
中图标  ac_<f>_middle_icon      例：ac_take_screenshot_middle_icon
条图标  ac_tab_icon_<f>        例：ac_tab_icon_screen_shot
圆底    action_button_oval_<f> 例：action_button_oval_screenshot
```

⇒ `ui/model/ActionCatalog.kt` 里**直接写全名**，不拼前缀。

设置页侧（`u:r:platform_app:s0`）的结果：

| 路径                                                           | 写           | 读           |
| -------------------------------------------------------------- | ------------ | ------------ |
| `/data/user_de/0/com.oplus.gesture/files/opluskey_config.conf` | **OK**       | **OK**       |
| `/sdcard/OPlusKey/config.conf`                                 | FAIL(ENOENT) | FAIL         |
| `/storage/emulated/0/OPlusKey/config.conf`                     | FAIL(ENOENT) | FAIL         |
| `/data/system/OPlusKey/config.conf`                            | FAIL(EACCES) | FAIL(EACCES) |
| `/data/local/tmp/opluskey_config.conf`                         | FAIL(EACCES) | FAIL         |
| `/data/misc/opluskey/config.conf`                              | FAIL(ENOENT) | FAIL         |

⇒ 只能放**宿主 App 自己的私有目录**。写入时必须**逐级放开遍历权限**：

```
/data/user_de/0/com.oplus.gesture              0700 → 0711   ← 不放开 system uid 进不来
/data/data/com.oplus.gesture/files         0775
…/files/opluskey_config.conf              0644
```

`system_server` 能否读（SELinux 的 `app_data_file` 权限）**待重启后验证**。

| 入口                                     | 结果                                                                                                                                                                |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `XposedServiceHelper.registerListener`   | binder **永远不来**。它靠**模块自己 App 进程**里的 `XposedProvider` 接收（`call("SendBinder")`），而本模块**零组件** ⇒ App 进程永远不起来。日志里一条相关记录都没有 |
| `XposedInterface.getRemotePreferences()` | 能拿到（日志 `远端配置通道已就绪`），但**注入侧是只读的** —— 写入抛 `UnsupportedOperationException: Read only implementation`                                       |

libxposed 的设计前提是「**模块 App 写、注入侧读**」；我们的界面在**宿主 App 进程**里，既不是模块 App、也不是注入侧读的那一方 ⇒ 两条都不成立。

### 8.8 两个自己踩的坑（都是"看起来像功能坏了"的那种）

1. **诊断代码踩了真实数据路径**。`ConfigPathProbe` 的候选 #1 就是配置文件本身，每跑一次就把配置覆盖成 `probe-by-page`
   ⇒ 表现为「选了没反应 / 重开回到默认值」。⇒ **诊断用的临时路径必须和真实数据路径错开**，诊断完立刻删。
2. **选动作必须同时 `enabled=true`**。页面读不到旧配置时回落到 `Config.DEFAULT`，而默认
   `plus_enabled=0`（"装上模块不改变行为" 是刻意的默认值），

> **这一节是历史记录，不是当前实现**（2026-10-11「全量迁移」后已弃用）。设置页现在是「**宿主真·COUI 控件 + 平台组件**」的纯 View 树，见
> **第十节**； `Md3eSettingsPage.kt` 与全部 Compose 依赖已删，产物里 `Landroidx/` 为 0。

设置页最终改用 Compose + Material 3 渲染（`page/Md3eSettingsPage.kt`）。 `ComposeView` 的崩点在 `onAttachedToWindow` →
`ensureCompositionCreated` → `resolveParentCompositionContext` →
`getWindowRecomposer`，**这个时机在 try/catch 之外**，一旦抛就是整个设置页进程挂掉。要让它在宿主这棵树上跑起来，下面四条**缺一不可**：

|---|---|---| | 1 | `ComposeView` 的 `context` 是 `ContextThemeWrapper`，**不是** owner。`context as? LifecycleOwner`
静默返回 null | 从 `baseContext` 链里挖出 Activity（`Md3eSettingsPage.activity`） | | 2 | 宿主
`ActionButtonSettingsActivity` **自己也不实现**那三个 owner 接口（日志
`lifecycle=false viewModel=false savedState=false`）。它不是 AppCompat 系 | **自建**：`LifecycleRegistry` +
`ViewModelStore` + `SavedStateRegistryController` | | 3 | `resolveParentCompositionContext` 只沿 **parent 链**
找 owner（**不含自身**）。报错原文点名 `androidx.constraintlayout.widget.ConstraintLayout`（宿主布局根） | 在
`container.addView(page)` **之后**挂满整条祖先链（实测 8 层：ComposeView → container → ConstraintLayout → … →
decorView） | | 4 | `SavedStateRegistryController.performAttach()` **只能在 lifecycle 的 INITIALIZED 阶段**调用；先推
`RESUMED` 再 attach 会抛 `Restarter must be created only during owner's initialization stage`
| 顺序：`performAttach()` + `performRestore(null)` → **最后**才 `currentState = RESUMED`。 绝不能推到
`DESTROYED`（Compose 的 `Recomposer` 收到就永久取消） |

日志锚点（这四行齐了才算接通）：

```
Compose 可用性：宿主自备 owner=false（lifecycle=false viewModel=false savedState=false），宿主没有就用自建兜底 ⇒ 放行
宿主不提供 ViewTree owner（lifecycle=false viewModel=false savedState=false）⇒ 已自建 LifecycleRegistry + ViewModelStore + SavedStateRegistry 兜底
设置页用 Compose + Material 3 Expressive 渲染
ViewTree owner 已挂满祖先链 8 层（ComposeView → decorView）
```

### 8.10 COUI 的颜色多是「半透明覆盖层」，Compose 不做自动叠加

```
couiColorBackgroundWithCard → #ff000000   页面底（实心）
couiColorCardBackground     → #1affffff   卡片 = 10% 白  ← 覆盖层，不是最终色
couiColorLabelPrimary       → #e6ffffff   主文字 = 90% 白 ← 覆盖层
couiColorDivider            → #33ffffff   分隔线 = 20% 白 ← 覆盖层
```

`#1a1a1a`）。**Compose 不会** —— 半透明色直接画上去会透出底下 / 被父级裁掉。

⇒ `page/Md3eSettingsPage.kt` 里有个 `overlay(overlayArgb, baseArgb)`
做标准 alpha 合成，把 COUI 的覆盖色算成实心色再喂给 MD3 色板。分段控件的两级底色也是这么算的

### 8.11 MD3 组件的取色链（覆盖 `primary` 没用）

`#4A4458`（MD3 默认 `surfaceContainerHighest` 的紫灰调）、卡片底 `#1A1A1A` （MD3 默认
`surfaceContainerLow`）。实际取值链：

| 组件                           | 取的颜色角色              |
| ------------------------------ | ------------------------- |
| `SegmentedButton` **未选中**底 | `surfaceContainerHighest` |
| `SegmentedButton` **选中**底   | `secondaryContainer`      |
| `Card` 默认底                  | `surfaceContainerLow`     |
| `ListItem`                     | 走外层 `Card`，自身透明   |

⇒ 要改外观必须覆盖 `surfaceContainer*` 与 `secondaryContainer`，只改 `primary` 不够。

### 8.12 `SingleChoiceSegmentedButtonRow` 的等分与图标位（「tab 被截」的成因）

两个坑叠在一起：

1. 给 `SingleChoiceSegmentedButtonRow` 加 `fillMaxWidth()` **不会**让各段等分 ——它内部是 `Row(wrap_content)`
   语义，各段仍按内容取宽，宽度不均、末段顶出父宽被裁。⇒ 每段显式 `Modifier.weight(1f)`。
2. `SegmentedButton` **默认为选中态的勾选图标预留宽度**（约 18dp），三段平分本就紧，再被占掉一截，文字就被裁。⇒ 传空的
   `icon = {}` 收掉。

### 8.13 Compose 版的最终观感（深色模式实测）

| 元素             | 量到的值                                                       |
| ---------------- | -------------------------------------------------------------- |
| 页面底           | `#000000`                                                      |
| 顶栏底           | 刷色前 `#101010`（宿主 `couiColorBackground`）→ 刷后 `#000000` |
| 卡片底           | `#1a1a1a`（`#1affffff` 叠黑，与 COUI 一致）                    |
| 分段按钮选中底   | `#333333`（`#33ffffff` 叠黑）                                  |
| 分段按钮未选中底 | `#000000`（轨道 `#1fffffff` 极淡）                             |
| 单选圈选中色     | `onSurface` 合成后约 `#e6e6e6`（亮白实心圈）                   |
| 行文字左边距     | 252px（有图标 / 无图标行**一致**，`uiautomator` 量证）         |

### 8.14 图标：宿主那套不够用，改用 SVG 图标库

**宿主只有一部分动作有图标**（逐项核对 `com.oplus.gesture` 资源表）：

| 动作                                         | 宿主图标                                             |
| -------------------------------------------- | ---------------------------------------------------- |
| 不操作 / 保持原样                            | `ac_no_operation_middle_icon`、`ac_tab_icon_nothing` |
| 打开相机                                     | `ac_camera_middle_icon`、`ac_tab_icon_camera`        |
| 打开设置 / 联系人 / 时钟 / 微信              | **无**（只能取目标 App 的真实图标）                  |
| 返回键 / 主页键 / 上一首 / 下一首 / 播放暂停 | **无**（`keyevent:` 类动作，宿主没有对应资源）       |

⇒ 混搭的结果是"有的有图、有的空着"，而且宿主那套是**给深色页面画的白色描边图** （浅色下要 `mutate().setTint()`
重新上色）。**统一改用图标库**。

| 方案                            | debug 包 | release 包（R8） |
| ------------------------------- | -------- | ---------------- |
| 不引入库（宿主图标 + App 图标） | 25.6MB   | —                |
| `material-icons-extended:1.7.8` | **66MB** | **1.6MB**        |

**debug 包会打进全部几千个图标**（+40MB），但 R8 在 release 里能精确裁到只留实际引用的那几个 ⇒
**交付物（release）反而更小**。所以这个库可以放心用。

该库里**没有 `PlayPause`**（Material Symbols 才有）。可用的是 `Icons.Outlined.PlayArrow` / `Pause` /
`PlayCircle`。选图标前先在 jar 里核一遍类名：

```bash
unzip -l <material-icons-*-classes.jar> | grep -iE "outlined/<IconName>"
```

部分图标只在 `automirrored` 包下有 RTL 版本（如 `Undo` / `Chat` / `ArrowBack`），但**普通 `outlined`
版本也在**，中文（LTR）场景用 `Icons.Outlined.*` 即可。

实现上的防呆：`ActionCatalog.Spec.icon` 是**无默认值的必填字段**
——新增动作时漏填图标会**编译不过**，从机制上保证「每一个都有」。

### 8.15 顶栏底色：与页面底**不是同一个值**，要自己刷

```
顶栏底 #101010 左右   ← 宿主的 couiColorBackground（而且不均匀，9~16）
页面底 #000000        ← couiColorBackgroundWithCard（我们给页面用的）
```

两者在深色下不是一个值，接缝处有一道看得出的浅灰条。**改顶栏**（而不是改页面底）——页面底要留给卡片做对比，且顶栏那个值是宿主主题给的、跟着 ROM 变。

顶栏结构（`appbar_with_divider_layout`）实测刷色时遍历到的容器层：

```
AppBarLayout / COUIToolbar / COUIActionMenuView     ← 这三层刷背景色
  └── 返回键（ImageButton，自带圆形背景）/ 标题 TextView / 分隔线 View   ← 叶子，不碰
```

只刷 **`ViewGroup`（容器层）**，叶子一律不动 —— 否则会把返回键的圆形背景刷成方块。只刷最外层没用，会被 `COUIToolbar`
自己那份不透明底色盖住 ⇒ **两层都刷**。

宿主 APK 的**资源路径被混淆**（`res/-K.9.png`、`res/1I.ogg` 这种），按名字 `aapt2 dump xmltree --file res/layout/...` 会
`failed to find file` ——想读宿主的 layout 只能靠运行时遍历 + 打日志，别在反编译上耗时间。

### 8.16 MD3 `RadioButton` 默认拿 `primary` 当选中色（会看不清）

MD3 的 `RadioButton` 选中色默认是 `primary`。而我们的 `primary` 被设成了 COUI 的**分段滑块色**
`#333333`（很暗的中性灰）—— 压在 `#1a1a1a` 卡片上只亮一点点，

⇒ 必须显式给：`RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface)`。

**一般规律**：MD3 组件的默认取色角色（`primary` / `secondaryContainer` /
`surfaceContainer*`）在自定义色板里都要**逐个核对**，不能只改 `primary` 就以为全套生效。

### 8.17 状态栏「不是沉浸式」的真相：系统在状态栏 inset 上叠了一层渐变（实测）

（日志为证），可截图逐行取色仍是：

| 位置                 | 改前                            | 改后      |
| -------------------- | ------------------------------- | --------- |
| `y=0..50`（状态栏）  | `(8,8,8)`→`(10,10,10)` **渐变** | `(0,0,0)` |
| `y=110..140`（顶栏） | `(13,13,13)`                    | `(0,0,0)` |
| `y≥160`（页面）      | `(0,0,0)`                       | `(0,0,0)` |

- 顶栏自己已经是纯黑、却量到 `(13,13,13)` ⇒
  **那层渐变画在内容之上**，改任何 View 的背景都盖不住；刷背景自然「看不见效果」。
- 渐变的**范围正好是状态栏 inset**：模块量到 `statusBarInset = 141px`，而渐变的边界也在 141px 左右。
- 成因是 edge-to-edge 强制之后（宿主 `targetSdk=37`，`setStatusBarColor()` 被忽略）系统给状态栏加的**对比度薄纱**（AOSP
  `mStatusBarContrastWhenTransparent`，对 targetSdk ≥ Q 默认开）。旁证：宿主自己的 `BaseActionActivity#onCreate`
  只对**导航栏**关了这个（`setNavigationBarContrastEnforced(false)`），漏了状态栏。

**修法（两条一起上，`ui/HostChrome.kt`）**：

1. `Window.setStatusBarContrastEnforced(false)` —— 关掉系统那层薄纱；调用点必须在 `setContentView`
   **之后**（默认值来自主题属性 `android:enforceStatusBarContrast`，首次建 `DecorView` 时会按主题重算一遍）；
2. `coverStatusBar`：在 `decorView` 上**最后 addView**
   一块页面底色的实心 View（高度 = 状态栏 inset，跟随 inset 变化）—— 盖住「那一块还有别的 View 在画」的情况。

**一般规律**：状态栏/顶栏这一带的「颜色不对」先按**逐行取色**量一遍再动手 —— `(8,8,8)`
这种「比页面亮一点点」的值就是有半透明覆盖层的特征， `(0,0,0)` 才是真的没人盖。

- **那一条本来就在模块页面的画布里**：页面 edge-to-edge（`Scaffold(contentWindowInsets = 0)`）、顶栏 107dp 含状态栏 inset、底色铺到
  `y=0`；
- **§8.17 里那块 `decorView` 最上层的实心遮罩恰好把它挡在模块控制之外**
  ——顶栏的模糊在那条上断掉、浮层的全屏 dim 也够不到（于是另开了一条 `SettingsPage.onStatusBarDim`
  回调手动推颜色，本次一并删掉）；是它 / 它子树里的叶子 View 在那条上作画；2026-10-11 顶栏改自绘后宿主顶栏整条
  `GONE`，那一条就只剩模块页面在画；
- 所以现在：模块页面在显示时**不盖**（那一条完全归页面，模糊 / dim
  / 深浅切换全是它自己的事），只有「打开自带的快捷键界面」露出宿主原版页时才盖一块深色遮罩（那时模块页面是 `GONE`
  的），切回来立刻 `removeStatusFiller()` 拆掉。

（§8.17 里两条是一起上的，「光关薄纱」那次没单独验过）。判据是一行取色 ——状态栏那一条应当与紧邻其下的顶栏**完全同色**；若仍比顶栏**亮一点点**（半透明覆盖层的特征），说明薄纱还在，得在「薄纱」与「受控」之间取舍（见
`coverStatusBar` 的注释）。

`dumpsys activity recents` + 宿主清单（`aapt2 dump xmltree`，宿主 8.6.0）实测：

```
* Recent #0: Task{… #23989 type=standard A=10277:com.oplus.gesture nonResizable}
  intent={act=com.oplus.gesture.StartActionButtonSettingsActivity
          id=oplus.intent.category.START_FROM_SETTINGS_MAIN_PAGE
          flg=0x10000000  pkg=com.oplus.gesture mCallingUid=1000}
  Activities=[ActivityRecord{… ActionButtonSettingsActivity t23989}]   ← 任务里就这一个
* Recent #1: Task{… A=1000:com.android.settings.root}                   ← 设置自己那条
```

- 清单：`ActionButtonSettingsActivity` 声明了 **`android:launchMode="singleInstance"`（值 3）**、
  `android:process="com.oplus.gesture.ui"`、`android:resizeableActivity="false"`、
  `screenOrientation="1"`（竖屏锁定），**没有** `android:taskAffinity` （⇒ affinity 取默认值 = 包名
  `com.oplus.gesture`）。
- **`singleInstance` 意味着这一页天生独占一个任务**，无论谁拉、怎么拉，都不可能并进「设置」的 task；设置侧又是用
  `FLAG_ACTIVITY_NEW_TASK` （`flg=0x10000000`，`mCallingPackage=com.android.settings`）拉它的，两条叠加 = 独立任务。
- 卡片标题取的是**根 Activity 所属应用**的标签，而 `com.oplus.gesture` 的 `application-label-zh-CN`
  就是「**手势体感**」⇒ 最近任务里显示「手势体感」。
- **与本模块无关**：模块不碰任务 / 启动模式，`ui/` 下没有任何 `startActivity` /
  `setTaskDescription`（`FLAG_ACTIVITY_NEW_TASK` 只出现在 `host/action/ActionRunner.kt`，
- 真要改只能改**卡片语义**、改不了任务归属：`Activity.setTaskDescription()` 换标题与图标，或
  `AppTask.setExcludeFromRecents(true)` 从最近任务里去掉。

## 九、侧键手势的真实来源：框架判「单击 / 长按」，宿主数「双击」（实测）

> 结论先行：**这个侧键本来就有单击 / 双击 / 长按这套事件**，由「框架 + 宿主」一起判好，模块该做的是**在宿主进程里接住它们**，而不是在
> `system_server` 里自己计时。于是「设置页写配置 →
> system_server 读配置」这条跨域死路也一并绕开了（宿主与设置页同 uid）。

### 9.1 框架侧：自己判单击 / 长按（`oplus-services.jar` 反编译）

`com.android.server.policy.StrategyActionButtonKeyLaunchApp`（父类 `InputStrategy`）里的常量：

| 常量                                | 值                                            | 含义                         |
| ----------------------------------- | --------------------------------------------- | ---------------------------- |
| `SINGLE_TAP`                        | `"singleTap"`                                 | 手势类型：单击               |
| `LONG_PRESS`                        | `"longPress"`                                 | 手势类型：长按               |
| `LONG_PRESS_TIME`                   | `495`                                         | **框架自己的长按阈值（ms）** |
| `MSG_ACTION_BUTTON_LONG_PRESS_DOWN` | `2`                                           | 长按内部消息                 |
| `MSG_ACTION_BUTTON_LONG_PRESS_UP`   | `3`                                           | 长按内部消息                 |
| `ACTION_KEY_TYPE`                   | `"ActionType"`                                | 派发时携带手势类型的键名     |
| `SECOND_MENU_NAME`                  | `"second_menu_name"`                          | 二级菜单                     |
| `TAG`                               | `"STKeyLog_StrategyActionButtonKeyLaunchApp"` | 它自己的日志 TAG             |

- 它**没有**任何 `launch` / `startApp` 之类的「执行动作」方法 —— 执行是**按场景派发给场景宿主**
  （`scene_owner`），派发点看不到，说明走的是 `InputStrategy` 那条通用链路。
- 场景表里每种场景各有 `click_action_info` / `click_action_type` 与 `long_press_action_info` /
  `long_press_action_type`、`second_menu_activity_info`、 `long_press_has_own_vibrator`（`content query` 实测）—— ⇒
  **框架的一等手势只有「单击」和「长按」，没有双击**。
- 当前生效的场景存在 `Settings.Global` 的 **`oplus_action_button_switch_state`** （本机实测为 `null`
  ⇒ 侧键什么都不做；设成 `start_app` 后框架立刻去 bind 宿主服务）。

### 9.2 宿主侧：双击是宿主自己数的（`reference/gesture.apk`）

宿主 `com.oplus.gesture` 里能看到这些**日志字面量**（方法名被混淆，只有日志字符串可读）：

```
onSingleTap: owner is null or empty
hyperKeyDoubleClick: currentScene: … / game In Foreground / pkgName is scene type
longPressStartApp currentScene = … / longPressStartCamera … / longPressStartScreenShot …
KeyStartCamera init, mDoubleClickCount= …      ← 宿主自己维护双击计数
```

⇒ 框架发 `singleTap` 给宿主，**宿主自己按 `mDoubleClickCount` 判双击**。

### 9.3 框架 → 宿主的通道：bind 服务 + `Messenger`

宿主两个相关服务**都只实现 `onBind`、没有 `onStartCommand`**（dex 实测）：

```
com.oplus.gesture.actionbuttonkey.quicklaunch.StartAppService   ← 「打开应用」场景
  a: android.os.Messenger                 ← 跨进程就靠它
  b: StartAppService$a extends Handler    ← 手势消息落点
com.oplus.gesture.server.ActionButtonKeyService                 ← 其余场景
  c: ActionButtonKeyService$a extends Handler
```

两个服务都在**宿主主进程**（清单里没有 `android:process`）⇒ 手势劫持要装在 `com.oplus.gesture` 主进程，不是 `.ui`。

```
I OplusKey: 宿主服务 onCreate：StartAppService
I OplusKey: 宿主服务 onBind：StartAppService | intent=action=null
            component=ComponentInfo{com.oplus.gesture/…quicklaunch.StartAppService}
```

⇒ 框架**确实在 bind** 这个服务，通道成立。

在 system_server 里读设置页写的文件是**必然失败**的：

```
D OplusKey: 读配置失败（忽略）：/data/data/com.oplus.gesture/files/opluskey_config.conf
            | open failed: EACCES (Permission denied)
```

文件权限位是 `0666`、路径也对 —— 但它的 SELinux 标签是
`u:object_r:app_data_file:s0:c512,c768`（**带 MCS 分类**），`system_server` 域进不去。结果是**一直回落到
`/data/system/OPlusKey/config.conf` 里的旧配置** ⇒

**改到宿主进程之后这条通道自然就通了**：设置页（`com.oplus.gesture.ui`）与宿主主进程（`com.oplus.gesture`）**同一个 uid**，读同一个文件不需要任何跨域特权。

> 另外两个「注入侧读得到」的位置也一并记下：`/data/system/*`（`system_data_file`）可读； `Settings.Global` 在 Android
> 16+ 落盘成 **ABX 二进制 XML** （`/data/system/users/0/settings_global.xml` 开头是
> `ABX\0`），**不是纯文本，别去解析它**。

框架每次按键都会先判「当前场景」，**只有开关开着**它才把手势派发出去。开关关着时它只打一行：

```
W/STKeyLog_StrategyActionButtonKeyLaunchApp: action button switch close
D/KEYLOG_Notify: INTERCEPT Queueing down, interceptor = …StrategyActionButtonKeyLaunchApp$2…
```

—— 键被**吞掉**、但什么都不做（宿主一条消息都收不到）。而**模块的换页界面正好替换掉了唯一能打开它的那个界面**（系统原版「快捷键」页），所以表现就是「模块装了、按了没反应」。

打开它（在系统原版页面上走一遍即可，模块自带 `opluskey_bypass` 旁路能把原版页面放出来）：

1. 点底部动作图标选一个动作 → 底部按钮从灰的「当前使用」变成可点的**「设定」**；
2. 点「设定」→ 框架立刻推来 `btn_switch_status=true`，并记下新场景：

```
D/STKeyLog_StrategyActionButtonKeyLaunchApp: readActionButtonSwitchState, mActionSwitchState: camera
D/ActionButtonKeyUtils(504=宿主主进程): readActionButtonSwitchState, actionButtonSwitchState: camera,
    userId=0 DEFAULT_ACTION_SWITCH_STATE=flash_memory
D/STKeyLog_StrategyActionButtonKeyLaunchApp: onChange mActionSceneOwner = com.oplus.camera,
    mActionSwitchStat=camera mSecondMenuName=camera_key_magic_photo
```

两个要点：

- `mActionSwitchState` **就是「当前场景名」**（不是布尔开关）；`DEFAULT_ACTION_SWITCH_STATE=flash_memory`。
- 场景是**按 `scene_owner` 派发**的 —— 场景表里 `scene_owner = com.oplus.gesture` 的只有 **`start_app`** 与
  **`quick_function`**。**只有当前场景属于这两个之一，框架才会来找宿主**，手势才可能落到我们手里；选成 `camera` /
  `translate` / `screen_shot` 时框架找的是 `com.oplus.camera` / `com.coloros.translate` /
  `com.oplus.screenshot`，模块在宿主里等不到任何东西。

### 9.6 通道是 `Messenger`（不是 provider）

```
I/OplusKey: 宿主服务 onBind：StartAppService | binder=android.os.Handler$MessengerImpl
W/OplusKey: 侧键手势消息：StartAppService$a#handleMessage | what=3 arg1=0 arg2=0
            data={btn_switch_status=true}
```

⇒ 框架 → 宿主是 **Message**（`what=3` = 开关 / 场景状态通知），宿主 → 框架是
`msg.replyTo.send(...)`。**要改「当前场景」就该在这一层动手** —— `Messenger.send` 与宿主 Handler 的 `handleMessage`
模块都已挂上，两个方向都看得到。

顺带否掉两条路（都实测过）：

- **不是** `Settings.Global`：`oplus_action_button_switch_state` 设成 `1/true/open/on/start_app`
  都不影响框架判定（它读的是场景名，且来自宿主侧）；
- **不是** provider 写接口：把 `ActionButtonProvider` 的 `insert/update/delete/call/bulkInsert/applyBatch`
  全挂上，走一遍「选动作 + 设定」， **一条写入都没有** —— 场景不是从这条路存进去的。

### 9.7 侧键有**三个** keyCode：735 + ColorOS 内部注入码 780 / 782（实测）

```
D/OplusKey: 按键（策略类）：keyCode=780 不是 侧键，放行给系统
D/OplusKey: 按键（策略类）：keyCode=25  不是 侧键，放行给系统   ← 音量-（真的不是侧键）
```

⇒ 两个结论：

1. **策略类那条路上，侧键报的是 ColorOS 自己注入的内部码 780 /
   782**，不是原始上报码 735。只认 735 会把侧键整个放行掉 ——表现就是「模块装了、侧键完全不被接管」。（735 是**原始**上报码，`injectActionButtonPressKeyEvent`
   之后才变成 780/782。）
2. **音量键 / 电源键也会走进同一个策略回调**（日志里的 25）——所以「能进来就是侧键」这个老假设是错的，会吃掉音量键。

⇒ 正确写法是一份**白名单**：`KeyId.acceptedKeyCodes`（侧键 =
`{735, 780, 781, 782}`，781 为 §14.4 补齐），策略类路径按它认领；PM 兜底路径**只认原始码**（735），否则同一次按键会被策略类和 PM 两条路各判一次。

### 9.7.1 侧键的**原始**硬件码 = 735，来自设备自己的输入描述符（实测，2026-10-13）

```
$ getevent -pl | grep -A3 gpio-keys
add device 12: /dev/input/event0
  name:     "gpio-keys"
    KEY (0001): BTN_TRIGGER_HAPPY32
```

`BTN_TRIGGER_HAPPY1 = 0x2c0 = 704` ⇒
`BTN_TRIGGER_HAPPY32 = 704 + 31 = **735**`。即**侧键在驱动层就报 735**，`KeyId.keyCode = 735` 是正确的原始码。侧键独占
`event0`（另一路 `gpio-keys` 的 `event1` 是音量-、`event2` 是电源）。

### 9.8 重启后「配置读成默认值」不是文件丢了，是 CE 存储还没解锁（实测）

重启后宿主进程日志会是 `接管=false 单击=native …`（默认值），看起来像配置文件被删了。实际不是：

```
$ ls -d /data/user/0/io.github.lo0kie.opluskey /data/user/0/com.oplus.gesture /data/user/0/com.android.settings
No such file or directory   ×3        ← 所有 App 的 CE 目录都看不到
$ ls -d /data/user_de/0/io.github.lo0kie.opluskey
/data/user_de/0/io.github.lo0kie.opluskey   ← DE 目录正常
$ dumpsys window | grep mDreamingLockscreen
mDreamingLockscreen=true              ← 手机还停在锁屏
```

⇒ 首次解锁前 **CE（凭据加密）存储未挂载**，`/data/user/0/<包名>` 一律不可见，root 去 `mkdir` 会报
`Required key not available`。 **这是无害的**：按键路径每次手势都重读配置（`GestureReceiver.handle` →
`ConfigStore.read()`），解锁后再按就正常。只有「开机瞬间读一次」的地方（宿主进程安装时那条日志）会看到默认值。

> **已被 9.9 取代（2026-10-14）**：配置后来挪到了 **DE**
> 目录，解锁前也读得到 ——本节说的「开机到解锁之间只能读到默认值」不再成立，留作 CE 布局时期的记录。

### 9.9 配置改放 DE 目录：解锁前也能读到（实测，2026-10-14）

9.8 记的是「CE 存储解锁前不可见 ⇒ 开机到解锁之间配置读成默认值 ⇒ 侧键那一段走系统原生」。那不是修不掉的限制，是**位置选错了**：换成
**DE（设备加密）** 目录即可。

宿主 App 的 DE 目录本来就存在（它是 `PARTIALLY_DIRECT_BOOT_AWARE`，`dumpsys package` 里可见
`deDataInode`），只是它自己从没用过，所以里面没有 `files/`：

```
$ ls -ld /data/user_de/0/com.oplus.gesture
drwx------ u0_a277 u0_a277   ...    ← 在，但只有 cache/ code_cache/ databases/ shared_prefs/
$ ls -l /data/user/0/com.oplus.gesture/files/opluskey_*
-rw-r--r--  364  opluskey_config.conf   ← 老位置在 CE
```

改法：`ConfigStore.HOST_DIR` 换成
`/data/user_de/0/com.oplus.gesture`，三个文件（主配置 / 命令库 / 界面状态）一起走。**没写任何兼容代码**：本模块尚未发行过，CE 上那份旧文件直接删掉即可，不做搬迁。

真机复核（`kill` 宿主主进程让它带着新路径重读）：

```
13:26:42.284  侧键宿主主进程启动，开始安装手势劫持 Hook
13:26:42.291  宿主进程读到配置：接管=true 单击=zoom:mini 双击=none 长按=power:menu（文件 /data/user_de/0/…）
13:26:42.821  配置已更新：长按=250ms 双击窗口=200ms 立即单击=true 单击=zoom:mini
```

路径换了、读到的是真配置，注入侧那份也跟着刷新了。

- 生效方式：这次只动**宿主进程那一层**（`ConfigStore` 只给 `host/` `ui/` 用，注入侧不引 `core/config`），所以 `kill`
  宿主主进程即可，**不用整机重启**。
- ⚠️
  **仍未验证**：锁屏（解锁前）状态下宿主进程能否读到 DE。本机现在已解锁，这一段只能等下一次开机才看得到；现有证据只到「DE 目录本身开机即可读」（9.8 里
  `/data/user_de/0/io.github.lo0kie.opluskey` 在锁屏时可见）。
- ⚠️ **行为反转**：解锁前也接管 ⇒ 锁屏下侧键按模块配置走，不再是系统原生的相机 / 手电。

## 十、宿主进程里能**安全**使用哪些系统组件（离线逆向，2026-10-11）

> 起因：设置页原先用 Compose /
> MD3 组件 + 从宿主主题取色 ⇒ 观感是「MD 与 COUI 混用」。要「用系统自己的组件样式」，先得把「宿主进程里到底能用什么」摸清。结论如下。

### 10.1 宿主 `com.oplus.gesture` 自带的 COUI（`reference/gesture.apk`）

`com/coui/appcompat` **733 个类**，另加 `com/coui/animatekit`（53）、`com/coui/component`（48）。与设置页相关的：

| 类别                | 类                                                                                                                                                                                                                                                                                            |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 开关                | `com.coui.appcompat.couiswitch.COUISwitch`                                                                                                                                                                                                                                                    |
| 勾选/单选           | `com.coui.appcompat.checkbox.COUICheckBox`                                                                                                                                                                                                                                                    |
| 按钮                | `com.coui.appcompat.button.COUIButton` / `COUILoadingButton`                                                                                                                                                                                                                                  |
| 列表                | `com.coui.appcompat.list.COUIListView`、`cardlist.*`、`input.*`                                                                                                                                                                                                                               |
| 弹窗                | `com.coui.appcompat.dialog.*`（`COUIDialogTitle` / `COUIAlertDialogMessageView` / `COUIAlertDialogOverlayHelper` …）                                                                                                                                                                          |
| **Preference 全套** | `com.coui.appcompat.preference.*`：`COUIMarkPreference`、`COUISwitchPreference`、`COUIJumpPreference`、`COUIInputPreference`、`COUIListPreference`、`COUICheckBoxPreference`、`COUISlideSelectPreference`、`COUIStepperPreference`、`COUIStatusSwitchingPreference`、`COUIBottomPreference` … |

**但这些类的方法名全被 R8 混淆**（实测 `COUISwitchPreference` 的成员名是 `b0` / `c0` / `d0` …）⇒
**不能 new 它们、也不能调它们的 API**（一改 ROM 就碎）。

**可以安全用的是它们的「资源名」**（字符串稳定，按名字查 id 即可）：

```
style/COUISwitchStyle           0x7f12027c   （另有 .Light / .Dark 两个变体）
style/Preference.COUI.SwitchPreference              0x7f1202d9
style/Preference.COUI.COUISwitchWithDividerPreference
attr/couiSwitchStyle / couiMarkPreferenceStyle / couiJumpPreferenceStyle
attr/couiInputPreferenceStyle / couiStepperPreferenceStyle / couiSlideSelectPreferenceStyle
layout/coui_preference / coui_preference_listview / coui_mark_with_divider_preference
layout/coui_select_dialog_singlechoice（对话框版单选行，见 8.6）
style/COUIAlertDialog 系列（COUIAlertDialog.Bottom / .Center / .List / .SingleEdit …）
```

### 10.2 分段控件（手势 tab）宿主里**没有**

- 宿主 APK 里 `COUISegmentButton*` / `SegmentButtonLayout` / `COUITabLayout` 命中数 **0**，样式 `style/SegmentButton`
  也不存在（只有 `dimen/segment_button_height` 等几个尺寸）。
- 它只在 **Settings.apk** 里： `com.coui.appcompat.segmentbutton.COUISegmentButtonLayout` （伴生
  `ISegmentButtonLayout`、`OnSegmentSelectedListener`、`OnSelectedSegmentChangeListener`、
  `$SegmentButtonDrawDelegate`、`$SmoothRoundCornerHelper` …）。
- **跨 APK 用不了**：类的资源 id 属于 Settings.apk 的资源表，用宿主的 Context 建出来必然取到错资源（而且 dex 也不是宿主 classloader 的）⇒ 只能**照规格手搭**，规格见 8.6（轨道 40dp
  / 内缩 4dp / 滑块 32dp / 圆角胶囊 / 轨道与滑块那两支配色 / 动效 extra_width 7dp、over_distance 4dp、switch_distance
  5dp、阴影 1dp+2dp）。

### 10.3 结论：安全的做法

1. **控件用框架的**（`android.widget.Switch` / `CheckBox` / `RadioButton` / `TextView` / `ImageView` / `LinearLayout`
   …）—— 它们的 API 由平台保证，**永不会被混淆**；
2. **外观用宿主的 COUI 资源名**套上去：例如 `android.widget.Switch(ctx, null, 0, R.style.COUISwitchStyle)`（style id 用
   `resources.getIdentifier("COUISwitchStyle", "style", "com.oplus.gesture")` 取）、列表行照
   `coui_select_dialog_singlechoice` 的结构与尺寸、卡片/分隔线用宿主 drawable、顶栏沿用宿主 `COUIToolbar`、弹窗按
   `COUIAlertDialog.*` 的规格搭；
3. **不要**
   new 宿主的 COUI 类（方法名混淆）、**不要**从别的 APK 加载类（资源表不匹配）。—— 这两条是「安全」的边界：越界了就会在 ROM 更新时碎，或者直接取错资源。

### 10.4 「无障碍」页那个 tab 条 = COUI 的**大号文字 tab**（实测）

```
com.android.settings:id/tabs            HorizontalScrollView     y 323..491（168px = 48dp）
  └ LinearLayout（内容宽 1096px = 313dp，左侧留 25dp）
      ├ androidx.appcompat.app.ActionBar$Tab  ×4（通用 / 视觉 / 听觉 / 互动，每格 78dp）
      └ 选中格底部一条指示线：y 484..490 ≈ 2dp
```

取色（暗色，PLK110 1272×2772 / 560dpi）：

| 元素            | 实测                                          |
| --------------- | --------------------------------------------- |
| 选中 tab 文字   | `#e6e6e6`（= `couiColorLabelPrimary` 叠黑）   |
| 未选中 tab 文字 | `#8a8a8a`（= `couiColorLabelSecondary` 叠黑） |
| 指示线          | `#e6e6e6`，高 ≈2dp，画在 tab 底部             |
| 卡片底 / 页面底 | `#1a1a1a` / `#000000`（与本模块页面一致）     |

它是 **AppCompat 的 ActionBar tab**（不是 `COUIChipTabGroup` 那个胶囊分段控件），而 AppCompat /
COUI 的类都**不在宿主里能安全调用**（方法名混淆，见 10.1）。

**但宿主有这套 tab 的全部「资源」**，可以照资源名安全手搭：

```
style/COUILargestTabLayoutStyle      0x7f1201bb  parent=COUITabLayoutBaseStyle
style/COUITabLayoutBaseStyle         0x7f12027f
color/coui_tab_default_color         0x7f06090c  （selector：选中 couiColorPrimaryNeutral / 常态 couiColorSecondNeutral）
color/coui_tab_background_dark       0x7f06090b  #ff000000
dimen/tablayout_large_layout_height  0x7f070990  40dp
dimen/tablayout_large_text_size      0x7f070993  16（sp）
dimen/coui_tab_layout_* / coui_tab_* （各档边距、文字高、指示线高）
```

⇒ 手势 tab 若要换成这套「文字 + 底部指示线」，做法与 10.3 一致： **框架 `TextView`
×N + 一条 2dp 指示线 View**，文字色直接用 `color/coui_tab_default_color` 这个 selector（`getIdentifier` 取 id，套成
`ColorStateList`），尺寸取上表那几个 dimen —— 全程不碰任何混淆类，也不会跨 APK。（胶囊分段控件 `COUIChipTabGroup`
只在 Settings.apk，宿主没有，见 10.2。）

### 10.5 「设置 → 隐私与安全」那个分段控件：逐像素实测（2026-10-11）

> `com.oplus.securitypermission/...PrivacySecuritySettingsDashboardActivity`（暗色 / 1272×2772 / 560dpi）：

```
android.widget.Button  隐私   [70,358][636,470]      ← 两段各 566px = 162dp
android.widget.Button  安全   [636,358][1202,470]
轨道（胶囊）          x 64..1207（1144px = 327dp） y 344..483（140px = 40dp）
滑块（选中段）        x 82..623  y 358..469（112px = 32dp）
```

| 元素       | 实测值                 | 换算                                                              |
| ---------- | ---------------------- | ----------------------------------------------------------------- |
| 轨道底     | `#353535`（53,53,53）  | **20% 白叠页面底**（`#33ffffff` 叠 `#000000`）                    |
| 滑块底     | `#5d5d5d`（93,93,93）  | **20% 白叠「轨道」**（`#33ffffff` 叠 `#353535`）← 不是叠页面底    |
| 选中文字   | ≈`#efefef`（峰值 239） | `#e6ffffff`（90% 白）叠滑块底 = 239                               |
| 未选中文字 | ≈`#ebebeb`（峰值 235） | 与选中**同一个字色**（`couiColorLabelPrimary`），选中只靠滑块区分 |

**修正 8.6 里的两处数值**：那份写的是「轨道 深 `#1fffffff` / 滑块 深 `#33ffffff`」——实测轨道是
**20% 白**（不是 12%），且**滑块叠在轨道上**（不是叠页面底）。按旧值做出来的控件两级都偏暗，跟系统那个不像（本模块已按实测值改，见
`Md3eSettingsPage` 里 `trackColor` / `thumbColor` 的合成）。浅色下沿用 8.6 记的实测值（轨道 `#e2e3e4` = 6% 黑叠页、滑块
`#f6f6f6` = 70% 白叠轨道）。

差 2 个色阶（四舍五入），肉眼一致。

```
COUI 探测：com.coui.appcompat.couiswitch.COUISwitch 造出来了，平台 API 可用（setChecked → isChecked=true）
COUI 探测：com.coui.appcompat.checkbox.COUICheckBox 造出来了
COUI 探测：com.coui.appcompat.button.COUIButton 造出来了
```

1. **类名没被混淆** —— 它们被宿主自己的 XML 布局按名字引用，R8 必须保留类名（`COUISwitch` / `COUICheckBox` /
   `COUIButton` / `COUIListView` / `com.coui.appcompat.dialog.*` 都是原名）。
2. **构造器是标准的 View 构造器** —— 一律 `(Context, AttributeSet[, int])` **没有单参 `(Context)`**（只试单参会得到
   `NoSuchMethodException`，那是探针写错，不是控件不可用）。
3. **平台那部分方法名不会被改** —— 它们 override 的是 `android.view.View` / `TextView` / `CompoundButton`
   的方法，R8 改不了：`setText` / `setOnClickListener` / `setChecked` / `isChecked` / `setEnabled` / `performClick` /
   `setVisibility` … 全都在
4. 监听器也用**平台接口**：`View.OnClickListener` / `CompoundButton.OnCheckedChangeListener` —— 都是平台类，签名稳定。

**哪些不能用**：

- **AndroidX Preference 那套全部不可用**：`androidx/preference/Preference` / `SwitchPreference` / `PreferenceScreen`
  以及 `com.coui.appcompat.preference.*` （`COUIMarkPreference` / `COUISwitchPreference`）**方法名全被 R8 改成
  `A`/`A0`/`B`/`B0` …** ⇒ 没法 `setTitle` / `setSummary` / 挂监听；宿主里连 `PreferenceFragmentCompat`
  都找不到（按名）。 `COUICheckBox.setOnStateChangeListener` / `setState` / `getState`； `COUIButton.getSolidColor` /
  `getStrokeWidth` / `getButtonTypeFromStyle` / `getCornerParams`
  …）⇒ 设计上**只依赖平台 API**，COUI 专有方法当彩蛋，不当依赖。

⇒ 所以「完完全全用系统的组件」是可行的：**开关用 `COUISwitch`、单选/勾选行用 `COUICheckBox`、按钮/分段用
`COUIButton`、列表用
`COUIListView`**（都反射造 + 平台 API 驱动），只有**分段控件的胶囊底**宿主没有对应类（见 10.2），仍按 10.5 的实测规格手搭。

### 10.7 第二批能力边界：滑块、对话框、列表项尺寸（离线核对 `reference/gesture.apk`，2026-10-11）

> 结论先放这里：**滑块可以反射造真的 COUI 控件；对话框的类拿不到、但它的样式与资源名全在**。

**① 滑块（SeekBar）—— 可以反射造**

宿主里这两支的**类名没被混淆**（被 XML 按名引用 ⇒ R8 必须保留），构造器是标准 View 构造器：

| 类名                                                  | 说明                            |
| ----------------------------------------------------- | ------------------------------- |
| `com.coui.appcompat.seekbar.core.COUISeekBarAdvanced` | 首选（带 thumb 缩放等那套动效） |
| `com.coui.appcompat.seekbar.COUISectionSeekBar`       | 备选（分段滑块）                |

- 主题里的样式 attr：**`couiSeekBarStyle`**（还有 `couiSectionSeekBarStyle` / `couiIntentSeekBarStyle`）。
- 三参构造器 `(Context, AttributeSet, int)`
  的第三个参数要传**这个 attr 的 id**，传 0 等于「没样式」⇒ 造出来是裸控件（外观就不对了）。
- 尺寸/颜色资源名一大把（按需取）：`coui_seekbar_view_min_height`、
  `coui_seekbar_progress_padding_horizontal`、`coui_seekbar_progress_height`、
  `coui_seekbar_background_height`、`coui_seekbar_thumb_radius`、
  `coui_seekbar_progress_color_normal`、`coui_seekbar_background_color_normal`。

**② 对话框（AlertDialog）—— 类拿不到，样式与资源名全在**

- **`COUIAlertDialog` 那一族被 R8 混淆了**：`com/coui/appcompat/dialog/a`（含
  `a$a`~`a$f`）。它没有被 XML 引用（dialog 都是代码创建），所以类名保不住 ⇒
  **不能反射使用**。幸存的只有零件类名：`COUIAlertDialogRootLayout` / `COUIDialogTitle` / `COUIAlertDialogMessageView` /
  `COUIAlertDialogOverlayHelper`（当线索可以，当依赖太脆）。
- 但**属性和资源名都在**，于是可以「`Dialog` + 宿主对话框主题 + 按资源名自绘内容」：
- 主题 attr：**`couiAlertDialogTheme`**（还有 `couiAlertDialogStyle`）—— `theme.obtainStyledAttributes → getResourceId`
  取到对话框该用的主题，传给 `Dialog(ctx, id)`。
- 背景/圆角：`drawable/coui_alert_dialog_background`、`dimen/coui_alert_dialog_bg_radius`、
  `dimen/coui_alert_dialog_bg_padding_left`（窗口到屏幕边的距离）。
- 字色：`color/coui_alert_dialog_title_text_color`、 `color/coui_alert_dialog_content_text_color`（各有 `_dark`/`_light`
  变体）。
- 按钮：`dimen/coui_alert_dialog_button_height`、 `dimen/coui_alert_dialog_button_horizontal_padding`、
  `color/coui_alert_dialog_button_color_pressed`；COUI 的按钮是**左右均分的文字按钮**（无底色 + 波纹），强调色用
  `couiColorPrimary`。

**③ 列表项尺寸 —— 之前的 48dp 是量错了对象**

| dimen 名                                                           | 用途                                                                                     |
| ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `coui_list_item_normal_height` / `coui_list_item_preferred_height` | **列表项**行高（首选口径）                                                               |
| `coui_list_item_left_padding` / `_right_padding`                   | 列表项左右内边距                                                                         |
| `coui_delete_alert_dialog_button_height`                           | 这是**对话框按钮**的高（48dp），**不是列表行高** —— 之前拿它当行高，页面偏挤就是这里来的 |

⇒ 一般规律：尺寸**运行时按名字取**（`res.getIdentifier(name, "dimen", "com.oplus.gesture")`），不要把我离线量到的某个数写死进代码 —— 换个字号档就偏了。

不是猜的。涉及的文件：`inject/KeyHooks.kt`（system_server 侧）、`host/action/ActionRunner.kt`（宿主侧）。

### 10.8 宿主的包可见性：声明了 `QUERY_ALL_PACKAGES`（离线核对 `reference/gesture.apk`，2026-10-13）

要列**系统应用**（`ui/screen/AppPicker.kt`
里「显示系统应用」那一项）先得回答「看得见哪些包」，答案就在宿主自己的清单里：

```bash
aapt2 dump xmltree --file AndroidManifest.xml reference/gesture.apk | grep -A1 QUERY_ALL_PACKAGES
#   E: uses-permission (line=29)
#     android:name="android.permission.QUERY_ALL_PACKAGES"
```

- 我们的界面代码跑在**宿主进程**里 ⇒ 包可见性**按宿主算**：`getInstalledApplications(0)`
  拿得到全量（含系统应用），`getLaunchIntentForPackage` / `getApplicationInfo` 对任意包都通。
- ⇒ **模块自己的清单不要加这条权限**：权限按 uid 判，而页面代码的 uid 是宿主（同 `AndroidManifest.xml` 里 `INTERNET`
  那条注释的道理）。
- ⚠️ 这条推翻了一处旧文案：「选择应用」页的空态写着「系统限制了本页可见的应用列表」——那个前提不成立（真出现空列表只会是别的原因）。（2026-10-14 该空态已按此改成「没有找到可以打开的应用」，见
  `changelog/2026-10-14-结构重构.md`。）
- ⚠️ 另一笔：`getLaunchIntentForPackage` 认的入口是 `CATEGORY_INFO` /
  `CATEGORY_LAUNCHER`，对**没有这两个入口**的系统应用一律返回 null
  —— 拿它当「能不能列出来」的判据，会把追加集合筛成空集（2026-10-13 踩过：开关打开了，列表毫无变化）。要「只列拉得起来的」才用它，要「列出装着的系统应用」就别用它。

### 11.1 两台「状态栏」不是一回事

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

### 11.2 `clearCallingIdentity` 在这里是把双刃剑（两条路相反）

宿主发的那条广播，被 hook 接住时**跑在宿主的 binder 线程上** ——

- `togglePanel()`：**不能清身份**。`checkCanCollapseStatusBar` 判的就是 `Binder.getCallingUid()`，宿主恰好持有
  `STATUS_BAR`（dumpsys `granted=true`），清了反而判丢。
- `StatusBarManagerInternal.toggleRecentApps()`：**要清**。它没有权限校验，但下游（SystemUI
  → 启动器代理）看到的调用方应该是 system（uid 1000），与 `PhoneWindowManager` 自己调时一致。

- 两个码在 `PhoneWindowManager` 里都只在 **`handleKeyGestureEvent(KeyGestureEvent, IBinder)`**
  这一支被处理 ——那是 Android 16 的**按键手势**入口（`KeyGestureEvent` 由输入子系统在手势场景下产生），模块下发的普通
  `KeyEvent` 进不去那一支。
- 对照实验：`input keyevent 83` **能**展开通知栏、`input keyevent 187` **不能**唤起概览（用启动器的
  `LRV ... toState=Overview` 日志判定，手势 `input motionevent` 上滑悬停可以）。
- 结论：这两个码不要靠注入，走 11.1 那两条原生调用。

### 11.4 `KeyEvent` 构造器的坑：6 参的第 6 个参数是 **metaState**

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
就是 delegate 的**返回值**，也就是最终落下去的档位（见 12.2 的日志构造）。⇒ 请求**送进去了**，是被 framework 内部改写的；**不是权限被拒**
—— 被拒会抛 `SecurityException: Not allowed to change Do Not Disturb state`，日志里没有这一条。

### 12.2 `AudioService` 的两条入口（`/system/framework/services.jar` → `classes.dex`）

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

### 12.3 内部入口的门槛：`STATUS_BAR_SERVICE` —— 宿主刚好有

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

### 12.4 客户端方法存在，签名是 `(I)V`

`/system/framework/framework.jar` → `classes3.dex`：

```java
// AudioManager.java:8834
public void setRingerModeInternal(int ringerMode) {
    getService().setRingerModeInternal(ringerMode, getContext().getOpPackageName());
}
```

`@hide` ⇒ 只能反射调（宿主进程里隐藏 API 反射可用，同 `inject/input/KeyInjector` 调 `InputManager.injectInputEvent`
那条先例）。

| 做法                                                        | 结果                                                                                       |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| 宿主 uid(10277) 调公共 `setRingerMode(SILENT)`              | 被 delegate 改写成 **VIBRATE**（`delegateModified=1`）                                     |
| 宿主 uid(10277) 跑 `cmd audio set-ringer-mode SILENT`       | **挂死**（`timeout` 退出码 124；ringer 历史里一条记录都没有）；`cmd audio help` 同样零输出 |
| shell(2000) / root(0) 跑 `cmd audio set-ringer-mode SILENT` | `mode=SILENT`、`zen=ZEN_MODE_OFF`                                                          |
| `settings put global mode_ringer 0`                         | AudioService 不采纳（档位不变）                                                            |

⇒ 「宿主身份跑 `cmd` 不行、shell/root 行」这条也解释了 `ShellRunner` 注释里那句「`cmd audio set-ringer-mode`
以 App 身份跑会挂住」。

### 12.6 与勿扰（zen）**没有关系**

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

### 13.1 症状

点「按键绑定」往里走（push）看着正常，**点返回（pop）时上一页一上来就盖住大半屏**，像是没滑就直接铺满了。

### 13.2 根因：入场内容画在最上层 ⇒ iOS 式 pop 的参数在这里不成立

`androidx.compose.animation.AnimatedContent` 内部维护 `currentlyVisible` 列表，
**新状态追加在末尾 ⇒ 后画 ⇒ 入场内容恒定在最上层**。它**没有**「把入场内容放到下层」的参数。

```kotlin
slideInHorizontally(spec) { -it / 4 } togetherWith slideOutHorizontally(spec) { it }
```

—— 新页只走 `width/4`、旧页整条滑出。这套参数成立的**前提是「新页在旧页下面」**
（旧页滑开才把新页露出来）。放到「入场在最上层」的 `AnimatedContent` 里，同一组参数就变成了：**转场第 0 帧新页已经落在
`-width/4`，一上来就盖住 3/4 屏**，再慢慢蹭回原位。

### 13.3 取证方法（可复用）：整帧刚体平移拟合

1. `settings put global animator_duration_scale 10`（350ms 转场放大到 3.5s，便于连拍）。
2. 设备端起连拍：`for i in …; do screencap -p /data/local/tmp/fr/t$i.png; done &` 然后
   `sleep 1; input tap <x> <y>`（`screencap` ≈330ms/帧，3.5s 转场约 10 帧）。
3. 本地按**列签名**（`mean over rows`）对 `ref_before` / `ref_after` 做一维交叉相关，求每帧的最佳平移量：
   - 新页位移 `o`：`F[x] ≈ ref_after[x - o]`
   - 旧页位移 `s`：`F[x] ≈ ref_before[x + s]`

   残差 ≈ 0.00 表示**完美刚体平移**（不是裁剪、不是淡入）。

### 13.4 实测数据（PLK110，1272×2772，density 560）

**修复前**（`pop`，tap 返回键 154,232）：

| 帧  | 新页位移 `o` | 残差 | 含义                                           |
| --- | ------------ | ---- | ---------------------------------------------- |
| t05 | **−303**     | 0.00 | ≈ `−width/4` = −318 ⇒ **第 0 帧就盖住 76% 屏** |
| t06 | −204         | 0.00 |                                                |
| t07 | −129         | 0.00 |                                                |
| …   | …            | 0.00 | 单调收敛到 0                                   |

旧页位移 `s`：60 → 455（残差 6.9，被新页遮住大半，拟合不可信）。

**修复后**（`pop2`，同上坐标）：

| 帧  | 新页位移 `o`                 | 残差                     |
| --- | ---------------------------- | ------------------------ |
| t05 | 无匹配（新页**完全在屏外**） | 5.75                     |
| t06 | −700                         | 3.62（运动模糊抬高残差） |
| t07 | **−622**                     | **0.00**                 |
| t08 | −439                         | 0.00                     |
| t09 | −307                         | 0.00                     |
| t10 | −209                         | 0.00                     |
| t11 | −142                         | 0.00                     |
| t12 | −87                          | 0.00                     |
| t13 | −51                          | 0.00                     |
| t14 | −27                          | 0.00                     |
| t15 | −11                          | 0.00                     |

旧页位移 `s`：76 → 150 → 196 → 229 → 254 → 271（向 `width/4` = 318 收敛）。

⇒ 新页**第 0 帧整页在屏幕外**，再自左滑入；旧页往右退 1/4。与 push 完全镜像。

### 13.5 顺带确认：push 本来就是对的

同一方法测 push（首页 → 按键绑定）：

| 帧  | 新页 `o` | 旧页 `s` | 由 `o` 推 e | 由 `s` 推 e |
| --- | -------- | -------- | ----------- | ----------- |
| t06 | 860      | 103      | 0.324       | 0.324       |
| t07 | 548      | 181      | 0.569       | 0.569       |
| t08 | 373      | 225      | 0.707       | 0.708       |
| t09 | 247      | 256      | 0.806       | 0.805       |
| t10 | 170      | 275      | 0.866       | 0.865       |
| t11 | 107      | 291      | 0.916       | 0.915       |

两页**同一进度 `e`**，且 `o` 从 ≈`width` 单调收敛到 0、`s` 从 0 收敛到 `width/4` —— 完全符合
`slideInHorizontally { it } togetherWith slideOutHorizontally { -it/4 }`。所以**只有 pop 那支是错的**。

### 13.6 修法

```kotlin
if (targetState.ordinal > initialState.ordinal) {
    slideInHorizontally(spec) { it } togetherWith
        slideOutHorizontally(spec) { -it / 4 }      // push：新页自右入，旧页退左 1/4
} else {
    slideInHorizontally(spec) { -it } togetherWith
        slideOutHorizontally(spec) { it / 4 }       // pop ：新页自左入，旧页退右 1/4
}
```

结论：**在 `AnimatedContent`
里写转场，参数必须按「入场内容在最上层」来配**。想用「新页在下层」那套 iOS 参数是做不到的 —— 只能把入场偏移改成整幅
`±it`。

取证期间 `.temp/` 被外部清空过两次（同期还冒出 `ArrowPreference.kt`、 `SwitchPreference.kt`、`coui_docs.html`
等本会话没建过的文件）——**同一工作区有并发会话**。对策：抓帧脚本与产物放
`.temp/anim/`，并且**抓取 + 拉取 + 分析尽量串在同一条命令里**；设备端 `/data/local/tmp/fr` 可当二级缓存（重拉很快）。

## 十四、宿主侧键分发的**权威调用链**（dexdump 指令级，2026-10-13）

> 起因：要用 REA 把宿主「看透」，把 hook 的实现路径压到最短、最不易错。这一节是 `oplus-services.jar`（含
> `classes2.dex`）的 `dexdump -d` 指令级结论，不是猜。结论推翻了之前两处实现假设 —— 见 §14.4。

### 14.1 完整链路（从原始输入到侧键动作）

```
InputReader/InputDispatcher
  → InputPolicy.interceptKeyBeforeQueueing(KeyEvent, down, keycode, policyFlags, interactive)   // 2e7f30
      → InputKeyEventNotifyService.interceptKeyBeforeQueueing(...)                              // 2e7158
          ├─ notifyObserversBeforeQueueing(...)          // 观察者（只读，不改语义）
          └─ notifyInterceptorBeforeQueueing(...)        // 2e71a4 ← 拦截器链
               对 mQueueingInterceptorsCopy 里每个 KeyInterceptor:
                 if (!preCondition()) continue;
                 ret = interceptor.interceptKeyBeforeQueueing(event, keycode, policyFlags, down, interactive)
                 if (ret == 0) intercept = false            // 有人拦下 ⇒ 返回 0
               return intercept ? 1 : 0
      → 返回 0 = 系统吞掉这次按键；返回 1 = 继续往下派发
```

`StrategyActionButtonKeyLaunchApp$2` 在 `initStrategy()` 里经 `InputKeyEventNotifyService.registerKeyInterceptor(...)`
注册进 `mQueueingInterceptorsCopy`（注册后 `Collections.sort`）—— **这就是它被调用的唯一途径**。

### 14.2 `StrategyActionButtonKeyLaunchApp$2` 是**纯转发**（12 条指令）

```
[3058a0] iget-object v0, v6, ...$2;.this$0:...StrategyActionButtonKeyLaunchApp;
[3058a4] move-object v1, v7 / move v2..v5, v8..v11      ← 原样搬 5 个参数
[3058ae] invoke-virtual {v0..v5}, ...StrategyActionButtonKeyLaunchApp;.actionInterceptKeyBeforeQueueing(...)I
[3058b4] move-result v7
[3058b6] return v7
```

`preCondition()` 更短：`const/4 v0, #1; return v0`（恒真）。⇒ **挂 `$2` 与挂外层 `actionInterceptKeyBeforeQueueing`
落在同一条代码路径上，能力完全相同**；两个都挂只会让同一次按键被回调两次。这是 §14.4 的一处优化点。

### 14.3 `actionInterceptKeyBeforeQueueing` 的 keyCode 分发树（335 指令，指令级）

| 指令地址 | 比较                                      | 去向                                              |
| -------- | ----------------------------------------- | ------------------------------------------------- |
| `0000`   | `const/16 v0, #780`                       | —                                                 |
| `0008`   | `if-ne v10(=keyCode), 780`                | 不等 ⇒ 跳 `00be`                                  |
| `000d`   | `getRepeatCount()==0`                     | 780 主分支（拦截/放行判定）                       |
| `00be`   | `const/16 v0, #782`                       | —                                                 |
| `00c2`   | `if-ne keyCode, 782`                      | 不等 ⇒ 跳 `0122`                                  |
| `00c4`   | `getRepeatCount()==0`                     | 782 分支（长按：`longPressStartVibrate`）         |
| `0122`   | `const/16 v0, #781`                       | —                                                 |
| `0124`   | `if-ne keyCode, 781`                      | 不等 ⇒ 跳 `0140`                                  |
| `0126`   | `getRepeatCount()==0`                     | 781 分支（`pokeUserActivity` + `singleTap` 统计） |
| `0140`   | `const/16 v0, #765`                       | —                                                 |
| `0142`   | `if-lt keyCode, 765`                      | 小于 ⇒ 跳 `0148`                                  |
| `0144`   | `const/16 v0, #772`                       | —                                                 |
| `0146`   | `if-le keyCode, 772`                      | 大于 ⇒ 跳 `014e`                                  |
| `0148`   | `mOplusPowerKeyHandler.removeMessages(2)` | 765..772 归**电源键**处理                         |
| `014e`   | `return v1`（v1 = 1）                     | 公共尾：**未接管 ⇒ 返回 1（继续派发）**           |

780 主分支内部（`000d`–`00bc`）： `mActionSwitchState` 为空则该读 `readActionButtonSwitchState(userId)`；
`"nothing".equals(mActionSwitchState)` ⇒ 打 `switch_nothing` 统计并 **`return v4`（0，吞掉）**；否则
`checkInterceptConditions(down)`：非 null ⇒ **`return interceptResult.intValue()`**；再按 `getAction()` 分
`interceptActionKeyDown(event)` / `interceptActionKeyUp()`，两者后 **`return 0`**。

⇒ **框架原生的 780 分支在「开关=nothing」时返回 0（吞键）**，与模块的「接管 ⇒ 返回 null」语义一致（详见 §14.5）。

### 14.4 由此得到的 **hook 实现路径优化**（本次改动）

1. **`Framework.kt` 不再挂 `...$2`**：`$2` 是纯转发（§14.2），挂它 0 收益、且与外层 `actionInterceptKeyBeforeQueueing`
   重复回调。删掉 `STRATEGY_CALLBACK_CANDIDATES` 与对应循环，接管点从「4 个候选」收敛为「1 个外层类 + PM 兜底」。
2. **`KeyId.acceptedKeyCodes` 补齐 781**：dexdump 证明 780/781/**782** 三个码都进这条方法（781 走
   `pokeUserActivity` + 单击统计）。旧集 `{735, 780, 782}`
   **漏了 781**，若某场景它作为原始码出现会被误判「不是侧键」而放行。
3. **接管点去重**：`findSideKeyPoints` 用 `LinkedHashSet` 按 `owner#method`
   去重，防止候选类名重复 / 同方法多入口导致的重复安装。

### 14.5 返回值语义（模块 `onIntercept` 的 `true/false` 对接点）

- 框架侧 `KeyInterceptor.interceptKeyBeforeQueueing` 返回 **0 = 吞掉**（⇒ `notifyInterceptorBeforeQueueing` 置
  `intercept=false` ⇒ 上层返回 0 ⇒ 系统不派发）； **非 0 = 放行**。
- 模块 `SideKeyInterceptor.handle` 返回 `true=接管`，`KeyHooks.onIntercept` 把 `true` 映射为**返回 `null`**（Xposed 的
  `null` = 吞掉原方法返回值 / 不执行原方法）。两者语义对齐：接管 ⇒ 系统看不到这次按键。

### 14.6 顺带确认：框架**自己**也判「长按 / 单击」，但只有 `singleTap`/`longPress` 两个一等手势

`checkInterceptConditions(Z)`（190 指令）依次挡：`isUserSetupComplete()`、
`isWsnCompleted()`、`getGameStateValue()`+`isKeyguardLocked()`+`mGamePreventSwitch`（游戏防误触 ⇒ 返回 0）、
`getIsNearState()`+`isNotSupportedPocketMisTouch()`（口袋防误触 ⇒ 返回 0）、
`getUnderWaterCameraStatus()`（水下相机）。任何一项命中都 `return 0`（吞键、不派发）。⇒
**框架会在这些场景主动吞掉侧键**，模块 hook 在**更外层**，拿到的是「框架还没判过」的原始事件 ——这正是模块把判定器放在
`system_server` 侧的合理性来源（§9 已述）。

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

## 十六、「放行」的两条坏路：空转与自持循环（实测，2026-10-13，PLK110）

> ⚠️ **2026-10-14：「放行」（`native` / `passthrough`）这一档已从模块里整条删掉**
> —— 判定手势必须先吞掉 DOWN，做不到「只交还某一个手势」，用户要的是「整键给系统」，而那正是主开关的语义。本节保留的是**当时**的实测记录（重注入、场景派发、自持循环都在这一档上踩过），不代表现在的实现；现状与取舍见
> `docs/implementation.md` 第三节。

「放行」（`native` / `passthrough`）的实现是「吞掉原始键 + 宿主侧重注入一对 735」。真机实测暴露了两条坏路：

**① 空转**（`single=native`
时的旧表现）：重注入的 735 不会被本模块认领（见下），它只走原生管线 ——而原生动作的载体是框架派给宿主的 `what=1`
场景消息，宿主侧钩子**无条件吞掉**它 ⇒ 模块吞了原键、原生动作也没了 ⇒ 按下去什么都不发生。

**② 自持循环**（本次实测复现）：物理按压 → 策略类钩子认领并吞掉原方法 → 判出单击 → 注入侧转发 `passthrough`
→ 宿主重注入 735 →
**重注入的 735 又一次进到策略类钩子**（只按 keyCode 认领，认不出是合成事件）→ 再判一次单击 → 再放行一次 ⇒ 每 ~110ms 一轮，日志三行一组连续刷：

```
(com.oplus.gesture) 收到 system_server 转来的手势：key=plus phase=passthrough repeat=false
(com.oplus.gesture) 侧键：放行一次（重新注入 keyCode=735）
(com.oplus.gesture) 场景派发放行（本次按键判为「放行」⇒ 执行原生动作）
```

实测窗口：22:47:56.579 → 22:47:58.140（`modules_*.log`，约 16 轮 /
1.6s）。**次生现象**：每轮的场景动作把侧键设置页重新拉起，宿主 `Activity` 是 `singleInstance` ⇒ 走 `onNewIntent`
⇒ 模块的「重进即清栈回首页」被反复触发（用户看到的是「进其他页面自动退回主页」）。停掉宿主进程（`am force-stop com.oplus.gesture`）即断环 ——环需要宿主侧的重注入才能维持。

**❌ 曾用错的判别手段**：`adb shell input keyevent 735` **不会**触发本模块的钩子（注入后 `(system)`
侧零转发行，设备也无任何原生动作），不能用它代替物理按压做「放行」实验 —— `input` 与
`InputManager.injectInputEvent`（`KeyInjector`：`deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD`(-1)、
`scancode = 0`、`source = SOURCE_KEYBOARD`）的 deviceId / source 组合不同。

**修法（2026-10-13）**：

- 注入侧按
  `keyEvent.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD`（-1）识别**合成事件**、不认领（放行给系统）⇒ 断掉 ② 的环；
- 宿主侧的场景派发钩子改为「本次按压判为放行 ⇒ 放行**第一条** `what=1`（原生动作的载体），其余仍吞」⇒ 救回 ①；
- 宿主侧另加兜底闸：1s 内收到 ≥3 次放行判定即判为环，暂停注入 3s（防个别 ROM 判不出合成事件）。

**⚠️ 判定的到达顺序是竞态的（实测）**：宿主主线程忙时（原生动作自己就会占住它），框架那条 `what=1`
会**排在「放行」广播前面**到达（实测 23:02:22 / 23:02:24：`what=1` 早 1ms，判据扑空、被误吞，随后补发的判定已无 `what=1`
可放 ⇒ 原生动作丢失）。所以宿主侧的钩子不能只看「判定到没到」这一瞬间：

- 判定已到（配额有余）⇒ 立即放行（正常路径）；
- 判定未到 ⇒ **扣住**这条 `what=1`，700ms 内判定若到达就补投（补投必须**复制**一条新 `Message` ——原实例在本次
  `handleMessage` 返回后已被 Looper 回收进复用池）；
- 一次「放行」只放一条（配额），同一次按压的「按压自己那条」与「重注入那条」不会各放一次。

实测两条路都跑通（23:04:51 正常路径 / 23:04:53 竞态路径走延迟补投），每次按压各只放一条。

## 十七、电源菜单的直接入口（`services.jar` 反编译，2026-10-13，PLK110）

动作 `power:menu` 走这条，全程在 system_server 内本机调用：

```
ServiceManager.getService("window")  →  WindowManagerService 实例（不是代理）
  field mPolicy : WindowManagerPolicy  →  PhoneWindowManager
    showGlobalActions()                →  mHandler.sendEmptyMessage(10)
      showGlobalActionsInternal()      →  mGlobalActions ??= mGlobalActionsFactory.get()
                                         mGlobalActions.showDialog(keyguardShowing,
                                                                  isDeviceProvisioned())
        LegacyGlobalActions.handleShow() →  mDialog.show()
```

- **不能调 `WindowManagerService.showGlobalActions()`**：它第一句是
  `checkCallingPermission("android.permission.INTERNAL_SYSTEM_WINDOW")`，而 `ContextImpl.checkCallingPermission` 只处理
  `pid != myPid()` 的情况、**同进程调用一律返回 DENIED** ——在 system_server 里本机调必抛
  `SecurityException`。必须往下钻一层到 `mPolicy`，那层没有权限检查。
- **ColorOS 没有自己的电源菜单类**：`oplus-services.jar` 搜 `GlobalActions` 0 命中；`services.jar` 3 个（`GlobalActions`
  / `GlobalActionsProvider` / `LegacyGlobalActions`）。弹窗由 system_server 自渲染（`handleShow` 里直接
  `mDialog.show()`），不转发给 SystemUI。
- 与「长按电源键」的差别：那条要等框架的长按超时（`interceptPowerKeyDown` →
  `powerLongPress`），本机调用是立即的，也不经过输入子系统。

### 17.2 系统侧键场景表：13 个场景的调用目标（真机 `content query`，2026-10-13）

`content://uri.gesturemanagerprovider/action_button`（宿主 `com.oplus.gesture` 的 `ActionButtonProvider`）。它要
`oppo.permission.OPPO_COMPONENT_SAFE`，**shell 身份读不到**（`SecurityException`），用 `su -c 'content query …'`：

| scene_name     | scene_owner                    | click_action_info                                        | type    |
| -------------- | ------------------------------ | -------------------------------------------------------- | ------- |
| ring_mode      | system_server                  | `getRingerMode`                                          | method  |
| flash_light    | com.android.systemui           | `…notification.keymagicservice.KeyFlashlightService`     | service |
| screen_shot    | com.oplus.screenshot           | `showScreenShotFluidCloud`                               | method  |
| no_disturb     | com.android.systemui           | `…notification.keymagicservice.KeyDndService`            | service |
| recording      | com.coloros.soundrecorder      | `com.soundrecorder.common.card.KeyMagicMessengerService` | service |
| camera         | com.oplus.camera               | `com.oplus.camera.feature.keymagic.KeyMagicService`      | service |
| translate      | com.coloros.translate          | `com.coloros.translate.ui.cube.KeyMessengerService`      | service |
| quick_function | com.oplus.gesture              | `getClickActionInfo`                                     | method  |
| start_app      | com.oplus.gesture              | `getClickActionInfo`                                     | method  |
| flash_memory   | com.coloros.colordirectservice | `…flashnotes.business.service.DataCollectService`        | service |
| controlcenter  | com.android.systemui           | `…qs.magickey.QsMagicKeyService`                         | service |
| ocrscanner     | com.coloros.ocrscanner         | `com.oplus.scanner.ui.keymagic.KeyMagicService`          | service |
| test           | com.oplus.exserviceui          | `com.oplusos.accessibilityhelper.KeyMessengerService`    | service |

（`long_press_action_info` / `second_menu_*` 另有值，多数与 click 指向同一个服务；完整行见真机。）

- **`type=service` 就是「起一个服务」**：模块的 `host/action/OplusFeature` 只抄其中四行，不多猜。
- **三个目标服务都带签名级权限**（`DataCollectService` → `com.oplus.permission.safe.SECURITY`； `InvokeEntranceService`
  → `oppo.permission.OPPO_COMPONENT_SAFE`），而**宿主 `com.oplus.gesture` 本人就持有** 这些权限（另有
  `WRITE_SECURE_SETTINGS` / `ACCESS_NOTIFICATION_POLICY`(granted) /
  `MODIFY_AUDIO_SETTINGS`）⇒ 从宿主进程直接起即可，不需要 root，也不必绕 system_server。
- **四道权限闸门的真机核对**（`dumpsys package`，2026-10-13，PLK110；四个服务 `exported=true`）：宿主四项全
  `granted=true` —— `oppo.permission.OPPO_COMPONENT_SAFE`（`InvokeEntranceService`）、
  `com.oplus.permission.KEY_MAGIC_SERVICE`（`KeyDndService`，`signature|privileged`，由 SystemUI 定义）、
  `com.oplus.permission.safe.SECURITY`（`DataCollectService`）、`com.oplus.permission.safe.ASSISTANT`
  （`KeyMagicMessengerService`）。**只证明「起得到」，不证明「起了会做那件事」。**
- **诊断坑**：没有 `<intent-filter>` 的服务（`KeyDndService` 就是）**不会出现在** `dumpsys package` 的 Service Resolver
  Table 里 —— 别据此判定「服务不存在」，要去 APK 清单里查。
- **起法按目标清单声明分两种**：声明了 `foregroundServiceType=special_use` 的（`FLASH_MEMORY`，见 17.3）先
  `startForegroundService`，那是它给自己定的起法；没声明的先 `startService` —— 对方不调 `startForeground`
  时，用前台方式起会让**它的进程**抛 `ForegroundServiceDidNotStartInTimeException`。两条都失败才算失败。
- **intent 形式**：有 action 的用
  `Intent(action).setPackage(owner)`（系统自己的写法，走清单 filter）；场景表里只有组件名（无 action）的（`no_disturb` /
  `recording`）才用显式组件 —— 这两个服务 `exported=true`
  且无 filter（`KeyDndService`）或 filter 与场景表 action 不一致（`KeyMagicMessengerService` 的 action 是
  `com.oplus.soundrecorder.keyMagicMessengerService`，场景表没写），显式组件更忠于场景表。

### 17.3 小布识屏：两条入口最终都落在同一个服务上

13 个侧键场景里没有它。入口是
`com.coloros.colordirectservice/.InvokeEntranceService`（`ColorDirectService.apk`）。清单声明：`exported=true`、`android:permission=oppo.permission.OPPO_COMPONENT_SAFE`、
`foregroundServiceType=special_use` +
`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="special_use_for_start_identification">`，intent-filter 共 14 条 action。系统自己用的是其中两条：

- **侧边栏** —— `com.coloros.smartsidebar` 的 `BreenoScreenIdentifyTool.handle()`：
  `Intent("oplus.intent.action.DIRECT_SIDEBAR_SERVICE").setPackage(getValidPkg())`， `getValidPkg()` 直接返回字面量
  `"com.coloros.colordirectservice"`；extra 为
  `extra_entrance_function=full_screen_ocr`、`triggered_app=<自己包名>`，另带可选点击坐标 `first_point_down_x` /
  `first_point_down_y`；最后 `startForegroundService`。
- **导航栏长按** —— `com.android.systemui` 的 `OplusOcrScreenService.getServiceIntent()`：action 用
  `com.coloros.directui.InvokeEntranceService`，同样 `setPackage("com.coloros.colordirectservice")`，extra 多一个
  `skip_gesture_check=true` 和 `StartUpType`（2=手势条 / 1=虚拟键 / 0=其它），但起法是
  `bindServiceAsUser`（`BIND_AUTO_CREATE|BIND_ALMOST_PERCEPTIBLE`）+ 走
  `com.oplus.infocollection.collection.IEntranceServiceInterface` 的 AIDL。**宿主拿不到这个接口，不采用。**

结论：之前从别处推测的 `DIRECT_SIDEBAR_SERVICE` + `extra_entrance_function=full_screen_ocr`
是对的，两条 action 只是同一 filter 里的兄弟项。据此改两处：

- **起法**：`special_use` 说明它就是按前台服务设计的 ⇒ 有 action 的目标改用
  `Intent(action).setPackage(owner)`（系统自己的写法），并按清单声明的类型决定先试
  `startForegroundService`（`FLASH_MEMORY` 的 `DataCollectService` 同样声明了 special-use）。

顺带记一条死路：`AIUNIT_OCR_SERVICE` / `OCR_SERVICE`
看着更对口，但**不在这条 filter 里**，只是该 dex 里出现过的字符串（属它发给别人的）；侧边栏自身也没有独立识屏服务。

### 17.4 场景表 `type=service` 的两种调用方式（2026-10-13，PLK110，实测教训）

场景表**只有**
`scene_name / scene_owner / click_action_info / click_action_type / long_press_* / second_menu_* / long_press_has_own_vibrator`
这些列 —— **没有 extras，也区分不出「起服务」还是「bind 后发 Message」**。光抄表不够，得再反编译目标服务看入口：

- **Messenger 绑定型**（`KeyBaseService` 族：`KeyDndService` / `KeyFlashlightService`； `KeyMagicMessengerService`；估计
  `KeyMessengerService` / `KeyMagicService` 同理）—— **没有 `onStartCommand`**，入口是 `onBind` 返回的
  `Messenger`。协议（`KeyBaseService$messenger$1.handleMessage`）： `what=1` 单击 →
  `handleActionButtonClick()`；`what=2` 长按 → `handleActionButtonLongPress()`；结果经 `message.replyTo.send(...)`
  回给调用方 ⇒ **调用方必须设 `replyTo`，否则服务端空指针**。 `startService`
  对它们是空操作：实测（2026-10-13）`startService` / `startForegroundService`
  都被 AMS 受理、logcat 里目标进程零动静，`settings get global zen_mode` 纹丝不动 —— 连 `su` 下
  `am start-foreground-service` 也一样。这就是「新加的动作全都不触发」的根因。
- **带 extras 的 startService**（`DataCollectService`，一键闪记）—— 有 `onStartCommand`，但它读
  `triggerType`（`TriggerType.MAGIC_KEY` = **1**；UNKNOWN=0 / THREE_FINGERS_UP=2 / CUI=3 / SCREENSHOT=4 / VOICE_RECORD=5
  / SCREENSHOT_NORMAL=6 / SCREENSHOT_LONG=7 / SCREENSHOT_AREA=8 / AUTO_PAYMENT=11 / AUTO_COLLECT=12 /
  AUTO_PAYMENT_NOTIFICATION=14）和 `longPressEventType`（0=单击 / 1=语音 / 2=长按）。不带 `triggerType`
  落到 UNKNOWN 分支，记 `trigger type error`
  后什么都不做。另有前置：AI 内存（闪记）已装、`USER_SETUP_COMPLETE`、开关未关，否则 `stopSelf`。

模块侧结论：`host/action/OplusFeature.Target` 增加 `message`（ Messenger 型发什么码）， `host/action/AppLauncher`
里 bind → `Messenger.send` → 立刻解绑；带 extras 的照常 start。

### 17.5 三个 `oplus:` 动作的真实入口（2026-10-14 复核，PLK110）

**结论：模块侧整条链路是通的，三条命令先前全失效的根因在「取了单击那档」。**
这一族里**单击档经常只回一句提示，「长按」那档才真正干活**。

证据链（`modules.log`，2026-10-14 00:34–00:36）：三条都执行到了目标、都无效果 ——

```
侧键·单击 → oplus:no_disturb（宿主进程执行） → Messenger.send(what=1)(com.android.systemui/KeyDndService)
侧键·单击 → oplus:recording（宿主进程执行）  → Messenger.send(what=1)(com.coloros.soundrecorder/KeyMagicMessengerService)
侧键·单击 → oplus:flash_memory（宿主进程执行）→ startForegroundService(com.coloros.colordirectservice/DataCollectService)
```

场景表实测全列（`su -c 'content query --uri content://uri.gesturemanagerprovider/action_button'`）：

| scene        | click_action_info / type                         | long_press_action_info / type                                      |
| ------------ | ------------------------------------------------ | ------------------------------------------------------------------ |
| no_disturb   | `…keymagicservice.KeyDndService` / service       | **同上** / service                                                 |
| recording    | `…card.KeyMagicMessengerService` / service       | **`oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON` / activity** |
| flash_memory | `…business.service.DataCollectService` / service | 同上 / service（另有 `second_menu_activity_info=tips://web/…`）    |

另有一列 `long_press_has_own_vibrator`（这三个场景都是 `0`）。

- **录音**：`KeyMagicMessengerService`（`NewSoundRecorder.apk`）`<init>` 里
  `mMessenger = Messenger(Handler(mainLooper) { … })`，`onBind` 返回 `mMessenger.binder`； `handleMessage`：`what==1`
  → 组 `side_text` / `app_name` / `fluid_cloud_text` / `icon_res_id` 回给 `msg.replyTo` + 拿一条 wakelock；`what==2`
  → 回 `startResult=reply`。 **两条分支都不碰录音机** ⇒ 对它发 what=1 是死路（`side_text`
  取的就是「长按开始录音」）。真入口是长按档那条界面：清单里
  `<activity name=oplus.multimedia.soundrecorder.slidebar.TransparentActivity permission=oplus.permission.OPLUS_COMPONENT_SAFE exported=True>`，filter 里有
  `oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON`；真机
  `cmd package resolve-activity -a oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON`
  也落它（透明界面，进去就起 / 停录音）。
- **免打扰**：SystemUI 清单声明
  `<service name=com.oplus.systemui.statusbar.notification.keymagicservice.KeyDndService permission=com.oplus.permission.KEY_MAGIC_SERVICE exported=True>`；实现是
  `KeyBaseService` → `KeyBaseController`，`messenger` 的 `handleMessage` 把 `what` 分派给 `handleActionButtonClick()` /
  `handleActionButtonLongPress()`。`KeyDndController` 里 **只有 `handleActionButtonLongPress` 读
  `ZenModeController.getZen()` 并 `setZen(...)`** （外加 `dissmissNotificationCenter`、息屏时延迟 500ms 的 `openDnd`）；
  `handleActionButtonClick` 只判 `isOnKeyGuard()` / `isScreenOn()`、全程不碰 zen
  ⇒ 发 what=1 的表现就是「什么都不发生」。
- ⚠️ **别拿「jadx 摘要里没有 `onBind` / 没有无参构造器」判它「不是 Service」**： `KeyDndService`
  的类摘要只列到构造器，`KeyBaseService$messenger$1.handleMessage` 与 `KeyDndController.handleActionButtonClick`
  jadx 都只给 "no decompiled body"。实测 `bindService` 能连上（`onServiceConnected` 到达且 `send`
  成功）—— 那是**载荷**问题不是**通道**问题。这两条的结论来自本地 dex 指令级 disassembly（`.temp/rea/dexasm.py`，直接读
  `code_item`）。
- **一键闪记**：**表里的 `scene_owner` 过期了 —— `com.coloros.colordirectservice` 那份是死的。**
  实测（01:00:28，模块调它）：进程起来了、`onStartCommand` 也进了，但第一道闸门就把它挡住 ——
  `ColorDirectServiceLog: DataCollectService: Gleaner has Installed or intent is null, stop service`，紧跟
  `onDestroy`，**连 extras 都没读**。对应代码就是 `if (c.c() || intent == null) { stopSelf(); return 2; }`
  —— 它认「Gleaner 已安装 ⇒ 我不干」。真入口是新包
  `com.oplus.gleanerservice`（`/product/app/Gleaner/Gleaner.apk`）里的同名服务：

  ```
  <service name=com.oplus.gleanerservice.flashnotes.business.service.DataCollectService
           permission=com.oplus.permission.safe.SECURITY exported=True
           foregroundServiceType=type=hex:1073741824>          <!-- SPECIAL_USE -->
    <property name=android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE value=special_use_for_ai_memory>
    <intent-filter><action name=oplus.gleanerservice.intent.action.COLLECT_DATA>
  ```

  它的 `onStartCommand` **没有「Gleaner 已安装就退出」那道闸门**，只读 `triggerType` （`MAGIC_KEY`=1 →
  `CollectTaskManager.j(...)` 真正开始采集；`UNKNOWN` / `SCREENSHOT` → 记 `trigger type error`），另有一道
  `USER_SETUP_COMPLETE` / `a.b()` / `a.a()` / `a.c()` 的组合闸门。 **实测可用**：01:02:43 用
  `su -c 'am start-foreground-service -a oplus.gleanerservice.intent.action.COLLECT_DATA --ei triggerType 1 -n …'`
  起它，状态栏随即出现闪记的「已记住」胶囊，服务约 15s 后自己 `onDestroy`。
  - `longPressEventType` 只有 CDS 那份读，Gleaner 这份不读 ⇒ extras 只留 `triggerType=1`。
  - **`uid 0` 会绕过组件权限**（`checkComponentPermission` 对 ROOT / SYSTEM_UID 直接放行），所以这一族都能用
    `su -c 'am …'`
    直接复现，不必真按键 —— 上一轮「起法（前台/普通）导致被回收」的猜测因此作废：**起法是对的，错的是目标**。

- 三个目标都带签名级权限（`oplus.permission.OPLUS_COMPONENT_SAFE`、
  `com.oplus.permission.KEY_MAGIC_SERVICE`、`com.oplus.permission.safe.SECURITY`），宿主 `com.oplus.gesture` 都在
  `requested permissions` 里（`dumpsys package`）。

### 17.6 小窗打开应用的接口（`oplus-framework.jar`，2026-10-14，PLK110）

> **汇总定稿见 `docs/zoom-window.md`**
> —— 两条命令的现行实现、所有入口配方、动画方案、反射坑都在那边；本节保留过程性取证（逐条调用链、日志原文、试错记录）。

ColorOS 的「小窗」在框架里叫 **ZoomWindow**（同族还有 `startMiniZoomFromZoom` / `hideZoomWindow` /
`getZoomBoundsByTaskId` / `registerZoomStateListener`）。

- **客户端类 `android.app.OplusActivityManager`**（在 `/system/framework/oplus-framework.jar`，同目录有
  `boot-oplus-framework.vdex`；它内部的 `android.app.OplusActivityTaskManager` 只是那层 AIDL 门面）：
  ```
  startZoomWindow(Intent, Bundle, int userId, String callPkg): int
  startZoomWindowByPendingIntent(PendingIntent, Bundle, int, String): int
  startActivityForFreeform(Intent, Bundle, int, String): int
  hideZoomWindow(int) / getZoomBounds(boolean, int) / isSupportZoomWindowMode()
  ```
  取实例走静态 `getInstance()`。
- **服务端**在 `oplus-services.jar`：`com.android.server.wm.OplusActivityTaskManagerService`（及 `…ServiceEx` /
  `IOplusActivityTaskManager`），另有一支 `OplusFloatingWindowHelper` / `IOplusFloatingWindow`。
- **权限**：宿主 `com.oplus.gesture` 的 `android.permission.MANAGE_ACTIVITY_TASKS: granted=true` （另有
  `START_ANY_ACTIVITY`、`WRITE_SECURE_SETTINGS`，都 granted）⇒ 这族接口从宿主进程调得动。
- **隐藏 API**：宿主 `dumpsys package` 给出
  `hiddenApiEnforcementPolicy=0`（DISABLED）⇒ 反射这些隐藏类与成员不会被拦；清单里的 `usesNonSdkApi=false`
  不是判据，别拿它下结论。
- **另一条退路**（公开 API，但那一对常量 / 方法本身也是隐藏的）：`ActivityOptions.makeBasic()` +
  `setLaunchWindowingMode(WINDOWING_MODE_FREEFORM)`；ColorOS 不认它时就是一次普通打开。
- 模块侧：动作 `window:<包名>`（`Action.AppWindow`）→ `MiniWindowLauncher.launchAppInWindow`，入口解析与 `app:`
  共用（17.5 那套显式组件兜底照用），起法逐层降级 `startZoomWindow` → 自由窗口 → 普通
  `startActivity`，每层都记日志（断在哪一环看日志）。

#### 17.6.1 小窗为什么「调了没反应」：`extra_window_mode` 与服务端那个 0（2026-10-14 真机实证）

**现象**：真机日志里 `window:<包名> → startZoomWindow=0`，屏幕毫无变化（`zoom:mini` 也是调完没动静）。

**两条调用链**（dex 反汇编逐条核过，`.temp/rea/dexasm.py`）：

```
android.app.OplusActivityManager#startZoomWindow(Intent, Bundle, int userId, String callPkg): int
   ├─ $Stub$Proxy：writeTypedObject(Intent) → writeTypedObject(Bundle) → writeInt → writeString
   │                → transact(14) → readException → **readInt**（返回值是服务端给的）
   └─ OplusActivityTaskManagerServiceEnhance#startZoomWindow
        ├─ mAmInternal.enforceCallingPermission("oplus.permission.OPLUS_COMPONENT_SAFE", "startZoomWindow")
        └─ FlexibleWindowManagerService.startFlexibleTask(...) → FlexibleTaskController.startFlexibleTask(...)
             ← 返回值一路原样传回客户端
```

- **返回值不是成败标志**（真机探针实测，`.temp/probe/`，app_process + root 直接调）：

  | 调用                                                     | 返回  | 屏幕                                       |
  | -------------------------------------------------------- | ----- | ------------------------------------------ |
  | `startZoomWindow(微云盘, Bundle{})`（不带 extra）        | `0`   | 应用**全屏**打开（服务端自己退成普通启动） |
  | `startZoomWindow(微云盘, Bundle{extra_window_mode=100})` | `0`   | **小窗**出现 ✓                             |
  | `startZoomWindow(设置, Bundle{…=100})`                   | `0`   | **小窗**出现 ✓                             |
  | `startZoomWindow(当前前台那个活动, Bundle{…=100})`       | `3`   | **不变**（前台转小窗走不通）               |
  | 组件名写坏（相对类名拼成双点）                           | `-92` | 不变（错误码）                             |

  结论：**0
  = 已发起**（不管起没起成小窗），**负数 = 错误码**；模块侧只有负数才降级。「带 extra 起了小窗也返回 0」这条是关键 —— 按「0
  = 失败」降级会把好不容易通的路径再破坏掉。

- **根因：Bundle 里得带窗口模式**。空 `Bundle` 时服务端把它当普通启动。能用的调用方是 SystemUI 的
  `com.oplus.systemui.qs.scenario.domain.model.ScenarioAssistantNavigationImpl#startActivityByZoonWindow`：

  ```
  Bundle b = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle();
  b.putInt("extra_window_mode", 100);   // OplusZoomWindowManager.EXTRA_WINDOW_MODE / WINDOWING_MODE_ZOOM
  OplusZoomWindowManager.getInstance().startZoomWindow(intent, b, getCurrentUser(), ctx.getPackageName());
  ```

  常量表（`com.oplus.zoomwindow.OplusZoomWindowManager`，`ofw/classes2.dex`）：
  `EXTRA_WINDOW_MODE = "extra_window_mode"`、`WINDOWING_MODE_ZOOM = 100`、
  `WINDOWING_MODE_ZOOM_TO_FULLSCREEN = 101`、`WINDOWING_MODE_FULLSCREEN = 1`、`WINDOWING_MODE_ZOOM_LEGACY = 6`。

- **`startMiniZoomFromZoom(int)`
  在这台 ROM 上是废接口**：全量扫过 SystemUI（9 个 dex）、桌面（OplusLauncher，7 个 dex）、设置侧拉到的 APK 与
  `oplus-services` —— 除了框架自己的 AIDL 门面与 SDK 封装，**没有任何调用方**；原型是 `void`，没有返回值可判。探针把
  `startWay = 1 / 2 / 7` 各调一遍（当时屏幕上正开着一个小窗），全部无可见变化。
- **`startZoomWindow` 的返回值就是 `ActivityStarter` 的 `START_*` 码**：`0` = `START_SUCCESS`， `3` =
  `START_DELIVERED_TO_TOP`（intent 交给已在栈顶的那个实例消化、屏幕不变），`-92` 一类负数是错误码。服务端
  `FlexibleTaskController.startFlexibleTaskInternal` 里 `extra_window_mode == 0x64` 时才
  `setLaunchWindowingMode(100)`；home 正在过渡时它会**延后执行并立刻返回 0**（`Slog.d` 里有 "wait for
  HomeTransitionFinish startFlexibleTask"）。
- **`FLAG_ACTIVITY_MULTIPLE_TASK`
  才是「前台应用也能起小窗」的那把钥匙（2026-10-14 起模块已不再用它，见 §17.6.5：它会无限多开实例）**（二次真机对照，同一台机、`设置`
  当前正 resume，探针逐组合单调用）：

  | flags                                                | 返回 | 结果                                                  |
  | ---------------------------------------------------- | ---- | ----------------------------------------------------- |
  | `NEW_TASK`（= `getLaunchIntentForPackage` 自带那个） | `0`  | 又开出一份**全屏**实例（`windowingMode=1`），不是小窗 |
  | `NEW_TASK \| MULTIPLE_TASK`（`0x18000000`）          | `0`  | **小窗出现**（`windowingMode=100`，独立 task）✓       |

  `NEW_TASK` 单独一个会被 `ActivityStarter` 判成「交给栈顶那个实例消化」（`3`）或者重开一份全屏实例；加上
  `MULTIPLE_TASK` 才会真的另起一个 task，窗口模式才落得下。早先「`+MULTIPLE_TASK` 也是
  `3`、前台转小窗走不通」那次结论**有误** —— 当时判据只有返回码 + 截图，没看 `getAllTopAppInfos()` 的
  `windowingMode`，把「重开了一份全屏实例」误读成「没起」。

- **「把前台应用收成小窗」的可用配方，已按模块的 intent 形状逐字复现**：
  ```
  Intent i = pm.getLaunchIntentForPackage(pkg);   // flg=0x10000000, cmp=com.android.settings/.Settings
  i.addFlags(NEW_TASK | MULTIPLE_TASK);           // → 0x18000000
  startZoomWindow(i, Bundle{extra_window_mode=100}, userId, ctx.packageName)  → 0
  复核：新 task windowingMode=100（小窗 ✓），原全屏 task 仍在（= 第二份实例）
  ```
  返回 `3` 时先发一个 CATEGORY_HOME 的 intent 回桌面、延迟 400ms 再起一次（`MULTIPLE_TASK`
  在场时正常走不到这一档，留着当兜底）。模块里 `window:<包名>` 与 `zoom:mini` 共用这一套（见
  `MiniWindowLauncher.launchAppInWindow`）。**代价**：小窗里是新起的一份实例，不是把现有那扇窗挪过去。
- **对「刚退到后台的应用」起小窗也成立**：先回桌面、再对 `设置` 调同一支接口，拿到 `0` +
  `windowingMode=100`，且能稳定停住（+1s / +5s / +11s 三张截图核过，小窗一直浮在桌面上）。只是既然 `MULTIPLE_TASK`
  那条在前台就通，`zoom:mini` 不必再绕「先回桌面」这一圈。
- **桌面那条「带几何启动」配方在宿主进程里不生效**（已实测）：取 `getAllTopAppInfos()` 的 taskId/displayId/topActivity →
  intent 带 `androidx.activity.LaunchScenario=1` + `LaunchFlexibleTaskId` →
  `calculateFlexibleWindowBounds(intent,-2,displayId)`（这步真返回 576 字节的 Bundle）→
  `ActivityOptions.setExtraBundle(bundle)` →
  `startActivity`。链路全绿但屏幕不动 ——几何真进了 options（`androidx.flexible.LaunchBounds` /
  `flexible_window_mini_rect` / `ResizeMode` / `LaunchScale` / `LaunchCornerRadius` /
  `android:activity.mZoomLaunchFlags` 逐字核过），服务端不认这个调用方。
- **试错记录**（都不通，别再走）：`startMiniZoomFromZoom(1/2/7)`（无人调用的废接口）、
  `startZoomWindow(前台活动)`（返回 3 无效果，只肯收没在跑的组件）、
  `toggleFlexibleWindow("type_toggle_to_flexible_task" / "…pocket_studio")`（返回 true 无效果，要
  `com.oplus.permission.safe.WINDOW`，宿主持有）、 `startFlexibleWindowForRecentTasks(taskId, rect)`（走的是**分屏**）、
  `WindowContainerTransaction.setWindowingMode(token, 100)(+bounds)`（transaction 成功但窗口不变）。
- **手势那条路本身**（导航手势 → WM shell
  `StageCoordinatorExt`）应用侧够不着；但桌面 quickstep 用的是上面那条「带几何启动」，可复用。
- **task id 怎么取**：`OplusActivityManager#getAllTopAppInfos(): List<OplusAppInfo>`（要
  `oplus.permission.OPLUS_COMPONENT_SAFE`，宿主持有）→ 字段 `taskId` / `topActivity` / `windowMode` / `displayId` /
  `appBounds`。**判断「有没有真起成小窗」只能看 `windowingMode`**（`1` = 全屏，`100`
  = 小窗），返回码和截图都会骗人。小窗在场时这个列表里会同时出现「小窗那份（`100`）」与「全屏那份（`1`）」。
- **取前台活动**：`OplusActivityManager#getTopActivityComponentName(): ComponentName`（arity
  0，同一支类）。有小窗在场时它返回的就是小窗里那个应用 —— 语义是「最前面那个」，不是「全屏那个」。
- **同族的其它入口**（模块里没用，备查）：`hideZoomWindow(int)`（void）、 `toggleFlexibleWindow(String)` /
  `toggleFlexibleWindowByApp(IBinder,int,boolean,boolean)`（均 boolean）、
  `startFlexibleWindowForRecentTasks(int, Rect)`、`startActivityFromRecentsForFlexible(int, Bundle)`、
  `splitScreenForTopApp`。
- **侧键场景表里没有小窗档**（`action_button` 全 13 行核过），所以没有「官方侧键怎么起小窗」可抄。
- **模块侧**：`window:<包名>`（`Action.AppWindow`）→ `MiniWindowLauncher.launchAppInWindow`；`zoom:mini`
  （`Action.MiniWindow`）→ `MiniWindowLauncher.putForegroundInMiniWindow`（取前台包名后复用同一条）。两条都走同一个
  `startZoomWindow` + `zoomWindowBundle()`（`extra_window_mode = 100`）+
  `NEW_TASK | MULTIPLE_TASK`，只在返回**负数**时降级 —— `0` / `2` / `3`
  都不是失败标志。⏭️ 「把**现有**那扇窗原地收成小窗」应用侧仍然做不到：系统自己的「交换应用」走的是「小窗 ⇄ 全屏」两份任务互换，且**明确拒绝同一个包**（见 17.6.2），抄不过来。

#### 17.6.2 「交换应用」 = `notifyFlexibleTaskEvent(taskId, 2004)`（2026-10-14，真机实证可复现）

小窗菜单里那一项「交换应用」（前台 ⇄ 小窗互换），整条链已核到**应用侧可调用的那一层**：

```
小窗菜单点「交换应用」
  └─ com.oplus.flexibletask.menu.FlexibleMenuManager#lambda$initListener$2（SystemUI，classes3.dex）
       标题 == R.string.zoom_exchange  →  i2 = FlexibleUtils.FLEXIBLE_TASK_CHANGE_EVENT(= 2004)
       → notifySystemMenuEvent(2004, Bundle{notify_event_async = true})
         → FlexibleUtils.notifyFlexibleTaskEvent(taskId, 2004, bundle)
              └─ com.oplus.flexiblewindow.FlexibleWindowManager.getInstance()      ← 反射可调
                   .notifyFlexibleTaskEvent(int taskId, int event, Bundle)
                   └─ android.app.OplusActivityTaskManager#notifyFlexibleTaskEvent
                        → IOplusActivityTaskManager$Stub$Proxy（Binder）
                          └─ OplusActivityTaskManagerServiceEnhance#notifyFlexibleTaskEvent
                               ── 这一层**没有任何 enforceCallingPermission** ✅
                               └─ FlexibleWindowManagerService#notifyFlexibleTaskEvent
                                    └─ FlexibleTaskController#notifyFlexibleTaskEvent
                                         switch (event) case 2004 →
                                         handleFlexibleExChange(task, "fromMenu")
```

- **`com.oplus.flexiblewindow.FlexibleWindowManager` 定义在 `oplus-framework.jar`（boot
  classpath）**，不是 SystemUI 的私有类 —— 任何进程都 `Class.forName`
  得到（真机核过：`loader = BootClassLoader`）。它是「静态单例 + AIDL 封装」，字段
  `mOAtms = OplusActivityTaskManager.getInstance()` 就是那把 Binder。真机
  `FlexibleWindowManager.getInstance().notifyFlexibleTaskEvent(25447, 2002, bundle)` 调用无异常返回。
- **事件码表**（`com.oplus.flexibletask.utils.FlexibleUtils`，SystemUI `classes3.dex`）：

  | 常量                                               | 值          | 服务端动作                                             |
  | -------------------------------------------------- | ----------- | ------------------------------------------------------ |
  | `FLEXIBLE_TASK_FULL_EVENT`                         | 2001        | `ExitFlexibleTaskToFullStrategy`（小窗 → 全屏）        |
  | `FLEXIBLE_TASK_MINI_EVENT`                         | 2002        | `ExitFlexibleTaskToFloatStrategy`（→ 小窗）            |
  | `FLEXIBLE_TASK_CLOSE_EVENT`                        | 2003        | `ExitFlexibleTaskToBackStrategy`（关闭）               |
  | `FLEXIBLE_TASK_CHANGE_EVENT`                       | 2004        | `handleFlexibleExChange(task, "fromMenu")`（**交换**） |
  | `FLEXIBLE_TASK_MENU_SHOW_EVENT` / `…DISMISS_EVENT` | 2011 / 2012 | 菜单开合的状态同步                                     |

- **`handleFlexibleExChange` 的前置条件**（`FlexibleTaskController` 内逐条读过）：传进去的 `task` 是
  **小窗**那一份，`fullTask = getFullTaskUnderFlexibleTask(task)` 是它下面压着的**全屏**那一份。要求
  `fullTask != null`、**不是桌面**（`isActivityTypeHome` → 直接弹 toast 拒绝）、不是最小化 / canvas、支持小窗、并且
  **`fullTask.getBasePackageName() != task.getBasePackageName()`** （同包 → `fail while package is same` +
  toast）。全过之后：**全屏那份 → 小窗，小窗那份 → 全屏**。
- **调用方只有菜单这一条**：全 `oplus-services` + SystemUI 扫过，`handleFlexibleExChange` 只有 `case 2004`
  那一个调用点（`source = "fromMenu"`）；另一处 `SOURCE_FROM_MINI_DOUBLE_TAP`
  只是它的入参分支，没有第二条可达路径 —— 所以**没有「导航手势版交换」可抄**。
- **真机复现**（探针 `ev <taskId> <event>`，`app_process` + root）：

  | 步骤                                                              | 观察（`getAllTopAppInfos().windowingMode`）                           |
  | ----------------------------------------------------------------- | --------------------------------------------------------------------- |
  | 起点：`设置` 全屏 + `mark.via` 起成小窗                           | task `7` = `设置` `1`；task `25449` = `mark.via` **`100`**            |
  | `notifyFlexibleTaskEvent(25449, 2004, {notify_event_async:true})` | —                                                                     |
  | 截图结果                                                          | `mark.via` 铺满全屏，**`设置` 变成右上角的小窗** ✓ 与手点菜单完全一致 |

  服务端日志同步印证：`FlexibleTaskController: handleFlexibleExChange FlexibleExChange handle change Event Task{…mark.via.gp}`（这行打的是
  **fullTask**）→ `… will exchange to flexible` →
  `FlexibleTaskTransitionHandler: start exchange full to flexibleTask anim success`。

- **为什么「交换应用」抄不进
  `zoom:mini`**：它的语义是「两份**不同包**的任务互换」，既拿不到「把某一份现有窗口原地收成小窗」，也强制要求先有**另一个应用**的小窗在场（那本身就要一次
  `startZoomWindow`），换完还得把陪跑那个应用再关掉（对它发 2003）。为了这么一条路去挑陪跑应用，不如 17.6.1 那条「新起一份实例的小窗」直接。
- **顺带核过**：事件 2002 对**普通全屏任务无效** —— 对 `com.oplus.gesture` 的一份普通全屏任务发 2002，服务端日志只有
  `exitFlexibleTaskWindowInnerLocked exitTo:5` +
  `resetWhenEndExit … fwOptMode-> 0`，屏幕不动。它是「柔性任务」专用收缩，不是「任意任务 → 小窗」。
- **备查（模块里没用）**：同一支接口发 2003 / 2001 可以驱动**已存在的小窗**关闭 / 转全屏； `notifyFlexibleTaskEvent`
  这层无权限闸，是这族里唯一「随便谁都能调」的写接口。

#### 17.6.3 「把现有任务原地收成小窗」的正门：`startActivityFromRecentsForFlexible`（2026-10-14 真机实证）

17.6.1 那条配方的代价是**新起一份实例**（`MULTIPLE_TASK`
强制新任务）。要**原地收**（taskId 不变、界面状态全留、不用先按 Home），正门在 recents 那条：

```
android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int taskId, Bundle options)
  └─ OplusActivityTaskManagerServiceEnhance#startActivityFromRecentsForFlexible
       enforceCallingPermission("com.oplus.permission.safe.WINDOW")      ← 宿主持有 ✅
       SafeActivityOptions.fromBundle(options, callingPid, callingUid)
       → TaskSupervisor#startActivityFromRecents(...)
            └─ FlexibleTaskController#onStartActivityFromRecents(Task, ActivityOptions)
                 zoomLaunchFlags   = OplusZoomWindowUtil.getZoomLaunchFlag(options)   // 读 android:activity.mZoomLaunchFlags
                 pendingLaunchZoom = adjustZoomModeIfNeed(options, null, task)
                     ↑ isLaunchFromZoomMode(options)
                       = OplusZoomWindowUtil.getLaunchWindowingMode(options) == 100 || hasLaunchZoomFlag(options)
                 if (pendingLaunchZoom) startFreeFormFromRecent(task, options, zoomLaunchFlags)
                      fullToMini = (zoomLaunchFlags == 32)
                      → FlexibleWindowManagerService#toggleToFlexibleTask(task, record, options, info)
```

**options 里要放什么**（真机逐项试出来的）：

| key                                 | 值    | 作用                                                                                                                       |
| ----------------------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------- |
| `android.activity.windowingMode`    | `100` | **必须**。`ActivityOptions#fromBundle` 只认这个（**点号**）；`extra_window_mode` 那条下划线的 key 在这条路上服务端根本不读 |
| `android:activity.mZoomLaunchFlags` | `2`   | 决定**尺寸档**：`2` = **展开**的普通小窗；`32` = **收起**的最小化窗。见下面那张对照表                                      |
| `extra_window_mode`                 | `100` | 带上无害（只有 `startZoomWindow` 那条读它）                                                                                |

**真机对照**（`设置` / `mark.via` 都**在前台**，直接对前台那份任务调）：

| 步骤                                                         | 观察                                                                                                                                                                                                                                        |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 起点                                                         | task `#25503` = `com.android.settings`，`windowingMode = 1`                                                                                                                                                                                 |
| `startActivityFromRecentsForFlexible(25503, {上面三个 key})` | 返回 `2`（`START_TASK_TO_FRONT`）                                                                                                                                                                                                           |
| 服务端日志                                                   | `onStartActivityFromRecents … pendingLaunchZoom:true` → `startFreeFormFromRecent current pendingLaunchZoom will toggle FlexibleTask` → `toggleToFlexibleTask task:Task{#25503 …}` → `relayoutCaptionView: reason = startFreeFormFromRecent` |
| 复核 `getAllTopAppInfos()`                                   | **还是 task `#25503`，`windowingMode` 由 `1` 变 `100`** ✅                                                                                                                                                                                  |

- **taskId 不变 ⇒ 是同一份任务被原地改了窗口模式**：没有第二份实例、界面状态（滚动位置 / 输入内容 / 返回栈）全保留，也不需要先回桌面。第三方应用（`mark.via`，task
  `#25505`）同样通过。
- **只给 `mZoomLaunchFlags`、不给窗口模式不行**：那次 `pendingLaunchZoom:false`，钩子跑完什么都没做（`windowingMode`
  仍是 `1`）—— 判据是 `getLaunchWindowingMode() == 100`，不是 flags。
- **`mZoomLaunchFlags` 决定的是「展开 / 收起」哪一档**，给错了就会开成右上角那条最小化窗：

  | `mZoomLaunchFlags` | 服务端                                                           | `windowType` | `zoomRect` / `scale`                   | 观感                              |
  | ------------------ | ---------------------------------------------------------------- | ------------ | -------------------------------------- | --------------------------------- |
  | `2`                | `setUserPositionInfo`（沿用用户上次的位置与尺寸）                | `1`          | `Rect(126,319 - 1146,2131)` / `0.8017` | **展开**的普通小窗（居中大面积）✓ |
  | `32`               | `fullToMini = zoomLaunchFlags == 32` → `getMiniFlexibleTaskInfo` | `2`          | `Rect(898,168 - 1203,710)` / `0.2397`  | **收起**的最小化窗（右上角一条）  |

  判「展开还是收起」最省事的是 `OplusZoomWindowInfo#windowType`（`1` = 展开，`2` = 收起），取值走
  `OplusActivityManager#getCurrentZoomWindowState()`，或直接看 `notifyZoomStateChanged` 那行日志。

- **和 17.6.2 的「交换应用」比**：那条要求「另有一份**不同包**的小窗在场」，服务端还明确拒绝同包；这条只要一个 taskId，同包完全没问题。两者都能动现有任务，这条干净得多。
- **模块侧用的就是这一条**（`zoom:mini`）：一步到位、原地收、状态全留。它没有窗口动画，见 17.6.4。

##### 附：「伪造应用不在前台」为什么不必做

服务端**确实**有一处前台判定：`OplusZoomNewTaskHelper#isAppAtTop(atms, intent, userId)`，判据是「目标包是不是 top root
task 的 root activity 的包」；它下面紧跟着 `resetActivityOption(activityOptions)`，会把 `setLaunchWindowingMode(0)` /
`setZoomLaunchFlags(-1)` —— 这就是「应用在前台时起不成小窗」的由来。

**但它在真机上是死代码**：`OplusZoomNewTaskHelper#startZoomWindowForNewTaskIfNeed` 第一句就是
`if (FlexibleWindowManagerService.isSupportFlexibleFreeform()) return;`，而
`isSupportFlexibleFreeform() = mIsSupportFlexibleFreeform && !mIsDisableCustomZoomWindow && isZoomSwitchOpened()`，其中
`mIsSupportFlexibleFreeform` 取自特性 `oplus.software.flexible_freeform.support` ——该特性在本机
`/my_stock/etc/extension/com.oplus.oplus-feature.xml` 里**是声明了的**。真机佐证：`com.android.settings` 在前台跑
`startZoomWindow` 时，`OplusZoomNewTaskHelper` 那几行 `Slog.v` 一行都没出现（同一个 logcat 窗口里
`FlexibleTaskController` 的 D 级日志正常在）。

所以在这台 ROM 上，**伪造「不在前台」不会改变任何东西**；真正卡住的是 `ActivityStarter`
的「目标任务是不是当前 top」以及「窗口模式选项只在建任务时生效」这两件事 ——绕开它们不是靠伪装，而是走上面那条 recents 正门（服务端拿到 taskId 后走的是
`toggleToFlexibleTask`，跟「是不是前台」无关）。

#### 17.6.4 「原地收 + 有转场动画」的四条路对比（2026-10-14 真机实测）

17.6.3 那条 recents 正门能原地收，但**没有窗口动画**
—— 按下侧键那一瞬窗户直接跳成小窗。把四条路摆一起量过：判据是 logcat 里 `animation-leash`
的条数（真有窗户动画才会刷它，伴随 `SurfaceAnimationRunner: onAnimationStart`），再加 `getAllTopAppInfos()`
里 taskId 有没有变。

| 变体                                                                             | 原地（taskId 不变） | `animation-leash` |
| -------------------------------------------------------------------------------- | ------------------- | ----------------- |
| `startZoomWindow` + `NEW_TASK \| MULTIPLE_TASK`                                  | ✗ 另起一份实例      | 2                 |
| `startActivityFromRecentsForFlexible(taskId, {windowingMode=100, flags=2})`      | ✓                   | **0**             |
| 同上但 `flags=32`（`fullToMini` 档）                                             | ✓                   | **0**             |
| **挪出栈顶 → `startZoomWindow(入口, {extra_window_mode=100})`（只 `NEW_TASK`）** | **✓**               | **2**             |

- **为什么 recents 那条没有动画**：`onStartActivityFromRecents` 走的是 `toggleToFlexibleTask`
  —— 直接改任务的窗口模式与边界，没有「启动」这个动作，也就没有可播的窗户动画（那条路上 SystemUI 的
  `FlexibleTaskTransitionHandler` 只把 transition 收掉，不挂 leash）。
- **挪出栈顶那条为什么有**：它是货真价实的 `ActivityStarter.execute()`
  启动，窗口模式随 options 落下去，于是走标准窗口动画。目标还在栈顶时这条落不下（见 17.6.1），所以先发一个 CATEGORY_HOME 的 intent；服务端
  `startFlexibleTask` 自己会「等 HomeTransitionFinish」，**调用方不用 sleep**，紧接着调就行。
- **只加 `NEW_TASK`，别加 `MULTIPLE_TASK`**：后者会强制新建任务，那就不是原地收了（回到 17.6.1 的代价）。
- **模块侧选的是 recents 那条（无动画）**：`zoom:mini`
  要的是一步到位 —— 挪出栈顶那条虽然既原地又有动画，但要多回桌面一次，权衡后**否掉**了。起完
  `MINI_VERIFY_MS`（当时 350ms； **2026-10-14 改成 700ms**，因为过渡帧约 600ms 才落定，见附十三）再核那份任务的
  `windowingMode` 是不是
  `100`，不是才退回 17.6.1 那条「按包名另起一份实例」。代价：会先回桌面一瞬（服务端要等桌面过渡铺开），换来的是**原地 + 动画**。

#### 17.6.5 `window:` 的「多开实例」与修法（2026-10-14 真机实证）

17.6.1 的配方带着 `MULTIPLE_TASK`，副作用是**同一个应用被按几次就多出几份实例**（`window:`
是「打开应用小窗」，对同一个目标反复按很容易触发）。真机量过：对 `com.android.settings` 连按三次，每按一次就多一份
`windowingMode=100` 的小窗。

**根因是 `MULTIPLE_TASK` 强制新建任务**，而它当初是给「目标已在前台」兜底的 —— 那个场景现在归 `zoom:mini` （17.6.3 /
17.6.4），`window:` 不再需要它。

去掉之后（只加 `FLAG_ACTIVITY_NEW_TASK`），服务端的行为正好是想要的：

| 目标当时的状态           | 服务端行为                                                        | 实例数          |
| ------------------------ | ----------------------------------------------------------------- | --------------- |
| 还没跑过                 | 新起一份、落在小窗（`windowingMode=100`）                         | 1（本来就该有） |
| 有现成任务（在前台之外） | **复用那份任务**，`windowingMode` 1 → 100，taskId 不变            | **不变** ✓      |
| 已是小窗                 | 再按返回 `0`/`2`，没有第三份                                      | 不变 ✓          |
| 就在前台                 | 会被栈顶那份实例消化（返 `3`）→ 改走 17.6.3 的 recents 正门原地收 | 不变 ✓          |

- **真机实证**（`设置`）：task `#25535` 全屏 → 回桌面 → 按 `window:` 那套起 → **还是 `#25535`**， `windowingMode` 由 `1`
  变 `100`；连按三次之后 dump 只剩这一份小窗 + 桌面，没有累积。
- **模块侧**：`launchAppInWindow` 改成 ① 目标就在前台 → 直接走 17.6.3 那条原地收（`foregroundTaskFor` 判
  `getAllTopAppInfos()[0]` 是不是它）；② 其余只加 `NEW_TASK` 起 `startZoomWindow`；③ 反射路不通才退自由窗口 / 普通打开。
  `MULTIPLE_TASK`
  整个删掉。原来「返 3 就回桌面再起一次」的兜底保留，但只在 ① 没接住时当保险（它本身也是原地转，不新建）。
- 判断「有没有真成小窗」照旧只看 `windowingMode`（`1` = 全屏，`100` = 小窗）。

##### 附二：为什么 in-place 那族**结构上不可能**有动画（2026-10-14 定论）

不是「参数没给对」，是过渡本身被丢掉了。in-place 那族（`startActivityFromRecentsForFlexible` 与公开的
`ActivityStarter#startActivityFromRecents`）是在 binder 调用里**同步**把窗口模式改完的；等框架为这次调用建立过渡时，任务表面已经在新位置了，过渡里**一个可动画的 root 都没有**：

```
WindowManagerShell: No transition roots in (#127) android.os.BinderProxy@… so abort
FlexibleTaskTransitionHandler:  onTransitionConsumed transition:… ,aborted:true
```

实测（`animation-leash` 计数，判据同 17.6.4）：in-place 那条**前台**跑 `leash=0`，**把任务放后台再跑也是 `leash=0`**
—— 与任务在不在前台无关，它就没有过渡可播。

动画只可能来自**一次带 root 的真启动**：`startZoomWindow` →
`ActivityStarter.execute()`，窗口模式随 options 落下去，任务表面要移动 ⇒ 过渡里有 root
⇒ 标准窗口动画开跑（`leash=2`）。注意这条动画**不是**柔性任务那支动画 —— SystemUI 的 `FlexibleTaskTransitionHandler`
对它是 `no need handler this transitionInfo`（`consumedByThisHandler` 返回 false），跑的是普通应用开合动画。

##### 附三：动画入口全表（找过一圈，2026-10-14）

| 入口                                                                                                                       | 原地 | 动画                                  | 卡在哪                                                                                                      |
| -------------------------------------------------------------------------------------------------------------------------- | ---- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `startZoomWindow`（真启动）                                                                                                | ✗    | ✓（`leash=2`，标准窗口动画）          | 目标在栈顶时启动被吞 / 窗口模式落不下，必须先挪出栈顶                                                       |
| `startActivityFromRecentsForFlexible(taskId, opts)`                                                                        | ✓    | ✗                                     | 过渡无 root → abort（附二）                                                                                 |
| 公开 `ATS#startActivityFromRecents(taskId, opts)`                                                                          | ✓    | ✗                                     | 同上                                                                                                        |
| `notifyFlexibleTaskEvent(taskId, 2002)`                                                                                    | ✓    | —                                     | 只对**已经是柔性**的任务（普通全屏任务实测只打 `exitTo:5` 就结束）                                          |
| `toggleFlexibleWindow(toType)` / `OplusFlexibleAnimationRunner`                                                            | —    | ✓                                     | 只对**已存在的柔性任务**：`ToggleTaskRunnable` 取的是 `embeddedTasks`，没有柔性任务时返回 true 但什么都不做 |
| `notifyFlexibleTaskEvent(taskId, 2004)`「交换」                                                                            | ✓    | ✓ `performExchangeFullToFlexibleAnim` | 要求另有一份**不同包**的小窗在场，且服务端明确拒绝同包                                                      |
| 拖拽手势（`OplusZoomWindowManager` 的 `onInputEvent` / `initializeDragZoomAnimation` → `finishDragAnimationAndStartZoom`） | ✓    | ✓                                     | 只能靠喂输入事件驱动；调用方是桌面 / SystemUI 的输入接收器，应用侧没有一步到位的调法                        |

- **那支动画是按「过渡类型」选的，不是按调用方**：`FullscreenTaskOperations#handleFullscreenToFreeformAnimation( IBinder transition, TransitionInfo info, Transaction t, Transaction finishT, TransitionFinishedCallback cb)`
  —— 签名里就带着过渡与 finish 回调，**只能在一次真实过渡里跑**，没法对着某个任务单独调用。选型在
  `FullscreenTaskOperations$TransitionHandler`：`transitionInfo.getType() == 1106` 时才走这支（`1107`
  = 交换）。我们的 in-place 那条产生的是普通 `TO_FRONT`，压根不是 1106。
- **推论**：想有动画，得让这次变更落进一次**类型正确的过渡**里。应用侧造不出这种过渡（见附二）；能造的只有框架自己（真启动）或 shell 侧（桌面 /
  SystemUI）。

##### 附四：框架自己那条 full→mini 动画的正门（2026-10-14）

原生那条动画的实现是 **`com.android.server.wm.FlexibleTaskController#switchState(Task, int nextState, String reason)`**
（`public`）→
`com.android.server.wm.FlexibleWindowAnimator#startFlexibleTaskToMiniAnimations( ArrayMap<Task, FlexibleAnimationInfo>, Handler surfaceAnimHandler, SurfaceControl.Transaction, callback, reason)`。
`nextState`：`1` = 小窗、`2` = 全屏；每个任务配一个
`FlexibleAnimationInfo`（startRect/finishRect/startScale/finishScale/
alpha/时长/插值器/leash…，**构造器是 public**）。`switchState(task, 1, …)` 就是「全屏 → 小窗」那一档。

`switchState(…, 1, …)` 全 ROM 只有三个调用点，逐条核过判据：

| 调用点                                                                 | 判据                                                                                                                                                        | 应用侧能不能满足                                                                                                                                                                     |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `onStartActivityFromRecents`（reason `onStartActivityFromRecents`）    | `zoomLaunchFlags == 2` **且** `isFlexibleTaskAndHasCaption(task)` **且** `task.getFlexibleZoomState() == 2`（柔性任务、处于**全屏态**）                     | 实测：普通全屏任务走 recents 正门只打 `pendingLaunchZoom:true` 就转 `startFreeFormFromRecent`，**不命中**，`leash=0`；`flags=3` 也造不出「柔性 + 全屏态」（结果仍是 `state=1` 小窗） |
| `getReusableOptionsForFreeForm`（reason `StartMiniFromLauncher`）      | `isStartFromLauncher()` = `startActivity.isLaunchSourceType(2) && mAtms.mHomeProcess.mPid == request.realCallingPid` —— **调用方 pid 必须等于桌面进程 pid** | ✗：这个 pid 是服务端在 `Binder.clearCallingIdentity()` **之前**取的**真实**调用方 pid，参数里伪造不了。要满足只能**从桌面进程里发起**（= 注入桌面）                                  |
| `switchState(task, 2/1, "STATE_CHANGE_TO_MINI"/"STATE_CHANG_TO_ZOOM")` | 内部状态机                                                                                                                                                  | ✗                                                                                                                                                                                    |

- **可复用的原语都已确认可反射**：`FlexibleWindowManagerService.getInstance(null)`（框架自己就到处这么传 null）→
  `.getFlexibleTaskController()` → `switchState(...)`；`Task` 由
  `ActivityTaskManagerService#mRootWindowContainer#anyTaskForId` 拿；动画器与 `FlexibleAnimationInfo` 同上。
- **模块本来就注入 system_server**（侧键接管那一侧），所以上面这些**在模块侧够得着**
  —— 这是最省的「一比一」：用框架自己的动画器，而不是自己拿 leash 画。
- ⚠️ 代价：这是**注入侧**改动，一旦抛异常就是系统进程崩溃 / 开机循环，必须整包 `try/catch` 只降级；且换包后要
  **整机重启**才生效（软重启会把框架停坏，见六之四）。

##### 附五：`flexibleZoomState` 的实测取值（2026-10-14，模块经 system_server 通道读出）

为这条通道新建了注入侧执行器 `inject/system/FlexibleWindowBridge.kt`（与 `StatusBarBridge`
同款：宿主发广播、注入侧在 AMS 广播入口接住、本机执行），把一直读不到的 `TaskExtImpl#getFlexibleZoomState()` 读出来了：

- **普通全屏任务（前台）**：`flexibleZoomState = 0`，`bounds = Rect(0,0-1272,2772)`；此时 `switchState(task, 1, …)`
  第一句 `isFlexibleTaskAndHasCaption` 不满足，直接返回 —— 不会播动画。
- **recents 正门转完之后**：同一任务 `flexibleTaskState = 1`（小窗）、`isFlexibleWindow = true`。
- `flexibleZoomState` 的取值语义：`0` = 非柔性任务、`1` = 柔性任务在小窗态、`2` = 柔性任务最大化到全屏。

所以原生动画要的 `state == 2`，从 `0` 出发**必须经过
`1`**（先收成小窗、再最大化到全屏）—— 一次可见的中间态，应用侧跳不过去。这也把「手工复刻」的形态定死了：**不能**走
`switchState`，只能自己构造 `FlexibleAnimationInfo`（start = 全屏 rect/scale 1.0，finish
= 小窗 rect/scale）+ 拿任务的快照 leash，直接调 `FlexibleWindowAnimator#startFlexibleTaskToMiniAnimations`
—— 动画器是框架的，配方是自己的。原语全部 public、全部在 system_server（模块已注入），但快照捕获（`TaskSnapshotController`）与 leash 构建需要另写，是一个 200+ 行、每轮调参都要整机重启的子系统。

- **踩过的坑（记下）**：`Reflect.call(receiver, name, args)` 按**参数运行时类**找方法 —— Kotlin 的 `Int` 会被装箱成
  `Integer`，而 `anyTaskForId(int,int)` / `switchState(Task,int,String)` 的签名是原始类型， `getDeclaredMethod`
  匹配不上且**静默返回 null**。对原始类型参数必须走 `Reflect.method(cls, name, Integer.TYPE, …)` 拿到 `Method` 再调。
  **结论：应用侧不存在「一步到位 + 带动画」的全屏→小窗入口。**
  有动画的那条必须让任务先离开栈顶；一步到位的那条结构上就没有过渡可播。

##### 附六：伪造态路线的两个落点缺陷（「太长」/「没有操作条」，2026-10-14 修正）

伪造态 + `switchState` 跑通后剩两个缺陷，逐条挖到根：

1. **落点太长**：`switchState` 收缩的 finishRect 取自 `TaskExtImpl#getRememberPosInfo()` （`getAnimationInfo(task, 1)`
   里 `isRememberInfoValid`
   不成立时落到「按应用比例算默认」分支，用**全屏宽高比**缩放 → 窗口被拉长）。修法：`switchState` 前调
   `ext.setRememberPosInfo(bounds, scale)` 预置落点。注意 `setRememberPosInfo(Rect, float)` 含原始类型参数，必须显式
   `getDeclaredMethod`（装箱坑见附五）。
   - ⚠️ **预置值不能取
     `controller.getMiniFlexibleTaskInfo(task, record)`**（2026-10-14 真机否掉）：它返回的是**收起档**几何（`Rect(898,168-1203,710)`
     / `0.2397`，同 17.6.3 尺寸档表 `flags=32`
     那一行），喂进去就把「展开」的动画落到了收起的位置 —— 真机表现是**「按一下直接变成右上角那条最小化窗」**。
     **展开档要用 `setUserPositionInfo` 那条路的值**（17.6.3 表里 `flags=2` 那一行）。⚠️ 但**别照抄它的屏幕矩形**：喂给
     `setRememberPosInfo` 的必须是**配置矩形** `Rect(126,319-1398,2580)`
     —— 两者差一个 scale，抄错就「小一圈」，**见附七**。本机现在就是这么写死的（`FlexibleWindowBridge.EXPANDED_BOUNDS`）。⚠️ 这组数是 PLK110（1272×2772
     @560dpi）实测值，与机型允许表绑定，加机型要重测。
   - 📌 **订正**：本小节 2026-10-15 那版把上面这两个值错记成了「`getMiniFlexibleTaskInfo`
     的真机标准值」，两个来源被混为一谈 —— 那正是这次「落点变收起」的根因。现已按真机结果分开。

2. **没有操作条**：操作条视图由 `TaskExtImpl#updateFlexibleTaskParams` **内部**负责（`mHasCaption` 分支 →
   `FlexibleWindowManagerService#createFlexibleCaptionView(task, captionName)` → `Class.forName(captionName)` 反射建
   `FlexibleTaskCaptionView(Context, FlexibleTaskController, Task, ATMS)` → `initOrUpdateFlexibleCaptionView`
   挂到任务上）。**captionName 不是任意字符串，是视图实现类的全名**，来自策略配置
   `FlexibleWindowManagerService#getCaptionName(scenario)`（mini 的 scenario=1）。我们喂的 bundle 里没放
   `androidx.activity.CaptionName` → `mCaptionName=null` → `Class.forName(null)` 抛异常被吞 → 视图建不出来。修法：反射
   `getCaptionName(1)` 拿到真名（本机为 `com.android.server.wm.FlexibleTaskCaptionView`）塞进 bundle 的
   `androidx.activity.CaptionName`。

##### 附七：`setRememberPosInfo` 的 `Rect` 是**配置矩形**，不是屏幕矩形（2026-10-14 真机定论，PLK110）

**现象**：附六那两个缺陷都修完之后，`zoom:mini`
打出来的**是小窗、也不是收起档**，但就是「比 ColorOS 原生的小一圈」，用户一眼判「还是不对」。

**取证**（可复用，别靠肉眼）：同一次按键，两条路各取一个数 ——

| 来源                 | 手段                                            | 结果                                                                  |
| -------------------- | ----------------------------------------------- | --------------------------------------------------------------------- |
| 模块产出             | 注入侧日志                                      | 预置 `Rect(126,319-1146,2131)` / `0.8017`                             |
| 模块产出             | 截图逐列扫亮像素（脚本 `.temp/measure_win.py`） | 窗口屏幕框 `(126,319)-(942,1770)` = **817 × 1452**，宽占比 **0.6423** |
| 宿主判定             | `OplusAppInfo.appBounds` 宽 ÷ 屏宽              | **0.8019**（于是打了「已是展开档」的假绿灯）                          |
| 用户手调出的正确形态 | 截图同上                                        | `(126,319)-(1145,2130)` = **1020 × 1812**，宽占比 **0.8019**          |
| 用户手调出的正确形态 | `dumpsys activity activities` → `Task.mBounds`  | `Rect(126,319 - 1398,2580)` = **1272 × 2261**                         |

**根因**：`setRememberPosInfo(Rect, float)` 的 `Rect` 是**应用配置矩形**，系统还会把它的尺寸 **再乘一次 `scale`**
才是屏幕上的实际大小：

```
屏幕尺寸 = Rect.size × scale
```

我当时把**屏幕**矩形 `1020 × 1812` 直接喂了进去，于是又被缩了一次： `1020 × 0.8017 = 817.6` → 真机读到的 `817`；高度
`1812 × 0.8017 = 1452.7` → 读到的 `1452`。 **两个轴都严丝合缝，比值同为 1.2485 = 1/0.8017**，不是巧合。而
`Rect.left/top` 是**直接照搬到屏幕坐标**的、不含缩放 —— 所以两头左上角都是
`(126,319)`：这也是「只有尺寸错、位置没错」的原因。

**正确值**（本机型实测，`FlexibleWindowBridge.EXPANDED_BOUNDS`）：

|        | 配置矩形（喂 API 的）                     | scale    | 屏幕落点（验收看的）                      |
| ------ | ----------------------------------------- | -------- | ----------------------------------------- |
| 展开档 | `Rect(126,319 - 1398,2580)` = 1272 × 2261 | `0.8017` | `Rect(126,319 - 1145,2131)` = 1020 × 1812 |
| 收起档 | 同左（系统自算）                          | `0.2397` | `Rect(898,168 - 1203,710)` = 305 × 542    |

> 口径说明：上表用**半开区间**（`right/bottom` 不含）。逐像素扫截图得到的是**闭区间**，同一个窗口记作
> `(126,319)-(1145,2130)`，两者等价、尺寸都是 `1020 × 1812`，别当成差 1px 的异常。模块日志里 `screenBoundsOf`
> 打的是半开形式，所以判据字符串应是 `Rect(126,319 - 1145,2131)`。

**由此得到两个可复用结论**：

1. **小窗的逻辑尺寸恒为 `1272 × 2261`，两档共用**，只有 scale 不同。交叉验证：收起档 `542 ÷ 0.2397 = 2261`、展开档
   `1812 ÷ 0.8017 = 2260.4` —— 同一个 2261。所以**配置矩形分不出档位**（两档都是 1272 宽），拿 `appBounds`
   当档位判据必然是假绿灯。
2. **两个 API 的 Rect 语义相反**，这是最容易踩的地方：
   - `getMiniFlexibleTaskInfo` 返回的是**屏幕矩形**（`Rect(898,168-1203,710)`，305 宽）；
   - `setRememberPosInfo` 要的是**配置矩形**（1272 宽）。
   - 一进一出正好差一个 scale，直接互喂就会「小一圈 / 大一圈」。

> 📌 **订正**：附六里写的「展开档 `Rect(126,319-1146,2131)` / `0.8017`」是**屏幕值**，那是**验收口径**，不是能喂给
> `setRememberPosInfo` 的参数 —— 直接照抄进代码就会得到上面那个「小一圈」。喂 API 请用本节的配置矩形
> `Rect(126,319-1398,2580)`。

##### 附八：伪造态小窗是**半成品** —— 缺「柔性任务注册」那一步（2026-10-14 真机，PLK110）

附七把几何修对之后，真机（重启后）判据全绿：

```
小窗指令：已预置展开档几何（宿主下发）配置矩形=Rect(126, 319 - 1398, 2580) scale=0.8017 → 屏幕落点应为 Rect(126, 319 - 1145, 2131)
小窗指令：caption 实现类 = com.android.server.wm.FlexibleTaskCaptionView
小窗指令：已喂 updateFlexibleTaskParams（含 HasCaption=true）
小窗指令：冒充柔性全屏态成功（mScenario=1 / mHasCaption=true / mFlexibleZoomState=2）
小窗指令：switchState(#71, 1) 已调用
宿主侧：任务 #71 已是小窗、预置几何被吃住（配置宽占比 1.0）
```

**几何确实对了**（`Requested w=1272 h=2261`、屏幕落点
`1020 × 1812`），**但窗口不能用**。用户原话：「点击被视为拖动，也没有菜单和操作条和圆角」。逐条坐实：

| 症状              | 真机所见                                                              |
| ----------------- | --------------------------------------------------------------------- |
| 没圆角            | 窗口是**直角**矩形，没有柔性窗那种大圆角                              |
| 没操作条 / 没菜单 | 顶部那行 `●●●` 操作条与 `⋮` 菜单**完全不存在**                        |
| 输入被吃掉        | **点一下会被当成拖动**，整个窗口跟着手走                              |
| 内容叠两份        | 微信标题栏 `微信(28) 🔍 ⊕` 在窗口顶部**出现两次**（纵向相差约 160px） |

**排查过程（可复用的两条）**：

1. **窗口列表对比**：拿「用户手调出的正确形态」与「伪造态」两次 `dumpsys window windows` 逐行列， **窗口集合完全同构**
   —— 都没多出操作条窗口。⇒ 操作条**不是独立窗口**，是挂在应用窗口内部的；所以「找不到操作条窗口」不等于「操作条没建」，别顺着这条路找。
2. **`windowType` 不能当档位判据**：`dumpsys activity activities` 里 ActivityRecord 的 `windowType`
   在展开档、伪造态、**连桌面**上都是 `2`。⚠️ 这和附六/`zoom-window.md` §五表里那个
   `OplusZoomWindowInfo.windowType`（1=展开/2=收起） **不是同一个字段** —— 那个是 `OplusZoomWindowInfo`
   上的，这两个名字撞车了，别再混用。

**根因（推断，四条症状同一指向）**：**框架的「柔性任务注册」那一步没做**。操作条视图、圆角、拖拽与输入处理都是那一步配上去的；伪造态只捅了三个状态字段（`mScenario`
/ `mHasCaption` / `mFlexibleZoomState`）+ `switchState`，**状态位对了、外围那层壳子没人装**
——于是几何/档位都对，窗口却是个「只有正确大小与位置的裸窗口」。

**结论与处置**：暂时**不用**伪造态这条开窗，改走 **recents 正门** （`startActivityFromRecentsForFlexible` +
`mZoomLaunchFlags = 2`，§17.6.3，真机实证过的展开档）——它走的是框架自己的柔性窗启动链路，操作条 / 圆角 / 输入都有人配。代价是**没有原生动画**。伪造态那条留在代码里、由宿主侧开关
`MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE` 控制（默认 `false`），哪天把注册那步补上再打开。

**正门真机验证（同日 17:44，未重启整机）**：`kill` 宿主进程后用假手势触发（见 `zoom-window.md` §11.1），日志
`走 recents 正门` → `从 recents 把任务 #72 原地收成小窗（flags=2，返回 2）`，四个症状**全好**：

| 症状            | 正门开出来                                                                             |
| --------------- | -------------------------------------------------------------------------------------- |
| 没圆角          | ✅ 大圆角                                                                              |
| 没操作条 / 菜单 | ✅ 顶部 `●●●`；点它弹出原生菜单（全屏 / 分屏 / 最小化 / 关闭 / 交换应用 / 透明度调节） |
| 点击被当成拖动  | ✅ `input tap` 打操作条：菜单弹出、**窗口 frame 未变**（始终 `[126,319][1398,2580]`）  |
| 内容叠两份      | ✅ 只有一份                                                                            |

几何：截图量出 `(126,319)-(1144,2130)` = 1019 × 1812（目标 1020 × 1812，差 1px 是扫描阈值）。⇒
**「窗口是半成品」确实是伪造态独有的问题，不是设备 / 应用的问题。**

> ⚠️ 正门的落点**不是** `EXPANDED_CONFIG_BOUNDS` 给的，而是 `setUserPositionInfo` 沿用
> **系统记住的位置**（「用户上次的落点」）。那两个常量现在只对伪造态那条有效。

> 待办：要拿回动画，就得找到那条「柔性任务注册」的入口（候选：`FlexibleWindowManagerService` 上那条 `launchFlexibleTask`
> 一类的方法，或 `FlexibleTaskController` 里建完 caption 之后还要调的那一步）。§17.6.4 附三「动画入口全表」可作为起点。

##### 附九：「前台 → 小窗」的过渡为什么 abort；壳子其实是**过渡**装的（2026-10-14 三组实测对照）

附八把「伪造态是半成品」的根因推成了「柔性任务注册那步没做」。**本节修正这个推断**：壳子（圆角 + 操作条）是那条**窗口过渡**装的，所以更可能只是「过渡 abort 的后果」。

**三组对照**（同机型、同一天，逐行比 `logcat`）：

|                          | 目标任务状态               | `onStartActivityFromRecents`               | 多出来的一步                                   | 过渡结果                                                                     |
| ------------------------ | -------------------------- | ------------------------------------------ | ---------------------------------------------- | ---------------------------------------------------------------------------- |
| 桌面「**近期任务浮窗**」 | 在最近任务里（**非前台**） | `isFlexibleTask:false` `zoomLaunchFlags:2` | **`skipCurrentOrAdjustChange … launchFrom=2`** | 建出 leash → `FlexibleTaskTransitionHandler` → **`MultiSpringAnimation`** ✅ |
| 模块 recents 正门        | 普通全屏、**前台可见**     | `isFlexibleTask:false` `zoomLaunchFlags:2` | 无                                             | `No transition roots in (#62) … so abort` ❌                                 |
| 模块 recents 正门        | 已是小窗、不可见（脏状态） | `isFlexibleTask:true` `zoomLaunchFlags:2`  | 无                                             | 同上 abort ❌                                                                |

三点结论：

1. **`zoomLaunchFlags=2` 两边一模一样** ⇒ 不是参数问题；**调用方身份**（桌面 vs 模块宿主）才是变量。桌面那次比我们多一条
   `skipCurrentOrAdjustChange … launchFrom=2`，而 `launchFrom` 很可能就是 `ActivityRecord#LaunchSourceType`
   的取值（`isLaunchSourceType` / `determineLaunchSourceType` / `LaunchSourceType` 都在 **`services.jar` 的
   `classes4.dex`** 里，不在前两个 dex）。
2. **壳子是过渡装的**，不是「注册」装的 —— 桌面成功那次有这两句，都只在真过渡里跑：
   ```
   FlexibleTaskTransitionController: adjustSurfaceForTransitionIfNeed type=3, cornerRadius=91, scale=0.8016529
   FlexibleTaskTransitionController: finishTransition will update task's caption visible when open transit.
   ```
   ⚠️ 我们 abort 那次 `finishTransition` 也打了第二句，但那是在 `aborted:true` **之后**跑的，没救回来。
3. **前台任务确实没有过渡 root**，与附二一致 —— 这不是测试脏状态造成的，清干净后复测仍然 abort。

**两条可复用的查法**：

- 桌面那个入口菜单项叫「**近期任务浮窗**」。按「小窗」在桌面 dex 里**一条都搜不到**
  ——找 OEM 入口时别只搜一个词，先把 UI 上看来的原词记下来再搜。
- 圆角 / 操作条的**归属**可以直接从日志认：谁打的 `adjustSurfaceForTransitionIfNeed` /
  `caption visible`，就是谁装的。别从「有没有对应窗口」倒推（附八那条弯路）。

**由此得到两条待试的路**（都还没做）：

- **A · 从桌面进程发起**：赌 `launchFrom=2`
  会让框架**把窗口模式变更延后进过渡**，于是前台任务也有 root。⚠️ 风险：root 也可能只是因为「桌面的画面在那儿、有东西可以从它动画出去」；若是这样，A 对前台场景不成立。
- **C · 保留 `switchState` 的动画、自己装壳**：圆角用 `SurfaceControl.Transaction#setCornerRadius`
  （**公开 API**），操作条用 `FlexibleWindowManagerService#createFlexibleCaptionView(task, captionName)`
  （§7.5 已查到，`captionName` 也已反射拿到）。不赌框架分支，且"点击被当拖动"很可能随操作条一起好 ——拖拽手柄就是操作条。

##### 附十：C 的前半已成立（圆角），后半卡在**线程**上（2026-10-14 真机，两轮）

靠 debug 通道（`docs/debug-channel.md`）在真机上试的，**没有为试错编译过**：

- **操作条视图本来就已经建出来了** —— 不是「注册没做」： `TaskExtImpl.getFlexibleCaptionView()` 返回
  `FlexibleCaptionView{taskId=N}`，`mHasCaption=true`、
  `mCaptionName=com.android.server.wm.FlexibleTaskCaptionView`。所以缺的是**挂载 / 显示**，不是创建。
- **真签名（通道 `dump` 出来的，不用再猜）**：
  `FlexibleWindowManagerService.createFlexibleCaptionView(Task, String) : FlexibleCaptionView`、
  `FlexibleCaptionView.initCaptionViewAndAttachToTask(Task, Rect)`、`initCaptionView()`、 `showCaptionViewWithAnim()` /
  `hideCaptionViewWithAnim()`、`updateFlexibleTaskCaptionVisible(Task, boolean)`、
  `TaskExtImpl.getFlexibleCaptionView() / setFlexibleCaptionView(FlexibleCaptionView)`。
- **圆角：成功**。`SurfaceControl` 取自 `WindowContainer#getSurfaceControl()`（0 参那个候选命中），
  `Transaction#setCornerRadius(sc, 91)` + `apply()` 之后**截图四角肉眼可见圆角**（补壳前是直角）。
- ⚠️ **操作条：卡在调用线程**。 `initCaptionView()` 与 `initCaptionViewAndAttachToTask(Task, Rect)` 都抛：
  ```
  android.view.ViewRootImpl$CalledFromWrongThreadException:
    Only the original thread that created a view hierarchy can touch its views.
    Expected: android.ui     Calling: binder:NNNN_1
  ```
  即这两个方法**必须在 `android.ui`
  线程上调**，而我们是从 AMS 广播入口（binder 线程）调的。旁证：`updateFlexibleTaskCaptionVisible(task, true)`
  **不抛**（它内部应该是自己 post 到对线程的），调完 `mCaptionViewSurfaceShow` 读出来是
  `true`，但**截图上操作条仍然没显示**。
- ⚠️ 两处**反射诊断本身的坑**（都踩过、已修）：
  1. `Method.invoke` 抛的是 `InvocationTargetException`，它的 **message 恒为 null** ——只打 `e.message`
     会得到「抛了 InvocationTargetException: null」，真实原因全丢；必须剥到最内层 `cause`。
  2. `getDeclaredField` **只看本类**，而 `mCaptionViewSurfaceShow` 声明在父类 `FlexibleCaptionView` 上 ——字段查找要沿
     `superclass` 往上走。

**第 3 轮（把调用 post 到 `android.ui` 线程后）**：线程问题解决 ——用**视图自己的 `View.getHandler()`**
post 过去，`initCaptionView` / `initCaptionViewAndAttachToTask` / `showCaptionViewWithAnim` /
`updateFlexibleTaskCaptionVisible(true)` **四个调用全部无异常**， `mCaptionViewSurfaceShow = true`。

**但屏幕上仍然看不到操作条**。⚠️ 别把**左边缘那个小圆条**当成它 —— 那是 ColorOS 的**窗口边缘提示**（用户指出），跟操作条无关。所以状态是：视图建出来了、挂上了、`mCaptionViewSurfaceShow=true`、`getVisibility()=0`（VISIBLE），
**但它什么都没画出来**。

**第 4 轮（量它的几何）**：

```
getLeft()=0  getTop()=0  getWidth()=1272  getHeight()=2261   ← 撑成整窗，与任务同尺寸
getVisibility()=0（VISIBLE）
getFlexibleCaptionViewHandleButtonRegion() = Rect(0, 0 - 0, 0)   ← 手柄区是空的
父 0 = com.android.server.wm.FlexibleTaskCaptionView
父 1 = android.view.ViewRootImpl                                  ← 直接就是根视图
```

⇒ **`initCaptionViewAndAttachToTask(Task, Rect)` 的第二个参数就是操作条自己的尺寸**，而我们把**任务 bounds**
传了进去，于是它把自己撑成整窗、`handleButtonRegion` 也没算出来。这不只是「位置不对」，是**尺寸就该是顶部那条窄 rect**。

**第 5 轮（显式 rect + `relayout` + `mode`）**：**操作条露出来了**
—— 真机截图里窗口顶部中央出现那条灰色的圆角把手（`●●●` 在完整小窗里就是它）。⏹ 但它**点不动**：

- `handleButtonRegion` 仍然是 `Rect(0, 0 - 0, 0)` —— **触及区域没算出来**，所以那条把手画得出来、按下去没反应。
- ⚠️ **订正**：`initCaptionViewAndAttachToTask(Task, Rect)` 的第二个参数**不是**视图尺寸 ——传了 `(0,0 - 1272,157)` 之后
  `getWidth/Height` 仍是 `1272 × 2261`。这个视图的设计是**整窗覆盖、只在顶部画把手**，所以它的画面位置本来就是对的。
- 真正缺的是 **`FlexibleCaptionView.updateTouchRegion()`**（还有 `updateTouchRegionInner()`、
  `updateTouchableRegion(Region)`）—— 触及区域由它算。

**⏹ 收敛（2026-10-14 用户拍板）**：C 这条线**暂停**，`USE_FORGED_ANIMATION_ROUTE` 关回 `false`
（默认走 recents 正门 = 完整可用但无动画）。判断依据：

- 伪造态要通，就得**自己把框架「注册柔性任务」那一整套外围动作补齐**（操作条挂载顺序、圆角、触及区域、拖拽处理），而我们只能一样一样试出来，**每样隔一次整机重启**（已用 6 轮）；补完还有「点击被当拖动」「内容叠两份」两处没验。
- 正门给的是**完整可用**的窗口（圆角 / 操作条 / 菜单 / 输入都正常），代价只是**没有动画**（纯观感）。
- 而且伪造态依赖一堆内部状态位，ROM 一变全部重来。

**要接着做的人，从这里起手**（调用面已经完备，不用再为「调某个方法」编译）： `call` op 加了
`target=caption`（任务的操作条视图），且**视图方法会自动 post 到视图自己的线程** ——于是 `updateTouchRegion`
一类可以直接用广播调：

```bash
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op call --es target caption --es name updateTouchRegion --ei task_id N'"
```

剩下没验的两条：**触及区域补齐后能否点击 / 拖动**、**「内容叠两份」是不是也会随之消失**。

##### 附十一：**A 方案被证伪**；真正的变量是「过渡有没有 root」（2026-10-14 真机，无重启）

**先说结论：A 方案（换调用方）不成立。** 用 debug 通道的 `zoom` op **从桌面进程**发起、target 是
**前台任务**时，日志仍然是：

```
onStartActivityFromRecents task:#116 … isFlexibleTask:false ,pendingLaunchZoom:true ,zoomLaunchFlags:2
startFreeFormFromRecent current pendingLaunchZoom will toggle FlexibleTask.
toggleToFlexibleTask task:#116
relayoutCaptionView: reason = startFreeFormFromRecent
No transition roots in (#26) … so abort           ← 还是 abort
```

即：附九把「桌面那次多一条 `skipCurrentOrAdjustChange … launchFrom=2`」归因于**调用方身份**，这个归因
**是错的**。换成桌面进程发起，`skipCurrentOrAdjustChange` 并没有出现。

**但同一轮拿到了关键的正结果**：把 `zoom` op 改对之后（见文末的坑），对**在最近任务总览里展示着的任务**
调用，产出的日志与桌面原生「浮窗」**逐行一致**，动画完整跑通：

```
onStartActivityFromRecents … taskForceMaximized:true ,isFlexibleTask:false ,pendingLaunchZoom:true ,zoomLaunchFlags:2
collectFlexibleTaskInfo: mScale=0.8016529, mCornerRadius=91, mLaunchBounds=Rect(126,319-1398,2580)
setVisibility(Token{…}) visible=true defer commitVisibility for transition collecting
skipCurrentOrAdjustChange task=#116 launchFrom=2
onTransitionReady (#31) … r=[0@Point(0, 0)]                     ← 有 root
playTransition → FlexibleTaskTransitionHandler → MultiSpringAnimation
startSpringFlexibleAnimation alpha=0.0 ,scale=0.0  →  alpha=1.0 ,scale=0.8016529
Transition animation finished (aborted=false) ✅
```

两张对照（同机型、同一天、同一个 op、只改目标任务的呈现状态）：

| 目标任务的呈现状态               | `taskForceMaximized` | 过渡 root                    | 结果                             |
| -------------------------------- | -------------------- | ---------------------------- | -------------------------------- |
| 前台、全屏（我们「就在它里面」） | `false`              | **无**                       | `No transition roots → abort` ❌ |
| 在最近任务总览里（卡片在屏上）   | `true`               | **有**（`r=[0@Point(0,0)]`） | `MultiSpringAnimation` ✅        |

⇒ **变量是「目标是不是已经在最前」**，不是「谁发起」。前台任务本来就 top，`m=TO_FRONT`
不构成任何变化，框架收集不到 root，这次过渡直接丢。这与附九第 3 条（前台确实没有过渡 root）完全一致；A 方案的赌注（「`launchFrom=2`
会把窗口模式变更延后进过渡，于是前台任务也有 root」）**没有兑现**。

**另有一处认知订正**：原生动画的形状是 **`scale 0 → 0.8016529`、`alpha 0 → 1` 的「弹入」**，而**不是**
「从全屏缩到小窗」。所以前台场景缺的不是「一个能缩小的起点」，而是**框架压根不打算在这个场景开过渡**。

**顺带修掉一个真坑（值得单记）**：`zoom` 第一版把 options 造成了 `ActivityOptions`，而真实签名是

```
android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int, Bundle)   ← AIDL 门面直接收 Bundle
```

服务端自己 `SafeActivityOptions.fromBundle(options, callingPid, callingUid)`。两个反射陷阱叠在一起：

1. `Reflect.method` 会退化成「**名字 + 参数个数**」匹配 → 把 `Bundle` 形参误配成 `ActivityOptions` 也能"找到"；
2. `Reflect.call` 内部 `runCatching` 把 `argument type mismatch` **吞掉** → 日志上只剩一句干干净净的
   `startActivityFromRecentsForFlexible(#116, flags=2) → null`，看起来像「调了但没反应」。

⇒ 结论：**反射调用要显式打 cause，别用会吞异常的封装**（改用 `getDeclaredMethod`
精确签名 + 自己 try/catch）。修好之后返回值是对的：`2`（`START_TASK_TO_FRONT` = 已受理）。

**这一轮还顺手把迭代成本砍下来了**：注入侧改 `ProbeChannel` 后**不必整机重启** —— `assembleDebug` + `install -r` +
`am force-stop com.android.launcher`，桌面进程重启即加载新 dex（实测一轮约 1 分钟；日志里能看到新的模块指纹）。只有
`system_server` 那半仍需重启。

**下一步的三个选项**（待拍板）：

- **D1 · 自己放动画（最被看好）**：走 recents 正门拿到**完整可用**的窗口（圆角 / 操作条 / 输入都正常），然后在任务自己的
  `SurfaceControl`
  上跑一段与原生同形的弹入（`alpha 0→1`、`scale 0→0.8016529`，圆角 91 已验证能设）。原生动画本就是弹入而非「全屏缩下去」，所以这条**在观感上能做到一致**，且**不依赖过渡 root、也不需要伪造
  `mScenario`**。
- **D2 · 先回桌面再收**：同一次手势里先把桌面 / 总览置顶造出 root，再 `zoom`。代价是视觉上变成「应用先走、小窗弹入」。
- **D3 · 接受无动画**：recents 正门，窗口完整，只付观感的代价。

##### 附十二：伪造态**也**判死 —— 它压根没转换任务（2026-10-14 复测，无重启）

附十一把 A 方案判死后，伪造态（`switchState`）成了唯一「有动画」的路，于是复测一次。
**结论：它同样不成立，而且根因比「缺几个调用」更硬 —— 那个任务从来就不是小窗。**

**复测怎么做的**（全程无重启）：把 `MiniWindowLauncher.USE_FORGED_ANIMATION_ROUTE` 翻成 `true` （**宿主侧**改动 = 重编 + `kill`
宿主进程即可，几分钟内可来回翻），然后用 `ACTION_GESTURE` 广播直接触发侧键动作 —— 宿主那个接收器是
`NOT_EXPORTED`，**root（uid 0）发得进去**，不需要真的按侧键：

```bash
NOW=$(adb shell "cat /proc/uptime" | awk '{printf "%d", $1*1000}')
adb shell "su -c 'am broadcast -a com.lookie.opluskey.GESTURE -p com.oplus.gesture \
  --es key plus --es phase single --el sent_at $NOW'"
```

（`sent_at` 必须是新鲜的：`GestureReceiver` 2000ms 之外一律当积压丢弃，漏了它动作会静默不执行。）

**伪造态确实跑起来了，而且比历史记录里好**：

```
小窗指令：已喂 updateFlexibleTaskParams（含 HasCaption=true）
小窗指令：冒充柔性全屏态成功（mScenario=1 / mHasCaption=true / mFlexibleZoomState=2）
use memory rect to default:Rect(126, 319 - 1398, 2580)        ← 宿主下发的几何被吃住
switchState will startAdjustFlexibleTask task: 125
registerPointerEventListener success.                          ← 历史「点击被当拖动」的钩子这次装上了
updateFlexibleTaskCaptionVisible … isVisible=true
FlexibleCaptionView.initCaptionViewAndAttachToTask(…)          ← 框架自己把操作条挂上了
FTIWM[Task=125]: isAnimation Running
```

再加调试通道补的 `corner`（`setCornerRadius`）之后，屏幕上确实拿到了：**几何对 + 圆角 + 一条带 `🔍 ＋` 的操作条**（见
`build/shots/forged2.png`）。

**但它根本没变成小窗。** 决定性的一行是这个（`TaskOrganizerController`）：

```
onTaskInfoChanged taskid 125 … mWindowingMode=fullscreen        ← 自始至终是全屏，不是 100
```

`switchState` 只做了一套**视觉**动作（`OplusFlexibleInfoManager#setFlexiblePositionInfo` 一路把 surface 缩到
`scale=0.8014`、挪到小窗位置），**任务本身仍是 `fullscreen`**。也就是：**一个穿着小窗外衣的全屏任务。**

后果与历史记录那三条一一对上，且都有根因：

| 症状                                                                 | 根因                                                                                                                                                                                                                                                                                                                      |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `handleButtonRegion = Rect(0,0-0,0)`，`updateTouchRegion()` 也补不上 | 柔性任务注册那步没做（它才算把手区域）                                                                                                                                                                                                                                                                                    |
| **点一下就成拖动**，一拖小窗就没了                                   | 把手区域空 ⇒ 框架退化成「**整个窗口都是把手**」。真机日志：`isInFlexibleWindowHandleRegion is mini … rawX=703 rawY=1440` → `initMovingFlexibleTaskState` → `beginDrag` → 窗口被拖出屏幕顶部 → `exitFlexibleTaskWindowInnerLocked exitTo:6` → 任务退回全屏（`mScenario=0` / `mHasCaption=false` / `mFlexibleZoomState=0`） |
| 没圆角 / 没 ⋮ 菜单                                                   | 同源；圆角可以用 `setCornerRadius` 自己补，**但补了也改不了"它不是真小窗"**                                                                                                                                                                                                                                               |

**⇒ 三条路到此全部判死**（`device-findings.md` 附二/附九/附十一/附十二）：

| 路                           | 原地 | 窗口完整        | 动画                           | 结论                                                                         |
| ---------------------------- | ---- | --------------- | ------------------------------ | ---------------------------------------------------------------------------- |
| recents 正门                 | ✓    | ✓               | ✗（前台**结构上**无过渡 root） | 可用，但永远没动画                                                           |
| A · 换调用方（桌面进程发起） | ✓    | ✓               | ✗                              | 与调用方身份无关，**附十一证伪**                                             |
| C · 伪造态 + `switchState`   | ✓    | ✗（不是真小窗） | ✓                              | **附十二证伪**：任务始终 `mWindowingMode=fullscreen`，点一下就被当拖动并甩掉 |

**⇒ 「前台应用 → 小窗 + 框架原生过渡动画」在这个 ROM 上拿不到。** 剩下的唯一办法是
**自己放那段动画**（附十一的 D1）：走稳定的 recents 正门拿到真小窗，再在它的 `SurfaceControl`
上复刻原生同形的弹入（`alpha 0→1`、`scale 0→0.8016529`，圆角 91 已验证能设）。原生动画本就是「弹入」而非「全屏缩下去」，所以观感可以对齐，且不依赖过渡 root、不需要伪造状态。

##### 附十三：横屏应用的「收小窗」= 配置矩形被当成**横屏全屏任务**那套（2026-10-14 真机，PLK110）

**现象**（用户报「横屏的应用小窗尺寸还是大」）：把横屏应用收成小窗后，窗口**比屏幕还宽**。

**取证过程与硬数据**（设备 `ROTATION_90`，`tv.danmaku.bili` 任务 `#153` `…/UnitedBizDetailsActivity`，全屏
`mBounds=Rect(141,266-2381,1526)` = **2240 × 1260**）：

| #   | 证据                                                          | 值                                                                              |
| --- | ------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 1   | `dumpsys window` 里该窗口的请求尺寸                           | `Requested w=2240 h=1260`（= 横屏全屏任务那套）                                 |
| 2   | 触发 `zoom:mini` 后 `notifyZoomWindowShow` 的最终态           | `zoomRect = Rect(141,266 - 1457,1007)`，`scale=0.5875`                          |
| 3   | **截图像素级边缘检测**（判「`zoomRect` 到底是不是屏幕矩形」） | 左边界 `x=141`、下边界 `y=1006` ⇒ 与 `zoomRect` 的 `left`/`bottom` **双边吻合** |
| 4   | 由 1+2 算                                                     | `2240 × 0.5875 = 1316` = `zoomRect` 宽 ✓                                        |

⇒ **`zoomRect` 就是屏幕矩形**，横屏这次它是 **1316 × 741**，而屏宽只有 **1272** ——
**右侧溢出 185px**（约 14.5% 的窗口在屏幕外）。中途还有一帧 `Rect(119,730-1152,1311)` /
`scale=0.46116072`（1033×581，屏内），600ms 后被最终态覆盖。

**⚠️ 附带一条时序事实（会直接决定验收怎么写）**：收小窗那一下**先落一个过渡帧**，
**约 600ms 后**才定成最终几何。真机两次读数：

```
+0ms    Rect(119, 730 - 1152, 1311)   scale=0.46116072   ← 过渡帧，在屏幕内
+591ms  Rect(141, 266 - 1457, 1007)   scale=0.5875       ← 最终态，右侧溢出
```

按 350ms 去核会读到过渡帧，把「最后会溢出」的窗口报成「落在屏幕内」——模块的 `MINI_VERIFY_MS` 因此从 `350` 改成
`700`（2026-10-14）。 **判据教训**：小窗这类「框架自己会重新布局」的状态，验收时点必须晚于它落定，否则就是在测中间态。

**根因链**（离线读 `oplus-services.jar` → `classes3.dex`）：

```
FlexibleTaskController#startFreeFormFromRecent
  launchFlag == 32 ? getMiniFlexibleTaskInfo(task, record)                    ← 收起档
                   : collectFlexibleTaskInfo(intent, 1, dc, actInfo, 1)      ← 展开档（flags=2 走这条）
      └→ calcDefaultLaunchBounds(…, ActivityInfo.isFixedOrientationLandscape(actInfo), …)
          └→ OplusFlexibleTaskLayoutPolicy#calcLaunchBounds(outBounds, list, 横屏?, info, …)
              └→ getTaskRealSize(横屏?, info.getRatio())      ← 横屏/竖屏分档就在这里
```

`collectFlexibleTaskInfo` 又经 `fillFlexibleTaskInfo(Builder, Rect, Intent, ActivityInfo, 横屏?, DisplayContent)`
取几何，里面两处关键读法：
`intent.getStringExtra("calculate_bounds")`（`FlexibleTaskController.KEY_CALCULATE_BOUNDS`，等于 `"mini"` 时会改用
`OplusZoomDisplay#getDefaultFlexibleScale(scale, true)` 那条默认档）、以及 `OplusZoomDisplay#getDefaultFlexibleScale` /
`getScreenWidth` / `getFlexibleTaskFullScreenRatio`。

⇒ **配置矩形取的是「横屏全屏任务那套 2240×1260」（宽高比 1.778 = 16:9），再乘上一份缩放**：
`2240 × 0.5875 = 1316 > 1272`。而 0.5875 不是为 2240 宽配的 ——
`1272 × 0.5875 = 747`，它显然是**为竖屏那套配置矩形标定的缩放**。
**「配置矩形」与「缩放」这一对没配对**，才是溢出的直接原因。

框架本身有「横屏应用」专用的一整排常量（`com.oplus.zoomwindow.OplusZoomWindowManager`）：
`ZOOM_SCALE_FOR_LANDSCAPE_APP_DEFAULT` / `MINI_ZOOM_SCALE_FOR_LANDSCAPE_DEFAULT` / `ZOOM_LANDSCAPE_APP_RATIO` /
`ZOOM_LANDSCAPE_RATIO`。宿主侧反射可读的入口有 `getZoomBounds(boolean,int)` / `getZoomBoundsByTaskId(int,int)` /
`getCurrentZoomWindowState()`。⇒ 也就是说「横屏小窗该多大」系统自己有定义，我们这条 recents 路**没有对上**它。

**顺带修掉的那条假绿灯**：模块原先的验收判据是
`OplusAppInfo.appBounds.width() / Resources.getSystem().displayMetrics.widthPixels`，分母**不随旋转变**（竖屏恒 1272），横屏读到
`1.76` 却仍然落在「≥0.95 就算好」那一边 ⇒ 把「比屏幕还宽」报成了「几何正常」。现已改为读
`getCurrentZoomWindowState().zoomRect` 与 `Display#getRealSize` 比**是否落在屏幕内**（宿主侧，不用重启）。

**竖屏那条「未定论」已解**（2026-10-14 复测）：竖屏走的不是 recents 的「current is fullscreen task」那支，几何是正常的
`Rect(126,319-1146,2131)` = `1020×1812`、完全在屏内（`tv.danmaku.bili` `#157`
全屏在前台实测）。原先那个「竖屏也溢出」的怀疑来自 `aweme` 那次读数，没有再复现。

#### 附十三之二：**「回桌面」会把设备拽回竖屏** ⇒ 横屏小窗按竖屏比例生成（2026-10-14 真机）

用户口径：「这个版本前台横屏小窗是从竖屏态的比例往横屏态的比例收的」。真机时序：

```
触发前      ROTATION_90                             ← 横屏应用全屏在前台
发 HOME 后  ROTATION_0                              ← 桌面是竖屏 ⇒ 设备被拽回去
            Rect(126,319-1146,2131) s=0.8016529     ← 小窗按**竖屏比例**生成（1020×1812）
```

⇒
**小窗几何是按「起那一刻的显示帧」算的**。launch 配方要先把目标挪出栈顶才起得来，而「挪出栈顶」在这台机上的唯一手段就是回桌面 ⇒ 必然经过竖屏 ⇒ 横屏应用拿到竖屏比例。

**想不走这条路，试过并否掉：**

- 给 intent 加 `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` 后**原地直接起** ⇒ 仍返回 **`3`**
  （`START_DELIVERED_TO_TOP`）。该 flag 是侧边栏的配方（见下），但它**不改「栈顶实例消化」**这条判定。

**侧边栏官方配方（`com.coloros.smartsidebar` 拉 APK 反编译，`PageRoutUtils`）：**

```java
// startNormalApp：intent = ACTION_MAIN + setPackage + setClassName + CATEGORY_LAUNCHER
b.putInt("extra_window_mode", 100);                 // ← 我们也有 ✓
b.putInt("android.activity.splashScreenStyle", 1);  // ← 我们没带
b.putInt("start_zoom_at_other_side", 13/14/31);     // ← 我们没带（13=左 14=右 31=第三档）
Bundle b = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle();   // 不是裸 Bundle()
OplusZoomWindowManager.getInstance().startZoomWindow(intent, b, userId, pkgName);
// flags：0x10200000 = NEW_TASK|RESET_TASK_IF_NEEDED（常规）
//        0x18000000 = NEW_TASK|MULTIPLE_TASK + putExtra("multi_instance_source", 5)（多实例档）
```

⚠️ **订正一条历史结论**：`zoom-window.md` §6.2 曾记「侧边栏配方 = `extra_window_mode=100` +
`NEW_TASK|CLEAR_TASK`」—— 反编译看是 **`RESET_TASK_IF_NEEDED`（`0x00200000`）**，不是 `CLEAR_TASK`
（`0x00008000`）。`RESET_TASK_IF_NEEDED` 就是「点桌面图标」的语义，**不清后台栈**，比 `CLEAR_TASK`
安全得多。侧边栏能用它，是因为它**只对不在栈顶的应用**起小窗（`isAlreadyOpenedInTop` 时直接放弃）。

**⇒ 两条路各对一半，还没合起来**：launch 配方比例错（竖屏）但进屏；recents 正门比例对（横屏，因为不改方向）但几何溢出（见上）。**要的是「横屏帧 + 放得下的缩放」。**

⚠️ 复测作业纪律：这台机上「单击」动作会被用户**随时改**（本轮实测撞到过
`none`、`keyevent:24`，两次读数之间就被改掉）—— 借配置做实验必须用「一条命令块里 存-改-还原」，且还原值要现取、别用旧备份。

#### 附十三之三：**「前台应用不支持小窗」的框架判据**（2026-10-14 真机，PLK110）

用户报「有些应用不支持小窗」时的第一手判据 —— 框架自己会把这行打出来，**不用猜**：

```
I/FlexibleTaskController: current activity unsupport flexible window. Intent {… cmp=com.heytap.market/.activity.MainActivity}
E/FlexibleTaskController: startFreeFormFromRecent invalid flexibleTaskInfo, pls check:
    FlexibleTaskInfo{mResizeMode=0, mRatio=0.0, mScale=0.85, mLaunchBounds=Rect(0, 0 - 0, 0), mFlexibleState=0, …}
```

对照**支持**的应用（同一次实测，`com.android.settings`）：

```
D/FlexibleTaskController: collectFlexibleTaskInfo:FlexibleTaskInfo{mResizeMode=1, mRatio=1.7777778,
    mScale=0.8016529, mLaunchBounds=Rect(126, 319 - 1398, 2580), mFlexibleState=0, …}
```

读法（**这三项是关键**）：

| 字段            | 不支持           | 支持（竖屏标准）          | 含义                                                               |
| --------------- | ---------------- | ------------------------- | ------------------------------------------------------------------ |
| `mResizeMode`   | `0`              | `1`                       | 0 = 不可调尺寸 —— 框架在 `startFreeFormFromRecent` 里**直接 bail** |
| `mRatio`        | `0.0`            | `1.7777778`               | 高/宽（`2261/1272`）—— `0.0` 表示没算出来                          |
| `mLaunchBounds` | `Rect(0,0-0,0)`  | `Rect(126,319-1398,2580)` | 空表示没有可用落点                                                 |
| `mScale`        | `0.85`（没意义） | `0.8016529`               | 与 §五 尺寸档表里展开档的 scale 一致                               |

⚠️ **`ActivityInfo.resizeMode == 0` **不能**单独当判据**（这也是为什么模块里那个开关默认关着）： **游戏**（真机
`com.tencent.tmgp.supercell.clashofclans`）也是 `resizeMode=0`，但它走 **`startZoomWindow`（launch）**那条能起成小窗 ——
`invalid flexibleTaskInfo` 那道闸只在
**`startFreeFormFromRecent`（recents 正门）**里。所以「不支持」要看**具体那条路**的返回。

⇒ 模块侧的处置（2026-10-14 用户口径）：**什么都不做，只弹一条 Toast**。判据取「服务端受理了（`2`）却没收出窗口」或「压根没受理」，见
`docs/zoom-window.md` §2.2。
**不许**再退「按包名另起一份实例」—— 那条会回桌面再起，用户看到的是「一按侧键就回桌面重开」。

#### 附十三之四：`isSupportZoomMode` **不是纯查询** —— 判「不支持」时框架会自己弹 toast（2026-10-14 真机，PLK110）

用户报「选择应用里**一直弹**此应用不支持小窗，我还什么都没执行」。屏幕上的原话是：

> 此应用尚未支持浮窗

**这条 toast 不是模块发的**，是框架发的：

| 项             | 实测                                                                                                                        |
| -------------- | --------------------------------------------------------------------------------------------------------------------------- |
| toast 的投稿方 | `pkg = android, uid = 1000`（系统进程，`NotificationService` 的 `OplusNotificationLogger-->TOAST-->showToast` 行）          |
| 文案出处       | `reference/OplusLauncher.apk` 里有 `未支持浮窗`（本模块、`gesture.apk` 里都没有）—— 即 ColorOS 给用户按「浮窗」时的原话     |
| 触发者         | **我们自己那一串 `isSupportZoomMode` 调用**：判 `false` 的那一行后面 ~5ms 就跟着一条 toast，一条一条按 `false` 的顺序排出来 |

对照实验（同一台机、同一份 598 个应用清单，只差模块里有没有那次询问）：

```
预判开着  → 21:29:11 进页，21:29:11~14 每秒 0.7 个 toast（队列在排空）
预判关掉  → 21:30:42 进页、清单照常列出 598 个 → showToast 计数 0
```

**不是 `Bundle` 引起的**：把 `extra_window_mode=100` 换成**空 bundle**
再跑一遍，判据一模一样、**照样弹**（21:32:11.860 判 `false` → 21:32:11.865 弹一条）。

**框架服务里也没有可替代的静默判据**（`probe` 的 `dump` 实测）：

```
probe/dump com.oplus.zoomwindow.OplusZoomWindowManager filter='isSupport' → 2 个方法
  · isSupportZoomMode(String, int, String, Bundle) : boolean   ← 就是这个
  · isSupportZoomWindowMode() : boolean                        ← 设备级开关，不按包判
probe/dump com.android.server.wm.FlexibleWindowManagerService filter='Support' → 只剩
  isSupportResize(int) / isSupportScenario(int) —— 都不按包判
```

⇒ **结论**：这个 API 自带**用户可见副作用**（它就是 launcher 按「浮窗」时用的那一个），
**绝不能拿它当整表轮询的谓词**；只能用成「用户按下那一刻问一次」。模块侧最终落法见 `docs/zoom-window.md`
§十（勾选态或选中项每变一次问一次，两处合起来才盖住每一条会被写成 `window:<包名>` 的路径）。

#### 附十三之五：「重启服务」怎么把**页面自己**拉回来（2026-10-14 真机，PLK110）

需求是「点了重启服务，UI 进程也重启、页面重进一遍」。三条路逐一试过，只有第三条通：

**一、杀掉 `:ui` 进程，指望系统重建那个 Activity —— 不行。**

```
23:35:10.252 Process com.oplus.gesture.ui (pid 23276) has died: fg  TOP
23:35:10.253 ActivityTaskManager: Force removing ActivityRecord{...ActionButtonSettingsActivity t271}:
             app died, no saved state
```

进程死了，那条 `ActivityRecord` 是**直接抹掉**的（「没有 saved state」），任务栈塌回下面那个应用（实测前台落到桌面 /
KernelSU 管理器）。**不会有任何东西把页面重新拉起来**，用户被丢在外面。

**二、闹钟挂 `PendingIntent`（`getActivity`）预约自己回来 —— 被后台启动策略拦掉。**

```
23:35:14.784 E/ActivityTaskManager: Background activity launch blocked! goo.gle/android-bal
  [callingPackage: com.oplus.gesture; callingUid: 10277; callingPid: -1;
   isPendingIntent: true; originatingPendingIntent: PendingIntentRecord{363dd4d ... startActivity};
   realCallingUid: 1000; realCallingPid: 4360;              ← 真发起方是系统（闹钟投递）
   balAllowedByPiCreator: BSP.NONE;                         ← 我们（PI 的创建方）没开这档许可
   resultIfPiCreatorAllowsBal: BAL_ALLOW_PERMISSION;         ← 我们一开就是「按权限放行」
   balAllowedByPiSender: BSP.NONE; resultIfPiSenderAllowsBal: BAL_BLOCK;
   realCallerStartMode: MODE_BACKGROUND_ACTIVITY_START_DENIED;]
```

两处「谁允许」都是空的，于是拦掉。注意 `resultIfPiCreatorAllowsBal` 写的是 `BAL_ALLOW_PERMISSION`
—— 我方 uid 本来就**有**后台启动权限（见下），只是这条 PendingIntent 路线还额外要求「创建方声明允许」，而闹钟这一侧我们控制不了发起方的档位。**别再走这条路。**

**三、交给宿主**主**进程去 `startActivity` —— 通。**

本包拿着后台启动的正门权限（`dumpsys package com.oplus.gesture` 实测）：

```
android.permission.START_ACTIVITIES_FROM_BACKGROUND: granted=true
android.permission.SYSTEM_ALERT_WINDOW: granted=true
android.permission.SCHEDULE_EXACT_ALARM: granted=true
```

宿主**主进程自己**调 `startActivity`
是「app 自己的后台启动」，按权限放行；而它本来就活着（收手势的接收器、设置页那句「在不在」都在它身上，「重启服务」的前置条件也正是它在跑）。落法：设置页发一条
`ACTION_PAGE_RELAUNCH` → 宿主主进程等 900ms（**必须等页面进程死透**，否则 `singleInstance`
只会把旧进程那一个拿到前台，一行新代码都没换）→ 拉起设置页 → 再过 500ms 自杀重来。实测一轮（23:40:4x）：

```
+1.0s  :ui = 31823（新进程，页面已回来）      宿主主进程 = 30326（还是旧的）
+2.0s  :ui = 31823（1s）                     宿主主进程 = 31918（新，已重装手势 Hook）
```

全程没有一条 `Background activity launch blocked`（对照：走闹钟那条路每次都有一条）。

## 十八、COUI `SnackbarHost` 的裁切边界 = **item 自己的盒子**（实测，2026-10-14，PLK110）

### 18.1 现象

首页那条常驻提示（`SnackbarHost`）的胶囊，**顶上那道影子被齐平整切**：不只「看着淡」，而是有一条一眼可见的直边。用户 2026-10-14 报的原文是「这个 snackbar 的阴影被裁切了」「阴影还是被切断」「切断和 padding 没关系，肯定是有容器设置了不外溢」——**最后一句是对的**。

### 18.2 取证手法（可复用）：逐列扫像素、找**硬跳变**

整屏截图（`adb exec-out screencap -p`）后用 PIL 逐列扫，判据不是「颜色深浅」而是**相邻像素的跳变**：

- 真影子的衰减是**平滑**的：沿中心列从胶囊边往外，每 ~4px 掉 1 个灰阶（实测斜率 ≈0.2~0.27 灰阶/px）；
- 被裁掉的地方是**一步跳变**：底色 `240` 直接到 `230`（10 个灰阶）或 `0` 直接到 `3`。

实测数据（PLK110，1272×2772，density 560 —— **1dp = 3.5px**）：

| 位置         | 数据                                                               | 结论                                                                        |
| ------------ | ------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| 胶囊白底     | y ∈ [2473, 2603]，高 131px = 37.4dp                                | 单行胶囊                                                                    |
| 胶囊**上方** | y=2459 处 240→230（**跳 10**），此后每 4px 掉 1 阶，到 2470 才 227 | **顶上被切**，可见只剩 14px = **4dp**                                       |
| 胶囊**下方** | y=2604 起 220 → 2709 平滑回到 240，全程无跳变                      | 底下**没**被切，有 30dp 可用                                                |
| 胶囊**左侧** | y=2500/2538/2560 三条线上，影子起点都精确落在 x=229                | 距胶囊左边 285 恰好 **56px = 16.0dp**，且起点是 0→3 的跳变 ⇒ **横向也被切** |

「横切在 16.0dp、上切在 4.0dp」这两个数**正好等于库给胶囊的内边距** —— 见下。

### 18.3 机制：`SnackbarHost` 是「内容多高就多高」的 `LazyColumn`，视窗 = item 盒子

反编译 `coui-ui` 的 `SnackbarKt`（`javap -c`，`Snackbar.kt:272` / `Snackbar.kt:279`）得到结构：

- `SnackbarHost(state, modifier, canSwipeToDismiss, content)`：外面一个 `Box`（挂调用方的 `modifier`），里面是
  **`LazyColumn`** —— `reverseLayout = true`、`verticalArrangement = Arrangement.Top`、
  `horizontalAlignment = CenterHorizontally`、`userScrollEnabled = false`、 **`contentPadding` 底部 28dp**；
- item 内容 = `Snackbar(data, modifier, …)`，而 `Snackbar` 自己第一层就是
  **`Modifier.padding(horizontal = 16.dp, vertical = 4.dp)`**，然后才
  `dropShadow(shape, Shadow(elevation = 26.dp, offset = DpOffset(0.dp, 4.dp), alpha = 0.14f))` + `Surface`。

于是几何完全对上：

```
影子需要的余量（elevation 26dp、下偏 4dp）  ： 上 ~22dp、下 ~30dp、左右 ~26dp
库实际给的（胶囊外面）                      ： 上 4dp、下 4dp+28dp=32dp、左右 16dp
                                              ↑ 只有这一档不够，就是被切的那条边
```

**视窗 = item 盒子向下多出 `contentPadding` 的 28dp**，所以底下 32dp 刚好够、四周只够库里给的那点。 `dropShadow`
本身**不裁**（影子能画到胶囊下方 30dp，远超自己的 layout bounds）—— 裁的是 item 外面那层。

### 18.4 试错过的那条路（别再走）

给 `SnackbarHost` 自己加 `Modifier.padding(bottom = 16.dp)` —— **没用**： `padding`
是从视窗**往里抠**，而视窗跟着内容一起缩，切痕会**跟着胶囊一起上移**（实测两次截图的切痕位置差正好等于所加的 padding）。这条也解释了为什么用户会说「和 padding 没关系」。

### 18.5 正解

走 `SnackbarHost` 的 `content` 槽位（签名是 `content: @Composable (SnackbarData) -> Unit`， **不带 `Modifier`
参数**），把胶囊包进一层留白：

```kotlin
SnackbarHost(state = …, modifier = …) { data ->
    Box(Modifier.padding(SNACKBAR_SHADOW_ROOM)) { Snackbar(data = data) }
}
```

裁切边界是 **item 的盒子**，所以只有撑大 item 才会把边界一起撑出去。`SNACKBAR_SHADOW_ROOM = 20.dp` （配上库自己的 4dp /
16dp ⇒ 上 24dp、下 52dp、左右 36dp，四周都够）。

**改后复测（同一台机、同一判据）**：胶囊上方从「距顶 4dp 处 240→230 一步跳」变成
**连续 77px（22dp）的平滑衰减**（每 ~4px 掉 1 阶，全程无跳变），下方仍 105px 平滑 —— 顶上那一刀没了。胶囊整体比改前高 4dp（去掉外层 16dp
inset、补 20dp item 留白 ⇒ 净 +4dp），符合预期。

### 18.6 顺带否掉的嫌疑人

页面根那个 `Box(Modifier.fillMaxSize().padding(padding).clipToBounds())`（`SettingsPage.kt`） **不是**元凶：它是
`fillMaxSize`、底边就是屏幕底边，而切痕在 `y=2459`（屏幕中部偏下），它物理上切不出这条线。那一句 `clipToBounds()`
继续留着裁 push/pop 转场出画的部分。

## 十九、「按了没反应，可它还是振了一下」：转发那一步卡在 **system_server 主 looper** 上（实测，2026-10-14，PLK110）

### 19.1 现象

用户报「整体服务没起来时按键不要振动，不要会误以为功能能用」。查日志发现不是「服务没起来」，而是**手势被宿主当成过期包丢掉了**，可注入侧**早就振过**了：

```
15:14:55.523 (system) 侧键：单击                      ← 判出手势（InputReader 线程）
15:14:58.413 (system) 已把侧键手势转给宿主：single      ← 2.89s 之后才真发出去
15:14:58.695 (host)   收到 system_server 转来的手势：key=plus phase=single
15:14:58.696 (host)   侧键 的 single 已过期（发出于 3161ms 前），丢弃不执行
```

宿主 `STALE_MS = 2000`，3161 > 2000 ⇒ 丢弃。宿主那条「执行」日志一次都没出现，**动作没跑**。

### 19.2 根因：`sentAt` 打点在前、`sendBroadcast` 却排在主 looper 后面

- `GestureForwarder.forward()` 取
  `sentAt = SystemClock.elapsedRealtime()`（这一步在**判出手势那一刻**、InputReader 线程上同步跑），随后用
  `handler.postDelayed` 投递真正那条 `sendBroadcast`；
- 它用的 `handler` 是 `KeyHooks.handler = Handler(Looper.getMainLooper())` ——
  **system_server 的主 looper**。主 looper 被占住多久，广播就晚多久；而 `sentAt` 早就在那之前定死了。
- 于是宿主算出来的 `age = 收到时刻 - sentAt` **= 真实发-收延迟 + 主 looper 的迟滞**。实测这一次：真实「发→收」只有
  **282ms**，主 looper 迟滞 **2879ms**，合计 3161ms。
- 因为「已把侧键手势转给宿主」这行**是在 runnable 里打**的（= 真发出的时刻），所以注入侧日志看起来一切正常 —— 单看它发现不了问题，必须拿宿主那笔
  `age` 去对。

### 19.3 判据（可复用）

**同一次按压的「注入侧发出」与「宿主收到」两个时间戳之差，必须约等于宿主报出来的 `age`。**
两者不等（这里差 2879ms），就说明卡在中间某一跳上，而不是链路真的慢。

### 19.4 影响面与待办

- 这次按压的**动作没执行**（用户读到的就是「按了没反应」）；旧代码还在注入侧振了一下 ⇒「振了却什么都没发生」，正是用户说的「会误以为功能能用」。
- 触觉反馈已经挪回宿主侧（见 changelog 同日那节）⇒ **这类被丢弃的包再也不会振** ✓。
- ⏭️ **但动作本身还是会丢**：待办是把转发那一步挪出主 looper（例如给 `GestureForwarder` 一个专用
  `HandlerThread`，只承载那两条 `sendBroadcast`，不动 `GestureDetector`
  的计时器线程）。⚠️ 属注入侧改动，**要整机重启才生效**；且它改的是按键热路径的线程归属，须先想清楚再动。

## 二十、COUI 浮层列表：**滑动会被当成「选行」**（实测，2026-10-15，PLK110）

用户报「添加命令滑动时偶尔会直接触发点击」：在「添加命令」那张长列表里滑动，偶尔会选中手指下面那一行（加了一步命令 / 开了二级菜单 / 菜单自己关了）。

**取现场的办法**（这一段是新增 `ui/component/GestureProbe` 的由来）：行的点击由库在**抬指那一刻**
派发（`PopupListGestureHost.onUp` →
`handle.onClick()`），出问题时日志里只剩「某一行被点了」这个结果，看不出那一下其实滑了多远。给整列外面挂一层只记账、**不消费事件**的手势探针（`PointerEventPass.Initial`
读，抢在任何消费之前），把「位移 / 峰值 / 移动次数 / 用时 / 列表内容移位」连同结果一起打出来。

**现场（用户真机，00:22:29 与 00:22:51 两笔）**：

```
浮层列表：这一段是滑动 —— 位移 261px / 峰值 261px（阈值 28px），移动 64 次，用时 764ms，列表内容移位 261px
浮层点选：创建自定义命令 —— 位移 261px / 峰值 261px（阈值 28px），移动 64 次，用时 764ms，列表内容移位 261px

浮层列表：这一段是滑动 —— 位移 273px / 峰值 273px（阈值 28px），移动 32 次，用时 418ms，列表内容移位 273px
浮层点选：截图 —— 位移 273px / …
多条命令：加第 1 步 → shell:service call color_screenshot 1
```

判据一句话：**「点选」那一行的「位移」是 0~几 px 才是真点击；带着几百 px 就是滑动被当成了点击。**
（两笔的列表内容也确实跟着滚了 261 / 273px —— 不是「列表没动能滚」那种，是**滚着也在选**。）

**机制**：库的判据只有内部那个
`isDynamicSelection`（源码：`!scrollState.canScrollForward && !scrollState.canScrollBackward`，等于「这一列量不出滚动范围」时才允许**拖拽选行**）。长列表显然能滚，可那一瞬间它仍被判成了「拖拽选行」⇒ 整段滑动期间按压高亮跟着手指走，抬指就选中。同一次会话里 30 次滑动只中 2 次（我这边用
`input swipe` 复现不到，只有「点开菜单后紧接着滑」中过一次）——
**是竞态，不是手势形状**：位移 / 时长 / 移动次数三档在有问题的与没问题的之间完全重叠。

**收口（本项目侧，不动库）**：`MenuPopup.kt` 的 `MenuRow` 在派发那一行之前，按**这一段手势自己的位移**
兜一道 —— 越过阈值（`peak > touchSlop`）就是滑动，**吞掉这次选中**。阈值取库同一个
`LocalViewConfiguration.touchSlop`；真点击是 0~几 px，不会误吞。副产物：滑动不再「选中」，短菜单上那套「按住滑到某行再松手＝选它」也一并没了 —— 用户要的是「滑就是滑，点就是点」。⚠️ 只挂在浮层行上：页面行（「添加命令」那一行、各步骤行）的点击是 Compose
`clickable` 给的，拖动会被页面滚动消费掉，没有这个毛病（同一次会话的日志里它们的位移都是 0px）。

## 二十一、改配置外皮之前该知道的两条前提（实测，2026-10-15，PLK110）

### 21.1 正式包的 dex：自家包名只留入口与资源类，其余**改名 + 重打包进默认包**

`app/proguard-rules.pro` 放开混淆之后（不再有 `-keepnames class com.lookie.opluskey.** { *; }`，
它等于 `-keep,allowshrinking`：只钉名字、允许收缩），release 包的 `classes.dex` 共 3146 个类：

- 留在 `Lcom/lookie/opluskey/` 下的只有 **6 个**：`KeyModule`（`META-INF/xposed/java_init.list`
  按名字取）与 `R` / `R$drawable` / `R$mipmap` / `R$raw` / `R$string`（`Resources.getIdentifier` 按名字取）。
- 其余 **3117 个全在默认包**（无包名），`mapping.txt` 里形如 `FileObfuscation -> t50:`。
  `FileObfuscation` / `scramble` / `unscramble` / `unwrap` / `ConfigCodec` 在包里都搜不到了。

⇒ **`core/config/FileObfuscation` 那两层只挡「打开配置文件的人」，挡不住「读 dex 的人」** ——
异或密钥与替身字母表仍以字节形式躺在 dex 里，只是没了可以搜的名字。这不是缺陷、是有意的，
但它划定了外皮的能力边界：任何「加了混淆就等于保密」的说法都不成立。

**R8 默认就会重打包**（所以 `-repackageclasses ''` 是句废话，要关掉得写 `-keeppackagenames`），
默认包里撞名之所以不怕：LSPosed 的 `LspModuleClassLoader` 委托顺序是 **`findClass` 先于
`parent.loadClass`**，模块自己的类永远取自己那份；宿主那边看不到模块的类，反向也不会串。
实测各宿主类路径的默认包类数（`a`~`d` 是与本包重名的全部）：

| 类路径 | 默认包类数 | 与本包撞名 |
| --- | --- | --- |
| `framework.jar` | 0 | 0 |
| `oplus-framework.jar` | 0 | 0 |
| `services.jar` | 7（`LZMA*Demo`） | 0 |
| `OplusGestureUI.apk`（`com.oplus.gesture`） | 4（`a`~`d`） | 4 |

那 4 个撞名在**带 `-keepnames` 的在机版本上就已经一样**，模块照常工作 ⇒ 撞名字段无害。

查法（两条都要）：

```bash
# 类描述符要按 dex 头部三段读：0x38 string_ids → 0x40 type_ids → 0x60 class_defs，
# class_idx 是 type_ids 的下标、不是 string_ids 的（读错会得到一堆成员名字符串）
unzip -p app-release.apk classes.dex > /tmp/classes.dex
strings /tmp/classes.dex | grep -c 'Lcom/lookie/opluskey/'    # 只剩入口与 R*，粗筛够用
adb exec-out su -c 'cat <真机 base.apk>' > local.apk && sha256sum local.apk <本地构建的包>   # 确认装的就是这个包
```

**同级的一条**：构建时刻 `resValue("string", "build_id", …)` 落在 `resources.arsc` 里、**不在 dex 里** ——
想在包里核「手上这个包是哪一刻出的」，要读 `resources.arsc`（`unzip -p <apk> resources.arsc | grep '20..-..-.. ..:..:..'`）。
同一份源码连编两次，**只有 `resources.arsc` 这一项会变**（就是它），其余各条含 `classes.dex` 逐字节相同 ⇒
R8 的类名分配是可复现的，谁手上哪个包都能对上同一份 `mapping.txt`；想连这一项也定住就构建时给
`SOURCE_DATE_EPOCH`（`build_id` 已经按它取时间）。

### 21.2 工作树的**行尾不统一**：多数 CRLF，少数 LF

`core.autocrlf=true` 且索引里存的是 CRLF ⇒ `git checkout` 出来的文件是 CRLF；而后来**新写**的文件
（`docs/device-findings.md` 就是其中一个）是 LF。同一批要改的文件里混着两种行尾是**常态**。

⇒ 批量改这些文件时必须**按文件原本的行尾写回**（读时按 universal newline，写时按该文件的 EOL）；
拿 LF 去写 CRLF 文件会造出一整篇「全文件都改了」的假 diff。核法：

```bash
grep -c $'\r' <文件>     # 等于行数 ⇒ CRLF；0 ⇒ LF
```

（另：本项目的 `.workbuddy` 是**被有意占位成普通文件**的，所以 `.workbuddy/` 下任何路径都写不进去、
工作区记忆不落盘 —— 见仓库根目录那个占位文件自身的说明。）
