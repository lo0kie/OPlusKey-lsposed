package com.lookie.opluskey.core.model

/**
 * 「执行时提示」的**落点** —— 界面上那个菜单的四档，与执行侧分岔的同一个枚举。
 *
 *  四档共用一个读法（[of]）：配置里存的是三颗布尔（`toastOnRun` / `toastNotify` / `toastFluid`），
 *  它们的老底子是「开不开 + 走不走流体云」两位；加「通知」这一档时没有再拆一位新语义，而是
 *  补一颗 [NOTIFY] 用的布尔，四档一律从这三颗**派生**。派生只许写在这里
 *  —— 界面菜单、公共设置页、独立设置卡、宿主执行侧读的都是这一个函数，别处再拼一次就会出现
 *  「菜单显示通知、执行时弹 Toast」那类对不上。
 *
 *  为什么不把它存成一个字段：两位布尔已经在配置、广播、独立设置三处铺开，换成单字段是一次
 *  跨模块搬家（键名、md5、迁移全动），而这一轮要的只是多一档。等哪天要加第五档，再一起收。
 */
enum class NoticeMode(
    /** 存进配置 / 日志里的写法（与界面无关）。 */
    val id: String,
) {
    /** 不提示：什么都不发，「提示文案」那一行也不显示。 */
    NONE("none"),

    /** 通知：在通知中心留一条（可回看），同一段正文反复替换同一条。 */
    NOTIFY("notify"),

    /** Toast：AOSP 那条，宿主身份弹，几秒后自己消失。 */
    TOAST("toast"),

    /**
     * 流体云：ColorOS 的状态栏胶囊。**依赖「使用 ColorOS 接口」** —— 那一项关着时由执行侧回落
     * [TOAST]（胶囊是 Seedling 卡片，只有已注册的 seedling 宿主发得出去）。
     */
    FLUID("fluid"),
    ;

    companion object {
        /** 出厂 / 新装默认：不提示（与配置里三颗布尔的默认值一致）。 */
        val DEFAULT: NoticeMode = NONE

        /**
         * 三颗布尔 → 四档。**唯一出口**。
         *
         *  优先级「开不开 → 通知 → 流体云」：`toastOnRun` 关着时另外两颗一律不算数（界面上它们
         *  也都不显示）；两颗同时为真的那一档只可能是手改配置文件写出来的，按 [NOTIFY] 收。
         */
        fun of(
            on: Boolean,
            notify: Boolean,
            fluid: Boolean,
        ): NoticeMode =
            when {
                !on -> NONE
                notify -> NOTIFY
                fluid -> FLUID
                else -> TOAST
            }

        /** 认一个 id（配置 / 日志里的写法）；认不出返回 `null`（调用方自己决定兜底档）。 */
        fun fromId(id: String?): NoticeMode? {
            val text = id?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == text }
        }
    }

    /**
     * 折算成**这次真的能用的落点**：[FLUID] 依赖「使用 ColorOS 接口」（胶囊是 Seedling 卡片，
     * 只有已注册的 seedling 宿主发得出去），那一项关着时回落 [TOAST]。
     *
     *  唯一出口 —— 执行侧两处（就地发的那条、命令输出那条）都从这里拿，别处再判一次就会出现
     *  「菜单显示流体云、实际弹 Toast，但日志说走的是流体云」那种对不上。
     */
    fun effective(colorOsApi: Boolean): NoticeMode = if (this == FLUID && !colorOsApi) TOAST else this
}
