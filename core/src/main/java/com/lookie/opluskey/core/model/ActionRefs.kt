package com.lookie.opluskey.core.model

// 「某条动作在配置里被哪些地方引用」这一件事 —— 目前只有一处要用：移除一条常用命令。
//
//  **为什么不写进 `Config.kt`**：那边已经过千行（见 `tools/ratchet.txt`），而这几支纯是
//  「逐档过一遍」的机械映射，塞回去只会让那个文件更难读。

/**
 * 把 [token] 这条动作从**这份配置里所有引用它的地方**清掉，换成「无操作」（[Action.None]）。
 *
 * ## 为什么移除常用命令要顺手清引用
 *
 *  常用命令只是**指向一条动作的指针**（`cmd:common.*`）。清单里删掉它之后那个 token 就再也念不出
 *  名字：绑着它的按键照样按那串字执行，而界面上那一格只会写一串 `cmd:common.wechat_scan`。
 *  用户按的是「移除」，期待的是「这一条不再存在」—— 留着比清掉更让人困惑。
 *
 * ## 为什么清成「无操作」而不是把那一格从绑定里抹掉
 *
 *  槽位是各自的：清空一档不影响同一颗键的其它档，也不需要判断「这颗键还剩不剩东西」——
 *  清完什么都不剩的键本来就等于没配。多击那张表里被清成「无操作」的那一格**从表里删掉**，
 *  与 `KeyConfig.withAction` 的写侧同一个口径（那边写成无操作时也是删格，不存一条 `none`）。
 *
 * ## 清哪些地方
 *
 *  单键的每一档（含两个松手槽）、多击表、场景块（含它自己的两个松手槽），以及**组合键**那一族
 *  —— 它们都能绑 `cmd:` 那一族的动作，漏掉一处就是「移除之后那颗键还在悄悄执行它」。
 *
 *  **只清这份配置里的绑定**：命令库里的命令正文不引用别的命令（见 `CommandLibrary` 的说明），
 *  这一层没有别的引用点。
 */
fun Config.withoutAction(token: String): Config =
    copy(
        keys = keys.mapValues { (_, key) -> key.withoutAction(token) },
        combos = combos.map { it.withoutAction(token) },
    )

/** 这一条若就是 [token]，换成「无操作」；其余原样。 */
private fun Action.without(token: String): Action = if (this.token == token) Action.None else this

/** 这颗键的每一档（清单见 [Config.withoutAction]）。 */
private fun KeyConfig.withoutAction(token: String): KeyConfig =
    copy(
        single = single.without(token),
        double = double.without(token),
        long = long.without(token),
        longRelease = longRelease.without(token),
        clickThenLong = clickThenLong.without(token),
        clickThenLongRelease = clickThenLongRelease.without(token),
        extraClicks =
            extraClicks
                .mapValues { (_, action) -> action.without(token) }
                .filterNot { (_, action) -> action is Action.None },
        scenes = scenes.map { it.withoutAction(token) },
    )

/** 一块场景：档位表 + 它自己的两个松手槽（`release` / `chainRelease`）。 */
private fun SceneRule.withoutAction(token: String): SceneRule =
    copy(
        actions = actions.mapValues { (_, action) -> action.without(token) },
        release = release.without(token),
        chainRelease = chainRelease.without(token),
    )

/** 一组组合键：只有短按 / 长按两档，外加松手槽与它自己的场景表。 */
private fun ComboKey.withoutAction(token: String): ComboKey =
    copy(
        single = single.without(token),
        long = long.without(token),
        longRelease = longRelease.without(token),
        scenes = scenes.map { it.withoutAction(token) },
    )
