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
 * 模块自带的**内置常用命令** —— 只是一份**候选清单**，默认不进任何配置。
 *
 *  读盘不再补它们（早先按 `SEED_VERSION` 一次性写进命令库与「常用命令」清单，已废弃）：要哪几条
 *  由用户在「常用命令」页点加号、在内置选择窗里勾选并确认 —— 那一支走 `addBuiltInCommands`
 *  （先按 [mergeCommandSeeds] 补命令库、再按 `cmd:<名字>` 钉进清单，两处各自去重）。
 *
 *  于是补进去之后它们没有第二条查询路径：正文、root / Shizuku 开关、改名、删除全在命令库里，
 *  与用户自己建的命令走同一套增删改查。
 *
 * ## 名字与开态
 *
 *  名字按**当时界面语言**取一次字面量（[I18n.get]），之后作为命令名落进命令库 —— 中途换语言不会
 *  把已有的那条改掉。微信那两条带「以 root 执行」（起的是不 `exported` 的 Activity，没有 root 时
 *  会在 `startActivity` 那一步被拒）；支付宝两条走自己的深链，不开 root 也能用。四个开关都落在
 *  各自那条命令上，用户随时能改。写法与原因见 `docs/device/actions/launch.md` 二十九。
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
