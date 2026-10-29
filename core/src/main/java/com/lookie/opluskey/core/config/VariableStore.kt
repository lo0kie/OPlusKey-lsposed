package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.ProcClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.VarType
import com.lookie.opluskey.core.model.Variable
import com.lookie.opluskey.core.model.VariableName
import com.lookie.opluskey.core.model.Vars
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile

/**
 * 自定义变量的**持久化** —— 自己一个槽（`opluskey_vars.conf`），与主配置 / 命令库同一层外皮。
 *
 * ## 为什么单独一份文件
 *
 *  变量的值会被**命令**改（`setvar:`），而主配置是界面上改一次、整份重写一次的东西：
 *  混在一起的话，每按一次「+1」都要把整份配置重写一遍、还要发一次配置广播 —— 代价与风险
 *  （写坏配置）都远超「记一个数」。分开之后这一份只装变量，写得再频繁也只影响自己。
 *
 * ## 变量跟着配置走
 *
 *  多配置切换时**一起换**：一份配置 = 主配置 + 命令库 + 变量表（见 [ProfileVars]）。
 *  现在这一份（`vars` 槽）永远是**当前生效**配置的那套变量 —— 宿主按键时读的也是它，
 *  所以切完配置，绑定与它们读写的那几个值是一致的。
 *
 * ## 持久化 + 开机重置
 *
 *  · 值一变就原子写盘（[ModuleFiles.write]），进程被杀 / 手机重启都不会退回去；
 *  · 文件里额外记一个 [BOOT] 标记（`ProcClock.bootWall()`，开机那一刻的 wall clock）；
 *    宿主进程起来时调 [applyBootResets]，标记变了（= 这台机器重启过）才把**带 `resetOnBoot`**
 *    的那几颗拉回初始值。标记跟着每次写盘更新，所以同一次开机里反复起进程不会重复重置。
 *
 * ## 读不懂时不动
 *
 *  与主配置同一条纪律：文件在、但读不出来（权限 / 截断 / 手改坏）时 [apply] / [applyBootResets]
 *  **一个字节都不写** —— 否则一次偶发读失败就会把用户攒的变量全抹成空表。
 */
object VariableStore {
    /** 文件里的格式版本（认不出就整份不认，不猜）。 */
    private const val VERSION = 1

    private const val KEY_VERSION = "version"

    /** 开机标记（wall clock，老文件只有这一项；见 [bootId] 与类注释）。 */
    private const val KEY_BOOT = "boot"

    /** 开机指纹（`/proc/sys/kernel/random/boot_id`）—— 与墙上时钟无关，见 [bootId]。 */
    private const val KEY_BOOT_ID = "boot_id"
    private const val KEY_VARS = "vars"
    private const val KEY_NAME = "name"
    private const val KEY_TYPE = "type"
    private const val KEY_INITIAL = "initial"
    private const val KEY_VALUE = "value"
    private const val KEY_RESET_ON_BOOT = "reset_on_boot"

    private val json = Json { prettyPrint = true }

    /** 变量文件的全部内容：那几颗变量 + 写这一份时的开机标记。 */
    data class Snapshot(
        val vars: List<Variable>,
        /** 写盘那一刻的开机时间戳（`ProcClock.bootWall()`）；0 = 老文件没这一项。 */
        val boot: Long,
        /**
         * 写盘那一刻的开机指纹；空串 = 老文件没有这一项（或本机读不到 `/proc/.../boot_id`）。
         *
         *  判「这一轮开机跑过重置没有」**先看它**：wall clock 会被 NTP / 用户改时间平移，
         *  拿它当判据等于「对一次时就重置一次计数器」（见 [bootId]）。
         */
        val bootId: String = "",
    )

    /** 读的结果三态 —— 与主配置同一套说法（见 `ModuleFiles.Load` 的类注释）。 */
    sealed interface Load {
        data class Ok(
            val snapshot: Snapshot,
        ) : Load

        /** 还没写过（首装 / 从没用过变量）—— 允许直接建一份新的。 */
        data object Absent : Load

        /** 文件在、但读不出来 / 读不懂 —— **不许**按空表写回。 */
        data object Unreadable : Load
    }

    /**
     * 读全部变量（带指纹缓存，理由与 `ConfigStore.load` 一样：执行侧每条引用都要读一次）。
     *
     *  只缓存「内容推出来的结论」（读到 / 读不懂），IO 失败不缓存 —— 那是瞬时故障。
     */
    fun load(): Load {
        val f = ConfigStore.files().file(ConfigStore.SLOT_VARS)
        val modified = f.lastModified()
        val length = f.length()
        val inode = inodeOf(f)
        cache?.let {
            //  `inode != 0` 是必要条件：主文件与 `.bak` 都不在时三个值一起是 0，只比三个数会把
            //  「已经不存在」判成命中（与 `ConfigStore.Cached` 同一条）
            val sameFile = it.modified == modified && it.length == length && it.inode == inode
            if (inode != 0L && sameFile) return it.result
        }
        val result =
            when (val r = ConfigStore.files().read(ConfigStore.SLOT_VARS)) {
                ModuleFiles.Load.Absent -> {
                    //  d：首装（从来没建过变量）是正常状态，执行侧每按键都会走到
                    XLog.d("读变量：文件不在，按没有变量处理")
                    return Load.Absent
                }

                ModuleFiles.Load.Unreadable -> {
                    XLog.w("读变量失败（文件在但读不出来）⇒ 按读不懂处理，不写回")
                    return Load.Unreadable
                }

                is ModuleFiles.Load.Ok -> {
                    val text = FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, r.text)
                    text?.let(::decode)?.let { Load.Ok(it) } ?: Load.Unreadable
                }
            }
        cache = Cached(modified, length, inode, result)
        return result
    }

    /** 执行侧要用的那一份（读不到 / 读不懂都给空表：变量不该让命令整条失败）。 */
    fun cached(): Vars =
        when (val l = load()) {
            is Load.Ok -> Vars(l.snapshot.vars)
            Load.Absent, Load.Unreadable -> Vars()
        }

    /**
     * 写一份变量（顺带把开机标记更新成这一次）。失败只记日志、返回 `false`。
     *
     *  `openReadable = false`：只有宿主这一侧（`:ui` 与宿主主进程，同 uid）读写它，
     *  注入侧（system_server）不碰变量 —— 它的执行请求仍然只走配置广播。
     *
     * @param slot 写哪一槽。默认就是**当前生效**那一槽（`vars`）；[ProfileVars.save] 会给
     *  别的配置那一槽落一份空表 —— 那条路与当前配置无关，所以只在写当前槽时清读缓存。
     */
    fun save(
        vars: List<Variable>,
        slot: String = ConfigStore.SLOT_VARS,
    ): Boolean {
        val text = FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, encode(vars, ProcClock.bootWall()))
        val ok = ConfigStore.files().write(slot, text, openReadable = false)
        if (!ok) {
            XLog.w("写变量失败：${vars.size} 颗（槽 $slot），这一次改动没落盘")
        } else if (slot == ConfigStore.SLOT_VARS) {
            cache = null
        }
        return ok
    }

    /**
     * 按一条 `setvar:` 动作改一个变量（读 → 改 → 写）。
     *
     * @return 真的落盘了吗。变量不存在 / 类型不符 / 值归一不了都不写（原因进日志）
     */
    fun apply(set: Action.VarSet): Boolean =
        update { vars ->
            val next = Vars(vars).assign(set)
            if (next == null) {
                XLog.w("变量：${set.name} 这次改不了（不存在 / 类型不符 / 值不行）：${set.op.symbol}${set.value}")
                return@update null
            }
            next.all
        }

    /**
     * 新建一颗（读 → 追加 → 写）。
     *
     *  同名已存在时不写 —— 同名的两颗永远只有第一颗会被引用。
     *
     * @return 真的落盘了吗
     */
    fun add(variable: Variable): Boolean =
        update { vars ->
            if (vars.any { it.name == variable.name }) {
                XLog.w("变量：新建被拒（已有同名）—— ${variable.name}")
                return@update null
            }
            vars + variable
        }

    /**
     * 删掉一颗（读 → 摘掉 → 写）。
     *
     *  不存在时不写 —— 那种情况本来就没有要改的东西，写一遍只是白改一次开机标记。
     *
     * @return 真的落盘了吗
     */
    fun remove(name: String): Boolean =
        update { vars ->
            if (vars.none { it.name == name }) {
                XLog.w("变量：删除没落地 —— $name 已经不在了")
                return@update null
            }
            vars.filterNot { it.name == name }
        }

    /**
     * **开机后跑一次**：这台机器重启过（开机标记变了）就把带 `resetOnBoot` 的变量拉回初始值。
     *
     *  同一次开机里反复调用是幂等的（第一次就把标记更新了）。
     *
     * @return 标记成功落到盘上了吗
     */
    fun applyBootResets(): Boolean {
        val loaded = load()
        if (loaded !is Load.Ok) {
            //  没写过 ⇒ 还没有变量；读不懂 ⇒ 不许写回（见类注释）
            return false
        }
        //  **先看 boot_id，再看 wall clock**：对时 / 改系统时间会平移 wall clock，拿它当唯一判据
        //  就会「对一次时重置一次计数器」；boot_id 只在内核重启时变（见 [bootIdOf]）。
        //  老文件没有 boot_id 时退回 wall clock 判这一次 —— 写盘之后这份文件就带上了。
        val now = bootIdOf()
        val sameBoot =
            if (now.isNotEmpty() && loaded.snapshot.bootId.isNotEmpty()) {
                loaded.snapshot.bootId == now
            } else {
                loaded.snapshot.boot == ProcClock.bootWall()
            }
        if (sameBoot) return true
        val before = loaded.snapshot.vars
        val after = before.map { if (it.resetOnBoot) it.reset() else it }
        val ok = save(after)
        if (ok) {
            XLog.w("变量：开机重置完成（${after.count { it.resetOnBoot }} 颗回到初始值，开机指纹 ${now.ifEmpty { "（无，按时刻判）" }}）")
        }
        return ok
    }

    /**
     * 「读出来 → 改 → 写回去」的一段 —— [apply] / [add] / [remove] 都走它。
     *
     *  读不懂时**直接收手**（`false`），一个字都不写：那正是「不许按默认值写回」那条纪律。
     *  调用方给 `null`（这一改不成立）时同样不写。
     *
     *  ## 两层串行
     *
     *   · `@Synchronized`：同进程两条线程同时进来会互相把对方的改动盖掉（后写的那条带着先读的
     *     旧表），先在本进程里排好队；
     *   · [withCrossProcessLock]：**变量文件有两个写方**（`:ui` 进程与宿主主进程，同 uid），
     *     进程锁管不到对方 —— 界面里点「加一」与侧键触发 `setvar` 撞在一起时，仍然会丢一次改动。
     *     所以再拿一把文件锁。
     */
    @Synchronized
    private fun update(change: (List<Variable>) -> List<Variable>?): Boolean =
        withCrossProcessLock {
            val vars =
                when (val l = load()) {
                    is Load.Ok -> {
                        l.snapshot.vars
                    }

                    Load.Absent -> {
                        emptyList()
                    }

                    Load.Unreadable -> {
                        XLog.w("变量：这一份读不出来，这次改动不落地")
                        return@withCrossProcessLock false
                    }
                }
            val next = change(vars) ?: return@withCrossProcessLock false
            save(next)
        }

    /** 解析一份变量文件；版本不对 / 结构不对返回 `null`（= 读不懂）。坏条目单独丢掉。 */
    private fun decode(raw: String): Snapshot? {
        val root =
            try {
                json.parseToJsonElement(raw).jsonObject
            } catch (e: Throwable) {
                XLog.w("读变量：解出来不是 JSON：${e.message}")
                return null
            }
        val version = root.int(KEY_VERSION)
        if (version != VERSION) {
            XLog.w("读变量：版本 $version ≠ 本版 $VERSION，按读不懂处理")
            return null
        }
        val boot = root.long(KEY_BOOT)
        val bootId = root.str(KEY_BOOT_ID)
        //  **缺 `vars` 段 = 读不懂**，不是「空表」：文件被截断 / 手改 / 别的工具生成时会缺这一段，
        //  而 `update` 那套「读不懂一个字都不写」正是靠这里判出来的 —— 返回空表 + Ok 的话，
        //  下一次 `setvar` 就会拿这份空表写回去，用户攒的变量无声消失（与版本不对那一路同一口径）
        val list =
            root[KEY_VARS] as? kotlinx.serialization.json.JsonArray ?: run {
                XLog.w("读变量：文件里没有 $KEY_VARS 段，按读不懂处理（不拿空表覆盖）")
                return null
            }
        //  一条一条解析，任何一项不对就丢这一条（不猜、不改写；类型认不出时**不退回默认类型**
        //  —— 那等于替用户改了他定的类型）
        val vars =
            list.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = VariableName.normalize(obj.str(KEY_NAME)) ?: return@mapNotNull null
                val type = VarType.fromId(obj.str(KEY_TYPE)) ?: return@mapNotNull null
                val initial = type.coerce(obj.str(KEY_INITIAL)) ?: return@mapNotNull null
                //  当前值不合法（手改坏了）⇒ 退回初始值：类型没变、数据也不至于整条丢掉
                val value = type.coerce(obj.str(KEY_VALUE)) ?: initial
                val reset = (obj[KEY_RESET_ON_BOOT] as? JsonPrimitive)?.booleanOrNull ?: false
                Variable(name, type, initial, value, reset)
            }
        if (vars.size != list.size) {
            XLog.w("读变量：${list.size - vars.size} 颗名字 / 类型 / 值不合法，已丢弃（文件可能被手改过）")
        }
        return Snapshot(vars, boot, bootId)
    }

    /** 序列化：信封版本 + 开机标记 + 变量表（缩进两格，与别的配置同一套写法）。 */
    private fun encode(
        vars: List<Variable>,
        boot: Long,
    ): String =
        json
            .encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put(KEY_VERSION, VERSION)
                    put(KEY_BOOT, boot)
                    put(KEY_BOOT_ID, bootIdOf())
                    put(
                        KEY_VARS,
                        buildJsonArray {
                            vars.forEach { v ->
                                add(
                                    buildJsonObject {
                                        put(KEY_NAME, v.name)
                                        put(KEY_TYPE, v.type.id)
                                        put(KEY_INITIAL, v.initial)
                                        put(KEY_VALUE, v.value)
                                        put(KEY_RESET_ON_BOOT, v.resetOnBoot)
                                    },
                                )
                            }
                        },
                    )
                },
            ) + "\n"

    /** 指纹缓存 —— 与 `ConfigStore.Cached` 同一个口径（修改时间 + 长度 + inode）。 */
    private class Cached(
        val modified: Long,
        val length: Long,
        val inode: Long,
        val result: Load,
    )

    @Volatile
    private var cache: Cached? = null
}

/**
 * 三个「从 JSON 里宽容取值」的小工具 —— 放在文件顶层而不是 [VariableStore] 里：
 * 它们是纯函数，而那个对象的函数条数已经贴着 detekt 的 `TooManyFunctions` 那一档。
 *
 *  「宽容」是有意的：变量文件可能被手改过，某一项类型不对时按「没有」处理，由调用方决定丢不丢。
 *
 *  **`JsonNull` 必须单独判**：`"name": null` 解出来是一个 `JsonPrimitive`（`JsonNull`），
 *  它的 `content` 是字符串 `"null"` —— 不挡的话会造出一颗**名字就叫 `null`** 的变量，
 *  然后谁也认不出它是从哪儿来的。
 */
private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content.orEmpty()

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

/**
 * 本轮开机的**指纹** —— `/proc/sys/kernel/random/boot_id`（内核每次开机生成一个新的 UUID）。
 *
 *  为什么不拿 wall clock 当指纹（`ProcClock.bootWall()`）：NTP 对时 / 用户改系统时间会让那个值
 *  整体平移，看起来就像「换了一轮开机」—— 于是一次对时把 `resetOnBoot` 的计数器全拉回初始值，
 *  而用户什么都没做。boot_id 只在内核重启时变，和不看时钟。
 *
 *  读不到（极少数内核配置）时返回空串，调用方退回 wall clock —— 那一路有误判风险，但好过完全不判。
 */
private fun bootIdOf(): String =
    try {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    } catch (e: Throwable) {
        XLog.w("变量：读不到 boot_id（${e.javaClass.simpleName}），开机判据退回墙上时钟")
        ""
    }

/** 跨进程锁文件的后缀（见 [withCrossProcessLock]）—— 不以 `.conf` 结尾，不会被当成一个槽。 */
private const val VAR_LOCK_SUFFIX = ".lock"

/**
 * 读改写的**跨进程**互斥 —— 锁文件是变量文件旁边那个 `.lock`。
 *
 *  拿不到锁时**不阻塞功能**：记一行日志、照常做这一次（宁可极小概率丢一次改动，
 *  也不要在按键路径上卡住）。锁文件不以 `.conf` 结尾，不会被 [ModuleFiles.slots] 当成一个槽。
 */
private fun <T> withCrossProcessLock(block: () -> T): T {
    val lockFile = File(ConfigStore.files().file(ConfigStore.SLOT_VARS).path + VAR_LOCK_SUFFIX)
    val channel =
        try {
            RandomAccessFile(lockFile, "rw").channel
        } catch (e: Throwable) {
            XLog.w("变量：跨进程锁打不开（${e.javaClass.simpleName}），这一次不串行")
            return block()
        }
    return channel.use { ch ->
        val lock =
            try {
                ch.lock()
            } catch (e: Throwable) {
                XLog.w("变量：拿不到跨进程锁（${e.javaClass.simpleName}），这一次不串行")
                return@use block()
            }
        try {
            block()
        } finally {
            runCatching { lock.release() }
        }
    }
}
