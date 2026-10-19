# 降耦合 / 高内聚审计与收敛方案

> 本文是 `docs/architecture-refactor.md` 的**续篇**，不是替代。
>
> - 那份写的是**已经落地的包契约**：按进程切包 → 每包单一职责 → 用依赖方向把「谁能用谁」钉死，并给了重建前的逐文件映射表。
> - 这份写**它落地之后还剩什么耦合**：先立可检验的判据，再用实测数据审计整个项目（含界面层内部），最后给出逐条收敛动作、阶段划分与防回归手段。
>
> 事实依据：本文所有「现状」数字都是 2026-10-16 在**当前工作区**用脚本实测的（脚本见「七」），不是估计。
> 引用处一律给出 `文件:行号`，复查时可直接跳过去。

---

## 零、实测基线（2026-10-19 复核）

正文各节的 `43 字段 / 15 文件 / ui/component / 18 个环` 是**改造前**的口径。改造后的可复核值：

| 项                                       | 值                                                                       | 判据命令（在 `app/src/main/java/com/lookie/opluskey` 下执行）                       |
| ---------------------------------------- | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| 包级环 / 边                              | **0 / 30**（细分档位口径；`ui/hook` 单独成档后由 27 变 31，根包文件归入 root 档后为 30，见下）        | 跑 `python3 tools/audit-coupling.py`，第一行就打印这两个数                          |
| `SettingsPage` 状态字段                  | **21**                                                                   | `grep -cE '^    (internal )?(private )?(val\|var) ' ui/SettingsPage.kt`             |
| `SettingsPage.xxx` 扩展                  | 215 个，散在 67 文件（2026-10-19 复核：204 / 65 → 215 / 67）             | `grep -rcE 'fun SettingsPage\.' --include=*.kt .`                                   |
| `ui/SettingsPage.kt` 行数                | **517**（879 → 699 → 497 → 517）                                         | `wc -l ui/SettingsPage.kt`                                                          |
| `inject/ProbeChannel.kt` 行数            | 337（`call` 那族在 `ProbeCall.kt`，四个 op 在 `ProbeOps.kt`）            | `wc -l inject/Probe*.kt`                                                            |
| `host/action/MiniWindowLauncher.kt` 行数 | 695（常量与反射面在 `MiniWindowFacts.kt`，验收在 `MiniWindowVerify.kt`） | `wc -l host/action/MiniWindow*.kt`                                                  |
| 单文件最大行数                           | **791**（`ui/model/ActionIcons.kt`）；全仓**无 > 1000 行**的文件         | `find . -name '*.kt' \| xargs wc -l \| sort -n \| tail`                             |
| 屏文件 import 页面                       | 47 —— 屏目录按功能切成 3~4 块之后跟着涨，**不是耦合指标**，别做趋势线    | `grep -rl 'import com.lookie.opluskey.ui.SettingsPage' ui/screen/`                  |
| 零引用 import                            | 0，且由 detekt 拦下                                                      | `./gradlew :app:detekt`（`UnusedImports` 已在 `config/detekt/detekt.yml` 显式打开） |

**边数 27 → 31 → 30 是量具变细，不是新增耦合**：`ui/hook` 原先被折进 `ui` 档，它自己的五条外连
（`→ bridge / core / ui / ui.screen / ui.state`）和 `root` 那一条都被记在别的档上，方向看不出来。
拆开之后 `root` 的依赖也从含糊的 `root → ui` 变成准确的 `root → ui.hook`，同时腾出位置新增三条
「不许反向依赖 `ui.hook`」的禁止边（见五）。

新的分层（方向是单向的，`ui.hook` 在最上层，只往 `ui.screen` → `ui` → `ui.state` / `ui.model` 走；
2026-10-19 起「下层反向引 `ui.hook`」由 `audit-coupling.py` 逐条禁止，不再只靠人守）：

```
ui.hook  宿主页注入（换页 / 返回拦截 / 配置导入导出 / 窗口色）
   ↓
ui.screen 屏 + 装配（PageRoot 建页）
   ↓
ui       页面模型与页面行为（SettingsPage、Screen、页面组件、主题）
   ↓
ui.state 纯状态类（不扩展页面）      ui.model 纯展示数据
```

「页面行为」原先散在 `ui/state`，与「纯状态类」混住；现在 `fun SettingsPage.*` 一律在 `ui` 或
`ui.screen`，`ui/state` 里只剩不反持页面的状态类 —— 这是 `ui ↔ ui.state` 环消失的原因。

---

## 一、判据：什么叫「降耦合、高内聚」，怎么量

不定判据的重构 = 想清楚之前先动手 —— `AGENTS.md` 一、1 只放行**拿得出可量理由**的重构（说不出理由的大幅重构须用户明确要求）。所以先立六条**可 grep / 可脚本化**的判据（每条都给出当前值与目标值）：

| #   | 判据                                                              | 怎么量                                  | 现状                                                                                                           | 目标                                |
| --- | ----------------------------------------------------------------- | --------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| 1   | **进程边界**：只许出现「低层 ← 高层」的边，且注入侧零界面依赖     | 导入矩阵；`grep '^import'` 按包前缀判定 | 注入侧合格（CI 已卡）；**host → inject 6 条、host → ui 2 条**违规                                              | 违规边为 0                          |
| 2   | **包内无环**：包依赖图是 DAG                                      | 对包级导入图做环检测                    | `core` 无环；**`ui` 内部有三处环**（见三）                                                                     | `ui` 也成 DAG                       |
| 3   | **单一状态所有者**：一份状态只有一个写入者，读取者通过参数/接口拿 | 统计「谁在写某个页面级字段」            | `SettingsPage` 上有 **43 个状态字段**，被 15 个文件、约 90 个扩展成员读写                                      | 状态按域分组持有，写入者收窄到 1 个 |
| 4   | **单元规模**：单文件 ≤ 1000 行、单文件顶层声明 ≤ 20               | `wc -l` / 正则数声明                    | 4 个文件 > 1500 行；`ui/model/PageConst.kt` 有 **115 个顶层声明**                                              | 全部达标                            |
| 5   | **扩展点成本**：加「一档动作 / 一个页面」要改几处                 | 统计引用中心表的文件数                  | 加一档动作要动 **6 处以上**（`Action` / `ActionRunner` / `ActionCatalog` / `ActionIcons` / 字典 / 菜单与网格） | 收敛到 2 处（定义 + 注册）          |
| 6   | **文案与常量单口**：用户可见文案可翻译；同域常量一处定义          | 字典键集合比对；常量按文件归口          | 文案**两条路**（字典 261 键 / 硬编码 17 条在 `PageConst`）；常量散在 7 个文件                                  | 文案全走字典；常量按域分文件        |

判据 1、2 是**硬约束**（越界就是错），3~6 是**收敛方向**（允许分阶段）。

---

## 二、总览：当前依赖图与合格项

```
                        ui/                 host/                inject/
              (com.oplus.gesture.ui)  (com.oplus.gesture)     (system_server)
                        │                    │                     │
                        │  ◀── 环 ──▶        │                     │
                        ▼                    ▼                     ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │  core/  model · log · reflect · config · wire · seal · i18n       │
        └───────────────────────────────────────────────────────────────────┘
```

**包间导入条数**（源 → 目标，取前 12 条，实测）：

| 源                    | 目标                                      | 条数      | 评价                             |
| --------------------- | ----------------------------------------- | --------- | -------------------------------- |
| `ui/screen`           | `ui/model`                                | 120       | 正常（屏读展示层数据）           |
| `ui/screen`           | `ui/component`                            | 44        | 正常                             |
| `ui/component`        | `ui/model`                                | 43        | 正常                             |
| `ui/screen`           | `ui/state`                                | 31        | 正常                             |
| `ui`（根：骨架/挂载） | `ui/screen`                               | 21        | **成环**（screen → ui 根 12 条） |
| `ui/model`            | `core/model`                              | 18        | 正常（展示层读领域）             |
| `host/action`         | `core/model`                              | 14        | 正常                             |
| `core/config`         | `core/model`                              | 12        | 正常                             |
| `host/action`         | `core/log` · `core/i18n` · `core/reflect` | 8 / 4 / 2 | 正常                             |
| `host/action`         | `inject/*`                                | **4**     | **越界**（见三、A）              |
| `host`（根）          | `inject/*`                                | **3**     | **越界**（见三、A）              |
| `ui/screen`           | `host/action`                             | 3         | 预期（「试一下」共用执行器）     |

**合格项**（继续保持，别在收敛时碰坏）：

1. **注入侧零界面依赖**：`inject/` 下 grep `androidx` / `io.github.suqi8` / `io.github.lyxnx` / `ui.` 全空 —— 而且 CI 已经把这条钉死了（`tools/verify-apk.sh` 里「(5) 注入侧不得引用 …」那一段，含 `KeyModule.kt` 单列一档；`ci.yml` 与 `release.yml` 都调这个脚本）。
2. **`core/` 不认识进程、不认识界面**：`core/*` 全部导入都落在 `core/` 内 + 标准库（`core/wire → core/config` 那一条是唯一的例外，见三、C）。
3. **`core/seal`、`ui/component/*` 的单职责已经很干净**：`Seal` / `SealAssets` / `SealCertificate` 一包三件、各管一件事；`component/` 已从早先的「PageWidgets 一个文件装所有控件」拆成 15 个文件（`MenuPopup` / `ConfirmDialog` / `PageFab` / `SliderRow` / `SwitchRow` / `TileWidgets` / `Haptics` / `Visibility` / `TextMetrics` / `IconInput` / `MarkdownBody` …）。

---

## 三、待收敛的耦合（按严重度排，全部给到行号）

### A. `host/` → `inject/`：包名与进程归属不符（6 条）

```
host/action/ActionRunner.kt:10      → inject.input.KeyInjector
host/action/ActionRunner.kt:11      → inject.system.FlashlightController
host/action/ActionRunner.kt:12      → inject.system.RingerController
host/action/ActionRunner.kt:13      → inject.system.StatusBarBridge
host/action/MiniWindowLauncher.kt:13 → inject.system.FlexibleWindowBridge
host/GestureReceiver.kt:28          → inject.Framework
host/SideKeyGestureHooks.kt:13-15   → inject.Framework / inject.hookGuarded / inject.system.SystemActionButtonConfig
```

**问题不是「依赖了不该依赖的类」，而是这批类**根本不在注入侧执行**：手电筒、铃声、状态栏、小窗桥、按键注入 —— 它们的调用点全在**宿主进程\*_（`host/action/_`）。包名说「注入侧」，实际是「宿主进程也会跑的系统桥」。

这正是 `architecture-refactor.md` 一、1 记录过的老毛病（当年 `hook/` 名下混着宿主执行器）**在新结构里换了个地方复发**；那份文档四、5 甚至预先点过一句：「`SystemActionButtonConfig` 放在 `inject/` 里是因为它由注入侧代码定义，但调用点必须在宿主侧 —— 若发现调用点跑到 `inject/` 内部，说明原结构本来就错了」。

**代价**：新人（和未来的我）看包名判断进程归属，会得出**相反**的结论；而进程归属错了 = 一次 `NoClassDefFoundError` = 系统进程崩（`AGENTS.md` 五之二 全部条款都在讲这件事）。判据越依赖命名，这种命名失真就越贵。

### B. `host/` → `ui/`：执行层依赖展示层（2 条）

```
host/action/ActionNotice.kt:8 → ui.model.ActionCatalog     （执行时的 Toast 文案去查 UI 的动作目录）
host/PageRelaunch.kt:10       → ui.SettingsPageHooks       （宿主主进程去拉设置页 Activity）
```

契约上 `ui/` 与 `host/` 互相引用**是允许的**（同 APK 两个进程，见 `architecture-refactor.md` 二），但这两条方向反了：

- `ActionNotice` 依赖 `ui/model/ActionCatalog`（30KB 的**展示层**目录）只是为了取一句标题 —— **执行**要不要弹提示，是宿主业务；「这档动作叫什么」是展示层的知识。结果 `host/action` 现在离不开 `ui/model`。
- `PageRelaunch` 拿 `SettingsPageHooks.SETTINGS_ACTIVITY` 去 `startActivity`，方向是「宿主主进程 → 界面层常量」。

### C. 传递违反：`inject/` → `core/wire` → `core/config`（1 条）

```
core/wire/ConfigBroadcast.kt:5 → core.config.CommandLibrary
```

`architecture-refactor.md` 二 的硬规矩是「**`inject/` 不准碰 `core/config`**（落盘那包）—— 这样『注入侧读不到配置文件』在**编译期**就成立」。现在 `ConfigChannel`（注入侧）导入 `core/wire`，而 `core/wire` 又导入 `core/config` —— **这条编译期保证被传递依赖绕开了**：注入侧代码只要顺手写一句 `CommandLibrary.xxx`，编得过。

注意这条**当前没有实际 bug**（注入侧没直接调 `core/config`，CI 的黑名单也只查界面类），但它让「编译期契约」退化成「口头约定」。

### D. `ui/` 内部三处环（这是最大的一块）

```
① ui/screen  ◀──▶  ui/component       component → screen 有 7 条：
   component/CommandMenus.kt:21-23 → screen.{addStep, openAppPickerForStep, replaceStep}
   component/Haptics.kt:15         → screen.ActionTile
   component/PageFab.kt:37         → screen.CommandScreen
   component/PageHeader.kt:30      → screen.LaunchableApp
   component/TileWidgets.kt:18     → screen.CommandScreen
② ui/screen  ◀──▶  ui（根）           screen → ui 根 12 条（拿 SettingsPage / HostSession / …）；ui 根 → screen 21 条
③ ui/state   ◀──▶  ui/screen          state → screen 2 条；screen → state 31 条
```

「通用组件」反过来依赖具体屏，含义很清楚：**这些「组件」不是组件，是屏的私有零件被放在了公共目录**（`PageFab` 就是「自定义命令页那颗 FAB」、`TileWidgets` 就是那两个屏的格子形，`Haptics` 依赖 `ActionTile`）。公共目录里躺着别人屏的零件，于是「通用层」与「屏幕层」互相锁死，谁都动不了。

### E. `SettingsPage` 是事实上的页面级单例（43 个状态字段）

`ui/SettingsPage.kt:119` `internal class SettingsPage(...)`，字段实测 43 个，覆盖：宿主资产（`context` / `activity` / `vibrator` / `audioManager` / `rootView`）、主题（`dark`）、磨砂（`pageHazeState` / `frostDegradeLogged`）、导航（`navStack` / `navBackInput`）、配置（`config` / `configReadOnly` / `commands` / `commandsReadOnly`）、界面度量（`bindingBandHeight` / `commandBandHeight` / `appSearchBandHeight`）、搜索与筛选（`appFilter` / `showSystemApps` / `showUnopenable`）、草稿（`draft` / `draftResetPending` / `draftResetAborted` / `draftResetBaseline`）、确认窗与浮层（`confirmReset` / `confirmLeaveCommands` / `appPickerGesture` / `appPickerForStep` / `appPickerReplaceStep`）、宿主回调（`onSystemThemeChanged` / `onBackInterceptionChanged` / `onOpenStock` / `onExportLog` / `onExportConfig` / `onImportConfig`）……

扩展成员（`fun/val/var SettingsPage.xxx`）**约 90 个，散在 15 个文件**：

| 文件                         | 扩展成员数 | 文件                       | 扩展成员数 |
| ---------------------------- | ---------- | -------------------------- | ---------- |
| `ui/screen/CommandScreen.kt` | 16         | `ui/screen/HomeScreen.kt`  | 7          |
| `ui/state/ConfigActions.kt`  | 16         | `ui/theme/PageTheme.kt`    | 5          |
| `ui/screen/AppPicker.kt`     | 9          | `ui/state/LinkCheck.kt`    | 4          |
| `ui/component/PageHeader.kt` | 7          | `ui/state/PageFeedback.kt` | 4          |
| `ui/screen/BindingScreen.kt` | 7          | （其余 6 个文件）          | 1~3        |

后果不是「不好看」，是三条实打实的：

1. **写入者不可枚举**：想知道「谁改过 `draft`」，得把 15 个文件翻一遍；
2. **状态与生命周期脱钩**：页面级字段活在页面对象上 ⇒ 页面出栈它还在（这类坑项目里已经踩过多次，比如「页面级状态不再随页面出栈注销」的注释不止一处）；
3. **新屏开发默认写成「再挂一个字段」**，于是 43 只会继续涨。

### F. 巨型文件与「常量垃圾抽屉」

| 文件                                                 | 字符数         | 顶层声明 | 主要东西                                                                       |
| ---------------------------------------------------- | -------------- | -------- | ------------------------------------------------------------------------------ |
| `ui/screen/CommandScreen.kt`                         | ~88k           | 27       | 自定义命令页全屏 + 工具栏 + 步骤卡 + 表单 + 保存/试运行/增删步骤               |
| `ui/SettingsPage.kt`                                 | ~113k（字节）  | —        | 页面骨架 + 43 字段 + 挂载 + 转场 + 覆盖层编排                                  |
| `ui/screen/BindingScreen.kt`                         | ~67k           | 10       | 手势页 + pager + 分段控件 + FAB                                                |
| `ui/screen/AppPicker.kt`                             | ~53k           | 23       | 选择应用页 + 列表 + 两张拦人窗                                                 |
| `ui/model/PageConst.kt`                              | ~26k           | **115**  | 几何 / 颜色与 alpha / **17 条硬编码文案** / 7 条外链 / 行数常量 / 3 个标签函数 |
| `host/action/MiniWindowLauncher.kt`                  | ~58k（字节）   | —        | 小窗启动整套                                                                   |
| `bridge/FlexibleWindowBridge.kt` / `ProbeChannel.kt` | ~51k（字节）各 | —        | 小窗桥 / 诊断探针通道                                                          |

`PageConst.kt` 的 115 个声明聚了六类毫不相干的东西（`STEP_MS` 与 `DISCLAIMER_TEXT` 是邻居），是典型的**垃圾抽屉**：任何一个页面改一个边距，diff 都落在这个文件上。

### G. 文案两条路

- 字典路：`core/i18n/{I18n,Strings,DictionaryLoader}.kt`，`Strings` 261 个键、22 个文件引用，CI 已校验「两份字典键集合一致」。
- 硬编码路：`ui/model/PageConst.kt` 里 17 个 `internal val …: String`（免责声明、免费声明、不可用提示 …），以及各屏里直接写的中文。**这些翻不了**，于是「中英双语」是打折的。

### H. 磨砂（Haze）状态也是页面级共享

`dev.chrisbanes.haze:haze:2.0.1` 已成为唯一磨砂机制（此前的 `drawBackdrop` 取样那套已撤）。`SettingsPage.pageHazeState` 被 5 个文件读：`ui/SettingsPage.kt`、`ui/component/{PageHeader,MenuPopup,ConfirmDialog}.kt`、`ui/screen/CommandScreen.kt`。

这本身是**正确**的收敛（一个取样源，谁要用谁读）—— 但它是**跨包直接读字段**的形态，属于判据 3 的欠账：理想形态是「取样源由页面提供、组件通过参数或 CompositionLocal 消费」，这样组件才不依赖页面对象本身。

---

## 四、收敛方案（逐条：改什么 / 为什么 / 风险 / 判据）

> 原则（`AGENTS.md` 一、2 与 `architecture-refactor.md` 五）：只做「让依赖方向成立」的动作，不做审美重构；不拆 Gradle 模块、不引 DI、不引 ViewModel。

### 动作 1：把「宿主进程也会跑的系统桥」迁出 `inject/` → 新包 `bridge/`

- **怎么做**：新建 `bridge/`，迁入 `inject/system/{StatusBarBridge, RingerController, FlashlightController, FlexibleWindowBridge, SystemActionButtonConfig}` 与 `KeyInjector`；`inject/` 只留**只在 system_server 执行**的东西：`KeyHooks` / `Hooks` / `SideKeyInterceptor` / `ConfigChannel` / `input/{GestureDetector,GestureForwarder}`。
  （⚠️ 落地时与这段计划有一处出入：`Framework` **也**迁到了 `bridge/`，没有留在 `inject/` —— 见十一批的实际清单。
  另外 **2026-10-17 又按进程归属分了子包**：`bridge/host/`（只有宿主侧引用：`KeyInjector` / `RingerController` /
  `FlashlightController` / `SystemActionButtonConfig`）与 `bridge/inject/`（只有注入侧引用：`SystemContext`），
  中间那个 `bridge/` 留两侧共用的 `Framework` / `Hooks` / `StatusBarBridge` / `FlexibleWindowBridge` 一族。
  理由：**包名要等于进程归属** —— 按包名判「这段会不会在 system_server 里跑」原来会判错，
  CI 的注入侧扫描范围也跟着失准；现在那条扫描要 `--exclude-dir=host`。）
- **怎么判定谁该走**：一句话判据 —— **调用点在宿主侧（`host/`、`ui/`）的，一律不是注入侧**。（`Framework` 的候选表要留在 `inject/`，它是注入侧的降级表；但 `host` 读它做手势劫持这件事本身要重新审视：`SideKeyGestureHooks` 在宿主里用的是同一张候选表 ⇒ 更干净的做法是把「宿主与注入共用的候选常量」下沉 `core/model`，让两边各自读表。）
- **收益**：包名重新等于进程归属；`host → inject` 6 条边清零；`SystemActionButtonConfig` 的「只能在宿主进程调」变成结构上显然。
- **风险**：`host` 与 `inject` 之间的历史注释很多，迁移时**只改 `package`/`import`**，注释原样带走（沿用 `architecture-refactor.md` 六 的纪律）。系统侧代码换包不影响 CI 的 `(5)` 那条黑名单（它按 `inject/` 路径扫），但**要同步更新 CI 的 `injection_dirs` 与新增 `bridge/` 的允许依赖**。
- **判据**：`grep -rn '^import com.lookie.opluskey.inject' app/src/main/java/com/lookie/opluskey/host/` 为空；`grep -rn '^import com.lookie.opluskey.bridge' inject/` 只允许出现「注入侧确实也调」的那几条（应为空或白名单化）。

### 动作 2：切断 `host → ui` 两条边

- `host/action/ActionNotice.kt`：把「这档动作叫什么」从 `ui/model/ActionCatalog` 移到**宿主可用**的位置。两种做法，取其一：
    1. 通知文案改由**动作自己**给（`Action` 上已有 `token`，再加一个展示名）；
    2. 或者让 `ActionNotice` 走 `core/i18n` 的字典键（文案本来就是可翻译的），`ActionCatalog` 只负责「网格/菜单怎么摆」。
       推荐 2：它与判据 6（文案单口）同向，且不新增领域字段。
- `host/PageRelaunch.kt`：`SETTINGS_ACTIVITY` 这个**宿主 Activity 全限定名**本就不是界面层知识（界面层只是恰好也用到它），下沉到 `core/model/HostApp.kt`（那里已经有宿主包名常量），两边各自引用。
- **判据**：`grep -rn '^import com.lookie.opluskey.ui' app/src/main/java/com/lookie/opluskey/host/` 为空；页面仍能按 `ACTION_PAGE_RELAUNCH` 正常重进（真机判据）。

### 动作 3：打断 `core/wire → core/config`

- **怎么做**：`ConfigBroadcast` 需要的只是「命令库怎么序列化/反序列化」，把那份编解码从 `core/config/CommandLibrary.kt` 里**下沉到 `core/model`**（`CommandLibrary` 自身留在 `core/config` 做文件读写），或让线格式只传原始字符串、由接收侧自己解析。
- **判据**：`grep -rn '^import com.lookie.opluskey.core.config' app/src/main/java/com/lookie/opluskey/inject/ core/src/main/java/com/lookie/opluskey/core/wire/` 为空 —— 这才让「注入侧读不到配置文件」重新变成**编译期**事实。
- **风险**：线格式一动就是跨进程协议，必须三处同步（发送方 / 接收方 / 判据日志），并且**待真机验证**（`AGENTS.md` 二、1.2）。

### 动作 4：`ui/` 破裂环（component 不许依赖 screen）

- **怎么做**：把 `component/` 里那几个「别人的屏的零件」搬回它所属的屏（`PageFab` → `ui/screen/PageFab.kt` 或直接进 `CommandScreen`；`TileWidgets` / `Haptics` 里只服务某一屏的部分同理），把**真正跨屏**的东西留在 `component/`（`PageHeader` / `MenuPopup` / `ConfirmDialog` / `SwitchRow` / `SliderRow` / `Visibility` / `TextMetrics`）。
- 被 `component` 引用的屏级类型与函数（`LaunchableApp` / `ActionTile` / `addStep` / `replaceStep` / `openAppPickerForStep`）按归属下沉：类型进 `ui/model`，函数留在屏内，**屏之间的调用改走页面对象上的显式接口**（见动作 5）。
- **判据**：`grep -rn '^import com.lookie.opluskey.ui.screen' app/src/main/java/com/lookie/opluskey/ui/component/` 为空。

### 动作 5：拆 `SettingsPage`（分域持有，不是「搬去 ViewModel」）

目标形态：`SettingsPage` 只留**宿主资产 + 导航栈 + 转场编排**，其余状态按域分对象持有，每个域对象**只有一个写入者**：

```
SettingsPage           宿主 context/activity/rootView、navStack、页面骨架挂载
├── PageChromeState     主题 night、Haze 取样源、状态栏/顶栏高度度量、宿主回调钩子
├── CommandDraftState   draft / 重置待办 / 新建-编辑判定 / 保存与试运行
├── PickerState         搜索词、两个筛选开关、小窗开关、选中的应用、两张拦人窗
└── ConfigState         config / commands / 两个只读态 / 写盘与自检
```

**实施顺序**（每一步都能单独编译、单独回滚）：

1. **先冻结写入面**：把今天散在各屏的 `page.xxx = …` 逐处标注「写入者」，产出清单（这一步不改代码，只产出事实）；
2. **搬一个域**（建议先 `PickerState`：它最独立，出栈即失效，收益立刻可见）；
3. 再搬 `CommandDraftState`（最大、但边界清楚：进/出「自定义命令」页各一次）；
4. 最后 `ConfigState` 与 `PageChromeState`。

- **风险**：这是全项目最大的一次内部改动，**禁止一把梭**；每个域搬完都要跑 `spotlessCheck detekt lintDebug` + `assembleDebug`，并真机过一遍该域的功能（至少：进出页不丢状态、出栈会清干净）。
- **判据**：`grep -c '^    internal var \|^    internal val ' ui/SettingsPage.kt` 从 43 降到 ≤ 15；每个域对象只有一个文件的写入者。

#### 动作 5 的第 1 步产出：写入者清单（2026-10-17 实测）

口径：只在**声明了 `SettingsPage` 扩展**的文件里数**裸**字段名（那些文件用隐式接收者读页面状态），
外加任何文件里显式的 `xxx.<字段> = …` 赋值 —— 这样才不会把别处同名的局部量（`context` / `screen`
这类词到处都是）算进来。

- 声明 `SettingsPage` 扩展的文件 **20 个**（就是读页面状态的那一圈）。
- **只有一个写入者**的字段 **16 个**：`dark` · `config` · `configReadOnly` · `commandsReadOnly` ·
  `commands` · `bindingBandHeight` · `commandBandHeight` · `appSearchBandHeight` · `draftResetPending` ·
  `draftResetAborted` · `draftResetBaseline` · `confirmReset` · `confirmLeaveCommands` · `pendingDraftLoad` ·
  `hostServiceUp` · `injectStatus`。
- **跨文件写入者 3 个**：`context`（`DonateDialog` / `AboutScreen` / `DonateListScreen`）、
  `activity`（`BackInterception` / `SettingsPageHooks`）、`frostDegradeLogged`（`MenuPopup` / `CommandScreen`）。
  前两个是 `val`（本该只读）⇒ 那几处命中多半是同文件里的同名局部量，搬之前人工过一眼；
  `frostDegradeLogged` 是真的多写入者（谁先降级谁置位，属「多处置同一件事为真」）。
- **扫不到外部写入的 18 个**：在 `SettingsPage.kt` 内部写 / 只读 / 由固定回调写 ——
  `vibrator` · `audioManager` · `pageHazeState` · `navStack` · `screen` · `navBackInput` · `rootView` ·
  `tileIcons` · `picker` · `draft` · `debugGesture` · `hostAliveToken` · `injectAliveToken` · `hostEnabled` ·
  `hostLastActionAt` · `hostServiceSnackbar` · `pendingOwners` · `fallbackOwners`。

按这张清单，剩下的域可以逐域搬（每域一次真机过一遍）：**草稿**（`draft` 一族 4 个）、
**链路 / 报活**（`hostServiceUp` · 两个 `*AliveToken` · `injectStatus` · `hostEnabled` ·
`hostLastActionAt` · `hostServiceSnackbar`，共 7 个）、**配置**（`config` · `commands` · 两个只读态）。

### 动作 6：按屏拆巨型文件

每个屏一个「屏文件 + 该屏私有零件文件」，屏文件里只留「布局 + 状态接线」：

```
ui/screen/command/
├── CommandScreen.kt     屏骨架（pager 接线、顶栏、整页转场）
├── CommandForm.kt       名称/命令输入/格式提示/分类档（现在在 CommandScreen 里）
├── CommandSteps.kt      步骤卡（SerialSteps 那一大段）
├── CommandToolbar.kt    底部工具条（CommandActions）
├── CommandList.kt       已保存命令列表与格子
└── CommandIo.kt         保存/试运行/引用检查/去重（现在混在屏里）
```

- **判据**：屏目录下每个文件 ≤ 1000 行；`ui/screen/*.kt` 顶层文件不再出现 > 1500 行的。
- **收益**：这是**评审成本**问题 —— 88k 字符的文件里，任何一次改动都要在 20+ 个顶层声明之间定位上下文。

**落地情况（2026-10-17）**：命令页已经按这个形状切完，实际落成四块（比上面这张拟案少两块：
表单与「工具条 + 已保存列表」各自能在一起读，硬拆反而要把共用的局部状态搬来搬去）：

```
ui/screen/command/CommandScreen.kt    904 行   屏骨架 + 本页度量常量 + StepRowItem（这一页共用的行数据）
ui/screen/command/CommandSteps.kt     398 行   步骤卡（SerialSteps / moveStep）
ui/screen/command/CommandToolbar.kt   231 行   底部工具条（CommandActions / commandToolbarVisible）
ui/screen/command/CommandLibrary.kt   339 行   已保存命令那张卡 + 保存/试跑/删/重名与环检查
```

其余四个页（`BindingScreen` 1527 · `AppPicker` 1189 · `HomeScreen` 680 · `AboutScreen` 538 · `DonateListScreen` 235）
按同一套做法搬：**换目录 + 同包搬迁**，`SettingsPage` 那边改 import；
四块各自的 import 用「这段正文里出现过这个符号」筛出来，别整份抄（否则每块开头都是一百多行没用的 import）。
`private` 的顶层声明在跨块要用时放宽到 `internal`；detekt 基线里「同一个函数换了文件」的条目跟着挪路径。

### 动作 7：`PageConst` 按域拆开 + 文案全部走字典

```
ui/model/PageMetrics.kt   几何与边距（SLIDER_* / PAGE_* / TILE_* / ROWS / COLUMNS …）
ui/model/PageColors.kt    颜色与 alpha（FROSTED_* / POPUP_* / DIALOG_* / DISABLED_ALPHA …）
ui/model/PageLinks.kt     外链与身份（REPO_URL / AUTHOR / ISSUE_URL / TELEGRAM_URL …）
core/i18n/Strings.kt      17 条硬编码文案 → 键（并补两份字典）
ui/model/PageLabels.kt    3 个标签函数（gestureLabel / commandKindLabel / languageLabel）
```

- **判据**：`PageConst.kt` 消失或只剩「跨域共享的极少数」；`grep -rn 'internal val .*: String = "' ui/model/` 为空（文案不再硬编码）。
- **注意**：文案进字典要同时改 `Strings.kt` + `assets` 两份字典，CI 的「(6) 字典覆盖」会替你把漏项挑出来。

### 动作 8：新增动作的扩展面收敛到 2 处

现在加一档动作要动 `core/model/Action.kt`（定义）、`host/action/ActionRunner.kt`（执行分支）、`ui/model/ActionCatalog.kt`（目录与网格）、`ui/model/ActionIcons.kt`（图标）、`core/i18n`（文案）、`ui/component/CommandMenus.kt`（菜单项）。做到「定义 + 注册」两处的关键是把后四项**变成一处注册表**：

- `ActionCatalog.Spec` 已经承担了「目录项」的角色（`ui/model/ActionCatalog.kt`，被 10 个文件引用）—— 让它成为**唯一注册处**：图标、文案键、分类、网格位置都在同一条 `Spec` 里；
- 执行分支仍是 `when`，但**分支数 = 动作档数**这件事要显式化：`ActionRunner` 用穷尽 `when`（无 `else`），这样漏一档会在**编译期**报出来（比运行时静默降级强）。

---

## 五、目标结构（含「禁止边」白名单）

```
com.lookie.opluskey
├── KeyModule.kt                 入口（java_init.list 写死，不许动）
├── core/                        model · log · reflect · config · wire · seal · i18n
├── bridge/                      ← 宿主进程也会跑的系统桥（状态栏/铃声/手电筒/小窗桥/按键注入）
│                                 + 两侧都读的**查询与解析**：分身目录 / 入口解析 / 小窗支持判定
│   ├── host/                    ← 只有宿主侧引用（包名 = 进程归属，2026-10-17 分）
│   └── inject/                  ← 只有注入侧引用
├── inject/                      只在 system_server 执行：拦截 · 手势判定 · 配置接收 · 候选表
├── host/                        宿主主进程：接手势 · 跑动作 · 页面重进
└── ui/                          宿主设置页进程：骨架 · screen/ · component/ · model/ · state/ · theme/
```

**禁止边清单**（建议进 CI，见「七」）：

| 源              | 禁止                                                                                         | 理由                                                          |
| --------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `inject/`       | `androidx.*` · `io.github.suqi8.*` · `io.github.lyxnx.*` · `ui.*` · `host.*` · `core/config` | 已有 CI 黑名单 + 本文动作 3 补传递依赖                        |
| `host/`         | `inject.*` · `ui.*`                                                                          | 本文动作 1、2                                                 |
| `ui/component/` | `ui/screen.*`                                                                                | 本文动作 4                                                    |
| `ui/state/`     | `ui/screen.*`                                                                                | 同向（状态不许反过来认识屏）                                  |
| `ui/screen/`    | `ui.hook.*`                                                                                  | 分层图单向：hook 装页面，页面不认识 hook（2026-10-19 补）     |
| `ui/state/`     | `ui.hook.*`                                                                                  | 同上                                                          |
| `ui/`           | `ui.hook.*`                                                                                  | 同上（`SettingsPage` / `Screen` / 页面模型都不认识 hook）     |
| `ui/`           | `host/action.ActionRunner`（`CommandLibrary` / `TestRunnerCache` 两处除外）                  | 执行器归宿主；页面只借它跑一次「测试命令」                    |
| `core/`         | 任何上层包                                                                                   | 已有事实（唯一例外是 `core/wire → core/config`，动作 3 收掉） |
| `KeyModule.kt`  | 除 `core/*` 之外的一切 —— **例外**：`host.GestureReceiver` / `host.SideKeyGestureHooks` /    |
|                 | `inject.*` / `ui.hook.SettingsPageHooks`                                                     | 它在两侧都被加载，入口必须按进程分派（CI 已单列一档）         |

---

## 六、分阶段实施与绿灯判据

**进度（2026-10-16）**：动作 1（拆 `bridge/`）、2（切 `host → ui`）、3（打断 `core/wire → core/config`）、
4（UI 破裂环）、7 的前半（`PageConst` 按域拆开）已落地，依赖方向四条判据 grep 均为空、三道闸全过。
第二批（跨进程常量 / ROM 三表 / 混淆后缀 / 配置字段的表驱动）同样已落地，逐项的前后值：

| 收敛项                                                                                                                                            | 对应判据                   | 之前                                                                                              | 之后                                                                                  |
| ------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| 小窗跨进程常量（`WINDOWING_MODE_ZOOM` · 两个 `ActivityOptions` key · `extra_window_mode` · 两个尺寸档）                                           | 6                          | `= 100` 三处、`mZoomLaunchFlags` 两处、`windowMode` 两处、`extra_window_mode` 两处，散在 3 个文件 | `core/model/ZoomWindowWire` 一处，其余按名字引用                                      |
| `FlexibleWindowBridge` 的框架语义数字（`switchState` 目标态 / `mScenario` / `mFlexibleZoomState` / caption 场景档 / `anyTaskForId` 的 matchMode） | 6                          | 裸数字（`1` 在三个不同含义上出现，看代码分不出哪个是哪个）                                        | 5 个具名常量 + 判据注释                                                               |
| 宿主内部 Handler 的混淆后缀（`XxxService$a`）                                                                                                     | 1（降级纪律五、2 / 五、3） | 单值，猜错只剩一句「没找到 Handler 类」                                                           | 候选列表 + 逐个试 + 命中留痕 + 全落空时报出试过哪些                                   |
| ROM 档位登记（档位名 / 验证记录）                                                                                                                 | 1                          | `Framework.HOOK_SETS` 与 `RomProfile.PROFILES` 各写一遍                                           | `RomProfile` 一处登记，另一张按 `romSeries` 反查；安装期新增一行「三张表对账」        |
| 配置字段（名字 / 类型 / 默认值 / 钳制范围）                                                                                                       | 5                          | 加一个字段要改 5 个文件，其中钳制上下限在两个通道各写一遍                                         | 加一个字段改 2 个文件（`ConfigFields` 表 + `Config.kt` 的字段映射），两条通道按表生成 |

**进度（2026-10-17）**：第三批 = 动作 5 的第 1 步（上面那张写入者清单）+ 第 2 步的第一个域 ——
新增 `ui/state/PickerState.kt`，把「选择应用」页那 7 个字段（搜索词 / 两个落盘的筛选开关 / 小窗开关 /
这趟为谁挑的三种来源）从 `SettingsPage` 搬进一个域对象，调用点一律加 `picker.` 前缀（**同一 token +
接收者**的纯机械变换，73 行），`SettingsPage` 字段 **43 → 37**、文件 1757 → 1683 行；
三道闸与 `assembleDebug` 全过。**未做**：动作 5 剩下的三个域（草稿 / 链路报活 / 配置）、动作 6（按屏拆巨型文件）、
动作 7 的文案那半（复查后已达标：`PageCopy` 里 21 条全部走 `I18n.get(键, 中文兜底)`，判据 `grep 'internal val .*: String = "' ui/model/` 为空）、
动作 8 的图标那一项（理由见下）。

**进度（2026-10-17 晚）**：动作 5 的四个域全部搬完（`AppPickerState` · `CommandDraftState` · `HostLinkState` ·
`ConfigState`），`SettingsPage` 字段 **43 → 25**（剩下的 25 个是宿主资产、导航栈、度量、覆盖层编排与宿主回调 ——
正是本文目标形态里要留在页面上的那些）；动作 6 的六个屏目录已就位（`command/` `binding/` `apppicker/` `home/`
`about/` `donate/`），其中命令页已切成四块。三道闸 + `assembleDebug` 全过。
**界面层已完成**：`ui/` 下不再有 > 1000 行的文件（`SettingsPage.kt` 1591 → 887，另出 `SettingsPageView.kt` / `SettingsPageDebug.kt`；六个屏各自成目录并切成 3~4 块）。
**已拆（2026-10-18 复核）**：注入侧 / 宿主侧那三个 > 1000 行的文件都拆完了 —— `ProbeChannel`
（`ProbeCall` / `ProbeOps`）、`MiniWindowLauncher`（`MiniWindowFacts`）、`FlexibleWindowBridge`
（`FlexibleAnim`）。全仓现在没有 > 1000 行的文件，最大的是 `ui/model/ActionIcons.kt` 714 行。
**未做**只剩动作 8 的**图标**那一项（见下）。

动作 8 剩下那一项为什么不动：执行分支已经是**穷尽 `when`**（漏一档在编译期就报错），「定义 + 注册」
里的注册也已经落在 `ActionCatalog.Spec` 一处；唯一还在 `ui/model/ActionIcons` 里按 token 分派的是**图标**。
把它塞进 `Spec` 只有两条路，两条都要先跟用户对一次：1) 让 `core/model` 认识图标库的类型（违反
`AGENTS.md` 一、1「注入侧零界面依赖」的判据来源 —— `core` 是注入侧会加载的包）；2) 只放一个「字形名」
枚举进 `Spec`，那就得同时把 `CommandText.stepIcon` 的取值也改走清单 —— 那会**换掉步骤行现在显示的字形**
（入口格那几档：步骤行从具体取值那颗变成格子那颗），是可见的行为变化，不属于「结构收敛」。

**进度（2026-10-17 深夜，分身那一批）**：把「应用多开」的**执行面**从 `core/model` 挪到
`host/action`（`AppCloneCatalog` / `AppLaunchAsUser`）—— 列分身用户、按用户查包、按用户起界面三件事
都要 `Context` 与 hidden API，属平台适配与执行，不再让 `:core`（注入侧也加载的那一层）编得到它们。
同一批把界面层那两条私有面也收了：`ui/screen/apppicker` 不再调 `IPackageManager` 与
`createContextAsUser`，只从 `AppCloneCatalog` 拿「包名 / 名字 / 槽号」。
槽号与分身显示名各收成一处（`AppCloneCatalog.slotOf` · `ui/model.cloneLabel`），
快捷方式的目标写法也只剩 `Action.AppShortcut.plainTarget` 一处。
（`AppCloneCatalog` 在 2026-10-18 那一批又进了 `bridge/`：它是**查询**，不是执行 —— 见下。）

**进度（2026-10-18 凌晨，降耦合收口）**：包级环 **1 → 0**、包级边 **144 → 142**、`ui → host` **5 → 2**。
三处搬家，全部是「类型放错层」，没有改任何行为：

1. `AppPickerUiState`（连同 `SmallWindowBlock`）从 `ui/screen/apppicker` 进 `ui/state` —— 它与
   `ui → ui.screen.apppicker` 是同一个环的两条边。`rememberAppPickerUiState` 留在页面层
   （`SettingsPage.kt` 末尾），不跟着进 `ui/state`：它是 `SettingsPage` 扩展，跟进去会让
   `ui.state → ui` 把环换个方向长回来。
2. `appNameOf` / `appLabelOf` 从 `ui/screen/apppicker/AppNames.kt` 进 `ui/AppNaming.kt` —— 前者被
   `binding` / `command` 两处 / 顶栏 / 页面分派共 5 处引用，挂在某一个屏的包里等于让其余屏反向依赖那个屏。
3. `AppCloneCatalog` / `AppEntry` / `MiniWindowSupport` 从 `host/action` 进 `bridge/` —— 三者都是
   **查询与解析**（列分身、包名 → 入口、支不支持小窗），两个进程都读。搁在执行层时页面层只能
   `ui → host`。顺带给 `cloneUsersInDisplayOrder` 加了一份「只记非空结果」的进程内缓存：拼名字那条路
   是逐行调的，每次重列用户都要走 `UserManager#getUsers` 的反射 + binder。

余下 `ui → host.action.ActionRunner` 两处（`CommandLibrary` / `TestRunnerCache`）**不搬**：那是
「测试命令」按钮在页面进程里跑一次动作，执行器本身归宿主，见禁边表里新加的那一行。

**动作 8 的实际成本（`appshortcut:` 那一档的实测）**：加一档动作动了 6 个文件 ——
`core/model/Action.kt`（定义 + 解析）、`host/action/ActionRunner`（穷尽 `when` + 执行器）、
`host/action/ActionNotice`（执行提示文案）、`ui/model/ActionIcons`（字形）、
`ui/model/CommandText`（步骤名）、`ui/screen/binding/BindingGrids`（小窗标记与小字）。
菜单与目录（`ActionCatalog`）没动 —— 这一档被当成「打开应用」族的更具体形态，不是独立菜单项。
所以判据 5 的「2 处」仍差 3 处 `when` 分派，**不是只有上面说的「图标」一项**；
估下一档动作的改动面时按这 6 处算，别按「定义 + 注册」算。

每个阶段=一次提交=一个 `changelog/` 片段（`AGENTS.md` 一、三）；每阶段结束都跑：

```bash
./gradlew spotlessCheck detekt lintDebug      # 格式 / 静态检查 / Lint
./gradlew :app:assembleDebug                  # 产物
# 依赖方向（本文判据 1、2）—— 脚本见「七」，尚未落地；落地前先用那里的 grep 清单人工过一遍
```

| 阶段 | 内容                                                          | 触及文件 | 风险                             | 真机判据                                     |
| ---- | ------------------------------------------------------------- | -------- | -------------------------------- | -------------------------------------------- |
| 1    | 动作 3（`core/wire → core/config`）+ 动作 2（host → ui 两条） | ~5       | 低（线格式要三方同步）           | 改配置立即生效、页面仍能重进                 |
| 2    | 动作 1（拆 `bridge/`）                                        | ~12      | 中（换包后必须整机重启才验得到） | 侧键三档手势、手电筒/铃声/状态栏、小窗       |
| 3    | 动作 4 + 6 + 7（UI 破裂环、拆巨型文件、拆常量与文案）         | ~20      | 低（纯搬迁，但要防「顺手整理」） | 五页都能进、浮层/确认窗/工具条/FAB 正常      |
| 4    | 动作 5（拆 `SettingsPage` 状态域）+ 动作 8（动作注册表）      | ~15      | **高**（状态搬迁最容易丢状态）   | 每搬一个域过一遍该域：进出页不丢、出栈清干净 |

阶段 4 之后，判据 3、5 才有望达标；前三个阶段都能独立交付价值。

---

## 七、防回归：把判据做成脚本（建议，尚未落地）

建议新增 `tools/audit-coupling.py`（逻辑就是本文审计时跑过的那份，已实测可用），在 CI 里加一步。**在这份脚本落地之前**，判据 1、2 先用下面这几条 grep 人工过（就是 CI 现有风格的延伸）：

```bash
# 注入侧：已有 CI 项，另补一条「不许碰落盘那包」
grep -rn '^import com.lookie.opluskey.core.config' app/src/main/java/com/lookie/opluskey/inject/
grep -rn '^import com.lookie.opluskey.core.config' core/src/main/java/com/lookie/opluskey/core/wire/
# host 不许依赖注入侧与界面层
grep -rn '^import com.lookie.opluskey.inject' app/src/main/java/com/lookie/opluskey/host/
grep -rn '^import com.lookie.opluskey.ui'     app/src/main/java/com/lookie/opluskey/host/
# 通用组件不许依赖具体屏
grep -rn '^import com.lookie.opluskey.ui.screen' app/src/main/java/com/lookie/opluskey/ui/component/
grep -rn '^import com.lookie.opluskey.ui.screen' app/src/main/java/com/lookie/opluskey/ui/state/
```

脚本化之后应当做到：

```python
# 1) 包级导入图：包 = 源码目录的前两级；对 com.lookie.opluskey.* 的 import 记边
# 2) 环检测（DFS），有环即 exit 1 并打印环上的文件与行号
# 3) 禁止边白名单（见「五」表）逐条 grep，越界即 exit 1
# 4) 单元规模：单文件 > 1500 行 / 顶层声明 > 20 打印警告（不阻断，先看趋势）
# 5) 打印本文判据 1~6 的当前值，作为「趋势线」留在 CI 日志里
```

配套两条**软**约束（不阻断，进 review 清单）：

- `AGENTS.md` 已有「新代码不许再往 detekt 基线里加条目」——**同理**：新增文件不许把「越界边」当既成事实抄进白名单，白名单只允许缩短。
- 每次拆完一个域/一个文件，`changelog/` 片段里那条要点要写清「判据的数字从 X 变成 Y」（例如「`SettingsPage` 字段 43 → 21」），让收敛可度量。

---

## 八、明确不做（与 `architecture-refactor.md` 五 保持一致）

| 做法                         | 为什么不做                                                                                                                            |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| 拆 Gradle 多模块             | **已有例外**：`core/` 已独立成 `:core`（两进程共用的那一层，值得编译期约束）。其余各包仍留 `:app` —— 拆它们要重排可见性，风险大于收益 |
| MVVM / ViewModel / StateFlow | 本模块没有自己的 Activity，ViewModel 的宿主得自己伪造；加一层壳换不来东西                                                             |
| DI（Hilt / Koin）            | 注入侧零依赖与注解处理器冲突（system_server 类加载期可达性）                                                                          |
| Repository / DataSource 抽象 | 数据源只有宿主私有目录里两份文件，抽象即空壳                                                                                          |
| 为「好看着更整齐」的目录搬家 | `AGENTS.md` 一、1：挪目录 / 文件属**大幅重构**，拿不出可量理由的须用户明确要求 —— 本文每条动作都对应一个可量的判据                    |

---

## 九、已知局限

1. 本文**只静态审计**（导入矩阵、声明计数、字段清单），没有跑真机；凡是涉及运行期行为的结论（动作 2、3 的语义、阶段 4 的状态搬迁）都必须在真机上验一遍才算数（`AGENTS.md` 二、1.2）。
2. 「谁是某个状态的写入者」这一步没做完 —— 它是阶段 4 的第一步（产出清单，不改代码），本文只给了方法，没给清单。
3. `ProbeChannel.kt` / `MiniWindowLauncher.kt` / `FlexibleWindowBridge.kt` 三个 > 1000 行的文件
   **已按判据 4 补做审计（2026-10-17）**，结论是「内部本来就分了好几个清晰段落，可以按段拆」，切法如下：
    - `inject/ProbeChannel.kt`（1091 行，`internal object`，31 个成员）：天然按 op 分段 —— 接收侧两条
      （`handle` / `installInProcess`）、`dump` / `call` / `state` / `corner` / `dress` / `zoom`，以及尾部那 10 来个
      「拿任务 / `TaskExt` / Service / `SurfaceControl`」的小工具（约 160 行，文件里自己写着「与
      `FlexibleWindowBridge` 同源，避免跨对象耦合」）。**最独立的一段是那族小工具**，先把它搬出去
      （同包顶层 / 扩展），与 `FlexibleWindowBridge` 里的同名工具合成一处。
    - `host/action/MiniWindowLauncher.kt`（1044 行）：**已搬**「验收与几何」那族（8 个成员）→ `MiniWindowVerify.kt`，
      1044 → 877 + 185（2026-10-17）
    - `bridge/FlexibleWindowBridge.kt`（1032 行）：`handle` 里那两块 —— 「本地那条 `switchState`」（254~470）与
      「原生动画器直调」（471~624）—— 是两套互不相干的流程，可各自成同包扩展；`send` / `sendMiniAnim`
      与几何小工具留在骨架。预计 1032 → 350 + 300 + 370。
    - **为什么停在审计**：这三个都在**注入侧 / 宿主执行侧**，改完必须真机过一遍（一、11 与 二、1.2）；
      眼下设备没连上，而这几个文件任何一环出错的表现是「按了没反应」甚至开机循环（五之二）——
      切法已经写在这里，设备回来即可按上面的顺序动手。
4. 依赖条数是「import 行数」，不是「耦合强度」的完备度量：一条 `import` 背后可能是 1 个常量，也可能是 20 个符号。用它做趋势跟踪足够，做局部决策时要看具体符号。

---

## 十、层内耦合：脚本看不见、只能靠人守的那几条

判据 1、2（禁止边 / 无环）管的是**包与包**的方向。有一类耦合它结构上看不见 ——
同一个包里的两个文件互相认识，图上没有边，但语义上仍然是「谁依赖谁」。下面这几条是实测
存在、且**有正当理由**的，写在这里是为了下次改动时不再重新论证一遍，也别顺手把它们拆坏。

### 1. `ui/screen/PageContent.kt` 认识 COUI 的 `COUIPopupHost`

```kotlin
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
...
popupHost = { COUIPopupHost(); Box(...) { PageSnackbars() } }
```

`popupHost` 是 `Scaffold` 给的一个**槽**：默认值就是 `COUIPopupHost()`。我们要的东西
（提示条压在弹窗之上）在默认值**里面**，所以只能改写这个槽、把默认那几个 composable
自己再摆一遍 —— 这是「用槽」的固有代价，不是「依赖库内部实现」。

**为什么值得**：`COUIPopupHost` 是库**公开**的入口（`COUIPopupUtils` 的伴生成员），
不是反射 / 内部类；而槽的存在就是为了让调用方改写。反过来（继续用 `snackbarHost` 槽、
想办法把提示条抬到弹窗之上）没有任何 `Scaffold` 层面的做法 —— 库在 `layout {}` 里把
`popup` 放在**最后** `place()`。

**风险与守则**：这个 import 是「跟着库版本走」的，库若把 `COUIPopupHost` 挪位置 / 改名，
这里是唯一会编译失败的地方（正好也是唯一该改的地方）。**新代码不要照这个例子去 import
`io.github.suqi8.coui.kmp.utils.*`** —— 目前全仓只有这一处。

### 2. `ui/screen/binding/BindingList.kt` 读页面的 `overlays.hasOpen()`

屏文件读页面对象的浮层域 —— 这在 `ui/screen → ui` 那条**允许**（本来就是页面构件的写法，
`SettingsPage` 是 DSL 接收者），`audit-coupling.py` 的环检测也把 `ui*` 折成一层。

要守的是**语义**：`hasOpen()` 读的是「组合期登记了几层浮窗」，比 `PageOverlays` 文档里那句
「系统返回要不要整个吞掉」的判据**宽** —— 提示条也在 `openCount` 里。本轮用它做
「浮窗开着就别抢按键焦点」正好合适（提示条虽然不吃焦点，但那时抢焦点同样是意料之外的事）；
**下一次若有人拿它做「页面能不能响应按键」之类更窄的判断，要先看清这个口径。**

### 3. `show` 穿过两个包：`binding/` → `command/` 的 `IconInputView(show = …)`

`command/IconInputView.kt` 里的 `ImeGuard` 收 `show: Boolean?`，调用方在
`ui/screen/binding/`。方向是 `binding → command`（都已经在 `ui.screen` 一层，脚本不记边）。

写得克制的地方：`command/` **不认识「弹窗」「收集按键」「收集窗收场」这些概念** ——
参数就叫 `show`，`null` 表示「这不是弹窗里的输入框、守卫不启用」，注释里也只说
「外力把焦点 / 键盘收走时补回来」。`binding/` 那边才知道「这是 ROM 处理电源键之后的副作用」。

**这条边只许保持这个形态**：往 `IconInputView` 里加 `dialogId` / `onDismissFinished` /
`isCollecting` 这类参数，就是 `command/` 开始认识 `binding/` 的业务了，那才是真的反向耦合。
同样的道理适用于 `KeyNameBody` 上那个 `show`（它本身就是 `binding/` 内部的共用件）。
