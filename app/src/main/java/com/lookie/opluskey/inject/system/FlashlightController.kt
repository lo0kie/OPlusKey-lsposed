package com.lookie.opluskey.inject.system

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import com.lookie.opluskey.core.log.XLog

/** 手电筒开关（`flashlight:`）：语义是切换（开着的关掉、关着的打开）。 */
internal class FlashlightController(
    private val context: () -> Context?,
    /** 注册 torch 回调用的 Handler（回调要在有 Looper 的线程上）。 */
    private val handler: Handler,
) {
    /** 当前手电筒是否亮着（靠 torch 回调跟踪，不猜）。 */
    @Volatile
    private var torchOn = false

    /** 带闪光灯那颗摄像头的 id（回调第一次上报时记下）。 */
    @Volatile
    private var torchCameraId: String? = null

    @Volatile
    private var torchCallbackRegistered = false

    /** 切换一次手电筒。 */
    fun toggle() {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，flashlight: 跳过")
                return
            }
        val cm =
            try {
                ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            } catch (e: Throwable) {
                XLog.w("取 CameraManager 失败", e)
                null
            }
        if (cm == null) {
            XLog.w("拿不到 CameraManager，flashlight: 跳过")
            return
        }

        registerTorchCallbackOnce(cm)

        if (torchCameraId != null) {
            // 回调落地过 ⇒ torchOn 是系统上报的真值，直接切。
            applyToggle(cm)
            return
        }
        // 回调还没落地（首次切换）：`registerTorchCallback` 会立刻以当前状态回调一次，
        // 但那一次排在 handler 队列里，本帧读到的仍是初值 false —— 手电已经开着时
        // 第一下就按成了「继续开」。把切换排到它后面，读到的就是真状态。
        if (!handler.post { applyToggle(cm) }) {
            XLog.w("手电筒：状态回调还没落地且排不进队列，这次按下的状态可能反（仍会尝试切换）")
            applyToggle(cm)
        }
    }

    /** 真正那一下切换 —— 调用时 `torchOn` 必须是可信值（见 [toggle] 的排序说明）。 */
    private fun applyToggle(cm: CameraManager) {
        val id = torchCameraId ?: firstTorchCamera(cm)
        if (id == null) {
            XLog.w("找不到带闪光灯的摄像头，flashlight: 跳过")
            return
        }
        val target = !torchOn
        try {
            cm.setTorchMode(id, target)
            torchOn = target
            XLog.w("手电筒 → ${if (target) "开" else "关"}（camera=$id）")
        } catch (e: Throwable) {
            XLog.w("切换手电筒失败（相机被占用时 torch 不可用，见异常）", e)
        }
    }

    /** 注册一次 torch 回调（注册成功才记标记；失败下次切换时重试，只是状态可能不准）。 */
    private fun registerTorchCallbackOnce(cm: CameraManager) {
        if (torchCallbackRegistered) return
        try {
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(
                        cameraId: String,
                        enabled: Boolean,
                    ) {
                        //  一台机器可能有多颗带闪光灯的摄像头（主摄 / 超广 / 长焦），注册时会**逐个**回调
                        // 一次，于是「最后回调的那颗」会决定 `torchCameraId` —— 若它恰好不是正在亮的那颗，
                        // 下一次 `applyToggle` 就会去切一颗没亮的镜头（原状态读反、表现是按一下没反应）。
                        //  规矩：**亮着的那颗优先**（`enabled == true` 时认它）；都没亮时才按回调次序记一颗当兜底。
                        if (enabled) {
                            torchCameraId = cameraId
                            torchOn = true
                        } else if (torchCameraId == null) {
                            // 还没认过任何一颗、这一颗也没亮 —— 只是给 `firstTorchCamera` 之外留个候选，
                            //  **不改 `torchOn`**：多颗里绝大多数回调是 `false`，跟着它走会把「亮着」覆盖成「灭」。
                            torchCameraId = cameraId
                        }
                    }
                },
                handler,
            )
            // 标记必须写在成功之后：写在 try 之前的话，一次失败就永不重试，
            // torchOn 与真实状态会长期相反。
            torchCallbackRegistered = true
            XLog.w("已注册 torch 回调（手电筒状态由系统上报）")
        } catch (e: Throwable) {
            XLog.w("注册 torch 回调失败（状态可能不准，仍会尝试切换）", e)
        }
    }

    /** 兜底：自己找一颗带闪光灯的摄像头。 */
    private fun firstTorchCamera(cm: CameraManager): String? =
        try {
            cm.cameraIdList.firstOrNull { id ->
                cm
                    .getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Throwable) {
            XLog.w("枚举摄像头失败", e)
            null
        }
}
