> 系统镜像里读出来的。实现必须对齐本文件；本文件与代码冲突时以本文件为准，并回头修代码。

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

### 入口方法（两种，都要挂）

```
com.android.server.policy.StrategyActionButtonKeyLaunchApp
    # actionInterceptKeyBeforeQueueing(Landroid/view/KeyEvent;IIZZ)I

com.android.server.policy.StrategyActionButtonKeyLaunchApp$2
    # interceptKeyBeforeQueueing(Landroid/view/KeyEvent;IIZZ)I
    # preCondition()Z
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
4. 保留 `hook/SystemContext.kt`：注入侧发广播 / `startService`
   仍要一个可用的 Context（`ActivityThread.getSystemContext()` 的那个 `mAppThread` 是 null，`sendBroadcast`
   内部要它，所以必须借一个真实 App 进程的 Application Context）。

## 七、由此确定的实现方案

> **实测状态（2026-10-10，PLK110 / Android
> 17）**：链路**端到端跑通**。开机 5/5 接管点全挂上；单击 / 双击 / 长按三种手势实测判定正确、动作分发正确。

1. **Hook 面**：`StrategyActionButtonKeyLaunchApp#actionInterceptKeyBeforeQueueing`
2. **配置面**：配置只放**宿主 App 私有目录**（`/data/data/com.oplus.gesture/files/`），由宿主进程（判定 + 执行）读，**热加载**、改完立即生效、**无需重启**。注入侧**不读配置**（它读不到，见六之七）；它唯一需要的主开关布尔走广播。系统侧键映射由**宿主进程**用
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

⇒ `page/ActionCatalog.kt` 里**直接写全名**，不拼前缀。

设置页侧（`u:r:platform_app:s0`）的结果：

| 路径                                                      | 写           | 读           |
| --------------------------------------------------------- | ------------ | ------------ |
| `/data/data/com.oplus.gesture/files/opluskey_config.conf` | **OK**       | **OK**       |
| `/sdcard/OPlusKey/config.conf`                            | FAIL(ENOENT) | FAIL         |
| `/storage/emulated/0/OPlusKey/config.conf`                | FAIL(ENOENT) | FAIL         |
| `/data/system/OPlusKey/config.conf`                       | FAIL(EACCES) | FAIL(EACCES) |
| `/data/local/tmp/opluskey_config.conf`                    | FAIL(EACCES) | FAIL         |
| `/data/misc/opluskey/config.conf`                         | FAIL(ENOENT) | FAIL         |

⇒ 只能放**宿主 App 自己的私有目录**。写入时必须**逐级放开遍历权限**：

```
/data/data/com.oplus.gesture              0700 → 0711   ← 不放开 system uid 进不来
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

**修法（两条一起上，`page/SettingsPageHooks.kt`）**：

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
- **与本模块无关**：模块不碰任务 / 启动模式，`page/` 下没有任何 `startActivity` /
  `setTaskDescription`（`FLAG_ACTIVITY_NEW_TASK` 只出现在 `hook/ActionRunner.kt`，
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
`{735, 780, 782}`），策略类路径按它认领；PM 兜底路径**只认原始码**（735），否则同一次按键会被策略类和 PM 两条路各判一次。

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

不是猜的。涉及的文件：`hook/KeyHooks.kt`（system_server 侧）、`hook/ActionRunner.kt`（宿主侧）。

### 10.8 宿主的包可见性：声明了 `QUERY_ALL_PACKAGES`（离线核对 `reference/gesture.apk`，2026-10-13）

要列**系统应用**（`page/AppPicker.kt` 里「显示系统应用」那一项）先得回答「看得见哪些包」，答案就在宿主自己的清单里：

```bash
aapt2 dump xmltree --file AndroidManifest.xml reference/gesture.apk | grep -A1 QUERY_ALL_PACKAGES
#   E: uses-permission (line=29)
#     android:name="android.permission.QUERY_ALL_PACKAGES"
```

- 我们的界面代码跑在**宿主进程**里 ⇒ 包可见性**按宿主算**：`getInstalledApplications(0)`
  拿得到全量（含系统应用），`getLaunchIntentForPackage` / `getApplicationInfo` 对任意包都通。
- ⇒ **模块自己的清单不要加这条权限**：权限按 uid 判，而页面代码的 uid 是宿主（同 `AndroidManifest.xml` 里 `INTERNET`
  那条注释的道理）。
- ⚠️ 这条推翻了一处旧文案：「选择应用」页的空态写着「系统限制了本页可见的应用列表」——那个前提不成立（真出现空列表只会是别的原因）。
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

- 实现：`hook/ActionRunner.runSteps` 用 `handler.postDelayed` 串成链（**绝不能 `Thread.sleep`**
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

`@hide` ⇒ 只能反射调（宿主进程里隐藏 API 反射可用，同 `hook/KeyInjector` 调 `InputManager.injectInputEvent` 那条先例）。

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
