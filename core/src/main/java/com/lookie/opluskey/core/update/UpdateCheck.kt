package com.lookie.opluskey.core.update

import org.json.JSONObject

/**
 * 检查更新的**来源** —— 两路，各打各的 releases 接口。
 *
 *  为什么留两路：国内直连 GitHub 的接口不稳（DNS / 限流），Gitee 上有一份镜像。
 *  两路**并行**发起（见 `UpdateState`），都回来之后按版本取较新的那一条 —— 不是「谁先回来算谁」：
 *  镜像可能比 GitHub 晚几秒，先到的若是旧的，就会把真正的新版盖掉。
 *
 *  **两路问的不是同一件事**：GitHub 那路问的是模块仓库（管理器认的那份，`publish.yml` 上架时才建）；
 *  Gitee 那路问的是源码仓的 Release —— 那份在**钉版**时就建好了，早于上架。所以 Gitee 有可能
 *  先看到一版还没上架的包。那包是正式签名、装得上，只是「还没对外宣布」。要严格对齐这个语义，
 *  得在 Gitee 另建一个只放上架产物的镜像仓，把 [GITEE_MIRROR_REPO] 换过去。
 */
enum class UpdateSource(
    /** 日志与「从哪一路取的」用的短标识。 */
    val id: String,
    /** 这一路的 API 根（`…/repos/<owner>/<repo>`）。 */
    private val apiBase: String,
    /** 这一路的网页根（`…/<owner>/<repo>`），只在接口不给 `html_url` 时用来拼地址。 */
    private val webBase: String,
) {
    GITHUB("github", "https://api.github.com/repos/$MODULE_REPO", "https://github.com/$MODULE_REPO"),
    GITEE("gitee", "https://gitee.com/api/v5/repos/$GITEE_MIRROR_REPO", "https://gitee.com/$GITEE_MIRROR_REPO"),
    ;

    /** 这一路的「最新 release」接口。 */
    val apiUrl: String
        get() = "$apiBase/releases/latest"

    /**
     * 这一路某条 release 的网页地址 —— 只在接口没给 `html_url` 时兜底。
     *
     *  Gitee 的 release 对象**没有** `html_url`（`tag_name` / `body` / `assets` 都有，就缺这个），
     *  所以这一路必须自己拼；不拼的话「去下载」那颗按钮拿到的是空串。
     */
    fun tagPageUrl(tag: String): String = "$webBase/releases/tag/$tag"
}

/**
 * 模块仓库 —— 管理器认的那一份，`publish.yml` 上架时才建 release。
 *
 *  不取源码仓（`lo0kie/OPlusKey-lsposed`）：用户下载页与发版产物都在模块仓，版本号只有那份
 *  发布记录是权威的。
 */
private const val MODULE_REPO = "Xposed-Modules-Repo/io.github.lo0kie.opluskey"

/** Gitee 上那份镜像仓（`release.yml` 钉版时把同一批包发进去）。 */
private const val GITEE_MIRROR_REPO = "look1e/opluskey-lsposed"

/**
 * 一条发布记录里更新要用的三样东西。
 *
 * @param version 版本号（已去掉 `v` 与 `<versionCode>-` 前缀；带 `-debug` 这类后缀时原样保留在
 *   [version] 里，比较只看数字段，见 [isNewer]）
 * @param notes 更新说明（可能很长，界面上自己截断）
 * @param pageUrl 这条发布的网页地址（点「去下载」时打开它）
 */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
) {
    /**
     * 界面上显示的版本号 —— 去掉发布仓 tag 前面那段序号（`12-1.3.4` → `1.3.4`）。
     *
     *  模块仓的 tag 是 `<versionCode>-<versionName>`，前一段是发布序号、不属于版本；
     *  直接显示会变成「发现新版本 12-1.3.4」。判据只看第一段是不是纯数字：是就砍掉，
     *  不是就原样返回（`1.3.3` 没有 `-`；`1.3.3-debug` 的第一段带点，也不动）。
     *
     *  只影响显示 —— 比较与「不再提示」仍用 [version] 原文，与 tag 一一对应。
     */
    val displayVersion: String
        get() {
            val cut = version.indexOf('-')
            val head = if (cut > 0) version.substring(0, cut) else ""
            return if (head.isNotEmpty() && head.all(Char::isDigit)) {
                version.substring(cut + 1).ifEmpty { version }
            } else {
                version
            }
        }
}

/**
 * 解析一次 releases 接口的返回。
 *
 *  GitHub 与 Gitee 的「最新 release」字段名基本一致（`tag_name` / `body`），**只有 `html_url`
 *  是 GitHub 独有**：Gitee 不给，所以由调用方传 [fallbackPageUrl] 兜底（见 [UpdateSource.tagPageUrl]）。
 *  **缺字段就返回 `null`**：宁可当作「这次没查到」，也不要拿一个空版本号去和当前版本比
 *  （那样每次都会判成「有新版」）。
 *
 * @param fallbackPageUrl 接口没给 `html_url` 时，用原始 `tag_name` 拼一个地址
 * @return 解析成功且版本号非空时返回记录，否则 `null`
 */
fun parseLatestRelease(
    json: String,
    fallbackPageUrl: (String) -> String = { "" },
): ReleaseInfo? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    //  **必须 `isNull` 判一次**：`optString` 对 JSON `null` 的返回在各版本 org.json 上不一致
    //  （Android 那份会给出字符串 `"null"`），照抄下来就是版本号 `"null"`、更新说明写成 `"null"`。
    val tag = textOf(obj, "tag_name").trim()
    val version = plainVersionOf(tag)
    if (version.isEmpty()) return null
    return ReleaseInfo(
        version = version,
        notes = textOf(obj, "body").trim(),
        pageUrl = textOf(obj, "html_url").trim().ifEmpty { fallbackPageUrl(tag) },
    )
}

/** 取一个文本字段 —— JSON `null` 与缺失都当空串。 */
private fun textOf(
    obj: JSONObject,
    key: String,
): String = if (obj.isNull(key)) "" else obj.optString(key)

/**
 * `tag_name` → 界面要显示的那串版本号。
 *
 *  两种前缀都去掉：`v`（手打 tag 的写法）与 `<versionCode>-` —— 模块仓库那条 Release 的 tag 是
 *  `<versionCode>-<versionName>`（`12-1.3.4`，管理器按这个格式拆），直接摆进弹窗就是「最新 12-1.3.4」。
 *  只剥**开头那段纯数字加一个连字符**，且剥完必须还剩一个带小数点的版本号：`1.3.4-rc1` 那种
 *  后缀里的连字符不能被当成前缀切掉。
 */
private fun plainVersionOf(tag: String): String {
    val body = tag.removePrefix("v").removePrefix("V")
    val stripped = body.replaceFirst(Regex("^[0-9]+-(?=[0-9]+[.])"), "")
    return stripped.ifEmpty { body }
}

/**
 * 线上版本 [latest] 是不是比手上的 [current] 新。
 *
 *  逐段按数字比（`1.10.0` 比 `1.9.3` 新 —— 按字符串比会得出相反答案）；段数不同时短的按 0 补齐。
 *  带后缀的（`1.3.3-debug`、`1.4.0-rc1`）只取后缀之前那一段数字：
 *  后缀是构建档位，不是版本次序，拿它参与比较只会让 debug 包永远判成「旧」。
 *
 *  任一侧切不出语义版本号（例如 `latest` 这种名字）时返回 `false` —— 判不出新旧就不打扰人。
 */
fun isNewer(
    latest: String,
    current: String,
): Boolean = compareVersion(latest, current) > 0

/** 版本比较：[a] 比 [b] 新返回正数，旧返回负数，相等返回 0（判据见 [isNewer]）。 */
fun compareVersion(
    a: String,
    b: String,
): Int {
    val left = semanticVersion(a)
    val right = semanticVersion(b)
    if (left.isEmpty() || right.isEmpty()) return 0
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return if (l > r) 1 else -1
    }
    return 0
}

/**
 * 从一段 tag / 版本字符串里抠出**语义版本号**（`x.y.z` 那几段数字）。
 *
 *  发布仓库的 tag 是 `11-1.3.3` 这种「序号-版本号」（前面的 `11` 是仓库的发布序号，
 *  与版本无关），本机版本名是 `1.3.3-debug` 这种「版本号-构建档」。两者都靠**认出一段
 *  `数字.数字(…).数字`** 来取版本号，而不是「取第一段」或「取 `-` 之后」：
 *  前一种会把 `11` 当版本、后一种会把 `debug` 当版本。
 *
 *  具体做法：按 `-` / `+` / 空格 / 下划线切成几段，取**第一段能拆出至少两段纯数字**的
 *  （`11-1.3.3` → `["11", "1.3.3"]`，命中 `1.3.3`；`1.3.3-debug` → `["1.3.3", "debug"]`，
 *  命中 `1.3.3`）。认不出就返回空表 —— 那说明这串东西根本不是版本号，宁可判「不比」也不硬猜。
 */
private fun semanticVersion(version: String): List<Int> =
    version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('-', '+', ' ', '_')
        .mapNotNull { token ->
            //  **任一段不是纯数字就整段作废**：以前是 `mapNotNull { toIntOrNull() }`，于是
            //  `"1.3.4beta"` 被截成 `[1, 3]`，与 `1.3.x` 比较时得出相反的结论（把带后缀的当成旧的）。
            val parts = token.split('.')
            if (parts.size < 2) return@mapNotNull null
            parts.map { it.toIntOrNull() ?: return@mapNotNull null }
        }.firstOrNull()
        .orEmpty()
