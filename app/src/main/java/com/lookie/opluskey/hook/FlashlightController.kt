package com.lookie.opluskey.hook

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import com.lookie.opluskey.XLog

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
        val ctx = context() ?: run {
            XLog.w("没有 Context，flashlight: 跳过")
            return
        }
        val cm = try {
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

        val id = torchCameraId ?: firstTorchCamera(cm)
        if (id == null) {
            XLog.w("找不到带闪光灯的摄像头，flashlight: 跳过")
            return
        }

        val target = !torchOn
        try {
            cm.setTorchMode(id, target)
            torchOn = target
            XLog.i("手电筒 → ${if (target) "开" else "关"}（camera=$id）")
        } catch (e: Throwable) {
            XLog.w("切换手电筒失败（相机被占用时 torch 不可用，见异常）", e)
        }
    }

    /** 注册一次 torch 回调（只注册一次；失败不阻断切换，只是状态可能不准）。 */
    private fun registerTorchCallbackOnce(cm: CameraManager) {
        if (torchCallbackRegistered) return
        torchCallbackRegistered = true
        try {
            cm.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        torchOn = enabled
                        torchCameraId = cameraId
                    }
                },
                handler,
            )
            XLog.i("已注册 torch 回调（手电筒状态由系统上报）")
        } catch (e: Throwable) {
            XLog.w("注册 torch 回调失败（状态可能不准，仍会尝试切换）", e)
        }
    }

    /** 兜底：自己找一颗带闪光灯的摄像头。 */
    private fun firstTorchCamera(cm: CameraManager): String? = try {
        cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Throwable) {
        XLog.w("枚举摄像头失败", e)
        null
    }
}
