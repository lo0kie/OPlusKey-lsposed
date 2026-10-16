package com.lookie.opluskey.core.seal

import android.content.Context
import android.content.pm.PackageManager
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ModuleInfo
import java.security.MessageDigest

/**
 * 本包**签名证书**的那点事 —— 封存层的全部依据都在这里。
 *
 *  取的是 `PackageInfo.signingInfo.apkContentsSigners` 的原始 DER，与构建期从 keystore 里读出来的
 *  `Certificate.encoded` **是同一串字节**（`app/build.gradle.kts` 的 `SealReleaseAssets` 那边按它
 *  派生密钥）。两边必须逐字节一致，否则官方包也解不开自己的密文。
 *
 *  只判一次、结果缓存：一个进程里签名不会变，而 `getPackageInfo` 要跨进程问 PMS。
 */
internal object SealCertificate {
    private const val DIGEST = "SHA-256"

    /** 取到的那串（进程内缓存）；`null` = 还没取过。 */
    @Volatile
    private var cached: ByteArray? = null

    /** 取不到的标记 —— 与「还没取过」必须分开，否则每次判都要重问一遍 PMS。 */
    @Volatile
    private var failed = false

    /** 本包签名证书的 SHA-256 原始摘要；取不到返回 `null`。 */
    fun sha256(context: Context): ByteArray? {
        cached?.let { return it }
        if (failed) return null
        val bytes = read(context)
        if (bytes == null) failed = true else cached = bytes
        return bytes
    }

    /**
     * 本包签名是不是 `pin` 那一份 —— `null` = **判不出来**（不是「不是」）。
     *
     *  比较的是小写十六进制串；`pin` 为空（debug 那一侧）不该走到这里，真走到了也只会得到 `false`，
     *  由调用方按「没开闸」处理。
     */
    fun matches(
        context: Context,
        pin: String,
    ): Boolean? = sha256(context)?.joinToString("") { "%02x".format(it) }?.equals(pin, ignoreCase = true)

    /** `AA:BB:…` 形式（与 `keytool -list -v` 同一写法），给「关于」页对账用。 */
    fun fingerprint(context: Context): String? = sha256(context)?.joinToString(":") { "%02X".format(it) }

    private fun read(context: Context): ByteArray? =
        try {
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            val info = context.packageManager.getPackageInfo(ModuleInfo.PACKAGE, flags)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let {
                MessageDigest.getInstance(DIGEST).digest(it)
            }
        } catch (e: Throwable) {
            XLog.w("读本包签名失败：${e.javaClass.simpleName}: ${e.message}")
            null
        }
}
