package com.lookie.opluskey.core

/**
 * 按构建类型各放一份的编译期常量（`releaseLog` 版）。
 *
 * ## 这一档是什么
 *
 *  与 `release` **只差日志**：混淆 / 资源收缩 / 正式签名 / 资产封存 / 不可调试，全部照旧；
 *  唯一放开的是 [VERBOSE_LOG] —— 于是 `XLog.d` / `XLog.i` 那一批详细日志重新进包。
 *  给「用户报了个毛病、手上只有正式包」那一类排查用：行为与发布版一致，日志是完整的。
 *
 * ## 为什么单开一档，而不是给 release 加个开关
 *
 *  详细日志是**编译期折掉**的（`inline` + `if (VERBOSE_LOG)`，见 `XLog`），不是运行期开关 ——
 *  要在发布形态里拿到它，只能换一份 `BuildFlags`。源集物理隔离（`src/releaseLog/`）保证
 *  这两档各自折各自的，正式包里既没有那段代码、也没有那个开关可被拨开。
 *
 * ## 与 debug 的差别（这几条**不能**跟着 debug 放开）
 *
 *  [DEBUG_PROBE] 必须 false：它等于把「任意反射执行」开在 system_server 里，只在 debug 包成立。
 *  [STRICT_UI] 保持 false：那是开发期自检，给用户排查的包不该因为界面契约缺一格就崩。
 */
const val VERBOSE_LOG: Boolean = true

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种（`1.0.0-log`）。 */
const val BUILD_KIND: String = "log"

/**
 * 首页大标题那条副标题要不要带上构建时刻 —— 打开。
 *
 *  这一档的包也是「会同时存在好几个」的（同一版可能为不同问题各出一个），
 *  不带时刻就分不清手上是哪一个。
 */
const val SHOW_BUILD_TIME: Boolean = true

/** 模块 debug 通道的总闸 —— 见上面那段：这一档也不开。 */
const val DEBUG_PROBE: Boolean = false

/** 开发期把「界面契约写错」直接抛出来 —— 这一档不开（理由见上面）。 */
const val STRICT_UI: Boolean = false
