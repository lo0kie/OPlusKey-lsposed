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
     * 读某份配置的命令库；该配置**还没有自己的命令库**时返回 `null`（老配置，见类注释）。
     *
     *  读不懂也返回 `null`：导出时退回「当前这份的命令库」比导出一份空库有用。
     */
    fun read(configSlot: String): List<Command>? {
        val raw = rawOf(slotOf(configSlot)) ?: return null
        val text = FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, raw) ?: return null
        return CommandLibrary.parseOrNull(text)
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
        val live = rawOf(ConfigStore.SLOT_COMMANDS)
        val mine = rawOf(target)
        if (mine == null) {
            //  老配置：目标槽是空的 ⇒ 把当前这份存过去，当前配置的命令库不动（理由见类注释）
            val ok = live == null || writeRaw(target, live)
            //  走到这里 `live` 一定非空（`live == null` 时 `ok` 恒为 true、进不来），
            //  所以日志里不再重复那一位 —— 编译器会把它报成「条件恒真」。
            if (!ok) XLog.w("配置切换：命令库没跟过去（槽 $target，mine=false）")
            return ok
        }
        if (live != null && !writeRaw(target, live)) {
            XLog.w("配置切换：命令库没跟过去（第一步写盘失败，槽 $target）")
            return false
        }
        if (!writeRaw(ConfigStore.SLOT_COMMANDS, mine)) {
            //  第二步失败：目标槽里此刻是当前这份，原来那份只在内存里 —— 必须还原，否则它没了
            val restored = live == null || writeRaw(target, mine)
            XLog.w(
                "配置切换：命令库没换进来（第二步写盘失败，槽 $target）—— " +
                    if (restored) "已还原目标槽" else "还原也失败，目标槽里现在是当前这份",
            )
            return false
        }
        return true
    }

    /** 删掉某份配置的命令库（连 `.bak` / `.new` 一起，走 [ModuleFiles.delete]）。 */
    fun delete(configSlot: String): Boolean = ConfigStore.files().delete(slotOf(configSlot))

    /** 读一个槽的磁盘原文；不在 / 读不出来都是 `null`。 */
    private fun rawOf(slot: String): String? = ConfigStore.files().read(slot).textOrNull()

    /** 写一个槽的磁盘原文（`openReadable` 与主配置一致：宿主主进程也要读得到）。 */
    private fun writeRaw(
        slot: String,
        text: String,
    ): Boolean = ConfigStore.files().write(slot, text, openReadable = true)
}
