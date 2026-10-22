package com.lookie.opluskey.bridge.host

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.lookie.opluskey.core.log.XLog

/** 系统侧键配置（ColorOS「设置 → 快捷键」那一套）的只读访问。 */
internal object SystemActionButtonConfig {
    /** 真机确认的 authority + path。 */
    private const val AUTHORITY = "uri.gesturemanagerprovider"
    private const val PATH = "action_button"

    /**
     * 「场景名」列的**首选**名字 —— 不是唯一判据：列名随 ROM 版本变过，真名一律从 Cursor 现取
     * （见 [sceneColumnIndex]）。写死一个名字，ROM 一改就会静默变成「读取失败」。
     */
    private const val COL_SCENE_NAME = "scene_name"

    val URI: Uri = Uri.parse("content://$AUTHORITY/$PATH")

    /**
     * 一行配置：列名 → 值。列名由 Cursor 现取，不是硬编码的。
     */
    data class Row(
        val sceneName: String,
        val values: Map<String, String?>,
    )

    /**
     * 读全部行。只在宿主进程里调 —— `ContentResolver` 在 system_server 里必 NPE。
     *
     * @return 读成功返回列表（可能为空 = 没配置）；读失败返回 `null`
     *         —— 调用方要区分「没有配置」与「读不到」。
     */
    fun readAll(context: Context): List<Row>? {
        val resolver: ContentResolver =
            try {
                context.contentResolver
            } catch (e: Throwable) {
                XLog.w("取 ContentResolver 失败：${e.message}")
                return null
            }

        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(URI, null, null, null, null)
            if (cursor == null) {
                XLog.w("读系统侧键配置：query 返回 null（$AUTHORITY）")
                return null
            }

            val names = cursor.columnNames
            if (names.isEmpty()) {
                XLog.w("读系统侧键配置：Cursor 没有列 —— 表结构与预期不符")
                return null
            }
            XLog.d("系统侧键配置的列：${names.joinToString(", ")}")

            val sceneIdx = sceneColumnIndex(names)
            if (sceneIdx < 0) {
                XLog.w("读系统侧键配置：找不到「场景名」列（列=${names.joinToString(",")}）")
                return null
            }
            XLog.d("系统侧键配置的场景列：${names[sceneIdx]}")

            val out = ArrayList<Row>()
            while (cursor.moveToNext()) {
                val row = HashMap<String, String?>()
                for (i in names.indices) {
                    row[names[i]] =
                        try {
                            cursor.getString(i)
                        } catch (e: Throwable) {
                            // 非字符串列（BLOB / NULL）取不到就当空，不影响其它列
                            null
                        }
                }
                out +=
                    Row(
                        sceneName = cursor.getString(sceneIdx).orEmpty(),
                        values = row,
                    )
            }
            XLog.w("读系统侧键配置成功：${out.size} 行")
            out
        } catch (e: Throwable) {
            XLog.w("读系统侧键配置失败（降级为「不知道」）：$URI", e)
            null
        } finally {
            try {
                cursor?.close()
            } catch (e: Throwable) {
                XLog.d("关 Cursor 失败：${e.message}")
            }
        }
    }

    /**
     * 找出「场景名」那一列 —— 列名从 Cursor 现取，**不靠写死的那一个名字**。
     *
     *  1) 先认首选名（[COL_SCENE_NAME]）；
     *  2) 认不出就退到「名字里同时含 scene 与 name」的那一列，**必须唯一**才用 ——
     *     多于一个说明表结构变了，猜错会让诊断项读出别的列，宁可按「读不到」报。
     *
     * @return 列下标；`-1` = 认不出（调用方按「读失败」处理并打出全部列名）
     */
    private fun sceneColumnIndex(names: Array<String>): Int {
        val exact = names.indexOfFirst { it.equals(COL_SCENE_NAME, ignoreCase = true) }
        if (exact >= 0) return exact
        val loose = names.indices.filter { i -> names[i].contains("scene", true) && names[i].contains("name", true) }
        return if (loose.size == 1) loose.first() else -1
    }
}
