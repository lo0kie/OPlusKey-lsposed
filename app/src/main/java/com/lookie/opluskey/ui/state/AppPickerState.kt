package com.lookie.opluskey.ui.state

import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.model.LaunchableApp

/**
 * 「选择应用」页那一域的状态 —— **一域一个对象**，写入者收窄到少数几处（`docs/decoupling.md` 动作 5）。
 *
 * 为什么这一域要单独拎出来：这一页的状态原来直接摊在 `SettingsPage` 上（7 个字段），而**读写它的人
 * 分布在四处** —— 顶栏那棵树（`ui/screen/apppicker/AppPickerHeaderMenu.kt` 里那三项菜单）、页内容那棵树
 * （`ui/screen/AppPicker.kt`）、两个入口（`ui/StepActions.kt` 与网格那格）。摊在页面上时
 * 「谁改过 appFilter」得把四个文件翻一遍；收进一个对象之后，改它的入口一眼可列。
 *
 * 与页面级状态**同生共死的那几位仍留在页面里**（比如确认窗的挂起项），这一域只装
 * 「这趟挑应用"是谁在挑、怎么显示"」。
 *
 * 注意：这些字段提到页面对象上（而不是留在页面里 `remember`）**是有理由的**，见各字段自己的说明
 * —— 顶栏与页内容是同一层 `Box` 里**两棵并列的树**，页面那棵出栈即注销，顶栏那棵不走。
 */

internal class AppPickerState {
    companion object {
        /** [branchTarget] 的「不是在给分叉挑」。 */
        const val NOT_FOR_BRANCH = -2

        /** [branchTarget] 的「给分叉新加一支」。 */
        const val NEW_BRANCH = -1
    }

    /**
     * 搜索词。
     *
     *  搜索框挂在**顶栏**里，页面里的过滤逻辑与它读同一份 —— 两棵树共用，只能提上来。
     *  注意：随之多一笔账：那份 `remember` 不随页面出栈注销，提到上面就得**手动清** ——
     *  两个入口（「按键绑定」页的「打开应用」格、添加命令浮层的「选择应用」）都要清一次。
     */
    val filter = mutableStateOf("")

    /**
     * 「选择界面」页的搜索词 —— 与 [filter] 各归各的：两页是两条独立的搜索。
     *
     *  与 [filter] 同一笔账：搜索框挂在**顶栏**那条带子里，页内容是另一棵树，所以只能提到页面级；
     *  进页时清一次（见 `PageEntry` 里长按那一支）。
     */
    val activityFilter = mutableStateOf("")

    /**
     * 顶栏菜单里那一项「显示全部应用」。
     *
     *  与 [filter] 相反：它**不随进页清空** —— 那是「我想看到全部应用」这个**意图**，不是一次输入。
     *  而且它**会落盘**：本地持久化在 [UiState]（**不进用户配置**），进页时读一次，所以页面重建 /
     *  宿主 Activity 重建之后仍是上次那一档。
     *
     *  叫 `showSystemApps` 是**历史包袱**：菜单叫「显示系统应用」，而这一档并不按 `FLAG_SYSTEM` 筛，
     *  而是把没有启动图标的第三方应用（典型是各个 LSPosed 模块）一并列出来；落盘的键
     *  `show_system_apps` 也保持原名 —— 改名会让用户的这一档回默认。
     */
    val showSystemApps = mutableStateOf(false)

    /**
     * 顶栏菜单里那一项「显示失效应用」。
     *
     *  与 [showSystemApps] 同一套账：**不随进页清空**、**落盘**、进页读一次。
     *  判据是 `LaunchableApp.launchable`（执行端同一把尺子），关着时藏掉的正是「按下去必弹 toast」的那些。
     *  **默认关**；落盘的键就叫 `show_unopenable`，改语义必须换键名（旧键作废，不做迁移）。
     */
    val showUnopenable = mutableStateOf(false)

    /**
     * 菜单里那一项「以小窗口打开」。
     *
     *  它不筛列表，只决定**挑中的应用写成哪个动作**：开着写 `window:<包名>`，关着写 `app:<包名>`。
     *
     *  **它是临时的，不是偏好**：**不落盘**、**没有跨次记忆** —— 每次进这一页都由
     *  `SettingsPage.resetSmallWindowForPicker()` 按**正在编辑的那个动作**重算（本来就是 `window:`
     *  就开着、`app:` 或新绑就关着）。与 [showSystemApps] / [showUnopenable] 那两项**相反**。
     *  理由：这一项描述的是「这一次要往哪个动作上写」，留着上一次的 `true` 会让下一次挑应用
     *  **静默**变成小窗，用户看不出自己什么时候开的。
     */
    val smallWindow = mutableStateOf(false)

    /**
     * 进这一页时对应的**手势**（`null` = 没在挑应用，或者不是给手势挑的）。
     *
     *  用途：挑中应用时知道该写哪个手势。不能靠 pager 的当前页 —— 那一页不属于 pager。
     *  从「添加命令」浮层的「选择应用」进来时它是 `null`（挑中的应用是**一步**、不绑手势）——
     *  给多条命令加一步却没挑，本来就该什么都不发生。与 [replaceStep] 同生共死。
     */
    var gesture: Gesture? = null
        private set

    /** 这次是给「多条命令」加一步（挑中的包名走 `addStep`，不写手势绑定）。 */
    var forStep = false
        private set

    /**
     * 这次是给「条件执行」的某一支挑**条件应用**：`>= 0` = 改第几支，[NEW_BRANCH] = 新加一支。
     * [NOT_FOR_BRANCH] = 不是在给分叉挑。与 [forStep] 一样同生共死。
     */
    var branchTarget: Int = NOT_FOR_BRANCH
        private set

    /**
     * 这次是给「条件执行」的某一支挑**要执行的应用**（`app:` / `window:` 那一步）：
     * `>= 0` = 改第几支。`-1` = 不是在给分支命令挑。
     *
     *  与 [branchTarget] 是**两件事**，虽然落点都是「某一支」：那一支有两个位置能放应用 ——
     *  判别用的条件（包名）与执行用的命令（token）。合成一个字段时界面上分不出这次挑完该往
     *  哪一处写，而两者的写法（包名 vs `app:` token）也不一样。
     */
    var branchCommandTarget: Int = NOT_FOR_BRANCH
        private set

    /**
     * 这次进这一页是**替换多条命令的第几步**。`-1` = 追加为一步。与 [forStep] 同生共死。
     */
    var replaceStep = -1
        private set

    /**
     * 挑完应用（或中途返回）回到来源页时，那支底部抽屉该按哪个来处**重新打开**；`null` = 不用开。
     *
     *  为什么必须挂在页面级：抽屉的开关是**来源页组合里**的一个 `remember`，而挑应用要压一页
     *  「选择应用」—— 来源页那棵组合被注销，回来时那个 `remember` 已经回到初值（关着）。
     *  这一位与 [filter] 同一个理由（页面级对象，越过这一趟还在），回来后据此把抽屉原样支起来，
     *  用户不用再点一次「选择应用」。具体含义由各页解释：命令页是「`-1` 追加 / `>= 0` 换第几步」，
     *  条件执行是「第几条规则」。读过即清（见 `CommandSteps` / `CommandBranchMenus`）。
     */
    val resumeSheetAt = mutableStateOf<ResumeSheet?>(null)

    /**
     * 这一趟是在给「条件执行」挑 —— [branchTarget] 或 [branchCommandTarget] 有一头有效。
     *
     *  那一页的两处**只认应用本身**：条件记的是包名，动作记的是 `app:包名`。所以这一页对它们
     *  要关掉两件「把应用细化成别的东西」的事 —— **挑界面**（点行不再进二级页）与
     *  **以小窗口打开**（`window:` 那一档在这里没人读）。判据只此一处，两个界面各读它。
     */
    val forBranch: Boolean
        get() = branchTarget != NOT_FOR_BRANCH || branchCommandTarget != NOT_FOR_BRANCH

    // —— 「这趟为谁挑」那一组标记的三个改法 ——
    //
    //  三个字段同生共死（`forStep` 一起写着 `replaceStep` 才有意义，`gesture` 只归网格那条入口），
    //  所以入口也只留这三个：原来「进页那个手势」「加一步 / 换一步」「挑完 / 没挑就退」这四种场合
    //  各自就地赋三个字段，散在**六个地方** —— 少清一个就留下半个状态（`forStep=false` 却带着
    //  上一次的 `gesture`），而从代码上看不出来（见 `docs/decoupling.md` 判据 3）。

    /** 网格那条入口：给手势 [g] 挑应用（挑中的写成 `app:` / `window:` 绑给这个手势）。 */
    fun openForGesture(g: Gesture) {
        gesture = g
        forStep = false
        replaceStep = -1
        branchTarget = NOT_FOR_BRANCH
        branchCommandTarget = NOT_FOR_BRANCH
    }

    /** 「添加命令」那条入口：给多条命令**加一步**（[replaceIndex] `< 0`）或换掉第 [replaceIndex] 步。 */
    fun openForStep(replaceIndex: Int) {
        gesture = null
        forStep = true
        replaceStep = replaceIndex
        branchTarget = NOT_FOR_BRANCH
        branchCommandTarget = NOT_FOR_BRANCH
    }

    /**
     * 分叉那条入口的两档：给第 [index] 支挑**条件应用**（[forCommand] = false）
     * 或**要执行的应用**（[forCommand] = true）。
     *
     *  两档写的是同一支的两个不同位置 —— 条件（包名）与命令（`app:` token），落点不同、
     *  语义也不同，但「为谁挑」那一组的清场纪律完全一样，所以只留一个入口：
     *  分成两个函数时，那一串「把另外几位清掉」要写两遍，加一档就得改两处（`TooManyFunctions`
     *  也是这么来的）。
     */
    fun openForBranch(
        index: Int,
        forCommand: Boolean = false,
    ) {
        gesture = null
        forStep = false
        replaceStep = -1
        branchTarget = if (forCommand) NOT_FOR_BRANCH else index
        branchCommandTarget = if (forCommand) index else NOT_FOR_BRANCH
    }

    /** 这趟「为谁挑」整个作废 —— 挑完了、写失败留在原地、或干脆没挑就退出来。 */
    fun clearTarget() {
        gesture = null
        forStep = false
        replaceStep = -1
        branchTarget = NOT_FOR_BRANCH
        branchCommandTarget = NOT_FOR_BRANCH
        // 本地选中与草稿一并作废：这一趟挑应用的账到此为止
        pending.value = null
        draft = null
    }

    /**
     * 放弃「挑完回来要把抽屉支起来」那笔账 —— **来源页**出栈时调。
     *
     *  [resumeSheetAt] 记的是来源页的意图，所以它既不在进页时清、也不在 [resetSession] 里清
     *  （挑应用页出栈那一刻正是要用它的时候）；只有来源页自己走了，这个意图才失去意义。
     */
    fun cancelResume() {
        resumeSheetAt.value = null
    }

    /**
     * 这一趟「选择应用」**整趟结束**（页面离开栈）—— 把这一页引入的界面状态全部复位。
     *
     *  与 [clearTarget] 分工：[clearTarget] 只作废「为谁挑」，是「挑完了 / 没挑就退」那一步的账；
     *  这一支连搜索词、二级页在看谁、临时的「以小窗口打开」一起收 —— 页面离栈之后再进，
     *  看到的应当是一张干净的列表，而不是上一次的搜索词与筛选。
     *
     *  **不碰**两类：`showSystemApps` / `showUnopenable` 是落盘偏好（进页读一次，清了等于改设置）；
     *  `resumeSheetAt` 是**来源页**的账（挑完要回去把抽屉支起来），归来源页出栈时清。
     */
    fun resetSession() {
        clearTarget()
        forgetActivities()
        filter.value = ""
        activityFilter.value = ""
        smallWindow.value = false
    }

    /**
     * 这一趟挑完了 —— 返回「挑中的东西写到哪儿」，标记一并收掉；`null` = 不是这两条路（写手势绑定）。
     *
     *  三档合成一个出口：它们互斥、判据又在同一个「为谁挑」组里，分三个方法时调用方得先自己
     *  判一次谁有效再去取 —— 那一步正是「两个字段都可能非空」的漏洞来源。
     */
    fun takePickTarget(): PickTarget? =
        when {
            branchCommandTarget != NOT_FOR_BRANCH -> {
                val index = branchCommandTarget
                branchCommandTarget = NOT_FOR_BRANCH
                PickTarget.BranchCommand(index)
            }

            branchTarget != NOT_FOR_BRANCH -> {
                val index = branchTarget
                branchTarget = NOT_FOR_BRANCH
                PickTarget.Branch(index)
            }

            forStep -> {
                forStep = false
                val index = replaceStep
                replaceStep = -1
                PickTarget.Step(index)
            }

            else -> {
                null
            }
        }

    // ── 二级页「选择界面」在看哪个应用 ──

    /**
     * 「选择应用」页点某一行之后，二级页正在看的是**哪一个应用**（`null` = 不在那一页）。
     *
     *  与 [gesture] / [forStep] 那一组**分开存**：那组回答「挑中的应用写到哪儿去」，
     *  这一位只回答「二级页在看谁」。两者在二级页同时有效 —— 挑中界面之后仍然按那一组
     *  写回（见 `PageEntry.onDraftPicked`），所以不能合并。
     *
     *  与 [filter] 同一笔账：二级页的顶栏与内容也是两棵树，状态得提到页面级。
     *
     *  **只由 [openActivities] 写、[forgetActivities] 清**：退页时一概不动它。
     *  原来在二级页的  里清，结果退场动画没收完、用户已经点了下一个应用时，
     *  旧实例会把新目标抹掉，新页当场退回上一级（真机日志里那一串就是这么来的）。
     *  每次进页都会覆盖它，所以「不清」不会串到下一次。
     */
    var activityTarget: String? by mutableStateOf(null)
        private set

    /**
     * 点某个应用 → 进二级页看它的界面。
     *
     *  **不动 [draft]**：进这一页只是「看看」，用户可能什么都不确认就返回 —— 那时上一次
     *  （别的应用）确认过的那一条仍然应当留着，等他回上一级按 FAB 提交。
     *  真在那页确认了别的东西，会由页面层的写入点（`PageEntry.onDraftPicked`）直接覆盖。
     */
    fun openActivities(target: String) {
        //  判据级：用户报「挑错了应用 / 进错了页」时要能在日志里对上这一笔；低频路径，用 w
        XLog.w("openActivities：看 $target（原来是 $activityTarget）")
        activityTarget = target
    }

    /**
     * 无条件清空「在看谁」—— 只给**整个页面栈被清掉**那一档用（宿主重进模块）。
     *
     *  那一档连「选择应用」一起弹掉了，不存在「另一份新的目标正在用」的情况。
     *  **普通退页不需要清**：这一位每次进页都会被 [openActivities] 覆盖，
     *  在退页时清反而会误伤「退场动画还没收完、用户已经点了下一个应用」那一瞬（见字段说明）。
     */
    fun forgetActivities() {
        XLog.d("forgetActivities：无条件清掉 $activityTarget")
        activityTarget = null
    }

    /**
     * 本次编辑的**草稿** —— 在「选择界面」页点了确认之后放在这里，**不写配置**。
     *
     *  写盘只发生在「选择应用」页那颗 FAB 上（见 `PageEntry.onAppPicked`）：那一级才是
     *  「这一趟挑完了」的地方。二级页点确认只是把「打算绑哪个」记下来并退回上一级 ——
     *  用户还可以换个应用、或者干脆返回走人，那时候配置里一个字节都不该动。
     *
     *  与 [pending] 分工：[pending] 是「界面上亮着哪一条」（含进页时那个已绑定的），
     *  这一位是「本次真正改动过、等着被提交的那一条」—— 没有它，一级页只能把「当前绑定」
     *  重写一遍，分不清「用户刚挑了个新的」与「什么都没改」。
     */
    var draft: PickedDetail? by mutableStateOf(null)

    // ── 两级各自的「本次选中」──

    /**
     * 「选择应用」页里**当前选中的**那一条 —— 行尾那枚图标照它画，右下角那颗 FAB 照它写。
     *
     *  装的是 [PickedDetail] 而不只是一个目标字符串：行尾要在「具体界面」与「快捷方式」之间
     *  分开画（`device-screen` / `table-shortcut`），而这两者在配置里都长成 `包名/xxx`，
     *  光看字符串分不出来 —— 那一位 `shortcutId` 是唯一的判据。
     *
     *  进页时按**当前绑定**重置一次（见 `AppPickerUiState.beginVisit`）；在二级页点了确认会被
     *  换成刚挑的那一条（草稿），点行本身不改它（点行只是往下一级走）。
     *
     *  放在页面级而不是页面里 `remember`：被二级页盖住时那一份组合会被销毁，退回来就退回成
     *  「已绑定的那个」—— 用户明明挑过 A，回来却亮着 B。
     */
    val pending = mutableStateOf<PickedDetail?>(null)

    // ── 屏级缓存：清单 + 图标 ──
    //
    //  这几样原来挂在 `ui/screen/apppicker/AppList.kt` 的**包级 internal 可变全局**上 —— 没有所有者，
    //  「谁能改、什么时候失效」全靠注释约定，而「谁在什么时候清」正是这类缓存唯一的难点。
    //  收进这一域之后所有者是 `SettingsPage.picker`，与这一页的状态同生共死。
    //
    //  注意：页面**重建**（宿主 Activity 重建）会连它一起没。原来挂全局时能跨重建活下来，但那是**顺带**
    //  的性质、不是设计目标：缓存只省「首帧先铺旧清单」这一下，丢了不影响正确性（进页照样查一遍）。

    /**
     * 上一次查到的清单 —— 让二次进入这一页时立刻有内容可画。
     *
     *  缓存**不阻止重查**：每次进页仍会在后台重跑一遍并覆盖它，新装 / 卸载的应用照样能出现。
     *
     *  **必须是 compose 状态**：顶栏那行「已选择: <应用名>」直接读它查 label，
     *  裸 `var` 不会订阅读取 ⇒ 清单查完、缓存换了一茬，顶栏也不会重组，于是长期显示包名。
     */
    val cachedApps = mutableStateOf<List<LaunchableApp>?>(null)

    /**
     * [cachedApps] 是在哪个模式下查的（「显示全部应用」开没开）。
     *
     *  必须与清单一起写：否则会把「开着系统应用时查的清单」当成「关着时的首帧」用，
     *  退出再进会先闪一屏系统应用。
     */
    var cachedAppsWithSystem = false

    /** 图标缓存。整块收在一个对象里，见 [AppIconCache]。 */
    val iconCache = AppIconCache()
}

/**
 * 「打开应用」页那份图标 LRU —— 包名 → 图，容量见 [APP_ICON_CACHE_SLOTS]。
 *
 *  单独一个类，不挂在 [AppPickerState] 上：那边是「挑应用这趟会话的目标与筛选」，这一份只是
 *  缓存的存取（连版本号一起）。混在一起时那个类的方法数已经越过 `TooManyFunctions` 的阈值，
 *  而这两族改的理由完全不同。
 */
internal class AppIconCache {
    /** `accessOrder = true`：取用即算「刚用过」。 */
    private val icons =
        object : LinkedHashMap<String, Drawable>(APP_ICON_CACHE_SLOTS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean = size > APP_ICON_CACHE_SLOTS
        }

    /**
     * 版本号 —— **只在「清缓存」时 +1**（[clear]）。
     *
     *  它是 Compose 状态：`AppIcon` 在组合里读它，于是清缓存能让**已经组合出来的行**重新取一次。
     *  不读它不行 —— 那些行的键是包名，清单换了照样不重组、`LaunchedEffect` 也不重跑，
     *  清缓存对它们等于没清（换图标包后要滚出去再滚回来才换）。
     *
     *  「某一行取到了图」**不走这里**（见 [put]）：那一档由那一行自己的状态驱动 ——
     *  走全局版本号的话，一屏十来行依次到位就是十来次全表重组（每行各自那一份图与位图都得重跑一遍）。
     */
    val generation = mutableIntStateOf(0)

    /** 取一份图（滚回来的那一行靠它免掉第二次跨进程取图标）。 */
    fun cached(packageName: String): Drawable? = synchronized(icons) { icons[packageName] }

    /**
     * 往缓存里补一份图（只为「滚回去不再跨进程问第二轮」）。
     *
     *  注意**不动** [generation]：这一份是给**滚回来那一行**用的；当下这一行由它自己那份状态
     *  画出来（见 `AppIcon`），不需要惊动全表。
     */
    fun put(
        packageName: String,
        icon: Drawable,
    ) {
        synchronized(icons) { icons[packageName] = icon }
    }

    /** 清单重查完就清空（理由见 [APP_ICON_CACHE_SLOTS]）：让图标跟着那份新清单重新取。 */
    fun clear() {
        synchronized(icons) { icons.clear() }
        generation.intValue++
    }
}

/**
 * 应用图标的小 LRU（只给「打开应用」页用）—— 滚回去的项靠它免掉第二次跨进程取图标。
 *
 *  容量按「来回滚两三屏」给：一屏十来行，超出按最久没滚到的淘汰，淘汰只是下次滚回来多问一次
 *  `PackageManager`，不是错。
 *
 *  **每次拿到新清单就清一遍**（见 [AppIconCache.clear] 的调用点）：图标本身会因为换图标包 /
 *  换主题而变，缓存里留着的就是换之前那一份 —— 不清，最近滚过的那几十个应用会一直用它。
 *  清单没变时缓存照旧生效，滚动来回不走第二次 binder。
 */
internal const val APP_ICON_CACHE_SLOTS = 48

/**
 * 二级页挑中的一行 —— 「选择应用」页那颗确认键据此拼 token。
 *
 * @property target 那一行的完整目标写法（`包名/类名` 或 `包名/快捷方式id`，都带用户前缀）
 * @property shortcutId 非空 = 这是快捷方式那一节的行，拼 `appshortcut:` 而不是 `app:` / `window:`
 * @property label 这一行**自己的名字**（快捷方式的「扫一扫」）。落盘时它进 `Config.appNames`
 *   （按 [target] 的身份索引），**不拼进 token** —— 名字是显示信息，不是目标的一部分。
 *  界面那两节留空：它们的名字是**应用名**，由落盘那一侧按目标现取（见 `appNameOf`）。
 */
internal data class PickedDetail(
    val target: String,
    val shortcutId: String?,
    val label: String? = null,
) {
    /**
     * 拼成要写进配置的 token。
     *
     * @param window 「以小窗口打开」那一档 —— 只对「打开界面」有意义；快捷方式没有小窗起法，
     *   传什么都不影响。
     */
    fun token(window: Boolean): String =
        when {
            shortcutId != null -> {
                Action
                    .AppShortcut(
                        pkg = AppTarget.pkg(target),
                        shortcutId = shortcutId,
                        userId = AppTarget.userId(target),
                        // 小窗那一档对快捷方式同样有效：`appshortcut:w:…`（执行侧取它那条 intent 走小窗配方）
                        window = window,
                    ).token
            }

            window -> {
                Action.AppWindow(target).token
            }

            else -> {
                Action.App(target).token
            }
        }
}

/** 这一趟挑中的应用写到哪儿去 —— 见 [AppPickerState.takePickTarget]。 */
internal sealed interface PickTarget {
    /** 多条命令的某一「步」：`-1` = 追加、`>= 0` = 换掉那一步。 */
    data class Step(
        val index: Int,
    ) : PickTarget

    /** 条件执行的某一支：[AppPickerState.NEW_BRANCH] = 新加一支。写的是那一支的**条件**（包名）。 */
    data class Branch(
        val index: Int,
    ) : PickTarget

    /** 条件执行的某一支要**执行的应用**：写的是那一支的**命令**（`app:` / `window:` token）。 */
    data class BranchCommand(
        val index: Int,
    ) : PickTarget
}

/**
 * 「挑完应用回来，**哪一页**的抽屉要按哪个来处重新打开」。
 *
 *  [page] 是必须的：这一位是页面级、跨页存活 —— 只记一个下标时，条件页留下的值会被
 *  命令页当成自己的步骤下标读走（串页：自动弹出抽屉、再选一项就换掉那一步）。
 *  消费方各自 `takeIf { it.page == 自己 }`，读不到就当作没有。
 */
internal data class ResumeSheet(
    val page: Screen,
    val index: Int,
)
