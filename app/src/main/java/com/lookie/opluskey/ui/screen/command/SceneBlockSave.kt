package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.write

// 「这张场景表往哪儿写」的两条路 —— 整表（「系统场景」页那颗「确认」）与单块（「场景动作」页那颗）。
//
//  两条路共用同一段落盘（[writeScenes]）：找「在编的那一份」、换掉它的 `scenes`、写盘、打日志。
//  差别在两处**写哪张表、写表里的哪些字段**：整表写的是屏幕上那份草稿（条件 + 三档）；
//  单块写的是配置那份表里的一格，且只换那**动作那几格**（[saveSceneBlock]）——
//  条件与停用位归「系统场景」页，那一页没按「确认」就不该落盘。

/** 单块落盘的三种结果（见 [saveSceneBlock]）。 */
internal enum class SceneBlockSave {
    /** 写成了 —— 草稿快照已经跟上，调用方可以退页。 */
    Saved,

    /** 没写：这一块还没有配置槽位，或配置在别处变过。改动留在草稿里，上一页那颗「确认」会写。 */
    DraftOnly,

    /** 写盘失败（什么都没变）—— 调用方**不要**退页。 */
    Failed,
}

/**
 * 把 [scenes] 写进「在编的那一份」（这颗键 / 这一档组合键）—— 两条落盘路唯一共用的那一段。
 *
 *  别的键 / 别的组合键一个字节都不动，那一段仍按原样带过去。
 *
 * @return 调用方只在 [CommandsSave.Failed] 时按「什么都没变」处理（广播失败时盘上已经是新样子）
 */
internal fun SettingsPage.writeScenes(scenes: List<SceneRule>): CommandsSave {
    val cfg = configState.config.value
    val comboId = branchDraft.comboId
    if (comboId != null) {
        val combo = cfg.combos.firstOrNull { it.id == comboId }
        if (combo == null) {
            //  在别处把这一组删了：不能再往一个不存在的 id 上写（那等于凭空造一档组合键）
            XLog.w("系统场景：组合键 $comboId 已经不在了，本次不保存")
            return CommandsSave.Failed
        }
        val label = comboLabel(cfg, combo)
        val next = cfg.copy(combos = cfg.combos.map { if (it.id != comboId) it else it.copy(scenes = scenes) })
        val result = write(next, "系统场景：$label（${scenes.size} 块）")
        if (result != CommandsSave.Failed) XLog.w("系统场景已保存：$label ${scenes.size} 块")
        return result
    }
    val key = branchKeyNow()
    val next =
        cfg.copy(
            keys =
                cfg.keys.mapValues { (id, keyConfig) ->
                    if (id != key) keyConfig else keyConfig.copy(scenes = scenes)
                },
        )
    val result = write(next, "系统场景：${key.label}（${scenes.size} 块）")
    if (result != CommandsSave.Failed) XLog.w("系统场景已保存：${key.label} ${scenes.size} 块")
    return result
}

/**
 * 配置里「在编的那一份」现在的场景表（`null` = 那一份已经不在了）。
 *
 *  收成**纯函数**而不是 `SettingsPage` 扩展：它只读配置，唯一调用点就是下面那颗「确认」的落盘口，
 *  不需要页面上的任何东西。页面扩展成员数是棘轮判据 3（`tools/ratchet.txt`），
 *  凡是「拿得到 cfg 就能算」的都不该再往那个计数器上加一格。
 */
private fun scenesNowOf(
    cfg: Config,
    comboId: String?,
    key: KeyId,
): List<SceneRule>? =
    if (comboId != null) {
        cfg.combos.firstOrNull { it.id == comboId }?.scenes
    } else {
        cfg.key(key).scenes
    }

/**
 * 只把草稿里第 [at] 块的**动作那几格**写回配置 —— 「场景动作」页那颗「确认」的唯一落盘口。
 *
 * 「动作那几格」= 三档手势 + 松手那一族（`releaseOn` / `release`）：它们回答的都是「这一档做什么」。
 *
 * ## 为什么不整表写回
 *
 *  「场景动作」页与「系统场景」页是**两件独立的事**（这一页改「这一档做什么」，上一页改
 *  「这一块在什么时候生效」）。整表写回会把上一页还没确认的条件改动一起落盘 —— 等于替用户
 *  按了那颗「确认」，那正是这一页不该做的事。
 *
 * ## 也不整块写回
 *
 *  只换一格还不够：草稿里那一块是**同一个 `SceneRule`**，上一页改了还没确认的 `conditions` /
 *  `enabled` 就挂在它身上。整块写下去等于把「这一块在什么时候生效」也一并确认了 —— 连
 *  「条件还没选完」那种半成品都会被写进配置（上一页那颗「确认」本来会拦住它）。
 *
 *  所以写下去的那一块是「**配置里的条件 + 草稿里的动作那几格**」：这一页只认动作，另外两个字段
 *  原样取配置那份。条件改动仍留在草稿里，退回上一页那颗「确认」还亮着，由它自己决定什么时候写。
 *
 * ## 凭什么能只写一块
 *
 *  草稿每一行记着自己在配置里的**槽位**（`BranchDraft.slots`，载入时按位置对齐、加块时为
 *  `null`），删块 / 挪块都跟着行自己走。所以「草稿第 N 块」能落到「配置第 M 格」，而不用
 *  按内容去猜对应关系（`scenesToWrite` 那条注释说的就是为什么不能猜）。
 *
 *  两道闸，过不去就**不写**（改动留在草稿里，上一页那颗「确认」照样能整表写下去）：
 *   · 这一块是新加的（没有槽位）—— 配置里还没有它的位置；
 *   · 槽位上那一块已经不是载入时那一块 —— 配置在别处变过（导入 / 别的入口写过），
 *     这时按旧下标写会把别的一块盖掉。
 */
internal fun SettingsPage.saveSceneBlock(at: Int): SceneBlockSave {
    val draft = branchDraftNow()
    val block = draft.scenes.getOrNull(at) ?: return SceneBlockSave.DraftOnly
    val slot = draft.slotOf(at)
    val base = scenesNowOf(configState.config.value, branchDraft.comboId, branchKeyNow())
    val write = blockWrite(base, slot, draft.loadedAt(slot), block)
    return when {
        write == null -> {
            XLog.w("场景动作：第 ${at + 1} 块没有可写的配置槽位，本次只留在草稿里")
            SceneBlockSave.DraftOnly
        }

        writeScenes(write.scenes) == CommandsSave.Failed -> {
            SceneBlockSave.Failed
        }

        else -> {
            //  快照换成**写下去的那一块**（不是草稿那一块）：条件改动没跟着走，这一块就还是脏的
            draft.noteBlockSaved(at, write.block)
            XLog.w("场景动作：第 ${at + 1} 块的动作已写回配置")
            SceneBlockSave.Saved
        }
    }
}

/** 单块写回要落下去的那一份：写进配置的那一块 + 换掉这一格之后的整表。 */
private class BlockWrite(
    val block: SceneRule,
    val scenes: List<SceneRule>,
)

/**
 * 算出「配置里那一格 + 草稿里那一块的动作那几格」—— [saveSceneBlock] 说的两道闸过不去就答 `null`。
 *
 * @param loaded 载入时那一格的样子，用来判断配置有没有在别处变过
 */
private fun blockWrite(
    base: List<SceneRule>?,
    slot: Int?,
    loaded: SceneRule?,
    block: SceneRule,
): BlockWrite? {
    if (slot == null || base == null) return null
    if (slot !in base.indices) return null
    val old = base[slot]
    if (old != loaded) return null
    //  这一页的东西：各档动作 + 松手那一族；`conditions` / `enabled` 归上一页，原样留着
    val written =
        old.copy(
            actions = block.actions,
            releaseOn = block.releaseOn,
            release = block.release,
        )
    return BlockWrite(written, base.toMutableList().also { it[slot] = written })
}
