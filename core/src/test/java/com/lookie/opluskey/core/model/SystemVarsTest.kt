package com.lookie.opluskey.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 系统状态变量（`${sys.xxx}`）的**纯规则**判据。
 *
 *  这一族错起来都是静默的：名字翻译不出来 ⇒ 那个 `${sys.xxx}` 按原文留在命令里，
 *  界面照常保存、执行侧照常跑，只是流体云 / 通知里多出一段没替换的占位符。
 *  所以把「名字 ⇄ 条件档位」这一层钉成会红的判据。
 */
class SystemVarsTest {
    @Test
    fun everyKindThatSaysWhatItIsNowHasAVariableName() {
        // 只有前台应用那一族说不清「此刻是什么」（取值是一组包名）；其余都该有名字 ——
        // 有候选取值的那几档报「命中哪一档」，时间区间报此刻的时刻
        ConditionKind.entries.forEach { kind ->
            if (kind.isAppFamily) {
                assertNull("${kind.id} 不该有变量名", kind.systemVarName)
            } else {
                assertEquals("sys.${kind.id}", kind.systemVarName)
                assertEquals("\${sys.${kind.id}}", kind.systemVarRef)
            }
        }
    }

    @Test
    fun kindListMatchesTheSceneConditionList() {
        assertEquals(ConditionKind.entries.filter { it.systemVarName != null }, SYSTEM_VAR_KINDS)
        assertTrue("前台应用不该进这张表", ConditionKind.APP !in SYSTEM_VAR_KINDS)
        assertTrue("前台应用以外不该进这张表", ConditionKind.APP_NOT !in SYSTEM_VAR_KINDS)
        assertTrue("时间区间该进这张表（值是此刻的时刻）", ConditionKind.TIME in SYSTEM_VAR_KINDS)
    }

    @Test
    fun nameParsesBackToItsKind() {
        SYSTEM_VAR_KINDS.forEach { kind ->
            assertEquals(kind, systemVarKind(kind.systemVarName.orEmpty()))
        }
        // 认不出的写法一律返回 null（调用方据此把原文留着）
        listOf("ringer", "", "sys.", "sys.app", "sys.nosuch", "SYSTEM.ringer").forEach {
            assertNull("「$it」不该认成一档状态", systemVarKind(it))
        }
    }

    @Test
    fun textSuffixSelectsTheLabelForm() {
        // 同一档的两种写法：不带后缀要输出值（`silent`），带 `.text` 要界面上那句文案（「静音」）
        assertEquals(SystemVar(ConditionKind.RINGER), systemVarOf("sys.ringer"))
        assertEquals(SystemVar(ConditionKind.RINGER, asText = true), systemVarOf("sys.ringer.text"))
        assertEquals("sys.ringer", ConditionKind.RINGER.systemVarName)
        assertEquals("\${sys.ringer.text}", ConditionKind.RINGER.systemVarTextRef)
        // `.text` 不是档位名，也不许给「没有当前值」的档当后缀
        assertNull(systemVarOf("sys.text"))
        assertNull(systemVarOf("sys.app.text"))
        assertNull(systemVarOf("sys.nosuch.text"))
        // 「这是哪一档」不看形态（名单那边只关心档位）
        assertEquals(ConditionKind.RINGER, systemVarKind("sys.ringer.text"))
    }

    @Test
    fun timeValueIsTheClockText() {
        // `${sys.time}` 念的是「当天第几分钟 → HH:mm」：补零、长度恒定，跨午夜那一档的判定也用它
        assertEquals("00:00", TimeRange.clock(0))
        assertEquals("09:05", TimeRange.clock(9 * 60 + 5))
        assertEquals("12:30", TimeRange.clock(750))
        assertEquals("23:59", TimeRange.clock(1439))
    }

    @Test
    fun expansionAsksTheFallbackOnlyWhenTheUserTableMisses() {
        val vars = Vars(listOf(Variable("计数", VarType.STRING, "3")))
        val asked = mutableListOf<String>()
        val text =
            vars.expand("是\${计数}，屏幕\${sys.screen}，还有\${没有这个}") { name ->
                asked += name
                if (name == "sys.screen") "on" else null
            }
        assertEquals("是3，屏幕on，还有\${没有这个}", text)
        //  用户表里有的名字不该惊动 fallback（每按一次键都会走这一趟）
        assertEquals(listOf("sys.screen", "没有这个"), asked)
    }

    @Test
    fun userVariablesWinOverSameNamedSystemOnes() {
        // `sys.` 只是约定，不是保留字：用户真建了同名变量时，用户那一颗赢（见 SYSTEM_VAR_PREFIX 的说明）
        val vars = Vars(listOf(Variable("sys.ringer", VarType.STRING, "mine")))
        assertEquals("mine", vars.expand("\${sys.ringer}") { "silent" })
    }

    @Test
    fun textPayloadsExpandInNotificationAndFluidCloud() {
        // 「用流体云弹一下现在的响铃状态」正是这两档：正文原先不展开，界面上完全看不出来
        val vars = Vars()
        val fallback: (String) -> String? = { if (it == "sys.ringer") "silent" else null }
        assertEquals(Action.Fluid("现在是silent"), Action.Fluid("现在是\${sys.ringer}").expandedWith(vars, fallback))
        assertEquals(Action.Notify("现在是silent"), Action.Notify("现在是\${sys.ringer}").expandedWith(vars, fallback))
    }

    @Test
    fun shellTextIsLeftForItsSingleFunnel() {
        // `shell:` 的展开点沉到 `ActionRunner.runShell`（三条进来的路只有一条经过这里）——
        // 这里再展开一次的话，值里带 `${…}` 就会被展开第二遍
        assertEquals(Action.Shell("echo \${sys.ringer}"), Action.Shell("echo \${sys.ringer}").expandedWith(Vars()))
    }
}
