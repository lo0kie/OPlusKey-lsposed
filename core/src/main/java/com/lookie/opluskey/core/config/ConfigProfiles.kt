package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config

/**
 * **多份配置**的切换与管理 —— 建在 [ModuleFiles] 之上的一层业务规则，自己不碰任何文件路径。
 *
 * ## 存储布局（全部复用现成的槽）
 *
 * ```
 * opluskey_config.conf   + opluskey_commands.conf    ← 当前生效的那一份（宿主按键时读的就是它）
 * opluskey_config-2.conf + opluskey_commands-2.conf  ← 第 2 份
 * opluskey_config-3.conf + opluskey_commands-3.conf  ← 第 3 份……
 * ```
 *
 * 槽名就是 [ModuleFiles] 那套 `<模块 id>_<槽名>.conf` 里的槽名，所以列、读、写、删全走它 ——
 * 这一层只决定「哪份算当前、槽号怎么分、谁能改」。**命令库那一半在 [ProfileCommands] 里**
 * （它管同一套槽号的 `commands-*`）：一份配置 = 主配置 + 它的命令库，切换 / 复制 / 删除时
 * 两边一起动，这里的每个入口都替调用方带上那一步。
 *
 * ## 「当前」为什么固定是 `config` 槽
 *
 *  宿主在**按键路径**上读 `opluskey_config.conf`（`ConfigStore.load`），注入侧吃的又是宿主发的
 *  配置广播 —— 如果「当前是哪份」变成一张指针表，那两个地方都要多读一份文件、还多一种
 *  「指针与内容不一致」的故障。所以约定：**当前生效的永远是 `config` 槽**，切换就是两份内容互换。
 *
 *  代价是切换要写四次盘（配置两份 + 命令库两份），几 KB 的事；换来的是执行侧零改动。
 *
 * ## 名字跟着内容走
 *
 *  [Config.profileName] 存在配置自己里头，不绑槽号 —— 交换之后名字仍跟着内容走，不会错位。
 *  空串 = 「默认」配置（升级时自动落下的退路），它**不许改名、不许删除**：删掉就没地方回去了。
 */
object ConfigProfiles {
    /** 当前生效那一份的槽名 —— 与 [ConfigStore.SLOT_CONFIG] 同一个，别各写一份字面量。 */
    const val CURRENT_SLOT: String = ConfigStore.SLOT_CONFIG

    /** 其余配置的槽名前缀（`config-2` / `config-3` …）。 */
    private const val SLOT_PREFIX = "${ConfigStore.SLOT_CONFIG}-"

    /** 第一份可分配的编号：`config-1` 不与 `config` 抢，但跳过 1 更好读（「第 2 份」）。 */
    private const val FIRST_INDEX = 2

    /**
     * 除「默认」之外**最多还能有几份配置** —— 用户口径「配置不包含默认在内，最多三份」。
     *
     *  加上「默认」一共四份：这是一个「够用就好」的量（两三套场景轮换），也是列表在菜单里
     *  铺得下、不长到需要滚动的前提。
     *
     *  界面拿它做「新建 / 导入」那一行的闸（灰掉 + 说明），这里仍然是真正的拦截点。
     */
    const val MAX_PROFILES: Int = 3

    /**
     * 一份配置在列表里的样子。
     *
     * @param slot 槽名（[CURRENT_SLOT] = 当前生效的配置）
     * @param name 用户起的名字；[isDefault] 为真时它是空串，界面自己显示成「默认」
     * @param isCurrent 是不是当前生效的配置
     * @param isDefault 是不是「默认」配置（名字为空）—— 不许改名、不许删除
     * @param order 列表位次（[Config.profileOrder]；0 = 「默认」配置）
     * @param broken 文件在、但读不出来（截断 / 手改坏）—— 名字与位次都取不到，只剩槽名可信。
     *   这一档仍然要出现在列表里：它占着槽位，删不掉就等于永久少一格（见 [isFull]）
     */
    data class Item(
        val slot: String,
        val name: String,
        val isCurrent: Boolean,
        val isDefault: Boolean,
        val order: Int,
        val broken: Boolean = false,
    ) {
        /**
         * 界面上怎么写它 —— 名字为空的配置（[isDefault]）写「默认配置」，其余写用户起的名字；
         * 读不出来的那一份写「（读不出来）」。
         *
         *  放在这里而不是各页各写一份：切换 / 导出那几层菜单与删除提示都要这一行，
         *  各写一遍就会有一处忘了处理空名字，界面上出现一个空格子。
         */
        val displayName: String
            get() =
                when {
                    broken -> I18n.get(Strings.PROFILE_BROKEN, "（读不出来）")
                    isDefault -> I18n.get(Strings.PROFILE_DEFAULT, "默认配置")
                    else -> name
                }
    }

    /**
     * 列出全部配置，顺序**只看位次**（[Config.profileOrder]），「默认配置」永远在最上面。
     *
     * ## 为什么不用槽号排
     *
     *  切换是**两份内容互换**：内容会在槽之间搬家。按槽号排的话，刚切过去的那份会从后面的行
     *  跳到第一行、切回来又跳回去 —— 用户会觉得列表自己在动。位次跟着**内容**走（随落盘），
     *  所以切换前后列表一模一样，只有打勾的那一行变了。
     *
     *  老配置没有位次（升级时都是 0）：那时三档依次是「是不是默认」「位次」「槽号」——
     *  默认仍在最上面，其余按槽号（= 老的创建顺序）排，行为与升级前一致。
     *
     *  注意：**每份都要读盘 + 解一层外皮 + 解析**（名字与位次都在文件里）。几份的量级，但调用点
     *  别放在组合期每帧跑 —— 放在 `LaunchedEffect` / IO 线程上。
     */
    fun list(): List<Item> {
        val slots =
            ConfigStore
                .files()
                .slots()
                .filter { it == CURRENT_SLOT || it.startsWith(SLOT_PREFIX) }
        return slots
            .mapNotNull { slot ->
                val config = readOf(slot)
                if (config == null) {
                    //  **读不出来的那一份也要列出来**：它占着槽位（[isFull] 按槽位算），
                    //  不列就既看不见也删不掉 —— 新建 / 导入会永久灰着，用户没有任何自救入口。
                    //  当前那一槽读不出来时不列：那不是「一份配置」，是整页只读态（见 `ConfigStore`）。
                    if (slot == CURRENT_SLOT) {
                        return@mapNotNull null
                    }
                    XLog.w("配置列表：$slot 读不出来，按「（读不出来）」列出（可删）")
                    return@mapNotNull Item(
                        slot = slot,
                        name = "",
                        isCurrent = false,
                        isDefault = false,
                        order = Int.MAX_VALUE,
                        broken = true,
                    )
                }
                Item(
                    slot = slot,
                    name = config.profileName,
                    isCurrent = slot == CURRENT_SLOT,
                    isDefault = config.profileName.isBlank(),
                    order = config.profileOrder,
                )
            }.sortedWith(
                compareBy<Item> { if (it.isDefault) 0 else 1 }
                    .thenBy { it.order }
                    //  老配置没有位次（都是 0）：再按槽号兜一层（`config` 是原始那一槽 = 1）
                    .thenBy { if (it.slot == CURRENT_SLOT) 1 else it.slot.removePrefix(SLOT_PREFIX).toIntOrNull() ?: Int.MAX_VALUE },
            )
    }

    /**
     * 切到 [slot] 对应的配置 —— **两份内容互换**，当前生效的仍然是 [CURRENT_SLOT]。
     *
     *  调用方随后要把返回的配置写进界面状态（[ConfigStore.load] 会按指纹自己发现文件换了，
     *  但当前进程手上这份 `Config` 要显式刷）。
     *
     * @return 切换后当前生效的配置；槽不存在 / 读不懂 / 写盘失败都返回 `null`（什么都不改）
     */
    fun switchTo(slot: String): Config? {
        if (slot == CURRENT_SLOT) return readOf(CURRENT_SLOT)
        val target =
            readOf(slot) ?: run {
                XLog.w("配置切换：$slot 读不出来，不动")
                return null
            }
        //  **顺序是有意的**：先把旧当前存进目标槽，再拿目标内容覆盖当前 —— 反过来写的话，
        //  中断时当前配置是「目标内容」而旧当前已经没了，那是最坏的一种。
        //  但只靠顺序不够：第二步失败时目标槽里已经是旧当前、原来那份只在这一次调用的 `target`
        //  里 —— 所以第二步失败要**把目标槽还原**，否则那份配置就永久被覆盖了。
        val current = readOf(CURRENT_SLOT)
        //  **两步都要成，第一步成了第二步失败要把第一步退回去**：
        //  第一步已经把旧当前写进了目标槽 —— 此时若不还原，那份目标配置就**永久没了**
        //  （目标内容已经被旧当前盖掉，内存里还留着的那个 `target` 只在这一次调用里有效）。
        val staged = current != null
        val firstOk = !staged || writeConfig(slot, current)
        if (!firstOk || !writeConfig(CURRENT_SLOT, target)) {
            //  第一步没写（当前配置读不出来）时目标槽还是原样，没什么要还原的
            val restored = !(staged && firstOk) || writeConfig(slot, target)
            XLog.w(
                when {
                    !firstOk -> "配置切换：$slot 没换成（第一步写盘失败），不动"

                    restored -> "配置切换：$slot 没换成（第二步写盘失败），目标槽已还原"

                    //  最坏的一种：两份都写不进去。目标槽里现在是旧当前的内容，而它原来那份
                    //  只在这一行日志里留了个名字 —— 让排查的人知道丢了什么
                    else -> "配置切换：$slot 没换成，且还原失败 —— 目标槽里现在是旧当前的内容，原来的「${target.profileName}」已丢"
                },
            )
            return null
        }
        //  命令库跟着走（见 [ProfileCommands] 的类注释）：配置里绑定的是 `cmd:<名字>` 这种引用，
        //  只换配置不换库，切过去看到的是一堆指向旧命令的绑定。这一步失败不回退配置 ——
        //  两份配置都完整、只是命令库对不上，回退反而会把刚写好的配置弄丢（日志由那边打）。
        ProfileCommands.swap(slot)
        //  变量表同样跟着走（见 [ProfileVars]）：绑定里写着「计数 += 1」，值却留在上一份，
        //  切过去那套绑定就会接着改别人的计数。失败理由同上，不回退配置。
        ProfileVars.swap(slot)
        XLog.w("配置切换：已切到 ${target.profileName.ifBlank { "默认" }}（槽 $slot）")
        return target
    }

    /**
     * 算出一份**改过名字**的配置（**不写盘**）。
     *
     *  两条不许：名字为空、以及这份是「默认」配置（名字本来就是空串，改了就没地方回去了）。
     *
     *  写盘交给调用方走通用写入通道（界面的 `write`）：那条路负责页面状态、配置广播与重问注入侧。
     *  这一层自己写一次的话，那三样都得在这儿再补一遍 —— 而它连页面都不认识。
     *
     * @return 改好名字的配置；不能改时 `null`（调用方只提示一句）
     */
    fun renamed(
        config: Config,
        name: String,
    ): Config? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (config.profileName.isBlank()) {
            XLog.w("配置改名：默认配置不许改名")
            return null
        }
        return config.copy(profileName = trimmed)
    }

    /**
     * 删除 [slot] 槽里的配置。
     *
     *  两条不许：**当前配置**（删了就没配置可跑）与**「默认」配置**（升级时落下的退路）。
     *
     *  **读不出来也要能删**：文件坏了的时候 `readOf` 返回 `null`，按「读不出来 ⇒ 不给删」处理的话，
     *  那个槽就永远删不掉；而 [isFull] 按**槽位**数，于是「新建 / 导入」永久灰着 —— 用户没有任何
     *  自救入口。坏文件里已经没有可用的内容了，删掉是唯一合理的出路。
     *
     * @return 真的删掉了吗
     */
    fun delete(slot: String): Boolean {
        if (slot == CURRENT_SLOT) {
            XLog.w("配置删除：当前配置不许删")
            return false
        }
        //  读得出来就按名字拦「默认配置」；读不出来无法判断，按「坏了的普通配置」处理
        val target = readOf(slot)
        if (target != null && target.profileName.isBlank()) {
            XLog.w("配置删除：默认配置不许删")
            return false
        }
        val ok = ConfigStore.files().delete(slot)
        //  命令库一起删：留着就是一份谁也认不出的孤儿槽（下一次 [nextSlot] 也不会回收这个名字）
        if (ok) ProfileCommands.delete(slot)
        //  变量表同理（见 [ProfileVars]）：孤儿槽一样不会被回收
        if (ok) ProfileVars.delete(slot)
        if (ok) {
            XLog.w("配置删除：已删 ${target?.profileName ?: "（读不出来的那一份）"}（槽 $slot）")
        }
        return ok
    }

    /**
     * **新建**一份**空白**配置，名字为 [name]，插在最末。
     *
     * ## 空白 = 什么算空白
     *
     *  按键绑定全回默认（无操作）、没有组合键、没有场景表、命令库也是空的 —— 这是一副「还没
     *  配过」的配置（用户口径「新建配置应该是一份空配置」），切过去就能从头配。
     *
     *  但**本机的全局设置跟着带过来**（模块总闸 / 语言 / 配色 / 振动 / 判定参数…）：那几位是
     *  「这台机器怎么用」，不是「这一套绑定怎么配」。新配置一上来就把模块关掉、界面换成英文，
     *  不是任何人想要的结果；「已确认免责协议」同理，不该被一次新建抹掉。
     *
     * @return 新配置的槽名；名字为空 / 已到上限 / 写盘失败返回 `null`
     */
    fun createAs(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val (slot, order) = nextSlot() ?: return null
        val current = readOf(CURRENT_SLOT)
        val blank =
            Config.DEFAULT.copy(
                profileName = trimmed,
                profileOrder = order,
                global = current?.global ?: Config.DEFAULT.global,
                disclaimerAccepted = current?.disclaimerAccepted ?: false,
                commandDisclaimerAccepted = current?.commandDisclaimerAccepted ?: false,
            )
        if (!writeConfig(slot, blank)) return null
        //  命令库留空（而不是复制当前那份）：空白配置里没有任何 `cmd:` 引用，把旧命令带过去
        //  会让它看着像「复制了一份」—— 与上一版的行为正好相反。
        ProfileCommands.save(slot, emptyList())
        //  变量表**落一份空表**（不是不写）：不写的话切换时目标槽空着会被当成老配置，
        //  新配置一上来就继承了上一套的计数（见 [ProfileVars] 的类注释）
        ProfileVars.save(slot, emptyList())
        XLog.w("配置新建：$trimmed（槽 $slot，空白）")
        return slot
    }

    /**
     * **导入**一份外来配置 —— 存成新的一份，**插在最末**，当前生效的配置一个字节都不动。
     *
     *  不覆盖当前配置：导入是「把别人配置里那套东西拿过来看看」，而用户此刻正在用的这份
     *  未必想丢。当前配置不动，就绝不会出现「导错了、原来的也没了」。
     *  （要换成它，导入完从菜单里切一下即可。）
     *
     *  两段一起成、或一起不动：先落命令库、再落主配置，任一步失败就把这一槽清干净。
     *
     * @param name 新配置的名字（调用方给；空白时按「导入配置」记）
     * @return 新槽名；写盘失败返回 `null`
     */
    fun importAs(
        config: Config,
        commands: List<Command>,
        name: String,
    ): String? {
        val (slot, order) = nextSlot() ?: return null
        val base = name.trim().ifEmpty { "导入配置" }
        //  同名会让列表里出现两行一样的名字 —— 加个序号区分（导入的那份仍然插在最末）
        //  **只扫配置槽**：`slots()` 里还有 commands / vars / logs 那些，拿它们去 `readOf`
        //  是把命令库当配置解析 —— 既白读一遍，又会每次导入都刷出一条「读不懂」的 W
        val taken =
            ConfigStore
                .files()
                .slots()
                .filter { it == CURRENT_SLOT || it.startsWith(SLOT_PREFIX) }
                .mapNotNull { readOf(it)?.profileName }
                .toSet()
        var trimmed = base
        var seq = 2
        while (trimmed in taken) {
            trimmed = "$base $seq"
            seq++
        }
        val ordered = config.copy(profileName = trimmed, profileOrder = order)
        val ok = ProfileCommands.save(slot, commands) && writeConfig(slot, ordered)
        if (!ok) {
            ConfigStore.files().delete(slot)
            ProfileCommands.delete(slot)
            XLog.w("配置导入：没写成（槽 $slot），已清理这一槽")
            return null
        }
        XLog.w("配置导入：$trimmed（槽 $slot，插在最末）")
        return slot
    }

    /** 读某一份（导出、以及列表里取名字都用它）。读不出来返回 `null`。 */
    fun readOf(slot: String): Config? =
        when (val r = ConfigStore.files().read(slot)) {
            is ModuleFiles.Load.Ok -> {
                FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, r.text)?.let { ConfigCodec.parseOrNull(it) }
            }

            ModuleFiles.Load.Absent, ModuleFiles.Load.Unreadable -> {
                null
            }
        }

    /**
     * 除「默认」之外还能不能再建一份 —— 按**槽位数**，不按「能读出来的内容」。
     *
     *  某一份文件损坏时它读不出内容，但那个槽仍然占着一个位置：按内容数会让用户以为还有空位、
     *  建出来的新一份却把别人的位置顶了。
     *
     *  这一档与 [list] 的口径必须一致：那边**也**把读不出来的那份列出来（带 `broken`），
     *  用户看到几行就是几个槽位、删掉一行就腾出一格 —— 否则「已满」与「只看到两份」会
     *  同时出现在一屏里，而中间那一格既看不见也删不掉。
     *
     *  界面拿它做「新建 / 导入」的闸（灰掉 + 一句提示），`nextSlot` 里还有同一道判据。
     */
    fun isFull(): Boolean = ConfigStore.files().slots().count { it == CURRENT_SLOT || it.startsWith(SLOT_PREFIX) } - 1 >= MAX_PROFILES

    /**
     * 下一份新配置该用的**槽名与位次**（新建与导入都要这两个，所以一起算）。
     *
     *  · 槽号 = 现有最大值 + 1（不回收：回收会让「槽号 = 创建顺序」这条失效）；
     *  · 位次 = 现有最大值 + 1（「默认」配置固定占 0）—— 位次随**内容**走，切换不会把它弄乱。
     */
    private fun nextSlot(): Pair<String, Int>? {
        val slots = ConfigStore.files().slots()
        //  **与 [isFull] 同一把尺子**（都是槽位数，不是「读得出来的份数」）：
        //  两处各数各的时，刚好在「有一份文件坏了」的时候给出不同答案
        //  （界面按已满灰掉，内部却还以为有空位）。
        if (isFull()) {
            XLog.w("配置新建：除「默认」之外已经有 $MAX_PROFILES 份，不再新增")
            return null
        }
        val configs =
            slots
                .filter { it == CURRENT_SLOT || it.startsWith(SLOT_PREFIX) }
                .mapNotNull { slot -> readOf(slot)?.let { slot to it } }
        val maxIndex =
            slots
                .filter { it.startsWith(SLOT_PREFIX) }
                .mapNotNull { it.removePrefix(SLOT_PREFIX).toIntOrNull() }
                .maxOrNull() ?: (FIRST_INDEX - 1)
        val maxOrder = configs.maxOfOrNull { it.second.profileOrder } ?: 0
        return "$SLOT_PREFIX${maxOf(maxIndex + 1, FIRST_INDEX)}" to (maxOrder + 1)
    }

    /** 写某一份（与 [ConfigStore.write] 同一层外皮与 `openReadable`：宿主主进程要读得到）。 */
    private fun writeConfig(
        slot: String,
        config: Config,
    ): Boolean =
        ConfigStore.files().write(
            slot,
            FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, ConfigCodec.serialize(config)),
            openReadable = true,
        )
}
