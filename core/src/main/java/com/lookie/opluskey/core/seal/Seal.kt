package com.lookie.opluskey.core.seal

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
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

    /**
     * 容器格式版本 —— **必须与 `core/build.gradle.kts` 里那个同名常量逐字一致**。
     *
     *  2 = 条目名进 GCM 的 AAD（1 那版没有：同一份容器里两条密文可以互换位置）。
     */
    private const val FORMAT_VERSION = 2

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
        //  **只缓存有结论的那两档**：`UNKNOWN` 是「这一次判不出来」（签名读不到，可能是瞬时故障），
        //  缓存下来就永远不再重判了 —— 一次偶发失败会让整页后续一直显示「读不到签名」。
        if (result != Verdict.UNKNOWN) judgement = result
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
        val verdict = verdict(context)
        if (verdict != Verdict.OFFICIAL) {
            //  `UNKNOWN` 与 `UNOFFICIAL` 是两件事：前者是**读不到签名**（可能是官方包遇到一次
            //  读取失败），后者才是真被改过。混成一句「签名不是本包」会把排查带偏。
            val why =
                if (verdict == Verdict.UNKNOWN) {
                    "读不到本包签名，无法确认是不是官方包"
                } else {
                    "签名不是本包"
                }
            XLog.e("封存资产被拒（$why，见「关于 → 签名」）：$name")
            return null
        }
        val key = SealCertificate.sha256(context) ?: return deny(name)
        val all =
            entries ?: decryptContainer(context, key)?.also { entries = it } ?: run {
                //  `decryptContainer` 自己会为「解不开」记一条 e；走到这里只剩「容器没进包」
                //  —— 那是打包配置的问题，必须留一行，否则表现只是「那份资产是空的」
                XLog.e("封存容器不在包里（$CONTAINER）—— 资产 $name 读不到，检查 SealReleaseAssets 有没有生成")
                return null
            }
        return all[name] ?: deny(name)
    }

    /** 把整份容器解开（一次），`null` = 容器不在包里或解不开。 */
    private fun decryptContainer(
        context: Context,
        key: ByteArray,
    ): Map<String, ByteArray>? {
        val container =
            SealAssets.bytes(context, CONTAINER) ?: run {
                XLog.e("封存容器不在包里（asset/$CONTAINER）—— 整份受保护资产都读不到")
                return null
            }
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
                //  **先判等再分配**：IV 长度只可能是 [GCM_IV_LENGTH]，原先允许到 8MB 才交给
                //  `decipher` 去 check —— 一个坏容器就能让这条路径先按 8MB 分配一次。
                check(ivLen == GCM_IV_LENGTH) { "IV 长度不对：$ivLen" }
                val iv = ByteArray(ivLen).also { input.readFully(it) }
                val sealedLen = input.readInt()
                check(sealedLen in 0..MAX_ENTRY_BYTES) { "密文长度 $sealedLen 不合理" }
                val sealed = ByteArray(sealedLen).also { input.readFully(it) }
                put(name, decipher(name, iv, sealed, key))
            }
        }
    }

    /**
     * 解一条。
     *
     *  **条目名当 AAD**（与构建期那一段逐字对应）：GCM 只认密钥与 IV，所以同一份容器里
     *  两条密文的 IV / tag 都是合法的、可以被互换位置 —— 换上去解出来是另一条资产的合法字节。
     *  把名字绑进 AAD 之后，换位会让这一条的 tag 校验不过。
     */
    private fun decipher(
        name: String,
        iv: ByteArray,
        sealed: ByteArray,
        key: ByteArray,
    ): ByteArray {
        check(iv.size == GCM_IV_LENGTH) { "IV 长度不对：${iv.size}" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
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

/**
 * 「按包名问自己要一份资产」—— 两条路，按可靠性排。
 *
 *  1. `getResourcesForApplication`：模块自己那几个进程走这条；
 *  2. **把模块 APK 当 zip 读**：宿主**主**进程只能走这条。
 *
 *  第二条是必需的：LSPosed 只把模块 **dex** 注进宿主进程，模块 APK 本身不在那个类加载器的资源
 *  路径上（同一条账见 `core/i18n/DictionaryLoader`）。
 *
 *  两条都不能用 `context.assets` —— 那是**宿主** APK 的资源，读出来的是别人的东西。
 */
internal object SealAssets {
    /** 读一份资产；读不到返回 `null`，并留一句 `w`（与「文件本来就不该在」分得开）。 */
    fun bytes(
        context: Context,
        name: String,
    ): ByteArray? =
        fromResources(context, name)
            ?: fromApkZip(context, name)
            ?: missing(name)

    private fun fromResources(
        context: Context,
        name: String,
    ): ByteArray? =
        try {
            context.packageManager
                .getResourcesForApplication(ModuleInfo.PACKAGE)
                .assets
                .open(name)
                .use { it.readBytes() }
        } catch (e: Throwable) {
            XLog.d("按资源读 $name 未成：${e.javaClass.simpleName}")
            null
        }

    private fun fromApkZip(
        context: Context,
        name: String,
    ): ByteArray? =
        try {
            val apk =
                context.packageManager
                    .getApplicationInfo(ModuleInfo.PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
                    .sourceDir ?: return null
            ZipFile(apk).use { zip ->
                // APK zip 里的路径带 `assets/` 前缀，而调用方传的是相对 assets 根的路径
                // （`sealed.bin` / `donate/alipay.webp`）—— 不补这一层，这条兜底路径恒返回 null。
                val entryName = if (name.startsWith("assets/")) name else "assets/$name"
                val entry = zip.getEntry(entryName) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Throwable) {
            XLog.d("按 zip 读 $name 未成：${e.javaClass.simpleName}")
            null
        }

    private fun missing(name: String): Nothing? {
        XLog.w("模块资产读不到（退成兜底形态）：$name")
        return null
    }
}

/**
 * 本包**签名证书**的那点事 —— 封存层的全部依据都在这里。
 *
 *  取的是 `PackageInfo.signingInfo.apkContentsSigners` 的原始 DER，与构建期从 keystore 里读出来的
 *  `Certificate.encoded` **是同一串字节**（`core/build.gradle.kts` 的 `SealReleaseAssets` 那边按它
 *  派生密钥）。两边必须逐字节一致，否则官方包也解不开自己的密文。
 *
 *  只判一次、结果缓存：一个进程里签名不会变，而 `getPackageInfo` 要跨进程问 PMS。
 */
internal object SealCertificate {
    private const val DIGEST = "SHA-256"

    /** 取到的那串（进程内缓存）；`null` = 还没取过。 */
    @Volatile
    private var cached: ByteArray? = null

    /**
     * 上一次取失败的**单调时刻**（`SystemClock.elapsedRealtime()`；`0` = 没失败过）——
     * 与「还没取过」必须分开，否则每次判都要重问一遍 PMS。
     *
     *  **只能用单调时钟**：过期判据是「`now - failedAt < RETRY_MS`」，用 wall clock 时一次校时回拨
     *  （NTP / 用户改时间）会让 `failedAt` 跑到未来，这个窗口就一直命中 —— 签名闸长期不重试，
     *  注入侧整条不接管，而且看起来像「那台机器永远拿不到签名」。
     *
     *  **但不许永久粘住**：`PackageManager` 一时拿不到（开机早期、包刚更新完）与「这台机器永远
     *  拿不到」是两件事。永久记住前者会让签名闸一直判不出口令、注入侧整条不接管，直到进程重启。
     *  口径与 `Reflect` 那边负命中的过期窗口一致。
     */
    @Volatile
    private var failedAt = 0L

    /** 失败之后多久再试一次。 */
    private const val RETRY_MS = 30_000L

    /** 本包签名证书的 SHA-256 原始摘要；取不到返回 `null`。 */
    fun sha256(context: Context): ByteArray? {
        cached?.let { return it }
        val now = SystemClock.elapsedRealtime()
        if (failedAt != 0L && now - failedAt < RETRY_MS) return null
        val bytes = read(context)
        if (bytes == null) {
            failedAt = now
        } else {
            cached = bytes
            failedAt = 0L
        }
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
    ): Boolean? = sha256(context)?.hex()?.equals(pin, ignoreCase = true)

    /** `AA:BB:…` 形式（与 `keytool -list -v` 同一写法），给「关于」页对账用。 */
    fun fingerprint(context: Context): String? = sha256(context)?.joinToString(":") { it.toHexPair().uppercase() }

    /**
     * 自己拼十六进制，不用 `String.format("%02x", …)` —— 后者吃系统区域设置，
     *  在阿拉伯语这类环境里会写出另一套数字（与 `ConfigCodec.md5Hex` 同一条口径）。
     */
    private fun ByteArray.hex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) out.append(b.toHexPair())
        return out.toString()
    }

    private fun Byte.toHexPair(): String {
        val v = toInt() and 0xFF
        return "${HEX[v ushr 4]}${HEX[v and 0x0F]}"
    }

    private const val HEX = "0123456789abcdef"

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
