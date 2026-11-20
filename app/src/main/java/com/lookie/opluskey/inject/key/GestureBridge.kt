package com.lookie.opluskey.inject.key

import com.lookie.opluskey.core.model.ClickChainParams
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboWindows
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.inject.input.ChainSpec
import com.lookie.opluskey.inject.input.ComboBinding
import com.lookie.opluskey.inject.input.DetectorWindows
import com.lookie.opluskey.inject.input.Gesture
import com.lookie.opluskey.inject.input.KeyRef

// 判定层与业务层之间的**那一处翻译** —— 全项目只有这里同时认识两边的类型。
//
//  为什么收在一个文件里：`inject/input` 那一层（`GestureDetector` / `ComboDetector` /
//  `ChainSpec` / `ComboBinding`）刻意不认识 `core.model` 的任何业务类型，读的是「毫秒 / 布尔 /
//  次数」这样的值对象与「带名字的键引用」。业务那边则在 `Config` / `KeyConfig` / `ComboKey` 上
//  继续按需求演进。两边各走各的，交接只在下面这几个函数上。
//
//  **加字段时的规矩**：业务那边加一个判定要读的位，先问「这一位是不是『有没有』而不是『是什么』」
//  —— 是就翻成 `Boolean` 加进 `ChainSpec` / `ComboBinding`，不是就说明它不该进判定层。
//  动作的**内容**永远不进判定层（判定器从不看动作是什么）。

/**
 * `KeyId` → 判定层的键引用。
 *
 * ## 身份用 **id 字符串**，不是 `KeyId` 对象本身
 *
 *  `KeyRef.equals` 比的是 `key` 这一位，而组合那边的成员来自 `ComboKey.keys` ——
 *  那是 `List<String>`（[keyRefOfId] 造出来的）。两边必须是**同一个类型**才比得上：
 *  拿 `KeyId` 对象当身份的话，`KeyRef(KeyId.PLUS) != KeyRef("plus")`，于是
 *  `ComboDetector.holdReady` 里 `pool.firstOrNull { it == c.starter }` 永远返回 null ——
 *  **按住组合一次都判不出来**，首键与成员键各自走单键判定（真机现象：「按住手势无效，
 *  反而触发了首键和成员键各自的事件」）。等待组合那条路同样靠这个相等性，一起坏。
 *
 *  `name` 仍取显示名 —— 判定日志里看到的就是用户在界面上给这颗键起的名字。
 */
internal fun keyRefOf(id: KeyId): KeyRef = KeyRef(id.id, id.label)

/**
 * 键 id 字面量（`plus` / `ck1`）→ 判定层的键引用。
 *
 *  **组合那边走这一条，不走 [keyRefOf]**：`ComboKey.keys` 存的是 id 字符串而不是 `KeyId`
 *  （组合的成员表在配置里就是字符串，判定侧只要它能比、能念）。身份用字符串本身，`name` 也从
 * 配置里现查（查不到就退回字面量 —— 那颗键可能刚被删掉，但组合还没跟着更新）。
 *
 *  @param nameOf 按 id 查显示名；`null` = 沿用 id 字面量
 */
internal fun keyRefOfId(
    id: String,
    nameOf: (String) -> String? = { null },
): KeyRef = KeyRef(id, nameOf(id) ?: id)

/**
 * 判定层的键引用还原成 `KeyId`。
 *
 *  **两条路回传的都是 id 字符串**（单键那条由 [keyRefOf] 造、组合那条由 [keyRefOfId] 造），
 *  所以一律走「从配置里查回来」这一支：`Config.keyIdOf` 在 `allKeys` 里线性找一个 id，
 *  一次手势只问一次，代价可忽略。
 *
 *  `is KeyId` 那一支**留作防御**（现在没有调用方造这种引用）：真有人直接把 `KeyId` 塞进
 *  `KeyRef.key` 也不会在这里炸。
 *
 *  查不到就抛：拦截路径上按下的键一定在 `allKeys` 里（认领那一步就按它判的），查不到只能是
 *  配置在两次按下之间被改了 —— 属于不该发生的情形。按 AGENTS 的红线，注入侧宁可抛给上层的
 *  `catch (Throwable)`，也不能悄悄拿错键去执行。
 */
internal fun keyIdOf(
    ref: KeyRef,
    config: com.lookie.opluskey.core.model.Config,
): KeyId =
    when (val k = ref.key) {
        is KeyId -> k
        is String -> config.keyIdOf(k) ?: error("判定层回传的键 id 在配置里查不到：$k")
        else -> error("判定层回传的键引用认不出：$k")
    }

/**
 * 一串判定层的键引用 → [`KeyId`] —— 只给**装配侧的查询**用（`combosFor` / `widerComboOf` 那些
 * 原来就收 `KeyId` 的配置查询，用的是同一套翻译，本层不去动它们的签名）。
 */
internal fun keyIdsOf(
    refs: Collection<KeyRef>,
    config: com.lookie.opluskey.core.model.Config,
): List<KeyId> = refs.map { keyIdOf(it, config) }

/**
 * 连击参数 → 判定层的 [ChainSpec]。
 *
 *  **只搬「判定读得懂的东西」**：[ClickChainParams] 上的 `clickThenLongRelease` /
 *  `longRelease` 是 `Action`（动作内容），判定层一概不接；它们替换成两个存在位 ——
 *  抬手那一步「有没有事可做」（`clickThenLongReleaseActionExists` / `releaseBound`）。
 *
 *  @param params 单键配置或成员连击那一份视图
 */
internal fun chainSpecOf(params: ClickChainParams): ChainSpec =
    ChainSpec(
        clickWindowMs = params.clickWindowMs.toLong(),
        fixedWindowStart = params.fixedWindowStart,
        judgeCap = params.judgeCap,
        clickTiers = params.clickTiers,
        doubleClickOn = params.doubleClickOn,
        clickThenLongOn = params.clickThenLongOn,
        thenLongClicks =
            buildSet {
                if (params.clickThenLongOn) add(2)
                if (params.doubleThenLongOn) add(3)
                if (params.tripleThenLongOn) add(4)
            },
        thenLong =
            buildMap {
                //  detekt 的 `ForEachOnRange`：这一步在装配期只跑一次，但同一页里没有理由
                //  留一条会被静态检查点名的写法 —— 用普通 for。
                for (n in 2..4) {
                    params.thenLongAt(n)?.let { put(n, it) }
                }
            },
        clickThenLongPressMs = params.clickThenLongPressMs.toLong(),
        clickThenLongFireOnRelease = params.clickThenLongFireOnRelease,
        clickThenLongRepeatIntervalMs = params.clickThenLongRepeatIntervalMs.toLong(),
        clickThenLongRepeats = params.clickThenLongRepeats,
        //  「单击后长按」之后，抬手那一下**有没有人接** —— 与业务侧
        //  `KeyConfig.clickThenLongReleaseAction` 是同一套四位判据，两位挪到了这里：
        //   · 场景块给这一档配的松手（注入侧看不到场景表，宿主算好带过来）；
        //   · 按键自己那一格：松手开关开着、挑过动作，**且这一档本身有事可做**（松手是这一档
        //     执行之后的收尾，主语没有就不该有谓语 —— 用户口径「设为无操作时禁用松手后额外触发」）。
        //
        //  **后一半（主语守卫）不能丢**：`clickThenLongReleaseOn` 是存位，值改成「无操作」时它仍留着，
        //  只按 `releaseOn && isBound` 判会让「主档无操作、松手那格还留着动作」的配置在抬手补一个孤立动作。
        //  **场景那一半也不能丢**：只在场景块里配了松手的配置，抬手那一档会整条不派发。
        clickThenLongReleaseActionExists =
            params.sceneChainRelease ||
                (params.clickThenLongReleaseOn && params.clickThenLongRelease.isBound && params.clickThenLongActionExists),
        longPressMs = params.longPressMs.toLong(),
        longFireOnRelease = params.longFireOnRelease,
        //  `releaseBound` 已经把「长按有动作 / 开关开着 / 挑过动作 / 不是松手才触发」
        //  四条算在一起，判定层只要这一位。
        longReleaseActionExists = params.releaseBound,
        longRepeats = params.longRepeats,
        longRepeatIntervalMs = params.longRepeatIntervalMs.toLong(),
    )

/** 单键配置 → [ChainSpec]（单键那条链就是它）。 */
internal fun chainSpecOf(config: KeyConfig): ChainSpec = chainSpecOf(config as ClickChainParams)

/**
 * 组合档位 → 判定层的 [ComboBinding]。
 *
 *  **`isWait` / `ordered` 由触发方式派生**：`ComboKey.mode` 是这一档的身份（存盘 / 广播都按它），
 *  判定层只按「等窗口 / 先按住」与「要不要求按顺序」这两种形态分。派生只在这里做一次。
 *
 *  @param memberChain 这一档的成员连击参数（`null` = 不支持）
 *  @param longReleaseActionExists 这一档长按之后松手有没有事可做
 *  @return 判定层那一份；调用方把它当不透明句柄回传，执行时用 [comboKeyOf] 翻回来
 */
internal fun comboBindingOf(
    combo: ComboKey,
    memberChain: ChainSpec?,
    longReleaseActionExists: Boolean,
    nameOf: (String) -> String? = { null },
): ComboBinding =
    ComboBinding(
        key = combo,
        name = combo.id,
        isWait = combo.isWait,
        ordered = combo.ordered,
        members = combo.keys.map { keyRefOfId(it, nameOf) },
        isHold = combo.isHold,
        hasLong = combo.hasLong,
        //  **不是 `hasLong`**：那一格存在不等于里面配了东西。成员连击让位给组合长按时看的是
        //  「组合长按真会执行」—— 只看结构位会让「组合长按没绑动作、成员连击绑了」的配置两边都空
        //  （让位的成员连击不再派发，组合长按派发出去又没人接）。
        longActionExists = combo.longActionExists,
        longPressMs = combo.longPressMs.toLong(),
        longFireOnRelease = combo.longFireOnRelease,
        longReleaseActionExists = longReleaseActionExists,
        longRepeats = combo.longRepeat,
        longRepeatIntervalMs = combo.longRepeatIntervalMs.toLong(),
        starterKeepLong = combo.starterKeepLong,
        useOwnSettings = combo.useOwnSettings,
        memberChain = memberChain,
    )

/** [comboBindingOf] 的反向：判定层回传的那一档还原成业务模型。 */
internal fun comboKeyOf(binding: ComboBinding): ComboKey =
    binding.key as? ComboKey
        ?: error("判定层回传的组合不是 comboBindingOf 造的：${binding.key}")

/**
 * 配置的四格窗口 → 判定层的 [DetectorWindows]。
 *
 *  两型字段一一对应（`wait` / `waitPair` / `hold` / `holdFollower`），只是把 `Int` 铺成 `Long`
 *  —— 判定层内部一律用毫秒的 `Long`，免得在每条判据上各写一次 `.toLong()`。
 */
internal fun detectorWindowsOf(w: ComboWindows): DetectorWindows =
    DetectorWindows(
        wait = w.wait.toLong(),
        waitPair = w.waitPair.toLong(),
        hold = w.hold.toLong(),
        holdFollower = w.holdFollower.toLong(),
    )

/**
 * 判定层判出的形态 → 业务的手势档位。
 *
 *  一一对应（[Gesture] 的注释里写着「不多也不该多」）。`Click(count)` 落到 `Click(1)`~`Click(N)`：
 *  判定器封顶过（`ChainSpec.judgeCap`），这里不会越界。
 */
internal fun triggerOf(gesture: Gesture): GestureTrigger =
    when (gesture) {
        is Gesture.Click -> GestureTrigger.Click(gesture.count)
        Gesture.LongPress -> GestureTrigger.LongPress
        Gesture.Release -> GestureTrigger.Release
        is Gesture.ClickThenLong -> GestureTrigger.ClickThenLong(gesture.count)
        is Gesture.ClickThenLongRelease -> GestureTrigger.ClickThenLongRelease(gesture.count)
    }

/**
 * [triggerOf] 的反向 —— 业务手势档位 → 判定层的形态。
 *
 *  只有一处用得到：ROM 合成的那一对事件（`SideKeyInterceptor.runRomGesture`）手上拿到的是
 *  宿主给的 `GestureTrigger`，而判定器的入口只要判定层的 [Gesture]。
 */
internal fun gestureOf(trigger: GestureTrigger): Gesture =
    when (trigger) {
        is GestureTrigger.Click -> Gesture.Click(trigger.count)
        GestureTrigger.LongPress -> Gesture.LongPress
        GestureTrigger.Release -> Gesture.Release
        is GestureTrigger.ClickThenLong -> Gesture.ClickThenLong(trigger.count)
        is GestureTrigger.ClickThenLongRelease -> Gesture.ClickThenLongRelease(trigger.count)
    }
