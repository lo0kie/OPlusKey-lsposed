# 十、应用控制（快捷功能 › 应用控制 + `proc:stop:` 动作）

**一格两档、一张页面**：网格里那一格叫「应用控制」，点它先弹一层菜单（打开应用 / 结束应用），
两档进的是**同一张**「选择应用」页 —— 差别只有一个：确认之后写成 `app:<包名>` 还是 `proc:stop:<包名>`。
页面、搜索、定位、确认键、空态全部是「选择应用」那一份实现，没有第二套。

- **入口那一格**（`ActionCatalog.APP_CONTROL_TILE` = `appctl:`）**永不写进配置**：它是关卡标记，
  那一层菜单的两行（`appctl:open` / `appctl:stop`）也都不是取值。`STEP_GROUPS` /
  `BRANCH_GROUPS` 都**原样保留这一格**：点开菜单后，打开 / 结束两行各自可以成为一步，
  分别落成 `app:<包名>` 与 `proc:stop:<包名>`。
- **菜单里的勾**由 `ActionCatalog.isBound` 判：打开那行认 `app:` / `window:` / 快捷方式，
  结束那行认 `proc:stop:`（两行都是标记 token，比的是「绑的是哪一族」）。
- **唯一的动作是 `proc:stop:<包名>`**（`Action.Process`，只存包名）。原来那一档 `proc:start:`
  已删：它与「打开应用」（`app:`）做的是同一件事，两格并排只会让人以为两者有区别。
  老配置里残留的 `proc:start:` 由 `parseProcess` 按「认不出」处理（落 `None`，不凭空执行）。
- **写盘只有一处**：两张页面共用的那颗确认键（`onAppPicked`）。它按 `AppPickerState.stopApp`
  选 token，然后走与其它动作同一条 `writeToken` 路径 —— 「绑到哪颗键的哪一档」仍由入口带下去的
  `BindingTarget` 决定。
- **点行不写盘**：这一页先「选中」，确认键才落盘。结束那一趟不挑界面（`AppPickerState.appOnly`：
  点一行就是选中、没有二级页、菜单里也没有「以小窗口打开」）。
- **结束是三步**，不是一条命令：`am force-stop` → 按 `ps` 第 2 列挑出残留 pid 一律 `kill -9` →
  **再查一遍这批 pid，查不到才算成功**。三个理由都是真机踩出来的：
  · `am force-stop` 对系统应用会被 `system_server` 拦住而**照样退出码 0**（PLK110 上
  `am force-stop com.android.systemui` 返回 0、那一行进程的 `ETIME` 一动不动）—— 原来的判据是
  「`am` 没报错」，于是日志写着「已执行」而屏幕上一个反应都没有；
  · 强杀要用 `ps` 第 2 列挑 pid，**不能 `pkill -f 包名`**：它按整条命令行匹配，而这条命令自己的
  `sh -c …` 里就带着那个包名，一枪先把执行它的 shell 打了，后面那行 `tip:` 永远等不到 ⇒ 成功也报失败；
  · 两处 `sleep` 是给 `system_server` 与内核收尸留时间，否则查表查到的是还活着的旧 pid。
  判据是「这几个 pid 没了」而不是「这个包没有进程了」：常驻应用被杀之后会被立刻重新拉起（新 pid），
  按包名判会把成功报成失败。
  包名拼进命令串之前过一道形状校验（`Action.isPackageName`，与解析入口同一把尺子）。
- **按键那条路必须异步**：`proc:stop:` 在宿主主线程的按键回调里执行，等 `su` 会卡住宿主 UI ——
  所以执行器调的是 `ProcessController.perform`（回调式）。
- **要 root**：宿主跑在 `platform_app` 域，别的应用的进程它既看不见也停不掉，整条命令走 `su`。
- **「显示失效应用」按这一趟分档**（`AppPickerState.stopApp` / `showUnopenable`）：**打开**那一趟
  菜单里有这一项（默认关 = 默认不显示），关着时空态那句话指回它；**结束**那一趟没有这一项、
  也不读它 —— 一律全列（停掉一个没有启动图标的应用正是它的用处）。行尾的「不可打开」标签同样
  **只挂在打开那一趟**：那一趟可能把它们藏起来，看得见时得知道点下去会失败；结束那一趟不挂。
- **两个显示开关都只是过滤，不参与取清单**（`AppPickerUiState.shown`）：清单**一次取全**
  （`loadLaunchableApps`：有启动图标那一批 + 模块 + `getInstalledApplications` 补回的其余应用），
  每一行带两档事实 —— `LaunchableApp.byDefault`（默认档该不该列）与 `launchable`（点不点得开）。
  `显示系统应用` 放 `byDefault == false` 那一批，`显示失效应用` 放 `launchable == false` 那一批，
  结束那一趟两者全放。**拨开关不查盘、不重查、不换缓存**。
  两条判据**各管一维、取交集**（`AppPickerState.includesIconless` / `includesUnopenable`，唯一实现）：
  一行要同时过两关才留下 —— 拨「显示失效应用」不会顺带放出没有启动图标的那一批。
  · 为什么改：上一版把这两个开关做成「取哪一档清单」的输入（`includeAll`），拨开关等于换查询 +
  把清单打回加载态 + 换一份缓存，而这三笔账跨趟残留（`lastMode` / `cachedAppsIncludeAll` / 重查失败
  又一次没人看得见的日志）。任一处对不上，用户看到的就是**开关按了没反应**；结束那一趟更直接 ——
  `stopApp` 没进取法，它拿到的仍是精简那份，要停没有启动图标的应用时清单里压根没有它们。
  · 全量取法付得起这笔账，靠的是「点不点得开」不再逐包 [`AppEntry.resolve`]（几百次 binder），
  而是 `AppEntry.launchablePackages` 一次问全机（三次 bulk 意图查询），判据仍是同一份
  `isLaunchable`，外加那份 `ApplicationInfo` 上的 `FLAG_HAS_CODE`。
  · `AppPickerState.stopApp` 因此**必须是 compose 状态**：`shown` 是 `derivedStateOf`，它读的是
  「这一趟是不是结束」，而这一位是在组合之外改的 —— 普通 `var` 时那次改写不会让缓存失效，
  「结束那一趟全列」的算法会跟着上一趟留下来。
- **两档只认自己那一族**（`SettingsPage.appPickerSelectedTarget`）：键上绑的是 `proc:stop:` 时，
  进「打开应用」那一趟既不点亮那一行、也不滚过去；反过来 `app:` 进「结束」那一趟同理。
- **搜索框的占位带条数**（`搜索应用 (42)`，两页共用 `PickerSearchBand` 的那一个 `count`）：
  只在**搜索框空着**时才加 —— 一有输入，那行字读的是「你在搜什么」，再挂个数字会被当成搜出来的
  条数；`null` / 0 也不加（清单还没读到时不摆一个 `(0)`）。句式收在
  `Strings.COMMON_SEARCH_COUNT_FORMAT` 一处。条数从哪来：选择应用读 `appPickerUi.shown.size`；
  应用组件那一页的清单在**页内容那棵树**里 `remember`，顶栏那条带子读不到，所以由页面在
  清单到手那一刻写到页面对象上（`AppPickerState.activityCount`）。
