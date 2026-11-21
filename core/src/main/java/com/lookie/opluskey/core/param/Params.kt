package com.lookie.opluskey.core.param

import com.lookie.opluskey.core.model.Action

/**
 * 一个**设置项**的声明 —— 全项目唯一的「加一项设置」入口。
 *
 *  为什么收成这样：加一项设置原先要同步改六处（运行时字段、`OwnField` 枚举、`withField` 的
 *  `when`、DTO 字段、Codec 读写、广播的 `Field`），漏一处就是「界面配得出来、存下去没了」。
 *  现在它只是本表里的一条：落盘键、默认值、类型、哪几层有它，全在这一处。
 *
 *  **[id] 与老落盘键名逐字相同**（`click_then_long_on` 这类 snake_case）：迁移因此不需要一张
 *  「新名 → 老名」的映射表 —— 老文件的每一个键值对都可以按 id 直接搬进值包。
 *
 *  **它只管「值」**。手势动作不走这里（动作是 `Map<GestureTrigger, Action>`，档位天然是键），
 *  这样加一档手势不需要新增任何 `Param`。
 */
sealed class Param<T>(
    /** 落盘键 / 广播列 / 日志名 —— 三者同一份，改它就是改落盘口径。 */
    val id: String,
    /** 出厂值；值包里缺这一项时读它。 */
    val default: T,
) {
    abstract fun encode(value: T): String

    /** 认不出的值返回 `null`（调用方回落到默认值，不抛）。 */
    abstract fun decode(raw: String): T?

    internal open fun isDefault(value: T): Boolean = value == default
}

/** 布尔开关。编码 `1` / `0`（与老文件一致）。 */
class BoolParam(
    id: String,
    default: Boolean,
) : Param<Boolean>(id, default) {
    override fun encode(value: Boolean): String = if (value) "1" else "0"

    override fun decode(raw: String): Boolean? =
        when (raw) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
}

/**
 * 整数。落盘时按 [range] 钳一次 —— 手改配置写超范围时不该让判定侧收到越界值。
 *
 *  [range] 与界面滑杆两端、出厂值同源：范围写在这一处，界面与读取侧不必各自再钳。
 */
class IntParam(
    id: String,
    default: Int,
    val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) : Param<Int>(id, default) {
    override fun encode(value: Int): String = value.coerceIn(range).toString()

    override fun decode(raw: String): Int? = raw.toIntOrNull()?.coerceIn(range)
}

/** 动作 token（`Action.None` 即「没配」）。 */
class ActionParam(
    id: String,
    default: Action = Action.None,
) : Param<Action>(id, default) {
    override fun encode(value: Action): String = value.token

    override fun decode(raw: String): Action = Action.parse(raw)
}

/**
 * **三态布尔**：`null` = 「没自己的意见，跟随公共那一份」。
 *
 *  只在组合那几格用（`sceneOnStarter` / `starterKeepLong` / `fixedWindowStart` /
 *  `holdFixedWindowStart`）—— 与 `BoolParam` 分开是为了让「没设过」与「显式关」在值包里
 *  是两件事：前者缺键、后者写着 `0`。合成一张 `BoolParam` 会把这个区别压平，
 *  而它正是「组合这两档用公共的还是自己的」的判据。
 */
class OptBoolParam(
    id: String,
    default: Boolean? = null,
) : Param<Boolean?>(id, default) {
    override fun encode(value: Boolean?): String =
        when (value) {
            null -> ""
            true -> "1"
            false -> "0"
        }

    override fun decode(raw: String): Boolean? =
        when (raw) {
            "" -> null
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
}

/** 纯文本（提示模板那一类）。 */
class TextParam(
    id: String,
    default: String,
) : Param<String>(id, default) {
    override fun encode(value: String): String = value

    override fun decode(raw: String): String = raw
}

/**
 * 一组小整数的集合（「开着哪几档多击」）。编码 `3,4`，升序、去重。
 *
 *  与「每个击数一个布尔」的差别：集合本来就表达了「中间档可以空着」（只开 3、4 而不开 2 是
 *  合法配置），拆成布尔反而要再写一遍「哪几档参与」的清单。
 */
class SetParam(
    id: String,
    default: Set<Int> = emptySet(),
) : Param<Set<Int>>(id, default) {
    override fun encode(value: Set<Int>): String = value.sorted().joinToString(",")

    override fun decode(raw: String): Set<Int>? =
        if (raw.isBlank()) {
            emptySet()
        } else {
            raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
}

/**
 * 一份值包：`Param.id → 字符串`。
 *
 *  它是**结构字段之外的一切设置**。好处是「加一项设置」不需要改落盘结构：缺键 = 出厂默认，
 *  所以老文件天然读得上来、新文件被旧版本读也不会炸（旧版本只是看不见它）。
 *
 *  **写入时与默认值相同的项会被移除**（[with]）：这一条让「显式设成默认」与「没设」同义 ——
 *  两层兜底（独立 > 公共 > 出厂）判「这一层设没设过」靠的就是「包里有没有这个键」。
 *
 *  值包不可变；所有写操作返回新实例。
 */
@JvmInline
value class Params(
    private val values: Map<String, String> = emptyMap(),
) {
    /** 读一项；缺键或值认不出时回落到出厂值（回读失败不当故障，见 `docs/` 的降级纪律）。 */
    operator fun <T> get(spec: Param<T>): T = getOrNull(spec) ?: spec.default

    /** 这一项在本包里有没有**显式设过**（两层兜底的判据）。 */
    fun <T> getOrNull(spec: Param<T>): T? = values[spec.id]?.let { spec.decode(it) }

    fun has(spec: Param<*>): Boolean = values.containsKey(spec.id)

    /** 写一项；写成出厂值时把它从包里删掉（见类注释）。 */
    fun <T> with(
        spec: Param<T>,
        value: T,
    ): Params =
        if (spec.isDefault(value)) {
            Params(values - spec.id)
        } else {
            Params(values + (spec.id to spec.encode(value)))
        }

    /** 把另一份叠上来（后者优先）—— 迁移与「公共 → 独立」的复制都用它。 */
    fun merge(other: Params): Params = Params(values + other.values)

    /** 只留 [prefix] 前缀之外的那些（按前缀清一族的项，见 `ModelParams.dropFamily`）。 */
    fun withoutPrefix(prefix: String): Params = Params(values.filterKeys { !it.startsWith(prefix) })

    /** 落盘 / 广播用的原始形态。 */
    fun toMap(): Map<String, String> = values

    /**
     * 按注册表**归一**：等于出厂值的项从包里删掉，认不出的键**原样留着**。
     *
     *  为什么两头都要过这一道：写出时是全量写（含默认值，见 `encodeFrom`），读回来若原样收下，
     *  同一份配置在「写出 → 读回」之后会从「空包」变成「一堆等于默认值的键」—— `equals` 判不等，
     *  往返测试直接红，而用户侧的表现是「配置文件每存一次就长大一圈」。
     *  **认不出的键要留着**：那可能是更新版本写的这一项，本版不认识但不能替用户删掉。
     */
    fun normalized(specs: List<Param<*>>): Params {
        if (values.isEmpty()) return this
        val byId = specs.associateBy { it.id }
        return Params(
            values.filterKeys { key ->
                val spec = byId[key] ?: return@filterKeys true
                !spec.isDefaultRaw(values[key].orEmpty())
            },
        )
    }

    companion object {
        val EMPTY: Params = Params()

        /** 从原始键值重建（落盘 / 广播读回来的那一份）。 */
        fun of(raw: Map<String, String>): Params = Params(raw)
    }
}

/**
 * **全量序列化用**：把这一项在 [params] 里的当前值编出来（缺键时编的是出厂值）。
 *
 *  为什么不让 `Params` 自己遍历：`Params` 不知道有哪些项 —— 表在 `KeyParams.ALL` 这类注册表里。
 *  写出时必须全量（含默认值），否则「按当前 DTO 重编码算 md5」这类口径会假红，
 *  而假红的后果是「存量配置被判读不懂」。
 */
fun Param<*>.encodeFrom(params: Params): String {
    @Suppress("UNCHECKED_CAST")
    val self = this as Param<Any?>
    return self.encode(params[self])
}

/** 这一项在**原始字符串**形态下等不等于出厂值（`normalized` 用；认不出的值不算默认）。 */
internal fun Param<*>.isDefaultRaw(raw: String): Boolean {
    @Suppress("UNCHECKED_CAST")
    val self = this as Param<Any?>
    return self.decode(raw)?.let { self.isDefault(it) } ?: false
}
