# OPlusKey

把一加 /
OPPO 的侧键重映射成单击、双击、长按动作的 LSPosed 模块。下载：[LSPosed 模块仓库](https://github.com/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases)
· [Releases](../../releases)

## 使用

1. 安装 APK
2. 在 LSPosed 里启用模块
3. **重启手机**

入口在 **设置 → 快捷键**；「其他 → 更多选项」里有运行状态与配置导入导出。「导出配置」存成一份
`.opk`（混淆过的文本），「导入配置」读回 —— 整份校验通过才写入。

## 机型

只允许表内机型（`core/model/DeviceSupport`，按 `Build.MODEL` 判）：**OnePlus PLK110**（一加 15，ColorOS 17 / Android
17）。表外机型仍可打开设置页，但整页禁用并提示「未知设备」；新增机型请加进允许表并在真机上重验接管点（判据见 `docs/`）。

## 安全

零权限、零网络，不依赖 root（系统能力全走宿主进程本来就持有的签名级权限）；没有 Activity / Service
/ 静态 Receiver，界面寄生在宿主页内；跨进程通道按 uid 比对防伪造；注入 `system`
是必需的 ——侧键派发点只有该作用域能拿到。

## 编译

需要 JDK 21 与 Android SDK；签名读根目录 `keystore.properties`（不入库），debug 与 release 共用。

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

MIT 许可，见 [LICENSE](LICENSE)。
