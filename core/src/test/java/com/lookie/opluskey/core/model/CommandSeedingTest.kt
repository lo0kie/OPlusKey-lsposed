package com.lookie.opluskey.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 预设命令补进命令库的**纯规则**判据（`mergeCommandSeeds`）。
 *
 *  补错的表现都很安静：重复一条同正文的命令、覆盖掉用户自己起的同名命令、或者把第 51 条
 *  硬塞进已经到上限的库里 —— 界面上都不会报错，只有用户发现列表里多了一条不是他建的。
 */
class CommandSeedingTest {
    private fun cmd(
        name: String,
        body: String = "shell:echo $name",
    ): Command = Command(name = name, kind = CommandKind.SINGLE, single = body)

    @Test
    fun emptyLibraryTakesEverySeed() {
        val seeds = listOf(cmd("微信扫一扫"), cmd("支付宝扫一扫"))
        assertEquals(seeds, mergeCommandSeeds(emptyList(), seeds))
    }

    @Test
    fun existingCommandsKeepTheirOrderAndSeedsGoLast() {
        val mine = cmd("我自己")
        val seed = cmd("预设")
        assertEquals(listOf(mine, seed), mergeCommandSeeds(listOf(mine), listOf(seed)))
    }

    @Test
    fun sameBodyIsNotAddedTwice() {
        val mine = cmd("我自己起的名字", "shell:am start -n com.tencent.mm/.plugin.scanner.ui.BaseScanUI")
        val seed = cmd("微信扫一扫", "shell:am start -n com.tencent.mm/.plugin.scanner.ui.BaseScanUI")
        assertEquals(listOf(mine), mergeCommandSeeds(listOf(mine), listOf(seed)))
    }

    @Test
    fun sameNameWithAnotherBodyIsNotOverwritten() {
        val mine = cmd("微信扫一扫", "shell:echo mine")
        val seed = cmd("微信扫一扫", "shell:echo seed")
        assertEquals(listOf(mine), mergeCommandSeeds(listOf(mine), listOf(seed)))
    }

    @Test
    fun singleCommandLimitIsRespected() {
        val full = (1..Command.MAX_COMMANDS).map { cmd("已有 $it") }
        val merged = mergeCommandSeeds(full, listOf(cmd("预设")))
        assertEquals(Command.MAX_COMMANDS, merged.size)
        assertEquals(full, merged)
    }

    @Test
    fun serialCommandsDoNotConsumeTheSingleLimit() {
        val serial =
            (1..Command.MAX_COMMANDS).map {
                Command(name = "多条 $it", kind = CommandKind.SERIAL, steps = listOf("keyevent:3"))
            }
        val seed = cmd("预设")
        assertEquals(serial + seed, mergeCommandSeeds(serial, listOf(seed)))
    }

    @Test
    fun blankSeedsAreIgnored() {
        val seed = Command(name = "", kind = CommandKind.SINGLE, single = "")
        assertEquals(emptyList<Command>(), mergeCommandSeeds(emptyList(), listOf(seed)))
    }
}
