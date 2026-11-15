package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.CommonCommand

/**
 * 「常用命令」那张表 —— **由模块维护**的几条自定义命令（微信扫一扫这种）。
 *
 * ## 与用户自己的命令库是两套东西
 *
 *  它们不写进 [com.lookie.opluskey.core.config.CommandLibrary]：命令库是**用户的**那份清单
 *  （命令页里能编辑 / 删除），这几条是模块带的、用户改不了。绑定时写进配置的是 `cmd:<id>`
 *  （见 [Entry.token]），执行侧按名字在命令库里查不到时**再退到这张表**（[commandOf]）。
 *  于是：界面上它们单独一段、命令页里一条都不出现，而 `cmd:` 那一族读名字的地方仍然念得出人话。
 *
 *  名字用 **id 寻址**、不用落盘时的字面量：命令名会进配置并被 `cmd:` 引用，而界面语言随时可切
 *  —— 存字面量的话，切成英文之后那条 `cmd:微信扫一扫` 就再也点不亮（[labelOf] 按当前语言念）。
 *
 * ## 那张表在 JSON 里，不在这里
 *
 *  条目本身（id / 品牌 / 要不要 root / 字典键 / 动作写法）收在 `core/tables/common-commands.json`，
 *  由 `tools/gen-static-tables.py` 生成 [COMMON_COMMAND_ENTRIES]。加一条 = 那张表里加一项、
 *  再跑一次生成器；本文件只管「怎么念、怎么执行」，不再夹带数据。
 *
 *  **为什么是生成而不是运行时读**：这一层会被注入侧加载，而那边没有零依赖的 asset 读法
 *  （见 `core/i18n/DictionaryLoader` 的两条路）。生成物把这份开销留在编译期，代价是改表要跑一次脚本。
 *
 *  那几条命令**为什么这么写**（微信为什么用组件、支付宝为什么用深链、哪两条离不开 root）见
 *  `docs/device/actions/launch.md` 二十九 与表里每项的 `note`。
 *
 * ## 界面上按条数列
 *
 *  界面那一段不写死数量（见 `ui/screen/binding/CommonCommandsGroup`）；
 *  要不要显示整段由 `GlobalConfig.showCommonCommands` 决定（开关在「按键设置」页）。
 */
object CommonCommands {
    /**
     * 「常用命令」露不露出来 —— 现行 `true`（入口放出来了）。
     *
     *  **一处开关管两道入口**：按键绑定页那一段（`ui/screen/binding/CommonCommandsGroup`）与
     * 「按键设置」页那颗「显示常用命令」（`GeneralScreen` 的 `commonCommandsRow`）。
     *
     *  这一档只决定**入口**在不在：关掉它，表、执行侧的兜底（`CustomCommandRunner`）、
     *  配置位（`GlobalConfig.showCommonCommands`）都不受影响 —— 已经绑上去的 `cmd:` 照常执行，
     *  手改配置也能引用这几条。真要收起来就改成 `false`，两处入口一起消失，分支都还在。
     */
    const val SHOW = true

    /**
     * 这条命令属于哪个 App —— 界面按它取行首那颗品牌字形。
     *
     *  **这里只给标识、不给图**：字形是界面层的东西（`PageIcons`），而这一层在 `:core`，
     *  那边一引就是跨层依赖（`:core` 的 classpath 里没有 `:app`，编译期就断）。
     *  界面那一侧的翻译表只有一份，见 `ui/screen/binding/CommonCommandsGroup`。
     */
    enum class Brand { WECHAT, ALIPAY }

    /**
     * 一条现成命令。
     *
     * @param id 稳定身份 —— `cmd:` 后面就是它（`common.wechat_scan`）。**不要随文案改**
     * @param brand 这条命令属于哪个 App（决定行首那颗字形）
     * @param root 要不要以 root 跑这条 `shell:` —— 只有**目标界面不 `exported`** 那两条非 root 起不来，
     *   其余一律不升权（理由见 [ENTRIES] 的说明）
     * @param nameKey 界面上那个名字的字典键（按当前语言念）
     * @param nameFallback 字典缺这一条时的兜底名字
     * @param summaryKey 行上那行小字的字典键（说清这一条做什么）
     * @param summaryFallback 同上兜底
     * @param step 这一条命令要跑的动作写法（`Action.parse` 认得的那种）
     */
    class Entry(
        val id: String,
        val brand: Brand,
        val root: Boolean,
        val nameKey: String,
        val nameFallback: String,
        val summaryKey: String,
        val summaryFallback: String,
        val step: String,
    ) {
        /** 绑进配置的取值（`cmd:common.wechat_scan`）—— 与 `Action.Custom` 的写法同一套。 */
        val token: String = Command.PREFIX + id
    }

    /**
     * 表里那四条**为什么这么写** —— 条目本身在 `core/tables/common-commands.json`，
     *  生成成 [COMMON_COMMAND_ENTRIES]（顺序即显示顺序）。这段只说「为什么是这几条写法」。
     *
     * ## 微信：起 Activity 组件，不走 `weixin://` 深链
     *
     *  真机实测（2026-11-12，WeChat 8.x）：`weixin://dl/scan` 落的是 `MMWebViewUI`（一个网页壳），
     *  不是取景框；`weixin://dl/offline` 更糟，被转到浏览器里开 Custom Tab。换成组件两个都对：
     *  `BaseScanUI` 当场出取景框，`WalletOfflineCoinPurseUI` 出收付款页（那页带 `FLAG_SECURE`，
     *  截图全黑 —— 正好当判据）。
     *
     * **这两条离不开 root**：宿主在 `platform_app` 域，直接 `startActivity` 起别人的界面
     * 会被后台启动限制拦掉（见 `docs/device/actions/launch.md` 二十九）；而这两个界面**不是
     *  `exported`**，非 root 身份一样起不来（`SecurityException: … not exported from uid …`），
     *  只有 uid 0 过得了那道检查。所以这两条标 `root = true` —— 拿不到 root 就报「没授权」，
     *  不退回普通 shell 去撞一个必然失败的 `SecurityException`。
     *
     *  非 root 的退路都试过、都不成立：`weixin://dl/scan` 落网页壳；微信那个「扫一扫」快捷方式
     *  在真机上 `ShortcutInfo.getIntent()` 返回 `null`，只能走系统代发，而系统代发会被 BAL 拦
     *  （同一份文档二十九）。要「扫一扫」又要不升权，只能等微信自己把那个界面放开。
     *
     * ## 支付宝：走 `alipays://` 深链
     *
     *  组件名带版本味，`appId` 反而稳。真机实测：`appId=10000007` 直落扫码界面
     *  （`com.alipay.mobile.scan.as.main.MainCaptureActivity`）、`appId=20000056` 落收付款页
     *  （`com.alipay.mobile.onsitepay.merge.OnsitepayActivity`）。
     *
     *  **这两条不升权**（`root = false`）：目标走的是支付宝自己注册的**深链**，`exported` 那道
     *  检查过得去；宿主自己起界面属于 BAL 允许的那一档（见 `docs/device/actions/launch.md` 二十九）。
     *  机器没 root 也照常能用 —— 与微信那两条的区别就在这里。
     *
     *  **单引号不能省**：`?` 在 shell 里是通配符、`&` 会把命令截断。整条是以**一个实参**交给
     *  `sh -c` 的（见 `ShellRunner.startProcess`），引号按字符串原样进去即可。
     */
    val ENTRIES: List<Entry> = COMMON_COMMAND_ENTRIES

    /**
     * `cmd:` 后面那一段 → 一条内置命令；不是内置的返回 `null`。
     *
     *  用户命令库里同名的那一条**优先**（查库在调用方那一步先做）：用户自己写的东西不许被模块盖掉。
     */
    fun find(raw: String): Entry? = ENTRIES.firstOrNull { it.id == raw }

    /** 内置命令的**显示名**（按当前语言）；不是内置的返回 `null`（调用方照旧显示原名）。 */
    fun labelOf(raw: String): String? = find(raw)?.let { I18n.getOr(it.nameKey, it.nameFallback) }

    /**
     * 一条**动作写法** → 模块自带的那一条（`cmd:common.wechat_scan` → [Entry]）；
     * 不是自带那几条的写法返回 `null`。
     *
     *  界面上要「这一条有没有自己的小字与品牌字形」时用这一支 —— 用户自己收录的动作
     *  多半没有，那时按动作本身取字形、也不画小字。
     */
    fun entryOf(token: String): Entry? = token.takeIf { it.startsWith(Command.PREFIX) }?.let { find(it.removePrefix(Command.PREFIX)) }

    /**
     * 内置命令 → 一条**能直接跑**的 [Command]（单条；要不要 root 由条目自己说，见 [Entry.root]）。
     *
     *  执行侧拿它当命令库里那条用，`CustomCommandRunner` 那边不再区分「来自库」还是「来自这张表」。
     */
    fun commandOf(raw: String): Command? =
        find(raw)?.let {
            Command(name = it.id, kind = CommandKind.SINGLE, single = it.step, root = it.root)
        }

    /**
     * 一条清单项在界面上叫什么 —— **用户起的名字优先**；没起过就按动作自己念。
     *
     *  为什么 `cmd:` 要单独一支：默认那几条存的是 `cmd:common.wechat_scan`，念法在 [labelOf] 里
     *  跟着界面语言走（存字面量的话，切英文之后这几行还是中文）。其余动作走 [commandTitleOf]，
     *  与命令页的步骤行、执行时提示同一套措辞。
     */
    fun displayName(cmd: CommonCommand): String {
        if (cmd.name.isNotBlank()) return cmd.name
        if (cmd.token.startsWith(Command.PREFIX)) {
            labelOf(cmd.token.removePrefix(Command.PREFIX))?.let { return it }
        }
        val action = Action.parse(cmd.token)
        return commandTitleOf(action).forSentence(action, { "" }, cmd.token)
    }
}
