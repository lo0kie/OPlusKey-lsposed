# 已知限制

- **手改配置里认不出的写法不会执行，且下次保存会归一成 `none`**：`Action.parse` 遇到没有支持前缀的
  写法返回 `None`（并打一条 `w`，写明这条后果）。不原样保留是**有意的** —— 那要新增一个动作类型
  （AGENTS.md 一、4 的动作契约变更），收益只是保住一条本来就执行不了的写法。改配置请走界面，
  或按 `docs/device/actions/` 各节里给的写法。
- **自定义键没有独立的「已确认」类状态**：`enabled` 是唯一的闸门，导入会给所有自定义键置开
  （见 `key-to-gesture.md` 的「只管内置的侧键」）。所以别人给的一份配置里若收了音量键 / 电源键，导入那一刻就会接管。
- **「添加到桌面」的快捷方式只有注入侧读得到**：`LauncherApps.getShortcuts` 的「钉住」那一档按**调用方**
  过滤，宿主不是桌面宿主读不到，走同一个 API 换系统身份（uid 1000）也一样
  （`probe/shortcuts` 实测 `动态 3 / 清单 0 / 钉住 0`）。真机上另一个正源是**快捷方式服务的活数据**：
  注入侧就住在 `system_server` 里，可以
  `LocalServices.getService(ShortcutService)` → `getUserShortcutsLocked(userId)` →
  `getAllPackagesForTest()[包名].getAllShortcutsForTest()`，筛 `isPinned`（见 `inject/ShortcutRead`），
  `id` + 标签与本进程读到的动态 / 清单**按 id 去重**合并。
  **桌面库那条路（`launcher.db`）走不通**：库属桌面、`-rw-rw----`，宿主没有 root（KernelSU 对未授权
  应用把 `su` 藏起来），uid 1000 直接读也是 `Permission denied`，`content://com.android.launcher.settings`
  也不对外给桌面项 —— 所以只留服务活数据这一条。
  **起**这一条仍走 `ShortcutStarter`（按 pkg + id 走 `getShortcutIntent` / `startShortcut`）：
  这些快捷方式当初不是本进程钉的，能不能起还没有真机结论，失败时按既有的失败回执提示。
- **开机后约 15 秒内侧键动作不会执行**：模块的执行侧在宿主进程里，它要等 ROM 自己绑上来（实测开机后
  ~15s）。1.4.0 曾在注入侧开机就 `startService` 预热宿主（`HostWarmup`），已撤掉 —— 这 15 秒换不回来。
  这一段里按侧键**不会再弹模块页**：弹页面的根因是宿主服务 `onStartCommand` 在锁屏 / 熄屏 / 游戏等条件下
  会打开侧键设置页，而唤醒那一次 `startService` 正好把它带出来；现在宿主侧掐掉了那段正文
  （反编译与判据见 `docs/device/side-key.md` §40）。
- **「常用命令」里那几条要 root**（**现行隐藏**：入口收在 `CommonCommands.SHOW` 后面，手改配置引用
  `cmd:common.wechat_scan` 仍可用）：它们由模块维护（不在你的命令库里），正文是 `shell:`（微信扫一扫走
  `am start -a android.intent.action.VIEW -d weixin://dl/scan`）—— 宿主在 `platform_app` 域，
  直接起别人的界面会被后台启动限制拦掉，只有 `su` 这一条路（`docs/device/actions/launch.md` 二十九）。
  宿主拿不到 root 时按下去会报「这条命令要 root」；要换写法只能自己在命令库里建一条同名的（同名时以你的那条为准）。
