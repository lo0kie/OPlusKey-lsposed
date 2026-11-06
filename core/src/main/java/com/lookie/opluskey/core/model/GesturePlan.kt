package com.lookie.opluskey.core.model

/**
 * 一档手势的**触发条件** —— 自定义手势要落地，档位就得能当数据描述。
 *
 *  现在的档位是 [Gesture] 那个三值枚举（`single` / `double` / `long`），而它在界面、字段表、广播、
 *  判定器、执行器里一共被穷举了七十多处：再往枚举里加一档，这七十处要挨个补齐，下一档再来一遍。
 *  这里把「档」拆成**触发条件**：连击次数是数据，长按是另一条轴；[Gesture] 则退化成它的三个已有取值。
 *
 *  **本阶段只定义数据，不产生任何新行为**：判定器仍按 [Gesture] 判、执行器仍按 [Gesture] 派发，
 *  没有第四档可配、也没有任何一处界面读这里。接进判定器要补的只有一件事 —— 让判定侧在按下之前
 *  拿到 [KeyGesturePlan]（窗口与最高击数），否则「这颗键配到三击」判定器根本不知道，
 *  单击也就不知道该多等一窗。
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
     *  没配松手动作时这条根本不发，行为与这一位出现之前完全一致。
     */
    data object Release : GestureTrigger

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

                is Click -> {
                    when (count) {
                        1 -> Gesture.SINGLE.label
                        2 -> Gesture.DOUBLE.label
                        else -> "$count 击"
                    }
                }
            }

    companion object {
        /** 多击档位 id 的前缀（`click3` / `click4` …）—— 与现有三个字面量不冲突。 */
        const val CLICK_ID_PREFIX = "click"

        /**
         * 「长按松手」那一档的 id（广播 / 配置里那一档）。
         *
         *  与三档手势的 id **不冲突**：`Gesture.ofId` 只认 `single` / `double` / `long`。
         */
        const val RELEASE_ID = "release"

        /**
         * 档位 id → 触发条件。
         *
         *  `single` / `double` / `long` 走 [Gesture.ofId]（**字面量只有那一处**），另外认
         *  `click{n}`（n ≥ 3，给多击预留，现在没有任何地方会写它）。
         *
         *  认不出返回 `null`：配置 / 广播里出现不认识的档位时，调用方按「没有这一档」处理 ——
         *  猜成单击的代价是「按一下执行了别的动作」，比不认更糟。
         */
        fun ofId(id: String?): GestureTrigger? {
            Gesture.ofId(id)?.let { return it.trigger() }
            if (id == RELEASE_ID) return GestureTrigger.Release
            return clickOf(id)
        }

        /** `clickN`（n ≥ 3）—— 除三档与 `release` 之外唯一认的写法。 */
        private fun clickOf(id: String?): GestureTrigger? {
            val digits = id?.takeIf { it.startsWith(CLICK_ID_PREFIX) }?.removePrefix(CLICK_ID_PREFIX) ?: return null
            val count = digits.toIntOrNull() ?: return null
            //  `click03` / `click+3` 这类不认：档位 id 是要落盘、要广播的字面量，只留一种写法
            return if (count >= 3 && digits == count.toString()) Click(count) else null
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
            if (single !is Action.None) add(GestureTrigger.Click(1))
            if (double !is Action.None) add(GestureTrigger.Click(2))
            if (long !is Action.None) add(GestureTrigger.LongPress)
            //  第 3 击起的额外档位（手改 / 导入配置才可能有，界面没有入口）
            extraClicks.filterValues { it !is Action.None }.keys.forEach { add(GestureTrigger.Click(it)) }
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
