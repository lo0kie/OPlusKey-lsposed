package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.log.XLog

/**
 * 流体云胶囊那颗图标的**落点** —— 界面层与宿主侧各写一半，名字必须同一份。
 *
 *  `fluid:` 的 `logo` 可以是 `content://`（系统自己弹相机胶囊时就这么传），它指向宿主
 *  `FileProvider` 映射的那个目录；文件**只由界面层画**（`ui/model/icon/FluidCardArt`，
 *  那里有 Compose，能把 Tabler 的 `ImageVector` 画成位图），宿主侧只负责「找到它、组出 uri」。
 *
 *  **没选图标就不留文件、也不画默认那颗**：那种命令（`fluid:正文`）执行时 `logo` 传空，
 *  胶囊左边那一格空着。以前这里有一套「默认图标 + 宿主兜底」的链路，于是一模一样的胶囊分不出
 *  「选过默认那颗」和「压根没选」，2026-11-13 按用户口径拆掉了。
 */
object FluidCard {
    /** 宿主 `files/` 下的共享目录（`FileProvider` 的 `share_images` 路径段）。 */
    const val SHARE_DIR = "share_images"

    /**
     * 「空 id」那一档的文件名。
     *
     *  现在**没有任何一条路会写出这个文件**（没选图标时执行侧直接给空 `logo`，不经这里）。
     *  留着是因为 [nameOf] 收的是一个 String、理论上能被传空 —— 让它落到一个确定的名字上，
     *  比拼出 `okey-fluid-.png` 这种残名再去查文件好。
     */
    const val ICON_NAME = "okey-fluid.png"

    /** 渲染尺寸（模板里那个槽就按这个量级画）。 */
    const val ICON_SIZE = 36

    /**
     * 某个图标 id 对应的文件名。
     *
     *  一个 id 一个文件：选择器里点一下就把那颗渲染成 png 存下来，命令里只记 id，
     *  执行时按 id 找回文件 —— 换图标不用重渲染别的，也不会互相覆盖。
     */
    fun nameOf(icon: String): String = if (icon.isEmpty()) ICON_NAME else "okey-fluid-$icon.png"

    /**
     * 「执行时提示」走流体云时，**被执行的那个动作**的图标文件名。
     *
     *  与 `fluid:` 那一档的区别：那边是用户点选的字形（id 就存在命令里），这边的图标由
     *  **动作本身**决定（`actionCellIcon`），而 token 可能是一整条 shell 命令 —— 直接拼进
     *  文件名会越界 / 带非法字符，所以按 [String.hashCode] 取一个稳定的短名。
     *
     *  两头必须同一份：界面层按这个名字落盘（`ui/model/icon/ActionCardArt`），宿主执行时
     *  按同一个名字找回来（`host/action/notice/FluidAction`）。
     *
     *  **不能只取 `hashCode`**：32 位哈希真的会撞（撞了就是后渲染的那张图覆盖前一张，
     *  提示气泡里显示成另一个动作的图标，清理旧图时也会把两条当成一条）。所以取 token 的
     *  摘要前 16 位十六进制 —— 仍然定长、仍然只含 `[0-9a-f]`，撞的概率降到可忽略。
     */
    fun actionNameOf(token: String): String = "$ACTION_PREFIX${digestOf(token)}.png"

    /** token 的稳定短摘要（12 个十六进制字符）—— 见 [actionNameOf] 里为什么不用 `hashCode`。 */
    private fun digestOf(token: String): String =
        try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md
                .digest(token.toByteArray(Charsets.UTF_8))
                .take(DIGEST_BYTES)
                .joinToString("") { byte -> "%02x".format(byte) }
        } catch (e: Throwable) {
            //  取摘要都不成（理论上不会）：退回老的 hashCode，至少名字定长且不是空
            XLog.w("动作图标名：取摘要失败，退回 hashCode（${e.javaClass.simpleName}）")
            token.hashCode().toUInt().toString(HEX_RADIX)
        }

    /**
     * [actionNameOf] 那一族文件名共用的前缀 —— 清理已经不在配置里的旧图时靠它认出来
     * （`ui/model/icon/ActionCardArt`），别拿去认 `fluid:` 那一档。
     */
    const val ACTION_PREFIX = "okey-action-"

    /** [actionNameOf] 里那个进制的基数 —— 只是为了让那一行不出现裸数字。 */
    private const val HEX_RADIX = 16

    /** [digestOf] 取摘要的前几个字节（12 个十六进制字符）—— 够短、也够不撞。 */
    private const val DIGEST_BYTES = 6
}
