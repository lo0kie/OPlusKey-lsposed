package com.lookie.opluskey.ui.screen.apppicker

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.icu.text.Transliterator
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.shortcutIdEscape
import com.lookie.opluskey.core.model.shortcutTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.target.LaunchableActivity

// 「选择界面」里**快捷方式那一节**的取数、判据与合并 —— 从 `ActivityList.kt` 分出来。
//
// 两个方向：那边回答「这个应用能打开哪些界面」（`PackageManager` + `ActivityInfo`），这边回答
// 「这个应用注册了哪些快捷方式」（`LauncherApps` + `ShortcutInfo`，再加一趟系统身份）。
// 取数来源、失败形态（`null` = 读不到）与排查路径都不同。

/** 明细里最多列几条（超过就只报条数）—— 一次十几行的清单是排查料，不该无上限地铺。 */
private const val SHORTCUT_LOG_MAX = 12

/** 三种档位各自的查询位 —— 单类问那一趟要用（合并位与单类位在这一层未必走同一条路）。 */
private val SHORTCUT_KINDS =
    listOf(
        "动态" to LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
        "清单" to LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
        "钉住" to LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
    )

/** 只问「钉住」那一档 —— 「添加到桌面」的快捷方式全在这里。 */
private val SHORTCUT_KINDS_PINNED = listOf(SHORTCUT_KINDS[2])

/**
 * 这个应用的**快捷方式** —— 走 `LauncherApps.getShortcuts`（不是 `ShortcutManager`：那个只看得见
 * **自己这个包**的快捷方式）。
 *
 *  权限：系统侧那道闸是 `hasShortcutHostPermission`，非桌面应用要靠 `ACCESS_SHORTCUTS`
 *  （宿主声明并已 grant，真机实测过）。拿不到就**这一节一条不列**并记一行 `w` —— 不是「这个应用没有」，
 *  是「我们读不到」，两件事别混。
 *
 * ## 两趟拼起来
 *
 *  本进程这一趟只读得到 `动态 / 清单`；**钉住的那一档**是按钉它的桌面存的，宿主不是桌面宿主
 *  一条也拿不到，换成 `system_server` 身份走同一个 API 也一样（`probe/shortcuts` 实测
 *  `动态 3 / 清单 0 / 钉住 0`）。所以本进程读完之后**再问一趟注入侧** —— 那边读的是快捷方式
 *  服务手上的活数据（见 `inject/ShortcutRead`），两批按 id 去重合并（[mergePinned]）。
 *
 *  那一趟是跨进程往返（几十到几百毫秒），`loadActivities` 整段都等在 IO 上；页面那边因此在
 *  `loaded == null` 期间铺**整页转圈**，不会先列一半、再自己多几行（见 `ActivityPickerList`）。
 *
 *  @return 查不到（应用一个都没注册）返回**空清单**（有效结论）；读不到返回 **`null`**
 *    —— 后者要能区分出来，否则页面会把「这次没查到」当成「那一项已经没了」，
 *    把用户绑的 `appshortcut:` 静默降级成 `app:`。
 */
internal suspend fun SettingsPage.shortcutsOf(
    pkg: String,
    userId: Int,
    romanizer: Transliterator?,
    /** 这一趟的附注：注入侧那趟**没回包**时也要算「这一节没查全」（见 [mergePinned]）。 */
    notes: LoadNotes? = null,
): List<LaunchableActivity>? {
    val launcherApps =
        try {
            context.getSystemService(LauncherApps::class.java)
        } catch (e: Throwable) {
            XLog.w("取 LauncherApps 失败", e)
            null
        }
    if (launcherApps == null) {
        //  这一档原来**静默返回**：服务拿不到时页面只是少一节，日志里一条都没有 ——
        //  而那正是「快捷方式一节凭空消失」最省事的一种解释，得让它自己说出来。
        XLog.w("快捷方式：$pkg（user=$userId）取不到 LauncherApps 服务 ⇒ 这一节不列")
        return null
    }
    val query =
        LauncherApps
            .ShortcutQuery()
            .setPackage(pkg)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
    val shortcuts =
        try {
            launcherApps.getShortcuts(query, AppCloneCatalog.handleOf(userId)).orEmpty()
        } catch (e: Throwable) {
            XLog.w("查快捷方式失败：$pkg（user=$userId，宿主要持有 ACCESS_SHORTCUTS）", e)
            return null
        }
    //  三类一起问、一条都没问到 ⇒ **逐类再问一次**：合并位问不出来而单类问得出来，说明是这一层
    //  `getShortcuts` 对合并位的处理有问题（ROM 改过这一层时见过）；逐类的结果同时进日志，
    //  「缺的是哪一类」与「哪一步坏了」就分得开了。这一趟只在这种情况跑，常态一次也不多问。
    //
    //  另一条：**动态/清单有、钉住一条都没有**时，单独再问一次钉住。合并位与单类位在这一层未必
    //  走同一条路；「添加到桌面」那种正是钉住档，它缺席时最该确认一次（见 [mergeKinds]）。
    val found =
        when {
            shortcuts.isEmpty() -> mergeKinds(launcherApps, pkg, userId, SHORTCUT_KINDS)
            shortcuts.none { it.isPinned } -> mergeKinds(launcherApps, pkg, userId, SHORTCUT_KINDS_PINNED, shortcuts)
            else -> shortcuts
        }
    //  判据行就在这一层打：它手里有「查到了什么」与「系统让不让查」两样，
    //  上层只拿得到一串结论（多少条），回答不了「为什么少一条」。
    logShortcuts(pkg, userId, launcherApps, found)
    val local =
        found.map { info ->
            // 本 SDK 里 `getId()` 非空，直接取（旧版本标的是可空，真为空的快捷方式本来也起不来）
            val id = info.id
            val label =
                info.shortLabel.toString().takeIf { it.isNotBlank() }
                    ?: info.longLabel.toString().takeIf { it.isNotBlank() }
                    ?: id
            shortcutRow(pkg, userId, id, label, romanizer)
        }
    //  桌面上钉的那些：本进程 `LauncherApps` 那一路对非桌面宿主一条也不给（换系统身份走同一个 API
    //  也一样），只有注入侧（`system_server`）读快捷方式服务手上的活数据才拿得到 —— 见 `ShortcutPull`。
    //  与本进程读到的那批按 id 去重：同一条快捷方式两处都会出现，重复的以本进程那份为准。
    //
    //  **那趟没回包（没 root / 超时）不等于「桌面没钉」**：这时本进程那份清单是**不全**的，
    //  已绑的 `appshortcut:` 很可能就在缺的那几条里 —— 必须让上层知道，否则它会把这个绑定
    //  降级成 `app:`（下次写回就真的丢了）。
    val pinned = ShortcutPull.pull(context, pkg, userId)
    if (pinned == null) notes?.shortcutsFailed = true
    return mergePinned(local, pinned, pkg, userId, romanizer)
}

/**
 * 一条快捷方式 → 列表里的那一行。
 *
 *  目标写法与界面那几行同一套（`包名/id|名字`）：`/` 后面是 id 不是类名，靠 `shortcutId` 区分，
 *  写回时走 `appshortcut:` 那一档。小字放 id —— 快捷方式之间就靠它区分（名字可能重）。
 */
private fun shortcutRow(
    pkg: String,
    userId: Int,
    id: String,
    label: String,
    romanizer: Transliterator?,
): LaunchableActivity =
    LaunchableActivity(
        target = shortcutTarget(pkg, id, userId),
        name = "$pkg/${shortcutIdEscape(id)}",
        label = label,
        summary = id,
        sortKey = sortKeyOf(label, romanizer),
        shortcutId = id,
    )

/**
 * 把**桌面上**读到的那几条补进来 —— 按 id 去重，本进程那份优先。
 *
 *  @param desktop `null` = 这一趟没读到（没 root / 复制失败 / 库结构不认识）；空表 = 桌面上没有它的
 */
private fun mergePinned(
    local: List<LaunchableActivity>,
    pinned: List<Pair<String, String>>?,
    pkg: String,
    userId: Int,
    romanizer: Transliterator?,
): List<LaunchableActivity> {
    if (pinned == null) return local.sortedBy { it.sortKey }
    val seen = local.mapNotNullTo(HashSet(local.size * 2)) { it.shortcutId }
    val added = ArrayList<LaunchableActivity>(pinned.size)
    for ((id, label) in pinned) {
        if (seen.add(id)) added += shortcutRow(pkg, userId, id, label, romanizer)
    }
    if (added.isNotEmpty()) {
        XLog.w("快捷方式：$pkg（user=$userId）钉住那档补到 ${added.size} 条（本进程 ${local.size} 条 ⇒ 去重后 ${local.size + added.size} 条）")
    }
    return (local + added).sortedBy { it.sortKey }
}

/**
 * 按 [kinds] 逐个各问一次，合并去重 —— 挂在两个触发点上：合并位一条都没问到；或者钉住一条都没有。
 *
 *  为什么留这一趟：合并位（`FLAG_MATCH_DYNAMIC | MANIFEST | PINNED`）一条都不给、单类问得出来的
 *  情况在 ROM 改过 `getShortcuts` 的机型上出现过。而「钉住」这一档在系统侧还是**按调用方**算的
 *  （钉那一方是桌面，模块不是），所以它单独问也常常是 0 —— 两种结果都得进日志，否则
 *  「平台不给」与「我们问错了」永远分不开。
 *
 * @param known 已经问到的那几条：合并时按 id 去重，新问到的排在后面
 */
private fun mergeKinds(
    launcher: LauncherApps,
    pkg: String,
    userId: Int,
    kinds: List<Pair<String, Int>>,
    known: List<ShortcutInfo> = emptyList(),
): List<ShortcutInfo> {
    val user = AppCloneCatalog.handleOf(userId)
    val out = LinkedHashMap<String, ShortcutInfo>()
    for (info in known) out[info.id] = info
    val before = out.size
    val counts = ArrayList<String>(kinds.size)
    for ((name, flag) in kinds) {
        val got =
            try {
                launcher
                    .getShortcuts(LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flag), user)
                    .orEmpty()
            } catch (e: Throwable) {
                counts += "$name 抛错(${e.javaClass.simpleName})"
                continue
            }
        counts += "$name ${got.size}"
        for (info in got) out.putIfAbsent(info.id, info)
    }
    val detail = counts.joinToString(" / ")
    val gained = out.size - before
    when {
        //  问到了新的：合并位那条路缺了这一档，这一趟把它补上了（判据）
        gained > 0 -> XLog.w("快捷方式：$pkg（user=$userId）单类再问补到 $gained 条 ⇒ $detail")

        //  合并位本来就是空的、逐类也是空的：这个应用确实没有（排查料）
        before == 0 -> XLog.d { "快捷方式：$pkg（user=$userId）三类都没有 ⇒ 逐类再问也是空（$detail）" }

        //  动态 / 清单有、钉住单独问还是 0：这一档系统没给我们（排查料）
        else -> XLog.d { "快捷方式：$pkg（user=$userId）钉住单独问还是 0（$detail）" }
    }
    return out.values.toList()
}

/**
 * 一次快捷方式查询的**判据行** —— 用户报「添加到桌面的快捷方式没出现在模块里」时先看它。
 *
 * ## 摘要那行为什么是 `w`
 *
 *  `hasShortcutHostPermission()` 是系统那道闸（非桌面应用要靠 `ACCESS_SHORTCUTS`）。拿不到时
 *  `getShortcuts` **不抛异常、只返回空清单** —— 不打这一位，「我们读不到」与「这个应用一条都没有」
 *  在日志里长得一模一样，而这两件事接下来该做的事完全不同。
 *
 * ## 三类为什么要分开数
 *
 *  查询同时带 `FLAG_MATCH_DYNAMIC` / `FLAG_MATCH_MANIFEST` / `FLAG_MATCH_PINNED`，可三类的可见性
 *  不一样：动态与清单是应用自己注册的，谁问都看得见；**钉住的那一类按调用方分** ——
 *  `FLAG_MATCH_PINNED` 给的是「调用方作为桌面宿主钉住的那些」，模块不是桌面，通常一条也拿不到。
 *  于是「长按图标拖到桌面的那一条」与「应用内置的快捷方式」在模块这边不是同一批，
 *  分开报才能一眼看出缺的是哪一类。
 *
 * ## 明细为什么是 `d`
 *
 *  条数与分类已经够回答「缺哪一类」；逐条只留 id 与状态（真机排查才需要），留在 `d` 里。
 *
 * @param list 这一次查到的全部快捷方式（不做任何筛选 —— 这一行说的是「系统给了什么」，
 *   不是「页面最后列了什么」）
 */
private fun logShortcuts(
    pkg: String,
    userId: Int,
    launcher: LauncherApps,
    list: List<ShortcutInfo>,
) {
    val hostAllowed =
        try {
            launcher.hasShortcutHostPermission()
        } catch (e: Throwable) {
            XLog.w("快捷方式：问桌面宿主权限失败（$pkg，user=$userId，${e.javaClass.simpleName}）")
            null
        }
    val dynamic = list.count { it.isDynamic }
    val manifest = list.count { it.isDeclaredInManifest }
    val pinned = list.count { it.isPinned }
    val disabled = list.count { !it.isEnabled }
    // 判据级：这一行回答「这个应用到底有没有快捷方式、我们看不看得见」。
    XLog.w(
        "快捷方式：$pkg（user=$userId）桌面宿主权限=" +
            when (hostAllowed) {
                true -> "有"
                false -> "没有"
                null -> "问不到"
            } +
            "，查到 ${list.size} 条（动态 $dynamic / 清单 $manifest / 钉住 $pinned，停用 $disabled）",
    )
    if (list.isEmpty()) return
    XLog.d {
        "快捷方式明细：$pkg → " +
            list.take(SHORTCUT_LOG_MAX).joinToString("；") { info ->
                "${info.id}（${shortcutKindOf(info)}${if (info.isEnabled) "" else "，已停用"}）"
            } +
            if (list.size > SHORTCUT_LOG_MAX) "；…共 ${list.size} 条" else ""
    }
}

/** 一条快捷方式算哪一档（只进日志）—— 三类可能同时成立，按「钉住 → 清单 → 动态」挑一个说。 */
private fun shortcutKindOf(info: ShortcutInfo): String =
    when {
        info.isPinned -> "钉住"
        info.isDeclaredInManifest -> "清单"
        info.isDynamic -> "动态"
        else -> "其它"
    }
