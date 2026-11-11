package com.lookie.opluskey.core.model

/**
 * 第 3 击起那一档的**人话**（`三击` / `四击` …）—— 界面那条手势带上只有两个字的位置，
 *  写「3 击」读起来像编号，写「三击」才是与「单击 / 双击」同一套叫法。
 *
 *  九击之外退回阿拉伯数字：`MAX_JUDGE_CLICKS` 封到 9，再往上只可能是手改的配置，
 *  那已经不是给界面看的档位了。
 */
private fun clickLabelOf(count: Int): String {
    val numeral =
        when (count) {
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "七"
            8 -> "八"
            9 -> "九"
            else -> return "$count 击"
        }
    return numeral + "击"
}

/**
 * 一档手势的**触发条件** —— 自定义手势要落地，档位就得能当数据描述。
 *
 *  现在的档位是 [Gesture] 那个三值枚举（`single` / `double` / `long`），而它在界面、字段表、广播、
 *  判定器、执行器里一共被穷举了七十多处：再往枚举里加一档，这七十处要挨个补齐，下一档再来一遍。
 *  这里把「档」拆成**触发条件**：连击次数是数据，长按是另一条轴；[Gesture] 则退化成它的三个已有取值。
 *
 *  **判定与执行两侧早就在按它走**：判定器读 `clicksForJudge`（= 这里 `maxClicks` 的投影）决定
 *  连击链数到第几下，执行器读 `KeyConfig.actionOf(trigger)` 取动作。界面这一半曾经只有三格 ——
 *  「按键绑定」页现在按 [GestureTrigger] 排页，三击 / 四击是**同一个入口里的另两档**，
 *  与前三档同一张网格、同一条写盘路径。
 */
sealed interface GestureTrigger {
    /** 连击 [count] 下（1 = 单击）。 */
    data class Click(
        val count: Int,
    ) : GestureTrigger

    /** 按住不放 —— 与击数是**两条轴**：长按由长按阈值判，不参与连击链。 */
    data object LongPress : GestureTrigger

    /**
     * 长按之后的**松手**那一刻（`KeyConfig.longRelease` 那一档）。
     *
     *  它不是一次独立手势，也不参与连击链：只有「这一按已经判成长按」才会发出来 —— 判定侧在抬手
     *  时发一条，执行侧据此执行**另一个**动作（与长按那一档分开配置、分开执行）。
     *  没配额外动作时这条根本不发，行为与这一位出现之前完全一致。
     */
    data object Release : GestureTrigger

    /**
     * **单击之后再按住不放**（`KeyConfig.clickThenLong`）—— 两段式：第 1 击抬手之后，
     *  第 2 击在连击窗口内按下并按住超过长按阈值。
     *
     *  与 [Click] / [LongPress] 的区别在**第二击的形态**：第 2 击快速抬起 ⇒ 双击（[Click] 2），
     *  按住不放 ⇒ 这一档。所以它与双击互斥，同一颗键不可能两档同时命中。
     *
     *  它是「要自己要才有」的档位（[KeyConfig.clickThenLongOn]），关着时判定侧根本不在
     *  第 2 击上排长按那一枪 —— 单击的等待窗口与这一位出现之前完全一样。
     */
    data object ClickThenLong : GestureTrigger

    /**
     * **「单击后长按」那一档的松手**（`KeyConfig.clickThenLongRelease`）—— 与 [Release] 同一个意思，
     *  但属于另一条路：那一档有自己的「松手执行额外动作」，不能与长按的松手格共用。
     */
    data object ClickThenLongRelease : GestureTrigger

    /** 配置 / 广播里用的档位 id —— 与 `Gesture.id` 同一套字面量（多击是 `clickN`）。 */
    val id: String get() = idOf(this)

    /** 日志与界面用的人话（三档与 `Gesture.label` 逐字相同）。 */
    val label: String
        get() =
            when (this) {
                GestureTrigger.LongPress -> {
                    Gesture.LONG.label
                }

                GestureTrigger.Release -> {
                    "长按松手"
                }

                GestureTrigger.ClickThenLong -> {
                    "单击后长按"
                }

                GestureTrigger.ClickThenLongRelease -> {
                    "单击后长按松手"
                }

                is Click -> {
                    when (count) {
                        1 -> Gesture.SINGLE.label
                        2 -> Gesture.DOUBLE.label
                        else -> clickLabelOf(count)
                    }
                }
            }

    companion object {
        /** 多击档位 id 的前缀（`click3` / `click4` …）—— 与现有三个字面量不冲突。 */
        const val CLICK_ID_PREFIX = "click"

        /**
         * 多击档位的**上限**（第 9 击）—— 与 `ConfigFields.MAX_JUDGE_CLICKS` 是**同一个数**
         * （这里直接引用它，不另写一份 9）。
         *
         *  超过它的档位一律当成认不出：判定侧数不了那么多次，写着只会让单击白等。
         */
        const val MAX_CLICKS = ConfigFields.MAX_JUDGE_CLICKS

        /**
         * 一颗键**默认就有**的几档 —— 不依赖 `KeyConfig.extraClicksOn`，永远在界面上。
         *
         *  顺序即页序的前三页；[HIGHER_CLICKS] 里开着的接在它后面。
         */
        val BASE_SLOTS: List<GestureTrigger> = listOf(Click(1), Click(2), LongPress)

        /**
         * **多击档位**（第 3 击起）—— **逐档**看 `KeyConfig.extraClicksOn`：开着的才出现在界面上，
         *  也只有在那种时候判定与执行才认它。
         *
         *  为什么单独一份：这几处（分页器页数、按键绑定页的目标、场景块的动作行）都要按**同一颗键
         *  的那几档**拼清单。原先是「一个 KEY_SLOTS 到处读」，开关一进来就得在每个读者那里再判一次
         *  —— 判错一处就是「界面少一页但场景里还能配」这种对不上的状态。
         */
        val HIGHER_CLICKS: List<GestureTrigger> = KeyConfig.DEFAULT_EXTRA_CLICKS.map { Click(it) }

        /**
         * 一颗键能配的**全部**几档 —— 顺序即界面页序，也是「这一块能接管哪几档」的清单。
         *
         *  **它是一个静态的全集**，不代表「现在能看到几档」：界面请走
         *  [slotsOn]（按这颗键开着的那几档拼），别直接拿它去排页。
         *
         *  为什么只到第 4 击：档数越多单击越迟钝（配了四击，单击最坏要等三个窗）。
         *  九击是判定侧的硬上限，不是界面该摊开的量。
         */
        val KEY_SLOTS: List<GestureTrigger> = BASE_SLOTS + HIGHER_CLICKS

        /**
         * 按「这颗键开着哪几档多击 / 两段式」拼出**现在能用的那几档** —— 界面与场景块排行的唯一入口。
         *
         *  只开四击、不开三击是合法配置：那时清单是「单击 / 双击 / 长按 / 四击」——
         *  第 3 下按了不算数（`actionOf` 回「无操作」），第 4 下照样能派发。
         *
         *  「单击后长按」排在最后：它是另一个维度（第二击的形态），与击数不是一个轴。
         *
         * @param on 这个目标开着的多击档位（`KeyConfig.extraClicksOn`）；组合键没有这一档，传 `emptySet()`
         * @param clickThenLong 这个目标开没开「单击后长按」；组合键同样没有，传 `false`
         */
        fun slotsOn(
            on: Set<Int>,
            clickThenLong: Boolean = false,
        ): List<GestureTrigger> =
            BASE_SLOTS +
                HIGHER_CLICKS.filter { (it as Click).count in on } +
                (if (clickThenLong) listOf(ClickThenLong) else emptyList())

        /**
         * 组合键能配的几档 —— 组合没有双击、没有多击（见 `ComboKey` 的类注释）。
         */
        val COMBO_SLOTS: List<GestureTrigger> = listOf(Click(1), LongPress)

        /**
         * 「长按松手」那一档的 id（广播 / 配置里那一档）。
         *
         *  与三档手势的 id **不冲突**：`Gesture.ofId` 只认 `single` / `double` / `long`。
         */
        const val RELEASE_ID = "release"

        /** 「单击后长按」两档的 id —— 同理与 `single` / `double` / `long` / `clickN` 都不冲突。 */
        const val CLICK_THEN_LONG_ID = "click_then_long"
        const val CLICK_THEN_LONG_RELEASE_ID = "click_then_long_release"

        /**
         * 档位 id → 触发条件。
         *
         *  `single` / `double` / `long` 走 [Gesture.ofId]（**字面量只有那一处**），另外认
         *  `click{n}`（n ≥ 3，给多击预留，现在没有任何地方会写它）。
         *
         *  认不出返回 `null`：配置 / 广播里出现不认识的档位时，调用方按「没有这一档」处理 ——
         *  猜成单击的代价是「按一下执行了别的动作」，比不认更糟。
         */
        fun ofId(id: String?): GestureTrigger? =
            Gesture.ofId(id)?.trigger()
                ?: when (id) {
                    RELEASE_ID -> GestureTrigger.Release

                    CLICK_THEN_LONG_ID -> GestureTrigger.ClickThenLong

                    CLICK_THEN_LONG_RELEASE_ID -> GestureTrigger.ClickThenLongRelease

                    //  `clickN`（n ≥ 3）—— 除三档与那几个具名档之外唯一认的写法
                    else -> clickOf(id)
                }

        /** `clickN`（n ≥ 3）—— 除三档与 `release` 之外唯一认的写法。 */
        private fun clickOf(id: String?): GestureTrigger? {
            val digits = id?.takeIf { it.startsWith(CLICK_ID_PREFIX) }?.removePrefix(CLICK_ID_PREFIX) ?: return null
            val count = digits.toIntOrNull() ?: return null
            //  `click03` / `click+3` 这类不认：档位 id 是要落盘、要广播的字面量，只留一种写法。
            //  上限按 [MAX_CLICKS] 收：判定侧只数得到那么多下，再高的档位写了也永远不会命中。
            return if (count in 3..MAX_CLICKS && digits == count.toString()) Click(count) else null
        }

        /** 触发条件 → 档位 id（配置字段后缀、广播里那一档）。 */
        fun idOf(trigger: GestureTrigger): String =
            when (trigger) {
                GestureTrigger.LongPress -> {
                    Gesture.LONG.id
                }

                GestureTrigger.Release -> {
                    RELEASE_ID
                }

                GestureTrigger.ClickThenLong -> {
                    CLICK_THEN_LONG_ID
                }

                GestureTrigger.ClickThenLongRelease -> {
                    CLICK_THEN_LONG_RELEASE_ID
                }

                is GestureTrigger.Click -> {
                    when (trigger.count) {
                        1 -> Gesture.SINGLE.id
                        2 -> Gesture.DOUBLE.id
                        else -> CLICK_ID_PREFIX + trigger.count
                    }
                }
            }
    }
}

/**
 * 这一档是不是**某一次按压的收尾**（松手那一刻才发）—— `Release` 与 `ClickThenLongRelease`。
 *
 *  两者都不是「新的一次按压」，而是已经判过的那一按结束时的另一件事：
 *  执行侧据此免掉「过期丢弃」（见 `GestureExec.staleOrDuplicate` 的 `keepLate`）——
 *  积压的按下可以丢，丢一条松手却是「这一按什么都没发生」。
 */
val GestureTrigger.isReleasePhase: Boolean
    get() = this == GestureTrigger.Release || this == GestureTrigger.ClickThenLongRelease

/**
 * 触发条件 → 现有三档；多击（`Click(n≥3)`）没有对应的 [Gesture]，返回 `null`。
 *
 *  谁需要「三档那一套」（条件分支 `StateRule`、界面文案、旧广播）就在这里退一次，
 *  其余路径一律按 [GestureTrigger] 走。
 */
fun GestureTrigger.gestureOrNull(): Gesture? =
    when (this) {
        GestureTrigger.LongPress -> {
            Gesture.LONG
        }

        //  松手这一档是**另一个槽位**（`KeyConfig.longRelease`），不是三档里任何一档：
        //  条件分支（`StateRule`）按三档判定，把它算成「长按」会取到长按那一档的动作。
        GestureTrigger.Release -> {
            null
        }

        //  同理：「单击后长按」是两段式，不是三档里任何一档（算成长按会取到长按的动作）
        GestureTrigger.ClickThenLong, GestureTrigger.ClickThenLongRelease -> {
            null
        }

        is GestureTrigger.Click -> {
            when (count) {
                1 -> Gesture.SINGLE
                2 -> Gesture.DOUBLE
                else -> null
            }
        }
    }

/** 现有三档 → 触发条件 —— **唯一的互转点**，别在别处再写一份 `when (gesture)`。 */
fun Gesture.trigger(): GestureTrigger =
    when (this) {
        Gesture.SINGLE -> GestureTrigger.Click(1)
        Gesture.DOUBLE -> GestureTrigger.Click(2)
        Gesture.LONG -> GestureTrigger.LongPress
    }

/**
 * 一颗键的**判定计划** —— 判定器在按下之前就必须知道的东西。
 *
 *  为什么非要提前知道：判定是**等多久**的问题。绑了双击，单击就得等一个窗才能排除第二击；
 *  将来绑到三击，单击要等两个窗。这些全是从「这颗键到底配了哪几档」推出来的，判定器自己看不到
 *  配置（注入侧不读配置文件），所以它必须由执行侧随配置一起送过来 [KeyGesturePlan]。
 *
 *  @param clickWindowMs 这一击到下一击的窗口（`KeyConfig.doubleClickMs`，每颗键自己的）
 *  @param longPressMs 长按阈值（与连击链无关）
 *  @param maxClicks 绑了动作的最高击数（0 = 连单击都没绑）
 *  @param instantSingle 「忽视无操作双击」：没绑双击时不为它排窗
 *  @param slots 真的绑了动作的那几档（没绑的档不该让判定多等一个窗）
 */
data class KeyGesturePlan(
    val clickWindowMs: Int,
    val longPressMs: Int,
    val maxClicks: Int,
    val instantSingle: Boolean,
    val slots: Set<GestureTrigger>,
) {
    /**
     * 第 [clicks] 击到达之后还要等多久才敢派发（毫秒）；0 = 到达即派发。
     *
     *  口径与判定器现在的实现**逐条对齐**（`inject/input/GestureDetector`）：
     *  1) 最高档到达即派发 —— 双击在第二击那一刻就执行，不等窗；
     *  2) 更低的档要等「连击链闭合」：差几击就多等几个窗；
     *  3) 只绑了一档（[maxClicks] ≤ 1）时：`instantSingle` 开 ⇒ 不等；关 ⇒ 仍等一窗
     *     （现状如此 —— 那个开关的意思是「别为没绑的双击等」，不是「单击永远不等」）。
     *
     *  判定器以后改成读 [KeyGesturePlan] 时，这段就是权威；在那之前它是投影，
     *  **那边改了这里必须跟着改**，否则两边对「等多久」有两份说法。
     */
    fun waitFor(clicks: Int): Long {
        if (maxClicks <= 1) return if (instantSingle) 0L else clickWindowMs.toLong()
        return if (clicks >= maxClicks) 0L else (maxClicks - clicks).toLong() * clickWindowMs
    }

    /** 单击最坏要等多久（= 连击链走完）—— 界面上「开了双击，单击会迟这么久」那一行读它。 */
    val maxWaitMs: Long get() = waitFor(1)

    /** 判据行：一眼看出「这颗键最多等多久、认哪几档」。 */
    override fun toString(): String {
        val names =
            slots
                .map { GestureTrigger.idOf(it) }
                .sorted()
                .joinToString("/")
                .ifEmpty { "无" }
        return "最高 $maxClicks 击 / 窗 ${clickWindowMs}ms / 单击最多等 ${maxWaitMs}ms / 长按 ${longPressMs}ms / 档 $names"
    }
}

/**
 * 从一颗键的现有配置推出判定计划 —— 只读投影，不改任何配置、不影响当前判定。
 *
 *  「哪几档算绑了」的判据是**动作不是无操作**：绑了 `none` 的档不该让判定多等一个窗
 *  （这正是 `instantSingle` 想解决的事，这里把它一般化到所有档）。
 */
fun KeyConfig.gesturePlan(): KeyGesturePlan {
    val slots =
        buildSet {
            if (single.isBound) add(GestureTrigger.Click(1))
            if (double.isBound) add(GestureTrigger.Click(2))
            if (long.isBound) add(GestureTrigger.LongPress)
            //  「单击后长按」与双击互斥（第二击按住 vs 快速抬起），但它**不延长**连击链：
            //  判定仍然只数到第 2 击，这一档是在第 2 击按住时另判的。
            if (clickThenLongOn && clickThenLong.isBound) add(GestureTrigger.ClickThenLong)
            //  第 3 击起的额外档位（`click3` / `click4` …，见 `KeyConfig.extraClicks`）——
            //  **那一档自己没开时不算**：执行侧对它回「无操作」，排进计划只会让单击白等
            //  （见 `KeyConfig.extraClicksOn`）。
            extraClicks
                .filterKeys { it in extraClicksOn }
                .filterValues { it.isBound }
                .keys
                .forEach { add(GestureTrigger.Click(it)) }
        }
    //  判定要等几个窗以 [clicksForJudge] 为准：它可能高于「绑了动作的最高击数」
    //  （手改配置只写 click3、没写单击时，判定仍要按 3 击的链走）。
    val maxClicks =
        maxOf(slots.filterIsInstance<GestureTrigger.Click>().maxOfOrNull { it.count } ?: 0, clicksForJudge)
    return KeyGesturePlan(
        clickWindowMs = doubleClickMs,
        longPressMs = longPressMs,
        maxClicks = maxClicks,
        instantSingle = instantSingle,
        slots = slots,
    )
}
