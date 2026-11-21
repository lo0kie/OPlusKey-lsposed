package com.lookie.opluskey.ui.screen.general

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.NoticeMode
import com.lookie.opluskey.core.model.ToastNotice
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.kit.NameDialog
import com.lookie.opluskey.ui.kit.SectionTitle
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.row.noticeModeMenu
import com.lookie.opluskey.ui.row.noticeModeRow
import com.lookie.opluskey.ui.row.rememberNoticeModeMenu
import com.lookie.opluskey.ui.screen.binding.ClickDelayCard
import com.lookie.opluskey.ui.screen.binding.SHOW_COMMON_COMMANDS
import com.lookie.opluskey.ui.screen.binding.clickGlobalParams
import com.lookie.opluskey.ui.screen.combo.HoldComboCard
import com.lookie.opluskey.ui.screen.combo.WaitComboCard
import com.lookie.opluskey.ui.screen.combo.comboPublicParams
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.write.setColorOsApi
import com.lookie.opluskey.ui.write.setMiniWindowAnim
import com.lookie.opluskey.ui.write.setNoticeMode
import com.lookie.opluskey.ui.write.setStepNotice
import com.lookie.opluskey.ui.write.setToastTemplate
import com.lookie.opluskey.ui.write.setVibrate
import com.lookie.opluskey.ui.write.setVibrateOnNone
import com.lookie.opluskey.ui.write.setVibrateStrength
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

/**
 * 「按键设置」页 —— 公共那份，**唯一的入口是首页「按键操作」卡的第二行**。
 *
 *  「按键绑定」页与「组合键编辑」页顶栏那颗设置键进的是**这一档**的「独立设置」
 *  （`Screen.KEY_SETTINGS`，见 `KeySettingsScreen`）—— 名字差一个字、改的对象完全不同：
 *  那一页改「这一颗键 / 这一组组合自己的覆盖」，本页改**所有档共用的默认值**。
 *  （「按键功能」页顶栏原先也有一颗进这里的齿轮，现在那一页顶栏只有「编辑 / 完成」。）
 *
 *  三节：**接口与显示**（走 ColorOS 专属接口还是 AOSP 标准接口、绑定页摆不摆常用命令）、**按键行为**（振动 / 执行提示）
 *  与**组合按键设置**（无操作时怎么走 / 两颗键之间等多久）。三节吃同一道闸 ——
 *  与首页那几行完全一致（模块总闸 + 链路齐 + 配置可写）。
 *
 * ## 与「独立设置」的关系
 *
 *  这一页上的都是**默认值**；每颗键（以及每一组组合键）可以在自己的「独立设置」页里
 *  另设一份，那几项**优先于**这里的 —— 判据只有一处：`Config` 上那五个 `xxxFor(owner)`。
 */
@Composable
internal fun SettingsPage.GeneralScreen(scrollBehavior: ScrollBehavior) {
    val pageEnabled = pageWritable()
    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。
        //  **不再另加 [PAGE_TOP_PADDING]**：这一页首行就是小标题，它自带的上间距（16 + 8）
        //  已经够撑开（与首页、更多选项同一笔账）。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        //  块与块之间的间距由小标题自带（与首页同一套），页面不再写 `Spacer`。
        SectionTitle(I18n.get(Strings.GENERAL_SECTION_INTERFACE))
        InterfaceCard(pageEnabled)
        SectionTitle(I18n.get(Strings.HOME_BINDING_CARD_TITLE))
        ActionsCard(pageEnabled)
        //  —— 单键（判定时机 + 连击等待窗口 + 单击和多击和长按延迟）——
        //  这一节是**全局默认值**：没开「使用独立设置」的键都按这一份判。
        //  前两行不吃「有没有多击手势」那道闸 —— 单键也要有个地方改「按下之后等多久」；
        //  第三行（三档「后长按」共用的按住时长）同样一直可调（用户口径「公共设置始终不禁用」）。
        //  逐键那一份在独立设置页里，闸门不同（见 `clickKeyParams`）。
        SectionTitle(I18n.get(Strings.KEY_SETTINGS_CLICK))
        ClickDelayCard(clickGlobalParams(pageEnabled))
        //  组合键那几项 —— 从「按键功能」页搬来：它们是**全局**设置，与「有哪几颗键」无关，
        //  与上面两节同一类。**按触发方式分成两节两张卡**（用户口径）：一条组合只认一种触发方式，
        //  两种方式的参数互不相干，并在一张卡里读起来像「同一套设置的四个旋钮」。
        //  两张卡本身在 `:combo` 包，与某一组「独立设置」页用的是同一份 —— 那边只换状态源。
        val combo = comboPublicParams(pageEnabled)
        SectionTitle(I18n.get(Strings.COMBO_SECTION_WAIT))
        WaitComboCard(combo)
        SectionTitle(I18n.get(Strings.COMBO_SECTION_HOLD))
        HoldComboCard(combo)
    }
}

/**
 * 「接口与显示」那张卡 —— 走 ColorOS 专属系统接口，还是 AOSP 标准接口；外加「显示常用命令」
 * 这一条只管页面显示的开关。
 *
 *  单独一张卡而不是塞进「按键行为」：接口那一条决定的是**命令怎么落地**（走哪套系统接口），
 *  「显示常用命令」决定的是**绑定页上摆不摆那一段** —— 两条都不碰「执行时振不振动、弹不弹提示」，
 *  与那张卡不是一件事。
 */
@Composable
private fun SettingsPage.InterfaceCard(pageEnabled: Boolean) {
    val cfg = configState.config.value
    val colorOs = cfg.global.colorOsApi
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_COLOROS_TITLE),
                        icon = PageIcons.ColorOsLink,
                        checked = colorOs,
                        // 吃整页总闸（含主开关自己）：主开关一关，这一行跟着灰
                        enabled = pageEnabled,
                    ) {
                        setColorOsApi(it)
                    },
                )
                //  「前台转小窗时使用动画」：`zoom:mini` 那一下要不要播缩小动画。
                //  关（默认）= 原地收、无动画；开 = 调框架自己的切换（`toggleFlexibleWindow`，
                //  落点与动画都由服务端算，配方见 `docs/features/zoom-window/notes.md` §六之二），
                //  没受理才退「先原地收、再补播框架动画」那一档。
                //  放在这一张卡里而不是「按键行为」：它与「走哪套系统接口」同一类（决定命令怎么落地），
                //  与振动 / 提示那几项不是一件事。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_MINI_ANIM_TITLE),
                        summaryOn = I18n.get(Strings.HOME_MINI_ANIM_DESC_ON),
                        summaryOff = I18n.get(Strings.HOME_MINI_ANIM_DESC_OFF),
                        icon = PageIcons.MiniAnim,
                        checked = cfg.global.miniWindowAnim,
                        enabled = pageEnabled,
                    ) {
                        setMiniWindowAnim(it)
                    },
                )
                //  「管理常用命令」—— 进清单页（收录哪几条、每条叫什么、换哪颗图标）。
                //  **不再配一颗「显示常用命令」**：常用命令本来就只是快捷方式，露不露由清单里
                //  有没有东西决定（空清单那一段只剩一行「去添加」），多一颗开关只是让人去关一个
                //  本该由内容决定的事。配置位仍留着（老配置读得懂），执行侧也照旧。
                if (SHOW_COMMON_COMMANDS) {
                    add(manageCommonCommandsRow(enabled = pageEnabled))
                }
            },
    )
}

/**
 * 「按键行为」那张卡 —— 振动反馈（+ 开着时的振动强度）/ 执行时提示（都是**全局**项，不分手势）。
 *
 *  「长按连发」那几个设置不在这张卡里（它们只管长按那一个手势，摆在首页会让人以为对三个手势
 *  都生效，见 `LongRepeatCard`）。
 */
@Composable
private fun SettingsPage.ActionsCard(pageEnabled: Boolean) {
    val cfg = configState.config.value
    val vibrate = cfg.global.vibrate
    val vibrateOnNone = cfg.global.vibrateOnNone
    // 「执行时提示」四档菜单开着没有 —— 与那一行同一支里（菜单行点开它，选完就收）。
    val noticeMenu = rememberNoticeModeMenu()
    // 「提示文案」那一行开着编辑弹窗没有 —— 弹窗与这一行同一张卡，状态就留在这支里。
    var editingToastTemplate by remember { mutableStateOf(false) }
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_VIBRATE_TITLE),
                        icon = PageIcons.Vibrate,
                        checked = vibrate,
                        // `enabled = false` 时 COUI 不挂 `clickableModifier`、开关也跟着灰，
                        // 行点击与开关点击自然都失效。
                        enabled = pageEnabled,
                    ) {
                        setVibrate(it)
                    },
                )
                //  「无操作时振动」：与上面那颗分开 —— 上面管「执行了动作」，这颗管
                //  「按下去有反应、但这一档什么都没绑」。**它依赖上面那颗**：振动反馈关着时
                //  整条振动链路都不走，再摆一颗「无操作时振动」等于让人调一个不起作用的东西
                //  （与下面那根强度同一口径）。显隐复用 `visible` 那套进出场动画。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_VIBRATE_ON_NONE_TITLE),
                        icon = PageIcons.VibrateOnNone,
                        checked = vibrateOnNone,
                        enabled = pageEnabled,
                        visible = vibrate,
                    ) {
                        setVibrateOnNone(it)
                    },
                )
                // 「振动强度」：只在振动反馈开着时出现 —— 关掉振动还摆一根强度滑杆，
                //  等于让人去调一个当前不起作用的东西。
                //  **连分隔线一起显隐**（`visible = vibrate`：线跟着这一行走）：只藏行会留一条孤零零的线。
                add(
                    CardRow.Custom(visible = vibrate) { position ->
                        VibrateStrengthRow(
                            position = position,
                            level = cfg.global.vibrateStrength,
                            enabled = pageEnabled,
                        )
                    },
                )
                add(
                    noticeModeRow(
                        mode = cfg.global.noticeMode,
                        enabled = pageEnabled,
                        anchor = noticeMenu.anchor,
                        onClick = noticeMenu::show,
                    ),
                )
                //  「多条命令逐条提示」：一条多条命令跑起来时每一步各念一句。
                //  **只在「执行时提示」不是「不提示」时出现**（整条链都不念的时候，逐不逐条无从谈起）——
                //  与「提示文案」同一档显隐，走 `visible` 那套进出场，连分隔线一起收。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_TOAST_STEP_NOTICE_TITLE),
                        icon = PageIcons.StepNotice,
                        checked = cfg.global.stepNotice,
                        enabled = pageEnabled,
                        visible = cfg.global.noticeMode != NoticeMode.NONE,
                    ) {
                        setStepNotice(it)
                    },
                )
                add(toastTemplateRow(cfg.global, pageEnabled) { editingToastTemplate = true })
            },
    )
    toastTemplateDialog(cfg.global.toastTemplate, editingToastTemplate) { editingToastTemplate = it }
    //  四档菜单：与那一行同一张卡（行的点击只负责把它打开）。流体云那行按「使用 ColorOS 接口」
    //  判能不能选 —— 与执行侧的回落同一条（见 `NoticeMode.effective`）。
    noticeModeMenu(
        state = noticeMenu,
        current = cfg.global.noticeMode,
        enabled = pageEnabled,
        colorOsApi = cfg.global.colorOsApi,
    ) { setNoticeMode(it) }
}

/**
 * 「提示文案」那一行 —— 只在「执行时提示」开着时出现（关着提示还摆一行改文案，
 * 等于让人去调一个当前不生效的东西，与「振动强度」那一行同一条口径）。
 *
 *  行尾副标题写的就是**当前模板**：清空时退回默认那句，所以这一行同时也是「改回去」的入口。
 *
 */
@Composable
private fun SettingsPage.toastTemplateRow(
    global: GlobalConfig,
    enabled: Boolean,
    onEdit: () -> Unit,
): CardRow =
    CardRow.Item(
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE),
        //  这两句**不给兜底**：里面带 `$`，而兜底审计是按源码字面量逐字比的 —— 写成 `\$` 就与字典
        //  对不上、不写反斜杠又编译不过。缺字典时显示的就是 key 本身，一眼看得出是哪一条。
        summary = global.toastTemplate.ifBlank { I18n.get(Strings.HOME_TOAST_TEMPLATE_DEFAULT) },
        icon = PageIcons.ToastTemplate,
        //  **不给行尾箭头**（用户口径）：那一颗的读法是「点了进下一页」（见 `PageIcons.EnterArrow`
        //  的注释），而这一行点开的是一张弹窗 —— 没有下一层可进。
        enabled = enabled,
        //  「不提示」这一档没有文案可改 —— 与原来「提示关着就不摆这一行」同一条口径
        visible = global.noticeMode != NoticeMode.NONE,
        onClick = onEdit,
    )

/**
 * 「管理常用命令」那一行 —— 进清单页（收录哪几条、每条叫什么、换哪颗图标）。
 *
 *  **单独一行，不再与上面一颗开关配成对**：那一段露不露已经由清单内容决定（见 `InterfaceCard`）。
 *  行尾箭头：点开是一整页。
 *
 *  **不画副标题**（用户口径）：标题已经说清这一行去哪，「添加、改名、换图标与固定」是进去
 *  之后才看得到的东西，摆在这一行只是把同一件事说两遍。
 */
private fun SettingsPage.manageCommonCommandsRow(enabled: Boolean): CardRow =
    CardRow.Item(
        title = I18n.get(Strings.COMMON_COMMANDS_MANAGE),
        icon = PageIcons.CommonCommandsManage,
        arrow = true,
        enabled = enabled,
        onClick = { navTo(Screen.COMMON_COMMANDS) },
    )

/**
 * 「提示文案」的编辑窗 —— 填一个值那一套（与按键重命名 / 每条滑杆同一个形状）。
 *
 *  空格子 = 用默认那句，所以这一档**允许空**（「确定」不会灰着）；框下面那行常驻小字写六个
 *  占位符各是什么，占位文案只写「留空将使用默认提示」（**不写默认那句本身**：那句随时会变，
 *  摆进占位就是一处会过期的说明）。**没有「重置」那颗按钮**：清空再按「确定」就是回到默认
 *  —— 落盘时把默认模板**直接赋过去**（与从前那颗按钮同一个落点），多一颗按钮只是把同一件事
 *  说两遍，还让按钮区从两颗变三颗。
 *
 *  @param onShowChange 弹窗自己开关时回报（`false` = 关）：状态留在 [ActionsCard] 里，
 *   因为它同时被那一行的点击用
 */
@Composable
private fun SettingsPage.toastTemplateDialog(
    initial: String,
    show: Boolean,
    onShowChange: (Boolean) -> Unit,
) {
    NameDialog(
        show = show,
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE),
        //  占位只写「留空则用默认」，**不再把默认那句摆进去**：那句话随时会变，摆进占位就成了
        //  一处会过期的说明；要看默认长什么样，清空并确定之后行尾副标题就是它。
        label = I18n.get(Strings.HOME_TOAST_TEMPLATE_PLACEHOLDER),
        //  六个占位符各是什么写在下面那行小字里 —— 填着内容时也一直在（这才是「变量提示」）。
        //  变量名单独排一档（与键名同一个写法）：这一行要读成「几个能填的变量」，
        //  纯文字读起来是一句话。顺序由 `ToastNotice.PLACEHOLDERS` 定。
        //  字典那一条只写「名字 说明」六组、**不写分隔符**：几项怎么分开归 `ChipLegend` 那两档
        //  间隙管，写进字里（以前的 `键名 / 手势 / …`）就会在换行处留一条挂着的斜杠。
        hint = I18n.get(Strings.HOME_TOAST_TEMPLATE_HINT),
        hintChips = ToastNotice.PLACEHOLDERS,
        icon = PageIcons.ToastTemplate,
        initial = initial,
        maxLength = MAX_TOAST_TEMPLATE_LENGTH,
        allowEmpty = true,
        onDismiss = { onShowChange(false) },
        onConfirm = { text ->
            onShowChange(false)
            //  空白 = 回到默认：**把默认模板直接写进配置**，不存空白。
            //  判据在 `ToastNotice.templateOrDefault` 一处（执行侧读的时候问的是同一句），
            //  这里只负责把那个值落盘 —— 默认模板也不在这里抄字面量。
            setToastTemplate(ToastNotice.templateOrDefault(text))
        },
    )
}

/**
 * 提示文案模板最多能输多少字。
 *
 *  比名字那档（`MAX_NAME_LENGTH`）长一截：模板是一整句话，`$key` / `$gesture` / `$action`
 *  三段都摆上就已经二十来个字符，再前后加几个字就顶到名字那档的上限了。
 */
private const val MAX_TOAST_TEMPLATE_LENGTH = 60

/**
 * 「振动强度」那一行 —— 三档滑杆（弱 / 中 / 强）。
 *
 *  轨道上把三个点都画出来（`keyPoints` 给全，而不是只标默认值那一个）：这一行要回答的是
 *  「一共几档」，只标一个默认值等于让用户自己猜还有没有别的档。
 *
 *  行首是**档位数字**（1 / 2 / 3），行尾写**这一档的说法**（弱 / 中 / 强）—— 两处不是同一件事：
 *  数字答「第几档、一共三档」，文字答「这一档是什么手感」。只有数字时，用户得自己把「2」翻成
 *  「中」，而滑杆从左到右是弱到强，越靠右越强这件事不写出来就得试。
 */
@Composable
private fun SettingsPage.VibrateStrengthRow(
    position: CardListPosition,
    level: Int,
    enabled: Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.HOME_VIBRATE_STRENGTH_TITLE),
                // 档位由行首那枚数字图标表达（1 / 2 / 3）
                icon = PageIcons.vibrateLevel(level),
                min = GlobalConfig.VIBRATE_WEAK,
                max = GlobalConfig.VIBRATE_STRONG,
                value = level,
                default = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
                step = 1,
                enabled = enabled,
                // 行尾跟着拖动的值一起变（滑杆那边每帧用 `dragging` 调这个 lambda）
                valueText = { vibrateStrengthLabel(it) },
                // 轨道上只画**默认值**那一个点（与其它滑杆一致）：三档的「第几档」已经由行首图标说了，
                //  再摆三个一样的圆点，读起来就是「三个默认值」。
                keyPoints = null,
            ),
    ) { setVibrateStrength(it) }
}

/**
 * 三档强度的说法（弱 / 中 / 强）—— 「振动强度」行尾那行字，与 `GlobalConfig` 的三个取值一一对应。
 *
 *  越界的取值落到「中」：滑杆与读盘两处都已钳在 `VIBRATE_STRENGTH_RANGE` 里，这里只是兜底
 *  （手改过的配置文件在钳制之前就会被读一次）。
 */
private fun vibrateStrengthLabel(level: Int): String =
    when (level) {
        GlobalConfig.VIBRATE_WEAK -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_WEAK)
        }

        GlobalConfig.VIBRATE_STRONG -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_STRONG)
        }

        else -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_MEDIUM)
        }
    }
