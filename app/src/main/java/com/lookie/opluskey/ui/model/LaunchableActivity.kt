package com.lookie.opluskey.ui.model

/**
 * 「这个应用的哪一个界面」—— 选择应用页第二级的一行。
 *
 * 与 [LaunchableApp] 同一套思路：**只装界面要用的那几样**，`PackageManager` 的查询结果不进模型
 * （`ActivityInfo` 带着 `ApplicationInfo`、`metaData` 等一大坨，留在模型里等于让界面层持有 PM 对象）。
 *
 * @property target 写进配置的**完整目标写法**（`包名/类名`，带 `999@` 分身前缀时也一并带上）。
 *   它就是这一行的身份：选中态比较、去重、以及 `onPick` 交出去的都用它，不要再从别的字段拼。
 * @property name 完整类名（`com.tencent.mm.ui.LauncherUI`）—— 写日志与排错用。
 * @property label 展示名：活动自己声明了 `android:label` 就用它，否则用 [summary]
 *   （`ActivityInfo.loadLabel` 在没有自己的 label 时**回落成应用名**，一列全是同一个名字，读不出区别）。
 * @property summary 行尾那行小字：去掉包名前缀的类名（`ui.LauncherUI`）—— 与执行端
 *   `AppEntry.intentOf` 认的写法同源，用户对着它手改配置文件也认。
 * @property launcher 带 `MAIN` + `LAUNCHER`（桌面图标那一个）—— 排最前面。
 * @property mainAction 带 `MAIN`（不一定是启动图标，比如电视版入口）—— 排在其余之前。
 * @property sortKey 名字的排序键（罗马化之后的小写 ASCII 串，见 `sortKeyOf`）：
 *   排序与搜索共用，拼音搜得到汉字名。
 * @property shortcutId 非空 = 这一行是**应用的快捷方式**（扫一扫 / 收付款），不是界面。
 *   此时 [target] 里的 `包名/…` 是快捷方式 id 而不是类名，写回时要走 `appshortcut:` 那一档。
 */
internal data class LaunchableActivity(
    val target: String,
    val name: String,
    val label: String,
    val summary: String,
    val launcher: Boolean,
    val mainAction: Boolean,
    val sortKey: String,
    val shortcutId: String? = null,
)
