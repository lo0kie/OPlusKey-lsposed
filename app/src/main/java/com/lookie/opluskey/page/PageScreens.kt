package com.lookie.opluskey.page

import io.github.suqi8.coui.kmp.nav.core.NavKey

/**
 * 页面（系统设置 → 快捷键 → 按键绑定 → 自定义命令 → 「打开应用」/「关于模块」）。
 * 路标交给 coui-nav 的返回栈（`NavDisplay` + `navBackStackOf`，见 [SettingsPage.navStack]），
 * 谁在谁上面由栈说了算（「打开应用」两个来路的返回目标天然正确）。
 * key 不需要 `@Serializable`：栈是纯内存栈、不做状态保存 —— 页面进程死了本来也不该恢复。
 */
internal enum class Screen : NavKey {
    SHORTCUTS,
    BINDING,
    COMMANDS,

    /**
     * 「打开应用」——挑本机应用
     *
     *  它**有两个入口**：「按键绑定」页的入口格（挑中的应用绑给**当前手势**）、
     * 「自定义命令」页里「添加命令」浮层的「选择应用」（挑中的应用作为**一步**）。
     * 父级 = 压它下面的那一页，不需要额外记账。
     */
    APP_PICKER,

    /** 「关于模块」——从首页 ⋮ 菜单进来（版本、仓库地址那几行从首页搬到了这里）。 */
    ABOUT,
}
