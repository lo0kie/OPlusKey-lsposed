package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.Variable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

/**
 * 配置文件的解析与序列化：纯函数，不碰文件 IO。格式为 JSON。
 *
 * 本层只管**正文那份 JSON**：磁盘上正文之外还套了一层外皮（[FileObfuscation]，`ConfigStore` 负责剥/包）。
 *
 * ## 用 kotlinx.serialization 而不是 `org.json`
 *
 *  `org.json` 有两个把人坑过的行为：`optString` 遇到 `null` 字面量会返回字符串 `"null"`
 *  （于是 `Action.parse("null")` 兜底成 `Shell("null")`，按那个手势会真去跑 `sh -c null`）；
 *  `optBoolean` 在键缺失时返回 `false`，而不是「没写」这个语义。两者都让「缺字段」与
 *  「明确写了 false / null」混在一起，只能靠手写清洗兜住。
 *
 *  kotlinx 直接绑到 [ConfigDto] 上的类型与默认值：缺字段就是缺字段（走 `@Serializable` 的默认值），
 *  类型不对直接抛异常 ⇒ 判「读不懂」，不会静默变成某个合法但错误的值。
 *
 * ## 完整性字段 `md5` 与版本
 *
 *  · **`version == VERSION`**：`md5` 是「把 `md5` 这一格置空之后、**按 [ConfigDto] 重新编码**得到
 *    的那份文本」的 MD5（不是原文逐字节），对不上判读不懂；
 *  · **`version < VERSION`**：**不校验 `md5`**，只要求「能被解析 + 键段完整」。
 *
 *  为什么老版本一律不校验：`md5` 的口径是「按**当前** [ConfigDto] 重新编码」，而 DTO 每加一个字段
 *  （`encodeDefaults = true` 会把它写进正文），老文件重编码出来的字节就必然与它自己那份 `md5`
 *  不同 —— 校验只会把**每一份老配置都判成读不懂**，随后 `ConfigStore.read` 退回默认值、下一次
 *  写盘把用户的绑定与开关整份抹掉。真机上就发生过这一回：「每颗键一份单击延迟」把字段从
 *  `global` 搬到 `KeyDto` 之后，所有 v2 文件当场全被判成读不懂、页面顶着「配置读取失败」。
 *
 *  所以**往 [ConfigDto] 加 / 挪字段必须同时抬 [VERSION]**（见下），老文件读进来之后下一次写盘
 *  自然升级成新版本并带上新 md5。
 */
object ConfigCodec {
    /**
     * 当前格式版本。
     *
     *  1 = `org.json` 那版；2 = kotlinx + 新的 md5 口径；
     *  3 = `KeyDto` 多了 `doubleClickMs` / `instantSingle`（从 `global` 段搬下来，每颗键一份）；
     *  4 = `GlobalDto` 多了 `enabled`（模块总闸从 `keys.plus.enabled` 里拆出来）；
     *  5 = `KeyDto` 多了 `longRepeatVibrate`（连续触发时是否每次重复都振动）；
     *  6 = `GlobalDto` 多了 `vibrateStrength`（振动强度三档）；
     *  7 = `KeyDto` 多了 `rules`（按状态分支的动作），顶层多了 `combos`（组合按键）；
     *  8 = `GlobalDto` 多了 `palette`（页面配色）；
     *  9 = `GlobalDto` 多了 `theme`（界面颜色：跟随系统 / 浅色 / 深色）；
     *  10 = `GlobalDto` 多了 `followDisplaySize` / `pageScale`（跟随系统显示大小 / 页面缩放）；
     *  11 / 12 = （见代码历史，本次未改动）；
     *  13 = 当前上一版；
     *  14 = `ComboDto` 多了 `rules`（组合键的条件执行）；
     *  18 = `StateRuleDto` 多了 `enabled`（场景可以停用；15~17 见代码历史）。
     *  19 = （见代码历史，本次未改动）；
     *  20 = 顶层多了 `profile_id`（多配置的身份从「名字空 + 位次」换成显式 id）。
     *  21 = `KeyDto` 多了 `extra_clicks`（第 3 击起的额外档位；界面没有入口，只对手改 / 导入生效）。
     *  22 = `KeyDto` 多了 `long_release`（长按之后松手要执行的**另一个**动作；组合那一档没有这一项）。
     *  23 / 24 = `long_release` 收成动作 token（兼容早先写成布尔的那批），并拆出开关 `long_release_on`。
     *  25 = `KeyDto` 多了 `long_fire_on_release`（长按「松手才触发」，与连发 / 松手再执行互斥）。
     *  26 = `GlobalDto` 多了 `toast_template`（执行时提示的文案模板，`$key` / `$gesture` / `$action`）。
     *  27 = `ComboDto` 多了 `long_fire_on_release` / `long_release_on` / `long_release`
     *       （组合键的松手两档：长按松手才触发、松手执行额外动作）；
     *  28 = `GlobalDto` 多了 `same_event`（三档同一事件：三档绑同一个动作的键按下即执行）。
     *       28 这个号在真机上已经以**另一种形状**写过盘（`same_event` 当时还在 `KeyDto` / 键段里），
     *       两种 v28 互不兼容 ⇒ 挪段必须再抬一号，让旧形状走「老文件不校验 md5」那一支；
     *  29 = `same_event` 从 `KeyDto`（键段）挪到 `GlobalDto`（全局段，`same_event` 不再逐键）。
     *  30 = `same_event` 从 `GlobalDto` **挪回** `KeyDto`（每颗键自己一份，按键级优先于公共设置）。
     *       29 那版 `GlobalDto.same_event` 仍留在 DTO 里**只当迁移源**读（`ConfigDto.toConfig` 拿它
     *       灌给每一颗键），但不再写出 —— 所以两种形状的正文必然不同，必须抬号让 29 的文件走
     *       「老文件不校验 md5」那一支。
     *  31 = `KeyDto` / `ComboDto` 各多了 **「按键单独设置」那五项**（`use_own_settings` 与
     *       接口 / 振动 / 振动强度 / 提示 / 提示文案）—— 单键与组合共用 `OwnSettings` 契约。
     *       又一个新字段批次 ⇒ 同一条规矩：不抬号，每一份已有配置都会被判成读不懂。
     *  32 = `ComboDto` 多了 `passthrough`（这一组自己的「无操作时执行按键命令」，可空 =
     *       跟随公共设置）。组合专属的一项，不进 `use_own_settings` 那一族。
     *  33 = **删掉**「三档同一事件」（`KeyDto.same_event` 与 `GlobalDto.same_event`）——
     *       功能整个移除，两个字段一并消失。老文件里残留的那两个键按未知键忽略
     *       （`version < VERSION` 走「不校验 md5」那一支，`ignoreUnknownKeys` 把它们丢掉）。
     *  34 = `KeyDto` / `ComboDto` 各多了 `own_settings_seeded`（独立设置那五项被公共值播过种没有）。
     *       又一个「加字段忘了抬号」的现场：加完没抬 33 → 那份 11824 字节的 v33 配置当场被判读不懂，
     *       页面顶着「配置读取失败」、卡片报异常 —— 而文件本身好端端的（魔数对、正文完好）。
     *       症状与 v3、`extra_clicks` 那两次**一模一样**，这次记在这里当第三次例子。
     *  35 = `ComboDto` 多了 `window_ms`（这一组自己的「按键之间的等待」，0 = 跟随公共）——
     *       第四次现场：加完字段忘了抬号，存量 v34 配置当场被判读不懂、页面顶着「配置读取失败」。
     *  36 = **系统场景翻转**：`KeyDto` / `ComboDto` 各多了 `scenes`（一块 = 一组条件 + 三档动作），
     *       老的 `rules` 降级为**只读迁移列**（新写的文件不再写它）。读到老文件时
     *       `ConfigDto.mergeLegacy` 把「条件完全相同的那几条」并成一块，行为逐字不变。
     *  37 = `SceneRuleDto` 多了 `release_on` / `release`（场景块自己的「松手执行额外动作」）。
     *       松手那一格原先只能挂在按键身上，而块一命中长按它就整条失效（判定侧不发 `Release`）——
     *       收进块里之后它归草稿管、也真的会执行。默认关 + `none`，老文件读进来行为逐字不变。
     *  38 = **场景块的动作从三格改成一张表**：`SceneRuleDto` 多了 `actions`（档位 id → 动作 token），
     *       老的 `single` / `double` / `long` 降级为**只读迁移列**（新写的文件不再写它们）。
     *       读到老文件时那三格被并进表里，行为逐字不变；改成表是为了让第 3 击起的档位
     *       （三击 / 四击）也能被场景接管，而不是每加一档就动一次 DTO。
     *  39 = `KeyDto` 多了 `extra_clicks_on`（**多击档位启用没有**）——多击从此是「自己要才有」的
     *       档位：关着时那几页不出现、判定侧也不数到那儿。老文件没有这一格 ⇒ 按「表里有没有东西」
     *       倒推（见 `KeyDto.clicksOn`），否则手改过 `extra_clicks` 的人在升级那一刻
     *       会发现三击悄悄不灵了。
     *  40 = 同一格**从布尔收成击数数组**（`[3]` / `[3,4]`）：三击与四击的代价不一样，不能再用一位
     *       统领（只要三击的人不该被迫接受四击那个窗）。读侧同时认 39 的布尔（`true` = 表里那几档
     *       全算）与「缺格」（同上倒推），所以 39 的文件读进来行为逐字不变。
     *  41 = `KeyDto` 多了 `click_then_long_on` / `click_then_long`（**单击后长按**这一档）——
     *       它不延长连击链，只决定「第 2 击按住」要不要另判，所以与多击分两位。老文件缺这两格 ⇒
     *       默认关 + `none`，行为逐字不变。
     *  43 = `SceneRuleDto` 多了 `chain_release_on` / `chain_release`（**场景块里「单击后长按」的松手**）——
     *       与长按的 `release_on` / `release` 同一对，只挂在另一档上。老文件缺这两格 ⇒ 默认关 + `none`。
     *  42 = 同一档多了它**自己那几项参数**（`click_then_long_press_ms` / `_repeat` /
     *       `_repeat_interval_ms` / `_repeat_vibrate` / `_release_on` / `_release`）——
     *       与长按那一族同名不同槽，调这一档不会动普通长按。老文件缺这几格 ⇒ 走默认值。
     *
     *  44 = `SceneRuleDto` 多了 `release_pin`（**松手沿用按下那一刻命中的这一块**，默认开）——
     *       松手是一条独立手势，原先宿主在松手时重新判条件，长按动作换了前台就会落到别处。
     *  43 见下方历史。
     *
     *  45 = `GlobalDto` 多了 `show_common_commands`（按键绑定页要不要露出「常用命令」那一段）——
     *       纯显示项，老文件缺这一格 ⇒ 默认显示。
     *
     *  46 = `GlobalDto` 多了 `common_commands`（「常用命令」那张**用户自己的清单**）——
     *       老文件缺这一格是 `null`（= 用模块自带那几条），与「进过那一页又全删了」的空表分得开。
     *
     *  47 = `KeyDto` 多了 `combo_only` / `wait_multi_window`（**只参与非首键的按住组合** / **逐次等满多击窗口**）——
     *       两位都默认「关」，老文件缺它们与这一位出现之前完全一致。
     *
     *  48 = `KeyDto` 多了 `multi_wait_ms`（**逐次等满那一档自己的窗口**）——
     *       老文件缺这一格是 `null`，按 0 读；0 = 跟随「单击延迟」，
     *       所以升级之后「开着等满」的键手感一字不差（见 `KeyConfig.multiWaitMs`）。
     *
     *  49 = `KeyDto` 多了 `double_click_on`（**双击这一档开着没有**）——
     *       老文件缺这一格是 `null`，按「开」读（与这一位出现之前一致，不需要迁移）。
     *
     *  50 = **删了** `KeyDto.instant_single` / `GlobalDto.instant_single`（**忽视无操作双击手势**）——
     *       这一位管的是「双击没绑动作时不等窗口」，而「启用双击」关掉之后判定本来就只数到一击、
     *       抬手即判，两件事重合。老文件里那一格按未知键丢掉（`ignoreUnknownKeys`）。
     *       **删字段同样是改 DTO**：重编码少了那一格，md5 与旧文件对不上，所以必须抬这一位。
     *
     *  51 / 52 = 按住手势开发中途的两版草稿，**都没有发布过**，编号直接跳过去。
     *       任何 `< VERSION` 的文件都走「不校 md5」那一支（见 [parseOrNull]），跳号没有副作用，
     *       也不必为它们留迁移。
     *
     *  53 = 按住手势定型：`holdOn` + 两套动作（`singleA` / `longA` / `singleB` / `longB`）。
     *       老文件缺它们 ⇒ 关 / 无动作，判定与这一位出现之前逐字一致。
     *
     *  54 = 组合的两条触发方式各自成一位：`GlobalDto` 多了 `combo_window_on` / `combo_hold_on`，
     *       `ComboDto` 多了同名的**每组覆盖**（`null` = 跟随公共），`holdOn` 也从布尔变成本类型。
     *       老文件缺它们 ⇒ 窗口照旧认、按住不认，行为逐字不变。
     *
     *  55 = `ComboDto` 多了 `hold_orders`（**手动加过的按住顺序**）。老文件读成空表 ⇒ 按住那条
     *       一条顺序都没有 —— 按住手势本来就是这一轮才做的，老配置里不可能有它。
     *
     *  56 = 执行时提示多了「走流体云」这一档：`GlobalDto` 多了 `toast_fluid`，
     *       `KeyDto` / `ComboDto` 多了同名的**每组覆盖**（`null` = 跟随公共）。老文件缺它们
     *       ⇒ 默认关（仍走 Toast），行为逐字不变。
     *
     *  57 = 按住手势多了「首键要先按住多久」：`GlobalDto` 多了 `combo_hold_min_ms`，
     *       `ComboDto` 多了 `hold_min_ms`（0 = 跟随公共）。老文件缺它们 ⇒ 跟随公共（100ms），
     *       而按住手势本来就是这一轮才做的，老配置里不可能有它。
     *
     *  58 = **触发方式成了组合的身份**：`ComboDto` 多了 `mode`（0 等窗口 / 1 先按住第一颗 /
     *       2 先按住第二颗），`GlobalDto` 的 `combo_window_on` / `combo_hold_on` 与 `ComboDto`
     *       的 `windowOn` / `holdOn` / `holdOrders` / 那四套 A·B 动作槽一起退场。
     *       老文件（没有 `mode`）由 `ComboDto.toCombo` 的 `legacyMode` 推一次：认窗口就当窗口那条，
     *       只认按住就取加过的那一种（两种都加过 ⇒ 取第一颗那套，另一套丢弃）。
     *
     *  60 = **按住组合的场景固定在首键按下**：`ComboDto` 多了 `sceneOnStarter`，`GlobalConfigDto`
     *       多了 `comboSceneOnStarter`（公共那份）。同一轮里 `SceneRuleDto` 的 `release_pin` 退场
     *       —— 那是「松手沿用按下时命中的那一块」，现在松手一律按当时条件重判（用户口径）。
     *       老文件缺这两个新位 ⇒ 走出厂值（开）；多出来的 `release_pin` 由 `ignoreUnknownKeys` 丢掉。
     *
     *  59 = **常用命令能换图标**：`CommonCommandDto` 多了 `icon`（`FluidIcons` 的 id，空串 = 用它
     *       本来那颗）。`show_in_binding` 在这一版之后**回来了**：它重新参与判断（每条自己决定
     *       要不要出现在按键绑定页那一段），读回来是什么就写回什么 —— 字段没动过，所以不必再抬
     *       版本；v59 那一版写下的 `true` 正好等于「全部显示」，与本版默认观感一致。
     *       老文件缺 `icon` ⇒ 空串，观感逐字不变。
     *
     *  61 = **删掉「无操作时执行按键命令」**（`GlobalDto.combo_passthrough` 与 `ComboDto.passthrough`）——
     *       组合某一格是「无操作」时不再让两颗键各跑一遍：留空就是什么都不做。
     *       老文件里残留的那两格按未知键丢掉（`version < VERSION` 走「不校验 md5」那一支）。
     *       删字段同样是改 DTO，必须抬这一位。
     *
     *  62 = **按住组合改成「首键一颗 + 后续成员任意多颗」**：`ComboDto.mode` 的编号 1（先按住第一颗）
     *       与 2（先按住第二颗）归一成 1（按住），「先按住哪一颗」改由 `keys` 的第一位表达。
     *       字段名与类型都没变，所以**这一位抬的是语义**：读到 2 时由 `ComboDto.toCombo` 把第二颗
     *       提到首位。老文件（v61 及以前）里 2 的那几档因此会被就地折成「键序反转 + 1」，
     *       写回时不再出现 2。
     *
     *  63 = **「提示走流体云」成了 ColorOS 接口模式下的默认落点**（用户口径「使用 ColorOS 接口时
     *       应该弹流体云」）。字段一个没动，抬的是**默认值与语义**：v62 及以前存下来的
     *       `toast_fluid: false` 是那版的旧默认、不是用户的选择，由 [ConfigDto.toConfig] 就地折成开
     *       （见 [VERSION_FLUID_DEFAULT]）；这一版之后关掉的那一位才算数。
     *
     *  **这个数只往上加**：一旦某个编号随一次提交发布出去，它对应的那组字段就定死了。
     *
     *  64 = `GlobalDto` 多了 `global_wait_multi_window` / `global_multi_wait_ms`（公共设置里的
     *       「判定时机」与「等待窗口」）；`double_click_ms` 从「只读的迁移字段」变回**活字段**
     *       （公共设置里的单击延迟）。三项都是 `null` 缺省，老文件读回来按默认 ——
     *       与这一位出现之前完全一致。
     *
     *  65 = `GlobalDto` 多了 `global_ui_vibrate`（**界面振动反馈**）——
     *       `null` 缺省按开读，与这一位出现之前一致（界面那一路一直有触觉）。
     *
     *  68 = **这一版没有字段变化**：抬版本只为让已有文件走「`version < VERSION` 不校 md5」那一支 ——
     *       真机上有一批文件的 md5 对不上（页面只读、注入侧不接管），抬过去之后它们能被重新读进来，
     *       下一次写盘按当前 DTO 重算。
     *
     *  69 = `ComboDto` 多了 `ordered` / `fixed_window_start`（等待组合的「按顺序触发」与「固定窗口
     *       起点」）。两项都有 `null` / `false` 缺省，老文件读回来按「只看集合 + 逐次等满」——
     *       与这一位出现之前完全一致，不需要迁移。
     *
     *  72 = `GlobalDto` 多了 `global_detailed_key_settings`（**显示更详细的按键设置**）——
     *       `null` 缺省按关读，与这一位出现之前一致（那一页只画常用项）。
     *
     *  73 = **「按键之间的等待」删掉、两个窗口长度合成一个「等待窗口」**：`combo_window_ms` /
     *       `window_ms` 现在就是界面上那一行「等待窗口」，老文件里那份「固定窗口长度」
     *       （`combo_fixed_window_ms` / `fixed_window_ms`）**优先搬过来**（见
     *       `GlobalDto.legacyWindowMs` 与 `ComboDto.toCombo`）；「固定窗口起点」两位原样保留。
     *       删字段必须抬这一位，否则老文件的 md5 会按新 DTO 重算、当场判「读不懂」。
     *
     *  74 = **窗口拆成四格**（等待 / 按住 × 固定窗口起点 开 / 关，公共与每组各四份）：
     *       新增 `combo_wait_window_ms` / `combo_wait_pair_ms` / `combo_hold_window_ms` /
     *       `combo_hold_follower_ms` 与 `ComboDto` 的同名四位。老文件那**一个**数（v73 的
     *       `combo_window_ms` / `window_ms`）铺到前三格，第四格用出厂值（`DEFAULT_COMBO_PAIR_MS`）。
     *
     *  75 = **两个「固定窗口起点」拆成两个变量**：`GlobalDto.combo_fixed_window_start`（等待组合）
     *       与新增的 `combo_hold_fixed_window_start`（按住组合），`ComboDto` 同理多一位
     *       `hold_fixed_window_start`。老文件只有前者 ⇒ 按住那一份按默认（关，逐次等满）读，
     *       与这一位出现之前行为一致。
     *
     *  76 = `CommandDto` 多了 `output`（「显示执行返回结果」四档：不显示 / 提示 / 通知 / 流体云）。
     *       缺键 ⇒ 空串 ⇒ 读成「不显示」，与这一位出现之前一致，不需要迁移。
     *
     *  77 = `CommandDto` 多了 `outputIcon`（「提示类型 = 流体云」时那颗胶囊图标）。
     *       缺键 ⇒ 空串 ⇒ 胶囊不带图标，与这一位出现之前一致。
     *
     *  78 = `ComboDto` 多了**成员连击**那一串（`member_double` / `member_clicks` /
     *       `member_click_then_long*`）。缺键 ⇒ 全关着 / 无操作，与这一位出现之前一致。
     *
     *  79 = `KeyDto` 多了 `click_then_long_fire_on_release`（「单击后长按」的松手时触发）。
     *       缺键 ⇒ false ⇒ 到点即执行，与这一位出现之前一致，不需要迁移。
     *
     *  80 = `ComboDto` 多了成员「单击后长按」的时序与收尾那一串
     *       （`member_click_then_long_fire_on_release` / `_repeat*` / `_release*`）。
     *       缺键 ⇒ 全关着 / 默认间隔 / 无操作，与这一位出现之前一致。
     *
     *  81 = **顺序组合自己一组窗口**：`GlobalDto` 多了 `combo_ordered_wait_window_ms` /
     *       `combo_ordered_wait_pair_ms` / `combo_ordered_hold_window_ms` /
     *       `combo_ordered_hold_follower_ms` / `combo_ordered_fixed_window_start` /
     *       `combo_ordered_hold_fixed_window_start`，`ComboDto` 多同名六位。
     *       缺键 ⇒ 出厂值（固定等 350 / 逐次等 250、两颗开关关）⇒ 与「不分顺序那四格」同一套数，
     *       不需要迁移。
     *
     *  82 = **`own_settings_seeded` 删掉**（独立设置不再"抄一份公共值"播种）。
     *       `KeyDto` / `ComboDto` 各少这一位；老文件里那个键由 `ignoreUnknownKeys` 丢掉，
     *       丢掉之后的表现正是新的口径：开着独立设置而没写过的格子取出厂默认
     *       （见 `docs/changelog/2026-11-17-独立设置去掉播种.md`）。无行为迁移。
     *
     *  83 = **命令的落点折成一个开关**：`CommandDto` 的 `outputIcon` 删掉、`output` 那一格
     *       保留为空串（只读迁移），新增 `showOutput` —— 非空且不是 `none` 的老值折成 `true`。
     *       缺键 ⇒ `false`，与这一位出现之前「不显示」一致；`outputIcon` 由 `ignoreUnknownKeys` 丢掉。
     *  84 = **「执行时提示」多一档通知**：`GlobalDto` / `KeyDto` / `ComboDto` 各多了 `toast_notify`
     *       （缺键 ⇒ `false`）。四档由三颗布尔派生（`NoticeMode.of`），老文件读进来逐档不变：
     *       `on=false` 仍是「不提示」、`on=true + fluid=true` 仍是「流体云」。
     *
     *  85 = **顺序组合那一组窗口删掉**（有序无序共用同一套）：v81 加进来的
     *       `combo_ordered_*` 六位（`GlobalDto`）与 `ComboDto` 的同名六位一并消失 ——
     *       档位要不要按先后落与「多久之内落完」是两件事，窗口只有一套。
     *       老文件里残留的这几位按未知键丢掉，取值回落到共用那四格 + 两颗开关。
     *
     *  注意 **往 [ConfigDto] 加字段必须同一次改动里抬这个数**：下面 [digestMatches] 的口径是
     *  「按**当前** DTO 重新编码算 md5」，`encodeDefaults = true` 会把新字段写进重编码结果 ——
     *  不抬版本的话，`version == VERSION` 那一支会拿新 DTO 去校旧文件，**每一份已有配置都当场
     *  被判读不懂**（页面只读、注入侧不接管，表现就是「配置读不出来」）。`extra_clicks` 这一轮
     *  就漏抬过一次，症状与 v3 那次一模一样。
     */
    const val VERSION = 85

    /**
     * 「提示走流体云」由「可选增强」变成「默认落点」的那一版 —— 迁移只看「文件是不是比它旧」。
     *
     *  **不能写成 `< VERSION`**：那样下一次抬版本会把 v63 用户自己关掉的那一位又翻回来
     *  （迁移会跟着版本号一起往前走）。
     */
    const val VERSION_FLUID_DEFAULT = 63

    /** 拼十六进制用，见 [md5Hex]。 */
    private const val HEX = "0123456789abcdef"

    /**
     * 唯一的编解码器 —— **写与校必须用同一份配置**，否则「自己写的文件自己读不出来」。
     *
     * `encodeDefaults = true`：文件里写全每一个字段（老那版也是全写），这样 `md5`
     * 覆盖的是完整正文。
     *
     * `ignoreUnknownKeys` 只对 `version < VERSION`（不校验 md5）有意义：当前的 md5 按 DTO 重新编码算，
     * 正文里多出来的键在重编码时会被丢掉 ⇒ 带未知键的当前版本文件必然对不上、判「读不懂」。
     * 所以新增字段必须同时抬 [VERSION]，不要指望未知键做向前 / 向后兼容。
     */
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            ignoreUnknownKeys = true
            // 显式 `null` 按「缺字段」处理（回退 DTO 上的默认值）：手改 / 外部工具写出来的文件里
            //  一个 `"keys": null` 就够让整份配置被判「读不懂」——页面随即只读，用户怎么改都存不进去。
            coerceInputValues = true
        }

    /**
     * 解析配置文本，**读不懂返回 null**（空文本 / 坏 JSON / 类型不对 / 缺键段 / 当前版本的 md5 对不上）。
     *
     * 为什么必须区分「读懂了但缺字段」与「读不懂」：默认值里 `enabled=false`、三个手势全是
     * 「无操作」—— **写方**一旦把这份默认值当成真相再写回去，用户配的东西就被整份替换掉了
     *（真机上发生过「振动开关自己变 false」）。
     */
    fun parseOrNull(text: String): Config? =
        try {
            val dto = json.decodeFromString<ConfigDto>(text)
            when {
                // 老文件（v0..VERSION-1）：md5 口径按当前 DTO 算，老文件重编码必然不同 —— 不校验
                dto.version < VERSION -> {
                    if (dto.isStructurallySound()) dto.toConfig() else null
                }

                !digestMatches(dto) -> {
                    // 把版本一起报出来：md5 对不上有两种来路，报出版本才能立刻分辨 ——
                    //  · `dto.version < VERSION`：压根进不来这里（上面那条拦住了），所以只要走到这一支，
                    //    版本就一定是**相等**的。相等还对不上，只可能是这两件之一：
                    //      ① 正文真被改过（手改 / 写到一半被杀 / 编码变了）；
                    //      ② **加了 DTO 字段却没抬 VERSION** —— 校验口径是「拿当前 DTO 重新编码再算 md5」，
                    //         多一个键，重编码的字节就与旧文件不同，于是存量配置整批判死。
                    //    ② 的现场看起来像「文件坏了」，排查方向会被带到文件系统 / 权限 / 编码上去
                    //    （真机上就这么绕过一次），所以日志直接把两个假设都摆出来。
                    XLog.w(
                        "配置正文与 md5 对不上（version=${dto.version} / 本版 $VERSION）—— 判为读不懂。" +
                            "两者相等 ⇒ 要么正文被改过，要么给 DTO 加了字段却忘了抬 VERSION（见 ConfigDto 注释）",
                    )
                    null
                }

                !dto.isStructurallySound() -> {
                    null
                }

                else -> {
                    dto.toConfig()
                }
            }
        } catch (e: Throwable) {
            // 只给 d：宿主每条按键前都要读一遍，这条会成为噪声；判据看调用方（页面那条 w）
            XLog.d("配置解析失败（读不懂）：${e.message}")
            null
        }

    /**
     * 序列化成 JSON 文本，缩进两格、末位带上 `md5`。
     *
     * 顺序是**先算再写**：把不含 `md5` 的那份编码出来算 MD5，再把这一格填上重新编码 ——
     * 于是它不覆盖它自己，校验时才有得比（[digestMatches]）。
     *
     * **写出去的是规范化之后的形态**（见 [canonical]）：map 段落按键名排序。这样文件本身
     * 有一个确定的书写顺序，手改时调换两行也不会改变摘要。
     */
    fun serialize(config: Config): String {
        val body = canonical(ConfigDto.from(config, VERSION, md5 = ""))
        val digest = md5Hex(json.encodeToString(body))
        return json.encodeToString(body.copy(md5 = digest))
    }

    /**
     * 摘要用的**规范形态**：把 map 段落按键名排序，其余保持原样。
     *
     *  为什么要有：`keys` / `app_names` 是 `Map`，序列化顺序等于解析顺序（= 文件里的书写顺序）。
     *  摘要却是在**解析后再编码**的字节上算的 —— 于是用户把两行换个位置（内容一字未改）就会
     *  整份判成「读不懂」，整页变只读。排序之后，书写顺序不再是内容的一部分。
     */
    private fun canonical(dto: ConfigDto): ConfigDto =
        dto.copy(
            keys =
                dto.keys
                    .toSortedMap()
                    //  键内还有一层 `extra_clicks`：那一层同样只按书写顺序排，漏了它等于
                    //  「只把两行次数换个位置」也会让 md5 失配（正是这里要消灭的那种）。
                    .mapValues { (_, key) -> key.copy(extraClicks = key.extraClicks.toSortedMap()) },
            appNames = dto.appNames.toSortedMap(),
        )

    /**
     * 「这份配置够不够格算一份配置」—— 缺键段 / 自定义键少了条目都算读不懂。
     *
     * 缺 `keys` 段（`{"keys":{}}` 配一份自洽的 md5 就是这种）意味着文件被裁剪过或被别的写法生成的：
     * 按默认值补等于静默把主开关 / 三个绑定 / 振动全抹成默认，比判「读不懂」危险得多。
     */
    private fun ConfigDto.isStructurallySound(): Boolean =
        keys.size >= KeyId.builtins.size &&
            KeyId.builtins.all { keys.containsKey(it.id) } &&
            customKeys.all { keys.containsKey(it.id) }

    /** 当前版本的完整性校验：把 `md5` 置空重新编码，与文件里那一格比。 */
    private fun digestMatches(dto: ConfigDto): Boolean {
        if (dto.md5.isEmpty()) return false
        //  规范化形态（新写出去的文件）与原始顺序（本版之前写的文件）都认：
        //  只认前者会让升级那一刻所有既有配置都判成「读不懂」；只认后者则永远修不掉
        //  「换个行序就失效」这个毛病。
        val body = dto.copy(md5 = "")
        return md5Hex(json.encodeToString(canonical(body))) == dto.md5 ||
            md5Hex(json.encodeToString(body)) == dto.md5
    }

    /** 小写十六进制 MD5。自己拼十六进制而不走 `String.format`：后者吃系统区域设置。 */
    private fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }
}

/**
 * **每份配置自己的变量表** —— 与 [ConfigProfiles] / [ProfileCommands] 共用同一套槽号。
 *
 * ```
 * opluskey_config.conf  + opluskey_commands.conf  + opluskey_vars.conf    ← 当前生效的配置
 * opluskey_config-2.conf + opluskey_commands-2.conf + opluskey_vars-2.conf  ← 第 2 份
 * ```
 *
 * ## 为什么变量也要跟着走
 *
 *  变量是**配置的一部分**：一套绑定里「计数」加到 3、另一套里它是 0，本来就是两套东西。
 *  只有一份变量表的话，切换配置只换了绑定、没换它们读写的那些值 —— 切过去那套绑定接着用
 *  上一套的计数，看着像「配置没切干净」。所以「一份配置」= 主配置 + 命令库 + 变量表，三者一起动。
 *
 * ## 与命令库同一套做法
 *
 *  复制 / 对调 / 删除都只搬**磁盘原文**（外皮一起搬）：这几个动作与「值是什么」无关，
 *  过一遍解析只多两种失败可能。那套规矩（读不懂 ≠ 没有 / 第二步失败要还原 / 老配置只搬当前）
 *  收在 [ProfileSlotFile] 一处，与命令库共用同一份实现 —— 两个文件各写一遍时判据会漂，
 *  而漂出来的坏法只在「刚好有一份文件读不懂」时出现。
 *
 * ## 老配置没有自己的变量表怎么办
 *
 *  加这一层之前建的配置只有 `config-N` / `commands-N`。目标槽空着时 [swap] 把**当前这份**变量表
 *  存过去、当前那份不动（等于「记住切走前的样子」）—— 于是老配置切走再切回来，变量原样还在，
 *  不会因为升级就把用户的计数抹成空表。**新建的空白配置**走 [save] 落一份空表，所以它是真的空
 *  （与命令库那条「空白配置 = 空命令库」同一个口径）。
 */
object ProfileVars {
    /**
     * 变量表这一层：槽名前缀 = 当前生效那份的槽名。
     *
     *  `openReadable = false` 与 [VariableStore.save] 同一个口径：只有宿主这一侧（`:ui` 与宿主
     *  主进程，同 uid）读写变量，注入侧不碰它（它只吃配置广播）。
     */
    private val file = ProfileSlotFile(ConfigStore.SLOT_VARS, openReadable = false)

    /**
     * 某份配置对应的变量表槽名。
     *
     *  映射就是「把 `config` 换成 `vars`」：`config` → `vars`、`config-2` → `vars-2` ——
     *  槽号因此在三层之间天然对得上，不需要另一张对照表。
     */
    fun slotOf(configSlot: String): String = file.slotOf(configSlot)

    /**
     * 切换配置时的变量表对调 —— 对称于 [ConfigProfiles.switchTo] 对主配置做的那件事。
     *
     * @return 对调是否成功（目标配置没有自己的变量表时，「存过去」成功就算成功）
     * @see ProfileSlotFile.swap 顺序与还原那两条规矩的唯一实现
     */
    fun swap(configSlot: String): Boolean = file.swap(configSlot, "变量表")

    /**
     * 给 [configSlot] 落一份变量表（新建的空白配置落空表用）。
     *
     * 「空白配置 = 没有变量」：不落这一份的话，切换时目标槽空着会被当成老配置，
     * 于是新配置一上来就继承了上一套的计数 —— 那正是不对的。
     */
    fun save(
        configSlot: String,
        vars: List<Variable>,
    ): Boolean = VariableStore.save(vars, slotOf(configSlot))

    /** 删掉某份配置的变量表（连 `.bak` / `.new` 一起，走 [ModuleFiles.delete]）。 */
    fun delete(configSlot: String): Boolean = file.delete(configSlot)
}

/**
 * **老文件（v58 以前）那一档组合**的触发方式与它当年用的那对动作槽。
 *
 * @param mode 由那两条开关 + 加过的按住顺序推出来的触发方式（[ComboKey.MODE_WAIT] 及以上）
 * @param single 短按该落到哪一个动作（老模型里按住那两档另有 A / B 两套槽）
 * @param long 长按同理
 */
internal class LegacyComboSlot(
    val mode: Int,
    val single: Action,
    val long: Action,
)

/**
 * 老文件的组合键怎么读成「一种触发方式 + 一对动作槽」—— **只在 [ComboDto.toCombo] 调一次**。
 *
 * 为什么单独一页：这套推演只服务「从老版本升上来的那一份文件」，而它读的七个字段
 * （`windowOn` / `holdOn` / `holdOrders` / `singleA` / `longA` / `singleB` / `longB`）在
 * v58 之后**写回恒为 `null` / 空表**。等确认没人再从老版本升上来，连同那一页一起删即可 ——
 * 留在 [ComboDto.toCombo] 里的话，新文件的正常解析会一直夹在这段历史里。
 *
 * 字段本身暂时不能删：`ignoreUnknownKeys` 会把不认识的键直接丢掉，字段一删，老文件里那几位
 * 就再也读不到，「用户配过按住手势」这件事会静默变成「这一档只是等窗口」
 * （见 [ComboDto.windowOn] 的说明）。
 *
 * ## 推演顺序
 *
 * 1. 认窗口（`windowOn` 缺省或为真）⇒ [ComboKey.MODE_WAIT]。窗口那条从组合键第一天起就有、
 *    而且是默认开，所以「没写过这一位」与「写过 true」同解；
 * 2. 不认窗口、也没认按住 ⇒ 那一档本来永远不成立，落到窗口那条（至少能编辑）；
 * 3. 只认按住 ⇒ 看加过哪一种顺序：只有「先按住第二颗」才推成 [ComboKey.MODE_HOLD_SECOND]，
 *    其余（含两种都加过）取 [ComboKey.MODE_HOLD]。**后者要由调用方把键序反过来** —— 现在
 *    「先按住哪一颗」由 `keys` 第一位表达，不再有第二个模式位（见 `ComboDto.toCombo`）。
 *    老模型里两种并存的组合只能留一种，另一种得再建一档。
 *
 * @return `null` = 新文件（[ComboDto.mode] 有值），调用点照常读 `single` / `long`
 */
internal fun ComboDto.legacySlot(): LegacyComboSlot? {
    //  新文件：不用迁移，调用点照常读通用那两位
    if (mode != null) return null
    return built()
}

/** 老文件那一档的推演 —— 与 [legacySlot] 同一条规则，只是不必再判 `mode` 有没有值。 */
private fun ComboDto.built(): LegacyComboSlot {
    val resolved =
        when {
            //  认窗口 ⇒ 窗口那条（`null` / `true` 同解）
            windowOn != false -> ComboKey.MODE_WAIT

            //  两条都关着的那一档本来永远不成立，落到窗口那条
            holdOn != true -> ComboKey.MODE_WAIT

            //  只加了「先按住第二颗」
            ComboKey.MODE_HOLD_SECOND in holdOrders && ComboKey.MODE_HOLD !in holdOrders -> ComboKey.MODE_HOLD_SECOND

            //  加过第一颗（含两种都加过）
            ComboKey.MODE_HOLD in holdOrders -> ComboKey.MODE_HOLD

            else -> ComboKey.MODE_WAIT
        }
    if (resolved == ComboKey.MODE_WAIT) {
        //  窗口那一档当年就用通用那两位，不必从 A / B 槽搬
        return LegacyComboSlot(ComboKey.MODE_WAIT, Action.parse(single), Action.parse(long))
    }
    val first = resolved == ComboKey.MODE_HOLD
    return LegacyComboSlot(
        resolved,
        Action.parse(if (first) singleA else singleB),
        Action.parse(if (first) longA else longB),
    )
}

/**
 * 「导入配置 / 导出配置」的文件格式与解析，全项目只有这一处。
 *
 * 磁盘上是**混淆过的**（[FileObfuscation]，魔数 [MAGIC]）：首行是魔数，之后是正文经两层变换
 * （异或 + Base64，再按一张替身字母表换写）压成的一行。为什么这么做、怎么手动解开，
 * 见 `FileObfuscation` 的类注释。
 *
 * 解开之后是一层 **JSON 信封**，里面套两份**现成的**序列化结果 —— 不另造文本格式：
 *
 *     {
 *       "version": 1,
 *       "config":   { … [ConfigCodec] 那一份 … },
 *       "commands": { … [CommandLibrary] 那一份 … }
 *     }
 *
 * 这样两段各自沿用**原有编解码器**：键名、默认值、容错口径与文件里那份完全一致，
 * 不存在「导入解析与运行解析两把尺子」。配置那段里的 `md5`（[ConfigCodec]）跟着一起进来、
 * 也由 `ConfigCodec.parseOrNull` 校验。
 *
 * 注意：**主开关状态与「已确认」不随文件走** —— 导出的那份把这两样一律写成「关 / 未确认」，
 * 导入时这里再归零一次（文件可能被人手改过）。它们是「这台机器上你本人的决定」，一份配置文件不该
 * 替别人做这个决定，也不该让人靠导入绕开「启用之前先确认」那一步。
 *
 * 但**本机已经确认过**的那两位不会被一次导入抹掉：`ConfigTransfer.applyImportedConfig` 在写盘前
 * 把它们换回本机此刻的值（用户口径「已经是确认免责协议就不需要再清除这个状态了」）。
 * 这里仍然输出 false —— 文件本身不该带走「别人已确认」这件事。
 *
 * 但**逐键的开关不在此列**：导出与导入一律把它们写成开着（见 [withShareableKeys]）——
 * 否则导回来的键全是关的，而那几个开关藏在 ⋮ 菜单里，用户不会想到去那儿找。
 *
 * 注意：**导入是全量校验**：整份文件（混淆层 + 信封 + 版本 + 两段）都过关才落盘，任何一处不过就一个字节都不写
 * —— 半份配置比没导入更难收拾（见 [parse]）。
 *
 * 注意：**界面状态（`UiState`）不进这个包**：它不是用户配置，落在自己那份文件里（见 `UiState` 的类注释；
 * 导出它等于把「这台机器上怎么看列表」这种本地偏好带到别的机器上）。
 */
object ConfigBundle {
    /** 信封版本 —— 目前只有 1；将来若真要改结构，靠它认旧文件（版本对不上直接拒收）。 */
    private const val VERSION = 1

    /**
     * 导出 / 导入文件的首行：固定前缀 + 信封版本。
     *
     * 一物两用：认得出「这是本模块导出的文件」（挡住随手挑个文件就导入），
     * 也是 [parse] 找解混淆入口的钥匙。
     */
    private const val MAGIC = "OPlusKey-Config-$VERSION"

    // 信封自己的三个键。与 `CommandLibrary.LIB_VERSION` / `ConfigCodec.VERSION` **同字面量
    //  不同义**（信封版本 / 命令库版本 / 主配置版本各一个），所以名字带 `ENVELOPE` 区分。
    private const val ENVELOPE_VERSION = "version"
    private const val ENVELOPE_CONFIG = "config"
    private const val ENVELOPE_COMMANDS = "commands"

    /** 信封的编解码器 —— 与另外两处同一个口径（缩进两格、只写给出的字段）。 */
    private val bundleJson =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    /** 解析结果 —— **两段都一定有值**（少一段就当整份不过关，见 [parse]）。 */
    data class Parsed(
        val config: Config,
        val commands: List<Command>,
    )

    /** 把主配置 + 命令库合成一份导出文本：先拼 JSON 信封，再套一层外皮（[FileObfuscation]）。 */
    fun export(
        config: Config,
        commands: List<Command>,
    ): String =
        try {
            // 落进文件的那份：主开关写关、确认记录写未确认（理由见类注释）——
            //  在**写出去之前**就剥掉，别人拿到手再怎么改也改不出「一导入就是已启用」。
            val shareable =
                config.withShareableKeys().copy(
                    disclaimerAccepted = false,
                    commandDisclaimerAccepted = false,
                )
            val json =
                bundleJson.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put(ENVELOPE_VERSION, VERSION)
                        // 两份序列化结果都是 JSON 文本，原样嵌进来（不再拆一遍字段，避免两处维护同一份键名）
                        put(ENVELOPE_CONFIG, bundleJson.parseToJsonElement(ConfigCodec.serialize(shareable)))
                        put(ENVELOPE_COMMANDS, bundleJson.parseToJsonElement(CommandLibrary.serialize(commands)))
                    },
                )
            FileObfuscation.wrap(MAGIC, json)
        } catch (e: Throwable) {
            // 序列化失败是「模块自己的问题」，不该让页面崩；调用方拿到空串会按「导出失败」处理
            XLog.w("导出配置：合成失败：${e.message}")
            ""
        }

    /**
     * 解析并**全量校验**导入的文本。
     *
     * 注意：**只有整份都过关才返回 [Parsed]**：认得出信封、版本认识、主配置与命令库两段都解析成功
     * —— 任何一条不过就返回 `null`，调用方据此**一个字节都不写**。
     * 半份配置比没导入更难收拾：绑定指向不存在的命令、开关与动作对不上，用户还以为导进去了。
     *
     * @return 全部通过时返回 [Parsed]；否则 `null`（调用方只报一句「未知的配置格式，请检查后重试」，
     *   具体哪一段不过关看日志）。
     */
    fun parse(text: String): Parsed? {
        // 先过外皮：**认不出魔数就拒** —— 否则「随手挑个 JSON 文件来导入」会一路过到底
        val json = FileObfuscation.unwrap(MAGIC, text) ?: return null
        val root =
            try {
                bundleJson.parseToJsonElement(json).jsonObject
            } catch (e: Throwable) {
                XLog.w("导入配置：解开之后不是 JSON：${e.message}")
                return null
            }
        // 版本先认：信封结构将来变了，旧版程序读新版文件是「不知道自己在读什么」，一律拒
        val version = (root[ENVELOPE_VERSION] as? JsonPrimitive)?.intOrNull ?: 0
        if (version != VERSION) {
            XLog.w("导入配置：信封版本 $version ≠ 本版 $VERSION，拒收")
            return null
        }

        //  `as? JsonObject` 而不是 `.jsonObject`：后者在「这一段不是对象」（手改过的导入文件
        //  把 config 写成字符串 / 数组）时抛 IllegalArgumentException，而本函数的约定是
        //  「任何一条不过就返回 null、调用方一个字节都不写」—— 异常漏到调用方就破坏了这个约定。
        val configObj = root[ENVELOPE_CONFIG] as? JsonObject
        if (configObj == null) {
            XLog.w("导入配置：没有 $ENVELOPE_CONFIG 段")
            return null
        }
        val config = ConfigCodec.parseOrNull(configObj.toString())
        if (config == null) {
            XLog.w("导入配置：主配置那段读不懂")
            return null
        }

        val commandsObj = root[ENVELOPE_COMMANDS] as? JsonObject
        if (commandsObj == null) {
            XLog.w("导入配置：没有 $ENVELOPE_COMMANDS 段")
            return null
        }
        // 用 parseOrNull：**空表是合法值**（用户的命令库本来就可以是空的），
        // 只有「读不懂」才是 null —— 不能把读不懂当成空表拿去覆盖
        val commands = CommandLibrary.parseOrNull(commandsObj.toString())
        if (commands == null) {
            XLog.w("导入配置：命令库那段读不懂（有条目认不出）")
            return null
        }

        // 导入进来的那份**不接受**主开关状态与「已确认」：我们导出的文件本来就不带，
        //  但这份文件也可能被人手改过 —— 所以在这里再强制归零一次。导入之后必须自己去开、
        //  自己再确认一遍（那一步会弹风险提示，见 `ui/screen/HomeScreen`）。
        //  自定义键那几颗相反：它们没有独立开关，一律置开（见 [withShareableKeys]）。
        return Parsed(
            config.withShareableKeys().copy(
                disclaimerAccepted = false,
                commandDisclaimerAccepted = false,
            ),
            commands,
        )
    }

    /**
     * 导出 / 导入时对开关的处理 —— **只关总闸，每一颗键一律开着**。
     *
     *  总闸现在是一位**独立字段**（`GlobalConfig.enabled`），不再是「侧键那份 `enabled`」：
     *  拆开之前它俩共用一个位，于是这里只能按「自定义键留着、侧键那一位写关」来凑出「别替我
     *  打开模块」的意思 —— 代价是导出的文件里侧键那颗看着是「停用」的。
     *
     * ## 为什么每一颗键都要一律开
     *
     *  文件可能被人手改过（外层混淆只是防误开，不是防篡改）。逐键的 `enabled` 原值是 `false`
     *  的话，导进来就是一堆看得见、用不了的键（早先对自定义键就踩过：导回来的键全是关的、
     *  绑定页整片灰，而界面上没有开关能打开它们）。强制开才不会把用户卡死在这一格；
     *  真要停用哪一颗，导入之后再从 ⋮ 菜单里关就是了。
     */
    private fun Config.withShareableKeys(): Config =
        copy(
            global = global.copy(enabled = false),
            keys = keys.mapValues { (_, key) -> key.copy(enabled = true) },
        )
}
