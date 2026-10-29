package com.lookie.opluskey.ui.screen.about

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.seal.SEAL_ENABLED
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.core.seal.Verdict
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appVersionBadge
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.AppTheme
import com.lookie.opluskey.ui.model.PagePalette
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

// 「更多选项」页 —— 从首页「其他 → 更多选项」那一行进来（见 `Screen.ABOUT`）。
//
// 这一层只做三件事：**算**那几个进页一次的值（版本 / 签名）、**建**菜单那三个锚点、
// 按顺序把卡铺出来。内容在隔壁几个文件：
//   · `AboutCards.kt`       —— 运行状态卡 / 配置卡；
//   · `AboutMachineCard.kt` —— 「关于」卡；
//   · `AboutMenus.kt`       —— 那三行各自弹出来的菜单。
// 拆之前它是一个 378 行的函数，五段内容 + 三个菜单全平铺在一起。

/** 「更多选项」页。 */
@Composable
internal fun SettingsPage.AboutScreen(scrollBehavior: ScrollBehavior) {
    // 取版本号要过 PackageManager（见 [moduleVersionName]），本页每次重组都算一遍纯属白花。
    // 「运行状态」那张卡是 `@Composable` 读状态、会频繁重组，而版本号在整个进程生命里不变。
    //  这一页走 [appVersionBadge]（`version-1.1.0-release-2610201546`）：这一页本来就回答
    //  「我这个包是哪一个」，「什么时候出的」正是它要说的事。首页那条副标题走 [appVersionLabel]，
    //  仍按构建类型决定带不带。
    val version = remember { appVersionBadge() }
    // 「签名」那一行：也要跨进程问 PMS，同样进页算一次。
    //  判据读的是**本包**（`ModuleInfo.PACKAGE`）而不是当前进程所属的宿主，见 `core/seal/Seal`。
    val context = LocalContext.current
    val currentLang = AppLanguage.fromCode(configState.config.value.global.language)
    // 界面颜色那三档（跟随系统 / 浅色 / 深色）—— 与语言同一个读法：配置里存 code，界面认枚举。
    val currentTheme = AppTheme.ofCode(configState.config.value.global.theme)
    // 系统配色同理：配置里存色板 id，界面认枚举（认不出的落回默认档）。
    val currentPalette = PagePalette.ofId(configState.config.value.global.palette)
    //  **实际生效的语言档**才是这两行的缓存键：`signatureNote` / `signatureTitle` 里都嵌着
    //  字典文案（「签名」/「调试版本（不校验签名）」），只按 `context` 记的话切档不会重算。
    //  用配置档（`currentLang`）不够：AUTO 档下系统语言变了、生效语言跟着变，配置档仍是 AUTO。
    //  指纹本身与语言无关，切档时多算一次的代价只是一次 PackageManager 查询。
    val effectiveLang = I18n.effectiveLanguage
    //  指纹与语言无关（一次 PackageManager 查询），单独算一次；那一行**收起时**显示的是下面那句
    //  一行结论，指纹只在展开后才列出来（见 `ui/ExpandableRow`）。
    val fingerprint = remember(context) { Seal.ownFingerprint(context) }
    val signatureNote = remember(context, effectiveLang) { signatureNote(context) }
    // 标题平时是「签名」；签名与本包不符时**标题自己改口**（结论上移，摘要只剩指纹）
    val signatureTitle = remember(context, effectiveLang) { signatureTitle(context) }
    // 这只是个布尔（与语言无关），不必跟着语言重算
    val signatureOfficial = remember(context) { Seal.verdict(context) == Verdict.OFFICIAL }
    // 三行菜单的锚点与开关（语言 / 反馈问题 / 作者）—— 行与菜单共用一份，见 [AboutUiState]。
    val ui = remember { AboutUiState() }

    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。这一页首行是 `SmallTitle`（下面「运行状态」
        // 那一个），它自带 8dp 上内边距，**不另加** [PAGE_TOP_PADDING] —— 那一档是给首行是卡片的
        // 「选择应用」的。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        //  块与块之间的间距由小标题自带（上 16 / 下 0），页面不再写 `Spacer`。
        SectionTitle(I18n.get(Strings.MORE_SECTION_STATUS, "运行状态"))
        LinkStatusCard()

        SectionTitle(I18n.get(Strings.MORE_SECTION_APPEARANCE, "外观"))
        AppearanceCard(currentTheme, currentPalette, ui)

        SectionTitle(I18n.get(Strings.MORE_SECTION_CONFIG, "配置"))
        ConfigCard(currentLang, ui)

        SectionTitle(I18n.get(Strings.MORE_SECTION_ABOUT, "关于"))
        MachineCard(
            version,
            SignatureInfo(signatureTitle, signatureNote, fingerprint, signatureOfficial),
            ui,
        )

        //  **浮层宿主**（本身不占布局）：摆在列容器外面，否则块间距会在它们前后各留一档空档。
        FeedbackMenu(ui)
        AuthorMenu(ui)
        LanguageMenu(ui, currentLang)
        ThemeMenu(ui, currentTheme)
        PaletteMenu(ui, currentPalette)
        // 「配置」卡里中间那两行：切换配置 / 导出配置，都是同一份配置列表的菜单
        ProfileMenu(ui)
        ProfileExportMenu(ui)
        // 新建 / 重命名那个输入窗（入口在「切换配置」菜单的底部）
        ProfileNamingDialog(ui)
        // 删除配置的确认窗（同一个位置点进来，删完才真删）
        ProfileDeleteConfirmDialog(ui)
    }
}

/**
 * 「关于」卡里「签名」那一行要显示的四样 —— 标题 / 一句结论 / 指纹 / 是不是官方。
 *
 *  打成一个类只是为了让 `MachineCard` / `SignatureRow` 的入参不至于摊成五六个
 *  （detekt 的 `LongParameterList`），顺带把「这四样是同一行的事」写明。
 *
 * @param title 平时是「签名」，不符时改口成结论（见 [signatureTitle]）
 * @param note 收起时那一行（官方版是空串）
 * @param fingerprint 展开后列出的子行；读不到是 `null`
 * @param official 只决定行首图标的颜色
 */
internal class SignatureInfo(
    val title: String,
    val note: String,
    val fingerprint: String?,
    val official: Boolean,
)

/**
 * 「关于」卡里「签名」那一行的**标题** —— 平时就是「签名」，签名与本包不符时改口。
 *
 *  不符时把结论放到标题上（`签名不符`）：那一行只剩指纹，扫一眼标题就知道这包不是原版。
 *  而「是不是官方」这层结论**平时不占一行** —— 标题已经叫「签名」、指纹就摆在下面，
 *  再补一句「官方正版」只是把同一件事说两遍。
 */
private fun signatureTitle(context: Context): String =
    when (Seal.verdict(context)) {
        Verdict.UNOFFICIAL -> I18n.get(Strings.MORE_SIGNATURE_MISMATCH, "签名不符")
        else -> I18n.get(Strings.MORE_ABOUT_SIGNATURE, "签名")
    }

/**
 * 「关于」卡里「签名」那一行**收起时**显示的那一行 —— 一句结论，没有结论就是空串。
 *
 *  为什么这一行存在：封存那一层（`core/seal/Seal`）能挡住「换图重打包」，但**挡不住**
 *  「把比对与派生一起 patch 掉」的人；而**签名指纹他改不了** —— 改包必然重签，指纹就变。
 *  所以「一眼分真假」靠的是这一行：指纹展开就能拿去与发布页上那串对一遍。
 *
 *  三档各去哪儿：**官方**不给结论（标题已经叫「签名」）；**不符**的结论上了标题；
 *  **读不到**（PMS 拒绝 / 包不在）既不是「官方」也不是「被改过」，仍要说一句 ——
 *  混进任何一边都会误导（见 `Verdict`）。
 */
private fun signatureNote(context: Context): String =
    when (Seal.verdict(context)) {
        // 结论已上标题，这一行只留指纹（展开后看）
        Verdict.UNOFFICIAL -> {
            ""
        }

        Verdict.UNKNOWN -> {
            I18n.get(Strings.MORE_SIGNATURE_UNKNOWN, "读不到本包签名")
        }

        // debug 包的结论也是 OFFICIAL，但那一侧**根本没校验**（`SealPin` 那份里 SEAL_ENABLED=false），
        //  必须说明 —— 否则调试包看起来像「已验过的正版」。
        Verdict.OFFICIAL -> {
            if (SEAL_ENABLED) "" else I18n.get(Strings.MORE_SIGNATURE_DEBUG, "调试版本（不校验签名）")
        }
    }
