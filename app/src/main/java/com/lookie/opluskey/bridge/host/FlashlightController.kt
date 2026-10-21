package com.lookie.opluskey.bridge.host

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.provider.Settings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.RomProfile

/** 手电筒开关（`flashlight:`）：语义是切换（开着的关掉、关着的打开）。 */
internal class FlashlightController(
    private val context: () -> Context?,
    /** 注册 torch 回调用的 Handler（回调要在有 Looper 的线程上）。 */
    private val handler: Handler,
    /** 「使用 ColorOS 接口」这一档由装配处传入（见 `ActionRunner`）—— 本类不自己去读配置。 */
    private val isColorOsApi: () -> Boolean,
) {
    /** 当前手电筒是否亮着（靠 torch 回调跟踪，不猜）。 */
    @Volatile
    private var torchOn = false

    /** 带闪光灯那颗摄像头的 id（回调第一次上报时记下）。 */
    @Volatile
    private var torchCameraId: String? = null

    @Volatile
    private var torchCallbackRegistered = false

    /** 注册上去的那个回调实例 —— 注销时必须拿同一个对象（`unregisterTorchCallback` 按引用比）。 */
    @Volatile
    private var torchCallback: CameraManager.TorchCallback? = null

    /** 切换一次手电筒。 */
    fun toggle() {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，flashlight: 跳过")
                return
            }

        val viaColorOs = isColorOsApi()
        XLog.w("手电筒：接口档=${if (viaColorOs) "ColorOS" else "AOSP"}")
        if (viaColorOs) {
            //  `onFail` 必须给：**受理 ≠ 连上**。服务 3 秒都没连上时请求根本没送出去，
            //  这时不让 CameraManager 兜底，用户看到的就是「按下去什么都没发生」。
            //
            //  回退那一刻**重新读一次** ColorOS 真值：走到这里说明服务没连上，而「未受理」那条
            //  路上读的那一次**根本没发生**（受理时直接 return 了）—— 闭包里捕获的初值 `false`
            //  不是真值，拿它去判「现在是开还是关」就会把开着的再开一次。
            val accepted =
                tryToggleViaColorOsService(ctx) {
                    toggleViaCamera(ctx, viaColorOs, colorOsStateKnown = syncStateFromSettings(ctx))
                }
            if (accepted) {
                XLog.w("手电筒：已交 ColorOS 服务（连不上时由 onFail 退 CameraManager）")
                return
            }
            // ColorOS 专属服务未成功，从 Settings.Secure 同步当前真值后再切
            toggleViaCamera(ctx, viaColorOs, colorOsStateKnown = syncStateFromSettings(ctx))
            return
        }
        toggleViaCamera(ctx, viaColorOs, colorOsStateKnown = false)
    }

    /** 取 `CameraManager` —— 取不到就记一行、返回 `null`（调用方直接收工）。 */
    private fun cameraManager(ctx: Context): CameraManager? {
        val cm =
            try {
                ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            } catch (e: Throwable) {
                XLog.w("取 CameraManager 失败", e)
                null
            }
        if (cm == null) XLog.w("拿不到 CameraManager，手电筒跳过")
        return cm
    }

    /**
     * 明确开到 / 关到 —— 三态里「打开 / 关闭」那两档。
     *
     *  **不走 ColorOS 那条服务**：它只有「切一下」（`what=2`），表达不了目标状态。
     *  `CameraManager.setTorchMode(id, on)` 是系统真开关（传什么就是什么），明确档直接用它；
     *  切不动时 `torchOn` 不跟着改 —— 状态以回调 / 读回来的为准。
     */
    fun setEnabled(on: Boolean) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，手电筒跳过")
                return
            }
        val cm = cameraManager(ctx) ?: return
        registerTorchCallbackOnce(cm)
        val id =
            torchCameraId ?: firstTorchCamera(cm) ?: run {
                XLog.w("找不到带闪光灯的摄像头，手电筒跳过")
                return
            }
        try {
            cm.setTorchMode(id, on)
            torchOn = on
            XLog.w("手电筒 → ${if (on) "开" else "关"}（camera=$id）")
        } catch (e: Throwable) {
            XLog.w("设置手电筒失败（相机被占用时 torch 不可用，见异常）", e)
        }
    }

    /** AOSP（`CameraManager.setTorchMode`）那一半 —— ColorOS 那条没接住时它就是兜底，所以独立成一段。 */
    private fun toggleViaCamera(
        ctx: Context,
        viaColorOs: Boolean,
        colorOsStateKnown: Boolean,
    ) {
        val cm = cameraManager(ctx) ?: return

        registerTorchCallbackOnce(cm)

        if (torchCameraId != null || colorOsStateKnown) {
            // 回调落地过，或者已从 ColorOS Settings 读到真值 ⇒ 直接切。
            applyToggle(cm, viaColorOs)
            return
        }
        // 回调还没落地（首次切换）：`registerTorchCallback` 会立刻以当前状态回调一次，
        // 但那一次排在 handler 队列里，本帧读到的仍是初值 false —— 手电已经开着时
        // 第一下就按成了「继续开」。把切换排到它后面，读到的就是真状态。
        if (!handler.post { applyToggle(cm, viaColorOs) }) {
            XLog.w("手电筒：状态回调还没落地且排不进队列，这次按下的状态可能反（仍会尝试切换）")
            applyToggle(cm, viaColorOs)
        }
    }

    /**
     * 通过 ColorOS 原生侧键服务（`RomProfile.NAME_KEY_FLASHLIGHT`，`what=2`）切换手电筒。
     *
     * 发消息这一步交给 [BuiltInMessenger] —— 侧键场景动作那条用的也是它，边界处理（超时 /
     * bind 被拒 / 失败上报）只该有一份。这里**只借用通道**：失败不报 `CommandFailure`，
     * 因为还有 CameraManager 那条兜底 —— 所以 [onFail] **必须传**，否则「受理但 3 秒没连上」
     * 这种最常见的失败就没人接手。
     *
     * @return true = 请求已受理（**不等于手电筒真的动了** —— 连接是异步的）
     */
    private fun tryToggleViaColorOsService(
        ctx: Context,
        onFail: () -> Unit,
    ): Boolean {
        val target =
            RomProfile.builtIn(RomProfile.NAME_KEY_FLASHLIGHT) ?: run {
                // 本机 ROM 档位表里没有这一条（或 release 包在本机没适配）—— 说出来，
                //  否则「为什么关了 ColorOS 接口还是一样」在日志里没有依据
                XLog.w("手电筒：本机 ROM 档位里没有 ${RomProfile.NAME_KEY_FLASHLIGHT} 的落点，走 CameraManager")
                return false
            }
        val accepted = BuiltInMessenger.send(ctx, target, "手电筒（ColorOS 接口）", onFail)
        if (!accepted) XLog.w("手电筒：ColorOS 服务那条没受理，退 CameraManager")
        return accepted
    }

    /**
     * 从 ColorOS 的 `Settings.Secure` 读手电筒状态；读到就写进 [torchOn]。
     *
     * 同一个键原先被两个函数各读一次（一个写状态、一个只判「读到没」）—— 同一件事的两条路径，
     * 合并成「读一次、把读到了没有返回出去」：ColorOS 那条失败后调用一次，结果留给后面那个判断复用。
     *
     * @return 读到了没有。`false` = 这台机器不写这个键，**不能**拿它当「手电筒关着」
     */
    private fun syncStateFromSettings(ctx: Context): Boolean {
        val enabled =
            runCatching {
                Settings.Secure.getInt(ctx.contentResolver, FLASHLIGHT_ENABLED_KEY, -1)
            }.getOrDefault(-1)
        if (enabled == -1) {
            XLog.w("手电筒：读不到 Settings.Secure.$FLASHLIGHT_ENABLED_KEY，按 torch 回调推状态")
            return false
        }
        torchOn = (enabled == 1)
        XLog.w("手电筒：从 Settings.Secure.$FLASHLIGHT_ENABLED_KEY 读到状态=${if (enabled == 1) "开" else "关"}")
        return true
    }

    /** 真正那一下切换 —— 调用时 `torchOn` 必须是可信值（见 [toggle] 的排序说明）。 */
    private fun applyToggle(
        cm: CameraManager,
        viaColorOs: Boolean,
    ) {
        val id = torchCameraId ?: firstTorchCamera(cm)
        if (id == null) {
            XLog.w("找不到带闪光灯的摄像头，flashlight: 跳过")
            return
        }
        val target = !torchOn
        try {
            cm.setTorchMode(id, target)
            torchOn = target
            val mode = if (viaColorOs) "ColorOS" else "AOSP"
            XLog.w("手电筒（$mode 接口）→ ${if (target) "开" else "关"}（camera=$id）")
        } catch (e: Throwable) {
            XLog.w("切换手电筒失败（相机被占用时 torch 不可用，见异常）", e)
        }
    }

    /** 注册一次 torch 回调（注册成功才记标记；失败下次切换时重试，只是状态可能不准）。 */
    private fun registerTorchCallbackOnce(cm: CameraManager) {
        if (torchCallbackRegistered) return
        try {
            val callback =
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(
                        cameraId: String,
                        enabled: Boolean,
                    ) {
                        //  一台机器可能有多颗带闪光灯的摄像头（主摄 / 超广 / 长焦），注册时会**逐个**回调
                        // 一次，于是「最后回调的那颗」会决定 `torchCameraId` —— 若它恰好不是正在亮的那颗，
                        // 下一次 `applyToggle` 就会去切一颗没亮的镜头（原状态读反、表现是按一下没反应）。
                        //  规矩：**认下的那颗状态是权威**，只有它变了才改 `torchOn`；其余镜头只在「亮了」时顶替。
                        if (cameraId == torchCameraId) {
                            //  用户从控制中心 / 锁屏磁贴 / 别的应用关灯时走的也是这里。不跟随 `enabled`
                            //  的话 `torchOn` 会一直停在 true，下一次按侧键就成了「去关一个已经关着的
                            //  手电筒」—— 表现为按一下没反应，得连按两次。
                            torchOn = enabled
                        } else if (enabled) {
                            //  另一颗亮了：以它为准（亮的优先于回调次序）。
                            torchCameraId = cameraId
                            torchOn = true
                        } else if (torchCameraId == null) {
                            // 还没认过任何一颗、这一颗也没亮 —— 只是给 `firstTorchCamera` 之外留个候选，
                            //  **不改 `torchOn`**：多颗里绝大多数回调是 `false`，跟着它走会把「亮着」覆盖成「灭」。
                            torchCameraId = cameraId
                        }
                    }
                }
            cm.registerTorchCallback(callback, handler)
            torchCallback = callback
            // 标记必须写在成功之后：写在 try 之前的话，一次失败就永不重试，
            // torchOn 与真实状态会长期相反。
            torchCallbackRegistered = true
            XLog.w("已注册 torch 回调（手电筒状态由系统上报）")
        } catch (e: Throwable) {
            XLog.w("注册 torch 回调失败（状态可能不准，仍会尝试切换）", e)
        }
    }

    /**
     * 摘掉 torch 回调 —— 执行器被释放时调（`:ui` 进程里关页就走这里）。
     *
     *  必须是**同一个回调实例**：`unregisterTorchCallback` 按引用比，新建一个摘不掉。
     *  摘过一次就把标记清掉，下一次 toggle 会重新注册。
     */
    fun release() {
        val callback = torchCallback ?: return
        torchCallback = null
        torchCallbackRegistered = false
        val cm =
            runCatching { context()?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
                .getOrNull()
        if (cm == null) return
        runCatching { cm.unregisterTorchCallback(callback) }
            .onSuccess { XLog.w("手电筒：已注销 torch 回调") }
            .onFailure { XLog.w("手电筒：注销 torch 回调失败（进程结束时一并消失）", it) }
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

    private companion object {
        /**
         * ColorOS 在 `Settings.Secure` 里记手电筒开关用的键（**平台事实**，不是本模块的选择）。
         *
         *  它是「ColorOS 那条绑定通道失败之后，还能不能拿到真状态」的唯一判据：读不到就该退回
         *  「按 torch 回调推」，而不是把「读不到」当成「手电筒关着」。换 ROM 时先回查它。
         */
        const val FLASHLIGHT_ENABLED_KEY = "flashlight_enabled"
    }
}
