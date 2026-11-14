package com.lookie.opluskey.core.model

/**
 * 「执行时提示」那一句的**模板** —— 用户可改，六个占位符各填一段。
 *
 *  拆分之前那句是写死的 `%s → %s`（主语 → 动作名），主语又是 `键名 - 手势` 拼出来的；
 *  于是「换一种说法」「只留动作名」「加个动作前缀」都做不到。现在整句都是一个模板：
 *
 *  ```
 *  $key     键名（内置键走字典，自定义键是用户起的名字）
 *  $gesture 手势（单击 / 双击 / 长按 / 3 击 …）
 *  $action  动作名（整句：系统开关: WIFI - 打开）
 *  $parent  父动作（系统开关）；没有父动作时是空串
 *  $name    子动作（WIFI）；没有名字那一段时回落到整句
 *  $state   状态（打开）；没有状态时回落到**这一档自己的名字**（`媒体控制: 上一首` ⇒ `上一首`）
 *  ```
 *
 *  后三段是 `$action` 的**拆法**，不是另一套说法：它们与整句由同一个出口算出来
 *  （`CommandTitle.reading`），所以「整句」与「拆开」永远自洽。回落的判据在 [valueOf]：
 *  「截屏」这种没有父也没有状态的单一功能动作，`$name` 就是它自己、`$state` 也回落到它，
 *  模板只写其中一段时不会念出一条空提示。**回落不带父名** —— `$state` 答的是「这一下具体
 *  干了什么」，把 `媒体控制:` 一起端出来等于换了个 `$action`。
 *
 *  **默认模板就是拆分之前那一句**：老配置没有这一项时字段表补的也是它，升级前后弹出来的字一模一样。
 *
 *  占位符为什么用 ASCII 名字而不是中文：模板是要**打字**的，`$键名` 在中文输入法下要来回切；
 *  界面里那行说明写中文（框里是 `$key`、框后面是「键名」），两边各用各自顺手的写法。
 *
 *  单趟扫描替换（不是六次 `replace`）：替进去的值里若正好带着 `$action` 这种字样（用户就给自己
 *  那颗键起名叫 `$action`），不会再被展开一次。
 */
object ToastNotice {
    /** 键名。 */
    const val KEY = "\$key"

    /** 手势。 */
    const val GESTURE = "\$gesture"

    /** 动作名 —— 整句。 */
    const val ACTION = "\$action"

    /** 父动作。 */
    const val PARENT = "\$parent"

    /** 子动作。 */
    const val NAME = "\$name"

    /** 状态。 */
    const val STATE = "\$state"

    /**
     * 界面上要逐个列出来的占位符（顺序即说明顺序）。
     *
     *  界面把它当**变量清单**用：说明那一句里的 `%s` 按这个顺序换成这几枚名字框
     *  （`ui/row/ChipLabel.ChipLegend`）。所以这里的顺序就是用户读到的那一行。
     */
    val PLACEHOLDERS: List<String> = listOf(KEY, GESTURE, ACTION, PARENT, NAME, STATE)

    /**
     * 默认模板 —— **必须与拆分之前那句逐字一致**：`侧键 - 单击 → 手电筒`。
     *
     *  这一句同时是「用户把模板删空」时退回的那一档，所以逗号 / 空格 / 箭头都别顺手改：
     *  改了就是所有没动过这项的人的提示语气都变一次。
     */
    const val DEFAULT_TEMPLATE = "$KEY - $GESTURE → $ACTION"

    /**
     * 按模板拼这一句提示。
     *
     *  @param template 用户写的模板（空 = 用 [DEFAULT_TEMPLATE]）
     *  @param action 这一次执行的动作（整句 + 拆开的三段，见 [ActionReading]）
     *  @return 拼好的整句；**不会是空串**（模板只剩空白 / 全是不认识的占位符时退回默认那句）
     */
    fun render(
        template: String,
        key: String,
        gesture: String,
        action: ActionReading,
    ): String {
        val text = expand(template.ifBlank { DEFAULT_TEMPLATE }, key, gesture, action).trim()
        //  模板可以写得很短（例如只剩一个 `$action`），但也可能被删成空白 —— 那一下弹个空 Toast
        //  等于「按了没反应」，所以退回默认那句。
        return text.ifEmpty { expand(DEFAULT_TEMPLATE, key, gesture, action) }
    }

    /** 单趟扫描替换：只认 [PLACEHOLDERS] 里那几个 token，替进去的值不再二次展开。 */
    private fun expand(
        template: String,
        key: String,
        gesture: String,
        action: ActionReading,
    ): String {
        val out = StringBuilder(template.length + key.length + gesture.length + action.sentence.length)
        var i = 0
        while (i < template.length) {
            val hit = PLACEHOLDERS.firstOrNull { template.startsWith(it, i) }
            if (hit == null) {
                out.append(template[i])
                i++
                continue
            }
            out.append(valueOf(hit, key, gesture, action))
            i += hit.length
        }
        return out.toString()
    }

    /**
     * 一个占位符换成什么 —— 六个 token 的**唯一**取值处，含「那一段不存在时回落到什么」。
     *
     *  父动作没有就是**空串**：那一格是「前缀」，拿名字去顶会念成 `截屏: 截屏`。
     *  子动作 / 状态没有则回落到**这一档自己的名字**（叶子那一段，不是整句）：
     *  `媒体控制: 上一首` 这种两段式动作里 `$state` 就是 `上一首` —— 把父名一起端出来
     *  （`媒体控制: 上一首`）等于换了个 `$action`，用户要的是「这一下具体干了什么」。
     */
    private fun valueOf(
        token: String,
        key: String,
        gesture: String,
        action: ActionReading,
    ): String {
        //  「这一档自己的名字」—— 状态没有时它就是叶子那一段；连名字都空（认不出的手改配置）
        //  才退回整句，那已经是最后一档兜底了。
        val leaf = action.name.ifEmpty { action.sentence }
        return when (token) {
            KEY -> key
            GESTURE -> gesture
            ACTION -> action.sentence
            PARENT -> action.parent.orEmpty()
            NAME -> leaf
            STATE -> action.state ?: leaf
            else -> ""
        }
    }
}

/**
 * 「这一次执行的是什么」—— 整句与拆开的三段。
 *
 *  提示模板两种念法都要：`$action` 要整句（`系统开关: WIFI - 打开`），`$parent` / `$name` /
 *  `$state` 要拆开的各段。三段与整句由同一个出口算出来（`CommandTitle.reading`），
 *  所以它们**必然自洽** —— 分两次取的话，将来哪一处改了拼法，提示里就会出现
 *  「整句是 A、拆开是 B」。
 *
 *  @param sentence 整句（`$action` 念的那一段）
 *  @param parent 父动作名；`null` = 这一档自己就是那个动作
 *  @param name 子动作名；空串 = 没有名字那一段
 *  @param state 状态那一段；`null` = 这一档没有状态
 */
class ActionReading(
    val sentence: String,
    val parent: String?,
    val name: String,
    val state: String?,
)
