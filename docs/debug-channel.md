# debug 通道（注入侧反射通道）

> 只在
> **debug 包**存在（`BuildFlags.DEBUG_PROBE`）。实现：`inject/system/ProbeChannel.kt`。2026-10-14 落地；同日起成为本工程的固定调试能力。

## 一、它解决什么

这个工程最贵的一项成本是**注入侧的迭代速度**：

| 改在哪                           | 生效方式                                           | 一轮试错 |
| -------------------------------- | -------------------------------------------------- | -------- |
| 宿主侧（`com.oplus.gesture`）    | `kill` 掉宿主进程                                  | **几秒** |
| 注入侧（`system_server` / 桌面） | **整机重启**（LSPosed 只在进程启动那一刻注入 dex） | 几分钟   |

小窗那次「按一下是小窗、但比原生小一圈」的坑，就是这么一次次重启试出来的。有了这条通道，**注入侧的实验不再需要改代码 + 重启**
——「拿真实签名 / 试调用 / 在别的进程里发起」全部变成**发一条广播**。

**规则：凡是注入侧的实验，先想这条通道。**

## 二、安全边界（三条，缺一条都不能上）

1. **只在 debug 包存在** —— `DEBUG_PROBE`
   是编译期常量，release 里这些代码连同调用点一起被 R8 折掉（不是运行时判断，是根本不存在）。
2. **只收 root / system 发的**
   —— 发送方不是宿主（调试从 adb 发），所以它单有一套来源校验（`KeyHooks.isFromRootOrSystem`）；宿主那几条通道的校验（`isFromHost`）不受影响。普通 App（uid
   ≥ 10000）发同名广播进不来。
3. **每个 op 整体包 catch** —— 调用点在 AMS 广播入口，漏一个异常出去就是系统崩 / 开机循环。

⚠️ 另外：**shell 身份（uid 2000）过不了 `RECEIVER_NOT_EXPORTED` 那类接收器**，所以发广播一律用
`su -c`。这条是实测出来的（见 `zoom-window.md` §11.1）。

## 三、落点

| 落点            | 怎么接住的                              | 拿到的能力                                                                            |
| --------------- | --------------------------------------- | ------------------------------------------------------------------------------------- |
| `system_server` | AMS 广播入口的 hook（与功能通道同一个） | 框架内部：`FlexibleTaskController` / `TaskExt` / `WindowContainer` / `SurfaceControl` |
| ~~桌面进程~~    | ——                                      | **已撤掉**（2026-10-14，见下）                                                        |

**广播的包名决定谁收到**：

- 不加 `-p`：`system_server` 那个 hook 读得到（它拦的是 AMS 入口）—— 现在**唯一的落点**就是这条。

⚠️
**「桌面落点」已撤掉**（2026-10-14 用户口径「桌面作用域可以去掉了」）：模块的静态作用域（`app/src/main/resources/META-INF/xposed/scope.list`）只声明
`system` + `com.oplus.gesture`， **不含 `com.android.launcher`** ——
debug 源集里那份为它加行的覆盖文件也删了。桌面进程里不再有模块代码，所以 `-p com.android.launcher`
发出去没人接（那条路原本是给「调用方 pid == 桌面进程 pid」的 A 方案实验用的，见 `device-findings.md` 附九）。

要再捡回来：debug 源集加一份 `scope.list`（`system` + `com.oplus.gesture` +
`com.android.launcher`）→ 在 LSPosed 管理器里给本模块勾上「桌面」→ 整机重启。三步缺一不可（静态声明只是**允许**，实际注入看作用域记录）。

⏭️ 若某次实验又需要「从桌面进程发起」，先决定值不值得再走这三步 —— 只有那条 A 方案要这个身份。

## 四、六个 op

统一前缀 `probe`，日志在 `/data/adb/lspd/log/modules_*.log`（**不在 logcat**）：

```bash
adb shell "su -c 'grep -a probe /data/adb/lspd/log/modules_*.log'"
```

### `dump` —— 拿真实签名（最常用）

把某个类的**声明方法与字段连参数类型一起**打出来。专治「签名靠猜」——本工程踩过好几次「以为有这个方法、其实签名不同」的坑。

```bash
# 看 FlexibleWindowManagerService 上所有与 Caption 有关的方法与字段
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op dump --es cls com.android.server.wm.FlexibleWindowManagerService --es filter Caption'"
```

- `cls`：完整类名（必填）
- `filter`：名字子串，不填就全列（上限 90 条，免得把日志刷爆）

### `call` —— 反射调一个方法

```bash
# 注意 --ez unsafe true：这个 op 会直接 invoke 宿主类，没有这一步一律被拒（日志里有判据行）
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op call --es target service --es name getCaptionName --esa args 1 --ez unsafe true'"
```

- `unsafe`：**必带**（`--ez unsafe true`）。`call` 会对广播指定的类直接 `invoke`，而 **`invoke` 会触发
  那个宿主类的 `<clinit>`** —— 顺序不对就把类标成 erroneous，之后系统真用到它的地方
  `NoClassDefFoundError`（AGENTS.md 五之二、1 那条红线）。所以它只在「广播显式要求」**且**「`cls` 命中
  白名单」时才执行；白名单是 `com.android.server.wm.` 与 `android.app.OplusActivityTaskManager`
  （就是本模块真要用到的那两族）。其余情况一律拒绝，并在日志里留一条 W 级判据行写明卡在哪一条。
- `cls`：目标类的完整类名（`target=static` 必填；`target=service` 可不填，由服务自身推出来）
- `target`：`static`（静态调用）/ `service`（拿 `FlexibleWindowManagerService.getInstance(...)` 当接收者）
- `name`：方法名；同名多载时按**参数个数 + 能否把 `args` 转成形参类型**自动挑
- `args`：字符串数组（`--esa`），只支持 int / long / float / double / boolean /
  String；另有三种字面写法能现构对象（`--esa` 用逗号分隔，所以对象内部不要出现逗号）： `null` /
  `rect:l,t,r,b`（`android.graphics.Rect`）/ `bundle:k=v;k=v`（`android.os.Bundle`，值按 int→boolean→String 猜）——
- 返回值和异常都会打出来

### `state` —— 只读看任务状态

```bash
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op state --ei task_id 75'"
```

打任务的 bounds、`TaskExt` 上的 `mScenario` / `mHasCaption` / `mFlexibleZoomState` / `mCaptionName`
等字段，并试着探「操作条视图到底建出来没有」（`getFlexibleCaptionView` / `mCaptionView` 一类）。

### `corner` —— 给任务窗口设圆角

```bash
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op corner --ei task_id 75 --ef radius 91'"
```

给目标任务的窗口 surface 设圆角（`SurfaceControl.Transaction#setCornerRadius`）。 `SurfaceControl`
的取法备了几个候选名，命中的那个会打进日志。

### `dress` —— 「补壳」：圆角 + 操作条挂载/显示

三个开关分开传，一次重启后就能任意组合试（每一步单独打日志）：

```bash
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE -p com.oplus.gesture \
  --es op dress --ei task_id 75 --ef radius 91 --ez caption true --ez show true'"
```

- `radius`（给了才做圆角）/ `caption`（`initCaptionViewAndAttachToTask`）/ `show`（`showCaptionViewWithAnim`）
- 末尾会打 `mCaptionViewSurfaceShow` —— 「视图在不在」是一回事，**「有没有真显示出来」是另一回事**

> ⚠️ 数值型 extra 的旗标：`--ei` int、`--el` long、`--ef` float、`--ez` boolean。用 `--el` 传 `task_id`
> 会读不到（`getIntExtra` 找不到 long 型会退成默认值 `-1`，症状是「拿不到任务 #-1」）—— 这个坑真机上踩过一次。

### `zoom` —— 从**当前进程**发起「把某任务收成小窗」

`android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int taskId, Bundle options)`。 `flags`
决定档位：`2` = 展开的普通小窗，`32` = 收起的最小化窗。

```bash
# ⚠️ 曾经要从桌面进程发起时带 `-p com.android.launcher` —— 桌面落点 2026-10-14 已撤掉，
#    现在不加 `-p`、由 system_server 那个 hook 接（见 §三）
adb shell "su -c 'am broadcast -a com.lookie.opluskey.PROBE \
  --es op zoom --ei task_id 116 --ei flags 2'"
```

返回值语义：**`2` = `START_TASK_TO_FRONT`，服务端已受理并 toggle**；`0` / 负数 = 没做任何事。

> ⚠️ **两个必须记住的坑**（都真机踩过，详见 `device-findings.md` 附十一）：
>
> 1. 这个方法的第二个形参是 **`Bundle`**，不是 `ActivityOptions`。传 `ActivityOptions` 会 `argument type mismatch`，而
>    `Reflect.call` 会把它吞掉 —— 日志上只剩一句干净的 `→ null`。所以本 op 用 `getDeclaredMethod`
>    精确签名 + 自己 try/catch 打 cause。
> 2. 参数对不对**只看日志不够**：`→ null` 可能是「成功但无返回值」，也可能是「抛了但被吞」。要判成功看**框架侧**的
>    `onStartActivityFromRecents`。

## 五、注意

- 这是**调试能力**，不是产品能力：不要在产品路径上依赖它的 op。
- `release` 包里它整体不存在 —— 所以**任何**用到它的代码都必须落在 `DEBUG_PROBE` 的 `if`
  里（编译期常量，写错会在 release 编译期直接报错，不会带到线上）。
- 换机型 / 大版本升级后，如果 `SurfaceControl` 的取法变了，`corner` 会打「拿不到 SurfaceControl」——按日志里的候选名去
  `dump` 一下就能补。
