package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog

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
 *   · **老配置（目标槽空着）只把当前这份存过去、当前配置不动**。
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
        if (mineText == null) {
            //  老配置：目标槽空着 ⇒ 把当前这份存过去，当前配置的不动
            //  （`liveText` 也空时根本没东西要搬，直接算成功）
            val ok = liveText == null || writeRaw(target, liveText)
            if (!ok) XLog.w("配置切换：$what 没跟过去（槽 $target）")
            return ok
        }
        return swapBoth(target, liveText, mineText, what)
    }

    /** 两份都在时的对调：先存当前、再换进目标；**第二步失败要把目标槽还原**。 */
    private fun swapBoth(
        target: String,
        liveText: String?,
        mineText: String,
        what: String,
    ): Boolean {
        if (liveText != null && !writeRaw(target, liveText)) {
            XLog.w("配置切换：$what 没跟过去（第一步写盘失败，槽 $target）")
            return false
        }
        if (writeRaw(prefix, mineText)) return true
        //  第二步失败：目标槽里此刻是当前这份，原来那份只在内存里 —— 必须还原，否则它没了
        val restored = liveText == null || writeRaw(target, mineText)
        XLog.w(
            "配置切换：$what 没换进来（第二步写盘失败，槽 $target）—— " +
                if (restored) "已还原目标槽" else "还原也失败，目标槽里现在是当前这份",
        )
        return false
    }
}
