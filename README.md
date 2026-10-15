# OPlusKey

把一加 /
OPPO 的侧键重映射成单击、双击、长按动作的 LSPosed 模块。下载：[LSPosed 模块仓库](https://github.com/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases)
· [Releases](../../releases)

## 使用

1. 安装 APK
2. 在 LSPosed 里启用模块
3. **重启手机**

入口在 **设置 → 快捷键**；「其他 → 更多选项」里有运行状态与配置导入导出。「导出配置」存成一份
`.bin`（混淆过的文本），「导入配置」读回 —— 整份校验通过才写入。模块自己的配置文件（存在宿主私有目录里）也是同一层混淆外皮，**手动编辑要先按
`core/config/FileObfuscation` 的说明解两层、改完再包回去，并把正文里的 `md5` 一起重算**。

## 机型

只允许表内机型（`core/model/DeviceSupport`，按 `Build.MODEL` 判）：**OnePlus PLK110**（一加 15，ColorOS 17 / Android
17）。表外机型仍可打开设置页，但整页禁用并提示「未知设备」；新增机型请加进允许表并在真机上重验接管点（判据见 `docs/`）。

## 安全

零权限、零网络，不依赖 root（系统能力全走宿主进程本来就持有的签名级权限）；没有 Activity / Service
/ 静态 Receiver，界面寄生在宿主页内；跨进程通道按 uid 比对防伪造；注入 `system`
是必需的 ——侧键派发点只有该作用域能拿到。

## 万一开不了机（自救）

模块有一部分代码跑在 `system_server`
里。正常路径上的异常都被就地吞掉（只记日志），但**理论上**仍存在「注入侧抛异常 ⇒ 开机循环」的风险 —— 所以先把自救步骤写在前面，真出事时照做即可：

1. **等**。开机循环的表现是「重启后卡在开机动画 / 反复重启」。先等 2～3 分钟，有时只是一次性的超时。
2. **安全模式**（最省事）：长按电源键 → 长按「关机」→ 确认进入安全模式。安全模式下 LSPosed 不加载模块，手机正常起来；进去后到
   **LSPosed 管理器 → 模块 → 把本模块关掉**，再正常重启。
3. **`adb` 卸载**（手机能连上 adb 时）：

   ```bash
   adb shell pm uninstall --user 0 io.github.lo0kie.opluskey
   ```

   或者只停用、不卸载：

   ```bash
   adb shell pm disable-user --user 0 io.github.lo0kie.opluskey
   ```

4. **停用框架**：实在进不去系统时，在 Recovery / 安全模式下停用 LSPosed（不同框架方式不同），起来之后再把模块关掉。

> 另外两条与本模块有关的硬约束（都会让「模块装了却完全不生效」，别误当成崩溃去修）：重装 APK 后要确认 LSPosed 库里的**安装路径 = 真机当前路径**；**换包必须整机重启**
> （系统进程不重启不会重新加载模块）。判据见 `docs/device-findings.md` 六之一~六之三、十五。

## 编译

需要 JDK 21 与 Android SDK；release 签名读根目录 `keystore.properties`（不入库）。
**debug 与 release 用不同签名**（有意如此）：两者不能互相覆盖安装，要换包先卸载。debug 包带调试通道（`DEBUG_PROBE`），不要拿它当正式版用。

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

MIT 许可，见 [LICENSE](LICENSE)。
