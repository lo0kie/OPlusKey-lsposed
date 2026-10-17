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
)
