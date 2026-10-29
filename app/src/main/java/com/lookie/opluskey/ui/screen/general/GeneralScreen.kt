package com.lookie.opluskey.ui.screen.general

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.SHOW_HOME_MINI_ANIM
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.setColorOsApi
import com.lookie.opluskey.ui.setMiniWindowAnim
import com.lookie.opluskey.ui.setToastOnRun
import com.lookie.opluskey.ui.setVibrate
import com.lookie.opluskey.ui.setVibrateStrength
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

/**
 * 「通用设置」页 —— 从首页「其他」卡第一行进来（**唯一入口**，在「更多选项」上面，见 `HomeCards`）。
 *
 *  单独成页的原因：那几项（接口选择、振动、执行提示）是**全局**开关，与「这颗键绑了什么」无关；
 *  摆在首页会把常用的「启用 / 按键绑定」两行挤在中间，也不好在同一张卡里解释它们各自管什么。
 *
 *  两节：**接口选择**（走 ColorOS 专属接口还是 AOSP 标准接口）与**按键行为**（振动 / 执行提示）。
 *  两节吃同一道闸 —— 与首页原来那几行完全一致（模块总闸 + 链路齐 + 配置可写）。
 */
@Composable
internal fun SettingsPage.GeneralScreen(scrollBehavior: ScrollBehavior) {
    val cfg = configState.config.value
    // 与首页 `pageEnabled` 同一条判据（含主开关自己）：这几项改的是「执行时怎么动」，
    //  模块没启用时改了也不会生效，放用户去改只会误导。
    val pageEnabled = pageWritable()
    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。
        //  **不再另加 [PAGE_TOP_PADDING]**：这一页首行就是小标题，它自带的上间距（16 + 8）
        //  已经够撑开（与首页、更多选项同一笔账）。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        //  块与块之间的间距由小标题自带（与首页同一套），页面不再写 `Spacer`。
        SectionTitle(I18n.get(Strings.GENERAL_SECTION_INTERFACE, "接口选择"))
        InterfaceCard(pageEnabled)
        SectionTitle(I18n.get(Strings.HOME_BINDING_CARD_TITLE, "按键行为"))
        ActionsCard(pageEnabled)
    }
}

/**
 * 「接口选择」那张卡 —— 走 ColorOS 专属系统接口，还是 AOSP 标准接口。
 *
 *  单独一张卡而不是塞进「按键行为」：它决定的是**命令怎么落地**（走哪套系统接口），
 *  与「执行时振不振动、弹不弹提示」不是一件事。
 */
@Composable
private fun SettingsPage.InterfaceCard(pageEnabled: Boolean) {
    val colorOs = configState.config.value.global.colorOsApi
    CardList(
        rows =
            listOf(
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
            ),
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
                // 「前台转小窗时使用动画」：`zoom:mini` 那一下要不要播缩小动画。
                //  关（默认）= 原地收、无动画；开 = 原地收成小窗后紧接着让框架自己的动画器播那一段
                //  （插值 / 矩阵 / spring 全是框架在跑，模块只交起止参数，配方见 `docs/zoom-window.md` §十二）。
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
}

/**
 * 「执行时提示」那一行 —— 执行命令时弹一条 Toast（内容是**命令名**）。
 *
 *  它是**全局**项，不分命令 —— 它回答的是「按下去到底有没有被触发」，与「这条命令是什么」无关。
 *  判定侧读它见 `host/action/CustomCommandRunner`（宿主进程里执行时**现读**配置，
 *  所以拨完开关立刻生效，不必重启）。
 *
 *  单独一支只为让 [ActionsCard] 留在 detekt 的长度上限之内。
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
