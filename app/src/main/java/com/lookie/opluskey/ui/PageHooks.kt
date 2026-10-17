package com.lookie.opluskey.ui

/**
 * hook 侧（`ui/hook/`）在**建页之前 / 建页之后**塞给页面的那几件东西 —— 一条宿主标题 + 六个回调。
 *
 *  为什么单列一个对象：这些位以前是七个平铺在 `SettingsPage` 上的 `var`，赋值在 `SettingsPageHooks`
 *  一处、调用散在六处。它们的共同点是「**页面自己实现不了、必须由 hook 侧给**」（开 SAF、
 *  露出宿主原版页、重刷状态栏、注册返回回调）—— 摊开时这条界线看不出来，容易被当成页面自己的状态。
 *
 *  注意：全部可空 / 有默认值 —— 页面在没有 hook 侧的情况下（单测、调试通道直接建页）也要能跑。
 */
internal class PageHooks {
    /** 首页标题 —— 宿主 Activity 的原文，hook 侧在建页之前赋值。 */
    var homeTitle: String = "快捷键"

    /** 系统深浅变化回调（给 hook 重刷状态栏配色）。 */
    var onSystemThemeChanged: (() -> Unit)? = null

    /**
     * 「这一刻还要不要自己接返回」变了（进出子页 / 浮层开合）—— 由 `PageEffects` 在组合期回报，
     * hook 侧据此在宿主上注册 / 注销返回回调（见 `SettingsPage.needsBackInterception`）。
     *
     *  **只有需要时的那一档才值得注册**：注册了系统就不做关页动画了，而首页那一退正需要它。
     */
    var onBackInterceptionChanged: (() -> Unit)? = null

    /** 菜单里「打开自带的快捷键界面」—— 由 hook 侧实现（藏模块页面、露出宿主原版页面）。 */
    var onOpenStock: (() -> Unit)? = null

    /** 「导出日志」—— hook 侧实现：弹系统的「保存到」选择器，写完再回报。 */
    var onExportLog: (() -> Unit)? = null

    /**
     * 「更多 → 导出配置」—— hook 侧实现：弹系统的「保存到」选择器，把主配置 + 命令库写成一份文件。
     *
     * 与 [onExportLog] 同一条 SAF 路（结果只能从宿主的 `onActivityResult` 收）。
     */
    var onExportConfig: (() -> Unit)? = null

    /** 「更多 → 导入配置」—— hook 侧实现：弹系统的「打开」选择器，读回文件再写进配置与命令库。 */
    var onImportConfig: (() -> Unit)? = null
}
