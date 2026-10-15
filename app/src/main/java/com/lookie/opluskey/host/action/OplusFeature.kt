package com.lookie.opluskey.host.action

/**
 * ColorOS 系统能力的调用表（`oplus:` 动作）。
 *
 * 调用目标取自系统自己的侧键场景表（真机 `content://uri.gesturemanagerprovider/action_button`
 * 的 `scene_name` / `scene_owner` / `click_action_info` / `long_press_action_info` 等列），
 * 但那张表**不带 extras，也区分不出「起普通服务」和「bind 后发 Message」**，`scene_owner` 还会过期 ——
 * 这几样都是对着目标 APK 反编译核的，见 `docs/device-findings.md` 十七。
 *
 * 注意：这一族**多数是「单击档只回一句提示、长按档才真正干活」**，所以下面几行的取值不都对着 `click_*`。
 *
 * 这些服务 / 界面都带签名级权限（`oppo.permission.OPPO_COMPONENT_SAFE` 等），宿主恰好持有，
 * 所以从宿主进程直接起即可 —— 不需要 root，也不需要绕 system_server。
 */
internal object OplusFeature {

    /**
     * 一个能力的调用目标。
     *
     * @param owner 场景表里的 `scene_owner`
     * @param service 场景表里的 `click_action_info`（完整类名）
     * @param action 该服务在清单里声明的 action；`null` = 只按组件起
     * @param extras 除 action 外还要带的 extra（`Int` / `String`）
     * @param foreground 目标在清单里声明了 special-use 前台类型 —— 决定先试哪种起法
     * @param activity 目标是**界面**（场景表 `*_action_type=activity`）—— 用 `startActivity` 起
     * @param message Messenger 绑定型服务要发的 `Message.what`（1=单击 / 2=长按）；
     *   `null` = 走 `startService`。这类服务没有 `onStartCommand`，起服务对它们是空操作
     */
    class Target(
        val owner: String,
        val service: String,
        val action: String? = null,
        val extras: Map<String, Any> = emptyMap(),
        val foreground: Boolean = false,
        val activity: Boolean = false,
        val message: Int? = null,
    )

    /**
     * 一键闪记（场景表 `flash_memory`）。`triggerType=1` 是它的 `TriggerType.MAGIC_KEY`。
     *
     * 落点取 Gleaner 自己那份 `DataCollectService`：场景表写的那个 `scene_owner` 已经过期，
     * 那份服务一进去就自己 `stopSelf`（它认「Gleaner 已安装 ⇒ 我不干」）。
     */
    val FLASH_MEMORY = Target(
        owner = "com.oplus.gleanerservice",
        service = "com.oplus.gleanerservice.flashnotes.business.service.DataCollectService",
        action = "oplus.gleanerservice.intent.action.COLLECT_DATA",
        extras = mapOf("triggerType" to 1),
        foreground = true,
    )

    /** 切换免打扰（场景表 `no_disturb`）。KeyBaseService 族，bind 后发 what=2 —— 只有长按档碰 zen。 */
    val NO_DISTURB = Target(
        owner = "com.android.systemui",
        service = "com.oplus.systemui.statusbar.notification.keymagicservice.KeyDndService",
        message = 2,
    )

    /**
     * 开始 / 结束录音（场景表 `recording`）。
     *
     * 只有长按档能录音 —— 单击档那条 `KeyMagicMessengerService` 只回一句提示文案，全程不碰录音机。
     */
    val RECORDING = Target(
        owner = "com.coloros.soundrecorder",
        service = RECORD_ACTION,
        action = RECORD_ACTION,
        activity = true,
    )

    /** 录音的入口 action —— 场景表 `recording` 的 `long_press_action_info` 原文。 */
    private const val RECORD_ACTION = "oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON"

    /**
     * 小布识屏。**不是侧键场景** —— 入口是 `com.coloros.colordirectservice.InvokeEntranceService`，
     * 系统里两条路（侧边栏、导航栏长按）都落在它身上，用的也是同一条 action。
     *
     * `triggered_app` 报侧边栏而不是真实调用方（宿主 `com.oplus.gesture`），是为了让服务
     * 按「侧边栏入口」这条**已验证可用**的分支走。
     */
    val SCREEN_OCR = Target(
        owner = "com.coloros.colordirectservice",
        service = "com.coloros.colordirectservice.InvokeEntranceService",
        // 与 KSU 别名表同一份取值
        action = KsuCompat.SIDEBAR_SERVICE,
        extras = mapOf(
            "extra_entrance_function" to "full_screen_ocr",
            "triggered_app" to "com.coloros.smartsidebar",
        ),
        foreground = true,
    )

    /** 配置里的功能名 → 调用目标；认不出的返回 `null`（调用方记日志）。 */
    fun of(feature: String): Target? = when (feature) {
        "flash_memory" -> FLASH_MEMORY
        "no_disturb" -> NO_DISTURB
        "recording" -> RECORDING
        "screen_ocr" -> SCREEN_OCR
        else -> null
    }
}
