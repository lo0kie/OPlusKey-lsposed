package com.lookie.opluskey.core.update

import org.json.JSONObject

/**
 * 检查更新的**来源** —— 目前两路，指向同一份 releases 接口。
 *
 *  为什么留两路：国内直连 GitHub 的接口不稳（DNS / 限流），Gitee 上会放一份同构的镜像。
 *  **镜像仓库还没建**（用户口径「gitee 暂时和 github 用同一个」），所以两路现在打的是同一个
 *  地址 —— 结构先按「两路各有各的地址」摆好，仓库一建好只改 [apiUrl] 一处，
 *  调用方与解析都不动。
 *
 *  顺序即**尝试顺序**（见 `UpdateState`）：先 GitHub，取不到再走下一路。
 */
enum class UpdateSource(
    /** 日志与「从哪一路取的」用的短标识。 */
    val id: String,
) {
    GITHUB("github"),
    GITEE("gitee"),
    ;

    /**
     * 这一路的 releases 接口。
     *
     *  Gitee 的镜像仓库尚未建立，**暂时与 GitHub 同一个地址**（见类注释）。
     */
    val apiUrl: String
        get() =
            when (this) {
                GITHUB -> GITHUB_LATEST_RELEASE
                GITEE -> GITHUB_LATEST_RELEASE
            }
}

/**
 * 模块仓库的「最新 release」接口。
 *
 *  取的是 **Xposed-Modules-Repo 那个发布仓**（不 `api.github.com` 的源码仓）：用户下载页与
 *  发版产物都在那儿，版本号只有那份发布记录是权威的。
 */
private const val GITHUB_LATEST_RELEASE =
    "https://api.github.com/repos/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases/latest"

/**
 * 一条发布记录里更新要用的三样东西。
 *
 * @param version 版本号（已去掉 `v` 前缀；带 `-debug` 这类后缀时原样保留在 [version] 里，
 *   比较只看数字段，见 [isNewer]）
 * @param notes 更新说明（可能很长，界面上自己截断）
 * @param pageUrl 这条发布的网页地址（点「去下载」时打开它）
 */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
)

/**
 * 解析一次 releases 接口的返回。
 *
 *  GitHub 与 Gitee 的「最新 release」字段名一致（`tag_name` / `body` / `html_url`），
 *  所以一份解析管两路。**缺字段就返回 `null`**：宁可当作「这次没查到」，
 *  也不要拿一个空版本号去和当前版本比（那样每次都会判成「有新版」）。
 *
 * @return 解析成功且版本号非空时返回记录，否则 `null`
 */
fun parseLatestRelease(json: String): ReleaseInfo? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val version =
        obj
            .optString("tag_name")
            .trim()
            .removePrefix("v")
            .removePrefix("V")
    if (version.isEmpty()) return null
    return ReleaseInfo(
        version = version,
        notes = obj.optString("body").trim(),
        pageUrl = obj.optString("html_url").trim(),
    )
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
        .mapNotNull { token -> token.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size >= 2 } }
        .firstOrNull()
        .orEmpty()
