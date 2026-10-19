package com.lookie.opluskey.core.seal

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 「封存的资产」—— 收款码与捐赠名单的读取口。
 *
 * ## 它在防什么
 *
 *  这两样东西都以明文进过包，`unzip` 一下就能换掉：把 `donate/wechat.webp` 换成自己的收款码重打包，
 *  再签个名发出去，用户看不出来。**只有 release 走加密**（见 [SEAL_ENABLED]）：明文在包里不存在，
 *  想换就得重新封一份；而密钥是从**签名证书**派生的（见 [SealCertificate]），重签之后指纹变了，
 *  密钥跟着变 —— 拿旧密文解不开，只见到兜底字形。
 *
 * ## 密钥为什么是「算出来的」而不是写死的
 *
 *  写死的常量谁反编译都看得到，解一次就能自己封一份新的。绑签名指纹把「能不能解开」与
 *  「是不是本包」绑在一起：证书是公开的，但**签出那份证书的私钥只在作者手里**。
 *  这一层抬高的是成本，不是保险箱 —— 真会逆向的人仍可以把比对与派生一起 patch 掉
 *  （见 `docs/` 里关于「防篡改能做到什么程度」的那段），所以「一眼分真假」那一路（关于页的
 *  签名指纹）才是绕不过去的那条。
 *
 * ## debug 与 release 是两套
 *
 *  debug（[SEAL_ENABLED] = false）读**明文** `assets/<name>`，因为开发时要能随手换图；
 *  release 读 `assets/sealed.bin` 这一份容器，明文根本不在包里。同一份源码，两条路。
 */
object Seal {
    /** 容器资产名（release 里唯一的资产本体，见 `core/build.gradle.kts` 的 `SealReleaseAssets`）。 */
    private const val CONTAINER = "sealed.bin"

    /** 容器头：魔数 + 版本，用来把「这不是我们那份文件」和「解不开」分开。 */
    private const val MAGIC = "OPKS"
    private const val FORMAT_VERSION = 1

    /** GCM 参数 —— 与构建期那份必须一致（`core/build.gradle.kts` 里同名常量）。 */
    private const val GCM_IV_LENGTH = 12

    /** 容器条目数与单条字节数的上限 —— 只为挡住「读出来是几亿」那种坏容器，正常资产远在它之下。 */
    private const val MAX_SEAL_ENTRIES = 4096
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val GCM_TAG_BITS = 128

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** 解出来的条目（进程内只解一次；这些是静态资源，不值得每次进页面重解一遍）。 */
    @Volatile
    private var entries: Map<String, ByteArray>? = null

    /** 签名判据只判一次：一个进程里签名不会变。 */
    @Volatile
    private var judgement: Verdict? = null

    /**
     * 读一份受保护的资产（条目名 = 明文时期在 `assets/` 下的路径，如 `donate/qq.webp`）。
     *
     *  读不到返回 `null` —— 与「文件没进包」同一个结果，调用方各自退成兜底形态
     *  （收款码退成品牌字形、名单退成空态），并留一句 `w` 当判据。
     */
    fun open(
        context: Context,
        name: String,
    ): ByteArray? = if (SEAL_ENABLED) sealedEntry(context, name) else SealAssets.bytes(context, name)

    /**
     * 本包签名与官方指纹的比对结论（见 [Verdict]）。
     *
     *  debug 一律 [Verdict.OFFICIAL]（那一侧没有指纹可比，也不该拿它拦任何东西）；
     *  判不出来（拿不到签名）时是 [Verdict.UNKNOWN] —— 「不知道」与「不是」必须分得开，
     *  否则排查时会把「读不到签名」当成「包被改过」。
     */
    fun verdict(context: Context): Verdict {
        if (!SEAL_ENABLED) return Verdict.OFFICIAL
        judgement?.let { return it }
        val result =
            when (SealCertificate.matches(context, SEAL_PIN)) {
                null -> Verdict.UNKNOWN
                true -> Verdict.OFFICIAL
                false -> Verdict.UNOFFICIAL
            }
        judgement = result
        return result
    }

    /**
     * 本包签名证书的 SHA-256，`AA:BB:…` 形式（与 `keytool -list -v` 打出来的那串同一种写法）——
     * 「关于」页拿它给用户对账。
     */
    fun ownFingerprint(context: Context): String? = SealCertificate.fingerprint(context)

    /** release 那条路：先判签名，再解容器里那一条。任何一步不成都是 `null`。 */
    private fun sealedEntry(
        context: Context,
        name: String,
    ): ByteArray? {
        if (verdict(context) != Verdict.OFFICIAL) {
            XLog.e("封存资产被拒（签名不是本包，见「关于 → 签名」）：$name")
            return null
        }
        val key = SealCertificate.sha256(context) ?: return deny(name)
        val all = entries ?: decryptContainer(context, key)?.also { entries = it } ?: return null
        return all[name] ?: deny(name)
    }

    /** 把整份容器解开（一次），`null` = 容器不在包里或解不开。 */
    private fun decryptContainer(
        context: Context,
        key: ByteArray,
    ): Map<String, ByteArray>? {
        val container = SealAssets.bytes(context, CONTAINER) ?: return null
        return try {
            DataInputStream(ByteArrayInputStream(container)).use { readEntries(it, key) }
        } catch (e: Throwable) {
            // 密钥不对时 GCM 自己就会抛（tag 校验不过）—— 这里正是「密文被换过 / 不是本包封的」
            //  那条路，所以记 `e` 而不是 `w`：它不是「文件没进包」那一档。
            XLog.e("封存容器解不开（$CONTAINER）：${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** 逐条读容器：头 → 条目数 → （名字、IV、密文）循环。 */
    private fun readEntries(
        input: DataInputStream,
        key: ByteArray,
    ): Map<String, ByteArray> {
        val magic = ByteArray(MAGIC.length).also { input.readFully(it) }
        check(String(magic, Charsets.US_ASCII) == MAGIC) { "容器魔数不符" }
        check(input.readUnsignedByte() == FORMAT_VERSION) { "容器版本不认识" }
        val count = input.readInt()
        //  坏 / 截断的容器会把这几个长度读成巨大的数，直接拿去分配就是一次巨量分配
        //  （`buildMap` 与 `ByteArray(...)` 都按它预分配）。封存资产是十来条量级，
        //  上限给得很宽，只为挡住「读出来是几亿」这种；越界一律抛出去，由 `decryptContainer`
        //  按「容器解不开」记一条。
        check(count in 0..MAX_SEAL_ENTRIES) { "容器条目数 $count 不合理（上限 $MAX_SEAL_ENTRIES）" }
        XLog.d("封存容器：$count 条")
        return buildMap(count) {
            repeat(count) {
                val name = input.readUTF()
                val ivLen = input.readInt()
                check(ivLen in 0..MAX_ENTRY_BYTES) { "IV 长度 $ivLen 不合理" }
                val iv = ByteArray(ivLen).also { input.readFully(it) }
                val sealedLen = input.readInt()
                check(sealedLen in 0..MAX_ENTRY_BYTES) { "密文长度 $sealedLen 不合理" }
                val sealed = ByteArray(sealedLen).also { input.readFully(it) }
                put(name, decipher(iv, sealed, key))
            }
        }
    }

    private fun decipher(
        iv: ByteArray,
        sealed: ByteArray,
        key: ByteArray,
    ): ByteArray {
        check(iv.size == GCM_IV_LENGTH) { "IV 长度不对：${iv.size}" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(sealed)
    }

    /** 名字在容器里找不到（与「容器压根没解开」分开记：前者是封存时漏了，后者是包不对）。 */
    private fun deny(name: String): Nothing? {
        XLog.w("封存容器里没有这一条（退成兜底形态）：$name")
        return null
    }
}

/**
 * 本包签名与官方指纹的比对结论。
 *
 *  三档而不是「是 / 不是」：拿不到签名时是 [UNKNOWN] —— 那既不是「官方」也不是「被改过」，
 *  混进任何一边都会让排查走向错误的结论（把读不到签名当成包被改过，或者反过来放行）。
 */
enum class Verdict {
    /** 与出厂指纹一致（debug 包一律算这一档：那一侧不设闸）。 */
    OFFICIAL,

    /** 签名与官方指纹不符 —— 包被改过并重签了。 */
    UNOFFICIAL,

    /** 判不出来（读不到本包签名）。 */
    UNKNOWN,
}
