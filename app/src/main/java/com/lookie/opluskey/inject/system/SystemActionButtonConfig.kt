package com.lookie.opluskey.inject.system

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

    /** 真机确认的「场景名」列 —— 一个场景一行配置。 */
    const val COL_SCENE_NAME = "scene_name"

    val URI: Uri = Uri.parse("content://$AUTHORITY/$PATH")

    /** 一行配置：列名 → 值。列名由 Cursor 现取，不是硬编码的。 */
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

            val sceneIdx = cursor.getColumnIndex(COL_SCENE_NAME)
            if (sceneIdx < 0) {
                XLog.w("读系统侧键配置：没有 $COL_SCENE_NAME 列（列=${names.joinToString(",")}）")
                return null
            }

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
}
