package com.lookie.opluskey.core.model

/**
 * 「按键级覆盖公共项」这一组设置 —— **单键与组合键共用这一份契约**。
 *
 * ## 这一族回答什么
 *
 *  公共设置页（`Screen.GENERAL`）里那几项「按键怎么工作」的开关，每一档（一颗键 / 一组
 *  组合键）都可以自己决定要不要另设一份：走不走 ColorOS 专有接口、振不振、振多重、
 *  弹不弹提示、提示写什么。这就是「按键单独设置」——**不是**把手势判定参数收拢到一页
 *  （那些本来就是每颗键一份，见 `KeyConfig.doubleClickMs` 那一族）。
 *
 * ## 为什么是一个接口
 *
 *  `KeyConfig` 与 `ComboKey` 在这一件事上**没有任何区别**，执行侧也不该为组合写第二条分支
 *  （那种分支正是把组合键当成独立存在的来源）。取值出口只有 `Config` 上那五个
 *  `xxxFor(owner)`：传谁进来都一样。
 *
 * ## 总开关怎么管
 *
 *  [useOwnSettings] 关着（默认）⇒ 这六项一律看公共设置，本对象上那几个值不参与取值；
 *  开着 ⇒ 六项**全部**用本对象这份。不做「逐项开关」：一个总开关 + 一整份值，
 *  用户不用为每一项先想「这一项我要不要单独设」。
 */
interface OwnSettings {
    /** 这一档要不要用自己那套公共项 —— 默认关 = 跟随公共设置。 */
    val useOwnSettings: Boolean

    /**
     * 这一档的独立值**被公共设置播过种没有** —— 决定「第一次打开总开关时要不要抄一份公共值」。
     *
     *  播过一次之后就一直是真的：关掉总开关再打开时用这一档**自己**上次那套值，
     *  不再拿公共设置覆盖一遍（用户调过的东西不该被别人的值顶掉）。
     *
     *  默认实现给 `false`（= 谁也没播过，第一次打开会播）。这条默认值只对「不参与持久化的
     *  临时实现」有意义 —— 真正落盘的两档（`KeyConfig` / `ComboKey`）各自读自己那一格。
     */
    val ownSettingsSeeded: Boolean get() = false

    /** 这一档自己的「使用 ColorOS 接口」—— 只在 [useOwnSettings] 开着时算数。 */
    val colorOsApi: Boolean

    /** 这一档自己的「振动反馈」—— 只在 [useOwnSettings] 开着时算数。 */
    val vibrate: Boolean

    /** 这一档自己的「振动强度」（弱 / 中 / 强，见 `GlobalConfig.VIBRATE_STRENGTH_RANGE`）。 */
    val vibrateStrength: Int

    /** 这一档自己的「执行时提示」—— 只在 [useOwnSettings] 开着时算数。 */
    val toastOnRun: Boolean

    /** 这一档自己的提示文案模板（空串 = 用默认那句，口径同 `GlobalConfig.toastTemplate`）。 */
    val toastTemplate: String
}
