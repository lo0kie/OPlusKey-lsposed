package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.model.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 配置编解码的**跨版本**判据。
 *
 *  这一族最贵的一次事故就出在这里：`ConfigDto` 加一个字段、忘了抬 `ConfigCodec.VERSION`，
 *  于是「按当前 DTO 重新编码再算 md5」这一支拿新 DTO 去校旧文件 —— 每一份已有配置当场判
 *  「读不懂」，页面只读、注入侧不接管。编译期没有任何信号（`tools/audit-config-version.py`
 *  是事后闸），所以行为这一侧也得有一条。
 *
 *  三条判据：往返不丢东西 / 老版本不看 md5 / 当前版本改过正文就拒收。
 */
class ConfigCodecTest {
    private val versionLine = Regex("\"version\"\\s*:\\s*\\d+")
    private val md5Line = Regex("\"md5\"\\s*:\\s*\"[^\"]*\"")

    private fun serialized(): String = ConfigCodec.serialize(Config.DEFAULT)

    @Test
    fun roundTripKeepsTheSameConfig() {
        val text = serialized()
        assertEquals("往返之后配置变了形", Config.DEFAULT, ConfigCodec.parseOrNull(text))
    }

    @Test
    fun olderVersionIsReadWithoutTheMd5Check() {
        // 老文件重编码出来的 md5 必然对不上（DTO 变过），所以 version < VERSION 这一支**不校验**
        val text =
            serialized()
                .replace(versionLine, "\"version\": ${ConfigCodec.VERSION - 1}")
                .replace(md5Line, "\"md5\": \"00000000000000000000000000000000\"")
        assertNotNull("老版本文件被 md5 拦掉了（那一支不该校验）", ConfigCodec.parseOrNull(text))
    }

    @Test
    fun currentVersionWithTamperedBodyIsRejected() {
        // 当前版本必须逐字节对上 md5：手改正文 / 加字段忘抬版本，都走这一支。
        //  md5 是「按当前 DTO 重新编码」算的，所以只有**改一个取值**才算改正文 ——
        //  改空白、改键序都会被重编码抹平（那是设计如此：md5 认的是语义，不是字节）。
        val text = serialized()
        val tampered = text.replace(Regex("\"language\"\\s*:\\s*\"auto\""), "\"language\": \"en\"")
        assertNotEquals("这一条测试自己没改到正文（线格式的字段名变了？）", text, tampered)
        assertNull("当前版本改过正文却仍然解析成功 —— md5 那一支没起作用", ConfigCodec.parseOrNull(tampered))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(ConfigCodec.parseOrNull(""))
        assertNull(ConfigCodec.parseOrNull("{"))
        assertNull(ConfigCodec.parseOrNull("null"))
    }
}
