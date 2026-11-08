# 文档索引

`docs/` 是**给维护者**的：机制、实测、判据。给使用者的说明在 `README.md`，做事准则在 `AGENTS.md`。

## 怎么找

| 你要回答的问题 | 去 |
| --- | --- |
| 这台机器 / 这个 ROM 到底是什么样 | `device/` |
| 模块自己怎么跑、为什么这么设计 | `runtime/` |
| 分层、依赖方向、哪些边不许连 | `architecture/` |
| 提交与发版要走哪几道闸 | `process/` |
| 某个专题的完整笔记 | `features/` |
| 某一笔提交改了什么 | `changelog/` |
| 某一版给用户看的更新说明 | `release-notes/` |

**事实以 `docs/` 为准**：与代码冲突时改代码或改文档；ROM 行为以 `device/` 为最高。
**行为约束以 `AGENTS.md` 为准**：`docs/` 与它冲突时不构成放行理由。

## device/ — 目标 ROM 的事实

按子系统分。改 Hook 面、加动作、排查「这台机器上为什么不行」之前先读这里。

| 文件 | 收什么 |
| --- | --- |
| `device/platform.md` | 目标机型与系统版本 |
| `device/side-key.md` | 侧键接管点、框架与宿主的分工、手势判定、权威调用链、放行与自持循环 |
| `device/settings-page.md` | 换页劫持进「设置 → 快捷键」：类结构、布局落点、两个坑 |
| `device/key-codes.md` | 侧键与压感拍照键的键码族、中间态码的处置 |
| `device/lsposed.md` | LSPosed 作用域、重装不加载、`ContentResolver` / `/data/adb` / 联网、注入侧 API 的坑 |
| `device/host.md` | 宿主进程能用哪些系统组件（含 COUI 资源名的可用边界） |
| `device/coui.md` | COUI 组件库的实测行为与观感：Snackbar、浮层、弹窗、Monet、配色 |
| `device/storage.md` | 配置存在哪、CE / DE 的区别、改配置外皮的前提 |
| `device/actions/` | 各类动作的系统配方，见该目录的 `README.md` |

## runtime/ — 模块运行时原理

| 文件 | 收什么 |
| --- | --- |
| `runtime/key-to-gesture.md` | 接管方式、两级开关、按键怎么变成手势、执行时提示、为什么不提供「交还系统」 |
| `runtime/config-channel.md` | 配置从哪来、怎么到注入侧、注入侧引用红线、降级设计 |
| `runtime/debug-channel.md` | 注入侧反射通道：安全边界、七个 op、用法 |
| `runtime/adaptation.md` | 适配清单与多版本档位表 |
| `runtime/app-control.md` | 「应用控制」那一格与 `proc:stop:` |
| `runtime/ui-decisions.md` | 界面取舍（命令输入框的格式前缀等） |
| `runtime/known-limits.md` | 已知限制 |

## architecture/ — 结构与依赖契约

| 文件 | 收什么 |
| --- | --- |
| `architecture/structure.md` | 目标结构、逐文件迁移映射、重构后仍须成立的契约、明确不采用的做法 |
| `architecture/decoupling.md` | 降耦合 / 高内聚的判据、实测基线、待收敛项、防回归脚本 |

## process/ — 交付流程

| 文件 | 收什么 |
| --- | --- |
| `process/pre-commit.md` | 提交前检查：三道闸 + 一道锁定校验 |
| `process/build.md` | 构建档位：debug / release 各开哪些能力 |
| `process/release.md` | 发版：版本号、tag 规则、钉版与上架两条流水线 |

## features/ — 专题

| 文件 | 收什么 |
| --- | --- |
| `features/launch-animation.md` | 侧键「打开应用」的转场动画完整技术文档 |
| `features/zoom-window/` | ColorOS 小窗，见该目录的 `README.md` |

## changelog/ — 变更日志片段

一笔提交一个片段（`YYYY-MM-DD-<主题>.md`）。**已入库的片段只读** —— 改一个错别字也会产生删除行，
破坏「一次工作区一个片段、diff 恒为新增」的约定；要补记只能写进当前工作区那个片段。
分区与格式规则在 `AGENTS.md` 第一部分 三，这里不复述第二份。

## release-notes/ — 发版公告

`<versionName>.md` 一份一版，管理器与设置页的更新弹窗显示的就是它。**发版前必须先写**，
缺文件时钉版与上架两条流水线直接红（规则见 `process/release.md`）。

## 旧路径 → 新路径

`docs/changelog/` 里已入库的片段**只读**，它们写的是当时的路径，不再改。查旧路径时按这张表换：

| 旧写法 | 现在在 |
| --- | --- |
| `docs/device-findings.md` 一 | `device/platform.md` |
| `docs/device-findings.md` 二、四、五、七、十四、十六、二十五、三十九 | `device/side-key.md` |
| `docs/device-findings.md` 三、二十一、9.8、9.9 | `device/storage.md` |
| `docs/device-findings.md` 六之一~六之七、十五、四十一、11.1、11.2、11.4 | `device/lsposed.md` |
| `docs/device-findings.md` 六之八、二十三、二十四、三十四 | `device/actions/toggles.md` |
| `docs/device-findings.md` 八（8.1~8.4、8.8） | `device/settings-page.md` |
| `docs/device-findings.md` 8.10~8.17、十八、二十、二十六、四十三、四十四 | `device/coui.md` |
| `docs/device-findings.md` 9.1~9.7.1 | `device/side-key.md` |
| `docs/device-findings.md` 十（10.1~10.8） | `device/host.md` |
| `docs/device-findings.md` 12.2~12.6、二十七、三十三 | `device/actions/audio.md` |
| `docs/device-findings.md` 13.1~13.6、二十二、二十九、四十七 | `device/actions/launch.md` |
| `docs/device-findings.md` 十七、17.2~17.5 | `device/actions/system.md` |
| `docs/device-findings.md` 17.6、附二~附十三 | `features/zoom-window/api.md` |
| `docs/device-findings.md` 附十三之二~之五 | `features/zoom-window/api-extra.md` |
| `docs/device-findings.md` 17.7、三十、三十五、三十六 | `device/actions/capture.md` |
| `docs/device-findings.md` 十九、四十 | `device/actions/feedback.md` |
| `docs/device-findings.md` 二十八、三十一、四十二 | `device/key-codes.md` |
| `docs/device-findings.md` 三十二、三十七、四十六 | `device/actions/voice.md` |
| `docs/device-findings.md` 三十八 | `device/actions/brightness.md` |
| `docs/device-findings.md` 四十五 | `device/actions/lock-screen.md` |
| `docs/implementation.md` 一~三 | `runtime/key-to-gesture.md` |
| `docs/implementation.md` 四~六 | `runtime/config-channel.md` |
| `docs/implementation.md` 七 | `runtime/ui-decisions.md` |
| `docs/implementation.md` 八、八之二 | `runtime/adaptation.md` |
| `docs/implementation.md` 八里的「构建档位」 | `process/build.md` |
| `docs/implementation.md` 八里的「发版：版本号与 tag」 | `process/release.md` |
| `docs/implementation.md` 九 | `process/pre-commit.md` |
| `docs/implementation.md` 十 | `runtime/app-control.md` |
| `docs/implementation.md` 已知限制 | `runtime/known-limits.md` |
| `docs/architecture-refactor.md` | `architecture/structure.md` |
| `docs/decoupling.md` | `architecture/decoupling.md` |
| `docs/debug-channel.md` | `runtime/debug-channel.md` |
| `docs/zoom-window.md` | `features/zoom-window/notes.md` |
| `SIDE-KEY-OPEN-APP-ANIMATION.md`（仓库根） | `features/launch-animation.md` |

节号（`六之四`、`§14.4`、`附七` 这类）沿用旧编号，拆分时没有重排 —— 它是代码注释与 `AGENTS.md` 的引用锚点。
