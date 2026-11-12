# 2026-11-13 · 静态检查收口、按职责分包、tools 统一到 Python

这一批 340 个文件，**没有一行行为变更** —— 全是「怎么组织代码」这一层：detekt 从「管写法」退回「管真问题」、把当初为过闸而拆开的代码合回去、`ui/` 的 72 个平铺文件按职责收进子包、`tools/` 四个脚本从 shell 换成 Python。

净行数是 +333（+7474 / −7141），其中 159 个是 `git mv` 的纯重命名；真正的内容改动集中在三处：`config/detekt/detekt.yml`（重写）、`tools/ratchet.txt`（记两个新千行文件）、以及 116 段被删掉的「为过 detekt 而拆」的注释。

`ConfigCodec.VERSION` **没动**（46），`tools/config-shape.txt` 也没变 —— 这一批没碰任何配置字段。

## detekt 从「管写法」退回「管真问题」

判据不是「严不严」，而是**这条规则的修复动作会不会改变代码的组织方式**。只改写法（加个名字、删个 import）的，严一点无所谓；逼你拆函数、搬文件、手工透传参数的，就是在替你做设计决策。

- **`LongMethod` 120**（原 60）：Composable 的长度正比于视觉层级深度，60 行逼出来的拆法是把内层一行抽成 `FooRow`，再把 `style / tick / labelColumn / ui` 一路透传 —— 全是父级作用域里的东西，等于手工搬一遍闭包。`ui/**` 与 `bridge/**` 整片排除（`bridge/` 的流程长度由 ROM 私有 API 的调用顺序决定，不由作者决定）。
- **`LongParameterList` 10 / 15**（原 6 / 7）：构造参数个数就是字段数（`MiniWindowGeometry` 8 个、`GestureDetector` 的手势状态表 14 个），由数据形状决定。`ui/**`、`bridge/**`、`rom/**` 排除。
- **`TooManyFunctions` 40**（原 11）：这条按**文件**计，逼出的动作是「另起一个文件」—— 而本项目同时有 `InvalidPackageDeclaration`（目录必须与包名一致），搬家要连带改全仓 import。
- **`CyclomaticComplexMethod` 30**（原 15）：`Action.parse`(28) / `ActionRunner.dispatch`(26) 这类**分派表**的复杂度等于「支持多少种动作」，可加不可缠。
- 关掉五条：`LoopWithTooManyJumpStatements`（扫集合直到找到，多一个出口是写法本身）、`SwallowedException`（与 `TooGenericExceptionCaught` 是同一件事的两半 —— 项目纪律要求每环 `catch (Throwable)`，两条一起开就是无解，原先只关了一半，22 处靠基线沉默兜着）、`InstanceOfCheckForException`（反射的标准写法就是 `catch (InvocationTargetException) { e.cause }`）、`SpreadOperator`（反射调用只有 `*args` 这一种写法）、`MatchingDeclarationName`。
- 放宽 `ComplexCondition` 6、`NestedBlockDepth` 6、`ReturnCount` 15（`excludeGuardClauses`）。
- **`config/detekt/baseline.xml` 清空**（130 条 → 0），**49 处 detekt 的 `@Suppress` 全删**（`LongParameterList` 32 / `CyclomaticComplexMethod` 8 / `TooManyFunctions` 5 / `SpreadOperator` 2 / `ReturnCount` 1 / `LongMethod` 1）—— 规则放宽之后它们全是空壳。`tools/ratchet.txt` 的 `count.detekt_baseline` 从 130 收紧到 **0**：从此一条基线都不许添。

## 把为过闸而拆开的代码合回去

**28 个文件合并回同包的目标文件**（import 取并集，逐条 `git mv` 之外只改 `package` 行）：

- `bridge/`：`BrightnessBridge` / `VoiceAssistBridge` → `StatusBarBridge`，`HotspotCall` → `SettingCalls`
- `host/action/`：`FeatureLaunch` / `AppShortcutLaunch` / `ShortcutThenMinimize` / `StockLaunchIntent` → `AppLauncher`，`ConditionChecks` / `ConditionSwitchChecks` → `ConditionProbe`
- `inject/`：`BroadcastSender` → `KeyHooks`，`ConfigArrivalLog` → `ConfigChannel`
- `ui/`：`CardRowParts` → `CardRow`，`DisplayWrite` → `GlobalWrite`，`HeaderSubtitleBar` / `PageHeaderButtons` → `PageHeaderWidget`，`ToastWrite` → `GlobalWrite`，`SwitchKindIcons` → `ActionIcons`，以及 `screen/*` 下 10 个部件文件回到各自的页面文件

**函数搬回对象**：`ShellRunner.killForcibly`、`KeyHooks` 六个顶层函数、`ComboDetector` 的 `Hit` / `multiFor` / `pairFor` / `stuckComboLog`、`AppLauncher` 九个顶层函数。搬回时顺手去掉了为透传而加的形参 —— `withSystemContext` 改用类里自己的 `postGuarded`（不再自己写 post + try/catch）、`onContextQuery` 少一个 `worker`、`pairFor` / `multiFor` 少三个 lambda（`PairScan` 整个删掉，`recent` 本来就是字段）。`ActionRunner` 两个 lambda 字段（`startOplusOrVoice` / `runOplus`）改回 `private fun`。

**参数对象**：只真拆了两个 —— `PageConfirmDialog.PanelMetrics`（单调用点）与 `ComboDetector.ComboWindows`（5 个 lambda 进构造函数后立刻摊成 5 个字段，包装层零收益；拆完构造函数 9 参）。其余四个（`ShortcutStarter.Request`、`RecorderStateBridge.Call`、`CommandBranches` 的 `RuleRowSpec` / `RuleCells` / `RuleText`）**保留**并改写了注释：判据是「这个包是否命名了一个真实概念、是否被 ≥2 层透传」，摊平会让 `RuleBlock` 变成 12 参。

## ui/ 从 72 个平铺文件收进 8 个包

`ui/` 根上只留 `SettingsPage.kt` 与 `Screen.kt`：

| 包 | 数量 | 装什么 |
|---|---|---|
| `chrome` | 13 | 页面骨架与运行期（顶栏 / FAB / 覆盖层 / 视图树 / 钩子 / 分段带 / 装载 / 更新 / 控制 / 文案 / 调试通道） |
| `row` | 15 | 列表行与卡片（CardRow / ExpandableRow / SwitchRow / SliderRow / ChipLabel / 长按卡 / 独立设置卡…） |
| `dialog` | 10 | 弹窗与浮层菜单（确认 / 命名 / 抽屉 / 选择器 / 百分比 / 菜单 / 亮度音量档位） |
| `style` | 10 | 样式、度量、主题、绘制（色板 / 文本样式 / 图标着色 / 绘制器 / 文本度量 / 主题） |
| `write` | 7 | 配置写回（全局 / 按键参数 / 命令库 / 重置 / 落点停用 / 步骤增删） |
| `wire` | 6 | 页面 ↔ 宿主 / 注入侧（配置广播与自检 / 取 URL / 本地广播 / 链路检查 / 手势探针 / 应用名） |
| `feedback` | 5 | 提示与触觉（提示通道 / 禁用原因 / 间距 / 振动 / 提示条退场） |
| `input` | 4 | 输入控件（单行输入框 / 行首图标输入 / 输入法 / Markdown） |

同时：`ui/model` 30 → `icon` 8 / `text` 7 / `theme` 6 / `page` 3 / `target` 6；`ui/state` 27 → `page` 7 / `command` 6 / `binding` 5 / `signal` 4 / `host` 3 / `picker` 2；`host/action` 24 → `app` 8 / `shell` 4 / `condition` 3 / `setting` 3 / `notice` 3（根上留 `ActionRunner` / `CommandFailure` / `CustomCommandRunner`）；`inject` 15 → `key` 7 / `wire` 5 / `system` 3。

**没动** `bridge`（15）、`core/model`（26）、`core/config`（13）、`ui/screen/command`（28）：这几个目录各自已经是单一职责（binder 桥 / 领域模型 / 配置层 / 一个页面），再拆只能按子类型切，粒度上可商榷。

搬家是用 `tools/repack.py` + `tools/fiximports.py` 做的，两步都留在仓库里（见最后一节）：

1. `git mv` + 只改 `package` 行 —— **保证 git 记成 rename**（159 个），diff 里才看得出「这是搬家」而不是「删了 159 个又加了 159 个」。
2. 再把全仓 `com.lookie.opluskey.<旧包>.<Name>` 文本替换成新路径。只改 `import` 行不够：还有 `import pkg.Obj.member`（成员导入，如 `MiniWindowFacts.ROM`）与代码里直接写全限定名两种写法。
3. 最后按**符号 → 当前包**的索引规范化 import，靠编译器的 `Unresolved reference` 迭代补齐。不能按文件名猜 —— 顶层函数和常量散在与文件名无关的文件里；索引里还必须跳过 `private` 顶层声明，否则会生成 `import pkg.privateFun` 直接编译失败。

`InvalidPackageDeclaration` 是默认开启的（`buildUponDefaultConfig = true`），实测「搬文件不改包名」当场报 `The package declaration does not match the actual file location` —— 所以 import 必然要全仓改，这条没有捷径。

## 清掉 ui/ 下 116 处「为过 detekt 而拆」的注释

规则放宽之后，`ui/` 里那批「抽出来只为让 X 留在 `LongMethod` 长度线内」「合成一个对象只为把参数个数压回上限」的说法全成了假话。

**没有自动摘句** —— 先试过一版按句删，结果是把 KDoc 的 `*/` 当成空注释吃掉、句子截在词中间；而且「菜单底部那几行管理动作 —— 从 `ProfileMenu` 里分出来：…会顶过 detekt 的 `LongMethod`」整句是同一句，自动删会连描述一起丢。最后是 63 + 53 条逐条写「(文件, 起行, 止行, 新内容）」：纯理由句整行删，句子里带真内容的就摘掉 detekt 那半句、留下说事的那半句。

顺带修掉三件连带问题：4 处删完留下两行空注释、7 处注释里指向已不存在文件的路径（`ui/HostWindow.kt` → `ui/hook/HostWindow.kt` 之类）。另外发现 `@Suppress("LongParameterList")` 其实早就删光了，但一批 KDoc 还在解释它 —— 一并清掉。

## tools/ 四个脚本统一到 Python

`tools/` 原本 14 个 `.py` + 6 个 `.sh`，分界线是「Python 写判据、shell 写动作」，但不干净（`verify-apk.sh` 是纯判据却用 shell）。

**转 4 个**：`verify-patches` / `read-version` / `relock` / `verify-apk`（共 496 行 shell → 644 行 Python）。`verify-apk` 最该转 —— 它那段 `ls "$sdk"/build-tools/*/aapt2.exe | sort -V | tail -1` 在 Windows 的 Git Bash 下靠运气（`sort -V` 不是每个平台都有），`zipfile` + `pathlib` 稳得多。

**故意留 2 个**：`gitee-release.sh`（靠 `curl --data-urlencode` + `-F` 做 multipart、`jq` 解析 JSON，换 Python 要么手写 multipart 要么继续 subprocess 调 curl，负收益）与 `adb-find.sh`（就是几条 adb 命令串起来，且与 `device-state.py` 不重叠 —— 后者收 `port` 参数、不找设备）。

新增 `tools/_common.py`（144 行）收掉原先各写一遍的四件事：`ROOT`（由 `__file__` 推，**从任何目录都能跑**了）、`::error::` / `::warning::` / `::notice::`、`set_output()`（写 `$GITHUB_OUTPUT`，强制 LF）、`run()` 与 `Checks` 累加器。`import _common` 能用是因为 `python3 tools/x.py` 时 `sys.path[0]` 就是 `tools/` —— 所以新脚本仍然叫 `verify-apk.py` 这种 kebab-case，与原有 `audit-arch.py` 一致。

移植时踩到的两处：

- **Windows 上 `print` 写 CRLF、CI 是 LF** ⇒ `_common.py` 里 `sys.stdout.reconfigure(newline='\n')`，否则本地与 CI 的日志逐字对拍时每一行都「不一样」。
- `sed` 的 `.*` 是贪婪的 ⇒ 取「第一条命中的行、行内最后一次出现」；`re.search` 是第一次，得用 `findall()[-1]` 才对得上。

顺带修了原 `verify-apk.sh` 一处编号重复：两处 `(7)`（`.temp/audit-1104/ledger.md` 记过这条 P3）→ 字典 `(10)`、封存 `(11)`；`(5) 注入侧` 保持不动，因为 `docs/architecture/decoupling.md:110` 引用了这个编号。

## 搬家打破的三道闸（都修好了）

静态检查对**目录结构**是硬编码依赖的，159 个文件一搬，三个脚本当场红：

- `audit-arch.py` 的趋势线里写死 `ui/state/HintCenter.kt` → 直接 `FileNotFoundError` 崩掉。改成 `ui/state/signal/HintCenter.kt`。
- `audit-perf.py` 的 `HOT_FILES` 与 `MT_ALLOW` 按**路径**登记 → 路径一变，已复核的 9 处主线程命中全部「复活」成违规，还报 `文件不在了`。六条路径跟着分包改。
- `audit-ratchet.py` → 合并回位造出两个新的千行文件（`CommandBranches` 1165、`AppLauncher` 1037），超 `count.files_over_1000`。`tools/ratchet.txt` 里 5 → 7 并写明理由（这是拆分的逆操作带来的必然结果），`SettingsPage.kt` 1062 → 1067（它自己上面那段注释早就写着「1062 → 1070」，只是数字没跟着改）。

`spotlessCheck` 也红过一次：合并时用 Python 的 `sorted()` 排 import，与 ktlint 的排序规则不同，**99 个文件**需要 `spotlessApply` 归位。这条提醒：**别用脚本自己排 import**。

## 验收

- `./gradlew gate`（JDK 21）：通过 —— `spotlessCheck` / `detekt` / `:app:lintDebug` / `:core:lintDebug` / `:core:testDebugUnitTest`，两个模块一起算。
- 三个变体离线全量构建：`assembleDebug` / `assembleRelease` / `assembleReleaseLog` 全过（2m19s）。**`lintVital` 离线已经能解析**，不用再加 `-x lintVitalAnalyzeRelease` 那三个参数了。
- CI 那 8 个审计逐个 exit 0：`audit-coupling` / `audit-arch --strict`（全部达标）/ `audit-ratchet`（未超上限）/ `check-i18n-cache` / `check-i18n-fallbacks` / `check-i18n-keys` / `audit-perf`（主线程阻塞 0、热路径分配 0）/ `audit-config-version`。
- `verify-patches.py`：`patches/repo` 6 份产物校验和一致。
- `read-version.py`：`1.4.2（16）`，tag `16-1.4.2`，构建 `2026-11-12-0002`；stdout 与 `$GITHUB_OUTPUT` 都与旧 shell 版逐字一致。
- `check_variants.py`：debug 25 dex / 17.33 MB，release 与 releaseLog 各 1 dex / 2.13 MB，全部符合预期。
- `verify-apk.py dist`：**41 项通过、0 失败**。含清单入口只放行 `EntryActivity`（MODULE_SETTINGS×1 / activity×1 / alias×0 / LAUNCHER·INFO×0 / exported×1 / noHistory×1 / 透明主题×1）、注入侧零 androidx/COUI、字典三边 749 条键 + 占位符一致、release 与 releaseLog 都封存生效、debug 保留明文资产。
- **对拍**：把两个包按 CI 命名放进 `dist/`，新旧脚本各跑一遍 `diff` —— `verify-apk` 输出逐字一致（32 行）。这是这次换语言唯一的硬证据。
- 本机 JDK 17 → 21（`C:\Users\Lookie\jdks\temurin-21`，用户级 `JAVA_HOME` + `~/.gradle/gradle.properties` 里的 `org.gradle.java.home`），与 CI 的 temurin 21 对齐；这两处都在仓库外，不影响 CI。
