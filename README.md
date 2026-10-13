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

## 已验证

实测通过：

- **OnePlus PLK110**（ColorOS 17 / Android 17）

## 编译

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

需要 JDK 17 与 Android SDK。正式签名读根目录 `keystore.properties`（不入库），debug 与 release 共用该签名。

## 许可

[MIT](LICENSE)
