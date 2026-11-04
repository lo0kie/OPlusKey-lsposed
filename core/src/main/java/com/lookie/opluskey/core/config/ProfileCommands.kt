package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command

/**
 * **每份配置自己的命令库** —— 与 [ConfigProfiles] 共用同一套槽号，格式与当前生效的配置完全一致。
 *
 * ```
 * opluskey_config.conf  + opluskey_commands.conf    ← 当前生效的配置（含它的命令库）
 * opluskey_config-2.conf + opluskey_commands-2.conf  ← 第 2 份
 * ```
 *
 * ## 为什么命令库也得跟着走
 *
 *  主配置里绑定的是 `cmd:<名字>` 这种**引用**，命令正文在命令库里。命令库只有一份的话，
 *  切换配置就只换了引用、没换被引用的东西：切过去看到的是一堆指向旧命令的绑定，
 *  切回来时自己那套命令已经被对调掉了。所以「一份配置」= 主配置 + 它的命令库，切换时两者一起动。
 *
 *  代价是切换要写四次盘（配置两份、命令库两份）。不这么做的话，任何一处不动都会留下
 *  「引用与内容对不上」的状态，而这种状态在界面上看不出来 —— 按钮都好好的，按下去不执行。
 *
 * ## 老配置没有自己的命令库怎么办
 *
 *  加这一层之前建的配置只有 `config-N`、没有 `commands-N`。读取时 [read] 返回 `null`；
 *  对调时 [swap] 把**当前这份**命令库存进那个空槽（等于「记住切走前的样子」），当前配置不动。
 *  于是第一次切走再切回来，命令库回到原样，什么都不丢。
 *
 * ## 为什么不解析再序列化
 *
 *  复制 / 对调 / 删除都只搬**磁盘原文**（已套好的外皮一起搬）：这两个动作与「内容是什么」无关，
 *  过一遍解析只多两种失败可能（读不懂就搬不动），而原文搬过去一定还是原来的内容。
 *  只有 [read] / [save]（导出、导入要用到内容）才真的过 [CommandLibrary]。
 */
object ProfileCommands {
    /**
     * 某份配置对应的命令库槽名。
     *
     *  映射就是「把 `config` 换成 `commands`」：`config` → `commands`、`config-2` → `commands-2`
     *  —— 槽号因此在两层之间天然对得上，不需要另一张对照表。
     */
    fun slotOf(configSlot: String): String = ConfigStore.SLOT_COMMANDS + configSlot.removePrefix(ConfigStore.SLOT_CONFIG)

    /**
     * 读某份配置的命令库，**三态**（导出那一路要用它分清「老配置没有库」与「库读不懂」）。
     *
     *  · [Load.Absent] —— 该配置还没有自己的命令库（老配置，见类注释）；
     *  · [Load.Unreadable] —— 文件在却读不懂（外皮 / 截断 / 解析失败）；
     *  · [Load.Ok] —— 读出来了（空表也是合法的一档）。
     *
     *  为什么必须分开：老配置可以退回「当前这份的命令库」凑合导出，而**读不懂的那份不能**——
     *  那样导出来的备份里是一份别人的命令库，用户拿它恢复时才发现对不上（真机上「导出的
     *  配置少了几条命令」就是这么来的）。
     */
    sealed interface Load {
        data object Absent : Load

        data object Unreadable : Load

        class Ok(
            val commands: List<Command>,
        ) : Load
    }

    /** 读某份配置的命令库（内容版，见 [load]）；只要内容、不关心是哪一种空时用它。 */
    fun read(configSlot: String): List<Command>? = (load(configSlot) as? Load.Ok)?.commands

    /** 读某份配置的命令库，分得清「没有」与「读不懂」。 */
    fun load(configSlot: String): Load =
        when (val r = ConfigStore.files().read(slotOf(configSlot))) {
            ModuleFiles.Load.Absent -> {
                Load.Absent
            }

            ModuleFiles.Load.Unreadable -> {
                Load.Unreadable
            }

            is ModuleFiles.Load.Ok -> {
                FileObfuscation
                    .unwrap(FileObfuscation.MAGIC_RUNTIME, r.text)
                    ?.let { CommandLibrary.parseOrNull(it) }
                    ?.let { Load.Ok(it) }
                    ?: Load.Unreadable
            }
        }

    /** 把一份命令库写进 [configSlot] 对应的槽（导入 / 新建时用）。 */
    fun save(
        configSlot: String,
        commands: List<Command>,
    ): Boolean =
        writeRaw(
            slotOf(configSlot),
            FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, CommandLibrary.serialize(commands)),
        )

    /**
     * 切换配置时的命令库对调 —— 对称于 [ConfigProfiles.switchTo] 对主配置做的那件事。
     *
     *  顺序与那边同一个道理：先把**当前这份**存进目标槽、再把目标配置的命令库换进来。
     *  第一步失败 ⇒ 什么都没动；第一步成了、第二步失败 ⇒ **把目标槽还原**（不还原的话目标那份
     *  命令库就被当前这份永久盖掉了，切回去看到的是一堆指向别人命令的绑定）。
     *
     * @return 对调是否成功（目标配置没有自己的命令库时，「存过去」成功就算成功）
     */
    fun swap(configSlot: String): Boolean {
        val target = slotOf(configSlot)
        val live = ConfigStore.files().read(ConfigStore.SLOT_COMMANDS)
        val mine = ConfigStore.files().read(target)
        //  **读不懂 ≠ 没有**：任一槽读不懂就整趟不动 —— 按「没有」处理会把那份**可能还能救**的
        //  文件用另一份盖掉（见 `ModuleFiles` 类注释里那条三态纪律）。
        if (live is ModuleFiles.Load.Unreadable || mine is ModuleFiles.Load.Unreadable) {
            XLog.w("配置切换：命令库有槽读不懂（槽 $target）⇒ 不动，先修那份文件")
            return false
        }
        val liveText = live.textOrNull()
        val mineText = mine.textOrNull()
        //  目标槽空着 = 老配置：把当前这份存过去、当前配置的命令库不动（理由见类注释）。
        //  `mineText == null` 时 `liveText` 若也空，则根本没东西要搬，直接算成功。
        if (mineText == null) {
            val ok = liveText == null || writeRaw(target, liveText)
            if (!ok) XLog.w("配置切换：命令库没跟过去（槽 $target，mine=false）")
            return ok
        }
        return swapBoth(target, liveText, mineText)
    }

    /**
     * 两份都在时的对调：先存当前、再换进目标；**第二步失败要把目标槽还原**。
     *
     *  抽出来只为让 [swap] 的返回点收在 detekt 的限制内；语义与它上面那段注释是一件事。
     */
    private fun swapBoth(
        target: String,
        liveText: String?,
        mineText: String,
    ): Boolean {
        if (liveText != null && !writeRaw(target, liveText)) {
            XLog.w("配置切换：命令库没跟过去（第一步写盘失败，槽 $target）")
            return false
        }
        if (writeRaw(ConfigStore.SLOT_COMMANDS, mineText)) return true
        //  第二步失败：目标槽里此刻是当前这份，原来那份只在内存里 —— 必须还原，否则它没了
        val restored = liveText == null || writeRaw(target, mineText)
        XLog.w(
            "配置切换：命令库没换进来（第二步写盘失败，槽 $target）—— " +
                if (restored) "已还原目标槽" else "还原也失败，目标槽里现在是当前这份",
        )
        return false
    }

    /** 删掉某份配置的命令库（连 `.bak` / `.new` 一起，走 [ModuleFiles.delete]）。 */
    fun delete(configSlot: String): Boolean = ConfigStore.files().delete(slotOf(configSlot))

    /** 写一个槽的磁盘原文（`openReadable` 与主配置一致：宿主主进程也要读得到）。 */
    private fun writeRaw(
        slot: String,
        text: String,
    ): Boolean = ConfigStore.files().write(slot, text, openReadable = true)
}
