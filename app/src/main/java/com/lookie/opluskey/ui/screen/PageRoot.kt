package com.lookie.opluskey.ui.screen

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.loadPageState
import com.lookie.opluskey.ui.pageBackgroundColor
import com.lookie.opluskey.ui.state.installI18nBridge
import com.lookie.opluskey.ui.systemNight
import kotlinx.coroutines.delay

// 建页 → 首帧，以及把页面 View 交出去。
//
// 「等数据到位」与「画整棵树」拆成两档（[Page] / [PageContent]）是有原因的：
//  建页要读的三份文件（配置 / 命令库 / 界面偏好）现在在组合期、IO 线程上读（见 [loadPageState]），
//  读回来之前页面状态里全是**默认值**（主开关关、三个手势全是无操作、命令库空）。
//  这一档必须**整棵页面树都不组合**，不能只把内容藏起来：
//   · 藏起来（alpha=0 / size=0）仍然会组合 —— 副作用照跑（链路自检接收器、调试通道、
//     换页收浮窗），而且首帧会先按默认值画出「模块未开」的界面、几十毫秒后再翻成真值，
//     用户看到的是开关自己弹一下；
//   · 整棵不组合时，等的那几毫秒露出来的是 [PageSplash] 那一屏底色 —— 与 `ComposeView`
//     自己刷的底色是同一个值（见 [createView]），所以中间那一帧看不出来。

@Composable
internal fun SettingsPage.Page() {
    // 键是 Unit：整页只跑一次。`loaded` 翻 true 会重跑本函数，但这里不会重启。
    LaunchedEffect(Unit) { loadPageState() }
    // 系统栏顶边在**首帧是 0**（注入进来的 ComposeView 还没收到 insets），而顶栏与内容的顶部
    //  让位都按它算 ⇒ 那一帧内容整体偏上约 40dp，下一帧才落回（真机录屏：让位 107dp → 165dp）。
    //  所以这一帧先只铺底色（与页面底色同值，看不出来），等 inset 到了再画整棵树。
    //  兜底：某些全屏 / 无状态栏场景它恒为 0，不能因此一直不画 —— 150ms 后无论如何都画。
    val barTop = with(LocalDensity.current) { WindowInsets.systemBars.getTop(this).toDp() }
    var insetsReady by remember { mutableStateOf(false) }
    var forced by remember { mutableStateOf(false) }
    SideEffect {
        if (barTop > 0.dp) insetsReady = true
    }
    LaunchedEffect(Unit) {
        delay(150)
        forced = true
    }
    if (configState.loaded && (insetsReady || forced)) {
        PageContent()
    } else {
        PageSplash()
    }
}

/** 读盘还没回来时铺的那一屏 —— 与 `ComposeView` 的底色同值，见 [Page] 的注释。 */
@Composable
private fun SettingsPage.PageSplash() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(pageBackgroundColor)),
    )
}

/**
 * 把模块页面建出来交给 hook 侧塞进宿主的容器里。
 *
 * 这里**一次读盘都不做** —— 建页要的三份文件（配置 / 命令库 / 界面偏好）归 [loadPageState]，
 * 由 [Page] 在组合期放到 IO 线程上读。这个方法只做三件不碰盘的事：装字典订阅、定深浅、
 * 把 `ComposeView` 与它需要的 owner 搭起来。
 */
internal fun SettingsPage.createView(): View? {
    // 先把字典订阅装上：配置里的语言档不在这一步读（现在是异步的），
    //  读回来之后由 `applyPageLoad` 换 `I18n.currentLanguage`（见 `ui/PageLoad.kt`）。
    //  订阅必须早于那次换档 —— 晚装的话，换档发生在界面层那份状态接上之前，
    //  首帧就会停在上一个语言的文案上。字典本体在 core（普通 Map），订阅这一半必须在界面层
    //  —— 见 `ui/state/I18nBridge.kt`。
    installI18nBridge()
    // 深浅是 binder 读一次系统全局配置（不是读盘），首帧底色与 [PageSplash] 都要它，留在同步路径上。
    chrome.dark = systemNight()

    val view =
        ComposeView(context).apply {
            // 首帧之前先把底色铺成页面底色，避免「先黑一下再画出来」
            setBackgroundColor(pageBackgroundColor or 0xFF000000.toInt())
            // 当前有焦点的 View** —— 手机是触摸模式，一个没打开 `isFocusableInTouchMode`
            // 的 View 拿不到 Android 焦点，事件就永远到不了 Compose（Compose 的按键处理挂在
            // `AndroidComposeView.dispatchKeyEvent` 上，见 `ActionTile` 的注释）。
            isFocusable = true
            isFocusableInTouchMode = true
            setContent { Page() }
        }
    viewTree.rootView = view

    // 自建 owner（宿主那棵树一个都没有）并先挂到自己身上；
    // 祖先链由 hook 侧在 `container.addView` **之后**回调 [PageViewTree.attachOwnersUpward] 挂。
    //
    //  宿主三件套齐全时 [PageViewTree.ownOwners] 返回 null —— **绝不能用占位实例去挂**：`attachOne`
    //  是「只挂不覆盖」，此刻 `view` 还没进树（`parent == null`），占位实例会被钉在
    //  `ComposeView` 自身上，之后 `attachOwnersUpward` 换不掉它，Compose 于是拿到那份
    //  停在 `INITIALIZED` 的 lifecycle ⇒ 页面一片空白。挂了 null 就让 Compose 沿
    //  parent 链自己去宿主那棵树上找（那正是它该走的路）。
    val own =
        viewTree.ownOwners(
            activity as? androidx.lifecycle.LifecycleOwner,
            activity as? androidx.lifecycle.ViewModelStoreOwner,
            activity as? androidx.savedstate.SavedStateRegistryOwner,
        )
    viewTree.pendingOwners = own
    if (own != null) viewTree.attachOne(view, own)

    XLog.w(
        "设置页用 COUI（Compose）渲染：night=${chrome.dark}" +
            " surface=#${Integer.toHexString(pageBackgroundColor)}",
    )
    return view
}
