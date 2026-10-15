package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import java.util.Base64

/**
 * 磁盘文本的**外皮**：首行魔数 + 两层变换叠出来的一行正文。
 *
 * 三处共用这一份实现（主配置文件、命令库文件、`ConfigBundle` 的导出包），区别只有传进来的 [magic]：
 *
 * ```
 * OPlusKey-<用途>-<版本>
 * <一行：既不是标准 Base64 的字母表，也没有折行>
 * ```
 *
 * 正文分两层，就按这个先后：
 *
 *  1. **先 Base64**：正文的 UTF-8 字节与 [MASK] 逐字节异或（密钥循环使用），结果走一遍标准 Base64；
 *  2. **再换一层皮**：把上一步那串 Base64 按 [SCRAMBLE] 这张替身字母表逐字符换写，每个字符再按
 *     它在串里的下标错开一段（[ROT]）—— 标准字母表、`=` 收尾、76 列折行这三个「一看就是 Base64」
 *     的特征一起消失，正文收成一行，`base64 -d` 直接失败。
 *
 *  这两层都**不是加密**，只是「别一眼看懂、别顺手手改」—— 密钥与替身字母表都写死在代码里、不进
 * 任何配置文件，它的对手是「好奇的眼睛」而不是「想破解的人」（要真保密得换成真加密 + 密钥管理）。
 *
 * **不做旧格式兼容**（有意）：首行不是对应魔数、或正文认不出，都算读不懂，没有「明文回退」这条退路。
 *  注意为什么不退回「当它没写过」：那等于把用户配的东西整份换成默认值（主开关、三个手势绑定、
 *  振动开关一起没），正是 `ConfigStore.Load` 三态设计要挡住的那种丢数据；判成「读不懂」会让页面
 *  只读并拒绝写回，用户还有机会把文件拿出来看一眼。
 *  加第二层之前写下的文件（只有第一层那批）落在「正文认不出」上：本模块尚未发行过，那是开发期
 *  残留，删掉那份文件重配即可。
 *
 * 注意：**手改配置文件要连着这两层一起改**（解两层、改完再包两层）——
 * 第二层的替身字母表与位置偏移在 [SCRAMBLE] / [ROT] 里，第一层是异或 + 标准 Base64。
 * 另外正文那份 JSON 自带 `md5` 字段（见 `ConfigCodec`），改完内容还得把这个数一起重算。
 */
internal object FileObfuscation {

    /** 运行时配置（`ConfigStore.sharedFile` 与 `commandFile`）的外皮首行。 */
    const val MAGIC_RUNTIME = "OPlusKey-Runtime-1"

    /**
     * 混淆用的固定密钥（异或，循环使用）—— 只在代码里，不进配置文件。
     *
     * 存的是**逐字节 +1** 的形态，初始化时还原：原文写成字面量会留在 dex 的字符串池里，
     * 数值数组也一样（那 27 个字节本身就是可打印 ASCII），变换一下才不至于一眼捞出来。
     * 密钥始终在包里，拦不住真正的逆向。
     */
    private val MASK: ByteArray = byteArrayOf(
        80, 81, 109, 118, 116, 76, 102, 122, 46,
        68, 112, 111, 103, 106, 104, 46, 80, 99,
        103, 118, 116, 100, 98, 117, 106, 112, 111,
    ).also { a ->
        for (i in a.indices) a[i] = (a[i] - 1).toByte()
    }

    /** 第一层用的标准 Base64 字母表 —— 第二层要从这张表里取回原始下标。 */
    private const val STD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Base64 的填充字符（0～2 个，只出现在正文最末）。第二层原样留下，所以把它从替身字母表里排除。 */
    private const val PAD = '='

    /**
     * 替身字母表的字符池：可见 ASCII（`!` 到 `~`）剔掉这几个。
     * 引号 / 反斜杠 / 反引号在编辑器与 shell 里容易惹麻烦，`=` 是 [PAD] 的位子。
     */
    private const val POOL_EXCLUDED = "\"'\\`="

    /** 从池子里取 64 个的起点与步长 —— 池子 89 个字，3 与 89 互质 ⇒ 取不到重复。 */
    private const val POOL_OFFSET = 1
    private const val POOL_STEP = 3

    /** 替身字母表的字数（Base64 的 64 个下标一位对一位）。 */
    private const val ALPHABET_SIZE = 64

    /**
     * 第二层的替身字母表。
     *
     * 由可见 ASCII 按固定步长跳着取，**不落字符串字面量** ⇒ 不会躺在 dex 的字符串池里被 `strings`
     * 一声捞走（与 [MASK] 同一个考虑）。跳取是让它看着无序；规则本身一读就懂、也不依赖任何库的
     * 洗牌实现，所以跨版本稳定 —— 这张表一乱，盘上的文件就全读不出来了。
     */
    private val SCRAMBLE: CharArray = run {
        val pool = ('!'..'~').filter { it !in POOL_EXCLUDED }.toCharArray()
        CharArray(ALPHABET_SIZE) { i -> pool[(i * POOL_STEP + POOL_OFFSET) % pool.size] }
    }

    /** 第二层的位置偏移（按下标循环用）：同一个字符落在不同位置，换出来的替身也不同。 */
    private val ROT = intArrayOf(17, 0, 43, 8, 61, 25, 3, 54, 31, 12)

    /** 把明文包成 [magic] + 一行两层变换过的正文。 */
    fun wrap(magic: String, plain: String): String {
        val raw = plain.toByteArray(Charsets.UTF_8)
        val masked = ByteArray(raw.size) { i ->
            (raw[i].toInt() xor MASK[i % MASK.size].toInt()).toByte()
        }
        // 第一层：异或之后的字节走标准 Base64。不带折行 —— 折行本身就是个「这是一段 Base64」的标记
        val b64 = Base64.getEncoder().encodeToString(masked)
        // 第二层：换替身字母表 + 按位置错开，收成一行
        return "$magic\n${scramble(b64)}\n"
    }

    /**
     * [wrap] 的逆。
     *
     * @return 明文；**首行不是 [magic]（不是本模块这一档的文件）或正文解不开（Base64 坏了 / 被截断）
     *   时都返回 `null`**。传进来的两家（运行时配置、导出包）由调用方各自决定怎么报这句失败。
     */
    fun unwrap(magic: String, text: String): String? {
        // 剥 BOM：文件被第三方编辑器另存一次就会多出 BOM，不剥的话首行匹配不上
        val lines = text.removePrefix("\uFEFF").lines()
        val head = lines.firstOrNull()?.trim()
        if (head != magic) {
            XLog.w("解混淆：首行不是 $magic（不是本模块这一档的文件）")
            return null
        }
        // 正文本来是**一行**；join 是为了兼容被编辑器折过行的文件 —— 折行只影响排版，不算进下标
        val b64 = unscramble(lines.drop(1).joinToString(""))
        if (b64 == null) {
            XLog.w("解混淆：正文认不出（加第二层之前写下的旧文件 / 文件被改过或截断）")
            return null
        }
        return try {
            val masked = Base64.getDecoder().decode(b64)
            val raw = ByteArray(masked.size) { i ->
                (masked[i].toInt() xor MASK[i % MASK.size].toInt()).toByte()
            }
            String(raw, Charsets.UTF_8).ifBlank {
                // 解出来是空串：包得起来就说明本来有内容，空的那份是坏的（不是「空配置」）
                XLog.w("解混淆：正文解出来是空的（文件可能被截断了）")
                null
            }
        } catch (e: Throwable) {
            XLog.w("解混淆失败（首行是 $magic，但正文解不开）：${e.message}")
            null
        }
    }

    /**
     * 第二层正向：标准 Base64 文本 → 替身字母表 + 位置错开。
     *
     * 下标按**去掉折行之后**的位置算（[unscramble] 那边同一套算法），所以正文被编辑器折过行也不影响。
     * 第一层的编码器本来就不折行，这里的换行判断只是防御性的。
     */
    private fun scramble(b64: String): String {
        val out = StringBuilder(b64.length)
        var pos = 0
        for (c in b64) {
            if (c == '\n' || c == '\r') continue
            val at = pos++
            if (c == PAD) {
                out.append(PAD)
            } else {
                val idx = STD_ALPHABET.indexOf(c)
                if (idx >= 0) out.append(SCRAMBLE[(idx + ROT[at % ROT.size]) and 63])
            }
        }
        return out.toString()
    }

    /**
     * [scramble] 的逆。
     *
     * @return 标准 Base64 文本；替身字母表里没有的字符 ⇒ `null`（本模块这一档的正文不该出现，
     *   出现了就是「不是这层皮罩着的内容」）。
     */
    private fun unscramble(body: String): String? {
        val out = StringBuilder(body.length)
        var pos = 0
        for (c in body) {
            if (c == '\n' || c == '\r') continue
            val at = pos++
            if (c == PAD) {
                out.append(PAD)
                continue
            }
            val idx = SCRAMBLE.indexOf(c)
            if (idx < 0) return null
            // 减完再加一整圈：Kotlin 的 % 对负数给负数，直接用会拿到负下标
            out.append(STD_ALPHABET[(idx - ROT[at % ROT.size] + ALPHABET_SIZE) and 63])
        }
        return out.toString()
    }
}
