package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.rom.RomProfile

// 「一条内置能力发出去的那个 Intent」与「把一条已经起着的能力停掉」—— 两件都只吃 [RomProfile.BuiltIn]，
// 与 [AppLauncher] 那套起法分开：那边管「怎么起」（前台 / 普通 / 显式组件三条路），这边管「发什么」。

/**
 * 造出「第一次」要发的那个 Intent（起采集与结束两条路共用）。
 *
 *  有 action 的按系统自己的写法发 —— `action + setPackage`，由清单 intent-filter 解析；
 *  场景表里 `scene_owner` + action 本来就是系统发给该服务的原样。只有组件名（无 action）
 *  的才用显式组件。失败之后的退路不在这里，在 [AppLauncher.startOplusFeature] 的显式组件兜底。
 *
 *  提成顶层函数只为让 [AppLauncher] 的函数数别顶破 detekt；本函数不吃对象状态。
 */
internal fun buildFeatureIntent(target: RomProfile.BuiltIn): Intent =
    if (target.action != null) {
        Intent(target.action).setPackage(target.owner)
    } else {
        Intent().setComponent(ComponentName(target.owner, target.service))
    }

/**
 * 把 [target] 那条**已经起着**的服务停掉 —— [RomProfile.BuiltIn.stop] 那一档（翻译的「关闭」）。
 *
 * ## 为什么是 `stopService`，不是广播、也不是 root
 *
 *  目标清单里那条退出广播（`GLOBAL_TRANSLATION_EXIT`）真机发出去 `result=0`、悬浮窗照旧；
 *  `Context.stopService` 才是真的收掉（服务记录消失，胶囊与全屏层两扇窗一起没）。
 *  停用要的那条权限（`com.oplus.permission.safe.AI_APP`）宿主持有，所以不必借 root ——
 *  借了反而会把「谁有权限」这件事藏起来（`shell` 去停会被直接拒）。
 *
 * ## 返回值不用来报错
 *
 *  `stopService` 在「本来就没在跑」时也回 `false`，那是**正常结果**（用户按了两次关闭、
 *  或者胶囊上那颗关闭已经点过）。所以这里只落一行日志，不报失败 —— 报错会让用户以为
 *  「关闭」这个动作坏了。
 */
internal fun stopFeatureService(
    ctx: Context,
    feature: String,
    target: RomProfile.BuiltIn,
) {
    val short = "${target.owner}/${target.service.substringAfterLast('.')}"
    val stopped =
        try {
            ctx.stopService(buildFeatureIntent(target))
        } catch (e: Throwable) {
            XLog.w("停 oplus:$feature 失败：${e.javaClass.simpleName}: ${e.message}")
            return
        }
    XLog.w("oplus:$feature → stopService($short)：${if (stopped) "已停" else "本来没在跑"}")
}
