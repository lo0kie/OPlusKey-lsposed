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

/** 「后长按」那一族的人话（`单击后长按` / `双击后长按` / `三击后长按`）—— 主档与它的松手共用。 */
private fun clickThenLongLabel(count: Int): String =
    when (count) {
        1 -> "单击后长按"
        2 -> "双击后长按"
        else -> clickLabelOf(count) + "后长按"
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
     * **连点 N 下之后再按住不放** —— 两段式：前 N 击各自抬手，下一击在连击窗口内按下并按住
     *  超过长按阈值。
     *
     *  [count] 是**前面那几击的击数**：1 = 单击后长按（第 2 击按住）、2 = 双击后长按（第 3 击
     *  按住）、3 = 三击后长按（第 4 击按住）。三者是同一件事的三个档位，配置、判定、界面都按
     *  这一位索引 —— 不为每一档另写一套。
     *
     *  与 [Click] / [LongPress] 的区别在**最后一击的形态**：那一击快速抬起 ⇒ 普通多击
     *  （[Click] count+1），按住不放 ⇒ 这一档。所以它与对应的多击档互斥，同一颗键不可能
     *  两档同时命中。
     *
     *  它是「要自己要才有」的档位（`KeyConfig.clickThenLongOn` / `doubleThenLongOn` /
     *  `tripleThenLongOn`），关着时判定侧根本不在那一击上排长按那一枪 —— 单击的等待窗口与
     *  这一位出现之前完全一样。
     */
    data class ClickThenLong(
        val count: Int = 1,
    ) : GestureTrigger

    /**
     * **「后长按」那一档的松手**（`KeyConfig.clickThenLongRelease` 那一族）—— 与 [Release] 同一个意思，
     *  但属于另一条路：那一档有自己的「松手执行额外动作」，不能与长按的松手格共用。
     *
     *  [count] 与 [ClickThenLong.count] **同一个口径**（前面那几击的击数：1 / 2 / 3）——
     *  三档各有自己的一格松手动作，不带这一位就只能读到第 1 击那一份（真机报过：
     *  「x 击后长按会拿单击后长按的松手动作触发」）。
     */
    data class ClickThenLongRelease(
        val count: Int = 1,
    ) : GestureTrigger

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

                is GestureTrigger.ClickThenLong -> {
                    clickThenLongLabel(count)
                }

                is GestureTrigger.ClickThenLongRelease -> {
                    clickThenLongLabel(count) + "松手"
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
         * 按「这颗键开着哪几档多击 / 两段式 / 双击」拼出**现在能用的那几档** —— 界面与场景块排行的唯一入口。
         *
         *  只开四击、不开三击是合法配置：那时清单是「单击 / 双击 / 长按 / 四击」——
         *  第 3 下按了不算数（`actionOf` 回「无操作」），第 4 下照样能派发。
         *
         *  「单击后长按」排在最后：它是另一个维度（第二击的形态），与击数不是一个轴。
         *
         *  双击关着时**连那一页都不排**（`KeyConfig.doubleClickOn`）：与「没开的多击」同一条口径 ——
         *  列一页写进去也永远不会命中。
         *
         * @param on 这个目标开着的多击档位（`KeyConfig.extraClicksOn`）；组合键没有这一档，传 `emptySet()`
         * @param clickThenLong 这个目标开没开「单击后长按」；组合键同样没有，传 `false`
         * @param doubleClick 这个目标开没开双击（`KeyConfig.doubleClickOn`）；组合键没有双击，传 `false`
         */
        fun slotsOn(
            on: Set<Int>,
            doubleClick: Boolean = true,
            thenLongClicks: Set<Int> = emptySet(),
        ): List<GestureTrigger> =
            BASE_SLOTS.filter { (it as? Click)?.count != 2 || doubleClick } +
                HIGHER_CLICKS.filter { (it as Click).count in on } +
                thenLongClicks.sorted().map { ClickThenLong(it - 1) }

        /**
         * 组合键能配的几档 —— 组合没有双击、没有多击（见 `ComboKey` 的类注释）。
         */
        val COMBO_SLOTS: List<GestureTrigger> = listOf(Click(1), LongPress)

        /**
         * **这一档组合**能配的几档 —— [COMBO_SLOTS] 按它有没有长按收口（判据见 [ComboKey.hasLong]），
         *  单成员按住组合再按成员连击的开关接上双击 / 三击 / 四击 / 单击后长按。
         *
         *  界面与场景块排行都从这一支取（「组合键」编辑页的手势带、「场景动作」页、
         *  系统场景里每块的动作行三处）：少一页 / 少一行只能由这一处决定 ——
         *  各写一遍的话，带子上少一档而动作行还在，那一行写进去永远不会命中。
         *
         *  @param member 成员连击开关（`ComboKey` 上那几位）；`null` = 这档不支持成员连击
         *  （不是按住档 / 不止一颗成员），带子上只有短按 / 长按。
         */
        fun comboSlots(
            hasLong: Boolean,
            member: MemberGestures? = null,
        ): List<GestureTrigger> {
            val base = if (hasLong) COMBO_SLOTS else COMBO_SLOTS - LongPress
            if (member == null) return base
            //  成员连击那几档接在短按 / 长按之后：双击 / 第 3 击起 / 后长按（按击数三档）。
            //  （短按 `Click(1)` 与长按已在 [COMBO_SLOTS] 里，不在这里重复。）
            return base +
                (if (member.doubleClickOn) listOf<GestureTrigger>(Click(2)) else emptyList()) +
                HIGHER_CLICKS.filter { (it as Click).count in member.extraClicksOn } +
                member.thenLongClicks.sorted().map { ClickThenLong(it - 1) }
        }

        /**
         * 「长按松手」那一档的 id（广播 / 配置里那一档）。
         *
         *  与三档手势的 id **不冲突**：`Gesture.ofId` 只认 `single` / `double` / `long`。
         */
        const val RELEASE_ID = "release"

        /** 「后长按」主档那一族的基础 id —— N = 1 用它，N ≥ 2 是 `click{N}` + [THEN_LONG_SUFFIX]。 */
        const val CLICK_THEN_LONG_ID = "click_then_long"

        /**
         * 「后长按」的松手那一档的 id —— 三档各一份（`click_then_long_release` /
         * `click2_then_long_release` / `click3_then_long_release`）。
         *
         *  N = 1 沿用 [CLICK_THEN_LONG_RELEASE_ID]：那一档在这一位产生之前就叫这个名字，
         *  改字面量等于让已落盘的场景配置与已发出的广播一起失效。
         */
        const val CLICK_THEN_LONG_RELEASE_ID = "click_then_long_release"

        /** `click{N}_then_long_release`（N ≥ 2）里后缀那一半 —— 与 [THEN_LONG_SUFFIX] 分开写。 */
        const val THEN_LONG_RELEASE_SUFFIX = "_then_long_release"

        /**
         * 「第 N 击后长按」里 N ≥ 2 那几档的 id 后缀（`click2_then_long` / `click3_then_long`）。
         *
         *  N = 1 沿用 [CLICK_THEN_LONG_ID]：那一档在 1.6.0 之前就叫这个名字，改字面量等于让
         *  已落盘的配置与已发出的广播一起失效。
         */
        const val THEN_LONG_SUFFIX = "_then_long"

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

                    CLICK_THEN_LONG_ID -> GestureTrigger.ClickThenLong(1)

                    CLICK_THEN_LONG_RELEASE_ID -> GestureTrigger.ClickThenLongRelease(1)

                    //  `clickN`（n ≥ 3）—— 除三档与那几个具名档之外唯一认的写法。
                    //  三个解析函数**按后缀长的先试**：`click2_then_long_release` 同时满足
                    //  `click` 前缀，先试短后缀会把它拆成认不出的 `2_then_long_release`。
                    else -> thenLongReleaseOf(id) ?: clickOf(id) ?: thenLongOf(id)
                }

        /** `click{N}_then_long_release`（N ≥ 2）—— 与 [thenLongOf] 分开：后缀不同，先试它。 */
        private fun thenLongReleaseOf(id: String?): GestureTrigger? = thenLongOf(id, release = true)

        /** `click{N}_then_long`（N ≥ 2）—— 与 [clickOf] 分开：两种写法的后缀不同，不共用一条解析。 */
        private fun thenLongOf(id: String?): GestureTrigger? = thenLongOf(id, release = false)

        /**
         * `click{N}_then_long` 与 `click{N}_then_long_release` 的共用解析。
         *
         *  @param release `true` = 解析松手那一档（多一段后缀）
         */
        private fun thenLongOf(
            id: String?,
            release: Boolean,
        ): GestureTrigger? {
            val suffix = if (release) THEN_LONG_RELEASE_SUFFIX else THEN_LONG_SUFFIX
            val text =
                id?.takeIf {
                    it.startsWith(CLICK_ID_PREFIX) && it.endsWith(suffix)
                } ?: return null
            val digits = text.removePrefix(CLICK_ID_PREFIX).removeSuffix(suffix)
            val count = digits.toIntOrNull() ?: return null
            //  只认规范写法（`click02_then_long` 这类不认）+ 上限按 [MAX_CLICKS] 收，理由同 [clickOf]。
            if (count !in 2..MAX_CLICKS || digits != count.toString()) return null
            return if (release) {
                GestureTrigger.ClickThenLongRelease(count)
            } else {
                GestureTrigger.ClickThenLong(count)
            }
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

                is GestureTrigger.ClickThenLong -> {
                    if (trigger.count == 1) {
                        CLICK_THEN_LONG_ID
                    } else {
                        CLICK_ID_PREFIX + trigger.count + THEN_LONG_SUFFIX
                    }
                }

                is GestureTrigger.ClickThenLongRelease -> {
                    if (trigger.count == 1) {
                        CLICK_THEN_LONG_RELEASE_ID
                    } else {
                        CLICK_ID_PREFIX + trigger.count + THEN_LONG_RELEASE_SUFFIX
                    }
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
 * **成员连击**那套开关 —— 按住首键、唯一成员键连击时，这几档开没开。
 *
 *  组合键本身没有双击 / 多击（多颗键「再按一遍」没有判据），但**按住组合只有一颗成员**时，
 *  那颗成员键连击就是这一档自己的手势（动作落在 `ComboKey` 的 `memberDouble` /
 *  `memberClicks` / `memberClickThenLong`）。界面按它拼手势带（`GestureTrigger.comboSlots`）。
 *
 *  多成员组合 / 等待组合回 `null`（`ComboKey.memberGestures`）。
 */
data class MemberGestures(
    val doubleClickOn: Boolean,
    /** 开着哪几档成员多击（第 3 击起）。 */
    val extraClicksOn: Set<Int>,
    val clickThenLongOn: Boolean,
    /** 成员「双击后长按」（第 3 击按住）/「三击后长按」（第 4 击）。 */
    val doubleThenLongOn: Boolean = false,
    val tripleThenLongOn: Boolean = false,
) {
    /** 成员那几档「后长按」对应的**按下击数**（2 / 3 / 4）—— 与单键同一套口径。 */
    val thenLongClicks: Set<Int>
        get() =
            buildSet {
                if (clickThenLongOn) add(2)
                if (doubleThenLongOn) add(3)
                if (tripleThenLongOn) add(4)
            }
}

/**
 * 这几档里**有开着的**吗 —— 「成员连击真的会判」的唯一判据。
 *
 *  读者是界面那一侧：按住组合卡上「固定窗口起点 / 等待窗口」两行在**只有一颗成员**时之所以
 *  还有意义，就是因为它们是成员连击的窗口（见 `Config.comboMemberChain`）——
 *  一档都没开时那两行量不到任何东西，得如实置灰。
 */
val MemberGestures.isOn: Boolean
    get() = doubleClickOn || extraClicksOn.isNotEmpty() || thenLongClicks.isNotEmpty()

/**
 * 这一档是不是**某一次按压的收尾**（松手那一刻才发）—— `Release` 与 `ClickThenLongRelease`。
 *
 *  两者都不是「新的一次按压」，而是已经判过的那一按结束时的另一件事：
 *  执行侧据此免掉「过期丢弃」（见 `GestureExec.staleOrDuplicate` 的 `keepLate`）——
 *  积压的按下可以丢，丢一条松手却是「这一按什么都没发生」。
 */
val GestureTrigger.isReleasePhase: Boolean
    get() = this == GestureTrigger.Release || this is GestureTrigger.ClickThenLongRelease

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
        is GestureTrigger.ClickThenLong, is GestureTrigger.ClickThenLongRelease -> {
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
 *  @param slots 真的绑了动作的那几档（没绑的档不该让判定多等一个窗）
 */
data class KeyGesturePlan(
    val clickWindowMs: Int,
    val longPressMs: Int,
    val maxClicks: Int,
    val slots: Set<GestureTrigger>,
) {
    /**
     * 第 [clicks] 击到达之后还要等多久才敢派发（毫秒）；0 = 到达即派发。
     *
     *  口径与判定器现在的实现**逐条对齐**（`inject/input/GestureDetector`）：
     *  1) 最高档到达即派发 —— 双击在第二击那一刻就执行，不等窗；
     *  2) 更低的档要等「连击链闭合」：差几击就多等几个窗；
     *  3) 最高只数到一击时**不等**：判定封顶按动作表算（`judgeCap`），没有第二击要等。
     *     参与组合的那一份等待不在这里 —— 那是 `ComboDetector` 的窗口，与连击链无关。
     *
     *  判定器以后改成读 [KeyGesturePlan] 时，这段就是权威；在那之前它是投影，
     *  **那边改了这里必须跟着改**，否则两边对「等多久」有两份说法。
     */
    fun waitFor(clicks: Int): Long = if (clicks >= maxClicks) 0L else (maxClicks - clicks).toLong() * clickWindowMs

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
 *  「哪几档算绑了」的判据是**动作不是无操作**：绑了 `none` 的档不该让判定多等一个窗。
 */
fun KeyConfig.gesturePlan(): KeyGesturePlan {
    val slots =
        buildSet<GestureTrigger> {
            if (single.isBound) add(GestureTrigger.Click(1))
            //  **双击要看开关**（与 `boundClicks` 同一条）：`doubleClickOn` 关着时执行侧对它回
            //  「无操作」，排进计划只会让判定侧按 2 击封顶、单击白等一个窗口。
            if (doubleClickOn && double.isBound) add(GestureTrigger.Click(2))
            if (long.isBound) add(GestureTrigger.LongPress)
            //  「单击后长按」与双击互斥（第二击按住 vs 快速抬起），但它**不延长**连击链：
            //  判定仍然只数到第 2 击，这一档是在第 2 击按住时另判的。
            if (clickThenLongOn && clickThenLong.isBound) add(GestureTrigger.ClickThenLong(1))
            //  第 3 击起的额外档位（`click3` / `click4` …，见 `KeyConfig.extraClicks`）——
            //  **那一档自己没开时不算**：执行侧对它回「无操作」，排进计划只会让单击白等
            //  （见 `KeyConfig.extraClicksOn`）。
            extraClicks
                .filterKeys { it in extraClicksOn }
                .filterValues { it.isBound }
                .keys
                .forEach { add(GestureTrigger.Click(it)) }
        }
    //  与判定器**同一个数**（[judgeCap]）：那个数按**档位开关**算 —— 双击开着时即使还没绑
    //  动作，判定也数到第 2 击、单击照样等满窗口，这里跟着它才对得上。
    //  计划里另算一遍 `maxOf(slots, clicksForJudge)` 的话，页面上写的「单击最多等 Xms」
    //  会比实际等待短一截。
    return KeyGesturePlan(
        //  **判定器实际用的是 `clickWindowMs`**（它把 `waitMultiWindow` 折进去了）：
        //  这里写 `doubleClickMs` 的话，「每次松手重新起算」那一档页面上报的等待会比实际短。
        clickWindowMs = clickWindowMs,
        longPressMs = longPressMs,
        maxClicks = judgeCap,
        slots = slots,
    )
}

/**
 * 一档组合键的**判定计划** —— 判定侧在第一颗键按下之前就要知道的那几件事。
 *
 *  为什么要单独一层：判定要的不是「两颗」这个数字，而是**这一档的形态描述** ——
 *  成员是谁、相邻两颗最多隔多久、凑齐之后按住多久算长按。有了它，判定侧只回答
 *  「这一串按下是否凑成了某一档」，与「一档到底几颗」解耦；两颗那一档仍走两两配对
 *  （`ComboDetector.pairFor`），3 颗起走多成员凑齐（`multiFor`），两条路都读这份计划。
 *
 *  @param keys 成员（**集合语义**：顺序不是身份，见 `ComboKey` 的类注释）
 *  @param spanMs 这一档**整串**的容忍跨度（最早那一颗到最后一颗）—— 已经按成员数与
 *    「固定窗口起点」展开过，判「凑没凑齐」直接用这一个数（`Config.comboSpanFor`：
 *    开着就是 `wait` 那一格，关着是 `waitPair × (n-1)`）。
 *    为什么不存「相邻两颗隔多久」：那样判定侧与镜像都得再乘一次成员数，而**开着固定窗口起点时
 *    整串只给一个窗口**，乘法表达不了 —— 一个数在两个地方各自展开，口径迟早会分家。
 *  @param longPressMs 凑齐之后一起按住多久算长按（这一档自己的值，不取某个成员键的）
 *  @param mode 这一档的触发方式（`ComboKey.MODE_*`）—— 判定侧按它选判据，日志按它说清
 *    「按住哪一颗才算」；解析时已经由 `ComboKey.modeFor` 夹过（三颗及以上只会是窗口那条），
 *    所以这里不再有「配了但判不出来」的形态
 */
data class ComboPlan(
    val id: String,
    val keys: Set<String>,
    val spanMs: Int,
    val longPressMs: Int,
    val mode: Int,
) {
    /** 成员数 —— 判定凑齐与否、多档候选时挑哪一档，都按它比。 */
    val size: Int get() = keys.size

    /** 判据行：一眼看出「这一档要几颗、怎么触发、等多久」。 */
    override fun toString(): String =
        "$id：${keys.size} 颗（${keys.sorted().joinToString("+")}）/ " +
            "${if (mode == ComboKey.MODE_WAIT) "等窗口" else "先按住第 $mode 颗"} / 跨度 ${spanMs}ms" +
            " / 长按 ${longPressMs}ms"
}

/**
 * 一档组合 → 判定计划（**只读投影**，不改配置、不影响当前判定）。
 *
 *  @param spanMs 由调用方给：`Config.comboSpanFor`（这一档自己的整串跨度）。
 */
fun ComboKey.plan(spanMs: Int): ComboPlan =
    ComboPlan(
        id = id,
        keys = keys.toSet(),
        spanMs = spanMs,
        longPressMs = longPressMs,
        mode = mode,
    )

/**
 * 这份配置里**用得上**的组合各自的判定计划 —— 凑不齐的都不进来。
 *
 *  口径与判定侧同一条（[Config.liveCombos]：自己关着、成员不足数、或任一成员键被停用），
 *  于是「日志里这一档在表里」与「按键时真的会为它等窗口」永远说的是同一件事。
 *
 *  成员数不影响能不能判：手改文件写进来的三颗组合也在表里、也是 `judgeable`，
 *  判定侧（`ComboDetector` 的多成员路径）直接照它凑。
 */
fun Config.comboPlans(): List<ComboPlan> = liveCombos().map { it.plan(comboSpanFor(it)) }
