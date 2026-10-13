package com.lookie.opluskey

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
        hook(origin).intercept(hooker)
    } catch (e: Throwable) {
        // Throwable 而非 Exception：框架侧失败有可能是 Error 系，一并吞掉只降级不扩散
        XLog.w("装 Hook 失败（已跳过，后续 Hook 继续）：${origin.declaringClass.name}#${origin.name}", e)
        null
    }
}
