# OPlusKey

把一加 / OPPO 的侧键重映射成单击、双击、长按动作的 LSPosed 模块。

## 下载

[LSPosed 模块仓库](https://github.com/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases) 和
[Releases](../../releases)。

## 使用

1. 安装 APK
2. 在 LSPosed 里启用模块
3. **重启手机**

模块入口为 **设置 → 快捷键**。

## 安全

- **零权限、零网络**：不声明任何权限（清单里只有 androidx 为 `RECEIVER_NOT_EXPORTED`
  自动加的签名级自权限），没有一行网络调用，不读设备标识 / 通讯录 / 短信 / 位置 / 剪贴板。
- **不依赖 root / su**：全部系统能力都走宿主进程（`com.oplus.gesture`）**本来就持有**的签名级权限，不请求也不调用
  `su`，不写任何需要提权的路径，源码与产物里都没有 root 相关字符串。
- **零入口组件**：没有 Activity / Service
  / 静态 Receiver，所以管理器不显示「打开模块」；界面寄生在宿主「设置 → 快捷键」页内。
- **注入 `system` 是必需的**：侧键派发点在
  `StrategyActionButtonKeyLaunchApp`，只有该作用域能拿到；接管点找不到时模块不生效，但绝不影响系统。
- **跨进程通道防伪造**：注入侧按 `Binder.getCallingUid()` 与宿主 uid 比对、取不到就丢弃，宿主侧接收器为
  `RECEIVER_NOT_EXPORTED`；第三方 App 发同名广播进不来。
- **`shell:` 动作**：以宿主进程身份执行**你自己配置的**命令，不涉及 root / system 提权。

## 已验证

实测通过：

- **OnePlus PLK110**（ColorOS 17 / Android 17）

## 编译

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

需要 JDK 21 与 Android SDK。正式签名读根目录 `keystore.properties`（不入库），debug 与 release 共用该签名。

## 许可

[MIT](LICENSE)
