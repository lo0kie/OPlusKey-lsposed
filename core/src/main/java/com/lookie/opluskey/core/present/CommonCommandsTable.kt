package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action

/**
 * 「常用命令」那张表 —— **生成物，改 `core/tables/common-commands.json`**。
 *
 *  生成器是 `tools/gen-static-tables.py`。每一项的字段含义见 [CommonCommands.Entry]；
 *  `name` / `summary` 是**字典键**，这里已经翻成 `Strings.X` 常量 —— 键写错当场编译失败，
 *  不会退成界面上那一行显示键名的样子。
 *
 *  兜底文案（`nameFallback` / `summaryFallback`）留在表里是对的：这两个键是**动态键**（存在
 *  表里、编译器看不见它有没有进字典），字典读不到时（宿主主进程那条路见
 *  `DictionaryLoader`）只有它能把这行念出来。
 */
internal val COMMON_COMMAND_ENTRIES: List<CommonCommands.Entry> =
    listOf(
        // 起组件、不走 weixin:// 深链（那条落的是网页壳 MMWebViewUI）；目标界面不 exported，只有 uid 0 过得去
        CommonCommands.Entry(
            id = "common.wechat_scan",
            brand = CommonCommands.Brand.WECHAT,
            root = true,
            nameKey = Strings.COMMON_COMMAND_WECHAT_SCAN,
            nameFallback = "微信扫一扫",
            summaryKey = Strings.COMMON_COMMAND_WECHAT_SCAN_DESC,
            summaryFallback = "打开微信的扫码界面",
            step = Action.SHELL_PREFIX + "am start -n com.tencent.mm/.plugin.scanner.ui.BaseScanUI",
        ),
        // 与扫一扫同一条通道；那页带 FLAG_SECURE，截图全黑 —— 正好当判据
        CommonCommands.Entry(
            id = "common.wechat_pay",
            brand = CommonCommands.Brand.WECHAT,
            root = true,
            nameKey = Strings.COMMON_COMMAND_WECHAT_PAY,
            nameFallback = "微信收付款",
            summaryKey = Strings.COMMON_COMMAND_WECHAT_PAY_DESC,
            summaryFallback = "打开微信的收付款页（付款码 / 收钱码）",
            step = Action.SHELL_PREFIX + "am start -n com.tencent.mm/.plugin.offline.ui.WalletOfflineCoinPurseUI",
        ),
        // 支付宝走 alipays:// 深链（组件名带版本味，appId 反而稳），不升权即可；单引号不能省
        CommonCommands.Entry(
            id = "common.alipay_scan",
            brand = CommonCommands.Brand.ALIPAY,
            root = false,
            nameKey = Strings.COMMON_COMMAND_ALIPAY_SCAN,
            nameFallback = "支付宝扫一扫",
            summaryKey = Strings.COMMON_COMMAND_ALIPAY_SCAN_DESC,
            summaryFallback = "打开支付宝的扫码界面",
            step = Action.SHELL_PREFIX + "am start -a android.intent.action.VIEW -d 'alipays://platformapi/startapp?appId=10000007'",
        ),
        // 与扫一扫同一条深链通道，只换 appId
        CommonCommands.Entry(
            id = "common.alipay_pay",
            brand = CommonCommands.Brand.ALIPAY,
            root = false,
            nameKey = Strings.COMMON_COMMAND_ALIPAY_PAY,
            nameFallback = "支付宝收付款",
            summaryKey = Strings.COMMON_COMMAND_ALIPAY_PAY_DESC,
            summaryFallback = "打开支付宝的收付款页",
            step = Action.SHELL_PREFIX + "am start -a android.intent.action.VIEW -d 'alipays://platformapi/startapp?appId=20000056'",
        ),
    )
