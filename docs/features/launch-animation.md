# 侧键「打开应用」转场动画技术文档

分析对象：ColorOS 17.0.0（PLK110 / `PLK110_17.0.0.105(CN01)`）中的
`/system_ext/app/OplusGestureUI/OplusGestureUI.apk`（包名 `com.oplus.gesture`）。

- APK 大小 28 796 905 字节，sha256 `95b5434da60582a3ae4a53103bc19c4ddf9810420335abdd0d5ce9efe780ade3`
  （与仓库内 `reference/gesture.apk` 完全一致）。
- 三个 dex：`classes.dex` / `classes2.dex` / `classes3.dex`。
- 本文所有类名、方法名、常量、字段偏移均直接取自该 APK 的字节码；`$` 为 Kotlin / R8 生成的嵌套类写法。

## 0. 结论摘要

1. 原版**没有**使用 `overridePendingTransition`、`anim` 资源或 `ActivityOptions.makeCustomAnimation`。
   它把目标 Activity 的启动包进 `RemoteAnimationAdapter`，再包成 `RemoteTransition`，
   由 `com.oplus.gesture` 自己在过渡期间绘制一层圆角卡片并逐帧改写目标窗口的 `SurfaceControl`（leash）；
   同时播一个 COE 系统特效 `assets/key_remote_sweep_anim.coz`。
2. 这套动画**与目标应用无关**：整条链路不读目标包名，APK 里也没有任何白名单 / 黑名单 / 跳过动画的字符串。
   是否套动画取决于「这一次启动怎么发起」，而不是「打开的是哪个应用」。
3. **静态分析曾判它恒不生效，真机实测推翻了这一点**（见第 9 节）：早先按字节码读出来
   「`d$b.a()` 恒 `false`、`d.n` 传 `Z = false` ⇒ `callback.a() && Z` 恒 `false` ⇒ adapter 恒 `null`」，
   但把模块总闸关掉、让侧键走原生行为后，系统日志里确实出现了 `debugName = SpruceLauncher` 的
   `RemoteTransition`（这个名字只有 `d$a.a()` 会写，而它在 `adapter != null` 才被调到）。
   即：883 ms 卡片 + leash 动画在本机**是活的**，那两处「恒 false」的静态结论与运行时不符。

## 1. 分析对象与方法

| 项目 | 值 |
| ---- | ---- |
| 包名 | `com.oplus.gesture` |
| 安装路径 | `/system_ext/app/OplusGestureUI/OplusGestureUI.apk` |
| ROM | `PLK110_17.0.0.105(CN01)`，ColorOS 17.0.0 |
| 字节码读取 | `tools/dexdump.py`（androguard 4.1.4 / Python 3.14.7） |
| 反汇编交叉核对 | Android SDK build-tools 36.0.0 的 `dexdump.exe -d` |

`dexdump.exe` 输出为 UTF-16LE，用 Python 读取时需要显式指定编码；否则中文与偏移全部错位，
这是核对控制流（`if-eqz` / `if-nez` 的目标地址）时最容易出错的一步。

## 2. 调用链

### 2.1 入口

| 场景 | 入口 | 说明 |
| ---- | ---- | ---- |
| 侧键打开应用 | `i9.g0.q(Intent, boolean)` → `remote/transition/d.m(Context, Intent, 2, Z)` | 类型固定为 `2` |
| 侧键 provider | `ActionRemoteStartContentProvider.call` 的 `onRemoteStart` 分支 → `Handler.post(i9.h0)` → `d.m(..., 2, false)` | 由外部进程通过 ContentProvider `call` 触发 |
| 打开侧键设置页 | `ActionButtonKeyService.e()` → `d.m(..., 2, false)` | 目标是 `ActionButtonSettingsActivity` |

三个入口最终都落到 `d.m`。`d.m` 本身只做两件事：按类型取一个 `ta/b` 实现，然后转交 `d.n`：

```
d.m(Context, Intent, int type, boolean z)
    ta/b impl = d.k(context, type);                        // 取动画实现
    if (impl == null) { Log.e("SpruceLauncher", "startActivityWithRemoteAnim: remote anim is null, type error"); return; }
    remote/transition/g controller = new remote/transition/g(context, null);   // 第二个参数是 null View
    d.n(context, intent, controller, impl, z);
```

### 2.2 类型分支（`d.k`）

- 类型 `1` / `3`：`remote/transition/e`，动画资源名 `anim_right`。
- 类型 `2`（打开应用、打开设置页）：
  - `lb/v.a()`（即 `i9.b.h()` / `getActionButtonIsRight`）为真 → `remote/transition/b`；
    其内部再按 `p9.e.a(context)` 选 `anim_right_inner_screen` 或 `anim_right_front_screen`；
  - 为假 → `remote/transition/a`，动画资源名 `anim_left`。
- 其它类型：返回 `null`，`d.m` 打上面那条 `Log.e` 并直接返回。

PLK110 的侧键在机身右侧，所以走 `remote/transition/b`。

### 2.3 组装（`d.n`）

`d.n(Context, Intent, ActivityTransitionAnimator$d, ta/b, Z)` 依次做：

1. `new d$b()` —— 这是后面传给 `ActivityTransitionAnimator` 的回调，见第 9 节。
2. `Log.d("SpruceLauncher", "startActivityWithRemoteAnim: threadId: " + Process.myTid())`。
3. `new Bundle()`，写入 `key_position = ta/b.e()`（几何见第 5 节），`intent.putExtras(bundle)`。
4. 三次统计 / 埋点调用：`lb/q.c("com.oplus.gesture", null)`、`lb/q.d(..., tid, 3300, null)`、`lb/q.f(3300, null, tid, true)`。
5. `this.d = ta/a.a(context).i()` —— 模糊开关（`BlurrySwitchManager`），只影响绘制参数，不影响是否启动。
6. `controller.a(ta/b)` —— 把动画实现交给调用方。
7. `remote/transition/c.a().f(context)` —— 单例初始化（未读实语义）。
8. `new ActivityTransitionAnimator(callback = d$b)`，构造器的 `Executor` 与第三个参数走默认值（`mask = 6`）。
9. `animator.o(this)` —— 把 `d` 自身交给动画。`d` 实现 `ActivityTransitionAnimator$f`，即
   `a()` / `b()` / `c(float)` / `d()` 四个生命周期回调。
10. `new ua/r(animator, Z, context, intent)` —— 一个 `ve/l` lambda，`invoke(adapter)` 时调 `d.o`。
11. `ActivityTransitionAnimator.u(animator, controller, Z = 0, ua/r, mask = 2, null)`
    —— `mask = 2` 表示第三个参数（`Z`）取默认值，默认值也是 `false`。

### 2.4 真正的启动（`d.o`）

```
d.o(ActivityTransitionAnimator animator, boolean z, Context ctx,
    Intent intent, RemoteAnimationAdapter adapter)
```

- `adapter == null` → `Log.i("SpruceLauncher", "startActivityIntentWithAnimation fail. RemoteAnimationAdapter is null.")`
  然后 `return he/n.a`（Kotlin Unit），**不启动任何东西**。
- `adapter != null`：
  1. `d$a.a(animator, adapter)` 生成 `ActivityOptions`：
     - `new RemoteTransition(ua/n.w(animator, adapter.getRunner()), adapter.getCallingApplication(), "SpruceLauncher")`
     - `ActivityOptions.makeRemoteTransition(remoteTransition)`
     - 若 adapter 为 null 则退回 `ActivityOptions.makeBasic()`（该分支从 `d.o` 不可达，因为 `d.o` 已提前返回）
     - 最后统一 `setSplashScreenStyle(0)`
  2. `setLaunchDisplayId(0)`
  3. 当 `z && lb/d.a().i() && lb/d.a().e()` 时调用 `setDismissKeyguardIfInsecure()`，并打
     `Log.d("SpruceLauncher", "startActivityWithRemoteAnim: setDismissKeyguardIfInsecure")`
  4. `Bundle` 写入 `"Spruce_qucik_start_app" = true`（字符串原文如此，含拼写错误），
     交给 `com.oplus.flexiblewindow.FlexibleWindowManager.setExtraBundle(options, bundle)`
  5. `context.startActivityAsUser(intent, bundle, UserHandle.CURRENT)`

全 APK 只有这一处 `startActivityAsUser`，也只有这一处 `RemoteAnimationAdapter.<init>`，
也只有这一处 `ActivityOptions.makeRemoteTransition`。

### 2.5 时间线

```
d.m → d.k（选实现）→ d.n（埋点 / COE / 构造 animator）→ u → t（造 adapter）→ ua/r.invoke → d.o（真正启动）
```

## 3. 关键常量

取自 `ActivityTransitionAnimator.<clinit>` 与 `ActivityTransitionAnimator.t`：

| 名称 | 值 | 用途 |
| ---- | ---- | ---- |
| 总时长 | `883 ms` | `RemoteAnimationAdapter` 的 duration，同时也是卡片动画的 `ValueAnimator` 时长 |
| 状态栏过渡延迟 | `733 ms` | `883 - 150`，`RemoteAnimationAdapter` 第二个参数 |
| 内容 alpha 起点 | `617 ms` | `883 - 266`，静态字段 `t` |
| 内容 alpha 时长 | `133 ms` | 从 617 ms 起算 |
| 卡片 alpha 窗口 1 | `[0, 150]` | `f$f.c()` → `f$f.d()` |
| 卡片 alpha 窗口 2 | `[150, 183]` | `f$f.a()` → `f$f.b()` |

`f$f` 的五个 `long` 字段与 getter 的对应关系（getter 名与字段名不同，容易读错）：

| 构造入参顺序 | 字段 | getter | 值 |
| ---- | ---- | ---- | ---- |
| 1 | `a` | `e()` | `883` |
| 2 | `b` | `c()` | `0` |
| 3 | `c` | `d()` | `150` |
| 4 | `d` | `a()` | `150` |
| 5 | `e` | `b()` | `183` |

时间归一化（`f$b.b(timings, progress, start, duration)`，`timings` 即上面的 `f$f`）：

```
constrain((progress * 883 - start) / duration, 0f, 1f)
```

## 4. 曲线

`ActivityTransitionAnimator.<clinit>` 里构造的曲线分两类。

绝对时间曲线（`android.view.animation.PathInterpolator`，字段 `n` / `o` / `p` / `u` / `v`）：

| 字段 | 控制点 |
| ---- | ---- |
| `n` | `(0, 0, 0.2, 1)` |
| `o` | `(0.33, 0, 0.67, 1)` |
| `p` | `(0.3, 0, 0.1, 1)` |
| `u` | `(0, 0, 0, 1)` |
| `v` | `(0.2, 0, 1, 1)` |

位置类曲线使用 COUI 的阻尼振荡插值器 `com.coui.appcompat.animation.p`（继承 `BaseInterpolator`，
构造参数为两个 `double`，内部含 `2π` 与 `sqrt` 计算）。`f$d` 的 10 个槽位（构造参数名由 Kotlin 的
`Intrinsics` 空检查直接给出）依次为：

| 槽位 | 取值 |
| ---- | ---- |
| `positionInterpolator` | `p(0.6, 0)` |
| `positionInterpolatorX` | `p(0.45, 0)` |
| `positionBelowStartInterpolator` | `p(0.3, 0)` |
| `positionBelowInterpolator` | `p(0.8, 0)` |
| `positionBelowEndInterpolator` | `p(0.8, 0)` |
| `positionXInterpolator` | `p(0.6, 0)` |
| `blurInterpolator` | `PathInterpolator(0.33, 0, 0.67, 1)` |
| `scaleInterpolator` | `PathInterpolator(0.3, 0, 0.1, 1)` |
| `contentBeforeFadeOutInterpolator` | `PathInterpolator(0, 0, 0.2, 1)` |
| `contentAfterFadeInInterpolator` | `PathInterpolator(0, 0, 0.6, 1)` |

`f$d` 在整个 APK 里只被构造一次（`ActivityTransitionAnimator.<clinit>`），随后与 `f$f` 一起组成
静态字段 `s = f(f$f, f$d)`。

## 5. 几何模型：`key_position` 与起始矩形

`lb/v` 是「按键在屏幕上的位置」的唯一来源，同时被 Intent extra、动画实现和卡片绘制使用。

- `lb/v.b()` 返回 `Pair(477f, 600f)`，这是设计基准（宽高基准 `2376`）上的两个纵向坐标。
- `lb/v.h(context, f) = round(f / 2376 * heightPixels)`，即按当前屏幕高度等比放大。
- `lb/v.c(context) = int[]{x, h(477), x, h(600)}`，其中
  `x = 键在右侧 ? widthPixels : 0`。

因此竖屏右键的起始状态是「屏幕右边的一条零宽竖线」，左键是左边；高度覆盖 `h(477)` 到 `h(600)`。

`remote/transition/a::b` / `b::b` 在此之上给出初始矩形：

- 右键（`b`）：`top = h(477)`、`bottom = h(600)`、`left = right = widthPixels`；
- 左键（`a`）：同上，但 `left = right = 0`；
- 横屏按 `rotation` 另走分支：`1` 贴在上边（`top = bottom = 0`），`3` 贴在下边
  （`top = bottom = heightPixels`），横向范围同样按 477 / 600 放大。

圆角：`f$e` 里的两个 `float` 字段（`e` / `f`）都是 `48.0f`，在 `f::h` 里被当成 8 个 corner radii
（前 4 个取一个、后 4 个取另一个），即起始状态是一个窄条，(接近) 全圆角。

## 6. 逐帧动画模型

### 6.1 卡片（自绘 overlay）

- `f::i` 建一个 `883 ms` 的线性 `ValueAnimator`，progress 从 0 到 1。
- 每帧调 `f::h(GradientDrawable, f$e state, float progress, View target, boolean, boolean)`：
  - `GradientDrawable.setBounds(state.rect 减去 overlay 窗口在屏幕上的偏移)`
  - `setCornerRadii(...)`（8 个半径）
  - `setAlpha(...)`
- 卡片透明度按两个窗口分段：`[0, 150]` 与 `[150, 183]`；反向播放时用
  `1 - interpolator.getInterpolation(t)`。
- 另有 `PorterDuffXfermode` 副本遮罩，用于在同一层里叠出「已淡出」的视觉。
  `f::h` 的两个布尔参数（`v17` / `v18`，「打开应用」方向上的实际取值未读实）决定是否启用这两条分支。

### 6.2 目标窗口（leash）

- `ActivityTransitionAnimator$Runner` 继承 `IRemoteAnimationRunner$Stub`，
  `ActivityTransitionAnimator$a` 在 `onAnimationStart` / `onAnimationEnd` 里逐帧处理 `RemoteAnimationTarget[]`。
- 通过 `SyncRtSurfaceTransactionApplier` 把 `Matrix` / `Rect` / blur 写到每个 target 的 `leash` 上。
- 缩放：`max(state.width / bounds.width, state.height / bounds.height)`，
  `Matrix.setScale(...)` 后 `postTranslate` 到目标位置；再把状态矩形换算成目标 `Rect`。
- 内容 alpha 从 `t = 617 ms` 起算、持续 `133 ms`，正向用 `PathInterpolator(0, 0, 0, 1)`，
  反向用 `(0.2, 0, 1, 1)`。

### 6.3 全链路时间表（毫秒）

- 卡片透明度：`0` – `150`（窗口 1）、`150` – `183`（窗口 2）；
- 状态栏过渡延迟：`733`（= `883 - 150`）；
- 内容 alpha：`617` 起，持续 `133`，即 `617` – `750`；
- 动画总时长：`883`。

## 7. COE 扫光特效

`d.n` 在构造 animator 之前调用 `d.l(context, ta/b)`：

- `new com.coe.syseffect.COESysEffect(context)`
- `load("key_remote_sweep_anim.coz", false, false)`（`assets/key_remote_sweep_anim.coz`，43 080 字节）
- `i(density)` 设置密度
- `sys_action = [256.0f]`
- `_HaloStartPoint` / `_WaveStartPoint` = `ta/b.f()`，竖屏为 `(key_position[0], (y1 + y2) / 2)`，
  即按键中心
- `_HaloEndPoint` / `_WaveEndPoint` = 屏幕中心 `(widthPixels / 2, heightPixels / 2)`

效果是一道从按键位置扫向屏幕中心的光晕与波纹。

**注意它只是「配置」，不负责播**：`d.l` 做完上面这些就返回；真正播它的是
`COESysEffect.playAnim(String)`，而全 APK 只有**一个**调用点 ——
`ActivityTransitionAnimator$a#w`，即远端动画的 `onAnimationStart`。
所以「手动调 `d.l` 就能看到扫光」是错的：没有 adapter 就不会有 `onAnimationStart`，
配好的特效永远不会开始。（早先本文写「它在判定之前执行、是唯一真会发生的效果」，
把「配置」当成了「播放」，已在 9.4 更正。）

## 8. 门禁分析：动画与目标应用无关

### 8.1 排查范围与结论

| 检查项 | 结论 |
| ---- | ---- |
| `d.k` / `d.m` / `d.n` / `d.o` 是否读目标包名 | 否，均不读 |
| `ActionRemoteStartContentProvider.call` 是否按包名放行 | 只把 `pkgName` 交给 `getLaunchIntentForPackage`，不做名单判断 |
| 三个 dex 里是否有 `whitelist` / `white_list` / `blacklist` / `skip_anim` / `no_anim` / `disable_anim` | 一个都没有 |
| 是否按包名查 `PackageManager` 的其它接口（`getApplicationInfo` 等）筛选 | 无 |

动画是挂在「这一次 `startActivityAsUser`」上的 `RemoteAnimationAdapter`，目标应用不参与，
也不需要它实现任何接口。

### 8.2 真正的门禁在启动方式上

1. **能拿到启动 Intent**：`ActionRemoteStartContentProvider.call` 里
   `pm.getLaunchIntentForPackage(pkgName)` 为空就直接走 `super.call`，既不启动也不放动画。
   纯后台服务、无 launcher activity、被停用或隐藏的应用属于这一类。
2. **必须是新起任务**：启动标志固定为 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`
   （`0x10200000`），另加 `android_action_key_launch_type = true`。目标已在栈顶时是一次 resume，
   系统不会套这套进场动画。
3. **必须由这条路径发起**：从桌面点图标、最近任务切回、其它应用内部 `startActivity` 都不走这里。
4. **系统侧仍可一票否决**：动画缩放关闭、省电策略、窗口过渡被系统降级时，`RemoteTransition`
   可能不播或被替换成系统默认过渡。

### 8.3 因此

「只有固定几个应用能用」这个说法在代码里找不到依据。准确的表述是：
**这套动画属于「侧键发起的那一次启动」，所有能被这样启动的应用都一样；不能用的应用是因为拿不到
launch intent，或这次启动不是新任务。**

## 9. 本版到底生不生效 —— 静态判「死」，实测是「活」

### 9.1 条件链

`ActivityTransitionAnimator.t(controller, z, intentStarter)` 的核心：

```
boolean use = callback.a() && z;
RemoteAnimationAdapter adapter = use
        ? new RemoteAnimationAdapter(runner, 883, 883 - 150)
        : null;
intentStarter.invoke(adapter);       // → ua/r.invoke → d.o
p(controller, runner);
runner.B()?.t();
if (use) callback.d(runner);
```

### 9.2 静态分析曾经怎么读的（**结论已被推翻，保留以便复核**）

- **`callback.a()`**：`d$b` 是接口 `ActivityTransitionAnimator$b` 的唯一实现
  （三个 dex 全扫过），四个方法里三个原样委派给 `ActivityTransitionAnimator$b$a`
  —— 即 R8 之后的 `ActivityTransitionAnimator$b$DefaultImpls`：

  | 方法 | `d$b` 的实现 |
  | ---- | ---- |
  | `a()Z` | 委派 `$b$a.b(...)` → `const/4 v0, 0` → `return v0`（读作恒 `false`） |
  | `b(f$g)Z` | 委派 `$b$a.c(...)` → `const/4 v0, 0` → `return v0`（读作恒 `false`） |
  | `c(TaskInfo)I` | 直接 `const/high16 v0, 0xFF000000` → `return v0` |
  | `d(IRemoteAnimationRunner)V` | 委派 `$b$a.a(...)` → `throw new UnsupportedOperationException()` |

- **`z`**：`d.n` 调 `ActivityTransitionAnimator.u(..., mask = 2, ...)`，`mask` 的第 2 位代表 `z`
  取默认值；`u` 的实现是 `and-int/lit8 v4, v4, 2` 命中后 `const/4 v2, 0`，默认值也读作 `false`。
  调用点另外还显式 `const/4 v5, 0`。

于是当时判定 `use` 恒 `false`、`adapter` 恒 `null`、`d.o` 恒走失败分支。

### 9.3 真机实测：它跑起来了

在同一台机器、同一个 APK（sha256 与本文开头一致）上，把模块总闸关掉、让侧键「放行给系统」，
按一次侧键，`logcat` 里出现：

```
WindowManagerShell:  … remoteTransitionInfo = RemoteTransitionInfo {
                        remoteTransition = android.window.IRemoteTransition$Stub$Proxy@104f439,
                        debugName = SpruceLauncher, filter = null }, … debugId = 47 }
WindowManagerShell: RemoteTransition directly requested for (#47) …:
                        RemoteTransition { remoteTransition = …Stub$Proxy@104f439,
                                           appThread = null, debugName = SpruceLauncher, … }
WindowManagerShell: Delegate animation for (#47) to RemoteTransition { … debugName = SpruceLauncher … }
```

`SpruceLauncher` 这个 debugName 全 APK **只有 `d$a.a()` 一处**写（第 2.4 节第 1 步），
而 `d$a.a()` 只在 `adapter != null` 时被调到 ⇒ 运行时 `use == true`、adapter 非 `null`、
`d.o` 走的是**成功**分支，883 ms 卡片与 leash 都真的在跑。

所以 9.2 那两处「恒 `false`」的读法至少有一处与运行时实际不符。**不要再用 9.2 当结论**：
它读的是字节码里的常量分支，而这条链路的实际取值不是它算的那个（可能来自别的实现注入、
也可能来自 `mask` 位的读法偏差）。

### 9.4 由此得到的两条实践结论

1. **要用这套动画，直接转交 `d#m(Context, Intent, 2, false)` 即可** —— 那是三个入口的汇合点
   （第 2.1 节），界面也由它起。不要自己造 `RemoteAnimationAdapter`：
   `IRemoteAnimationRunner` 是隐藏 AIDL，没有框架 stub 就编不过，而复刻出来的只是近似。
2. **「补几个 extra / options 就能让它播」和「手动调 `d#l` 播扫光」这两条都试过，无效**：
   - 只补 `0x10200000` 标志、`android_action_key_launch_type`、`key_position` 与
     `ActivityOptions`（`setLaunchDisplayId(0)` + `setSplashScreenStyle(0)`）：日志证明都带上了，
     观感完全没变 —— 系统不按这几个 extra 挑过渡。
   - 手动调 `d#l`：它只**配置** COE 扫光，不播。`COESysEffect.playAnim` 全 APK 只有一个调用点
     —— `ActivityTransitionAnimator$a#w`，而 `w` 是远端动画的 `onAnimationStart`。
     没有 adapter 就不会被调到，所以「配了特效但没人播」。

## 10. 复刻边界与建议

- 这套实现完全在 `com.oplus.gesture` 进程内，依赖：
  - `Context.startActivityAsUser(Intent, Bundle, UserHandle)`（`Context` 的隐藏 API）
  - `ActivityOptions.makeRemoteTransition(RemoteTransition)`（隐藏 API，或 `com.oplus.wrapper.app.ActivityOptions` 这层 wrapper）
  - `IRemoteAnimationRunner.Stub` + `RemoteAnimationTarget.getLeash()`
  - `SyncRtSurfaceTransactionApplier` + `SurfaceControl.Transaction`（逐帧写 `Matrix` / `Rect` / blur）
  - `com.oplus.flexiblewindow.FlexibleWindowManager.setExtraBundle(...)`（OPlus 私有，只影响折叠 / 分屏行为，
    不是动画的前提）
- 注入侧（`system_server`）不适合复刻：需要在「发起启动的那个 app 进程」里持有自己的
  `IApplicationThread`，并把 `RemoteTransition` 的第三个参数指向它。放在 `system_server` 里会变成
  「系统进程代表自己发起过渡」，行为与抓取到的形态不同。
- **不需要复刻**：既然 9.3 证明这条链路在运行时是活的，复用它的代价只是一个反射调用 ——
  在宿主进程里 `Class.forName("com.oplus.gesture.remote.transition.d", false, context.classLoader)`
  → `d#m(context, intent, 2, false)`，其余（选实现、配扫光、建 animator、
  `startActivityAsUser`）它自己做。注意两点：`Class.forName` 必须显式传**宿主的** ClassLoader
  （模块自己的 dex 路径里没有宿主的类）；`d#m` 用 `UserHandle.CURRENT`，分身上下文不支持。
- 只有在「拿不到宿主类 / 那个方法被改名」时才需要退回自己造 `RemoteAnimationAdapter`，
  把那套 API 与第 3–6 节的常量几何照搬 —— 那是下策（隐藏 AIDL 在没有框架 stub 时编不过）。

#### 10.1 分身目标（`999@包名`）**没有**原版动画，这是限制不是缺陷

  三条路都堵死，别再花时间试：

  1. **`createContextAsUser` 造不出分身上下文**：它要求调用方自己的包在目标用户里也装着，
     真机上是 `Own package not found for user 998: package=com.oplus.gesture`（见 `AppCloneCatalog`）。
  2. **`d#m` 里没有 userId 入口**：它内部写死 `context.startActivityAsUser(…, UserHandle.CURRENT)`，
     只能落到「这个 Context 自己的用户」上。而唯一能拿到的 Context 是主用户的。
  3. **不能覆写那一步**：`ContextWrapper.startActivityAsUser` 是隐藏方法，Kotlin 侧看不到签名、
     无法 override；反射改 `ContextImpl.mUser` 虽然能让 `CURRENT` 解析成分身，但 `d#m` 同一条路上
     还要用 `context.getPackageManager()` / `getResources()`，那两个在分身用户里同样起不来
     （就是第 1 条那个失败），于是还没走到 `startActivityAsUser` 就先崩了。

  现状：分身目标走 `AppLaunchAsUser` 的 ATMS 普通起法（明确 `userId`），**没有原版转场**。
  主用户下启动「装了分身的应用」则**有**原版动画 —— 那条路只要在转交之前挂上
  `OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER`（见 `docs/device/actions/launch.md` 二十二），
  ColorOS 就不会插一次「用哪一份」的选择器。

- 不确定项（只影响观感细节，不影响时间表与几何）：
  - `f::h` 的两个布尔在「打开应用」方向上的取值；
  - `ActivityTransitionAnimator$a` 里状态插值的逐帧公式细节；
  - `remote/transition/c.a().f(context)` 的具体作用。

## 11. 真机验证清单

判据分两层：**应用自己的日志**（`SpruceLauncher`，`Log.d` 两条 / `Log.i` 一条）与
**系统侧的过渡日志**（`WindowManagerShell`）。

```
adb logcat -s SpruceLauncher
adb logcat | grep -E "SpruceLauncher|RemoteTransition"
```

按一次侧键后应当看到：

1. `startActivityWithRemoteAnim: threadId: <tid>` —— 来自 `d.n`，证明进入了这条链路；
2. `WindowManagerShell: … debugName = SpruceLauncher …` —— **adapter 非空**的铁证
   （这个名字只有 `d$a.a()` 写）。有它就说明动画真的在跑；
3. `startActivityIntentWithAnimation fail. RemoteAnimationAdapter is null.` —— 来自 `d.o`。
   这一条若出现，说明 adapter 为 `null`（即 9.2 的读法在**那一次**成立）；
   它与第 2 条**同时出现**是不可能的 —— 遇到这种情况先怀疑拿到的日志不是同一次按压的。
4. 如果出现 `startActivityWithRemoteAnim: setDismissKeyguardIfInsecure`，说明走的是 adapter 非空分支。

其它可核对项：

- `pm path com.oplus.gesture` → `/system_ext/app/OplusGestureUI/OplusGestureUI.apk`；
- 该文件 sha256 与本文开头的值一致；
- 抓日志前确认设备、是否重启过（`uptime`）、日志时间戳，避免拿到缓冲区的旧记录。

## 12. 附录

### 12.1 主要符号对照

| 符号 | 含义 |
| ---- | ---- |
| `remote/transition/d` | 启动与动画组装的单例控制器（`m` / `n` / `o`） |
| `remote/transition/d$a` | `d` 的单例持有者，兼 `ActivityOptions` 生成 |
| `remote/transition/d$b` | `ActivityTransitionAnimator$b` 的唯一实现（空实现） |
| `remote/transition/a` / `b` / `e` | 左 / 右 / 其它方向的具体动画实现 |
| `ActivityTransitionAnimator` | 动画总控：`t` 造 adapter、`s` 持有曲线与时间常量、管理 `RemoteAnimationTarget` 生命周期 |
| `ActivityTransitionAnimator$b` | 回调接口，决定是否启用远端动画 |
| `ActivityTransitionAnimator$f` | 动画生命周期回调（`a()` / `b()` / `c(float)` / `d()`），由 `d` 实现 |
| `ActivityTransitionAnimator$a` | 逐帧写 `SurfaceControl` 的执行者（`p` / `w`） |
| `ActivityTransitionAnimator$Runner` | `IRemoteAnimationRunner.Stub` 实现 |
| `f` | 卡片绘制与计时引擎（`i` 建 `ValueAnimator`、`h` 每帧绘制），由 `ActivityTransitionAnimator` 持有 |
| `f$d` | 10 条插值曲线 |
| `f$e` | 单帧绘制状态（矩形、圆角、alpha、blur 参数） |
| `f$f` | 时间常量（883 / 0 / 150 / 150 / 183） |
| `ta/a` | `BlurrySwitchManager` |
| `ta/b` | 动画实现接口（`e()` 给几何、`f()` 给 COE 起点） |
| `lb/v` | 按键位置与几何换算 |
| `ua/r` | `ve/l` lambda，`invoke(adapter)` → `d.o` |

### 12.2 复现命令

```bash
# 1. 取三个 dex（离线分析用，不需要装到设备）
unzip -o reference/gesture.apk 'classes*.dex' -d .temp/gesture-anim

# 2. 方法级字节码（androguard）
python tools/dexdump.py .temp/gesture-anim/classes.dex method \
  'com.oplus.gesture.remote.transition.ActivityTransitionAnimator' t

# 3. 完整反汇编（控制流以这一份为准）
"$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/dexdump.exe" -d \
  .temp/gesture-anim/classes.dex > .temp/gesture-anim/classes.txt

# 4. 全 dex 字符串排查
python tools/dexdump.py .temp/gesture-anim/classes.dex uses \
  whitelist white_list blacklist skip_anim no_anim disable_anim

# 5. 真机核对 APK 一致性
adb shell sha256sum /system_ext/app/OplusGestureUI/OplusGestureUI.apk
adb logcat -s SpruceLauncher
```
