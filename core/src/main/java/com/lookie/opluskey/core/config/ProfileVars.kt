package com.lookie.opluskey.core.config

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
 *  复制 / 对调 / 删除都只搬**磁盘原文**（外皮一起搬）：这几个动作与「值是什么」无关，
 *  过一遍解析只多两种失败可能。那套规矩（读不懂 ≠ 没有 / 第二步失败要还原 / 老配置只搬当前）
 *  收在 [ProfileSlotFile] 一处，与命令库共用同一份实现 —— 两个文件各写一遍时判据会漂，
 *  而漂出来的坏法只在「刚好有一份文件读不懂」时出现。
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
     * 变量表这一层：槽名前缀 = 当前生效那份的槽名。
     *
     *  `openReadable = false` 与 [VariableStore.save] 同一个口径：只有宿主这一侧（`:ui` 与宿主
     *  主进程，同 uid）读写变量，注入侧不碰它（它只吃配置广播）。
     */
    private val file = ProfileSlotFile(ConfigStore.SLOT_VARS, openReadable = false)

    /**
     * 某份配置对应的变量表槽名。
     *
     *  映射就是「把 `config` 换成 `vars`」：`config` → `vars`、`config-2` → `vars-2` ——
     *  槽号因此在三层之间天然对得上，不需要另一张对照表。
     */
    fun slotOf(configSlot: String): String = file.slotOf(configSlot)

    /**
     * 切换配置时的变量表对调 —— 对称于 [ConfigProfiles.switchTo] 对主配置做的那件事。
     *
     * @return 对调是否成功（目标配置没有自己的变量表时，「存过去」成功就算成功）
     * @see ProfileSlotFile.swap 顺序与还原那两条规矩的唯一实现
     */
    fun swap(configSlot: String): Boolean = file.swap(configSlot, "变量表")

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
    fun delete(configSlot: String): Boolean = file.delete(configSlot)
}
