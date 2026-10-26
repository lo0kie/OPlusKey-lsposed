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

/**
 * 自定义变量的**持久化** —— 自己一个槽（`opluskey_vars.conf`），与主配置 / 命令库同一层外皮。
 *
 * ## 为什么单独一份文件
 *
 *  变量的值会被**命令**改（`setvar:`），而主配置是界面上改一次、整份重写一次的东西：
 *  混在一起的话，每按一次「+1」都要把整份配置重写一遍、还要发一次配置广播 —— 代价与风险
 *  （写坏配置）都远超「记一个数」。分开之后这一份只装变量，写得再频繁也只影响自己。
 *
 * ## 变量不跟配置档走
 *
 *  多配置切换时**不动**变量：它是运行期状态（计数器 / 开关位），不是「这套绑定怎么配」的一部分。
 *  切换配置回来时看到的还是当前的计数，这才符合直觉。
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

    /** 开机标记（见类注释）。 */
    private const val KEY_BOOT = "boot"
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
     * 写全部变量（顺带把开机标记更新成这一次）。失败只记日志、返回 `false`。
     *
     *  `openReadable = false`：只有宿主这一侧（`:ui` 与宿主主进程，同 uid）读写它，
     *  注入侧（system_server）不碰变量 —— 它的执行请求仍然只走配置广播。
     */
    fun save(vars: List<Variable>): Boolean {
        val text = FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, encode(vars, ProcClock.bootWall()))
        val ok = ConfigStore.files().write(ConfigStore.SLOT_VARS, text, openReadable = false)
        if (ok) {
            cache = null
        } else {
            XLog.w("写变量失败：${vars.size} 颗，这一次改动没落盘")
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
        val boot = ProcClock.bootWall()
        if (loaded.snapshot.boot == boot) return true
        val before = loaded.snapshot.vars
        val after = before.map { if (it.resetOnBoot) it.reset() else it }
        val ok = save(after)
        if (ok) {
            XLog.w("变量：开机重置完成（${after.count { it.resetOnBoot }} 颗回到初始值，开机标记 $boot）")
        }
        return ok
    }

    /**
     * 「读出来 → 改 → 写回去」的一段 —— [apply] 与将来的「恢复初始值」都走它。
     *
     *  读不懂时**直接收手**（`false`），一个字都不写：那正是「不许按默认值写回」那条纪律。
     */
    private fun update(change: (List<Variable>) -> List<Variable>?): Boolean {
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
                    return false
                }
            }
        val next = change(vars) ?: return false
        return save(next)
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
        val list = root[KEY_VARS] as? kotlinx.serialization.json.JsonArray ?: return Snapshot(emptyList(), boot)
        val vars = list.mapNotNull(::decodeOne)
        if (vars.size != list.size) {
            XLog.w("读变量：${list.size - vars.size} 颗名字 / 类型 / 值不合法，已丢弃（文件可能被手改过）")
        }
        return Snapshot(vars, boot)
    }

    /**
     * 一条变量 —— 任何一项不对就丢这一条（不猜、不改写）。
     *
     *  类型认不出时**不退回某个默认类型**：那等于替用户改了他定的类型。
     */
    private fun decodeOne(element: kotlinx.serialization.json.JsonElement): Variable? = (element as? JsonObject)?.let(::toVariable)

    /** 一条变量（[decodeOne] 的第二段）—— 拆出来只为把 return 条数压回 detekt 那一档。 */
    private fun toVariable(obj: JsonObject): Variable? {
        val name = VariableName.normalize(obj.str(KEY_NAME)) ?: return null
        val type = VarType.fromId(obj.str(KEY_TYPE)) ?: return null
        val initial = type.coerce(obj.str(KEY_INITIAL)) ?: return null
        //  当前值不合法（手改坏了）⇒ 退回初始值：类型没变、数据也不至于整条丢掉
        val value = type.coerce(obj.str(KEY_VALUE)) ?: initial
        val reset = (obj[KEY_RESET_ON_BOOT] as? JsonPrimitive)?.booleanOrNull ?: false
        return Variable(name, type, initial, value, reset)
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
 */
private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
