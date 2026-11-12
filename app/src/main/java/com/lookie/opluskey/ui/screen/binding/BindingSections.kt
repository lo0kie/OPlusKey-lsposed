package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.titleWithCount

/**
 * 「已保存的命令」那一块的小标题 —— **两页共用**（按键绑定 / 组合键编辑）。
 *
 *  有命令时可点：进「自定义命令」页去管命令库，落点是**命令列表**那一格（不是上次停着的表单页：
 *  留在列表页看得出全部命令，落在表单页只能看到一条草稿），草稿原样留着。
 *  **一条命令都没有时不接点击**：那一格里已经是「去创建自定义命令」（它会先 `reset()`，
 *  是「新增」那条路），同一趟去处摆两个入口，点哪个都行反而让人以为标题是另一件事。
 *
 *  **这一份自己被停用时也不接点击**（用户口径）：停用的键 / 组合不进「自定义命令」页 ——
 *  那一趟是「给这一档配命令」，而这一档根本不接管按键。标题仍照画（条数是事实），只是不再可点。
 *
 *  抽成一支有两个理由：两页的判据必须同一份（组合键那一页原先压根没接点击）；以及
 *  两边各写一遍的话，detekt 的长度上限会先替我们报警。
 */
@Composable
internal fun SettingsPage.SavedCommandsTitle(clickable: Boolean = true) {
    val commands = configState.commands.value
    val hasCommands = commands.isNotEmpty()
    SectionTitle(
        //  条数拼进标题（`自定义命令 (3)`）—— 与上面那几组同一个写法；0 条时也写出来，
        //  那一格里摆的是「去创建自定义命令」，标题上写 0 正好对得上。
        text = titleWithCount(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "自定义命令"), commands.size),
        click =
            if (hasCommands && clickable) {
                SectionTitleClick.Jump {
                    commandDraft.openListPage = true
                    navTo(Screen.COMMANDS)
                }
            } else {
                null
            },
    )
}

/**
 * 「按键绑定」页的**纵向顺序** —— 按键设置 → 常用命令 → 内置动作网格 → 已保存的命令 → 底部留白 → 入口浮层。
 *
 *  拆出来只是为了让 `BindingList` 只剩「参数 + 三条 effect」：这一页的纵向顺序里有一堆
 *  「哪一段之间该给间距」的细节（标题紧贴内容、内容之间才给档），和焦点账本混在一起时
 *  改一处间距得先把整个 666 行的函数读一遍。
 */
@Composable
internal fun SettingsPage.BindingGroups(
    target: BindingTarget,
    enabled: Boolean,
    st: BindingGridState,
    onCreateCommand: () -> Unit,
) {
    val g = target.gesture
    //  「按键设置」那一段 —— 只摆**这一档确实就是某颗键**的页面。
    //
    //  **「场景动作」页一张都不摆**（用户口径）：那几张卡调的是**整颗键**的判据
    //  （单击延迟 / 忽视双击 / 长按那几项），而注入侧在**按下那一刻**就按它们排枪 ——
    //  场景条件改不动它们。摆在这一页上，改的是整颗键：换一个条件进去编，看到的还是同一个值，
    //  读起来像「这一块单独配过」。长按那一档例外：它在这一页只剩「松手执行额外动作」那一族
    //  （见 `LongPressSections.ReleaseOnly`）—— 那一族是「这次按压的收尾」，与这一块挑的动作
    //  是同一件事的两半。
    //
    //  **组合键那一页不摆前两张**：组合没有单击 / 双击那两档（`ComboKey` 上就没有这两个位），
    //  摆出来是两张改不动的卡。长按那一档两边都有，走同一张卡（`LongPressCard`）。
    //  组合那一页的目标不是「一颗键」，那两张卡读的是 `KeyWatchState.editing` ——
    //  摆出来改的会是**另一颗键**的参数，比不摆更糟。

    // —— 按键设置：**在网格上面** ——
    //  先说「这一档怎么判」，再挑「绑什么」。**长按那一档三种目标共用同一张卡**
    //  （`LongPressCard`，参数走 `LongPressParams`）；单击 / 双击只有单键有（组合没有这两档）。
    Column {
        //  单击 / 双击那两张卡读的是 **`KeyWatchState.editing`**（`KeyConfig` 自己那几个位），
        //  所以只有「这一档就是它自己」的那一页摆 —— 单键页。
        //  **组合键那条路一张都不摆**：组合页摆出来改的是**另一颗键**的参数；组合的场景块同理
        //  （那一档属于一组组合，参数该在那一组的独立设置里）。**单键的场景块也不摆**：理由
        //  见上（那一块改不动这些参数）。长按那张卡不在这条判据里：它按 `target.longPress`
        //  自己取（组合读 `ComboKey`），三种目标都成立。
        val keyScoped = target is BindingTarget.Key

        //  **选了「跟随默认」就把这三张卡一起压灰**（用户口径）：那一档不归这一块管
        //  （继续往下找、最后回落到「按键绑定」里这一档），没有可调的项 ——
        //  在最上面那些卡里拧旋钮，读起来就是「调了没用」。
        //
        //  灰着点一下要给出**下一步该做什么**（「先换成一个动作」），而不是解释这套判定流程 ——
        //  用户此刻要的是「怎么让它亮起来」。
        //
        //  判据只对场景块成立：`Action.Follow` 只在场景那张网格里（`BRANCH_GROUPS`）可选，
        //  单键 / 组合的绑定里从来没有这一档，`actionIn` 也就返不回它。
        val followsDefault =
            target is BindingTarget.Scene && target.actionIn(configState.config.value) is Action.Follow
        val settingsEnabled = enabled && !followsDefault
        val followHint =
            if (followsDefault) {
                I18n.get(Strings.BINDING_FOLLOW_NO_SETTINGS, "[跟随默认] 没有可设的项，先换成一个动作")
            } else {
                null
            }

        if (keyScoped && g.isSingle) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            BindingSettingsCard(settingsEnabled, followHint)
        }

        //  双击那一页**不再摆卡**：「忽视无操作双击手势」挪进单击那张卡了（用户口径
        //  「移到单击里」）—— 它是「第二下抬手之后怎么办」，改它的动机是让单击更快，
        //  与「单击延迟」同属一次判定链（见 `BindingSettingsCard` 的类注释）。
        //  `GestureTrigger.isDouble` 留着：那是档位本身的判据，别再往这一页塞卡片。

        //  长按那张卡对**三种目标**都成立：单键与组合各读自己那几项（`LongPressParams`），
        //  场景块读的是这颗键的（见 `BindingTarget.Scene.longPress`，组合的场景表回 `null`）。
        //  场景块那一页只摆松手那一族 —— 由 `LongPressCard` 按目标给 `sections`，这一层不管。
        //
        //  三击 / 四击那两页一张卡都不摆：它们没有自己的判据可调（什么时候算数由「单击延迟」与
        //  最高档决定，那一位归这一颗键，摆在单击那一页）。
        //  「单击后长按」这一页摆它**自己那套**参数（长按延迟 / 连发 / 松手额外动作）——
        //  与长按那一页同一张卡的行、不同的字段（见 `ClickThenLongCard`）。
        //  只有单键有这一档：组合的两段建立在单键连击上。
        if (keyScoped && g == GestureTrigger.ClickThenLong) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            ClickThenLongCard(settingsEnabled, followHint)
        }

        if (g == GestureTrigger.LongPress && target.longPress(configState.config.value) != null) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            LongPressCard(target, enabled, followHint)
        }

        //  场景块里**「单击后长按」的松手** —— 只摆在这一档**自己那一页**（`ClickThenLong`）。
        //
        //  原先它按「只要是场景块就摆」写：于是**每一页**（单击 / 双击 / 三击 / 长按）底下都跟着
        //  一对「松手执行额外动作 + 额外动作」，与长按那一族逐字相同 —— 在长按那页上就是两行一模一样
        //  的东西（用户报「长按下面有两个一样的」）。它属于哪一档，就摆在哪一档的页面上。
        //  组合的场景页没有这一档（`sceneGesturePages` 只给单击 / 长按），于是那一侧也不该出现。
        //  单键 / 组合那一页的松手格在 `ClickThenLongCard` 里，不在这里。
        if (target is BindingTarget.Scene && g == GestureTrigger.ClickThenLong) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            SceneChainReleaseCard(target, settingsEnabled, followHint)
        }

        //  —— 常用命令：**在按键设置下面、网格上面** ——
        //  先摆「这一档怎么判」那张卡，再摆模块带的几条现成命令，最后才是整张动作网格。
        //  三种目标共用（这一页就是三种共用的一份实现）；关掉显示时整段不画
        //  （开关在「按键设置」页，见 `GeneralScreen`）。
        CommonCommandsGroup(target, enabled)

        BuiltInGrid(st, enabled)

        // 「自定义命令」这一段**始终在**：没有命令时给一行「去创建自定义命令」。
        //  小标题的跳转跟着这一份的启停走（停用了就不接点击，见 `SavedCommandsTitle`）。
        SavedCommandsTitle(clickable = bindingTargetStopped(target) == null)
        CustomCommandsGrid(st, enabled, onCreateCommand)
    }

    //  页底留白**不在这里**：这一页原先在末尾补 32 + 12 两个 Spacer（比别的页多出 12dp），
    //  现在统一由 `BindingList` 的 `.padding(bottom = PAGE_BOTTOM_PADDING)` 给 ——
    //  与首页及其它页同一处、同一档。

    // 入口格那一层的菜单（见 `BindingGridState.tileMenu`）。它**不占布局位置** ——
    // 真正的落点由那一格记下的触点决定，挂在这里只是让它跟着这一页一起进 / 出组合。
    BindingTileMenu(st)

    // 「自定义变量」那一格点开的两层抽屉（挑变量 → 挑对它做什么），同样不占布局。
}

/** 这一档是不是「单击」「双击」—— 两张设置卡按它分（`Click(n≥3)` 两样都不是，那两页不摆卡）。 */
internal val GestureTrigger.isSingle: Boolean get() = this == GestureTrigger.Click(1)

/** 见 [isSingle]。 */
internal val GestureTrigger.isDouble: Boolean get() = this == GestureTrigger.Click(2)
