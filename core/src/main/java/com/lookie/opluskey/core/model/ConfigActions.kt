package com.lookie.opluskey.core.model

/**
 * 配置里**每一处能放动作**的位置 —— 全项目唯一的遍历点。
 *
 * ## 为什么必须有这一处
 *
 *  「一条命令被改名 / 被删掉」时，配置里所有指向它的 `cmd:<名字>` 都得跟着挪或清。
 *  能放动作的地方有**五处**，分布在两种条目里：
 *
 *  · 每颗键的三格手势（`single` / `double` / `long`）；
 *  · 每颗键的条件规则（`rules[].action`）；
 *  · 每个组合键的两档（`single` / `long`，组合没有双击）。
 *
 *  这五处**先后被漏过三次**（删命令只清三格 → 补上规则 → 组合键那两档又漏），因为
 *  每个调用点都自己写一遍 `copy(single = …, double = …, long = …, rules = …)`。
 *  只要遍历散在调用点上，「下次多一处动作位」就必然再漏一次 —— 所以收成这两个函数：
 *  **加新的动作位只改这个文件**，改名的路径与删命令的路径都不必再动。
 *
 * ## 遍历的是 `keys` 全表，不是 `allKeys`
 *
 *  `allKeys` = 内置键 + `customKeys` 清单；而 `keys` 里可能还留着**已从清单删掉**的自定义键
 *  条目（`customKeys` 与 `keys` 是两份数据）。那种残留条目照样会被执行侧认领（按 keyCode），
 *  所以它上面的引用也必须清 —— 只看 `allKeys` 会把它漏成一个「按下去 NOT_FOUND」的键。
 *
 * @param transform 逐个动作换一份；返回同一个实例表示这一处不动
 */
fun Config.mapActions(transform: (Action) -> Action): Config =
    copy(
        keys =
            keys.mapValues { (_, key) ->
                key.copy(
                    single = transform(key.single),
                    double = transform(key.double),
                    long = transform(key.long),
                    rules = key.rules.map { it.copy(action = transform(it.action)) },
                )
            },
        combos =
            combos.map { combo ->
                combo.copy(single = transform(combo.single), long = transform(combo.long))
            },
    )

/**
 * 配置里**还有没有**任何一处绑着 [token]（`cmd:<名字>`）—— 与 [mapActions] 同一份清单。
 *
 *  用途：删命令时先清引用、再删库；库没删成要把引用写回去。判「有没有清过」与「要不要回滚」
 *  都得问这一个问题，两处若各写一遍，就会各自漏掉不同的动作位。
 */
fun Config.bindsToken(token: String): Boolean {
    fun Action.hit() = this.token == token
    return keys.values.any { key ->
        key.single.hit() || key.double.hit() || key.long.hit() ||
            key.rules.any { it.action.hit() }
    } || combos.any { it.single.hit() || it.long.hit() }
}
