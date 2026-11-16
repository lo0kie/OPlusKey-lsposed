package com.lookie.opluskey.core.model

/**
 * 把模块自带的预设命令补进命令库 —— **纯规则**（只吃两份列表，不碰磁盘）。
 *
 *  预设只补一次：补完之后它们就是普通自定义命令，改名 / 改正文 / 删除都走命令页那一套，
 *  不再有任何「模块内置」的查询分支。所以这里只在命令库加载时问一句「缺不缺」。
 *
 * ## 去重与上限
 *
 *  · **正文相同就跳过**：用户可能已经手写过同一条命令，再补一份只是重复；
 *  · **同名但正文不同也跳过**：不覆盖用户自己的命令（名字是命令库的主键）；
 *  · 单条命令已到 [Command.MAX_COMMANDS] 时停止补，剩下的留给用户自己处理。
 *
 * @param existing 命令库里现有的命令（保持原顺序）
 * @param seeds 要补的预设（只认单条；空名 / 空正文的条目跳过）
 * @return 合并后的命令表；没有可补的时候就返回 [existing] 本身
 */
fun mergeCommandSeeds(
    existing: List<Command>,
    seeds: List<Command>,
): List<Command> {
    if (seeds.isEmpty()) return existing
    val out = existing.toMutableList()
    val bodies = existing.filter { it.kind == CommandKind.SINGLE }.mapTo(HashSet()) { it.single.trim() }
    val names = existing.mapTo(HashSet()) { it.name }
    var singles = existing.count { it.kind == CommandKind.SINGLE }
    for (seed in seeds) {
        if (seed.kind != CommandKind.SINGLE) continue
        if (seed.name.isBlank() || seed.single.isBlank()) continue
        val body = seed.single.trim()
        if (body in bodies || seed.name in names) continue
        if (singles >= Command.MAX_COMMANDS) break
        out += seed
        bodies += body
        names += seed.name
        singles++
    }
    return out
}
