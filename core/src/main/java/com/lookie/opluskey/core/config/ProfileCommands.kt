package com.lookie.opluskey.core.config

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
 *  切换配置就只换了引用、没被引用的东西：切过去看到的是一堆指向旧命令的绑定，
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
 *
 *  对调那套「读不懂 ≠ 没有 / 第二步失败要还原 / 老配置只搬当前」的规矩收在
 *  [ProfileSlotFile] 一处 —— 与变量表共用同一份实现，别在这里再写一遍。
 */
object ProfileCommands {
    /** 命令库这一层：槽名前缀 = 当前生效那份的槽名；宿主主进程也要读，所以 `openReadable = true`。 */
    private val file = ProfileSlotFile(ConfigStore.SLOT_COMMANDS, openReadable = true)

    /**
     * 某份配置对应的命令库槽名。
     *
     *  映射就是「把 `config` 换成 `commands`」：`config` → `commands`、`config-2` → `commands-2`
     *  —— 槽号因此在两层之间天然对得上，不需要另一张对照表。
     */
    fun slotOf(configSlot: String): String = file.slotOf(configSlot)

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
        when (val r = file.load(slotOf(configSlot))) {
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
        file.writeRaw(
            slotOf(configSlot),
            FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, CommandLibrary.serialize(commands)),
        )

    /**
     * 切换配置时的命令库对调 —— 对称于 [ConfigProfiles.switchTo] 对主配置做的那件事。
     *
     * @return 对调是否成功（目标配置没有自己的命令库时，「存过去」成功就算成功）
     * @see ProfileSlotFile.swap 顺序与还原那两条规矩的唯一实现
     */
    fun swap(configSlot: String): Boolean = file.swap(configSlot, "命令库")

    /** 删掉某份配置的命令库（连 `.bak` / `.new` 一起，走 [ModuleFiles.delete]）。 */
    fun delete(configSlot: String): Boolean = file.delete(configSlot)
}
