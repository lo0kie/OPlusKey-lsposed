package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.SNACKBAR_SHADOW_ROOM
import io.github.suqi8.coui.kmp.basic.Snackbar
import io.github.suqi8.coui.kmp.basic.SnackbarHost

/**
 * 首页那条常驻提示（「模块未生效」这类链路提示）。
 *
 *  从 `PageEntry` 里拆出来：它只挂在首页、只吃一个对齐位置，与页面的装配逻辑无关；
 *  留在一个函数里会让那个函数一直贴着 detekt 的行数上限。
 *
 *  @param modifier 由调用点给（`align(BottomCenter)` + 满宽）—— `align` 是 `BoxScope` 的成员，
 *    拆出来之后函数体里写不了。
 */
@Composable
internal fun SettingsPage.HomeSnackbar(modifier: Modifier) {
    SnackbarHost(
        state = link.snackbar,
        modifier = modifier,
        //  胶囊外面要自己撑出一圈空档，否则它那圈影子被库的视窗裁掉
        //  ——为什么、以及为什么必须撑在这里，见 [SNACKBAR_SHADOW_ROOM]。
        //  **底部不撑**：下面那截由库自己的 `contentPadding`（28dp）负责，那正是它的默认落点。
        content = { data ->
            Box(
                Modifier.padding(
                    start = SNACKBAR_SHADOW_ROOM,
                    top = SNACKBAR_SHADOW_ROOM,
                    end = SNACKBAR_SHADOW_ROOM,
                ),
            ) {
                Snackbar(data = data)
            }
        },
    )
}
