package com.lookie.opluskey

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * LSPosed 管理器那颗「打开模块」的 FAB 落到这里。
 *
 * 模块本身没有界面（一、6），这一页**不画任何东西** —— 透明主题 + `noHistory`，它的全部工作
 * 就是把用户送到宿主自己的侧键设置页，然后退场。清单里也只有这一个 Activity。
 *
 * ## 为什么自己不 `startActivity`，要绕宿主一趟
 *
 * 宿主的设置页在它自己的清单里带 `android:permission="oplus.permission.OPLUS_COMPONENT_SAFE"`
 * （`/system_ext/app/OplusGestureUI`，aapt 核过）：那是系统应用才拿得到的级别，普通 app 申请不到，
 * 直接起必被 `SecurityException` 拦。
 *
 * 所以这里只发一条 [ModuleWire.ACTION_OPEN_SETTINGS] 给宿主主进程，由它起**自己包**的页面 ——
 * 同包自起动不走那道跨应用检查。**不需要 root**：这条链路一层 root 都不碰。
 *
 * ## 为什么用有序广播
 *
 * 要一句准话：宿主的模块没注入（没启用 / 换包后没重启）时没有任何接收器，`resultCode` 会停在
 * [RESULT_CANCELED]，这一页据此提示「去 系统设置 › 快捷键」，而不是静悄悄什么都不发生。
 */
class EntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sendOrderedBroadcast(
            Intent(ModuleWire.ACTION_OPEN_SETTINGS).setPackage(HostApp.PACKAGE),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    // 宿主没接上 = 模块没注入（没启用 / 换包后没重启），把手动路径说出来再退场。
                    if (resultCode != RESULT_OK) {
                        XLog.w("模块入口：宿主没接上（resultCode=$resultCode），提示手动路径")
                        Toast.makeText(this@EntryActivity, R.string.entry_open_failed, Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
            },
            null,
            RESULT_CANCELED,
            null,
            null,
        )
    }
}
