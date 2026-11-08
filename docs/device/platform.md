# 一、目标设备

| 项         | 值                                                                                  |
| ---------- | ----------------------------------------------------------------------------------- |
| 机型       | OnePlus `PLK110`                                                                    |
| 系统       | Android 17 / `PLK110_17.0.0.105(CN01)`                                              |
| adb        | over network `192.168.0.104:44029`                                                  |
| 侧键存在   | `ro.oplus.key.actionbutton = 1`                                                     |
| 设置包     | **`com.android.settings`**（不是 `com.oplus.settings`）                             |
| 侧键实现包 | **`com.oplus.gesture`**（APK：`/system_ext/app/OplusGestureUI/OplusGestureUI.apk`） |
| 框架侧实现 | **`oplus-services.jar`**（`/system/framework/oplus-services.jar`）                  |
| 签名校验   | 装了**核心破解** ⇒ 签名不同的包也能 `adb install -r -d` 直接覆盖，不必卸载重装      |
