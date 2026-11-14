package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import java.util.UUID

/**
 * 这份配置是不是「默认」那份 —— **名字空且位次 0**，两个条件都要。
 *
 *  只按名字空判过一次，代价是：任何一次把名字写丢（按 `DEFAULT` 重建内容、或导入一份没名字的
 *  配置）都会让一份**普通配置「变成」默认** —— 不许改名、不许删除，列表里还与真默认撞名，
 *  用户看到的是「两份默认配置，我这份动不了」。位次 0 固定留给默认（见 [Config.profileOrder]），
 *  拿它一起判才不会误伤。
 *
 *  反过来也要成立：名字空的普通配置（位次 ≥ 1）**不是**默认，它该能被改名、能被删除。
 *
 * ## 为什么**不**把 `profile_id` 也算进来
 *
 *  曾经是「id 是 [ConfigProfiles.DEFAULT_PROFILE_ID]，或者（id 空 且 名字空 且 位次 0）」
 *  —— 那次多出来的 `id 空` 一个条件，正好放跑了「导入覆盖默认配置」这个 bug 的后果：
 *  `importInto` 给被覆盖的那份铸了个新 id，于是它 id 非空、名字空、位次 0，
 *  两个判据都不认它是默认 ⇒ 界面上按「名字空的普通配置」显示成**「未命名」**，
 *  而它本来该是默认（位次 0 是**只留给默认**的，普通配置从 [ConfigProfiles.nextSlot] 拿到的
 *  位次永远 ≥ 1）。所以这里只认**形态**：名字空 + 位次 0 就是默认，
 *  文件里那个 id 是派生值 —— 由 [assignProfileIds] 一并归回 [ConfigProfiles.DEFAULT_PROFILE_ID]。
 */
val Config.isDefaultProfile: Boolean
    get() = profileName.isBlank() && profileOrder == 0

/**
 * 把 [profile] 的**身份**搬到这份内容上来 —— id / 名字 / 位次三样，一件都不换。
 *
 * ## 为什么覆盖与重置都必须走这里
 *
 *  「覆盖」与「重置」改的都是**内容**，不是「这份配置是谁」。用户选的是「把这份换成那套」，
 *  不是「新造一份」。而身份那三位是分开存的三样东西，任何一处漏搬都会当场变成一个可见的 bug：
 *
 *  · 漏搬 **id** —— 最严重。`Item.isDefault` 判的是 `id == DEFAULT_PROFILE_ID`（不按名字空），
 *    于是覆盖「默认」之后它不再是默认，而名字本来就是空串，落到 `Item.displayName` 的
 *    `name.isBlank()` 那一档，界面上显示成**「未命名」**（真机踩过：导入覆盖默认配置，
 *    默认那份变成「未命名」）。它同时也失去了「不许改名 / 不许删除」那两条约束。
 *  · 漏搬 **名字** —— 用户选的是「把这份换成导入的那套」，不是「给导入的另起一个名字」。
 *  · 漏搬 **位次** —— 列表顺序会跳（位次跟着内容走，见 `Config.profileOrder`）。
 *
 * ## 为什么还要把 id 归一
 *
 *  老文件没有 `profile_id`；新一点的也可能带着**错的** id —— 「导入覆盖默认配置」曾在
 *  `importInto` 里给它铸过一个新 id（那个 bug 已修，见那里的注释）。所以身份不照抄文件里那个值，
 *  而是按**形态**重算：[isDefaultProfile] 为真 ⇒ [ConfigProfiles.DEFAULT_PROFILE_ID]；
 *  其余照搬（空串也照搬，由 [ConfigProfiles.list] 按槽位补一个、下一次写盘带上）。
 *  文件里那个 id 是**派生值**，这里就是它唯一的推导处。
 */
fun Config.withIdentityOf(profile: Config): Config =
    copy(
        profileId =
            if (profile.isDefaultProfile) {
                ConfigProfiles.DEFAULT_PROFILE_ID
            } else {
                profile.profileId
            },
        profileName = profile.profileName,
        profileOrder = profile.profileOrder,
    )

/**
 * 老数据 / id 撞车时用的槽位 id 前缀（`slot:config-2`）。
 *
 *  它**稳定且唯一**，但不「跟着内容走」（切换会换槽）—— 只当兜底用，下一次写盘就会被换成
 *  [newProfileId] 那种真 id。
 */
private const val SLOT_ID_PREFIX = "slot:"

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

    /**
     * 「默认」那一份的固定 id —— 升级时自动落下的退路，删不掉也改不了名。
     *
     *  用 id 而不是「名字空」表达这个身份：名字是可以被抹空的（按 `DEFAULT` 重建内容就会），
     *  一空就会被当成默认，两份撞在一起（真机踩过）。
     */
    const val DEFAULT_PROFILE_ID: String = "default"

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
        /**
         * 这份配置的**身份**（[Config.profileId]；老文件由 [list] 现算一个）。
         *
         *  界面上凡是「是不是同一份」的判断都该用它 —— 名字会被改、会被抹空，槽号会随内容搬家，
         *  只有它是跟着内容走且唯一的那一个。
         */
        val id: String,
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

                    //  名字空的**普通**配置（位次 ≥ 1）：不是默认，但也没有名字可显示 ——
                    //  必须给一句话，否则菜单里出现一个空格子（名字被抹过之后就是这一档）
                    name.isBlank() -> I18n.get(Strings.PROFILE_UNNAMED, "未命名")

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
        //  先读出能读的那几份，**统一分配唯一 id**（见 [assignIds]），再拼 Item —— 分配要看全局，
        //  一份一份地拼没法知道「这个 id 是不是已经被别人占了」。
        val readable = slots.mapNotNull { slot -> readOf(slot)?.let { slot to it } }
        val ids = assignProfileIds(readable)
        val items =
            slots.mapNotNull { slot ->
                val config = readable.firstOrNull { it.first == slot }?.second
                if (config == null) {
                    //  **读不出来的那一份也要列出来**：它占着槽位（[isFull] 按槽位算），
                    //  不列就既看不见也删不掉 —— 新建 / 导入会永久灰着，用户没有任何自救入口。
                    //  当前那一槽读不出来时不列：那不是「一份配置」，是整页只读态（见 `ConfigStore`）。
                    if (slot == CURRENT_SLOT) return@mapNotNull null
                    XLog.w("配置列表：$slot 读不出来，按「（读不出来）」列出（可删）")
                    return@mapNotNull Item(
                        slot = slot,
                        id = SLOT_ID_PREFIX + slot,
                        name = "",
                        isCurrent = false,
                        isDefault = false,
                        order = Int.MAX_VALUE,
                        broken = true,
                    )
                }
                val id = ids.getValue(slot)
                Item(
                    slot = slot,
                    id = id,
                    name = config.profileName,
                    isCurrent = slot == CURRENT_SLOT,
                    //  **按 id 判默认**，不按「名字空」：两份都名字空时，只有拿到 [DEFAULT_PROFILE_ID]
                    //  的那一份是默认，另一份在界面上显示成「未命名」（见 [Item.displayName]）。
                    isDefault = id == DEFAULT_PROFILE_ID,
                    order = config.profileOrder,
                )
            }
        return items.sortedWith(
            compareBy<Item> { if (it.isDefault) 0 else 1 }
                .thenBy { it.order }
                //  老配置没有位次（都是 0）：再按槽号兜一层（`config` 是原始那一槽 = 1）
                .thenBy { if (it.slot == CURRENT_SLOT) 1 else it.slot.removePrefix(SLOT_PREFIX).toIntOrNull() ?: Int.MAX_VALUE },
        )
    }

    /**
     * 这个名字被别的配置占了吗（新建 / 改名之前问一次）。
     *
     *  **按 id 排除自己**：改名时新名字与自己原名相同，不该判成重名。
     *
     *  @param excludeId 自己那份的 id（新建时传 `null`）
     */
    fun nameTaken(
        name: String,
        excludeId: String? = null,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return list().any { !it.broken && it.name == trimmed && it.id != excludeId }
    }

    /**
     * 切到 [slot] 对应的配置 —— **两份内容互换**，当前生效的仍然是 [CURRENT_SLOT]。
     *
     *  调用方随后要把返回的配置写进界面状态（[ConfigStore.load] 会按指纹自己发现文件换了，
     *  但当前进程手上这份 `Config` 要显式刷）。
     *
     * @return 切换后当前生效的配置；槽不存在 / 读不懂 / 主配置或附加层（命令库、变量表）写盘失败
     *   都返回 `null`，且盘上退回切换前的那一份 —— 不会出现「配置换了、命令库没换」的半切状态
     */
    fun switchTo(slot: String): Config? {
        if (slot == CURRENT_SLOT) return readOf(CURRENT_SLOT)
        val target =
            readOf(slot) ?: run {
                XLog.w("配置切换：$slot 读不出来，不动")
                return null
            }
        return swapWith(slot, target)
    }

    /**
     * [switchTo] 的主体：把当前那份与已经读出来的 [target] 对调（含两侧写盘与失败还原）。
     *
     * @return 切换后当前生效的配置；写盘失败 / 附加层没跟过去时 `null`（盘上退回切换前）
     */
    private fun swapWith(
        slot: String,
        target: Config,
    ): Config? {
        //  **顺序是有意的**：先把旧当前存进目标槽，再拿目标内容覆盖当前 —— 反过来写的话，
        //  中断时当前配置是「目标内容」而旧当前已经没了，那是最坏的一种。
        //  但只靠顺序不够：第二步失败时目标槽里已经是旧当前、原来那份只在这一次调用的 `target`
        //  里 —— 所以第二步失败要**把目标槽还原**，否则那份配置就永久被覆盖了。
        //  **当前那份读不懂就拒绝切换**（不是「当成没有」）：`staged = false` 会让下面直接用目标
        //  内容盖掉当前槽 —— 而那份坏文件往往只是外皮 / 截断坏了，原样留着还能救回来。
        //  它走的是**同一个失败出口**（不另起一个 return），所以「什么都没写」这一点由下面那个
        //  `currentUnreadable ||` 短路保证：条件第一项就命中，后面的写盘一个都不会执行。
        //  **按 [ConfigStore.load] 的三态判**（不是文件层的 `ModuleFiles.read`）：外皮坏了、或正文
        //  md5 对不上时，文件层照样给 `Ok`，只有走完整解码链才判得出「读不懂」。用文件层的判据会
        //  把这种文件当成「没有当前配置」，`staged` 一 false 就短路掉备份，直接用目标内容盖过去 ——
        //  而它往往只是外皮 / 截断坏了，原样留着还能救。
        val currentLoad = ConfigStore.load()
        val currentUnreadable = currentLoad is ConfigStore.Load.Unreadable
        val current =
            when (currentLoad) {
                is ConfigStore.Load.Ok -> {
                    currentLoad.config
                }

                is ConfigStore.Load.Recovered -> {
                    currentLoad.config
                }

                ConfigStore.Load.Absent, ConfigStore.Load.Unreadable -> {
                    null
                }
            }
        //  **两步都要成，第一步成了第二步失败要把第一步退回去**：
        //  第一步已经把旧当前写进了目标槽 —— 此时若不还原，那份目标配置就**永久没了**
        //  （目标内容已经被旧当前盖掉，内存里还留着的那个 `target` 只在这一次调用里有效）。
        val staged = current != null
        val firstOk = !staged || writeProfileConfig(slot, current)

        if (currentUnreadable || !firstOk || !writeProfileConfig(CURRENT_SLOT, target)) {
            //  第一步没写（当前配置读不出来）时目标槽还是原样，没什么要还原的
            val needed = staged && firstOk
            val restored = !needed || writeProfileConfig(slot, target)
            XLog.w(
                when {
                    currentUnreadable -> "配置切换：当前配置读不懂 ⇒ 拒绝切换（先修好它，别拿 $slot 盖掉）"

                    !firstOk -> "配置切换：$slot 没换成（第一步写盘失败），不动"

                    //  「没有要还原的东西」与「还原成功」是两件事：混成一句会让排查的人
                    //  以为盘上已经回到原样，而实际上第一步压根没发生。
                    !needed -> "配置切换：$slot 没换成（第一步没写），目标槽没动过"

                    restored -> "配置切换：$slot 没换成（第二步写盘失败），目标槽已还原"

                    //  最坏的一种：两份都写不进去。目标槽里现在是旧当前的内容，而它原来那份
                    //  只在这一行日志里留了个名字 —— 让排查的人知道丢了什么
                    else -> "配置切换：$slot 没换成，且还原失败 —— 目标槽里现在是旧当前的内容，原来的「${target.profileName}」已丢"
                },
            )
            return null
        }
        //  命令库跟着走（见 [ProfileCommands] 的类注释）：配置里绑定的是 `cmd:<名字>` 这种引用，
        //  只换配置不换库，切过去看到的是一堆指向旧命令的绑定。
        //  变量表同理（见 [ProfileVars]）：绑定里写着「计数 += 1」，值却留在上一份，
        //  切过去那套绑定就会接着改别人的计数。
        //
        //  **三层是一份东西：要么都过去、要么都留在这儿**。主配置上面已经写完了，附加层失败时
        //  不退回去，盘上就是「新配置 + 旧命令库 / 旧变量表」—— 而那种状态在界面上看不出来
        //  （按钮都在，按下去不执行 / 改的不是自己那套计数），用户只会觉得「切了但没切干净」。
        //  所以这一支失败要**整趟退回**，并如实返回 `null`（调用方据此报「切换失败」）。
        //  [ProfileSlotFile.swap] 返回 `false` 时自己已经退回原样：命令库没跟过去时库本身没动，
        //  变量表失败时命令库已经换过去了 —— 得再换回来。
        val commandsSwapped = ProfileCommands.swap(slot)
        if (commandsSwapped && ProfileVars.swap(slot)) {
            //  **当前槽的 `.prev` 刚被写成「上一份配置」**（覆盖前留的那份属于另一个 profile）。
            //  留着它，新当前槽哪天坏一次，`ConfigStore.recoverFromBackup` 会把**别人的配置**
            //  恢复成当前配置 —— 用户看到的是「切了个配置，回来绑定全变了」。切成功即丢掉。
            dropModuleFileBackup(ConfigStore.files().file(CURRENT_SLOT))
            XLog.w("配置切换：已切到 ${target.profileName.ifBlank { "默认" }}（槽 $slot）")
            return target
        }
        if (commandsSwapped && !ProfileCommands.swap(slot)) {
            XLog.w("配置切换：命令库换回来也失败（槽 $slot）—— 盘上的命令库与配置可能对不上")
        }
        val back =
            if (revertSwitch(slot, target, current)) {
                "主配置已退回切换前的那一份"
            } else {
                "退回也失败，盘上现在可能是半切的状态"
            }
        XLog.w("配置切换：$slot 没换成（命令库 / 变量表没跟过去）—— $back")
        return null
    }

    /**
     * 把主配置退回切换前的那一份（此刻 [slot] 里是 [current]，当前槽里是 [target]）。
     *
     *  先把目标那份写回它自己的槽、再把旧当前写回当前槽 —— 与切过去的顺序正好相反，中断时
     *  留下的是「两份相似内容」而不是「旧当前被永久覆盖」。切换前当前槽**原来就空着**
     *  （[current] 为 `null`）时，退回原样意味着删掉刚写进去的那一份。
     *
     * @return 退回去了吗；写盘失败时盘上可能停在中途，由调用方如实报失败
     */
    private fun revertSwitch(
        slot: String,
        target: Config,
        current: Config?,
    ): Boolean {
        if (current == null) return ConfigStore.files().delete(CURRENT_SLOT)
        return writeProfileConfig(slot, target) && writeProfileConfig(CURRENT_SLOT, current)
    }

    /**
     * 算出一份**改过名字**的配置（**不写盘**）。只改当前配置（[CURRENT_SLOT]）。
     *
     *  两条不许：名字为空、以及这份是「默认」配置（名字本来就是空串，改了就没地方回去了）。
     *
     *  「是不是默认」按**它在列表里的身份**（id）判，不按名字形态 —— 理由见 [isDefaultSlot]。
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
        //  默认配置**不许改名**：名字空着就是它的身份，改了名之后「升级时落下的退路」在列表里
        //  就再也认不出来了。判据是 id（与 [list] 同一口径），不是名字形态 —— 见 [isDefaultSlot]。
        if (isDefaultSlot(CURRENT_SLOT)) {
            XLog.w("配置改名：默认配置不许改名")
            return null
        }
        //  **名字不许重**（按 id 排除自己）：两份同名时菜单里两行一模一样，切错了只能靠猜
        if (nameTaken(trimmed, excludeId = config.profileId.ifBlank { null })) {
            XLog.w("配置改名：「$trimmed」已被占用，不改")
            return null
        }
        //  老文件没有 id：改名顺手补一个（这一份从此有了稳定身份）
        return config.copy(profileId = config.profileId.ifBlank { newProfileId() }, profileName = trimmed)
    }

    /** 这个槽名属于配置列表吗（当前槽，或 `config-<数字>`）—— 删除 / 覆盖入口的唯一判据。 */
    private fun isProfileSlot(slot: String): Boolean =
        slot == CURRENT_SLOT ||
            (slot.startsWith(SLOT_PREFIX) && slot.removePrefix(SLOT_PREFIX).toIntOrNull() != null)

    /**
     * 这一槽在列表里是不是那个「默认配置」—— **按 id 判**，与 [list] / [assignProfileIds] 同一口径。
     *
     *  为什么不能按名字形态（[Config.isDefaultProfile]：名字空 + 位次 0）判：真机上出现过两份都
     *  满足这条的数据（名字被抹过两次）。那时 [assignProfileIds] 只把 id 分给其中一份，另一份在
     *  界面上显示成「未命名」；改名与删除若仍按形态判，就会把两份**同时**当成默认 ——
     *  当前那份既改不了名也删不掉，还占着 [isFull] 的额度，新建 / 导入永久灰着，没有任何自救入口。
     */
    private fun isDefaultSlot(slot: String): Boolean = list().firstOrNull { it.slot == slot }?.isDefault == true

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
        if (!isProfileSlot(slot)) {
            //  公开 API：传进来的是别的槽（`ui` / `vars` / `commands`）就会删掉界面状态或变量表，
            //  而那几个槽由各自的 Store 管，不归配置列表删。
            XLog.w("配置删除：$slot 不是配置槽，拒绝")
            return false
        }
        if (slot == CURRENT_SLOT) {
            XLog.w("配置删除：当前配置不许删")
            return false
        }
        //  **默认配置不许删**（用户口径）：它是升级时落下的退路 —— 删掉之后「新建 / 导入失败时
        //  退回到哪一份」就没有答案了。读不出来（`readOf` 为 null）的槽按「坏了的普通配置」处理、照删：
        //  那是自救入口，堵掉会让「新建 / 导入」永久灰着。
        //  判据按 **id**（与 [list] 同一口径），不按名字形态 —— 见 [isDefaultSlot]。
        val target = readOf(slot)
        if (isDefaultSlot(slot)) {
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
     *  **「常用命令」那张清单不带**：它记的是一条条 `cmd:` / `app:` 之类的**动作引用**，属于
     *  「这一套绑定」（与 `keys` 同一档）。带过来的后果不是「多几行」而是**指向空** ——
     *  新配置的命令库是空的，清单里那几条 `cmd:common.*` 全成了没有正文的名字，点下去也不执行
     *  （用户口径「常用命令也要跟着切换配置走」）。
     *
     *  **收集来的自定义键不带**：那几颗键属于「这一套绑定」，换一份配置就该从头收集
     *  （用户口径：新配置里不该还留着上一份的键）。
     *
     * @return 新配置的槽名；名字为空 / 已到上限 / 写盘失败返回 `null`
     */
    fun createAs(name: String): String? {
        val trimmed = name.trim()
        //  两道前置一次判完（名字空 / 已到上限都由下面这一支与 [nextSlot] 各自拦住）：
        //  **名字不许重** —— 列表里两行一样，用户分不出哪行是哪份（切错了只能靠猜）
        if (trimmed.isEmpty() || nameTaken(trimmed)) {
            XLog.w("配置新建：名字「$trimmed」不可用（为空 / 已被占用），不建")
            return null
        }
        val (slot, order) = nextSlot() ?: return null
        val current = readOf(CURRENT_SLOT)
        val blank =
            Config.DEFAULT.copy(
                profileId = newProfileId(),
                profileName = trimmed,
                profileOrder = order,
                //  整份全局设置照搬，**只把清单那一位清空**（见上面 KDoc：它跟着绑定走，不跟着机器走）
                global = (current?.global ?: Config.DEFAULT.global).copy(commonCommands = emptyList()),
                disclaimerAccepted = current?.disclaimerAccepted ?: false,
                commandDisclaimerAccepted = current?.commandDisclaimerAccepted ?: false,
            )
        //  命令库留空（而不是复制当前那份）：空白配置里没有任何 `cmd:` 引用，把旧命令带过去
        //  会让它看着像「复制了一份」—— 与上一版的行为正好相反。
        //  变量表**落一份空表**（不是不写）：不写的话切换时目标槽空着会被当成老配置，
        //  新配置一上来就继承了上一套的计数（见 [ProfileVars] 的类注释）。
        //  **三段任一失败就撤掉这一槽**：只写主配置不写那两份的话，新配置会带着上一套的
        //  命令库 / 计数上线，正是上面两条要避免的事，而调用方拿到的还是一个「建成功了」。
        val saved =
            writeProfileConfig(slot, blank) &&
                ProfileCommands.save(slot, emptyList()) &&
                ProfileVars.save(slot, emptyList())
        if (!saved) {
            XLog.w("配置新建：$trimmed 没落成（主配置 / 命令库 / 变量表），撤掉这一槽")
            delete(slot)
            return null
        }
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
     *  **名字一定带序号**（「导入配置 1」/「导入配置 2」…）：序号从 1 起，第一份也带 ——
     *  以前第一份不带号，列表里并排放着「导入配置」和「导入配置 2」，数不出这是第几份。
     *
     * @param name 打底的名字（调用方给；空白时用「导入配置」打底，序号一律另补）
     * @return 新槽名；写盘失败返回 `null`
     */
    fun importAs(
        config: Config,
        commands: List<Command>,
        name: String,
    ): String? {
        val (slot, order) = nextSlot() ?: return null
        val base = name.trim().ifEmpty { "导入配置" }
        //  同名会让列表里出现两行一样的名字 —— 加个序号区分（导入的那份仍然插在最末）。
        //  **只扫配置槽**：`slots()` 里还有 commands / vars / logs 那些，拿它们去 `readOf`
        //  是把命令库当配置解析 —— 既白读一遍，又会每次导入都刷出一条「读不懂」的 W
        val taken =
            ConfigStore
                .files()
                .slots()
                .filter { it == CURRENT_SLOT || it.startsWith(SLOT_PREFIX) }
                .mapNotNull { readOf(it)?.profileName }
                .toSet()
        //  **序号从 1 起**：第一份就叫「导入配置 1」。以前第一份不带号、第二份才从 2 开始，
        //  列表里并排放着「导入配置」和「导入配置 2」，一眼数不出这是第几份（用户口径）。
        var seq = 1
        var trimmed = "$base $seq"
        while (trimmed in taken) {
            seq++
            trimmed = "$base $seq"
        }
        //  **必须换新 id**：导入的文件里带着别人的 `profile_id`，照抄下来会和本机已有的撞车
        val ordered = config.copy(profileId = newProfileId(), profileName = trimmed, profileOrder = order)
        val ok =
            ProfileCommands.save(slot, commands) &&
                writeProfileConfig(slot, ordered) &&
                //  **变量表也落一份空表**：不落的话这一槽没有 vars 文件，切换时会被当成「老配置」，
                //  于是导入的配置一上来就继承了当时那套计数（与 `createProfile` 同一条口径）
                ProfileVars.save(slot, emptyList())
        if (!ok) {
            ConfigStore.files().delete(slot)
            ProfileCommands.delete(slot)
            ProfileVars.delete(slot)
            XLog.w("配置导入：没写成（槽 $slot），已清理这一槽")
            return null
        }
        XLog.w("配置导入：$trimmed（槽 $slot，插在最末）")
        return slot
    }

    /**
     * **导入并覆盖到指定槽** —— 把外来配置盖到 [slot] 那一份上，替代「插在最末」。
     *
     *  与 [importAs] 的差别：目标是**一份已有配置**（含「默认」那一份，也可能就是当前生效的那份），
     *  内容整个换成导入的。**身份不跟着内容换** —— 名字、位次、id 三样都保留目标那份自己的，
     *  由 [withIdentityOf] 一处搬（漏搬 id 的后果见那里）：
     *  · 覆盖「默认」配置时名字仍是空串、身份仍是默认（[DEFAULT_PROFILE_ID]）；
     *  · 覆盖普通配置时保留那份的名字（换内容、不换名）—— 用户选的是「把这份换成导入的那套」，
     *    不是「给导入的另起一个名字」。
     *
     *  两段一起成、或一起不动：先落命令库、再落主配置，任一步失败就把这一槽还原成原样
     *  （不能像 [importAs] 那样删槽 —— 这里槽里本来就有东西，删了就丢了那份旧的）。
     *
     *  变量表也换成空表（与 [importAs] 同一条口径）：导入的那套不带走别人的计数。
     *
     *  **覆盖当前槽时**（`slot == CURRENT_SLOT`）：文件直接换成新内容，但**不广播、不动界面状态** ——
     *  那一半交给调用方走通用写入通道（界面的 `write` / `saveCommands`），这一层只负责把文件写对。
     *  这里返回写好的 [Config]，调用方拿去刷新。
     *
     * @param slot 目标槽（[CURRENT_SLOT] 或 [SLOT_PREFIX] 那一族）
     * @return 覆盖后的配置（已换新 id、保留那份的名字）；写盘失败返回 `null`
     */
    fun importInto(
        slot: String,
        config: Config,
        commands: List<Command>,
    ): Config? {
        //  与 [delete] 同一道闸：公开 API 传进来的若是 `ui` / `vars` / `commands` 那几槽，
        //  写下去就是把界面状态或变量表整份换成一份配置（那几个槽归各自的 Store 管）。
        if (!isProfileSlot(slot)) {
            XLog.w("配置导入覆盖：$slot 不是配置槽，拒绝")
            return null
        }
        val target =
            readOf(slot) ?: run {
                XLog.w("配置导入覆盖：$slot 读不出来，拒绝覆盖")
                return null
            }
        //  **只换内容，不换身份**：id / 名字 / 位次全按目标那份原样搬（见 [withIdentityOf]）。
        //  这里曾经写的是 `profileId = newProfileId()` —— 名字与位次搬了、身份没搬，于是
        //  覆盖「默认」之后它不再是默认（判据是 id），而名字本是空串，列表里显示成「未命名」。
        val ordered = config.withIdentityOf(target)
        //  先落命令库、再落主配置，与 [importAs] 同一个顺序（命令库是「内容的一半」，先写它，
        //  主配置失败时还可以按原样还原命令库）。
        //  **必须用三态读**：`read()` 把「读不懂」与「没有」都塌成 `null`，于是「原来那份读不懂」时
        //  覆盖失败根本还原不了 —— 新内容把旧内容永久盖掉。这一档直接拒绝覆盖。
        val previous = ProfileCommands.load(slot)
        if (previous is ProfileCommands.Load.Unreadable) {
            XLog.w("配置导入覆盖：$slot 的命令库读不出来，拒绝覆盖（否则原来那份会被永久盖掉）")
            return null
        }
        val ok =
            ProfileCommands.save(slot, commands) &&
                writeProfileConfig(slot, ordered) &&
                ProfileVars.save(slot, emptyList())
        if (!ok) {
            //  三段都可能已经把前面几段写成新的了，所以**两样都要还原**：
            //  命令库还原成原来那份；主配置还原成调用前读到的那一份（`target`）。
            //  只还原命令库的话，用户看到「导入失败」，而主配置其实已经变成导入的内容 ——
            //  那份旧配置从此没有任何副本。
            if (previous is ProfileCommands.Load.Ok) ProfileCommands.save(slot, previous.commands)
            //  **原来就没有命令库时要把刚建出来的那份删掉**：`ProfileCommands.save` 已经在上面
            //  落过盘，只把「有内容」那一档还原的话，盘上会多出一份原本不存在的 `commands-N` ——
            //  报的是失败，盘上却回不到原样。
            if (previous is ProfileCommands.Load.Absent) ProfileCommands.delete(slot)
            writeProfileConfig(slot, target)
            XLog.w("配置导入覆盖：$slot 没写成，已还原命令库与主配置")
            return null
        }
        XLog.w("配置导入覆盖：已覆盖 ${target.profileName.ifBlank { "默认" }}（槽 $slot）")
        return ordered
    }

    /**
     * 读某一份（导出、以及列表里取名字都用它）。读不出来返回 `null`。
     *
     *  **主文件读不懂时也试一次 `.prev`**：`[writeProfileConfig]` 每次写盘前都留一份快照，
     *  正是为这种情况准备的。不比（`ConfigStore.load` 给当前配置做的那一步）的话，非当前的那一份
     *  坏一次就只能删掉 —— 它明明还有一份可救的副本躺在同一个目录里。
     */
    fun readOf(slot: String): Config? {
        val fromMain =
            when (val r = ConfigStore.files().read(slot)) {
                is ModuleFiles.Load.Ok -> decodeConfig(r.text)
                ModuleFiles.Load.Absent, ModuleFiles.Load.Unreadable -> null
            }
        if (fromMain != null) return fromMain
        val backup = readModuleFileBackup(ConfigStore.files().file(slot)) ?: return null
        return decodeConfig(backup)?.also {
            XLog.w("配置列表：$slot 的主文件读不懂，用 .prev 快照顶上")
        }
    }

    /** 一份文件正文 → 配置（外皮剥不开 / 解析不了都是 `null`）。 */
    private fun decodeConfig(text: String): Config? =
        FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, text)?.let { ConfigCodec.parseOrNull(it) }

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
    fun isFull(): Boolean = ConfigStore.files().slots().count { it != CURRENT_SLOT && it.startsWith(SLOT_PREFIX) } >= MAX_PROFILES

    /**
     * **主文件自己**读得懂吗（不回落 `.prev`）。
     *
     *  只服务 [writeProfileConfig] 那一道「该不该留快照」的判据，所以别拿它当读配置的入口
     *  （那个用 [readOf]）。为什么不能用 [readOf]：主文件已经坏、唯一可救的是 `.prev` 时，
     *  `readOf` 会因为 `.prev` 读得懂而返回非 null —— 于是写完一次就把那份备份换成了坏内容，
     *  退路永久消失。判据与 `ConfigStore.write` 的 `load() is Load.Ok` 同一条。
     */
    internal fun mainFileReadable(slot: String): Boolean =
        when (val r = ConfigStore.files().read(slot)) {
            is ModuleFiles.Load.Ok -> decodeConfig(r.text) != null
            ModuleFiles.Load.Absent, ModuleFiles.Load.Unreadable -> false
        }

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
        //  **位次只从读得出来的配置里取**：坏份的 `profileOrder` 不可知，给它一个 `Int.MAX_VALUE`
        //  之类的占位就会把后面每一次新建都顶到最大值上去。槽号那一半仍按槽位数算（见 [isFull]），
        //  所以「有一份坏了」不会让新配置复用它的槽号。
        val maxOrder = configs.maxOfOrNull { it.second.profileOrder } ?: 0
        return "$SLOT_PREFIX${maxOf(maxIndex + 1, FIRST_INDEX)}" to (maxOrder + 1)
    }
}

/**
 * 写某一份（与 [ConfigStore.write] 同一层外皮与 `openReadable`：宿主主进程要读得到）。
 *
 *  与 [assignProfileIds] / [newProfileId] 一样放在文件级：它们都是纯帮手，不碰这个对象的状态 ——
 *  对象只留「对外的那套业务动作」（`detekt` 的函数数阈值也是这个口径）。
 */
private fun writeProfileConfig(
    slot: String,
    config: Config,
): Boolean {
    //  **该槽的主文件现在读得懂才留 `.prev`** —— 与 [ConfigStore.write] 同一口径
    //  （那边判的是 `load() is Load.Ok`）：读不懂（截断 / 手改坏）时那份快照往往正是唯一
    //  还能救的副本，把它换成一份坏内容等于当场把退路毁掉。
    //
    //  **判据必须是「主文件本身」**，不能拿 [ConfigProfiles.readOf]：那个会在主文件坏掉时
    //  回落 `.prev` —— 主文件已经坏、唯一可救的是 `.prev`，`readOf` 却因为 `.prev` 读得懂
    //  而返回非 null，于是把那份备份换成坏内容：切档 / 导入一次，备份就永久没了。
    if (ConfigProfiles.mainFileReadable(slot)) snapshotModuleFile(ConfigStore.files().file(slot))
    return ConfigStore.files().write(
        slot,
        FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, ConfigCodec.serialize(config)),
        openReadable = true,
    )
}

/**
 * 给每一份配置分配一个**唯一** id（[ConfigProfiles.Item.id]）。
 *
 *  三条规则，顺序不能换：
 *  1. **默认那份按形态认领**：名字空 + 位次 0（[Config.isDefaultProfile]）⇒
 *     [ConfigProfiles.DEFAULT_PROFILE_ID]，**不管文件里写的是什么** —— 历史上「导入覆盖默认配置」
 *     给被覆盖的那份铸过新 id，照抄那个值的话它既不是默认、又显示成「未命名」（见 [isDefaultProfile]）；
 *  2. 其余照用文件里的 id（id 跟着内容走，切换后仍然是同一份）；老文件没有 id 的拿槽位 id
 *     （下一次写盘就会被补成随机 id，见 [ConfigProfiles.renamed] / `createAs`）；
 *  3. **撞了就往槽位 id 退**：真机上出现过两份都满足「名字空 + 位次 0」的数据（名字被抹过两次），
 *     那时两份都想当默认 —— id 必须唯一，否则菜单里两行分不出谁是谁。
 *
 *  「默认」优先分给**非当前槽**那一份：它是退路，本来就该待在非当前槽；当前槽那份如果是老数据
 *  没名字，宁可给它槽位 id、让界面显示「未命名」，也不要让它把「默认」抢走。
 */
private fun assignProfileIds(readable: List<Pair<String, Config>>): Map<String, String> {
    val taken = mutableSetOf<String>()
    val out = mutableMapOf<String, String>()
    for ((slot, config) in readable.sortedBy { if (it.first == ConfigProfiles.CURRENT_SLOT) 1 else 0 }) {
        var id =
            when {
                //  默认的身份由形态决定（规则 1），文件里那个 id 是派生值
                config.isDefaultProfile -> ConfigProfiles.DEFAULT_PROFILE_ID

                config.profileId.isBlank() -> SLOT_ID_PREFIX + slot

                else -> config.profileId
            }
        if (!taken.add(id)) {
            id = SLOT_ID_PREFIX + slot
            taken.add(id)
        }
        out[slot] = id
    }
    return out
}

/** 新配置的 id —— 与名字、槽号都无关，切换后跟着内容走。 */
private fun newProfileId(): String =
    "p-" +
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(10)
