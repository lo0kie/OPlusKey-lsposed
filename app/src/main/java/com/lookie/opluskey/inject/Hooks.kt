package com.lookie.opluskey.inject

import com.lookie.opluskey.core.log.XLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/** Hook 安装的统一收口，失败只记日志、逐项降级。 */
fun XposedModule.hookGuarded(
    origin: Executable?,
    hooker: XposedInterface.Hooker,
): XposedInterface.HookHandle? {
    if (origin == null) {
        XLog.w("Hook 目标为 null，本次跳过（调用方应已打过更具体的原因）")
        return null
    }
    return try {
        hook(origin)
            // hooker 运行期抛异常时由框架拦下（PASSTHROUGH 会往外抛、DEFAULT 语义不设防）——
            // 这是「失败只降级、不拖垮宿主进程 / system_server」的最后兜底，8 个 hook 点都走这一处。
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(hooker)
    } catch (e: Throwable) {
        // Throwable 而非 Exception：框架侧失败有可能是 Error 系，一并吞掉只降级不扩散
        XLog.w("装 Hook 失败（已跳过，后续 Hook 继续）：${origin.declaringClass.name}#${origin.name}", e)
        null
    }
}
