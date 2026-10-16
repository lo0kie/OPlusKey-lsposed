# OPlusKey

把一加 / OPPO 的侧键重映射成单击、双击、长按动作的 LSPosed 模块。下载：[LSPosed 模块仓库](https://github.com/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases) · [Releases](../../releases)

## 使用

1. 安装 APK
2. 在 LSPosed 里启用模块
3. **重启手机**

入口在**设置 → 快捷键**；「其他 → 更多选项」里有运行状态、适配清单与配置导入导出。

## 机型

只支持**适配清单**内的组合：**OnePlus PLK110**（一加 15，Android 17 / ColorOS 17.0.0）。清单外的机器仍能打开设置页，但整页禁用，页面上会说明原因。

自己编的 debug 包不做这道检查，任何机器都照常注入，方便在别的机器 / 新 ROM 上试。

另有一类命令是**逐条**判的：「一键闪记 / 免打扰 / 录音 / 小布识屏」要调 ColorOS 自己的服务，调用目标**每条 ROM 一档**。本机那一版 ROM 没有对应档时它们跑不起来，网格里与「添加命令」里都**置灰、点不动** —— 灰着的那几条就是按下去不会有反应的（那种情况下模块只记一条日志，不会去误起别的服务）。debug 包照最后一档试，所以那个包里不会灰。

## 安全

零权限、零网络、不依赖 root；不修改系统文件，关掉模块（或卸载）即回到原生行为。模块自己没有界面入口 —— 界面就长在宿主那一页里。

## 万一开不了机（自救）

模块有一部分代码跑在系统进程里。正常路径上的异常都被就地吞掉（只记日志），但**理论上**仍存在「注入侧抛异常 ⇒ 开机循环」的风险，所以先把自救步骤写在前面：

1. **等**。开机循环的表现是「卡在开机动画 / 反复重启」—— 先等 2～3 分钟，有时只是一次性的超时。
2. **安全模式**（最省事）：长按电源键 → 长按「关机」→ 确认进入安全模式。安全模式下不加载模块；进去后到 **LSPosed 管理器 → 模块 → 把本模块关掉**，再正常重启。
3. **`adb` 卸载**（手机能连上 adb 时）：

    ```bash
    adb shell pm uninstall --user 0 io.github.lo0kie.opluskey
    ```

    只想停用、不卸载：

    ```bash
    adb shell pm disable-user --user 0 io.github.lo0kie.opluskey
    ```

4. **停用框架**：实在进不去系统时，在 Recovery / 安全模式下停用 LSPosed，起来之后再把模块关掉。

> 两条容易被误判成「模块坏了」的事：**换包之后必须整机重启**（系统进程不重启不会重新加载模块）；LSPosed 里记的**安装路径要与真机实际一致**，不一致那一整次开机都会跳过本模块。

## 编译

需要 JDK 21 与 Android SDK；release 签名读根目录 `keystore.properties`（不入库）。**debug 与 release 用不同签名**（有意如此）：两者不能互相覆盖安装，要换包先卸载。debug 包带调试通道，别拿它当正式版用。

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

GPL-3.0 许可，见 [LICENSE](LICENSE)。版权所有 (C) 2026 lookie。

第三方：界面控件库 [COUI](https://github.com/suqi8/coui)（Apache-2.0）。`patches/` 下是它的补丁，`patches/repo/` 下是打过补丁的产物 —— 那一份的版权与许可归其原作者，随本仓库原样转发。
