# OPlusKey

LSPosed 模块：把一加 / OPPO 的侧键重映射成单击、双击、长按动作。

## 下载

[Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases) · [Releases](../../releases)

## 使用

启用模块（作用域：`system`、`com.oplus.gesture`）→ **重启手机**。

入口在 **设置 → 快捷键**（LSPosed 管理器里那颗「打开模块」的按钮也会直接跳过去）；
「其他 → 更多选项」里有运行状态、适配清单与配置导入导出。

> 换包或刚启用后必须**整机重启**：注入侧只在开机那一刻加载，重启 SystemUI 不够。

零权限、零网络、不依赖 root；关掉模块（或卸载）即回到原生行为。
LSPosed 管理器那颗「打开模块」的按钮也能用 —— 它只是把宿主那一页调到前台。

## 已验证

实测通过：

- **ColorOS 17.0.0 / Android 17**（OnePlus 15 · PLK110）

清单外的组合**照样能正常使用**（清单只回答「这一组验过没有」，不拦任何功能），
差别只是「更多选项」里那一行会说明是「机型不认识」还是「系统版本等待适配」。
自己编的 `debug` 包不做这道检查（见下面「编译」）。

## 自救

万一卡在开机动画：

1. 长按电源键 → 长按「关机」→ 进安全模式；到 LSPosed 里关掉本模块，再正常重启。
2. 能连上 adb 时：`adb shell pm uninstall --user 0 io.github.lo0kie.opluskey`。

## 编译

JDK 21 + Android SDK；release 签名读根目录 `keystore.properties`（不入库）。
`debug` 用自己的调试签名、`release` 用发布签名 —— **两者不能互相覆盖安装**（要换档先卸载）。

```bash
./gradlew assembleRelease   # 正式版：防篡改 + 封存资产
./gradlew assembleDebug     # 开发自用：另有调试通道（可在系统进程里跑任意反射），别装到别人机上
```

## 许可

[GPL-3.0](LICENSE)。界面控件库 [COUI](https://github.com/suqi8/coui)（Apache-2.0），`patches/` 下是它的补丁。
