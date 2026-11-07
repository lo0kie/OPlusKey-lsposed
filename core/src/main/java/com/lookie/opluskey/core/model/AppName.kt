package com.lookie.opluskey.core.model

// 「一个绑定该显示成什么名字」—— 全项目**唯一**一条优先级。
//
// 这句话有两个进程各答一次（`:ui` 的 `AppNaming`、宿主里的 `ActionNotice`），因为它们能查到的
// 东西不一样：界面那份有 `Config`、有一份查名字的缓存；宿主那份只有现读的配置和 `PackageManager`。
// 跨进程合不成一个函数 —— 但**顺序不该有两份**。
//
// 原先两处各写了一遍，靠注释声明「逐档对齐」。实际顺序是反的：界面把「落盘时记下的名字」摆在
// `PackageManager` 前面（理由是「冷启动清单还没回来」），宿主摆在后面（理由是「现查更准」）。
// 两个理由都成立，但它们不可能同时成立 —— 于是同一个应用在两个地方可能显示两个名字。
//
// 现在顺序在这里，两侧只回答「这一档你取到什么」：`fromSystem` 现查、`fromSaved` 查落盘那份。
// 另外两档（老配置后缀、包名）不需要外部信息，由这里自己切。

/**
 * **应用名**的优先级 —— 四条，从强到弱。
 *
 *  1. **现查系统**（`PackageManager`）—— 最准：应用改过名、换过图标包都跟得上，而且不依赖
 *     配置有没有落盘。这也是唯一一条「两个进程都能做到」的档；
 *  2. **落盘时记下的名字**（`Config.appNames`）—— 应用**已经卸载**时只剩它有值（PM 查不到，
 *     但用户明明是从来装着的那些里挑的）。它排在现查之后：应用还在的时候，现查永远更新；
 *  3. **老配置塞在 token 末尾的 `|名字`** —— 旧写法落下的，只读不写，用户重新挑一次就迁到第 2 档；
 *  4. **包名** —— 兜底，永远有值（手改配置 / 包被卸载且没记过名字）。
 *
 * ## 为什么「落盘那份」不排第一
 *
 *  第 2 档的名字是**绑定那一刻**抄下来的。应用后来改了名，它就成了旧文；而 `PackageManager`
 *  每次问都是当下的答案。界面那边原先把它排第一，理由是「冷启动直接进页、清单还没查回来时
 *  只有它有名字」—— 但 `appLabelOf` 走的是 `PackageManager` 的同步查询，与「清单有没有回来」
 *  无关，那条理由不成立。真正需要第 2 档的只有「应用已卸载」这一种情况，而那种情况下
 *  `fromSystem` 给 `null`，顺序自然落到它。
 *
 * @param target 绑定的目标写法（`999@包名`、`999@包名/类名`、`999@包名/快捷方式id`，可带 `|名字`）
 * @param fromSystem 现查系统名；**查不到必须给 `null`** —— 给包名会让下面三档全都走不到
 * @param fromSaved 查落盘那份（键是 [AppTarget.identity]，见 `Config.appName`）；没有给 `null`
 */
inline fun resolveAppName(
    target: String,
    fromSystem: (pkg: String) -> String?,
    fromSaved: (identity: String) -> String?,
): String {
    fromSystem(AppTarget.pkg(target))?.takeIf { it.isNotBlank() }?.let { return it }
    fromSaved(AppTarget.identity(target))?.takeIf { it.isNotBlank() }?.let { return it }
    AppTarget.label(target)?.takeIf { it.isNotBlank() }?.let { return it }
    return AppTarget.pkg(target)
}

/**
 * **目标自己的名字**（组件名 `LauncherUI` / 快捷方式名 `扫一扫`）—— 与 [resolveAppName] 是两件事。
 *
 *  界面那边这一段的顺序是「这一趟刚挑中的草稿 → 落盘那份 → 老配置后缀」，宿主那边没有草稿
 *  （它不在挑应用的现场），只有后两档。差别只有 [draft] 这一个入参，所以收进同一个函数。
 *
 *  取不到给 `null` —— 调用方据此决定「不补括号」还是「退回类名最后一段」（见 `appTargetLabel`
 *  的三段拼法）。这里不替它决定：组件与快捷方式的退路不一样（快捷方式没有类名可退）。
 *
 * @param draft 这一趟刚挑中、还没落盘的名字（只有「选择应用」页头有）
 * @param fromSaved 同 [resolveAppName]
 */
inline fun resolveTargetName(
    target: String,
    draft: String?,
    fromSaved: (identity: String) -> String?,
): String? {
    draft?.takeIf { it.isNotBlank() }?.let { return it }
    fromSaved(AppTarget.identity(target))?.takeIf { it.isNotBlank() }?.let { return it }
    return AppTarget.label(target)?.takeIf { it.isNotBlank() }
}
