package com.lookie.opluskey.inject

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.comboPlans
import com.lookie.opluskey.core.model.gesturePlan

// 配置到达时那三行**判据级日志** —— 从 `ConfigChannel` 分出来一支：那边已经贴着 detekt 的
// `TooManyFunctions` 上限，而这三支确实是一件事（「这一份配置长什么样」的投影），
// 且都只读 `Config`、不碰通道的任何状态。

/**
 * 配置到达时打的那几行 —— 「按了没反应」时全靠它们分辨「配置没到」与「判定不对」。
 *
 *  三支都只读 [Config]：不碰 `ConfigChannel` 的任何字段，所以放在这里与放在那边完全等价。
 */
internal object ConfigArrivalLog {
    /**
     * 把每颗自定义键的「键码 + 长按阈值」打一行 W。
     *
     *  上一行（`配置已更新`）只写侧键那份参数：自定义键的码是运行时收集来的、阈值也是每颗各一份，
     *  两者任一不对那颗键的长按就永远不响，而日志里除了 `DOWN` 看不到任何线索 —— 这一行是
     *  「自定义键长按没反应」时唯一能自证的配置快照。
     */
    fun customKeys(fresh: Config) {
        if (fresh.customKeys.isEmpty()) return
        XLog.w(
            "自定义键：" +
                fresh.customKeys.joinToString("、") { k ->
                    "${k.label}=${k.keyCode} 长按${fresh.key(k).longPressMs}ms"
                },
        )
    }

    /**
     * 把组合键打一行 W。
     *
     *  「两颗键一起按没反应」有两个完全不同的原因：注入侧**没收到**这一份配置（`combos` 为空 ⇒
     *  组合不成立、两颗键各走各的），或者收到了但**没配上**这一组。这一行是分辨它们的地方 ——
     *  没有它，日志里只有「DOWN / UP」，看不出注入侧手上到底有几档组合键。
     */
    fun combos(fresh: Config) {
        if (fresh.combos.isEmpty()) return
        XLog.w(
            "组合键：" +
                fresh.combos.joinToString("、") { c ->
                    "${c.id}=${c.keys.joinToString("+")}" +
                        " 短按=${XLog.redactToken(c.single.token)}" +
                        " 长按=${XLog.redactToken(c.long.token)}" +
                        " 阈值=${c.longPressMs}ms${if (c.enabled) "" else "（已停用）"}"
                },
        )
        //  每档组合的**判定计划**（`ComboPlan`）：要几颗、等多久、判定器认不认。
        //  成员数已经不影响能不能判（两颗走配对、3 颗起走凑齐），所以这一行现在读的是
        //  「这一档要几颗、窗口多大、长按阈值多少」——「按了没反应」时对得上按法就是配置问题，
        //  对不上才是判定问题，两者靠它分开。
        XLog.guard("组合判定计划") {
            fresh.comboPlans().forEach { XLog.d("组合计划：$it") }
        }
    }

    /**
     * 每颗键「这一档手势会判成什么」的投影。
     *
     *  用 `d` 不用 `w`：它**不是**判据（判定器现在还没读这份计划），只是给排查用的投影；
     *  注入侧的 `d` 在 LSPosed 日志页看不到，要用 logcat 看（`docs/device/lsposed.md` 十五）。
     */
    fun plans(fresh: Config) {
        XLog.guard("判定计划") {
            fresh.allKeys.forEach { id -> XLog.d("判定计划：${id.id} → ${fresh.key(id).gesturePlan()}") }
        }
    }
}
