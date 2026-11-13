package com.lookie.opluskey.core.model

/**
 * 配置里**每一处能放动作**的位置 —— 全项目唯一的遍历点。
 *
 * ## 为什么必须有这一处
 *
 *  「一条命令被改名 / 被删掉」时，配置里所有指向它的 `cmd:<名字>` 都得跟着挪或清。
 *  能放动作的地方分布在两种条目里：
 *
 *  · 每颗键的三档手势（`single` / `double` / `long`）与两格松手（`longRelease`）；
 *  · 每颗键「单击后长按」那一档（主档 `clickThenLong` 与它自己的松手 `clickThenLongRelease`）；
 *  · 每颗键的额外连击（`extraClicks`，第 3 击起，见 [KeyConfig.actionOf]）；
 *  · 每颗键的系统场景（`scenes[]` 的动作表 + 松手两格）；
 *  · 每个组合键的三档（`single` / `long` / `longRelease`，组合没有双击）；
 *  · 每个组合键的系统场景（同上）。
 *
 *  这几处**先后被漏过四次**（删命令只清三格 → 补上场景 → 组合键那两档又漏 → 额外连击与
 *  组合场景又漏），因为每个调用点都自己写一遍 `copy(single = …, double = …, long = …, scenes = …)`。
 *  只要遍历散在调用点上，「下次多一处动作位」就必然再漏一次 —— 所以收成这几个函数：
 *  **加新的动作位只改这个文件**，改名的路径、删命令的路径、删变量的反查都不必再动。
 *  「松手执行额外动作」那两格（`longRelease`）就是这样漏进来一次：加字段时这个文件没跟着改，
 *  改名之后那两格里留着指向旧名的 `cmd:`，删变量的反查也数不到它们。
 *
 * ## 遍历的是 `keys` 全表，不是 `allKeys`
 *
 *  `allKeys` = 内置键 + `customKeys` 清单；而 `keys` 里可能还留着**已从清单删掉**的自定义键
 *  条目（`customKeys` 与 `keys` 是两份数据）。残留条目本身不会被执行侧认领（`keysByCode`
 *  只按 `allKeys` 建表），但它上面的引用照样要清 —— 重新加回这颗键时就不会带出一条悬空的
 *  `cmd:`。只看 `allKeys` 会让这部分引用留在文件里。
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
                    longRelease = transform(key.longRelease),
                    //  「单击后长按」那一档的主档与松手各一格：少这两行，改名之后这两处留着
                    //  指向旧名的 `cmd:`，删命令时 `bindsToken` 也数不到它们（库删了、引用留着）。
                    clickThenLong = transform(key.clickThenLong),
                    clickThenLongRelease = transform(key.clickThenLongRelease),
                    extraClicks = key.extraClicks.mapValues { transform(it.value) },
                    scenes = key.scenes.map { it.mapActions(transform) },
                )
            },
        combos =
            combos.map { combo ->
                combo.copy(
                    single = transform(combo.single),
                    long = transform(combo.long),
                    longRelease = transform(combo.longRelease),
                    //  按住手势那两套（先按住第一颗 / 第二颗）同样是动作位 —— 少这四行，
                    //  改名 / 删命令之后那两套里留着指向旧名的 `cmd:`。
                    singleA = transform(combo.singleA),
                    longA = transform(combo.longA),
                    singleB = transform(combo.singleB),
                    longB = transform(combo.longB),
                    scenes = combo.scenes.map { it.mapActions(transform) },
                )
            },
    )

/**
 * 配置里**所有**动作的 token（顺序无关，可能有重复）—— 与 [mapActions] 同一份清单。
 *
 *  删除变量前的反查（`VariableRefs`）用它，免得那一边再抄一份「六处」的清单：
 *  抄一份的下场是「删命令认得组合规则、删变量不认得」，而两边都自称是唯一的遍历点。
 */
fun Config.actionTokens(): List<String> =
    buildList {
        keys.values.forEach { key ->
            add(key.single.token)
            add(key.double.token)
            add(key.long.token)
            add(key.longRelease.token)
            add(key.clickThenLong.token)
            add(key.clickThenLongRelease.token)
            key.extraClicks.values.forEach { add(it.token) }
            key.scenes.forEach { it.actionTokens().forEach(::add) }
        }
        combos.forEach { combo ->
            add(combo.single.token)
            add(combo.long.token)
            add(combo.longRelease.token)
            add(combo.singleA.token)
            add(combo.longA.token)
            add(combo.singleB.token)
            add(combo.longB.token)
            combo.scenes.forEach { it.actionTokens().forEach(::add) }
        }
    }

/**
 * 配置里**还有没有**任何一处绑着 [token]（`cmd:<名字>`）—— 与 [mapActions] 同一份清单。
 *
 *  用途：删命令时先清引用、再删库；库没删成要把引用写回去。判「有没有清过」与「要不要回滚」
 *  都得问这一个问题，两处若各写一遍，就会各自漏掉不同的动作位。
 */
fun Config.bindsToken(token: String): Boolean = actionTokens().any { it == token }

/**
 * 一块场景里的**全部动作位** —— 「换动作」与「列出动作」两条路共用，免得一处改了这里、另一处漏了那里。
 *
 *  档位是个**表**（`SceneRule.actions`）而不是三个固定字段：表里有什么就改什么，
 *  多一档 / 少一档都不用来改这段。**表之外**还有两格独立字段（松手 `release` 与
 *  「单击后长按」的松手 `chainRelease`）—— 它们不在表里，所以必须在这里各列一次：
 *  漏了 `chainRelease` 就等于「改名对那一格不生效、删命令时看不见它还引用着」。
 */
private fun SceneRule.mapActions(transform: (Action) -> Action): SceneRule =
    copy(
        actions = actions.mapValues { transform(it.value) },
        release = transform(release),
        chainRelease = transform(chainRelease),
    )

/** 一块场景里**写过**的那几格动作的 token（没配的档不算）。 */
private fun SceneRule.actionTokens(): List<String> = (actions.values + release + chainRelease).filter { it.isBound }.map { it.token }
