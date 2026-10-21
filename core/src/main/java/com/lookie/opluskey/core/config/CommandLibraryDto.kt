package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.model.Command
import kotlinx.serialization.Serializable

/**
 * 命令库文件的**线格式模型**（`@Serializable`）。
 *
 * 与 `ConfigDto` 同一套理由：线格式要稳定（用户机器上的文件），领域模型会跟着功能演进，
 * 两者合一的话，以后往 `Command` 加字段就等于改文件格式。
 *
 * 每个字段都有默认值 ⇒ 缺字段不再是「静默变成某个合法但错误的值」（`org.json` 的
 * `optString` 会把 `null` 字面量读成字符串 `"null"`），而是走这里的默认值；类型不对则
 * 解析失败，由上层决定是「跳过这一条」还是「整份读不懂」。
 *
 * `steps` 的元素类型是 `String?`：手改文件时写成 `["a", null, "b"]` 很常见，原来那版把 null
 * 当空串丢掉；用可空类型接住它，而不是让整条命令解析失败。
 */
@Serializable
internal data class CommandLibraryDto(
    val version: Int = 0,
    val commands: List<CommandDto> = emptyList(),
)

/**
 * 一条命令。`kind` 取 `CommandKind.id`：单条用 `single`，多条用 `steps` + `stepDelayMs`。
 *
 *  条件执行**不在这里** —— 它是叠在按键绑定之上的一张顺序表（`KeyConfig.rules`），不是命令。
 *  老文件里可能还留着 `kind: "branch"` 的条目，读的时候按「认不出的类型」跳过。
 */
@Serializable
internal data class CommandDto(
    val name: String = "",
    val kind: String = "",
    val single: String = "",
    val steps: List<String?> = emptyList(),
    val stepDelayMs: Int = Command.DEFAULT_STEP_DELAY_MS,
) {
    companion object {
        fun of(c: Command): CommandDto =
            CommandDto(
                name = c.name,
                kind = c.kind.id,
                single = c.single,
                steps = c.steps,
                stepDelayMs = c.stepDelayMs,
            )
    }
}
