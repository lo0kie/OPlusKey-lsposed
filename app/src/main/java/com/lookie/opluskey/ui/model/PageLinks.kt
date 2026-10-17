package com.lookie.opluskey.ui.model

// 外链与作者身份。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。

/**
 * 「查看地址」行点开的目标 —— 本仓库的线上地址（`AboutScreen` 那行的 `summary` 与 `onClick` 都直接引它）。
 */
internal const val REPO_URL = "https://github.com/lo0kie/OPlusKey-lsposed"

/** 「作者」行的值 —— 与仓库所有者同名。 */
internal const val AUTHOR = "lo0kie"

/** 「作者」菜单里「GitHub」那一项的目标 —— 作者的 GitHub 主页。 */
internal const val PROFILE_URL = "https://github.com/$AUTHOR"

/**
 * 「作者」菜单里「酷安」那一项的目标 —— 作者的酷安主页。
 *
 *  `AuthorTarget.configured` 判的是「地址空不空」（空串会让那一行置灰、点了不派发），
 *  所以这一行有值就等于菜单里那一项是亮的。
 */
internal const val COOLAPK_URL = "https://www.coolapk.com/u/1014357"

/**
 * 「反馈问题」菜单里「GitHub Issue」那一项的目标 —— 本仓库的 issue 列表。
 */
internal const val ISSUE_URL = "$REPO_URL/issues"

/**
 * 「反馈问题」菜单里「QQ 频道」那一项的目标 —— 频道的分享链接。
 *
 *  `pd.qq.com/s/<id>?b=<数字>` 是 QQ 频道的分享短链形式（`b` 那段是分享来源标记），
 *  点开会拉起 QQ 进频道卡片。
 */
internal const val QQ_CHANNEL_URL = "https://pd.qq.com/s/5q8sgrlx5?b=5"

/**
 * 「反馈问题」菜单里「Telegram」那一项的频道地址。
 *
 *  这一串是 Telegram 那种 `+` 开头的**邀请链接**（不是 `t.me/<频道名>` 的公开频道形式），
 *  点开会先过一个「加入」确认页 —— 正常，不用改。
 */
internal const val TELEGRAM_URL = "https://t.me/+u3I5kFTb25BkMjA1"
