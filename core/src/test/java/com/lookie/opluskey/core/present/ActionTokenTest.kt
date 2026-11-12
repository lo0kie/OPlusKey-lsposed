package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动作字符串的**往返**判据。
 *
 *  这一族的错误都是静默的：`Action.parse` 认不出前缀时按 `None` 处理（不执行、只留一行日志），
 *  所以一条写坏的动作在界面上照样能选中、能保存，按下去什么也不发生；而界面显示的名字来自
 *  [ActionCatalog] 那一份清单、执行看的是 `token` —— 两份只要有一条对不上，界面上写「手电筒」、
 *  按下去却是别的动作，同样不报错。
 *
 *  下面两张名单是**有意**分开的：清单里除了真正的动作，还有一批「只作菜单 / 格子标记」的 token
 *  （`nav:` 开系统导航菜单、`lock:` 开锁屏菜单那一类）—— 它们本来就不该能执行，也不该被写进配置。
 *  名单写死在这里，等于把「哪些是标记、哪些是动作」这个约定变成一条会红的判据：新加一个标记
 *  却没登记，测试当场失败。
 */
class ActionTokenTest {
    /** 清单里只作标记、解析不出动作的 token。加一个标记要**同时**改这里。 */
    private val menuOnly =
        setOf(
            "nav:",
            "key:",
            "lock:",
            "zoom:",
            "appctl:",
            "ai:",
            "ai:recording",
            "ai:screen_record",
            // 「屏幕翻译」那一格：点它弹「打开 / 关闭」两行单选，落成 `oplus:screen_translate`
            //  与 `oplus:screen_translate_stop`。标记本身与上面那两个 `ai:` 同类，永不写进配置。
            "ai:screen_translate",
        )

    /**
     * 能解析、但 `token` 会被归一成别的一档（组的表头那一行：`bright:` 进「亮度」菜单）。
     *
     *  `volume:` / `ringervolume:` 是音量那两族的**标记格**：它们只用来弹「增加 / 减少 / 设置为」
     *  三行，本身不落盘，解析出来是 `none` —— 与 `bright:` 同一档。
     */
    private val notRoundTrip = menuOnly + setOf("bright:", "volume:", "ringervolume:")

    private val tokens = ActionCatalog.ALL.map { it.token }.distinct()

    @Test
    fun catalogTokensArePartitionedTheWayTheParserSeesThem() {
        assertEquals("这两个 token 过不了「命令详情」的格式闸（选得中、存不下）", menuOnly, tokens.filterNot { Action.hasSupportedFormat(it) }.toSet())
        assertEquals("解析器与清单漂移了（界面显示的动作 ≠ 实际执行的动作）", notRoundTrip, tokens.filter { Action.parse(it).token != it }.toSet())
    }

    @Test
    fun everyActionTokenSurvivesARoundTrip() {
        val actions = tokens.filterNot { it in notRoundTrip }
        val broken = actions.filter { Action.parse(it).token != it }
        assertEquals("这些动作 token 往返之后变了形", emptyList<String>(), broken)
    }

    @Test
    fun followIsNamedInsteadOfItsRawToken() {
        //  「跟随默认」只在 `BRANCH_GROUPS` 里（不在 `ALL`），而「这个动作叫什么」走的是那张扁平表 ——
        //  漏了它，回执就退回原始 token（「已绑定「follow」」，真机反馈）。
        //  单测里没有字典，`label` 给的是**键本身**；所以这里判「查到了哪条键」，
        //  而不是判中文那四个字。
        assertEquals(
            "「跟随默认」必须念得出名字，不能退回 token",
            Strings.COMMAND_BRANCH_ACTION_FOLLOW,
            ActionCatalog.label(Action.Follow),
        )
        assertNotEquals("follow", ActionCatalog.label(Action.Follow))
    }

    @Test
    fun unsupportedTextFallsBackToNoneInsteadOfShell() {
        // 历史写法（裸命令）静默不执行 —— 宁可不跑，也不把一段不知名的字符串交给 shell
        assertTrue(Action.parse("am start -n a/b") is Action.None)
        assertTrue(Action.parse("") is Action.None)
    }

    @Test
    fun prefixedTokensKeepTheirOwnType() {
        // 这四档都曾经会掉进 shell 兜底（`cmd:` / `window:` / `zoom:` 是前缀顺序问题，`setting:` 同理）
        assertTrue(Action.parse("cmd:重启网络") is Action.Custom)
        assertTrue(Action.parse("window:com.example") is Action.AppWindow)
        assertTrue(Action.parse("zoom:mini") is Action.MiniWindow)
        assertTrue(Action.parse("setting:bt") is Action.Setting)
    }

    @Test
    fun emptyPayloadNeverBecomesRunnable() {
        // 只有前缀、载荷为空：要么解析成 `None`，要么解析成一个「空壳」动作（`isBarePrefix`）。
        //  两种都不可执行，也都不该让保存按钮亮起来 —— 判据是「不许变成一条能跑的东西」。
        val bare = listOf("cmd:", "app:", "window:", "shell:", "intent:", "setvar:", "nosuchprefix:")
        val runnable =
            bare.filter { token ->
                val action = Action.parse(token)
                action !is Action.None && !action.isBarePrefix
            }
        assertEquals("这些空载荷写法解析成了可执行动作", emptyList<String>(), runnable)
    }

    @Test
    fun nonEmptyPayloadIsAlwaysRunnable() {
        // 反向判据：载荷非空时必须解析成真动作（否则界面能存、执行侧什么都不做）
        assertNotEquals(Action.None, Action.parse("cmd:ok"))
        assertTrue(!Action.parse("cmd:ok").isBarePrefix)
    }
}
