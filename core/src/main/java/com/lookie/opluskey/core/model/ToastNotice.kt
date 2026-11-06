package com.lookie.opluskey.core.model

/**
 * 「执行时提示」那一句的**模板** —— 用户可改，三个占位符各填一段。
 *
 *  拆分之前那句是写死的 `%s → %s`（主语 → 动作名），主语又是 `键名 - 手势` 拼出来的；
 *  于是「换一种说法」「只留动作名」「加个动作前缀」都做不到。现在整句都是一个模板：
 *
 *  ```
 *  $key     键名（内置键走字典，自定义键是用户起的名字）
 *  $gesture 手势（单击 / 双击 / 长按 / 3 击 …）
 *  $action  动作名（自定义命令是命令名）
 *  ```
 *
 *  **默认模板就是拆分之前那一句**：老配置没有这一项时字段表补的也是它，升级前后弹出来的字一模一样。
 *
 *  占位符为什么用 ASCII 名字而不是中文：模板是要**打字**的，`$键名` 在中文输入法下要来回切；
 *  界面里那句说明写中文（「`$key` 键名 / `$gesture` 手势 / `$action` 动作」），两边各用各自顺手的写法。
 *
 *  单趟扫描替换（不是三次 `replace`）：替进去的值里若正好带着 `$action` 这种字样（用户就给自己
 *  那颗键起名叫 `$action`），不会再被展开一次。
 */
object ToastNotice {
    /** 键名。 */
    const val KEY = "\$key"

    /** 手势。 */
    const val GESTURE = "\$gesture"

    /** 动作名。 */
    const val ACTION = "\$action"

    /** 界面上要逐个列出来的占位符（顺序即说明顺序）。 */
    val PLACEHOLDERS: List<String> = listOf(KEY, GESTURE, ACTION)

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
     *  @return 拼好的整句；**不会是空串**（模板只剩空白 / 全是不认识的占位符时退回默认那句）
     */
    fun render(
        template: String,
        key: String,
        gesture: String,
        action: String,
    ): String {
        val text = expand(template.ifBlank { DEFAULT_TEMPLATE }, key, gesture, action).trim()
        //  模板可以写得很短（例如只剩一个 `$action`），但也可能被删成空白 —— 那一下弹个空 Toast
        //  等于「按了没反应」，所以退回默认那句。
        return text.ifEmpty { expand(DEFAULT_TEMPLATE, key, gesture, action) }
    }

    /** 单趟扫描替换：只认 [PLACEHOLDERS] 里那三个 token，替进去的值不再二次展开。 */
    private fun expand(
        template: String,
        key: String,
        gesture: String,
        action: String,
    ): String {
        val out = StringBuilder(template.length + key.length + gesture.length + action.length)
        var i = 0
        while (i < template.length) {
            val hit = PLACEHOLDERS.firstOrNull { template.startsWith(it, i) }
            if (hit == null) {
                out.append(template[i])
                i++
                continue
            }
            out.append(
                when (hit) {
                    KEY -> key
                    GESTURE -> gesture
                    else -> action
                },
            )
            i += hit.length
        }
        return out.toString()
    }
}
