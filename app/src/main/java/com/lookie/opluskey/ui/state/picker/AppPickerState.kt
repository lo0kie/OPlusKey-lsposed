package com.lookie.opluskey.ui.state.picker

import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.model.target.LaunchableApp

/**
 * 「选择应用」页那一域的状态 —— **一域一个对象**，写入者收窄到少数几处（`docs/architecture/decoupling.md` 动作 5）。
 *
 * 为什么这一域要单独拎出来：这一页的状态原来直接摊在 `SettingsPage` 上（7 个字段），而**读写它的人
 * 分布在四处** —— 顶栏那棵树（`ui/screen/apppicker/AppPickerHeaderMenu.kt` 里那三项菜单）、页内容那棵树
 * （`ui/screen/apppicker/AppPicker.kt`）、两个入口（`ui/write/StepActions.kt` 与网格那格）。摊在页面上时
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

        /** [sceneAt] 的「不是在给场景块挑」—— `-1` 与「第 0 块」撞不上，`0` 是合法下标。 */
        const val NOT_FOR_SCENE = -1
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
     * 「选择界面」页当前列出来的**条数** —— 搜索框占位里那个数字（`搜索应用组件 (12)`）。
     *
     *  提到页面级的原因与 [activityFilter] 一模一样：清单本身是页面里 `remember` 的
     *  （`ActivityPickerList` 的 `loaded`），而占位文案挂在**顶栏**那条带子上，两棵树读不到彼此。
     *  页面在清单到手那一刻写一次；离开这一页时留着的旧数字无害 —— 那条带子已经不在了。
     */
    val activityCount = mutableStateOf(0)

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
     * 顶栏菜单里那一项「显示失效应用」—— **只在「打开应用」这一趟出现**。
     *
     *  与 [showSystemApps] 同一套账：**不随进页清空**、**落盘**、进页读一次。
     *  判据是 `LaunchableApp.launchable`（执行端同一把尺子）：关着时藏掉的正是「按下去必弹 toast」的那些。
     *  **默认关**。
     *
     *  它只是**过滤**：清单一次就取全（见 `loadLaunchableApps`），拨它不触发查询、
     *  也不换缓存 —— 曾经它和 [showSystemApps] 一起决定「查哪一档清单」，那套在真机上
     *  表现为「开关按了没反应」，见 `docs/changelog/2026-11-05-应用控制.md`。
     *
     *  「结束应用」那一趟**没有这一项、也不吃它**：停掉一个没有启动图标的应用正是它的用处，
     *  所以那一趟一律全列（见 `AppPickerUiState.shown`）。
     */
    val showUnopenable = mutableStateOf(false)

    /**
     * 「选择应用」页顶栏那颗「以小窗口打开」开关（原先在 ⋮ 菜单里，现在单独一颗键）。
     *
     *  它不筛列表，只决定**挑中的应用写成哪个动作**：开着写 `window:<包名>`，关着写 `app:<包名>`。
     *
     *  **它是临时的，不是偏好**：**不落盘**、**没有跨次记忆** —— 每次进这一页都由
     *  `SettingsPage.resetSmallWindowForPicker()` 按**正在编辑的那个动作**重算（本来就是 `window:`
     *  就开着、`app:` 或新绑就关着）。与 [showSystemApps] **相反**。
     *  理由：这一项描述的是「这一次要往哪个动作上写」，留着上一次的 `true` 会让下一次挑应用
     *  **静默**变成小窗，用户看不出自己什么时候开的。
     */
    val smallWindow = mutableStateOf(false)

    /**
     * 「选择应用」页 ⋮ 菜单里那颗「使用原版动画」开关 —— 与 [smallWindow] **同一条性质、互相排斥**。
     *
     *  它也**不落盘**、不筛列表，只决定挑中的应用写成哪个动作：开着写 `app:a:<包名>`
     *  （[Action.App.stockAnim] 那一档，执行时套原版的启动包裹），关着写 `app:<包名>`。
     *  每次进页由 `SettingsPage.resetSmallWindowForPicker()` 按正在编辑的动作重算。
     *
     *  **为什么与 [smallWindow] 互斥**：小窗那一档走 ColorOS 自己的小窗配方（`MiniWindowLauncher`），
     *  塞不进原版那套启动包裹 —— 同一格只能装得下一种起法。
     */
    val stockAnim = mutableStateOf(false)

    /**
     * 这一条目标**吃不到原版动画**吗 —— 点 FAB 那一刻据此把 [stockAnim] 拨回关（并说一句）。
     *
     *  三种：**分身**（原版那条链路写死 `UserHandle.CURRENT`，见 `OriginalLaunch`）、
     *  **快捷方式**（走系统封好的 PendingIntent，拿不到内部 intent 交不出去）、
     *  **具体界面**（`包名/类名` 现拼的 intent 没有 `ACTION_MAIN` + `CATEGORY_LAUNCHER`，
     *  ROM 认不出「桌面启动」，只给系统默认动画 —— 执行侧补那对标记也只是尽力而为）。
     *
     *  改这里之前先看 `docs/features/launch-animation.md` 的 10.1：前两条是**做不到**，
     *  不是「还没做」。
     */
    fun stockAnimUnsupported(
        target: String,
        shortcutId: String?,
    ): Boolean = shortcutId != null || AppTarget.userId(target) != 0 || AppTarget.activity(target) != null

    /**
     * 进页那一刻这两个开关的值 —— 退出页面时据此判「这一趟拨过没有」。
     *
     *  为什么拨开关也算**草稿**：`window:` / `app:a:` 是**绑定的一部分**，拨完再挑应用、按确认
     *  才落盘；拨了却没挑就退出去，用户以为改过了，实际什么都没发生。所以退出时跟挑中那一条
     *  享受同一句「放弃这次选择」（见 `SettingsPage.hasUnconfirmedPick`）。
     *
     *  基线在 [com.lookie.opluskey.ui.SettingsPage.resetSmallWindowForPicker] 里记 —— 那里正是
     *  两个开关按「正在编辑的动作」重算的地方，进页只走那一条路。
     */
    private var visitSmallWindow = false
    private var visitStockAnim = false

    /** 「结束应用」那一趟进页时选中的那条 —— [stopAppPickedChanged] 的基线，见 [markStopBaseline]。 */
    private var visitStopTarget: String? = null

    /** 记下两个窗口开关的基线（进页重算完之后调，见 [visitSmallWindow]）。 */
    fun markWindowTogglesBaseline() {
        visitSmallWindow = smallWindow.value
        visitStockAnim = stockAnim.value
    }

    /**
     * 记「结束应用」那一趟的基线 —— 进页那一刻**复位之后**的值（`AppPickerUiState.beginVisit`
     * 里 `pending` 赋值完立刻调）。
     *
     *  为什么不能跟着 [markWindowTogglesBaseline] 记：那两处调用点都在 `beginVisit` **之前**
     *  （`resetSmallWindowForPicker` / `openAppPickerForStopApp`），那时 `pending` 还是**上一趟
     *  留下的**（正常收尾后是 `null`）。拿它当基线，进页后点一下「本来就是它」的那一行也会
     *  判成「挑过新的」，退页时凭空弹一句「放弃选择」。
     */
    fun markStopBaseline(target: String?) {
        visitStopTarget = target
    }

    /** 这一趟拨过「以小窗口打开」或「使用原版动画」吗 —— 退出页面要按草稿问一句。 */
    val windowTogglesChanged: Boolean
        get() = smallWindow.value != visitSmallWindow || stockAnim.value != visitStockAnim

    /**
     * 这一趟在「结束应用」里**挑中了一条却还没点 FAB** —— 与 [windowTogglesChanged] 同一条账。
     *
     *  那一趟没有二级页（点一行就是选中它），所以「草稿」只可能是「选中的那一行」：
     *  与进页那一刻记下的 [visitStopTarget] 一比就出来了。缺了这一条，挑了 A 直接返回
     *  会被当成「什么都没做」—— 用户以为已经改成结束了，其实一个字节都没写。
     */
    val stopAppPickedChanged: Boolean
        get() {
            val target = pending.value?.target ?: return false
            return stopApp && target != visitStopTarget
        }

    /**
     * 这一趟挑的应用要写成 **`proc:stop:包名`**（「结束应用」那一格），而不是 `app:`。
     *
     *  与 [forBranch] 同一条性质：**只认应用本身** —— 点一行就是选中它（没有「里面哪个界面」），
     *  也**没有**「以小窗口打开」（那一档在这里没人读）。差别只在落盘的 token。
     *
     *  **必须是 compose 状态**：`AppPickerUiState.shown`（`derivedStateOf`）与列表那几处都读它，
     *  而它是在**进页那一刻、组合之外**改的。普通 `var` 时那次改写不会让 `shown` 的缓存失效 ——
     *  「结束那一趟全列」的算法会跟着上一趟留下来，拨「显示失效应用」就成了对着过期结果重算
     *  （真机表现：从结束应用退出来再进选择应用，那个开关按了没反应）。
     *
     *  与 [gesture] / [comboId] 同生共死：挑完写回**进这一页时那颗键的那一档手势**。
     */
    private val stopAppState = mutableStateOf(false)

    /** 这一趟是不是「结束应用」（见 [stopAppState]）。 */
    val stopApp: Boolean get() = stopAppState.value

    /**
     * 这一趟排不排**没有启动图标**的那一批（系统应用、没有图标的部分第三方与模块）。
     *
     *  ## 两个开关各管一维、互不借光
     *
     *  [includesIconless] 只看 [showSystemApps]，[includesUnopenable] 只看 [showUnopenable]。
     *  曾经这一位写成「显示失效开着 ⇒ 无图标的也一并放开」，于是拨「显示失效应用」会把整批
     *  没有图标的系统应用一起涌出来（用户口径：这两个开关要**联合筛选**）—— 用户想过滤掉它们，
     *  不该因为开了另一个开关就全被放出来。
     *
     *  「结束应用」那一趟一律全列：停掉一个没有启动图标的应用正是它的用处。
     *
     *  三处读它（列表过滤 / 空态那句 / 选中项清空），各写一遍迟早分叉 —— 尤其这次改的是
     *  「两条判据之间的关系」，漏一处就让某一条路还在按旧口径走。
     */
    val includesIconless: Boolean get() = stopApp || showSystemApps.value

    /** 这一趟排不排**点不开**的那一批（[LaunchableApp.launchable]）—— 与 [includesIconless] 同一条口径。 */
    val includesUnopenable: Boolean get() = stopApp || showUnopenable.value

    /**
     * 进这一页时对应的**手势**（`null` = 没在挑应用，或者不是给手势挑的）。
     *
     *  用途：挑中应用时知道该写哪个手势。不能靠 pager 的当前页 —— 那一页不属于 pager。
     *  从「添加命令」浮层的「选择应用」进来时它是 `null`（挑中的应用是**一步**、不绑手势）——
     *  给多条命令加一步却没挑，本来就该什么都不发生。与 [replaceStep] 同生共死。
     *
     *  类型是**触发条件**而不是那个三值枚举：网格排到第 4、5 页时（三击 / 四击）这一趟也要
     *  原样记下来，退回枚举会把那两个档位的挑应用写成「没绑手势」。
     */
    var gesture: GestureTrigger? = null
        private set

    /**
     * 这一趟是给**哪一档组合键**挑（`null` = 给一颗键挑）。
     *
     *  与 [gesture] 并存而不是合成一个对象：这一层（`ui/state`）不认识界面那套「绑定目标」
     *  （`ui/screen/binding/BindingTarget`），只记两个原始值；组装成目标由调用方做
     *  （见 `PageEntryWrite.onAppPicked`）。组合那一档也要手势 —— 它也有短按 / 长按。
     */
    var comboId: String? = null
        private set

    /** 这次是给「多条命令」加一步（挑中的包名走 `addStep`，不写手势绑定）。 */
    var forStep = false
        private set

    /**
     * 这次是给**长按卡的「松手执行额外动作」**挑动作（挑中的写进这颗键的 `longRelease`）。
     *
     *  与 [forStep] 同一档性质（都是「往某个动作字段里写一条」），但落点不同：
     *  那不是命令库里的步骤，而是这一颗键自己的一个字段。
     */
    var forRelease = false
        private set

    /** 松手那一趟是给哪一档组合键挑（`null` = 给当前这一颗键，见 [forRelease]）。 */
    var releaseComboId: String? = null
        private set

    /** 松手那一趟写的是**「单击后长按」那一档**的松手格（见 `PickTarget.Release.chain`）。 */
    var releaseChain: Boolean = false
        private set

    /**
     * 这一趟是给**「常用命令」那张清单**挑一条动作（挑中的应用 / 结束应用落成清单里的一条）。
     *
     *  与 [forRelease] 同一档性质 —— 都是「把挑中的这一条写到某个地方」，只是那边的落点是某个键
     *  的一个字段，这边是一张清单。挑完**自己退页**，清单由 `PageEntryWrite.writeToken` 写
     *  （见 `PickTarget.CommonCommand`）。
     */
    var forCommonCommand = false
        private set

    /**
     * 这一趟挑中的应用**换掉清单里哪一条**（`null` = 追加一条新的）。
     *
     *  与 [replaceStep] 同一个读法：同一张抽屉既负责「加一条」也负责「换一条」，两者的差别只有
     *  落点 —— 留着上一次的值会把「修改」写成「又多一条」。
     */
    var replaceCommonToken: String? = null
        private set

    /**
     * 这次是给「条件执行」的某一支挑**条件应用**：`>= 0` = 改第几支，[NEW_BRANCH] = 新加一支。
     * [NOT_FOR_BRANCH] = 不是在给分叉挑。与 [forStep] 一样同生共死。
     *
     *  只服务**条件**（挑包名）。场景的某一档要执行什么应用不走这条路 ——
     *  那一档在「场景动作」页的网格里挑（`BindingTarget.Scene`，与网格同一套）。
     */
    var branchTarget: Int = NOT_FOR_BRANCH

    /**
     * 这一趟挑的应用要写进**那一块里的第几条条件**：`>= 0` = 换那一条，`-1` = 追加一条。
     *
     *  与 [branchTarget]（第几条规则）配对使用 —— 一块能带多条条件之后，「写进哪个槽位」
     *  不能再靠 `appConditionIndex` 猜：用户在「再加一条条件」里选的应用，是**新增**一条，
     *  而原来那套「这块已有前台应用就换它、没有就换第 0 条」会把别人的条件覆盖掉。
     */
    var branchConditionIndex: Int = -1
        private set

    /**
     * 这一趟挑的包名要写成**哪一档**条件（[ConditionKind.APP] / [ConditionKind.APP_NOT]）。
     *
     *  应用那一族两档的取值写法、多选、这一页怎么用**完全一样**（见 [ConditionKind.isAppFamily]），
     *  只有写回去的那个 `kind` 不同 —— 所以「为谁挑」这笔账里必须记着它：不记的话，从
     *  「前台应用以外」那一行进来的这一趟会照 [ConditionKind.APP] 写，用户点的负面条件被悄悄
     *  改成正面那一档，而页面上一个字都不会变。
     */
    var branchKind: ConditionKind = ConditionKind.APP
        private set

    /**
     * 这一趟挑的应用要写进**场景表里第几块**的那一档（`-1` = 不是给场景挑）。
     *
     *  与 [comboId] 同一条性质：这是从**场景动作**页的网格进来的那一趟（那几格里有
     *  「应用控制」），挑完要落回那一块的那一档 —— 而那一趟会压一层「选择应用」页，
     *  网格自己那棵组合在退场时就没了。写哪一档由 [gesture] 给（那一趟也记着它）。
     *
     *  **不并进 [branchTarget]**：那一位是「条件执行页里第几**条规则**」，写的是**条件包名**；
     *  这一位写的是**某一档的动作**，落点与写法都不同，合成一个就得再记「现在写哪半个」。
     */
    var sceneAt: Int = NOT_FOR_SCENE
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
     * 这一趟是在给「条件执行」挑**条件** —— [branchTarget] 有效。
     *
     *  只回答「写回走哪条路」。条件那一支只认包名（见 [appOnly]）：动作那一侧不经过这张页面。
     */
    val forBranch: Boolean get() = branchTarget != NOT_FOR_BRANCH

    /**
     * 这一趟**只认应用本身**（给条件挑 / 「结束应用」两档）：点行即选中、没有二级页、也没有小窗。
     *
     *  三处读它（点行 / 行尾图标 / 菜单里那一项），判据只此一份 —— 两档共用一个位是因为它们
     *  对「这一页怎么用」的要求一模一样，差别只在写出去的那个 token。
     *
     *  **给场景某一档挑的那一趟不在这里**：那一支执行的就是一个普通动作，与网格里绑的那一份
     *  没有区别，所以它照常能进二级页挑组件 / 快捷方式、也能开小窗。
     */
    val appOnly: Boolean get() = branchTarget != NOT_FOR_BRANCH || stopApp

    // —— 「这趟为谁挑」那一组标记的三个改法 ——
    //
    //  三个字段同生共死（`forStep` 一起写着 `replaceStep` 才有意义，`gesture` 只归网格那条入口），
    //  所以入口也只留这三个：原来「进页那个手势」「加一步 / 换一步」「挑完 / 没挑就退」这四种场合
    //  各自就地赋三个字段，散在**六个地方** —— 少清一个就留下半个状态（`forStep=false` 却带着
    //  上一次的 `gesture`），而从代码上看不出来（见 `docs/architecture/decoupling.md` 判据 3）。

    /**
     * 网格那条入口：给 [g] 这一档挑应用（挑中的写成 `app:` / `window:` 绑给这个手势）。
     *
     * @param comboId 非空 = 这一趟是给**那一档组合键**挑（[g] 是它的短按 / 长按）。
     * @param sceneAt 非负 = 这一趟是给**场景表里第几块**挑（[g] 是那一块里的哪一档）——
     *   与 [comboId] 互斥：一块要么属于某颗键、要么属于某档组合键，由草稿自己记着。
     */
    fun openForGesture(
        g: GestureTrigger,
        comboId: String? = null,
        sceneAt: Int = NOT_FOR_SCENE,
    ) {
        gesture = g
        this.comboId = comboId
        this.sceneAt = sceneAt
        forStep = false
        replaceStep = -1
        branchTarget = NOT_FOR_BRANCH
        branchConditionIndex = -1
        branchKind = ConditionKind.APP
        stopAppState.value = false
        //  这两位的清场纪律与 `openForStep` / `openForRelease` 一样，以前只有这一支漏了：
        //  上一趟「松手执行额外动作」留下的 `forRelease` 会让 `takePickTarget` 优先返回
        //  `Release`，于是网格里挑中的应用被写进 `longRelease`，而这一档的绑定没变。
        forRelease = false
        releaseComboId = null
        releaseChain = false
        forCommonCommand = false
        replaceCommonToken = null
    }

    /**
     * 「结束应用」那一格的入口：给 [g] 这一档挑应用，挑中的写成 `proc:stop:包名`。
     *
     *  与 [openForGesture] 只差 [stopApp] 这一位 —— 落盘那一步按它换 token（见
     *  `PageEntryWrite.onAppPicked`），其余「为谁挑」的账完全一样，所以两处各写一遍清场纪律
     *  不如共用 [openForGesture] 再来一支。
     */
    fun openForStopApp(
        g: GestureTrigger,
        comboId: String? = null,
        sceneAt: Int = NOT_FOR_SCENE,
    ) {
        openForGesture(g, comboId, sceneAt)
        stopAppState.value = true
    }

    /**
     * 「松手执行额外动作」那一趟：挑中的应用 / 组件 / 快捷方式写进这一档的**松手格** ——
     * 单键 / 组合写自己的 `longRelease`，场景块写**草稿里那一块**（见 `PickTarget.Release`）。
     *
     *  清场纪律与 [openForStep] 完全一样（那几位互斥，留着上一次的值会让写回落到别人头上）。
     *
     *  @param combo 非空 = 这一趟是给那一档组合键挑
     *  @param sceneAt 非负 = 这一趟是给**场景表里第几块**挑 —— 与 `combo` 互斥，由草稿自己记着
     */
    fun openForRelease(
        stop: Boolean = false,
        combo: String? = null,
        sceneAt: Int = NOT_FOR_SCENE,
        chain: Boolean = false,
    ) {
        gesture = null
        comboId = null
        this.sceneAt = sceneAt
        releaseComboId = combo
        releaseChain = chain
        forStep = false
        replaceStep = -1
        branchTarget = NOT_FOR_BRANCH
        branchConditionIndex = -1
        branchKind = ConditionKind.APP
        stopAppState.value = stop
        forRelease = true
        forCommonCommand = false
        replaceCommonToken = null
    }

    /**
     * 「常用命令」那条入口：给那张清单挑一条动作 —— 挑中的应用 / 结束应用落成清单里的一条。
     *
     *  与 [openForRelease] 同一个形状（挑法、清单、二级页、小窗全都一样），差别只有落点：
     *  那边写某个键的一个字段，这边写 `global.commonCommands`（见 `PageEntryWrite.writeToken`）。
     *  清场纪律照抄那一支 —— 这几位的账互斥，留着上一次的值会让写回落到别人头上。
     *
     *  @param stop `true` = 这一趟是「结束应用」（写 `proc:stop:包名`）
     *  @param replaceToken 非空 = **换掉清单里的这一条**（行菜单的「修改」那一趟）；
     *    `null` = 追加一条新的（右下角那颗加号）
     */
    fun openForCommonCommand(
        stop: Boolean = false,
        replaceToken: String? = null,
    ) {
        gesture = null
        comboId = null
        sceneAt = NOT_FOR_SCENE
        forStep = false
        replaceStep = -1
        branchTarget = NOT_FOR_BRANCH
        branchConditionIndex = -1
        branchKind = ConditionKind.APP
        stopAppState.value = stop
        forRelease = false
        releaseComboId = null
        releaseChain = false
        forCommonCommand = true
        replaceCommonToken = replaceToken
    }

    /**
     * 「添加命令」那条入口：给多条命令**加一步**（[replaceIndex] `< 0`）或换掉第 [replaceIndex] 步。
     *
     * @param stop `true` = 这一趟写 `proc:stop:包名`（抽屉里「应用控制 → 结束应用」那一档）
     */
    fun openForStep(
        replaceIndex: Int,
        stop: Boolean = false,
    ) {
        gesture = null
        comboId = null
        sceneAt = NOT_FOR_SCENE
        forStep = true
        replaceStep = replaceIndex
        branchTarget = NOT_FOR_BRANCH
        branchConditionIndex = -1
        branchKind = ConditionKind.APP
        stopAppState.value = stop
        forRelease = false
        forCommonCommand = false
        replaceCommonToken = null
    }

    /**
     * 「条件执行」那条入口：给第 [index] 块挑**条件应用**。
     *
     *  条件与动作的落点不同（条件存包名、动作存 token），所以**不需要**再记「这一趟写哪半个」：
     *  动作那一侧走「场景动作」页的网格（`BindingTarget.Scene`），与这张页面无关。
     *
     *  @param kind 写回**哪一档**（[ConditionKind.APP] / [ConditionKind.APP_NOT]）—— 两档的流程
     *    一模一样，只有写回去的那一位不同（见 [branchKind]）
     */
    fun openForBranch(
        index: Int,
        conditionIndex: Int = -1,
        kind: ConditionKind = ConditionKind.APP,
    ) {
        gesture = null
        comboId = null
        sceneAt = NOT_FOR_SCENE
        forStep = false
        replaceStep = -1
        branchTarget = index
        //  挑条件那一趟不写条件槽位以外的标记，留着上一趟的值只会在下一次挑选时误导
        branchConditionIndex = conditionIndex
        branchKind = kind
        //  条件只认包名：既没有「结束应用」那一档，也没有「松手额外动作」
        stopAppState.value = false
        forRelease = false
        forCommonCommand = false
        replaceCommonToken = null
    }

    /** 这趟「为谁挑」整个作废 —— 挑完了、写失败留在原地、或干脆没挑就退出来。 */
    fun clearTarget() {
        gesture = null
        comboId = null
        sceneAt = NOT_FOR_SCENE
        forStep = false
        replaceStep = -1
        branchTarget = NOT_FOR_BRANCH
        branchConditionIndex = -1
        branchKind = ConditionKind.APP
        stopAppState.value = false
        forRelease = false
        releaseComboId = null
        forCommonCommand = false
        replaceCommonToken = null
        // 本地选中与草稿一并作废：这一趟挑应用的账到此为止
        pending.value = null
        pickedApps.value = emptySet()
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
     *  **不碰**两类：`showSystemApps` 是落盘偏好（进页读一次，清了等于改设置）；
     *  `resumeSheetAt` 是**来源页**的账（挑完要回去把抽屉支起来），归来源页出栈时清。
     */
    fun resetSession() {
        clearTarget()
        forgetActivities()
        filter.value = ""
        activityFilter.value = ""
        smallWindow.value = false
        //  `stockAnim` 与 `smallWindow` 同性质（都是「这一趟以什么方式打开」的临时开关）：
        //  只收前者会让上一趟的 `app:a:` 残留到下一次挑应用。
        stockAnim.value = false
    }

    /**
     * 这一趟挑完了 —— 返回「挑中的东西写到哪儿」，标记一并收掉；`null` = 不是这两条路（写手势绑定）。
     *
     *  三档合成一个出口：它们互斥、判据又在同一个「为谁挑」组里，分三个方法时调用方得先自己
     *  判一次谁有效再去取 —— 那一步正是「两个字段都可能非空」的漏洞来源。
     */
    fun takePickTarget(): PickTarget? =
        when {
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

            forRelease -> {
                forRelease = false
                val combo = releaseComboId
                releaseComboId = null
                //  「哪一块」与「哪一档组合」同生共死：留着上一次的值，下一趟「打开应用」会把
                //  动作写进一块用户根本没在编的场景块
                val at = sceneAt
                sceneAt = NOT_FOR_SCENE
                val chain = releaseChain
                releaseChain = false
                PickTarget.Release(combo, at, chain)
            }

            forCommonCommand -> {
                forCommonCommand = false
                //  「换哪一条」与「加一条」同生共死：这一趟是修改时才带走那个 token
                val replace = replaceCommonToken
                replaceCommonToken = null
                PickTarget.CommonCommand(replace)
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
        XLog.d("forgetActivities：无场景清掉 $activityTarget")
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

    /**
     * 这一趟「给条件挑前台应用」**选中的全部包名** —— 那条条件支持多选（`包1||包2`，任一在前台即通过）。
     *
     *  为什么与 [pending] 分开：`pending` 是「一个目标」，动作那一侧（`app:` / `appshortcut:`）
     *  只能有一个；而条件这一侧可以是一组。合成一个类型会让另外三条路径（手势绑定、命令步骤、
     *  分叉动作）都跟着多一个「其实恒为单元素」的集合。
     *
     *  只在 [branchTarget] 有效时才有意义（给动作挑、给手势挑都是空的），进页时按那条规则已有的
     *  条件预填（见 `AppPickerUiState.beginVisit`）。
     */
    val pickedApps = mutableStateOf<Set<String>>(emptySet())

    /**
     * 这一趟进来时那条条件**已经选着**的包名 —— [pickedAppsChanged] 的基线。
     *
     *  跟着 [pickedApps] 一起在 `AppPickerUiState.beginVisit` 里定：那一处是这一页唯一一处
     *  「按这一趟重置」的地方，基线跟它同一刻写，判据才不会跨趟串味。
     */
    private var visitPicked: Set<String> = emptySet()

    /** 记下多选基线（`beginVisit` 写完 [pickedApps] 之后立刻调）。 */
    fun markPickedBaseline() {
        visitPicked = pickedApps.value
    }

    /**
     * 这一趟**改过**多选集合吗 —— 与 [windowTogglesChanged] 同一条账：改过、还没提交就退页，
     * 要把「放弃这次选择」那一问支起来。
     *
     *  多选那一趟（[forBranch]）点一行就是切换勾选：既不写草稿、也不动那两个开关。
     *  只按 `draft` / 开关判的话，这一退**什么都不会问** —— 勾掉的那几个包名连同整趟挑的
     *  静悄悄丢掉（真机口径「返回时的拦截在上面这个场景不生效」）。
     */
    val pickedAppsChanged: Boolean
        get() = pickedApps.value != visitPicked

    /**
     * 这一趟有没有**挑过、还没落盘**的东西 —— 退页前要不要问一句的**唯一**判据。
     *
     *  四样各自可能改过：草稿（二级页点了确认，提交在右下角那颗 FAB 上）、两个窗口开关、
     *  多选集合、「结束应用」那一趟挑中的那一行。前三样的出处见 [windowTogglesChanged] 与
     *  [pickedAppsChanged]，最后一样见 [stopAppPickedChanged]。
     *
     *  收在这里而不是写在调用点：这几个判据的基线都归本域管（进页那一刻定），
     *  组合口径也该在同一处，免得下次再加一样时又漏一处。
     */
    val unconfirmedChanges: Boolean
        get() = draft != null || windowTogglesChanged || pickedAppsChanged || stopAppPickedChanged

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
     * @param stockAnim 「使用原版动画」那一档 —— 与 [window] **互斥**（调用点保证两者不同时为真）：
     *   小窗走 ColorOS 自己的配方，塞不进原版那套启动包裹。只管「打开界面」，快捷方式没有这一档。
     */
    fun token(
        window: Boolean,
        stockAnim: Boolean = false,
    ): String =
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
                Action.App(target, stockAnim = stockAnim).token
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

    /**
     * 长按卡的「松手执行额外动作」：写的是这一档的**松手格** ——
     *  · [comboId] 为空且 [sceneAt] 非负 ⇒ 场景表里那一块（草稿里那一格，不落盘）；
     *  · [comboId] 非空 ⇒ 那一档组合键的 `longRelease`；
     *  · 两个都空 ⇒ 当前这一颗键的 `longRelease`。
     *
     *  [sceneAt] 与 [comboId] 互斥：一块要么属于某颗键、要么属于某档组合键。
     */
    data class Release(
        val comboId: String? = null,
        val sceneAt: Int = -1,
        /**
         * 写的是**「单击后长按」那一档**的松手格（`KeyConfig.clickThenLongRelease`）而不是长按的。
         *
         *  两者的挑法、清单、回填全是同一套，只有落点不同 —— 所以只加一位，不另开一条 PickTarget。
         */
        val chain: Boolean = false,
    ) : PickTarget

    /**
     * 「常用命令」那张清单：挑中的动作**追加成新的一条**，或**换掉已有的那一条**
     * （`global.commonCommands`）。
     *
     *  落点只有那一张表，挑的是「打开应用」还是「结束应用」已经写在 token 里了
     *  （`app:` / `proc:stop:`）。写成功之后自己退页，与 [Release] 同一条纪律。
     *
     *  @param replaceToken 非空 = 换掉清单里的这一条（行菜单「修改」那一趟挑的应用）；
     *    `null` = 追加一条（与右下角加号那一趟一致）
     */
    data class CommonCommand(
        val replaceToken: String? = null,
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
