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
object FileObfuscation {
    /** 运行时配置（`ConfigStore.sharedFile` 与 `commandFile`）的外皮首行。 */
    const val MAGIC_RUNTIME = "OPlusKey-Runtime-1"

    /**
     * 混淆用的固定密钥（异或，循环使用）—— 只在代码里，不进配置文件。
     *
     * 存的是**逐字节 +1** 的形态，初始化时还原：原文写成字面量会留在 dex 的字符串池里，
     * 数值数组也一样（那 27 个字节本身就是可打印 ASCII），变换一下才不至于一眼捞出来。
     * 密钥始终在包里，拦不住真正的逆向。
     */
    private val MASK: ByteArray =
        byteArrayOf(
            80,
            81,
            109,
            118,
            116,
            76,
            102,
            122,
            46,
            68,
            112,
            111,
            103,
            106,
            104,
            46,
            80,
            99,
            103,
            118,
            116,
            100,
            98,
            117,
            106,
            112,
            111,
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
    private val SCRAMBLE: CharArray =
        run {
            val pool = ('!'..'~').filter { it !in POOL_EXCLUDED }.toCharArray()
            CharArray(ALPHABET_SIZE) { i -> pool[(i * POOL_STEP + POOL_OFFSET) % pool.size] }
        }

    /** 第二层的位置偏移（按下标循环用）：同一个字符落在不同位置，换出来的替身也不同。 */
    private val ROT = intArrayOf(17, 0, 43, 8, 61, 25, 3, 54, 31, 12)

    /**
     * 两张字符 → 下标的表（`-1` = 表外字符）。
     *
     *  这一层跑在**每次读配置**与**每次落盘**的路径上：逐字符 `indexOf` 一遍 64 项，
     *  解一份命令库就是几十万次比较。两张表都由上面那两份定义现算，不另写字面量。
     */
    private val SCRAMBLE_INDEX: IntArray =
        IntArray(128) { -1 }.also { table ->
            SCRAMBLE.forEachIndexed { i, c -> if (c.code < table.size) table[c.code] = i }
        }

    private val STD_INDEX: IntArray =
        IntArray(128) { -1 }.also { table ->
            STD_ALPHABET.forEachIndexed { i, c -> if (c.code < table.size) table[c.code] = i }
        }

    /** 在 [table] 里查 [c] 的下标；码点超出表宽（不该出现）时按「表外」算。 */
    private fun indexIn(
        table: IntArray,
        c: Char,
    ): Int = if (c.code < table.size) table[c.code] else -1

    /** 把明文包成 [magic] + 一行两层变换过的正文。 */
    fun wrap(
        magic: String,
        plain: String,
    ): String {
        val raw = plain.toByteArray(Charsets.UTF_8)
        val masked =
            ByteArray(raw.size) { i ->
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
    fun unwrap(
        magic: String,
        text: String,
    ): String? {
        // 剥 BOM：文件被第三方编辑器另存一次就会多出 BOM，不剥的话首行匹配不上
        val lines = text.removePrefix("\uFEFF").lines()
        val head = lines.firstOrNull()?.trim()
        if (head != magic) {
            XLog.w("解混淆：首行不是 $magic（不是本模块这一档的文件）")
            return null
        }
        // 正文本来是**一行**；join 是为了兼容被编辑器折过行的文件 —— 折行只影响排版，不算进下标。
        //  再把空白一起滤掉：第三方编辑器（记事本 / 某些 Markdown 工具）会在行尾补空格、
        //  或存成带 `\u00A0`（不间断空格）的「智能排版」，那些字符不在替身字母表里，
        //  会让整份配置被判「读不懂」—— 而写方这时也被闸住，用户表现为「怎么改都保存失败」。
        //  `Char.isWhitespace()` 不认 `\u00A0`（它不属于 IsWhiteSpace），所以单独列一条。
        val body =
            lines.drop(1).joinToString("").filterNot {
                it.isWhitespace() || it == '\u00A0' || it == '\uFEFF'
            }
        val b64 = unscramble(body)
        if (b64 == null) {
            XLog.w("解混淆：正文认不出（加第二层之前写下的旧文件 / 文件被改过或截断）")
            return null
        }
        return try {
            val masked = Base64.getDecoder().decode(b64)
            val raw =
                ByteArray(masked.size) { i ->
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
                val idx = indexIn(STD_INDEX, c)
                if (idx >= 0) {
                    out.append(SCRAMBLE[(idx + ROT[at % ROT.size]) and 63])
                } else {
                    // 字母表外的字符（正常路径不会出现）：**别静默丢** —— 丢了以后
                    //  `unscramble` 那边只会看到「解不开」，看不出是打包时就少了一个字节。
                    XLog.w("命令库外皮：正文里有非 Base64 字符（已原样保留）：${c.code}")
                    out.append(c)
                }
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
            val idx = indexIn(SCRAMBLE_INDEX, c)
            if (idx < 0) return null
            // 减完再加一整圈：Kotlin 的 % 对负数给负数，直接用会拿到负下标
            out.append(STD_ALPHABET[(idx - ROT[at % ROT.size] + ALPHABET_SIZE) and 63])
        }
        return out.toString()
    }
}

/**
 * 「每份配置的附属文件」—— 命令库与变量表**共用这一份实现**。
 *
 *  两者是同一个形状：槽名 = 一个前缀 + 同一个 config 槽号；复制 / 对调 / 删除都只搬**磁盘原文**
 *  （套好的外皮一起搬），因为这几个动作与「内容是什么」无关。差异只有一处，作为构造参数：
 *  写盘时要不要让另一个 uid 也读得到（命令库要 —— 宿主主进程按它执行；变量表不要 ——
 *  只有宿主这一侧读写它）。
 *
 * ## 为什么必须收成一份
 *
 *  两边各写一份的后果不是「代码重复」，而是**判据会漂**。这里有三条踩过坑才定下来的规矩：
 *   · **读不懂 ≠ 没有**：任一槽读不懂就整趟不动（按「没有」处理会把可能还能救的文件盖掉）；
 *   · **第二步失败要把目标槽还原**（否则目标那份永久没了）；
 *   · **老配置（目标槽空着）只把当前这份存过去、当前配置不动**；反方向（当前槽空着）则把目标
 *     这份换进来、并把「空」写回目标槽 —— **两侧对称**，因为「空」本身也是一种内容。
 *  一处改了另一处没改时，坏法只在「刚好有一份文件读不懂」时出现 —— 最难复现的那种。
 */
internal class ProfileSlotFile(
    /** 槽名前缀；同时也是**当前生效那份**的槽名（`commands` / `vars`）。 */
    private val prefix: String,
    /** 写盘时是否放宽到「另一个 uid 也读得到」。 */
    private val openReadable: Boolean,
) {
    /**
     * 某份配置对应的槽名 —— 映射就是「把 `config` 换成这一层的前缀」：
     * `config` → `commands`、`config-2` → `commands-2`。槽号因此在几层之间天然对得上。
     */
    fun slotOf(configSlot: String): String = prefix + configSlot.removePrefix(ConfigStore.SLOT_CONFIG)

    /** 读某个槽（三态：没有 / 读不懂 / 读出来了）。 */
    fun load(slot: String): ModuleFiles.Load = ConfigStore.files().read(slot)

    /** 写一个槽的磁盘原文。 */
    fun writeRaw(
        slot: String,
        text: String,
    ): Boolean = ConfigStore.files().write(slot, text, openReadable = openReadable)

    /** 删掉某份配置的这份附属文件（连 `.bak` / `.new` 一起，走 [ModuleFiles.delete]）。 */
    fun delete(configSlot: String): Boolean = ConfigStore.files().delete(slotOf(configSlot))

    /**
     * 切换配置时的对调 —— 对称于 [ConfigProfiles.switchTo] 对主配置做的那件事。
     *
     *  顺序与那边同一个道理：先把**当前这份**存进目标槽、再把目标那份换进来。
     *  中断在第一步 ⇒ 当前还是原来的内容；**中断在第二步 ⇒ 把目标槽还原**（不还原的话，
     *  目标那份原样只留在这一次调用的内存里）。
     *
     * @param what 日志里的主语（「命令库」/「变量表」）—— 这一层不认识具体是哪一种
     * @return 对调是否成功（目标槽空着时，「存过去」成功就算成功）
     */
    fun swap(
        configSlot: String,
        what: String,
    ): Boolean {
        val target = slotOf(configSlot)
        val live = load(prefix)
        val mine = load(target)
        //  **读不懂 ≠ 没有**：任一槽读不懂就整趟不动（见 `ModuleFiles` 类注释里那条三态纪律）
        if (live is ModuleFiles.Load.Unreadable || mine is ModuleFiles.Load.Unreadable) {
            XLog.w("配置切换：$what 有槽读不懂（槽 $target）⇒ 不动，先修那份文件")
            return false
        }
        val liveText = live.textOrNull()
        val mineText = mine.textOrNull()
        //  **「空」也是内容**：对调要搬两侧的状态 —— 一侧有内容、另一侧空着时，空的这一侧也得
        //  跟着搬过去。只搬有内容的那一侧的话，切过去之后两个槽里是同一份内容，切回来看到的
        //  还是它（另一份已经没了）。
        if (liveText == null) return swapLiveEmpty(target, mineText, what)
        if (mineText == null) {
            //  老配置：目标槽空着 ⇒ 把当前这份存过去，当前配置的不动
            val ok = writeRaw(target, liveText)
            if (!ok) XLog.w("配置切换：$what 没跟过去（槽 $target）")
            return ok
        }
        return swapBoth(target, liveText, mineText, what)
    }

    /**
     * 当前槽空着、目标槽有内容时的对调：把目标这份换进来，再把**空**这个状态写回目标槽。
     *
     * @param mineText 目标槽的内容（`null` = 两侧都空着，什么都不用做）
     */
    private fun swapLiveEmpty(
        target: String,
        mineText: String?,
        what: String,
    ): Boolean {
        if (mineText == null) return true
        //  当前槽空着 ⇒ 把目标这份换进来，再把**空**这个状态写回目标槽（删掉那一槽的文件：
        //  这一层不解析内容，磁盘上没有文件就是它唯一能表达的「空」）。
        if (!writeRaw(prefix, mineText)) {
            XLog.w("配置切换：$what 没换进来（当前槽 $prefix 写盘失败，槽 $target 原样）")
            return false
        }
        if (ConfigStore.files().delete(target)) return true
        //  清不掉目标槽时两份就是同一份内容 —— 撤回刚写进去的那一份，回到调用前的状态
        ConfigStore.files().delete(prefix)
        XLog.w("配置切换：$what 没对调成（目标槽 $target 清不掉），当前槽已退回空")
        return false
    }

    /** 两份都在时的对调：先存当前、再换进目标；**第二步失败要把目标槽还原**。 */
    private fun swapBoth(
        target: String,
        liveText: String,
        mineText: String,
        what: String,
    ): Boolean {
        if (!writeRaw(target, liveText)) {
            XLog.w("配置切换：$what 没跟过去（第一步写盘失败，槽 $target）")
            return false
        }
        if (writeRaw(prefix, mineText)) return true
        //  第二步失败：目标槽里此刻是当前这份，原来那份只在内存里 —— 必须还原，否则它没了
        val restored = writeRaw(target, mineText)
        XLog.w(
            "配置切换：$what 没换进来（第二步写盘失败，槽 $target）—— " +
                if (restored) "已还原目标槽" else "还原也失败，目标槽里现在是当前这份",
        )
        return false
    }
}
