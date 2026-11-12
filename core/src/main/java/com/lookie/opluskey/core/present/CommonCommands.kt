package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind

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
 * ## 加一条 = 加一行
 *
 *  界面那一段按条数列、不写死数量（见 `ui/screen/binding/CommonCommandsGroup`）；
 *  要不要显示整段由 `GlobalConfig.showCommonCommands` 决定（开关在「按键设置」页）。
 */
object CommonCommands {
    /**
     * 「常用命令」露不露出来 —— 现行 `false`（这个功能还没发布，入口先收起来）。
     *
     *  **一处开关管两道入口**：按键绑定页那一段（`ui/screen/binding/CommonCommandsGroup`）与
     * 「按键设置」页那颗「显示常用命令」（`GeneralScreen` 的 `commonCommandsRow`）。
     *
     *  表、执行侧的兜底（`CustomCommandRunner`）、配置位（`GlobalConfig.showCommonCommands`）
     *  **一律不动**：已经绑上去的 `cmd:` 照常执行，手改配置也能引用这几条 —— 收起来的只是入口。
     *  改回 `true` 两处一起回来，那两处的分支都还是齐的，不必重写。
     */
    const val SHOW = false

    /**
     * 一条现成命令。
     *
     * @param id 稳定身份 —— `cmd:` 后面就是它（`common.wechat_scan`）。**不要随文案改**
     * @param nameKey 界面上那个名字的字典键（按当前语言念）
     * @param nameFallback 字典缺这一条时的兜底名字
     * @param summaryKey 行上那行小字的字典键（说清这一条做什么）
     * @param summaryFallback 同上兜底
     * @param step 这一条命令要跑的动作写法（`Action.parse` 认得的那种）
     */
    class Entry(
        val id: String,
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
     * 微信「扫一扫」—— 走微信自己注册的 `weixin://` 入口，与手点桌面快捷方式同一个去处。
     *
     * **必须经 root 的 `am start`**：宿主在 `platform_app` 域，直接 `startActivity` 起别人的界面
     * 会被后台启动限制拦掉（见 `docs/device/actions/launch.md` 二十九）；这条写法在本机实测退出码 0
     * （见 `host/action/ShellRunner.startProcess` 的注释）。所以 [Entry.step] 走 `shell:`，
     * 按它起出来的命令也是「以 root 执行」（`Command.root` 的默认值）。
     */
    private const val WECHAT_SCAN_STEP =
        Action.SHELL_PREFIX + "am start -a android.intent.action.VIEW -d weixin://dl/scan"

    /** 界面上按这个顺序列 —— 顺序即显示顺序。 */
    val ENTRIES: List<Entry> =
        listOf(
            Entry(
                id = "common.wechat_scan",
                nameKey = Strings.COMMON_COMMAND_WECHAT_SCAN,
                nameFallback = "微信扫一扫",
                summaryKey = Strings.COMMON_COMMAND_WECHAT_SCAN_DESC,
                summaryFallback = "打开微信的扫码界面",
                step = WECHAT_SCAN_STEP,
            ),
        )

    /**
     * `cmd:` 后面那一段 → 一条内置命令；不是内置的返回 `null`。
     *
     *  用户命令库里同名的那一条**优先**（查库在调用方那一步先做）：用户自己写的东西不许被模块盖掉。
     */
    fun find(raw: String): Entry? = ENTRIES.firstOrNull { it.id == raw }

    /** 内置命令的**显示名**（按当前语言）；不是内置的返回 `null`（调用方照旧显示原名）。 */
    fun labelOf(raw: String): String? = find(raw)?.let { I18n.get(it.nameKey, it.nameFallback) }

    /**
     * 内置命令 → 一条**能直接跑**的 [Command]（单条、以 root 执行）。
     *
     *  执行侧拿它当命令库里那条用，`CustomCommandRunner` 那边不再区分「来自库」还是「来自这张表」。
     */
    fun commandOf(raw: String): Command? = find(raw)?.let { Command(name = it.id, kind = CommandKind.SINGLE, single = it.step) }
}
