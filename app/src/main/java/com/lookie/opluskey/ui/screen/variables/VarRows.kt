package com.lookie.opluskey.ui.screen.variables

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.VarOp
import com.lookie.opluskey.core.model.VarType
import com.lookie.opluskey.core.model.Variable

// 变量弹窗里那一列动作 —— 两趟（管数据 / 挑一步）共用同一批文案，只按类型与用途挑行。
//  单独一个文件：这一批是「有哪几行」的清单，与弹窗的开关、落盘两件事分开写才看得清。

/**
 * 这一列动作 —— 两趟各一份清单：
 *
 *  · **管数据**（首页进来）：编辑 / 加一、减一（只有数字）/ 删除。**没有取反、没有恢复初始值** ——
 *    这一层管的是「把它改成什么」，取反与回到初值是拿来绑的一步动作，归挑一步那一趟；
 *  · **挑一步**（命令里进来）：设为… / 取反（只有布尔）或 加一、减一（只有数字）/ 恢复初始值。
 *    这一层没有「删除」：写的是「对这颗变量做一件事」，删掉它没意义。
 */
internal fun varRows(
    variable: Variable,
    step: Boolean,
    onValue: (Variable) -> Unit,
    onLand: (Action.VarSet) -> Unit,
    onDelete: (Variable) -> Unit,
): List<VarAction> =
    buildList {
        add(
            VarAction(
                if (step) {
                    I18n.get(Strings.VAR_OP_SET, "设为…")
                } else {
                    I18n.get(Strings.COMMON_EDIT, "编辑")
                },
            ) { onValue(variable) },
        )
        addAll(typeRows(variable, step, onLand))
        if (step) {
            add(
                VarAction(I18n.get(Strings.VAR_OP_RESET, "恢复初始值")) {
                    onLand(Action.VarSet(variable.name, VarOp.SET, variable.initial))
                },
            )
        } else {
            add(VarAction(I18n.get(Strings.COMMON_DELETE, "删除")) { onDelete(variable) })
        }
    }

/**
 * 按类型给的那几行（布尔取反 / 数字加减一）—— 文本没有这一组。
 *
 *  「取反」只在**挑一步**那一趟出现（见 [varRows]）。
 */
private fun typeRows(
    variable: Variable,
    step: Boolean,
    onLand: (Action.VarSet) -> Unit,
): List<VarAction> =
    when (variable.type) {
        VarType.BOOLEAN -> {
            if (step) {
                listOf(
                    VarAction(I18n.get(Strings.VAR_OP_TOGGLE, "取反")) {
                        onLand(Action.VarSet(variable.name, VarOp.NOT, ""))
                    },
                )
            } else {
                emptyList()
            }
        }

        VarType.NUMBER -> {
            listOf(
                VarAction(I18n.get(Strings.VAR_OP_INC, "加一")) {
                    onLand(Action.VarSet(variable.name, VarOp.ADD, "1"))
                },
                VarAction(I18n.get(Strings.VAR_OP_DEC, "减一")) {
                    onLand(Action.VarSet(variable.name, VarOp.SUB, "1"))
                },
            )
        }

        VarType.STRING -> {
            emptyList()
        }
    }
