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
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.ToastNotice
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.dialog.NameDialog
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.SHOW_HOME_MINI_ANIM
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.write.setColorOsApi
import com.lookie.opluskey.ui.write.setGlobalClickWindow
import com.lookie.opluskey.ui.write.setGlobalWaitMultiWindow
import com.lookie.opluskey.ui.write.setMiniWindowAnim
import com.lookie.opluskey.ui.write.setToastFluid
import com.lookie.opluskey.ui.write.setToastOnRun
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
        SectionTitle(I18n.get(Strings.GENERAL_SECTION_INTERFACE, "接口与显示"))
        InterfaceCard(pageEnabled)
        SectionTitle(I18n.get(Strings.HOME_BINDING_CARD_TITLE, "按键行为"))
        ActionsCard(pageEnabled)
        //  —— 单击（判定时机 + 单击延迟）——
        //  这一节是**全局默认值**：没开「使用独立设置」的键都按这一份判。
        //  它不吃「有没有多击手势」那道闸 —— 单键也要有个地方改「按下之后等多久」；
        //  逐键那一份在独立设置页里，只有开了多击才可调（见 `BindingSettingsCard`）。
        SectionTitle(I18n.get(Strings.KEY_SETTINGS_CLICK, "单击"))
        ClickDelayCard(pageEnabled)
        //  组合键那两项（无操作时怎么走 / 两颗键之间等多久）—— 从「按键功能」页搬来：
        //  它们是**全局**设置，与「有哪几颗键」无关，与上面两节同一类。
        SectionTitle(I18n.get(Strings.COMBO_SETTINGS_SECTION, "组合按键设置"))
        ComboSettingsCard(pageEnabled)
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
                        title = I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"),
                        // 开 = 走专属接口，关 = 走 AOSP —— 两句都只说「现在走哪条路」
                        summaryOn = I18n.get(Strings.HOME_COLOROS_DESC_ON, "部分命令走 ColorOS 专属接口"),
                        summaryOff = I18n.get(Strings.HOME_COLOROS_DESC_OFF, "部分命令改用 AOSP 标准接口"),
                        icon = PageIcons.ColorOsLink,
                        checked = colorOs,
                        // 吃整页总闸（含主开关自己）：主开关一关，这一行跟着灰
                        enabled = pageEnabled,
                    ) {
                        setColorOsApi(it)
                    },
                )
                //  「管理常用命令」—— 进清单页（收录哪几条、每条叫什么、换哪颗图标）。
                //  **不再配一颗「显示常用命令」**：常用命令本来就只是快捷方式，露不露由清单里
                //  有没有东西决定（空清单那一段只剩一行「去添加」），多一颗开关只是让人去关一个
                //  本该由内容决定的事。配置位仍留着（老配置读得懂），执行侧也照旧。
                if (CommonCommands.SHOW) {
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
    // 「提示文案」那一行开着编辑弹窗没有 —— 弹窗与这一行同一张卡，状态就留在这支里。
    var editingToastTemplate by remember { mutableStateOf(false) }
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"),
                        summaryOn = I18n.get(Strings.HOME_VIBRATE_DESC_ON, "每次执行动作振动一次"),
                        summaryOff = I18n.get(Strings.HOME_VIBRATE_DESC_OFF, "执行动作时不振动"),
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
                //  「按下去有反应、但这一档什么都没绑」。不跟着 `vibrate` 显隐：两者互不依赖。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_VIBRATE_ON_NONE_TITLE, "无操作时振动"),
                        summaryOn = I18n.get(Strings.HOME_VIBRATE_ON_NONE_DESC_ON, "未配置动作时同样振动"),
                        summaryOff = I18n.get(Strings.HOME_VIBRATE_ON_NONE_DESC_OFF, "未配置动作时不振动"),
                        icon = PageIcons.VibrateOnNone,
                        checked = vibrateOnNone,
                        enabled = pageEnabled,
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
                add(toastRow(cfg.global.toastOnRun, pageEnabled))
                add(toastFluidRow(cfg.global.toastFluid, pageEnabled, visible = cfg.global.toastOnRun))
                add(toastTemplateRow(cfg.global, pageEnabled) { editingToastTemplate = true })
                // 「前台转小窗时使用动画」：`zoom:mini` 那一下要不要播缩小动画。
                //  关（默认）= 原地收、无动画；开 = 原地收成小窗后紧接着让框架自己的动画器播那一段
                //  （插值 / 矩阵 / spring 全是框架在跑，模块只交起止参数，配方见 `docs/features/zoom-window/notes.md` §十二）。
                //  **现行隐藏**（[SHOW_HOME_MINI_ANIM] = false）：这一档还没收敛（终点几何是竖屏标定值、
                //  横屏播不了），先把入口收起来 —— 配置字段与执行侧都留着，改回 true 即恢复。
                if (SHOW_HOME_MINI_ANIM) {
                    add(
                        CardRow.Switch(
                            title = I18n.get(Strings.HOME_MINI_ANIM_TITLE, "前台应用转小窗时使用动画"),
                            summaryOn = I18n.get(Strings.HOME_MINI_ANIM_DESC_ON, "前台转小窗时播放缩小动画"),
                            summaryOff = I18n.get(Strings.HOME_MINI_ANIM_DESC_OFF, "前台转小窗时不播动画"),
                            icon = PageIcons.MiniWindow,
                            checked = cfg.global.miniWindowAnim,
                            enabled = pageEnabled,
                        ) {
                            setMiniWindowAnim(it)
                        },
                    )
                }
            },
    )
    toastTemplateDialog(cfg.global.toastTemplate, editingToastTemplate) { editingToastTemplate = it }
}

/**
 * 「用流体云提示」那一行 —— 只在「执行时提示」开着时出现（与提示文案同一档）。
 *
 *  它只换**落点**：关着走注入侧那颗系统身份的 Toast，开着走宿主自己的流体云胶囊。
 *  文案不跟着变 —— 同一句 `$key - $gesture → $action` 两个地方都念得对。
 *  图标来自被执行的那个动作（界面层预热成 png，宿主按 token 找回来）。
 */
private fun SettingsPage.toastFluidRow(
    checked: Boolean,
    enabled: Boolean,
    visible: Boolean,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.HOME_TOAST_FLUID_TITLE, "用流体云提示"),
        summaryOn = I18n.get(Strings.HOME_TOAST_FLUID_DESC_ON, "显示在状态栏的流体云里"),
        summaryOff = I18n.get(Strings.HOME_TOAST_FLUID_DESC_OFF, "以 Toast 弹出"),
        icon = PageIcons.FormatFluid,
        checked = checked,
        enabled = enabled,
        visible = visible,
    ) {
        setToastFluid(it)
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
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE, "提示文案"),
        //  这两句**不给兜底**：里面带 `$`，而兜底审计是按源码字面量逐字比的 —— 写成 `\$` 就与字典
        //  对不上、不写反斜杠又编译不过。缺字典时显示的就是 key 本身，一眼看得出是哪一条。
        summary = global.toastTemplate.ifBlank { I18n.get(Strings.HOME_TOAST_TEMPLATE_DEFAULT) },
        icon = PageIcons.ToastTemplate,
        //  行尾给箭头：这一行点开的是**一张弹窗**（改文案），与「页内下一层」同一档的读法
        arrow = true,
        enabled = enabled,
        visible = global.toastOnRun,
        onClick = onEdit,
    )

/**
 * 「管理常用命令」那一行 —— 进清单页（收录哪几条、每条叫什么、换哪颗图标）。
 *
 *  **单独一行，不再与上面一颗开关配成对**：那一段露不露已经由清单内容决定（见 `InterfaceCard`）。
 *  行尾箭头：点开是一整页。
 */
private fun SettingsPage.manageCommonCommandsRow(enabled: Boolean): CardRow =
    CardRow.Item(
        title = I18n.get(Strings.COMMON_COMMANDS_MANAGE, "管理常用命令"),
        summary = I18n.get(Strings.COMMON_COMMANDS_MANAGE_DESC, "添加、改名、换图标与固定"),
        icon = PageIcons.CommonCommandsManage,
        arrow = true,
        enabled = enabled,
        onClick = { navTo(Screen.COMMON_COMMANDS) },
    )

/**
 * 「提示文案」的编辑窗 —— 填一个值那一套（与按键重命名 / 每条滑杆同一个形状）。
 *
 *  空格子 = 用默认那句，所以这一档**允许空**（「确定」不会灰着）；框下面那行常驻小字写三个
 *  占位符各是什么，占位文案只写「留空将使用默认提示」（**不写默认那句本身**：那句随时会变，
 *  摆进占位就是一处会过期的说明）。按钮区多一颗「重置」：按下即把默认值写进配置并关窗。
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
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE, "提示文案"),
        //  占位只写「留空则用默认」，**不再把默认那句摆进去**：那句话随时会变，摆进占位就成了
        //  一处会过期的说明；要看默认长什么样，清空（或按「重置」）之后行尾副标题就是它。
        label = I18n.get(Strings.HOME_TOAST_TEMPLATE_PLACEHOLDER, "留空将使用默认提示"),
        //  六个占位符各是什么写在下面那行小字里 —— 填着内容时也一直在（这才是「变量提示」）。
        //  变量名画成**名字框**（与键名那档标签同一份样式）：这一行要读成「几个能填的变量」，
        //  纯文字读起来是一句话。顺序由 `ToastNotice.PLACEHOLDERS` 定。
        hint = I18n.get(Strings.HOME_TOAST_TEMPLATE_HINT),
        hintChips = ToastNotice.PLACEHOLDERS,
        icon = PageIcons.ToastTemplate,
        initial = initial,
        maxLength = MAX_TOAST_TEMPLATE_LENGTH,
        allowEmpty = true,
        //  「重置」= **把默认那句存进去、关窗**：与按「确定」落下别处一样是一次落盘，只是值取默认。
        //  默认文案只在 `ToastNotice.DEFAULT_TEMPLATE` 存一份，这里不抄字面量。
        onReset = {
            onShowChange(false)
            setToastTemplate(ToastNotice.DEFAULT_TEMPLATE)
        },
        onDismiss = { onShowChange(false) },
        onConfirm = { text ->
            onShowChange(false)
            setToastTemplate(text)
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
 * 「执行时提示」那一行 —— 执行命令时弹一条 Toast（内容是**命令名**）。
 *
 *  它是**全局**项，不分命令 —— 它回答的是「按下去到底有没有被触发」，与「这条命令是什么」无关。
 *  执行侧在 `host/GestureExec` 的 `noticeIfNeeded`（一次手势走一遍，`cmd:` 与内置动作都覆盖）；
 *  那边每按一次**现读**配置，所以拨完开关立刻生效，不必重启。
 *  每颗键 / 每组组合可以在「独立设置」里另设一份（`Config.effectiveFor` 判，本页是默认值）。
 *
 */
private fun SettingsPage.toastRow(
    checked: Boolean,
    enabled: Boolean,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"),
        summaryOn = I18n.get(Strings.HOME_TOAST_DESC_ON, "每次执行动作弹一条提示"),
        summaryOff = I18n.get(Strings.HOME_TOAST_DESC_OFF, "执行动作时不弹提示"),
        icon = PageIcons.CommandToast,
        checked = checked,
        enabled = enabled,
    ) {
        setToastOnRun(it)
    }

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
                title = I18n.get(Strings.HOME_VIBRATE_STRENGTH_TITLE, "振动强度"),
                summary = I18n.get(Strings.HOME_VIBRATE_STRENGTH_DESC, "命令触发时振动的力度"),
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
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_WEAK, "弱")
        }

        GlobalConfig.VIBRATE_STRONG -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_STRONG, "强")
        }

        else -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_MEDIUM, "中")
        }
    }

/**
 * 「单击」那张卡（**公共设置**）—— 判定时机 + 单击延迟。
 *
 *  这一份是**全局默认值**：没开「使用独立设置」的键都按它判（广播前由
 *  `KeyConfig.fieldValues(global)` 解析成生效值）。逐键那一份在独立设置页，那边**只在开了多击手势时
 *  可调**（那一页管的是「这颗键的多击怎么判」）；这里**不吃那道闸** —— 单键也要有个地方
 *  改「按下之后等多久」，否则一颗多击都没开的键就再也调不了单击延迟。
 *
 *  两根的关系与逐键那一页一样：判定时机决定下面这根滑杆**写哪一位**
 *  （`waitMultiWindow` 关着写 `doubleClickMs`，开着写 `multiWaitMs`），两个值分开存。
 */
@Composable
private fun SettingsPage.ClickDelayCard(pageEnabled: Boolean) {
    val g = configState.config.value.global
    val multi = g.waitMultiWindow
    //  0 = 「跟随单击延迟」（老配置长这样），滑杆要显示一个具体值
    val windowValue =
        if (multi) {
            g.multiWaitMs.takeIf { it > KeyConfig.FOLLOW_DOUBLE_CLICK_MS } ?: g.doubleClickMs
        } else {
            g.doubleClickMs
        }
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW, "固定窗口起点"),
                        summaryOn =
                            I18n.get(
                                Strings.BINDING_WAIT_MULTI_WINDOW_ON,
                                "从首次按下起算一个窗口，不随后续按压延长",
                            ),
                        summaryOff =
                            I18n.get(
                                Strings.BINDING_WAIT_MULTI_WINDOW_OFF,
                                "每次松手重新起算，按压越久等待越长",
                            ),
                        icon = PageIcons.CollectWindow,
                        //  开关说的是「固定窗口起点」= `waitMultiWindow` 的反面（与逐键那颗同一个口径）
                        checked = !multi,
                        enabled = pageEnabled,
                    ) { collect ->
                        setGlobalWaitMultiWindow(!collect)
                    },
                )
                add(
                    CardRow.Custom { position ->
                        SliderRow(
                            position = position,
                            spec =
                                SliderSpec(
                                    //  标题与逐键那一根统一：两种判定时机问的都是「按下去之后等多久」
                                    title = I18n.get(Strings.BINDING_WAIT_WINDOW, "等待窗口"),
                                    icon = PageIcons.DelayTap,
                                    summary = I18n.get(Strings.BINDING_WAIT_WINDOW_DESC, "多击判定所用的窗口长度"),
                                    //  上下限与逐键那一根统一（切换判定时机时范围不跟着跳）
                                    min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                                    max = KeyConfig.MAX_MULTI_WAIT_MS,
                                    value = windowValue,
                                    default = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                                    enabled = pageEnabled,
                                ),
                        ) { v -> setGlobalClickWindow(v) }
                    },
                )
            },
    )
}
