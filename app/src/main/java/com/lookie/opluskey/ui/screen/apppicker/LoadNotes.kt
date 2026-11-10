package com.lookie.opluskey.ui.screen.apppicker

/**
 * 一次查询的**附注** —— 目前只有两件：快捷方式那一节是不是没查出来、以及它是不是还在等回包。
 *
 *  为什么不并进返回值：清单本身是 `List<LaunchableActivity>?`（null = 整次失败），
 *  而「界面查到了、快捷方式没查到」是第三种状态。编码成哨兵行或改返回类型，会让每个
 *  只关心清单的调用点都跟着改；而它唯一的用处是「别把 `appshortcut:` 降级成 `app:`」
 *  （见 [resolveSelection]）。
 *
 *  落在自己这个文件里：[ActivityList] 那一份是**一组查询函数**（按主题命名），它是其中一个
 *  小对象；两者塞在同一个文件里时「文件名该跟谁」这件事没有正确答案（detekt 的
 *  `MatchingDeclarationName` 只认「一个文件一个声明」那一档）。
 */
internal class LoadNotes {
    /** 快捷方式那一节这次**没查出来**（读不到）—— 判据同旧版，见 [resolveSelection]。 */
    var shortcutsFailed = false
}
