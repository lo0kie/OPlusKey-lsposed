package com.lookie.opluskey.ui.state

/**
 * 命令库落盘的结果 —— 「落盘了、但广播没发出去」必须与「压根没落盘」**分开**。
 *
 *  前者数据已经是对的（该跟着挪的绑定照样得挪、草稿该清还是清），只是注入侧不知道；
 *  后者什么都没变，调用方必须收手。原来只用一个 `Boolean` 表达「有没有落盘」，
 *  而 `publishConfig` 的结果被直接丢掉：命令库改完没广播出去时界面一声不响。
 */
internal enum class CommandsSave {
    /** 落盘与广播都成了。 */
    Ok,

    /** 落盘成了、广播没发出去（注入侧仍是旧配置；提示已由 [saveCommands] 给过）。 */
    BroadcastFailed,

    /** 没落盘（只读态 / 写盘失败）—— 调用方必须按「什么都没变」处理。 */
    Failed,
}
