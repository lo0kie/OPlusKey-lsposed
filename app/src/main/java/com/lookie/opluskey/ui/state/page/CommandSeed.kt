package com.lookie.opluskey.ui.state.page

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind

/** 一条预设：命令本体 + 钉进「常用命令」清单时用它本来那颗品牌字形（`FluidIcons` 的 id）。 */
internal class CommandSeed(
    val command: Command,
    val icon: String,
)

/**
 * 模块自带的预设命令 —— **就是普通的自定义命令**，只在命令库第一次加载时补进去一次。
 *
 *  补完之后它们没有第二条查询路径：正文、root / Shizuku 开关、改名、删除全在命令库里，
 *  与用户自己建的命令走同一套增删改查。同时按命令名钉进「常用命令」清单（一条 `cmd:<名字>`
 *  的引用）—— 那一页与绑定页因此看得见这四条，而改正文仍然只改命令库那一份。
 *
 * ## 开态沿用之前的默认
 *
 *  微信那两条带「以 root 执行」（起的是不 `exported` 的 Activity，没有 root 时会在
 *  `startActivity` 那一步被拒）；支付宝两条走自己的深链，不开 root 也能用。四个开关都在各自的
 *  命令页里，用户随时能改。写法与原因见 `docs/device/actions/launch.md` 二十九。
 */
internal fun builtInCommandSeeds(): List<CommandSeed> =
    listOf(
        seed(
            Strings.COMMON_COMMAND_WECHAT_SCAN,
            icon = "brandwechat",
            root = true,
            command = "am start -n com.tencent.mm/.plugin.scanner.ui.BaseScanUI",
        ),
        seed(
            Strings.COMMON_COMMAND_WECHAT_PAY,
            icon = "brandwechat",
            root = true,
            command = "am start -n com.tencent.mm/.plugin.offline.ui.WalletOfflineCoinPurseUI",
        ),
        seed(
            Strings.COMMON_COMMAND_ALIPAY_SCAN,
            icon = "brandalipay",
            root = false,
            command = "am start -a android.intent.action.VIEW -d 'alipays://platformapi/startapp?appId=10000007'",
        ),
        seed(
            Strings.COMMON_COMMAND_ALIPAY_PAY,
            icon = "brandalipay",
            root = false,
            command = "am start -a android.intent.action.VIEW -d 'alipays://platformapi/startapp?appId=20000056'",
        ),
    )

/** 名字按当前语言念；正文统一是 `shell:` 那一档。 */
private fun seed(
    nameKey: String,
    icon: String,
    root: Boolean,
    command: String,
): CommandSeed =
    CommandSeed(
        command =
            Command(
                name = I18n.get(nameKey),
                kind = CommandKind.SINGLE,
                single = Action.SHELL_PREFIX + command,
                root = root,
                viaShizuku = false,
            ),
        icon = icon,
    )
