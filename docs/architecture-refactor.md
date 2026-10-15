# 项目结构重构方案

> 本文只谈**代码怎么摆**，不动任何行为契约。重构后的产物必须逐条满足 `AGENTS.md`
> 里的红线，那是验收的硬指标（见「四」）。

> ⚠️ 文中 `hook/` `page/` `gesture/` `config/`
> 是**重构前**的包名 —— 「三」的映射表左列就是它们，右列是重构后的位置，也就是今天的结构。

## 一、现状与问题

当前 `app/src/main/java/com/lookie/opluskey/` 共 55 个文件，按包分布：

| 包         | 文件数 | 说明                                       |
| ---------- | ------ | ------------------------------------------ |
| `page/`    | 22     | 设置页（Compose + COUI）                   |
| `hook/`    | 19     | 名字叫 hook，实际混了三层的东西            |
| `config/`  | 8      | 纯数据类与文件 IO 混在一起                 |
| `gesture/` | 3      | 只有三个文件，但分属**两个不同进程**       |
| 根包       | 4      | `KeyModule` / `Hooks` / `Reflect` / `XLog` |

四个具体问题：

1. **`hook/` 名不副实**。它同时装着注入侧基建（`KeyHooks` / `Framework` /
   `SideKeyInterceptor`）、注入侧的输入工具（`KeyInjector` / `GestureForwarder`）、系统桥（`StatusBarBridge` /
   `SystemContext` / `RingerController` /
   `FlashlightController`），以及**宿主进程**里跑的一整套动作执行器（`ActionRunner` / `AppLauncher` / `ShellRunner` /
   `CustomCommandRunner` …）。后面那批根本不在注入侧执行 —— 看目录名会得出相反结论。
2. **`config/` 把「数据」与「IO」混在一层**。`Action` / `Command` / `Config` / `KeyId` 是纯数据， `ConfigStore` /
   `CommandLibrary` / `ConfigCodec` 是落盘，`ConfigBroadcast`
   是跨进程线格式。最要命的是：**注入侧读不到配置文件**（宿主私有目录），但它和落盘代码在同一包里，新代码随手
   `ConfigStore.read()` 就能编过、到真机上才炸。
3. **`gesture/` 跨了两个进程**。`GestureDetector` 在注入侧判定手势，`GestureReceiver` / `SideKeyGestureHooks`
   在宿主主进程执行 —— 这是本项目最核心的一条架构分界，却被塞进同一个包。
4. **`page/`
   单包 22 个文件**。页面、通用组件、展示层数据（动作清单 / 文案 / 图标 / 常量）、界面状态四类东西平铺在一起，找一个页面要在 22 个名字里翻。

## 二、目标结构

### 划分依据：进程边界，不是「层」

本项目不是一个常规 App
—— 没有 Activity、没有 ViewModel、没有网络与数据库，它是由 LSPosed 注入到**三个进程**里、各自只干自己能干的那部分（`AGENTS.md`
一、6）。所以「最流行的架构」在这里的正确形态是：**先按进程切开，再在每个进程内部按职责分层，并用依赖方向把「谁能用谁」钉死**。

### 依赖方向

```
            ui/                    host/                   inject/
   com.oplus.gesture.ui      com.oplus.gesture          system_server
             \                       |                        /
              \                      |                       /
               ▼                     ▼                      ▼
        ┌──────────────────────────────────────────────────────┐
        │                        core/                         │
        │        model / log / reflect / config / wire         │
        └──────────────────────────────────────────────────────┘
```

四条硬规矩：

- **`core/` 不依赖任何上层**（它不认识进程、不认识界面）；
- **`inject/` 只准依赖 `core/model`、`core/log`、`core/reflect`、`core/wire`**， **不准碰
  `core/config`**（落盘那包）—— 这样「注入侧读不到配置文件」在**编译期**就成立；
- **`inject/` 也不准依赖 `ui/` 与 `host/`**：它跑在 `system_server`，与宿主那份 APK 不是同一个类加载器，引用上面两类必然
  `NoClassDefFoundError`（把系统进程拖死）；
- **`ui/` 与 `host/`
  互相引用代码是允许的，但运行期不许假设共享状态**：二者是宿主**同一个包的两个进程**（`com.oplus.gesture.ui` 与
  `com.oplus.gesture`，`KeyModule.PAGE_PROCESS` / `HOST_PROCESS`），类都来自同一份 APK
  —— 同一个执行器（`host/action/ActionRunner`）就是被两边共用的：`ui/` 里跑「试一下」、`host/`
  里跑真实手势。但**两个进程的静态状态各自独立**，需要跨进程传的东西只能走广播，或走那份同 uid 的配置文件。

### 各包放什么

```
com.lookie.opluskey
├── KeyModule.kt              ← 入口，必须留在根包（见「七、风险」）
├── core/
│   ├── log/                  XLog
│   ├── reflect/              Reflect
│   ├── model/                纯数据：Action / Command / Config / KeyId / Gesture / KeyConfig
│   ├── config/               落盘：ConfigStore / ConfigCodec / CommandLibrary      ← 只给 host / ui
│   └── wire/                 跨进程线格式：ConfigBroadcast                        ← 给 inject / host / ui
├── inject/                   ← 注入侧（system_server）：拦按键、判手势、注入按键、调系统服务
│   ├── KeyHooks / Hooks / Framework          挂点与降级候选表
│   ├── SideKeyInterceptor / ConfigChannel    按键拦截与配置接收
│   ├── input/                KeyInjector / GestureForwarder / GestureDetector
│   └── system/               SystemContext / StatusBarBridge / RingerController /
│                             FlashlightController / SystemActionButtonConfig
├── host/                     ← 宿主主进程：接手势、跑动作
│   ├── GestureReceiver / SideKeyGestureHooks
│   └── action/               ActionRunner / AppLauncher / AppEntry / OplusFeature /
│                             ShellRunner / CustomCommandRunner / CommandFailure / KsuCompat
└── ui/                       ← 宿主设置页进程（com.oplus.gesture.ui）
    ├── SettingsPage / SettingsPageHooks / HostWindow / PageScreens   页面骨架与挂载
    │   SettingsPageHooks 只留换页编排；伙伴：HostSession（当前 Activity / 页面）、
    │   HostChrome（窗口与状态栏）、BackInterception（系统返回）、ConfigTransfer（导入导出）
    ├── screen/               HomeScreen / BindingScreen / CommandScreen / AppPicker / AboutScreen
    ├── component/            PageHeader / PageWidgets / SliderRow / CommandMenus
    ├── model/                ActionCatalog / CommandText / ActionIcons / PageConst
    ├── state/                PageState / UiState / ConfigActions / PageFeedback
    └── theme/                PageTheme
```

## 三、逐文件迁移映射

### 根包与 core

| 现在                        | 迁移后                          | 理由                                      |
| --------------------------- | ------------------------------- | ----------------------------------------- |
| `XLog.kt`                   | `core/log/XLog.kt`              | 三个进程都要用                            |
| `Reflect.kt`                | `core/reflect/Reflect.kt`       | 注入侧为主，但无进程假设                  |
| `config/Action.kt`          | `core/model/Action.kt`          | 纯数据                                    |
| `config/Command.kt`         | `core/model/Command.kt`         | 纯数据                                    |
| `config/Config.kt`          | `core/model/Config.kt`          | 纯数据（含 `KeyConfig` / `GlobalConfig`） |
| `config/KeyId.kt`           | `core/model/KeyId.kt`           | 键码唯一定义处（一、5）                   |
| `config/ConfigStore.kt`     | `core/config/ConfigStore.kt`    | 落盘 —— 注入侧不许用                      |
| `config/ConfigCodec.kt`     | `core/config/ConfigCodec.kt`    | 落盘编解码                                |
| `config/CommandLibrary.kt`  | `core/config/CommandLibrary.kt` | 落盘编解码                                |
| `config/ConfigBroadcast.kt` | `core/wire/ConfigBroadcast.kt`  | 线格式，注入侧要用                        |
| `Hooks.kt`                  | `inject/Hooks.kt`               | LSPosed 安装入口的辅助                    |
| `KeyModule.kt`              | **不动**（根包）                | `java_init.list` 写死了这个全限定名       |

### inject（原 `hook/` 的前半 + `gesture/` 的一部分）

| 现在                               | 迁移后                                      |
| ---------------------------------- | ------------------------------------------- |
| `hook/KeyHooks.kt`                 | `inject/KeyHooks.kt`                        |
| `hook/Framework.kt`                | `inject/Framework.kt`                       |
| `hook/SideKeyInterceptor.kt`       | `inject/SideKeyInterceptor.kt`              |
| `hook/ConfigChannel.kt`            | `inject/ConfigChannel.kt`                   |
| `hook/KeyInjector.kt`              | `inject/input/KeyInjector.kt`               |
| `hook/GestureForwarder.kt`         | `inject/input/GestureForwarder.kt`          |
| `gesture/GestureDetector.kt`       | `inject/input/GestureDetector.kt`           |
| `hook/SystemContext.kt`            | `inject/system/SystemContext.kt`            |
| `hook/StatusBarBridge.kt`          | `inject/system/StatusBarBridge.kt`          |
| `hook/RingerController.kt`         | `inject/system/RingerController.kt`         |
| `hook/FlashlightController.kt`     | `inject/system/FlashlightController.kt`     |
| `hook/SystemActionButtonConfig.kt` | `inject/system/SystemActionButtonConfig.kt` |

### host（原 `hook/` 的后半 + `gesture/` 的其余）

| 现在                             | 迁移后                               |
| -------------------------------- | ------------------------------------ |
| `gesture/GestureReceiver.kt`     | `host/GestureReceiver.kt`            |
| `gesture/SideKeyGestureHooks.kt` | `host/SideKeyGestureHooks.kt`        |
| `hook/ActionRunner.kt`           | `host/action/ActionRunner.kt`        |
| `hook/AppLauncher.kt`            | `host/action/AppLauncher.kt`         |
| `hook/AppEntry.kt`               | `host/action/AppEntry.kt`            |
| `hook/OplusFeature.kt`           | `host/action/OplusFeature.kt`        |
| `hook/ShellRunner.kt`            | `host/action/ShellRunner.kt`         |
| `hook/CustomCommandRunner.kt`    | `host/action/CustomCommandRunner.kt` |
| `hook/CommandFailure.kt`         | `host/action/CommandFailure.kt`      |
| `hook/KsuCompat.kt`              | `host/action/KsuCompat.kt`           |

### ui（原 `page/`）

| 现在                        | 迁移后                         |
| --------------------------- | ------------------------------ |
| `page/SettingsPage.kt`      | `ui/SettingsPage.kt`           |
| `page/SettingsPageHooks.kt` | `ui/SettingsPageHooks.kt`      |
| `page/HostWindow.kt`        | `ui/HostWindow.kt`             |
| `page/PageScreens.kt`       | `ui/PageScreens.kt`            |
| `page/HomeScreen.kt`        | `ui/screen/HomeScreen.kt`      |
| `page/BindingScreen.kt`     | `ui/screen/BindingScreen.kt`   |
| `page/CommandScreen.kt`     | `ui/screen/CommandScreen.kt`   |
| `page/AppPicker.kt`         | `ui/screen/AppPicker.kt`       |
| `page/AboutScreen.kt`       | `ui/screen/AboutScreen.kt`     |
| `page/PageHeader.kt`        | `ui/component/PageHeader.kt`   |
| `page/PageWidgets.kt`       | `ui/component/PageWidgets.kt`  |
| `page/SliderRow.kt`         | `ui/component/SliderRow.kt`    |
| `page/CommandMenus.kt`      | `ui/component/CommandMenus.kt` |
| `page/ActionCatalog.kt`     | `ui/model/ActionCatalog.kt`    |
| `page/CommandText.kt`       | `ui/model/CommandText.kt`      |
| `page/ActionIcons.kt`       | `ui/model/ActionIcons.kt`      |
| `page/PageConst.kt`         | `ui/model/PageConst.kt`        |
| `page/PageState.kt`         | `ui/state/PageState.kt`        |
| `page/UiState.kt`           | `ui/state/UiState.kt`          |
| `page/ConfigActions.kt`     | `ui/state/ConfigActions.kt`    |
| `page/PageFeedback.kt`      | `ui/state/PageFeedback.kt`     |
| `page/PageTheme.kt`         | `ui/theme/PageTheme.kt`        |

### 源集变体

`app/src/debug/` 与 `app/src/release/` 下的三个同名文件（`BuildFlags` / `HostFilePerms` /
`PageDebugChannel`）跟着主源集一起挪：`config/HostFilePerms` → `core/config/`、 `page/PageDebugChannel` →
`ui/`。**两个源集必须同步改**，否则 release 包会缺同名空壳。

## 四、重构后仍须成立的契约

这些是 `AGENTS.md` 的硬指标，重构**只许换位置、不许换语义**：

1. **注入侧不引用宿主框架私有类型**（一、1）：`inject/` 下 grep `import com.android.server` / `import com.oplus.`
   必须为空，框架依赖保持 `compileOnly`。
2. **注入侧不引用界面层依赖**（二、1.2）：`inject/` 下不得出现 COUI /
   androidx.compose 的 import。重构后这条**更容易守**：`ui/` 独立成一个顶层包，扫一眼依赖图就能验。
3. **键码只定义在 `KeyId` 一处**（一、5）：迁到 `core/model/KeyId.kt` 后仍是唯一处， `inject/` 与 `host/` 都从那里取。
4. **Hook 面候选表只在 `Framework.kt`**（一、7）：随 `inject/Framework.kt` 一起走。
5. **系统侧配置读写只有一个出口**（一、8）：`SystemActionButtonConfig.kt` 随 `inject/system/`
   走，且仍然**只在宿主进程**调 —— 注意它现在放在 `inject/`
   里是因为它是「系统侧配置」的读取器、由注入侧代码定义，但**调用点必须仍在宿主侧**；若迁移中发现调用点跑到 `inject/`
   内部，说明原结构本来就错了，要单独记一笔。
6. **零界面入口**（一、6）：`AndroidManifest.xml` 不动，`ui/` 只是包名变化。
7. **作用域声明**（一、9）与 `scope.list` 不动。
8. **`page/`
   的注释密度与风格**（四、1 与项目既有纪律）：迁移时**只改 package 行与 import**，注释一律原样带走，不顺手改写、不「顺便整理」。

## 五、明确不采用的流行做法

「行业最流行」不等于「都适合这里」，以下四条**不采纳**，理由各自成立：

| 做法                              | 为什么不适用                                                                                                                                                                                                                                                                  |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MVVM（ViewModel + StateFlow）** | 本模块没有自己的 Activity / Fragment，页面是塞进宿主 `Activity` 里的一层 Compose 覆盖；ViewModel 的宿主（`ViewModelStoreOwner`）本来就得自己兜底伪造（`SettingsPage` 里已有 `LifecycleRegistry` 那套兜底）。再加一层 ViewModel 只增加一层生命周期要照顾的壳，换不来任何东西。 |
| **拆成多个 Gradle 模块**          | `AGENTS.md` 明确「单模块、无测试工程」，且注入侧**故意不声明**依赖，好让误加的直接引用变成编译错误。拆模块要重新编排 `compileOnly` / `implementation` 的可见性，风险大于收益；包结构分层已经能拿到同样的约束。                                                                |
| **Repository + 数据源抽象**       | 数据源只有一个：宿主私有目录里的两个文本文件。抽象出 `Repository` 接口 + `LocalDataSource` 只是给两个函数各套一层壳。                                                                                                                                                         |
| **引入 DI 框架（Hilt / Koin）**   | 注入侧是 system_server，任何注解处理器生成的代码与第三方容器都要求类加载期可达 —— 这与「注入侧零依赖」直接冲突。                                                                                                                                                              |

**采纳的是**：依赖方向（Clean Architecture 的内核）+ 按进程分包 + 每包单一职责。

## 六、实施顺序

分四步，**每步都能单独编译、单独回滚**，不要一把梭：

1. **第一步：`core/`**（12 个文件）。只挪位置 + 改 `package` 与 `import`，同时把 `config/` 拆成 `model` / `config` /
   `wire` 三包。这一步做完，**注入侧不许碰 `core/config`** 这条约束就可以立刻用 grep 验。
2. **第二步：`inject/` 与 `host/`**（22 个文件）。按「判定 vs 执行」「注入侧 vs 宿主侧」把原 `hook/` + `gesture/`
   拆开。这一步是重构的核心价值所在，做完 `hook/` 与 `gesture/` 消失。
3. **第三步：`ui/`**（22 个文件）。按 骨架 / screen / component / model / state /
   theme 分。纯机械迁移，风险最低，可以放最后。
4. **第四步：源集同步**（debug / release 各 3 个文件）+ 全量 `assembleDebug` + 逐条过「四」的清单。

每一步的验收动作都一样：

```bash
./gradlew :app:assembleDebug
grep -rn "com.android.server\|com.oplus\." app/src/main/java/com/lookie/opluskey/inject/   # 应为空
grep -rn "coui\|androidx.compose" app/src/main/java/com/lookie/opluskey/inject/            # 应为空
```

## 七、风险与注意事项

1. **`META-INF/xposed/java_init.list` 写死了 `com.lookie.opluskey.KeyModule`** ——所以 `KeyModule.kt`
   **必须留在根包**。挪它就要同步改这个文件，改错的表现是模块完全不加载（日志里一行都没有）。
2. **`AndroidManifest.xml` 与 `scope.list` 不涉及包名**，无需改动；但改动后仍要按 `docs/device-findings.md`
   六之一~六之三 复核作用域与安装路径。
3. **换包必须整机重启**才能验证（`AGENTS.md`
   一、11 与 二、1.2）：模块代码是 LSPosed 注入的，不重启一直跑旧代码 —— 这一点本轮已经踩过一次。
4. **迁移时只改 `package` 与 `import`**。注释、常量、方法名一律原样带走；任何「顺手整理」都会让这次 diff 失去可审查性。
5. **`git mv` 而不是删+建**，保留文件历史；一次提交只做一步（对应「六」的四步）。
6. 提交前 `git status` 里不应出现 `.temp/`；`changelog/` 的当前工作区片段要补一条。
