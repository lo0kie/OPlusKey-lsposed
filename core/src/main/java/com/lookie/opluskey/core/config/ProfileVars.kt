package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Variable

/**
 * **每份配置自己的变量表** —— 与 [ConfigProfiles] / [ProfileCommands] 共用同一套槽号。
 *
 * ```
 * opluskey_config.conf  + opluskey_commands.conf  + opluskey_vars.conf    ← 当前生效的配置
 * opluskey_config-2.conf + opluskey_commands-2.conf + opluskey_vars-2.conf  ← 第 2 份
 * ```
 *
 * ## 为什么变量也要跟着走
 *
 *  变量是**配置的一部分**：一套绑定里「计数」加到 3、另一套里它是 0，本来就是两套东西。
 *  只有一份变量表的话，切换配置只换了绑定、没换它们读写的那些值 —— 切过去那套绑定接着用
 *  上一套的计数，看着像「配置没切干净」。所以「一份配置」= 主配置 + 命令库 + 变量表，三者一起动。
 *
 * ## 与命令库同一套做法
 *
 *  复制 / 对调 / 删除都只搬**磁盘原文**（外皮一起搬，见 [ProfileCommands] 的同名注释）：
 *  这几个动作与「值是什么」无关，过一遍解析只多两种失败可能。
 *
 * ## 老配置没有自己的变量表怎么办
 *
 *  加这一层之前建的配置只有 `config-N` / `commands-N`。目标槽空着时 [swap] 把**当前这份**变量表
 *  存过去、当前那份不动（等于「记住切走前的样子」）—— 于是老配置切走再切回来，变量原样还在，
 *  不会因为升级就把用户的计数抹成空表。**新建的空白配置**走 [save] 落一份空表，所以它是真的空
 *  （与命令库那条「空白配置 = 空命令库」同一个口径）。
 */
object ProfileVars {
    /**
     * 某份配置对应的变量表槽名。
     *
     *  映射就是「把 `config` 换成 `vars`」：`config` → `vars`、`config-2` → `vars-2` ——
     *  槽号因此在三层之间天然对得上，不需要另一张对照表。
     */
    fun slotOf(configSlot: String): String = ConfigStore.SLOT_VARS + configSlot.removePrefix(ConfigStore.SLOT_CONFIG)

    /**
     * 切换配置时的变量表对调 —— 对称于 [ConfigProfiles.switchTo] 对主配置做的那件事。
     *
     *  顺序与那边同一个道理：先把**当前这份**存进目标槽、再把目标的变量表换进来。中断在第一步 ⇒
     *  当前还是原来的变量；中断在第二步 ⇒ 两份都完整，只是当前这份换掉了。
     *
     * @return 对调是否成功（目标配置没有自己的变量表时，「存过去」成功就算成功）
     */
    fun swap(configSlot: String): Boolean {
        val target = slotOf(configSlot)
        val live = rawOf(ConfigStore.SLOT_VARS)
        val mine = rawOf(target)
        val ok =
            when (mine) {
                //  老配置：目标槽是空的 ⇒ 把当前这份存过去，当前配置的变量不动（理由见类注释）
                null -> {
                    live == null || writeRaw(target, live)
                }

                else -> {
                    (live == null || writeRaw(target, live)) && writeRaw(ConfigStore.SLOT_VARS, mine)
                }
            }
        if (!ok) XLog.w("配置切换：变量表没跟过去（槽 $target，live=${live != null}, mine=${mine != null}）")
        return ok
    }

    /**
     * 给 [configSlot] 落一份变量表（新建的空白配置落空表用）。
     *
     * 「空白配置 = 没有变量」：不落这一份的话，切换时目标槽空着会被当成老配置，
     * 于是新配置一上来就继承了上一套的计数 —— 那正是不对的。
     */
    fun save(
        configSlot: String,
        vars: List<Variable>,
    ): Boolean = VariableStore.save(vars, slotOf(configSlot))

    /** 删掉某份配置的变量表（连 `.bak` / `.new` 一起，走 [ModuleFiles.delete]）。 */
    fun delete(configSlot: String): Boolean = ConfigStore.files().delete(slotOf(configSlot))

    /** 读一个槽的磁盘原文；不在 / 读不出来都是 `null`。 */
    private fun rawOf(slot: String): String? = ConfigStore.files().read(slot).textOrNull()

    /**
     * 写一个槽的磁盘原文。
     *
     *  `openReadable = false` 与 [VariableStore.save] 同一个口径：只有宿主这一侧（`:ui` 与宿主
     *  主进程，同 uid）读写变量，注入侧不碰它（它只吃配置广播）。
     */
    private fun writeRaw(
        slot: String,
        text: String,
    ): Boolean = ConfigStore.files().write(slot, text, openReadable = false)
}
