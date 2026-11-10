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
  ~15s）。1.4.0 曾在注入侧开机就 `startService` 预热宿主（`HostWarmup`），代价是宿主冷启动会把
  `ActionButtonSettingsActivity`——也就是被模块替换成模块页的那一页——带到前台，**每次开机都自己弹一次**，
  已撤掉；要拿回那 15 秒就得接受这一弹。
