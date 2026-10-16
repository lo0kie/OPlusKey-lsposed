package com.lookie.opluskey.core.seal

/**
 * debug 源集：封存层**关着** —— 资产走明文，签名不设闸。
 *
 *  为什么不封：开发时这张收款码 / 这份名单要能随手换（换图 → 重装 → 重启宿主，就这么一条路）；
 *  每换一次都要过一次 keystore 派生，试错成本白涨。
 *
 *  与 `BuildFlags.kt` 同一套做法：debug / release 各放一份同名文件，靠**源集物理隔离**顶替，
 *  release 产物里根本没有这一段 —— 不靠运行期开关（那种开关本身就是个可被 patch 的判据）。
 */
internal const val SEAL_ENABLED: Boolean = false

/** debug 没有可比的指纹（封存层关着，这条走不到）。 */
internal const val SEAL_PIN: String = ""
