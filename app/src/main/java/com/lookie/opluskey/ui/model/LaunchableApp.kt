package com.lookie.opluskey.ui.model

/**
 * 「打开应用」页里的一条 —— 界面要用的几样。
 *
 *  **不带图标**：查列表时不预取（两百多项全量取图标就是两百多次跨进程调用、还会把一批 `Drawable`
 *  钉在内存里），只记包名，真滚到哪一行才由 [AppIcon] 取哪一行，取到的那份记进 LRU。
 */
internal data class LaunchableApp(
    val packageName: String,
    val label: String,
    /**
     * 是不是系统应用（`FLAG_SYSTEM`）——「显示系统应用」开着时拿去画「系统 / 用户」小标签。
     *
     * 用 `FLAG_SYSTEM` 而非「来自 `getInstalledApplications` 那一批」：launcher 图标查询里
     * 本就混着系统应用，按来源分会把它们当用户应用。
     */
    val system: Boolean,
    /**
     * 是不是 Xposed 模块。标签三选一：「模块」压过「系统」压过「用户」——
     * 一个模块同时是系统应用时按模块报。
     */
    val xposed: Boolean,
    /**
     * [AppEntry.resolve] 解得出来入口 —— `app:` 那一步真的拉得起来。
     *
     * 与执行端（[AppLauncher.launchApp]）同一把尺子，拉不起来就在行尾挂「不可打开」提示。
     * 系统应用缺入口的居多，用户应用里也有（被停用 / 非导出入口 / 装了一半）。
     */
    val launchable: Boolean,
    /**
     * 这个名字的**排序键**（罗马化之后的小写 ASCII 字母数字串，见 [sortKeyOf]）。
     *
     *  随模型一起带出来、只算一次：列表按它排序，搜索也按它比（只比原名 / 包名的话，
     *  输入 `shezhi` 搜不到「设置」——名字与包名里都没有这串字母）。
     *  罗马化不便宜，所以既不能放进比较器里每比一次算一次，也不能在过滤时逐条现算。
     */
    val sortKey: String,
    /**
     * 这一份属于哪个用户 —— `0` 是主用户，其余是多开（分身）的 user id。
     *
     *  同一行在列表里的**身份**由 [target] 决定，不是 [packageName]：主用户的微信与分身微信
     *  包名相同，只按包名比会让两行互相顶上（选中态、去重、写回都是）。
     */
    val userId: Int = 0,
) {
    /** 是不是分身（多开槽里的那一份）—— 列表行据此挂那枚「分身」标签。 */
    val clone: Boolean get() = userId != 0

    /**
     * 这一份的**目标写法**（`999@com.tencent.mm`，见 `AppTarget`）—— 选中态与写回都用它。
     *
     *  **不带名字**：名字是显示信息、单独存在配置里（`Config.appNames`），不再拼进目标。
     *  以前拼进来，每个读它的地方都得先按 `|` 切一刀，漏一处就退化成包名。
     */
    val target: String get() =
        com.lookie.opluskey.core.model.AppTarget
            .format(packageName, userId)
}

/**
 * 分身那一份的显示名 —— `微信 1`（`slot` 由 `AppCloneCatalog.slotOf` 给，`0` = 主用户，不加编号）。
 *
 *  选择应用页的行名与「打开应用」格的小标题**都调这一处**：两处各拼一套的话，网格里的
 *  「微信 1」与列表里的「微信 1」可能指向不同用户。
 *
 *  @param slot 槽号：`> 0` 就是它，`0` = 主用户；
 *    [AppCloneCatalog.UNKNOWN_SLOT]（负数）= **查不到槽号**（那个分身已经删了，或者那个 user
 *    根本不是分身类型）—— 这时改用 `· user id`（`微信 · 998`），**不能**退回不带编号的
 *    `微信`：那与真·主用户那一行同名，一眼分不出是谁。
 */
internal fun cloneLabel(
    base: String,
    slot: Int,
    userId: Int,
): String =
    when {
        slot > 0 -> "$base $slot"
        userId != 0 -> "$base · $userId"
        else -> base
    }
