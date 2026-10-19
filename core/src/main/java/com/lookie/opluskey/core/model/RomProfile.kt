package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.log.XLog

/**
 * **一档 ROM 的适配事实** —— 内置命令的落点 + 反射面（ColorOS 私有类名 / 方法名 / 字段名）。
 *
 * 一版 ROM 一个 [Profile]，适配新版本 / 新机型时**只加一个条目**。表在 [PROFILES]，选档按
 * [DeviceSupport.currentRomSeries]（`17.0.0` 这种，与适配清单同一个口径、同一个输入）。
 *
 * ## 为什么按 ROM 系列分档，不按机型
 *
 * 这些名字由 ROM 决定（同一版 ROM 在不同机型上相同），机型只在适配闸里起作用。
 *
 * ## 加一档要做什么
 *
 * 1. 在 [PROFILES] 里加一个 [Profile]：`romSeries` + `label` + `verified` + [Profile.builtIns] +
 *    [Profile.reflection]，**逐项填满**（少填的项 = 那一处反射落空、记 `w` 后跳过）；
 * 2. 在 [DeviceSupport.ADAPTED] 里加这一版 ROM 那一行（那道闸管「能不能注入」，与本表各管一件事）；
 * 3. 真机验一遍：接管点、内置命令落点、反射面各验一轮。
 *
 * 第 1、2 步是「加表」，第 3 步本来就免不掉。
 *
 * ## 没验过的 ROM 照「基准实现」跑
 *
 * [PROFILES] 末尾那一档就是**基准实现**：本机 ROM 不在表里时，落点与反射面都照它试，各打一行 `w`
 * 说清「现在照哪一档跑」。这条路上**不拦、不灰** —— 起不来由执行侧收尾（toast 一句「这次没执行成功」），
 * 反射落空则逐处记 `w` 后跳过（与接管点候选同一条降级纪律）。
 * 验过一版新 ROM 之后再往 [PROFILES] 里加一档，把那一版的落点与反射面钉住。
 *
 * ## 接管点候选不在这张表里
 *
 * Hook 面的候选类名 / 方法名写在 `bridge/Framework.kt` 的候选表里（一、7 要求），那边**也是按
 * `romSeries` 分档的**（见它的 `HookSet`）。所以「适配一版新 ROM」要碰的表一共**两张**：
 * 本文件（执行落点 + 反射面）与 `bridge/Framework.kt`（接管点候选），两张都用同一个 ROM 键。
 */
object RomProfile {
    /**
     * 一个内置命令的调用落点。
     *
     * @param owner 场景表里的 `scene_owner`
     * @param service 场景表里的 `click_action_info`（完整类名）
     * @param action 该服务在清单里声明的 action；`null` = 只按组件起
     * @param extras 除 action 外还要带的 extra（`Int` / `String`）
     * @param foreground 目标在清单里声明了 special-use 前台类型 —— 决定先试哪种起法
     * @param activity 目标是**界面**（场景表 `*_action_type=activity`）—— 用 `startActivity` 起
     * @param message Messenger 绑定型服务要发的 `Message.what`（1=单击 / 2=长按）；
     *   `null` = 走 `startService`。这类服务没有 `onStartCommand`，起服务对它们是空操作
     * @param stopExtras 「按下开始、松手结束」那类能力**结束**时要改带的 extra
     *   （与 [extras] 合并后发出）；空 = 这一档没有结束形态，按一下就完事
     *
     *  参数多正是「一个落点要说清的那些事」：拆成几个小类只会让「加一档」从「填一个条目」
     *  变成「改好几处」，正是本文件要消灭的东西（`LongParameterList` 因此就地抑制）。
     */
    @Suppress("LongParameterList")
    class BuiltIn(
        val owner: String,
        val service: String,
        val action: String? = null,
        val extras: Map<String, Any> = emptyMap(),
        val foreground: Boolean = false,
        val activity: Boolean = false,
        val message: Int? = null,
        val stopExtras: Map<String, Any> = emptyMap(),
    )

    /**
     * 一档 ROM 的**反射面** —— 注入侧与宿主侧按名字反射时要用的那些名字。
     *
     * 全部是**字符串**（不引用宿主框架的类型，一、1）：类名 / 方法名 / 字段名 / 枚举值。
     * 默认值一律为空串 —— 漏填的那一项查找必然落空、记 `w` 后跳过，**不会**悄悄用上一档的名字
     * （那是「本机用着别版的类名」，比明确失败更难查）。
     */
    @Suppress("LongParameterList")
    class Reflection(
        /**
         * `OplusActivityManager` —— `startZoomWindow` 等接口挂它。
         *
         *  下面到 [resizeModeField] 为止是**宿主进程侧**用的那一组（ColorOS 自己的小窗 AIDL 门面）。
         */
        val zoomManager: String = "",
        /** `OplusActivityTaskManager` —— 「原地收成小窗」那一支挂它，**不在** `zoomManager` 上。 */
        val zoomTaskManager: String = "",
        /** `OplusZoomWindowManager` —— 判「这个应用支不支持小窗」与取 `zoomRect`。 */
        val zoomSupportManager: String = "",
        /** 取当前小窗状态（→ `OplusZoomWindowInfo`）。 */
        val zoomStateMethod: String = "",
        /** `OplusZoomWindowInfo` 上那个**屏幕矩形**字段（乘过 scale 的，不是 `appBounds`）。 */
        val zoomStateRectField: String = "",
        /** 判支持性的方法名。 */
        val zoomSupportMethod: String = "",
        /** 起小窗的方法名。 */
        val zoomStartMethod: String = "",
        /** 「原地收成小窗」的方法名。 */
        val zoomRecentsMethod: String = "",
        /** 上面那个方法的形参个数。 */
        val zoomRecentsArgs: Int = 0,
        /** 取各屏前台应用信息的方法名（→ `List<OplusAppInfo>`）。 */
        val zoomTopAppInfosMethod: String = "",
        /** `ActivityInfo` 上的尺寸可调性字段（编译期 stub 里没有，只能按名字读）。 */
        val resizeModeField: String = "",
        /**
         * 小窗服务门面。
         *
         *  下面到 [taskExtZoomStateField] 为止是**注入侧**（system_server）用的那一组。
         */
        val flexibleWindowService: String = "",
        /** 取 `FlexibleTaskController` 的方法名。 */
        val flexibleControllerGetter: String = "",
        /** `FlexibleTaskController#switchState(task, 1, reason)` —— 切小窗态那条。 */
        val flexibleSwitchStateMethod: String = "",
        /** 喂「能力配置」（操作条就是它开的）的方法名。 */
        val flexibleTaskParamsMethod: String = "",
        /** 「全屏 → 小窗」原生动画的方法名。 */
        val miniAnimMethod: String = "",
        /** 首选动画入口（无 `isInTransition` 过滤）。 */
        val springAnimMethod: String = "",
        /** 动画回调类名。 */
        val animCallbackClass: String = "",
        /** 动画参数对象的 Builder 类名。 */
        val animInfoBuilderClass: String = "",
        /** `FlexibleAnimationInfo.type` 要等于这个值才会走「结束上一段」分支。 */
        val animTypeTransitIme: Int = 0,
        /** 动画器用来选「过渡中」那一档过滤的 reason 串。 */
        val reasonMiniAnim: String = "",
        /** `TaskExt` 上「场景」字段（冒充柔性全屏态要写它）。 */
        val taskExtScenarioField: String = "",
        /** `TaskExt` 上「有没有操作条」字段。 */
        val taskExtCaptionField: String = "",
        /** `TaskExt` 上「柔性缩放态」字段。 */
        val taskExtZoomStateField: String = "",
    )

    /**
     * 一档 ROM 的全部适配事实。
     *
     * @param romSeries 适用的 ROM 系列（与 `DeviceSupport.romSeriesOf` 同一口径）
     * @param label 给人看的档位名（安装期判据行与「适配清单」页用它）
     * @param verified **真机验证记录**（机型 + 日期）—— 「这一档是谁在哪台机器上验的」。
     *   空串 = 还没验过（那就别指望它是对的）
     * @param builtIns 功能名（[NAME_FLASH_MEMORY] 那几条）→ 落点
     * @param reflection 反射面
     */
    class Profile(
        val romSeries: String,
        val label: String,
        val verified: String,
        val builtIns: Map<String, BuiltIn>,
        val reflection: Reflection,
    )

    // 配置里的功能名（`oplus:<名>` 的 `<名>`）—— 清单层拼 token 与本表的分派共用这一处，
    //  两处各写一遍时加一档只会漏掉其中一边。

    /** 一键闪记。 */
    const val NAME_FLASH_MEMORY = "flash_memory"

    /**
     * 灵感语音（AI 记忆的「长按录音」）。
     *
     *  **只在长按档出现**：它起的是一个**前台采集服务**（麦克风），短按 / 双击拨它等于「按一下就开录」，
     *  不是这个能力的语义。过滤在 `ActionCatalog.forGesture`，见那里的说明。
     */
    const val NAME_VOICE_MEMORY = "voice_memory"

    /** 免打扰。 */
    const val NAME_NO_DISTURB = "no_disturb"

    /** 录音。 */
    const val NAME_RECORDING = "recording"

    /** 小布识屏（**不在侧键场景表里**，见下面那一档的说明）。 */
    const val NAME_SCREEN_OCR = "screen_ocr"

    /** 侧键专属手电筒服务。 */
    const val NAME_KEY_FLASHLIGHT = "key_flashlight"

    /**
     * 一键闪记（场景表 `flash_memory`）。`triggerType=1` 是它的 `TriggerType.MAGIC_KEY`。
     *
     * 落点取 Gleaner 自己那份 `DataCollectService`：场景表写的那个 `scene_owner` 已经过期，
     * 那份服务一进去就自己 `stopSelf`（它认「Gleaner 已安装 ⇒ 我不干」）。
     */
    private val FLASH_MEMORY =
        BuiltIn(
            owner = "com.oplus.gleanerservice",
            service = "com.oplus.gleanerservice.flashnotes.business.service.DataCollectService",
            action = "oplus.gleanerservice.intent.action.COLLECT_DATA",
            extras = mapOf("triggerType" to 1),
            foreground = true,
        )

    /**
     * 灵感语音（长按记忆）。
     *
     * 真机实测（PLK110 / ColorOS 17）：`com.oplus.aimemory` 的
     * `voicecollect.service.DataCollectService` 读 `triggerType` 与 `longPressEventType`；
     * `triggerType=1`（MAGIC_KEY）+ `longPressEventType=1` 起服务后它进前台、FGS 类型里带上
     * 麦克风那一位（`SPECIAL_USE | MICROPHONE`），即开始采集。
     *
     * 落点**不是**闪记那两个包：CDS 那份一进去就因「Gleaner 已安装」自停，Gleaner 那份根本不读
     * `longPressEventType`（它的 dex 里连 `VoiceCollectTask` 都没有）—— 这个能力已经搬到 AI 记忆包里。
     *
     * 它是唯一**「按下开始、松手结束」**的能力：`longPressEventType=2` 是同一条服务的**结束**档
     * （真机实测：发完采集用的 FGS 类型退回去、卡片收掉），所以 [stopExtras] 只改这一位、
     * 其余 extra 与起采集时一致。
     */
    private val VOICE_MEMORY =
        BuiltIn(
            owner = "com.oplus.aimemory",
            service = "com.oplus.aimemory.voicecollect.service.DataCollectService",
            action = "oplus.aimemory.intent.action.COLLECT_DATA",
            extras = mapOf("triggerType" to 1, "longPressEventType" to 1),
            foreground = true,
            stopExtras = mapOf("longPressEventType" to 2),
        )

    /** 切换免打扰（场景表 `no_disturb`）。KeyBaseService 族，bind 后发 what=2 —— 只有长按档碰 zen。 */
    private val NO_DISTURB =
        BuiltIn(
            owner = "com.android.systemui",
            service = "com.oplus.systemui.statusbar.notification.keymagicservice.KeyDndService",
            message = 2,
        )

    /** 切换手电筒（侧键专属 KeyFlashlightService）。KeyBaseService 族，bind 后发 what=2 开关手电。 */
    private val KEY_FLASHLIGHT =
        BuiltIn(
            owner = "com.android.systemui",
            service = "com.oplus.systemui.statusbar.notification.keymagicservice.KeyFlashlightService",
            message = 2,
        )

    /** 录音的入口 action —— 场景表 `recording` 的 `long_press_action_info` 原文。 */
    private const val RECORD_ACTION = "oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON"

    /**
     * 开始 / 结束录音（场景表 `recording`）。
     *
     * 只有长按档能录音 —— 单击档那条 `KeyMagicMessengerService` 只回一句提示文案，全程不碰录音机。
     */
    private val RECORDING =
        BuiltIn(
            owner = "com.coloros.soundrecorder",
            service = RECORD_ACTION,
            action = RECORD_ACTION,
            activity = true,
        )

    /**
     * 小布识屏。**不是侧键场景** —— 入口是 `com.coloros.colordirectservice.InvokeEntranceService`，
     * 系统里两条路（侧边栏、导航栏长按）都落在它身上，用的也是同一条 action。
     *
     * `triggered_app` 报侧边栏而不是真实调用方（宿主 `com.oplus.gesture`），是为了让服务
     * 按「侧边栏入口」这条**已验证可用**的分支走。
     */
    private val SCREEN_OCR =
        BuiltIn(
            owner = "com.coloros.colordirectservice",
            service = "com.coloros.colordirectservice.InvokeEntranceService",
            // 与 KSU 别名表同一份取值（那条 shell 写法与这里的 action 是同一个串）——
            //  引用同一处常量，不再各写一遍
            action = KsuCompat.SIDEBAR_SERVICE,
            extras =
                mapOf(
                    KsuCompat.SIDEBAR_EXTRA_ENTRANCE to KsuCompat.SIDEBAR_EXTRA_ENTRANCE_VALUE,
                    KsuCompat.SIDEBAR_EXTRA_APP to KsuCompat.SIDEBAR_EXTRA_APP_VALUE,
                ),
            foreground = true,
        )

    /**
     * **各版本的表 —— 加一档就在这里加一个 [Profile]**。
     *
     * 落点的来路：系统自己的侧键场景表（真机 `content://uri.gesturemanagerprovider/action_button`
     * 的 `scene_name` / `scene_owner` / `click_action_info` / `long_press_action_info` 等列），
     * 但那张表**不带 extras，也区分不出「起普通服务」和「bind 后发 Message」**，`scene_owner` 还会过期 ——
     * 这几样都是对着目标 APK 反编译核的，见 `docs/device-findings.md` 十七。
     *
     * 注意：这一族**多数是「单击档只回一句提示、长按档才真正干活」**，所以下面几行的取值不都对着 `click_*`。
     * 这些服务 / 界面都带签名级权限（`oppo.permission.OPPO_COMPONENT_SAFE` 等），宿主恰好持有，
     * 所以从宿主进程直接起即可 —— 不需要 root，也不需要绕 system_server。
     */
    private val PROFILES: List<Profile> =
        listOf(
            Profile(
                romSeries = "17.0.0",
                label = "ColorOS 17.0.0",
                verified = "OnePlus 15（PLK110）/ 2026-10-13",
                builtIns =
                    mapOf(
                        NAME_FLASH_MEMORY to FLASH_MEMORY,
                        NAME_VOICE_MEMORY to VOICE_MEMORY,
                        NAME_NO_DISTURB to NO_DISTURB,
                        NAME_RECORDING to RECORDING,
                        NAME_SCREEN_OCR to SCREEN_OCR,
                        NAME_KEY_FLASHLIGHT to KEY_FLASHLIGHT,
                    ),
                reflection =
                    Reflection(
                        zoomManager = "android.app.OplusActivityManager",
                        zoomTaskManager = "android.app.OplusActivityTaskManager",
                        zoomSupportManager = "com.oplus.zoomwindow.OplusZoomWindowManager",
                        zoomStateMethod = "getCurrentZoomWindowState",
                        zoomStateRectField = "zoomRect",
                        zoomSupportMethod = "isSupportZoomMode",
                        zoomStartMethod = "startZoomWindow",
                        zoomRecentsMethod = "startActivityFromRecentsForFlexible",
                        zoomRecentsArgs = 2,
                        zoomTopAppInfosMethod = "getAllTopAppInfos",
                        resizeModeField = "resizeMode",
                        flexibleWindowService = "com.android.server.wm.FlexibleWindowManagerService",
                        flexibleControllerGetter = "getFlexibleTaskController",
                        flexibleSwitchStateMethod = "switchState",
                        flexibleTaskParamsMethod = "updateFlexibleTaskParams",
                        miniAnimMethod = "startFlexibleTaskToMiniAnimations",
                        springAnimMethod = "startFlexibleSpringAnimation",
                        animCallbackClass = "com.android.server.wm.floatwindow.FlexibleAnimationCallback",
                        animInfoBuilderClass = "com.android.server.wm.floatwindow.FlexibleAnimationInfo\$Builder",
                        animTypeTransitIme = 205,
                        reasonMiniAnim = "maySwitchStateToMini",
                        taskExtScenarioField = "mScenario",
                        taskExtCaptionField = "mHasCaption",
                        taskExtZoomStateField = "mFlexibleZoomState",
                    ),
            ),
        )

    /**
     * 本机 ROM 精确命中的那一档；`null` = 这一版 ROM 还没适配。
     *
     *  现算而不是缓存：ROM 系列可以被调试通道覆盖（见 `DeviceSupport.romSeriesOverride`），
     *  而这张表只有一两档，扫一遍的代价可以忽略。
     */
    val matched: Profile?
        get() = PROFILES.firstOrNull { it.romSeries == DeviceSupport.currentRomSeries }

    /**
     * 这张表登记过的 ROM 系列 —— **另外两张表（适配清单 / 接管点候选）的对账基准**。
     *
     *  从这里取而不是各自再列一遍：一档 ROM 的「档位名 + 验证记录」只有这一处是权威
     *  （接管点候选表按 `romSeries` 反查本表取名字，见 `bridge/Framework.HookSet`）。
     */
    val series: Set<String> get() = PROFILES.map { it.romSeries }.toSet()

    /** 按 ROM 系列取那一档（没登记返回 `null`）—— 供另一张表反查档位名 / 验证记录。 */
    fun bySeries(series: String): Profile? = PROFILES.firstOrNull { it.romSeries == series }

    /**
     * 反射面 / 接管点候选用的那一档 —— 本机没适配时**照最后一档试**（理由见类注释），并打一行 `w`。
     *
     * `PROFILES` 是常量表、不会是空的；真为空时退回全空的 [Reflection]（每一处查找都落空、各记一行 `w`），
     * 不抛异常 —— 注入侧抛异常就是开机循环（五之二）。
     */
    val effective: Profile
        get() {
            matched?.let { return it }
            val baseline = PROFILES.lastOrNull() ?: return EMPTY
            //  与 [landing] 同一条纪律：照基准档跑要说出来，否则「拦下来了但什么都不做」
            //  在日志里没有任何依据。只打一次 —— 这条 getter 会落在反射查找的热路径上。
            if (!effectiveWarned) {
                effectiveWarned = true
                XLog.w(
                    "本机 ROM ${DeviceSupport.currentRomSeries} 不在档位表里 ⇒ " +
                        "反射面与接管点候选照基准档（${baseline.label}）试",
                )
            }
            return baseline
        }

    /** 与 [landingWarned] 同一笔：只打一次，多个线程可能同时进来，漏了 `@Volatile` 会多打一行。 */
    @Volatile
    private var effectiveWarned = false

    /** 全空的兜底档（只在 [PROFILES] 被清空时用到）。 */
    private val EMPTY =
        Profile(romSeries = "", label = "（档位表为空）", verified = "", builtIns = emptyMap(), reflection = Reflection())

    /**
     * **内置命令落点**这一件事用的那一档 —— 本机没适配时照 [PROFILES] 末尾那一档（**基准实现**）试。
     *
     *  猜错的代价由执行侧兜：落点换成别的服务、或者那服务在别的 ROM 上起不来，表现就是起不来，
     *  执行侧会 toast 一句（`CommandFailure.UNKNOWN`）并把实际试过的组件写进日志。
     *  这一行只打一次（[landingWarned]）：`builtIn` 每按一次键都要问一次，每问一次打一行就是刷屏。
     */
    private val landing: Profile?
        get() {
            matched?.let { return it }
            val baseline = PROFILES.lastOrNull() ?: return null
            if (!landingWarned) {
                landingWarned = true
                XLog.w(
                    "本机 ROM ${DeviceSupport.currentRomSeries} 不在内置命令档位表里 ⇒ " +
                        "照基准实现（${baseline.label}）的落点试；起不来会 toast",
                )
            }
            return baseline
        }

    /** 「照基准实现跑」那行日志只打一次。 */
    @Volatile
    private var landingWarned = false

    /**
     * 配置里的功能名 → 调用目标。**功能名认不出**一律返回 `null`（调用方记日志）；
     * 本机 ROM 没适配时按 [landing] 的口径 —— 照基准实现试。
     */
    fun builtIn(feature: String): BuiltIn? = landing?.builtIns?.get(feature)

    /**
     * 本机档位的**一句话描述** —— 安装期判据行与「没有落点」那句日志都用它。
     *
     * 命中报档位名、内置命令条数与验证记录；没命中报「未适配 + 本机 ROM + 照哪一档跑」——
     * 后两种情形由这一句分开：**「这一版没验过」不是「没有落点」**，执行侧照样会去起基准实现。
     */
    fun describeCurrent(): String {
        val rom = DeviceSupport.currentRomSeries
        val exact = matched
        if (exact != null) return "${exact.label}（内置命令 ${exact.builtIns.size} 条；验证：${exact.verified}）"
        val unadapted = "未适配（本机 ROM ${rom.ifEmpty { "读不出来" }}）"
        return "$unadapted，落点照基准实现 ${landing?.label.orEmpty()} 试"
    }
}
