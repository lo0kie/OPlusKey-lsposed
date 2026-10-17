package com.lookie.opluskey.ui

/** 认哨兵的阈值（像素）：一屏也就几千 px，比它更负的一定是库的 `-Float.MAX_VALUE` 初值。 */
internal const val SENTINEL_MIN = -1_000_000f
