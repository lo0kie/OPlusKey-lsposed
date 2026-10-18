package com.lookie.opluskey.core.model

/**
 * `app:` / `window:` / `appshortcut:` 动作的**目标写法** —— 包名 / 组件名，外加一个可选的多开用户维度。
 *
 * ## 写法
 *
 * ```
 * com.tencent.mm                          纯包名，主用户（user 0）
 * com.tencent.mm/.ui.LauncherUI           显式组件，主用户
 * 999@com.tencent.mm                      纯包名，分身（user 999）
 * 999@com.tencent.mm/.ui.LauncherUI       显式组件，分身
 * ```
 *
 *  前缀规则是 `<userId>@`，**缺省即主用户** —— 老配置里那些 `app:com.tencent.mm/.ui.LauncherUI`
 *  一个字都不用改，它们仍然指向主用户。加一个 0 前缀（`0@pkg`）也认，写出来只是为了显式。
 *
 * ## 为什么用 `@` 而不是别的
 *
 *  包名与类名只允许字母、数字、下划线与点，**不含 `@`**；而 `@` 在人类读起来也像个「地址前缀」，
 *  与 `user@host` 同一套直觉。用 `/` 不行（组件名已经在用），用 `:` 不行（token 前缀在用）。
 *
 * ## 显示名**不再**塞在这里
 *
 *  早先的写法是 `999@包名/类名|微信 2` —— 名字跟在目标后面，靠 `|` 切回来。那是错的：
 *  名字是**显示信息**，不是目标的一部分，塞进去之后每个读它的地方都得先剥一层，剥漏一处就
 *  退化成包名（真机反馈「选完快捷方式 / 界面，再进来显示的是包名」）。现在名字单独存在
 *  `Config.appNames`（按身份索引），见 `AppNames`。
 *
 *  [label] 只为**读老配置**留着：那些文件里已经写着 `|名字`，删掉它老绑定就显示不出名字。
 *  [identity] 是「这个绑定是谁」（去掉那截名字之后的部分），名字表的键、去重、比较都用它 ——
 *  新写的目标不带名字，所以它对新数据就是原串。
 */
object AppTarget {
    /** 用户前缀与目标之间的分隔符（口径见类注释）。 */
    private const val SEPARATOR = '@'

    /** 老配置里目标与显示名之间的分隔符 —— **只读不写**，见类注释。 */
    private const val LEGACY_LABEL_SEPARATOR = '|'

    /**
     * 拼一个目标写法（**不带显示名**，见类注释）。
     *
     * @param userId 多开用户（`UserHandle` 的 id）；传 `0` 表示主用户 —— 那时**不加前缀**，
     *   产出与老配置逐字相同
     */
    fun format(
        target: String,
        userId: Int,
    ): String = if (userId == 0) target else "$userId$SEPARATOR$target"

    /**
     * 老配置里跟在 `|` 后面的显示名；没有这一段返回 `null`。
     *
     *  **只用于读旧数据与兜底显示**：新写的目标不带它（见类注释），新数据里的名字在
     *  `Config.appNames`。
     */
    fun label(target: String): String? =
        target
            .substringAfter(LEGACY_LABEL_SEPARATOR, "")
            .ifEmpty { null }

    /**
     * 剥掉老配置里那截显示名，其余（用户前缀 + 组件）保留 —— 就是**这个绑定的身份**。
     *
     *  新数据里它等于原串（没有 `|` 可剥），所以「身份」这一层可以无条件走它：
     *  `Config.appNames` 的键、去重、比较，全都用同一套口径。
     */
    fun identity(target: String): String = target.substringBefore(LEGACY_LABEL_SEPARATOR)

    /**
     * 目标里的用户 id —— **没有前缀就是 0**（主用户）。
     *
     *  只在 `@` 前面的部分**全是数字**时才认：`@` 出现在别处（比如某个手改坏的包名）时
     *  整串按主用户处理，交给后面的解析去失败 —— 不在这一层抛，避免「一个坏绑定崩掉整页」。
     */
    fun userId(target: String): Int {
        val plain = identity(target)
        val at = plain.indexOf(SEPARATOR)
        if (at <= 0) return 0
        val head = plain.substring(0, at)
        //  **只认 ASCII 数字**：`Char.isDigit()` 把全角 `０-９` 也算数字，而 `toIntOrNull()` 不认 ——
        //  那样「９９９@包名」会被判成 0，却仍被 [withoutUser] 剥掉前缀，坏前缀静默变成
        //  「主用户 + 合法目标」。
        return if (isAsciiDigits(head)) head.toIntOrNull() ?: 0 else 0
    }

    /** 去掉用户前缀与（老配置的）显示名之后的那一段（`pkg` 或 `pkg/activity`）—— 解析组件、查包都用它。 */
    fun withoutUser(target: String): String {
        val plain = identity(target)
        val at = plain.indexOf(SEPARATOR)
        if (at <= 0) return plain
        return if (isAsciiDigits(plain.substring(0, at))) plain.substring(at + 1) else plain
    }

    /** 只要包名（给 `getPackageInfo` / 图标 / 名字用）。 */
    fun pkg(target: String): String = withoutUser(target).substringBefore('/')

    /** 显式组件那一段（`包名/类名` 的类名部分），没有就返回 `null`。 */
    fun activity(target: String): String? = withoutUser(target).substringAfter('/', "").ifEmpty { null }

    /** 换成另一个用户，其余不变（选择页里「主用户 / 分身」互相切换时用）。 */
    fun withUser(
        target: String,
        userId: Int,
    ): String = format(withoutUser(target), userId)

    /**
     * 两个目标**是不是同一个绑定** —— 忽略显示名，只比用户 + 组件。
     *
     *  选择页拿它比「已选中的那个」与清单里的行：清单那行的名字是这次现查的，
     *  绑定里存的是上次存下的，应用改过名就会对不上。用 `==` 比会让选中凭空失效
     *  （`SelectionPrune` 会把 `pending` 清掉，确认键跟着消失）。
     *
     *  任一方为 `null` 一律 `false`：调用点的「选中项」是可空的，传进来的多半是 `pending`，
     *  拿 `null` 去比不该算「匹配上了」。
     */
    fun same(
        a: String?,
        b: String?,
    ): Boolean =
        a != null && b != null &&
            userId(a) == userId(b) && withoutUser(a) == withoutUser(b)

    /**
     * 两个目标是不是**同一个应用** —— 忽略 `/类名`（或快捷方式的 `/id`）与显示名。
     *
     *  与 [same] 的分工：那个问的是「是不是同一个绑定」，`包名` 与 `包名/类名` 在它眼里是两回事；
     *  这个问的是「是不是同一个应用」——「选择应用」页一行一个包，而绑定里可能带着具体界面，
     *  要判「这一行是不是当前选中的那个应用」只能用它。
     */
    fun sameApp(
        a: String?,
        b: String?,
    ): Boolean =
        a != null && b != null &&
            pkg(a) == pkg(b) && userId(a) == userId(b)
}

/**
 * 全是 ASCII `0-9` 且非空 —— 与 `toIntOrNull()` 同一套口径。
 *
 *  别用 `Char.isDigit()`：它认 Unicode 的十进制数字（全角 `０-９`、阿拉伯-印度数字等），
 *  而 `toIntOrNull()` 只解析 ASCII ⇒ 两者口径不一致时，前缀会被剥掉却解析成 0。
 *
 *  放在文件级而不是 object 里：`AppTarget` 的函数数已经顶到 detekt 的阈值。
 */
private fun isAsciiDigits(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' }
