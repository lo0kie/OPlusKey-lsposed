# 配置存储与落盘

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

## 9.8 重启后「配置读成默认值」不是文件丢了，是 CE 存储还没解锁（实测）

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

## 9.9 配置改放 DE 目录：解锁前也能读到（实测，2026-10-14）

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

改法：目录换成
`/data/user_de/<uid>/com.oplus.gesture/files`（现在这个路径只在 `core/config/ModuleFiles.hostFilesDir()` 里写一份），三个文件（主配置 / 命令库 / 界面状态）一起走。**没写任何兼容代码**：本模块尚未发行过，CE 上那份旧文件直接删掉即可，不做搬迁。

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

| 类路径                                      | 默认包类数       | 与本包撞名 |
| ------------------------------------------- | ---------------- | ---------- |
| `framework.jar`                             | 0                | 0          |
| `oplus-framework.jar`                       | 0                | 0          |
| `services.jar`                              | 7（`LZMA*Demo`） | 0          |
| `OplusGestureUI.apk`（`com.oplus.gesture`） | 4（`a`~`d`）     | 4          |

那 4 个撞名在**带 `-keepnames` 的在机版本上就已经一样**，模块照常工作 ⇒ 撞名字段无害。

查法（两条都要）：

```bash
# 类描述符要按 dex 头部三段读：0x38 string_ids → 0x40 type_ids → 0x60 class_defs，
# class_idx 是 type_ids 的下标、不是 string_ids 的（读错会得到一堆成员名字符串）
unzip -p app-release.apk classes.dex > /tmp/classes.dex
strings /tmp/classes.dex | grep -c 'Lcom/lookie/opluskey/'    # 只剩入口与 R*，粗筛够用
adb exec-out su -c 'cat <真机 base.apk>' > local.apk && sha256sum local.apk <本地构建的包>   # 确认装的就是这个包
```

**同级的一条**：构建时刻（`build_id` 字符串资源）落在 `resources.arsc` 里、**不在 dex 里** ——
想在包里核「手上这个包是哪一刻出的」，要读 `resources.arsc`（`unzip -p <apk> resources.arsc | grep '20..-..-.. ..:..:..'`）。
同一份源码连编两次，**只有 `resources.arsc` 这一项会变**（就是它），其余各条含 `classes.dex` 逐字节相同 ⇒
R8 的类名分配是可复现的，谁手上哪个包都能对上同一份 `mapping.txt`；想连这一项也定住就构建时给
`SOURCE_DATE_EPOCH`（`build_id` 已经按它取时间）。

注意它**不再是** `resValue`：那个值在配置期就固定，开了 configuration cache 会被冻住成第一次的时刻。
现在由 `GenerateBuildId` 任务在执行期生成 `values/build_id.xml`（见 `app/build.gradle.kts`）。

### 21.2 工作树的**行尾不统一**：多数 CRLF，少数 LF

`core.autocrlf=true` 且索引里存的是 CRLF ⇒ `git checkout` 出来的文件是 CRLF；而后来**新写**的文件
（`docs/device/` 下那批文件里就有几个）是 LF。同一批要改的文件里混着两种行尾是**常态**。

⇒ 批量改这些文件时必须**按文件原本的行尾写回**（读时按 universal newline，写时按该文件的 EOL）；
拿 LF 去写 CRLF 文件会造出一整篇「全文件都改了」的假 diff。核法：

```bash
grep -c $'\r' <文件>     # 等于行数 ⇒ CRLF；0 ⇒ LF
```

（另：本项目的 `.workbuddy` 是**被有意占位成普通文件**的，所以 `.workbuddy/` 下任何路径都写不进去、
工作区记忆不落盘 —— 见仓库根目录那个占位文件自身的说明。）
