package com.lookie.opluskey.core.model

/**
 * **ROM 键码族表** —— ColorOS 把「一颗物理键」拆成好几个码，这张表说明哪些码属于同一颗键、
 * 每个码代表哪个手势。
 *
 * ## 为什么必须有这张表（而不是「收集时把几个码都收下来」）
 *
 *  收集期只能拿到**码**，拿不到「这个码是哪个手势」：用户按单击报 767、按双击报 769，
 *  模块手上只有两个数字，没有映射就不知道该在双击时派发什么（猜「先按的算单击」会被
 *  用户的按压顺序打脸）。而恰恰是这类键**一次按压只报一个码**，靠时长等第二次按下也等不到。
 *  所以「码 → 手势」这条知识只能由代码提供，收集补不上。
 *
 * ## 为什么单独一个文件，而不是写在 [KeyId] 里
 *
 *  [KeyId] 管的是「一颗键的身份」（id / 名字 / 认哪些码）；**这张表是 ROM 适配知识**，
 *  跟着 ROM 版本走、与身份无关。分开之后：
 *   · 加一族新键 = 在这个文件里加一段，不碰身份类；
 *   · 谁想知道「ColorOS 有哪些多码键」，只看这一个文件。
 *
 * ## 加一族的门槛
 *
 *  只收**有依据**的族：`oplus-framework.jar` 里 `android.view.OplusKeyEvent` 的常量名 + 值，
 *  最好再加一条真机日志。**不许凭码值相邻猜**（相邻不代表同族，灵犀触控 670~694 就是
 *  一堆互不相干的分区码）。全部实测结论见 `docs/device-findings.md` 二十九。
 */
object RomKeyFamilies {
    /**
     * 压感拍照键（ROM 代号 SPRUCE）的整族码。
     *
     *  `android.view.OplusKeyEvent` 的 `SPRUCE_*` 常量：
     *  765 轻按 / 766 重按 / 767 短按 / 768 长按 / 769 双击 / 770 上滑 / 771 下滑 /
     *  772 真实手指动作 / 773 启动相机。
     *
     *  `134`（`KEYCODE_F4`）是这颗键在**按键布局表**里的原始码（按住时上报，见
     *  `docs/device-findings.md` 二十八），也放进族里：不认它的话按住那一下照样漏给系统。
     */
    private val SPRUCE_CODES: Set<Int> = setOf(134, 765, 766, 767, 768, 769, 770, 771, 772, 773)

    /**
     * 族里那几个**ROM 已经把手势判好**的码 → 手势。
     *
     *  这几个码不能喂给 `GestureDetector` 的时长判定：ROM 判成双击时**只发 769 一个码**
     *  （不会先发两次 767），靠时长等第二次按下永远等不到 ⇒ 只能按码直接派发。
     *
     *  `768` 这一条是**防御性**的：框架里有 `SPRUCE_PRESS_LONG_KEYCODE` 这个常量，但这颗键
     *  实测**不产生「按住」那一段**（按住 3 秒也只有一对间隔 1~7ms 的 DOWN/UP，没有 repeat，
     *  见 `docs/device-findings.md` 二十八），用户机日志里也从没出现过 768。
     *  留着它只是因为「常量存在、万一某个 ROM 版本真发」；**不代表长按在这颗键上可用**。
     */
    private val SPRUCE_GESTURES: Map<Int, Gesture> =
        mapOf(
            767 to Gesture.SINGLE,
            768 to Gesture.LONG,
            769 to Gesture.DOUBLE,
        )

    /** 所有已知族 —— 加一族就在这里加一项（`familyOf` 只查这里，不另留第二份清单）。 */
    private val FAMILIES: List<Set<Int>> = listOf(SPRUCE_CODES)

    /** 族里出现过的**全部**码（拍平一次留在这里，别每次问都重算）。 */
    private val ALL_CODES: Set<Int> = FAMILIES.flatten().toSet()

    /**
     * 这个码所属的整族；不属于任何族返回 `null`（普通键：一个码就是一颗键）。
     *
     *  收集到族里任何一个码，认领集合就要扩成整族（调用点在 `KeyId.custom`）——
     *  只认一个码的后果是其余手势的码全部漏给系统，由系统执行它自己的默认动作。
     */
    fun familyOf(code: Int): Set<Int>? = FAMILIES.firstOrNull { code in it }

    /**
     * 这个码是不是 ROM 已经判好手势的那一档 —— `null` = 不是（走时长判定那条路）。
     *
     *  调用点只有注入侧的认领路径（`SideKeyInterceptor`）：认下之后直接派发，不再喂判定器。
     */
    fun gestureOf(code: Int): Gesture? = SPRUCE_GESTURES[code]

    /**
     * 这个码属于已知族、但 ROM **没给**它手势 —— 认下之后应当吞掉、不派发。
     *
     *  轻按 765 / 重按 766 / 上滑 770 / 下滑 771 / 真实手指 772 / 启动相机 773 / 按住 134
     *  都是按压过程中的**中间态**：喂给时长判定器会按「按下 + 抬手」补判一次单击，于是
     *  一次中间形态按压就执行了绑定的动作；放行给系统又会让 ROM 自己去拍照。两条都不对，只吞。
     */
    fun isNonGestureMember(code: Int): Boolean = familyOf(code) != null && gestureOf(code) == null

    /**
     * 这个码属于任何已知族 —— 注入侧据此拦住「把族码写成 `keyevent:` 动作」。
     *
     *  族码是**模块认领**的码（见 `KeyId.custom` 把认领集合扩成整族）。注入一个自己认领的码，
     *  事件会再走一遍认领路径：`isSynthetic` 只认 [KeyId.INJECTABLE_CODES] 那张表，族码不在里面，
     *  于是认领 → 执行 → 再注入，在 system_server 里成环。
     */
    fun isFamilyCode(code: Int): Boolean = code in ALL_CODES
}
