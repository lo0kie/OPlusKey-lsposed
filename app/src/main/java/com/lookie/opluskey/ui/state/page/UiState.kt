package com.lookie.opluskey.ui.state.page

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.config.ModuleFiles
import com.lookie.opluskey.core.config.withCrossProcessLock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config

/**
 * 「界面状态」的本地持久化 —— 不走用户配置（那份文件是执行侧要读的契约，落在
 * `ConfigStore.uiFile()` 那种「只有本进程看」的文件里）。存的是「这一页怎么显示」的偏好，格式为 `key=value`
 * 若干行（`#` 开头为注释）。
 *
 *  **写方有两个进程**：设置页写显示偏好，宿主主进程写 [KEY_INJECT_EVER_ALIVE]（它收到注入侧回包那一下）。
 *  同 uid、同一份文件，所以「读 → 改 → 整份写」必须跨进程串行（见 [writeUpdate]）。
 *  读在 `readPageLoadSnapshot`（建页那一刻、**IO 线程**上，见 `ui/state/page/PageLoadSnapshot.kt`）、
 *  写在「选择应用」菜单翻档那一下，读不了 / 解析不了一律退默认（关），与配置同一条降级纪律。
 */
internal object UiState {
    /** 「选择应用」页是否列出系统应用。 */
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"

    /** 「选择应用」页（**只有「打开应用」那一趟**）是否把拉不起来的应用也列出来。 */
    private const val KEY_SHOW_UNOPENABLE = "show_unopenable"

    /**
     * 「系统那一侧**曾经**回过话」—— 只在收到注入侧回包那一下写 true，此后不再回 false。
     *
     *  它要回答的问题只有一个：**这次到底是「从来没用过」还是「用过、这会儿没起来」**。
     *  两者的现象一模一样（注入侧没有回包），但对用户说的话不同：前者要先去框架里勾作用域，
     *  后者只要重启。没有这一位，界面只能把两种可能一起糊成一句。
     *
     *  为什么存在界面状态里而不是用户配置里：它描述的是「这台机器上跑过没有」，不是用户的选择，
     *  也不该进执行侧要读的那份契约（`opluskey_config.conf`）。
     */
    private const val KEY_INJECT_EVER_ALIVE = "inject_ever_alive"

    /** 上一次收到注入侧回包时的那轮开机（`ProcClock.bootWall()`，秒级）—— 用来判「那次是不是本轮的」。 */
    private const val KEY_INJECT_ALIVE_BOOT = "inject_alive_boot"

    /**
     * 「不再提示」的那个版本号 —— 用户在这一版上勾过「不再提示」就记在这儿。
     *
     *  只在**版本号相等**时压住提示：下一版出来还照常提示（「不再提示」说的是「这一版我装了」，
     *  不是「以后都别问」）。
     */
    private const val KEY_SKIP_UPDATE_VERSION = "skip_update_version"

    /**
     * 界面状态的一次读全 —— **建页那一次用的唯一入口**。
     *
     * 逐项各读一遍的话会把同一个文件 open + parse 好几遍；建页那次读现在整个挪到了
     * IO 线程上（见 `ui/state/page/PageLoadSnapshot.kt`），但「一次开一次盘」这件事本身仍然成立
     * —— 少一趟就少一趟，行为完全一样。
     *
     * 注意：这里**没有**「以小窗口打开」—— 那一项**不落盘**：
     * 它默认关、每次进「选择应用」页按「正在编辑的那个动作」重算，见 `SettingsPage.picker`。
     */
    fun readPrefs(): UiPrefs {
        val map = parsePrefs(readTextOrNull().orEmpty())
        return UiPrefs(
            showSystemApps = map[KEY_SHOW_SYSTEM_APPS]?.toBooleanStrictOrNull() ?: false,
            showUnopenable = map[KEY_SHOW_UNOPENABLE]?.toBooleanStrictOrNull() ?: false,
            injectEverAlive = map[KEY_INJECT_EVER_ALIVE]?.toBooleanStrictOrNull(),
            injectAliveBoot = map[KEY_INJECT_ALIVE_BOOT]?.toLongOrNull() ?: 0L,
            skipUpdateVersion = map[KEY_SKIP_UPDATE_VERSION]?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 写一条；其余键原样保留（先读出来改一个键再整份写回）。走 [ModuleFiles.write]：
     * 虽只有本进程读写，但「先截断再写」遇到进程被杀会把整份状态变半截。
     * `openReadable = false`：不需要跨 uid 读。
     *
     * 注意：**读不出来时一个字节都不写**：写回是「读 → 改一个键 → 整份写」，
     *  读失败时手上只有空表 —— 照写就把**另一个键一起抹掉**了（用户只是切了一下「显示系统应用」，
     *  「不再提示」那一版却悄悄回到默认）。
     *
     * @return 是否真的落盘 —— 调用方（`PageHeader` 那三处切换）会紧接着向用户报「已…」，
     *   丢了这个返回值就变成了「明明没写进去却说已落盘」。吞掉返回值、那三处又用
     *  `XLog.d` 打一句，release 下那行被编译期消除，是最难发现的一类谎报。
     */
    fun writeShowSystemApps(value: Boolean): Boolean = write(KEY_SHOW_SYSTEM_APPS, value.toString(), refuse = { refuseWrite(it, value) })

    fun writeShowUnopenable(value: Boolean): Boolean = write(KEY_SHOW_UNOPENABLE, value.toString(), refuse = { refuseWrite(it, value) })

    /**
     * 记下「这一版的更新提示别再弹了」。
     *
     * @return 是否真的落盘 —— 调用方据此决定要不要说一句「已记下」
     */
    fun writeSkipUpdateVersion(version: String): Boolean =
        write(KEY_SKIP_UPDATE_VERSION, version, refuse = { refuseWrite(it, version.isNotEmpty()) })

    /**
     * 记下「系统那一侧回话了，就在这一轮开机里」。
     *
     *  调用方（`PageContent` 的注入侧回包处理）只在值**真的要变**时才调它：注入侧每次自检都回包，
     *  每次都落一次盘属于白写（这是主线程上的调用点，写盘必须可省）。
     *
     *  两条一起写：`ever` 是布尔、`boot` 是那一轮的开机指纹。`ever` 只增不减 ——
     *  它的语义是「这台机器上曾经跑起来过」，不是「现在活着」。
     *
     * @return 是否真的落盘
     */
    fun writeInjectAlive(bootWall: Long): Boolean =
        writeUpdate(
            change = { map ->
                map[KEY_INJECT_EVER_ALIVE] = true.toString()
                map[KEY_INJECT_ALIVE_BOOT] = bootWall.toString()
            },
            refuse = { refuseWrite(KEY_INJECT_EVER_ALIVE, true) },
        )

    /** 四个 public 写法的共同尾巴：读不出就 [refuse]，否则改一个键、整份写回。 */
    private fun write(
        key: String,
        value: String,
        refuse: (String) -> Boolean,
    ): Boolean =
        writeUpdate(
            change = { map -> map[key] = value },
            refuse = { refuse(key) },
        )

    /**
     * 「读 → 改 → 整份写」的那一段 —— **两层串行**，与 `VariableStore.update` 同一条口径：
     *
     *  · `@Synchronized`：同进程两条线程同时进来会各自读到旧表，后写的那次把先写的那次盖掉；
     *  · [withCrossProcessLock]：这份文件在**两个进程**里都会被改（`:ui` 进程写偏好、
     *    宿主主进程写 [KEY_INJECT_EVER_ALIVE]），进程内的锁管不到对方。
     *
     *  不加锁时最典型的一种丢失是「切一下『显示系统应用』，『不再提示』那一版却回到默认」。
     *
     * @param refuse 读不出原文件时怎么收场 —— 各调用方记的那一条日志不同，所以由它给
     */
    @Synchronized
    private fun writeUpdate(
        change: (MutableMap<String, String>) -> Unit,
        refuse: () -> Boolean,
    ): Boolean {
        val file = ConfigStore.files().file(ConfigStore.SLOT_UI)
        return withCrossProcessLock(file, LOCK_LABEL) {
            val text = readTextOrNull() ?: return@withCrossProcessLock refuse()
            val map = parsePrefs(text)
            change(map)
            writeText(map)
        }
    }

    /** 读失败时不写回，只报一声（判据级，用 `w`：`d` 在 release 被编译期消除）。 */
    private fun refuseWrite(
        key: String,
        value: Boolean,
    ): Boolean {
        XLog.w("写界面状态失败：原文件读不出来，不整份覆盖（$key=$value）")
        return false
    }

    /**
     * 读原始文本。
     *
     * @return **读不了返回 null**，与「文件还不存在 = 空串」分开 —— 写方靠这个区分
     *   「可以按空表重建」与「不许覆盖」（见 [writeShowSystemApps]）。
     *
     *  两件事都在 [ModuleFiles.read] 里：只剩 `.bak` 时由 `AtomicFile.openRead()` 先还原
     *  （**不能**先判 `isFile`，那会绕过还原、读到空串，下一次写回就把这份文件里其它键一起抹掉）；
     *  「文件在但读不出」与「文件真不在」分开判（同一个 `FileNotFoundException` 两种来路）。
     */
    private fun readTextOrNull(): String? =
        when (val r = ConfigStore.files().read(ConfigStore.SLOT_UI)) {
            is ModuleFiles.Load.Ok -> {
                r.text
            }

            ModuleFiles.Load.Absent -> {
                //  主文件与 .bak 都不在 = 还没写过 ⇒ 空表（与「读不了」分开，见 KDoc）
                XLog.d("读界面状态：主文件与 .bak 都不在，按空表处理")
                ""
            }

            ModuleFiles.Load.Unreadable -> {
                // 判据级：读失败会让写回被拒（用户看到的是「没保存」），用 w 才能在 release 里看见
                XLog.w("读界面状态失败（按默认处理、且不写回）")
                null
            }
        }

    /** 把改好的表渲染并整份写回；失败由 [ModuleFiles.write] 记日志。 */
    private fun writeText(map: Map<String, String>): Boolean =
        ConfigStore.files().write(
            ConfigStore.SLOT_UI,
            buildString {
                appendLine("# OPlusKey 界面状态 —— 模块自己的显示偏好，不是用户配置（见 ui/state/page/UiState.kt）")
                map.forEach { (key, value) -> appendLine("$key=$value") }
            },
            openReadable = false,
        )
}

/**
 * 跨进程锁的日志主语 —— 与变量文件那一份（`"变量"`）区分开，两边走同一个
 * [withCrossProcessLock]，日志里要能看出是哪一份文件在串行。
 */
private const val LOCK_LABEL = "界面状态"

/**
 * `key=value` 逐行解析；注释 / 空行 / 没有 `=` 的行一律跳过（半截文件也只是少几个键）。
 *
 *  摊在文件级只为不把 [UiState] 顶破 `TooManyFunctions`：它是纯解析，不碰这个对象的状态。
 */
private fun parsePrefs(text: String): MutableMap<String, String> {
    val map = LinkedHashMap<String, String>()
    text.lineSequence().forEach { line ->
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return@forEach
        val i = t.indexOf('=')
        if (i > 0) map[t.substring(0, i).trim()] = t.substring(i + 1).trim()
    }
    return map
}

/**
 * 界面状态的一次读全（见 [UiState.readPrefs]）。
 *
 * @param showUnopenable 「选择应用」页（只有「打开应用」那一趟）是否列出拉不起来的应用
 * @param injectEverAlive 系统那一侧**曾经**回过话（只增不减）；**`null` = 不知情**
 *   （文件里没这个键、或这份文件根本没读出来）—— 与 `false`（明确知道没回过话）不是一件事，
 *   见 `LinkFacts.injectEverAlive` 那一处唯一的折叠点
 * @param injectAliveBoot 上一次回话时的那轮开机（`ProcClock.bootWall()`，0 = 从没回话）
 */
internal class UiPrefs(
    val showSystemApps: Boolean,
    val showUnopenable: Boolean,
    val injectEverAlive: Boolean?,
    val injectAliveBoot: Long,
    /** 「不再提示」过的那个版本号（`null` = 没勾过）。见 [UiState.writeSkipUpdateVersion]。 */
    val skipUpdateVersion: String?,
)

/**
 * 「配置与命令库」那一域的状态 —— 主配置、命令库，以及它们各自的**只读位**。
 *
 *  为什么这两份文件同属一域：它们同生共死 —— 都在建页那次一起读、都由 ⋮ 菜单的
 *  「清空配置」一起重建、都由「导入 / 导出配置」一起换掉；两个只读位也是同一条纪律
 *  （读不懂就整份拒写，绝不拿默认值覆盖用户的东西）。
 */
internal class ConfigState {
    /**
     * 建页那次读盘**回来了没有** —— 页面里的内容一律等这一位翻 true 才组合。
     *
     *  为什么要有这一位：[config] / [commands] 的初值都是「安全的默认值」（主开关关、
     *  三个手势全是无操作、命令库空），而真正的值要读三份文件才拿得到。那三趟读以前是
     *  `SettingsPage.createView()` 里**同步**做的 —— 跑在宿主 Activity 的主线程上。
     *  现在挪到组合期、在 IO 线程上跑（见 `ui/chrome/PageLoad.kt` 的 `loadPageState`），于是
     *  「数据还没到」这一档真实存在了：不拦住的话首帧会先按默认值把「模块未开、全无操作」
     *  的界面画出来，几十毫秒后再翻成真值 —— 用户看到的是开关自己弹一下。
     *
     *  所以 [SettingsPage.Page] 在 false 时**整棵页面树都不组合**（只铺一屏底色），
     *  副作用也就一个都不会跑（接收器注册、链路自检、换页收浮窗）。
     *
     *  另一位用途是写闸：这一位为 false 时 [readOnly] / [commandsReadOnly] 还是初值 false，
     *  而 [config] 只是默认值 —— 这时候任何写回都会把用户的文件覆盖成默认值。
     */
    var loaded by mutableStateOf(false)

    /**
     * 当前配置（页面自己读自己写）。
     *
     * ## 为什么用**引用**相等而不是默认的结构相等
     *
     *  `mutableStateOf` 默认是 `structuralEqualityPolicy`：新值与旧值 `==` 时**直接丢弃新实例**、
     *  连快照都不换。而 [Config] 的结构相等**看不见按键名** —— `KeyId.equals` 只比 `id`
     *  （这是有意的：改名是改显示，不是换一颗键），于是「只改了个名字」的那份新配置与旧配置
     *  `==`，赋值成了空操作：页面继续拿着旧实例，菜单还显示旧名字，本地指纹（`ui/LinkCheck`）
     *  也一直算的是旧那一份 —— 注入侧明明收到了新名字，两边指纹对不上，页面顶着
     *  「设置未同步」；更糟的是下一次写盘会把旧名字写回文件，把用户刚改的名字抹掉。
     *
     *  表现是「给按键改名之后首页报『设置未同步』」—— 文件里已经是新名字，页面拿着旧实例。
     *  配置是不可变快照、每次写盘都换新实例 ⇒ 换实例本来就该重画，用引用相等才是它该有的语义。
     */
    val config = mutableStateOf(Config.DEFAULT, referentialEqualityPolicy())

    /**
     * 建页那次**没读成功**（文件在、但读不了 / 读不懂）⇒ 页面进**只读**：任何写回都被拒。
     *
     * 为什么要有这个开关：默认值长这样 —— 主开关**关**、三个手势全是「无操作」、**不振动**。
     * 读失败时若照常拿它渲染、又照常写回，用户配的东西就被整份替换成这份默认值
     *（真机上真的发生过，先丢的是「振动反馈」）。
     *
     * 退出方式：**重进页面**（会重读一次）；或走 ⋮ 菜单的「默认界面」离开；或点「清空配置」
     * ——那是用户明说要丢掉重来，`resetCurrentProfile` 会先把这一位清掉再写。
     *
     *  全项目只有两处写它：建页按读到的那一态赋值、以及 `resetCurrentProfile` 的放行 —— 别在别处顺手置 false，
     *  那正是这道闸要防的事。
     */
    var readOnly by mutableStateOf(false)

    /**
     * 这次建页的配置是**从 `.bak` 恢复**来的（主文件读不懂，上一次成功写入那份读得懂）。
     *
     *  与 [readOnly] 互斥：[readOnly] 是「一个字都写不回去」，这一位是「写回去正好能把主文件修好」。
     *  它只用来**提示**（那句「最近的改动可能丢了」），不拦任何操作 —— 用户改一次，
     *  恢复出来的这份就落盘了，主文件当场修好。
     *
     *  只在建页时置一次，此后一直为真：这一进程里的配置确实来自备份。
     */
    var configRecovered by mutableStateOf(false)

    /**
     * 命令库的只读位 —— 与 [readOnly] 同一条纪律，管的是**另一份文件**（`CommandLibrary`）。
     *
     *  建页那次读命令库读不懂（外皮解不开 / 被截断 / 正文不是合法 JSON）时置 true：手上那份空表
     *  不是用户的真相，`saveCommands` 一律拒写。少这一位的话，用户随手改一条命令就会把「读不懂」
     *  的那份文件覆盖成只有这一条 —— 用户的命令没了，而且没有任何一步提示过他。
     */
    var commandsReadOnly by mutableStateOf(false)

    /**
     * **自定义命令库**（`core/config/CommandLibrary` 里那一串）—— 界面自己的状态，与 [config] 分开。
     *
     *  命令的**内容**存在命令库里，绑定到手势时写进主配置的只是 `cmd:<名字>` 这个引用
     *（与别的动作一样只是 `plus_single` 里的一个取值，注入侧不需要知道名字背后是什么）。
     *  这份列表变了要重组的是 `ActionCatalog.forGesture` 的调用点（图标表与绑定列表）。
     */
    val commands = mutableStateOf<List<Command>>(emptyList())
}
