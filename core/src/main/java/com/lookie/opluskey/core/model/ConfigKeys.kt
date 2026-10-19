package com.lookie.opluskey.core.model

// 「按键清单」的增删改 —— 与 `Config` 那批**只读取值**的方法分开放。
//
//  为什么不写在 `Config` 类里：那几个方法让类的方法数撞上 detekt 的 `TooManyFunctions`
//  （上限 11，而 `Config` 已经 8 个）。拆成扩展函数既守住闸门，也把「怎么改一份配置」
//  与「怎么读一份配置」分成两屏看。

/**
 * 下一个可用的自定义键 id —— 序号**只增不减**（删掉中间那颗再收一颗，不能撞上还活着的 id）。
 */
fun Config.nextCustomKeyId(): String {
    val used = customKeys.mapNotNull { it.id.removePrefix(KeyId.CUSTOM_ID_PREFIX).toIntOrNull() }
    return KeyId.customId((used.maxOrNull() ?: 0) + 1)
}

/**
 * 收下一颗自定义键 —— 同时建好它的绑定，并**直接打开接管**。
 *
 *  两处一起写是必须的：`customKeys` 是「有哪些键」，`keys` 是「每颗键的绑定」。
 *  只写前者会让 `key(id)` 退回默认值而界面看着像「刚收的键绑定丢了」；
 *  只写后者则这颗键根本不会被广播出去（`allKeys` 不含它）⇒ 注入侧永远认领不到。
 *
 * ## 为什么 `enabled` 直接给 `true`
 *
 *  `KeyConfig.defaultFor` 是 `enabled = false`（"装上模块不改变行为" 是侧键那一档的默认值，
 *  见它的注释）。自定义键不能照搬：那颗键**没有独立的总开关**，页面闸门就是它自己的
 *  `enabled` —— 默认关着的话，用户刚收完键，绑定页是整片灰的、一个动作都选不了，
 *  而他又没有别的地方能把它打开（死锁）。
 *
 *  「按下想添加的按键」这个动作本身就是用户的明确意愿，收集即接管。
 *  三档绑定此时都是 `Action.None`（按下什么都不做），与侧键「开着但还没配」同一个表现。
 */
fun Config.withCustomKey(key: KeyId): Config =
    copy(
        keys = keys + (key to KeyConfig.defaultFor(key).copy(enabled = true)),
        // 先摘掉同 id 的旧条目再追加：`customKeys + key` 会留下两条同 id 的，
        //  之后「删一颗」只删掉一条、另一条还活着（清单与 keys 就此对不上）。
        customKeys = customKeys.filterNot { it.id == key.id } + key,
    )

/** 删掉一颗键（只对自定义键有意义）；两处一起摘，理由同 [withCustomKey]。 */
fun Config.withoutKey(id: String): Config {
    val target = keyIdOf(id) ?: return this
    // 内置键**不给删**：删掉 `plus` 等于把侧键整条从 keys 里抹掉，读回来退回默认配置，
    //  而界面上只是「删了一颗键」——静默丢配置。删键只对收集来的键有意义。
    if (!target.isCustom) return this
    return copy(
        keys = keys - target,
        customKeys = customKeys.filterNot { it.id == target.id },
    )
}

/**
 * 给某一颗自定义键改名。
 *
 *  **身份仍是 id**，这里只换显示名 —— 所以绑定不会丢（`KeyId` 按 id 判等）。
 *  但 `keys` 里那一条也得换成新对象：`KeyConfig.id` 是**属性**，留在旧对象上会让
 *  「从配置取名字」的调用点（`pick` 里的 `key.id.label`、日志里的键名）一直显示旧名。
 *
 *  内置键不允许改名（`customKeys` 里找不到就原样返回）。
 */
fun Config.withKeyName(
    id: String,
    name: String,
): Config {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return this
    val index = customKeys.indexOfFirst { it.id == id }
    if (index < 0) return this
    val old = customKeys[index]
    if (old.label == trimmed) return this
    val renamed = old.copyName(trimmed)
    val renamedKeys =
        keys.entries.associate { (k, v) ->
            if (k.id == id) renamed to v.copy(id = renamed) else k to v
        }
    return copy(
        keys = renamedKeys,
        customKeys = customKeys.toMutableList().also { it[index] = renamed },
    )
}
