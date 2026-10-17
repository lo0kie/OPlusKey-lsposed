package com.lookie.opluskey.ui.state

import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.model.LaunchableApp

/**
 * 「选择应用」页那一域的状态 —— **一域一个对象**，写入者收窄到少数几处（`docs/decoupling.md` 动作 5）。
 *
 * 为什么这一域要单独拎出来：这一页的状态原来直接摊在 `SettingsPage` 上（7 个字段），而**读写它的人
 * 分布在四处** —— 顶栏那棵树（`ui/screen/apppicker/AppPickerHeaderMenu.kt` 里那三项菜单）、页内容那棵树
 * （`ui/screen/AppPicker.kt`）、两个入口（`ui/StepActions.kt` 与网格那格）。摊在页面上时
 * 「谁改过 appFilter」得把四个文件翻一遍；收进一个对象之后，改它的入口一眼可列。
 *
 * 与页面级状态**同生共死的那几位仍留在页面里**（比如确认窗的挂起项），这一域只装
 * 「这趟挑应用"是谁在挑、怎么显示"」。
 *
 * 注意：这些字段提到页面对象上（而不是留在页面里 `remember`）**是有理由的**，见各字段自己的说明
 * —— 顶栏与页内容是同一层 `Box` 里**两棵并列的树**，页面那棵出栈即注销，顶栏那棵不走。
 */
internal class AppPickerState {
    /**
     * 搜索词。
     *
     *  搜索框挂在**顶栏**里，页面里的过滤逻辑与它读同一份 —— 两棵树共用，只能提上来。
     *  注意：随之多一笔账：那份 `remember` 不随页面出栈注销，提到上面就得**手动清** ——
     *  两个入口（「按键绑定」页的「打开应用」格、添加命令浮层的「选择应用」）都要清一次。
     */
    val filter = mutableStateOf("")

    /**
     * 顶栏菜单里那一项「显示全部应用」。
     *
     *  与 [filter] 相反：它**不随进页清空** —— 那是「我想看到全部应用」这个**意图**，不是一次输入。
     *  而且它**会落盘**：本地持久化在 [UiState]（**不进用户配置**），进页时读一次，所以页面重建 /
     *  宿主 Activity 重建之后仍是上次那一档。
     *
     *  叫 `showSystemApps` 是**历史包袱**：菜单叫「显示系统应用」，而这一档并不按 `FLAG_SYSTEM` 筛，
     *  而是把没有启动图标的第三方应用（典型是各个 LSPosed 模块）一并列出来；落盘的键
     *  `show_system_apps` 也保持原名 —— 改名会让用户的这一档回默认。
     */
    val showSystemApps = mutableStateOf(false)

    /**
     * 顶栏菜单里那一项「显示失效应用」。
     *
     *  与 [showSystemApps] 同一套账：**不随进页清空**、**落盘**、进页读一次。
     *  判据是 `LaunchableApp.launchable`（执行端同一把尺子），关着时藏掉的正是「按下去必弹 toast」的那些。
     *  **默认关**；落盘的键就叫 `show_unopenable`，改语义必须换键名（旧键作废，不做迁移）。
     */
    val showUnopenable = mutableStateOf(false)

    /**
     * 菜单里那一项「以小窗口打开」。
     *
     *  它不筛列表，只决定**挑中的应用写成哪个动作**：开着写 `window:<包名>`，关着写 `app:<包名>`。
     *
     *  **它是临时的，不是偏好**：**不落盘**、**没有跨次记忆** —— 每次进这一页都由
     *  `SettingsPage.resetSmallWindowForPicker()` 按**正在编辑的那个动作**重算（本来就是 `window:`
     *  就开着、`app:` 或新绑就关着）。与 [showSystemApps] / [showUnopenable] 那两项**相反**。
     *  理由：这一项描述的是「这一次要往哪个动作上写」，留着上一次的 `true` 会让下一次挑应用
     *  **静默**变成小窗，用户看不出自己什么时候开的。
     */
    val smallWindow = mutableStateOf(false)

    /**
     * 进这一页时对应的**手势**（`null` = 没在挑应用，或者不是给手势挑的）。
     *
     *  用途：挑中应用时知道该写哪个手势。不能靠 pager 的当前页 —— 那一页不属于 pager。
     *  从「添加命令」浮层的「选择应用」进来时它是 `null`（挑中的应用是**一步**、不绑手势）——
     *  给多条命令加一步却没挑，本来就该什么都不发生。与 [replaceStep] 同生共死。
     */
    var gesture: Gesture? = null
        private set

    /** 这次是给「多条命令」加一步（挑中的包名走 `addStep`，不写手势绑定）。 */
    var forStep = false
        private set

    /**
     * 这次进这一页是**替换多条命令的第几步**。`-1` = 追加为一步。与 [forStep] 同生共死。
     */
    var replaceStep = -1
        private set

    // —— 「这趟为谁挑」那一组标记的三个改法 ——
    //
    //  三个字段同生共死（`forStep` 一起写着 `replaceStep` 才有意义，`gesture` 只归网格那条入口），
    //  所以入口也只留这三个：原来「进页那个手势」「加一步 / 换一步」「挑完 / 没挑就退」这四种场合
    //  各自就地赋三个字段，散在**六个地方** —— 少清一个就留下半个状态（`forStep=false` 却带着
    //  上一次的 `gesture`），而从代码上看不出来（见 `docs/decoupling.md` 判据 3）。

    /** 网格那条入口：给手势 [g] 挑应用（挑中的写成 `app:` / `window:` 绑给这个手势）。 */
    fun openForGesture(g: Gesture) {
        gesture = g
        forStep = false
        replaceStep = -1
    }

    /** 「添加命令」那条入口：给多条命令**加一步**（[replaceIndex] `< 0`）或换掉第 [replaceIndex] 步。 */
    fun openForStep(replaceIndex: Int) {
        gesture = null
        forStep = true
        replaceStep = replaceIndex
    }

    /** 这趟「为谁挑」整个作废 —— 挑完了、写失败留在原地、或干脆没挑就退出来。 */
    fun clearTarget() {
        gesture = null
        forStep = false
        replaceStep = -1
    }

    /** 这一趟「加一步 / 换一步」挑完了 —— 返回要替换的那一步（`-1` = 追加），标记一并收掉。 */
    fun takeStepTarget(): Int {
        forStep = false
        val index = replaceStep
        replaceStep = -1
        return index
    }

    // ── 屏级缓存：清单 + 图标 ──
    //
    //  这几样原来挂在 `ui/screen/apppicker/AppList.kt` 的**包级 internal 可变全局**上 —— 没有所有者，
    //  「谁能改、什么时候失效」全靠注释约定，而「谁在什么时候清」正是这类缓存唯一的难点。
    //  收进这一域之后所有者是 `SettingsPage.picker`，与这一页的状态同生共死。
    //
    //  ⚠️ 页面**重建**（宿主 Activity 重建）会连它一起没。原来挂全局时能跨重建活下来，但那是**顺带**
    //  的性质、不是设计目标：缓存只省「首帧先铺旧清单」这一下，丢了不影响正确性（进页照样查一遍）。

    /**
     * 上一次查到的清单 —— 让二次进入这一页时立刻有内容可画。
     *
     *  缓存**不阻止重查**：每次进页仍会在后台重跑一遍并覆盖它，新装 / 卸载的应用照样能出现。
     *
     *  **必须是 compose 状态**：顶栏那行「已选择: <应用名>」直接读它查 label，
     *  裸 `var` 不会订阅读取 ⇒ 清单查完、缓存换了一茬，顶栏也不会重组，于是长期显示包名。
     */
    val cachedApps = mutableStateOf<List<LaunchableApp>?>(null)

    /**
     * [cachedApps] 是在哪个模式下查的（「显示全部应用」开没开）。
     *
     *  必须与清单一起写：否则会把「开着系统应用时查的清单」当成「关着时的首帧」用，
     *  退出再进会先闪一屏系统应用。
     */
    var cachedAppsWithSystem = false

    /** 图标缓存：包名 → 图。容量见 [APP_ICON_CACHE_SLOTS]。`accessOrder = true`：取用即算「刚用过」。 */
    private val icons =
        object : LinkedHashMap<String, Drawable>(APP_ICON_CACHE_SLOTS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean = size > APP_ICON_CACHE_SLOTS
        }

    /**
     * 图标缓存的版本号 —— **只在「清缓存」时 +1**（[clearIcons]）。
     *
     *  它是 Compose 状态：`AppIcon` 在组合里读它，于是清缓存能让**已经组合出来的行**重新取一次。
     *  不读它不行 —— 那些行的键是包名，清单换了照样不重组、`LaunchedEffect` 也不重跑，
     *  清缓存对它们等于没清（换图标包后要滚出去再滚回来才换）。
     *
     *  「某一行取到了图」**不走这里**（见 [putIcon]）：那一档由那一行自己的状态驱动 ——
     *  走全局版本号的话，一屏十来行依次到位就是十来次全表重组（每行各自那一份图与位图都得重跑一遍）。
     */
    val iconsGeneration = mutableIntStateOf(0)

    /** 取一份图（滚回来的那一行靠它免掉第二次跨进程取图标）。 */
    fun cachedIcon(packageName: String): Drawable? = synchronized(icons) { icons[packageName] }

    /**
     * 往缓存里补一份图（只为「滚回去不再跨进程问第二轮」）。
     *
     *  注意**不动** [iconsGeneration]：这一份是给**滚回来那一行**用的；当下这一行由它自己那份状态
     *  画出来（见 `AppIcon`），不需要惊动全表。
     */
    fun putIcon(
        packageName: String,
        icon: Drawable,
    ) {
        synchronized(icons) { icons[packageName] = icon }
    }

    /** 清单重查完就清空（理由见 [APP_ICON_CACHE_SLOTS]）：让图标跟着那份新清单重新取。 */
    fun clearIcons() {
        synchronized(icons) { icons.clear() }
        iconsGeneration.intValue++
    }
}

/**
 * 应用图标的小 LRU（只给「打开应用」页用）—— 滚回去的项靠它免掉第二次跨进程取图标。
 *
 *  容量按「来回滚两三屏」给：一屏十来行，超出按最久没滚到的淘汰，淘汰只是下次滚回来多问一次
 *  `PackageManager`，不是错。
 *
 *  **每次拿到新清单就清一遍**（见 [AppPickerState.clearIcons] 的调用点）：图标本身会因为换图标包 /
 *  换主题而变，缓存里留着的就是换之前那一份 —— 不清，最近滚过的那几十个应用会一直用它。
 *  清单没变时缓存照旧生效，滚动来回不走第二次 binder。
 */
internal const val APP_ICON_CACHE_SLOTS = 48
