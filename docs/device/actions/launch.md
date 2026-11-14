# 打开应用：多开、快捷方式与转场动画

## 二十二、ColorOS 多开选择器「弹不弹」只看 **intent 上的 oplus flag**，与调用方是谁无关（实测，2026-10-17，PLK110）

### 22.1 现象

模块里「打开分身」走 `IActivityTaskManager#startActivityAsUser(intent, userId)`，分身**确实起得来**，
但每次都先弹出 `com.oplus.multiapp/…chooser.MultiAppResolverActivity` 让人选「用哪一份」。
目标 userId 已经在 `startActivityAsUser` 里指定了，选择器纯属多余的一次确认。

### 22.2 先排除的错方向

第一轮从 `MultiAppResolverActivity` 的 dex 字符串池里扒出两个 extra 塞进 intent：

```
android.intent.extra.user_handle        ← UserHandle
oplus.intent.extra.chooser_finish_self  ← boolean
```

**没用**，选择器照弹。原因很简单：这两个键是**选择器自己**读的（决定它弹出来之后默认选谁），
而我们连「要不要弹」那一步都还没过 —— 塞给它的东西它压根没机会看。

### 22.3 对照实验：`am start --user 998` 不弹

同样一个分身 target，用 shell 起就不弹：

```bash
adb shell am start --user 998 -n com.tencent.mm/.ui.LauncherUI
```

```
D MultiApp-CMAService: replaceIntentForMultiApp, callingPackage=com.android.shell/0 targetPackage=com.tencent.mm/998
D MultiApp-CMAService: Don't show ResolverActivity, special callingPackage= com.android.shell/0 ...
D MultiApp-CMAService: resolveActivityForMultiApp, has cached userId: 998
```

⇒ 判定在 **system_server**（`com.android.server.am`），日志 TAG 是 `MultiApp-CMAService`，
和 `com.oplus.multiapp` 那个应用自己的 UI 无关。

### 22.4 定位：`OplusMultiAppManagerService#shouldShowResolverActivity`

实现体在 `/system/framework/oplus-services.jar`（`classes.dex`，三个 dex 均 STORED，可直接 grep）。
按 TAG 字符串定位到 `com.android.server.am.OplusMultiAppManagerService`，再用「字符串 → 引用它的方法」
反查出判定函数：

```
shouldShowResolverActivity(Landroid/content/Intent;Lcom/android/server/wm/SafeActivityOptions;
                           Ljava/lang/String;Ljava/lang/String;II)Z
```

字节码（`const/16` + `hasOplusFlag` 两连）：

```
0031: const/16  0x0800
0033: hasOplusFlag(0x800)    → 成立 → 0082 "has OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER or isFromGameSpace"
003a: const/16  0x1000
003c: hasOplusFlag(0x1000)   → 成立 → 0042 "has OPLUS_FLAG_MULTI_APP_DIRECT_MULTI_APP, go to multi app"
```

`hasOplusFlag` 的读取路径：

```
Intent.getIntentExt() → IIntentExt.getOplusFlags() & flag != 0
```

**即：只看 intent 自己带的 oplus flags，不看 callingPackage。** `com.android.shell` 只是更早在
「special callingPackage」那一条分支就被放行，不是唯一的路。

### 22.5 正解

```kotlin
// com.oplus.content.OplusIntent.OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER
private const val OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER = 0x800

Intent::class.java.getMethod("addOplusFlags", Int::class.javaPrimitiveType)
    .invoke(intent, OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER)
```

`Intent#addOplusFlags` / `setOplusFlags` / `removeOplusFlags` / `getOplusFlags` 都是 OPPO 往
AOSP `Intent` 上加的，编译期不存在，只能反射。加不上**不阻断启动**（顶多多点一次选择器）。

实测日志（`oflg` 就是 oplus flags）：

```
Intent { act=MAIN cat=[LAUNCHER] flg=0x10000000 pkg=com.tencent.mm cmp=…/.ui.LauncherUI
         oflg=0x800 mCallingUid=10277 }
Don't show ResolverActivity, has OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER or isFromGameSpace
resolveActivityForMultiApp, has cached userId: 998
```

`com.tencent.mm/998` 与 `com.autonavi.minimap/999` 都直接进了目标用户，选择器不再出现。

### 22.6 可复用的取证手法

1. `ls -l /system/framework/*.jar` 里挨个 `grep -c <日志TAG>` —— dex 若是 STORED 就能直接命中，
   省掉解压。本次 `oplus-services.jar` 一击即中。
2. 拉文件：`adb exec-out cat <路径> > 本地`。`adb pull` 读不了 `system_ext`；
   `adb shell base64` 传 30 MB 会断（`base64: invalid input`）。
3. 手写 DEX 解析足够用：`string_ids` / `type_ids` / `method_ids` / `class_defs` → `class_data`
   里 `method_idx` 反查类名；再按 opcode → format 长度表逐条走 `code_item`，
   就能拿到「某方法引用了哪些字符串 / 方法」与 `const` 的值。比配 JADX 快得多。
4. **别在字节里盲扫 opcode**：`0x1a` / `0x6e` 这种单字节在指令流里到处都是，
   不按长度表跳会扫出海量噪声（本次第一版就栽在这，结果 101 条调用全是假的）。

---

## 二十九、`LauncherApps.startShortcut` 在 Android 16 上被 BAL 静默拦掉（实测，2026-10-20，PLK110 / ColorOS 17.0.0）

现象：`appshortcut:com.eg.android.AlipayGphone/1002`（支付宝「收付款」）按下侧键**什么都不发生** ——
宿主侧日志停在「快捷方式 → com.eg.android.AlipayGphone/1002（user=0）」，没有异常、没有失败提示。

`ShortcutService` 那条路是**系统代发**（拿快捷方式创建者那条 PendingIntent 去起），Android 15/16 的
后台启动限制会拦，而 `startShortcut` 返回 `void` ⇒ **调用侧完全看不出来**。系统日志（按键那一刻）：

```
D ShortcutService: Getting shortcuts for launcher= com.oplus.gesture userId=0 pkg=com.eg.android.AlipayGphone
E ActivityTaskManager: Background activity launch blocked! …
    callingUid: 10367; realCallingPackage: com.oplus.gesture; realCallingUid: 10277
    realCallerUidHasVisibleActivity: false; resultIfPiSenderAllowsBal: BAL_BLOCK
E ActivityTaskManager: Abort background activity starts from 10367
I ActivityTaskManager: START … (BAL_BLOCK) result code=102
```

同一份日志里 `realCallerStartMode: MODE_BACKGROUND_ACTIVITY_START_ALLOWED` —— 宿主**自己**起是允许的，
被拦的是「替别人（快捷方式创建者）起」。

**对策**：先问 `LauncherApps.getShortcuts` 要**快捷方式自己那条 intent**（`ShortcutInfo.getIntent()`），
再用宿主身份 `startActivity` 起；拿不到 intent（真机见过：微信「扫一扫」返回 `null`）或直起失败，
才退回 `startShortcut`。实现在 `host/action/AppShortcutLaunch`。

**判据**：宿主日志里「快捷方式（直起 intent）→ …」= 走的是能过的那条；「快捷方式（系统代发）→ …」=
退路，仍可能被拦。后者只应出现在 `getIntent()` 为 `null` 的应用上。

### 二十九之二、「options 起法其实成了，是**验收**在骗人」（实测，2026-10-20 补，同机）

同一支动作下一轮（宿主 pid 21448，`appshortcut:w:com.eg.android.AlipayGphone/1002`）：

```
20:31:37.838  快捷方式 w：… 现有任务 #-1（小窗=false）          ← 起点：没任务
20:31:37.877  快捷方式 → 已按小窗 options 发出
20:31:38.581  快捷方式 → 小窗 …（任务 #1201）                    ← 0.7 秒就成了 ✓
20:31:42.412  小窗验收：… 还没有任务（冷启动中），再等一轮        ← 又按了一次，开始验
20:31:43.115  小窗验收：… 还没有任务（冷启动中），再等一轮
20:31:43.817  小窗 options 没被吃住（任务 #-1），改「先起再原地收」← 误判
20:31:43.833  快捷方式（自发 PendingIntent）→ …                  ← 多发的第二份
20:31:45.255  快捷方式 → 原地收成小窗 …（任务 #1206）            ← 4 秒后才有响应
```

两条定论：

1. **`OplusActivityManager#getAllTopAppInfos()` 不包含「刚刚起、还在注册」的那份任务** ——
   700ms / 1400ms / 2100ms 三个点全读到 `-1`，而任务实际在 0.7 秒时就已经是小窗了。
   这张表只能用来**看现状**，不能用来**验一次刚发出的启动**（连「按 taskId 点名」也读不到）。
2. **多发的每一发都直接变成用户可见的延迟**：误判之后的第二次 PI 让应用自己的跳板链接着跑，
   最终窗口在 4.1 秒后才落定 —— 用户口径正是「按了还是长时间等待」。
   ⇒ 「先起再收」只在第 0 轮真起，重试轮**只等不发**。

## 13.1 症状

点「按键绑定」往里走（push）看着正常，**点返回（pop）时上一页一上来就盖住大半屏**，像是没滑就直接铺满了。

## 13.2 根因：入场内容画在最上层 ⇒ iOS 式 pop 的参数在这里不成立

`androidx.compose.animation.AnimatedContent` 内部维护 `currentlyVisible` 列表，
**新状态追加在末尾 ⇒ 后画 ⇒ 入场内容恒定在最上层**。它**没有**「把入场内容放到下层」的参数。

```kotlin
slideInHorizontally(spec) { -it / 4 } togetherWith slideOutHorizontally(spec) { it }
```

—— 新页只走 `width/4`、旧页整条滑出。这套参数成立的**前提是「新页在旧页下面」**
（旧页滑开才把新页露出来）。放到「入场在最上层」的 `AnimatedContent` 里，同一组参数就变成了：**转场第 0 帧新页已经落在
`-width/4`，一上来就盖住 3/4 屏**，再慢慢蹭回原位。

## 13.3 取证方法（可复用）：整帧刚体平移拟合

1. `settings put global animator_duration_scale 10`（350ms 转场放大到 3.5s，便于连拍）。
2. 设备端起连拍：`for i in …; do screencap -p /data/local/tmp/fr/t$i.png; done &` 然后
   `sleep 1; input tap <x> <y>`（`screencap` ≈330ms/帧，3.5s 转场约 10 帧）。
3. 本地按**列签名**（`mean over rows`）对 `ref_before` / `ref_after` 做一维交叉相关，求每帧的最佳平移量：
    - 新页位移 `o`：`F[x] ≈ ref_after[x - o]`
    - 旧页位移 `s`：`F[x] ≈ ref_before[x + s]`

    残差 ≈ 0.00 表示**完美刚体平移**（不是裁剪、不是淡入）。

## 13.4 实测数据（PLK110，1272×2772，density 560）

**修复前**（`pop`，tap 返回键 154,232）：

| 帧  | 新页位移 `o` | 残差 | 含义                                           |
| --- | ------------ | ---- | ---------------------------------------------- |
| t05 | **−303**     | 0.00 | ≈ `−width/4` = −318 ⇒ **第 0 帧就盖住 76% 屏** |
| t06 | −204         | 0.00 |                                                |
| t07 | −129         | 0.00 |                                                |
| …   | …            | 0.00 | 单调收敛到 0                                   |

旧页位移 `s`：60 → 455（残差 6.9，被新页遮住大半，拟合不可信）。

**修复后**（`pop2`，同上坐标）：

| 帧  | 新页位移 `o`                 | 残差                     |
| --- | ---------------------------- | ------------------------ |
| t05 | 无匹配（新页**完全在屏外**） | 5.75                     |
| t06 | −700                         | 3.62（运动模糊抬高残差） |
| t07 | **−622**                     | **0.00**                 |
| t08 | −439                         | 0.00                     |
| t09 | −307                         | 0.00                     |
| t10 | −209                         | 0.00                     |
| t11 | −142                         | 0.00                     |
| t12 | −87                          | 0.00                     |
| t13 | −51                          | 0.00                     |
| t14 | −27                          | 0.00                     |
| t15 | −11                          | 0.00                     |

旧页位移 `s`：76 → 150 → 196 → 229 → 254 → 271（向 `width/4` = 318 收敛）。

⇒ 新页**第 0 帧整页在屏幕外**，再自左滑入；旧页往右退 1/4。与 push 完全镜像。

## 13.5 顺带确认：push 本来就是对的

同一方法测 push（首页 → 按键绑定）：

| 帧  | 新页 `o` | 旧页 `s` | 由 `o` 推 e | 由 `s` 推 e |
| --- | -------- | -------- | ----------- | ----------- |
| t06 | 860      | 103      | 0.324       | 0.324       |
| t07 | 548      | 181      | 0.569       | 0.569       |
| t08 | 373      | 225      | 0.707       | 0.708       |
| t09 | 247      | 256      | 0.806       | 0.805       |
| t10 | 170      | 275      | 0.866       | 0.865       |
| t11 | 107      | 291      | 0.916       | 0.915       |

两页**同一进度 `e`**，且 `o` 从 ≈`width` 单调收敛到 0、`s` 从 0 收敛到 `width/4` —— 完全符合
`slideInHorizontally { it } togetherWith slideOutHorizontally { -it/4 }`。所以**只有 pop 那支是错的**。

## 13.6 修法

```kotlin
if (targetState.ordinal > initialState.ordinal) {
    slideInHorizontally(spec) { it } togetherWith
        slideOutHorizontally(spec) { -it / 4 }      // push：新页自右入，旧页退左 1/4
} else {
    slideInHorizontally(spec) { -it } togetherWith
        slideOutHorizontally(spec) { it / 4 }       // pop ：新页自左入，旧页退右 1/4
}
```

结论：**在 `AnimatedContent`
里写转场，参数必须按「入场内容在最上层」来配**。想用「新页在下层」那套 iOS 参数是做不到的 —— 只能把入场偏移改成整幅
`±it`。

取证期间 `.temp/` 被外部清空过两次（同期还冒出 `ArrowPreference.kt`、 `SwitchPreference.kt`、`coui_docs.html`
等本会话没建过的文件）——**同一工作区有并发会话**。对策：抓帧脚本与产物放
`.temp/anim/`，并且**抓取 + 拉取 + 分析尽量串在同一条命令里**；设备端 `/data/local/tmp/fr` 可当二级缓存（重拉很快）。

## 四十七、侧键「打开应用」的转场动画：远端窗口动画 + 自绘卡片 + COE 扫光（反编译 `reference/gesture.apk`，2026-11-08，PLK110 / ColorOS 17.0.0）

> 完整技术文档在 `../../features/launch-animation.md`（调用链、常量、曲线、几何、逐帧模型、门禁分析、复刻边界）；
> 这里只留结论与取舍。

> 结论先行：原版**没有**用 `overridePendingTransition` / anim 资源 / `ActivityOptions.makeCustomAnimation`。
> `com.oplus.gesture` 把目标 Activity 的启动包进 `RemoteAnimationAdapter`（再包成 `RemoteTransition`），
> 过渡期间自己画一层圆角卡片 overlay、逐帧写目标窗口的 SurfaceControl（leash），另外播一个 COE 系统特效
> `assets/key_remote_sweep_anim.coz`。总时长 **883 ms**，状态栏过渡延迟 **733 ms**。
> 注意：本版 APK 把这套动画的回调实现成了空实现，adapter 恒为 null，这条路径实际不启用 —— 见 47.7。

### 47.1 调用链（类名与方法名均来自 `reference/gesture.apk`）

| 环节 | 位置 |
| ---- | ---- |
| 侧键打开应用 | `i9.g0.q(Intent, boolean)` → `remote/transition/d.m(Context, Intent, 2, Z)` |
| 同一个入口（供参考） | `ActionRemoteStartContentProvider.call$lambda$3$lambda$2$lambda$1`、`ActionButtonKeyService.e()`（打开侧键设置页）都调 `d.m(..., 2, false)` |
| 选动画实现 | `d.m` → `d.k(Context, int)`；`d.n(Context, Intent, ActivityTransitionAnimator$d, ta/b, Z)` 负责组装与启动 |
| 实际启动 | `d.o(...)`：`ActivityOptions` + `setLaunchDisplayId(0)` + `setDismissKeyguardIfInsecure()`，额外塞 `Spruce_qucik_start_app=true`，经 `com.oplus.flexiblewindow.FlexibleWindowManager.setExtraBundle` 后 `startActivityAsUser(intent, bundle, UserHandle.CURRENT)`；TAG `SpruceLauncher` |

`d.k` 的类型分支：

- 类型 `1` / `3` → `remote/transition/e`（名字 `anim_right`）；
- 类型 `2`（打开应用、打开设置页）→ `lb/v.a()`（即 `i9.b.h()` 的 `getActionButtonIsRight`）为真走
  `remote/transition/b`，否则 `remote/transition/a`。
  - `b.a()` 再按 `p9.e.a(context)` 返回 `anim_right_inner_screen` / `anim_right_front_screen`；
  - `a.a()` 返回 `anim_left`。
- 真机 PLK110 的侧键在右侧，因此走 `remote/transition/b`。

### 47.2 过渡参数（`ActivityTransitionAnimator.<clinit>` 实测常量）

- `RemoteAnimationAdapter(runner, 883, 883 - 150 = 733)`：总时长 883 ms，状态栏过渡延迟 733 ms。
  同时 `ActivityTransitionAnimator.t = f$f.e() - 266 = 617`（后面内容淡入的起点）。
- 时间表 `f$f` 构造为 `(883, 0, 150, 150, 183)`，对应字段 `a=883, b=0, c=150, d=150, e=183`；
  取值为 `e()=883`、`c()=0`、`d()=150`、`a()=150`、`b()=183`。
  所有子阶段都由 `f$b.b(timings, progress, start, duration)` 换算：
  `constrain((progress * 883 - start) / duration, 0, 1)`。
- 曲线：`android.view.animation.PathInterpolator`
  - `(0, 0, 0.2, 1)`、`(0.33, 0, 0.67, 1)`、`(0.3, 0, 0.1, 1)`、`(0, 0, 0, 1)`、`(0.2, 0, 1, 1)`、`(0, 0, 0.6, 1)`；
  - 位置类另用 COUI 的阻尼振荡插值器 `com.coui.appcompat.animation.p`（继承 `BaseInterpolator`），
    `f$d` 的 10 个槽位依次为：
    - `positionInterpolator` = `p(0.6, 0)`
    - `positionInterpolatorX` = `p(0.45, 0)`
    - `positionBelowStartInterpolator` = `p(0.3, 0)`
    - `positionBelowInterpolator` = `p(0.8, 0)`
    - `positionBelowEndInterpolator` = `p(0.8, 0)`
    - `positionXInterpolator` = `p(0.6, 0)`
    - `blurInterpolator` = `PathInterpolator(0.33, 0, 0.67, 1)`
    - `scaleInterpolator` = `PathInterpolator(0.3, 0, 0.1, 1)`
    - `contentBeforeFadeOutInterpolator` = `PathInterpolator(0, 0, 0.2, 1)`
    - `contentAfterFadeInInterpolator` = `PathInterpolator(0, 0, 0.6, 1)`

### 47.3 起始矩形与 `key_position`

- `lb/v.b()` 给出一对设计值 `(477f, 600f)`（设计基准高 **2376**），`lb/v.h(ctx, f) = round(f / 2376 * heightPixels)`。
- `lb/v.c(ctx)` 返回 `int[]{x, h(477), x, h(600)}`，`x = 侧键在右侧 ? widthPixels : 0`。
  这个数组既是 `d.n` 塞进 Intent 的 `key_position`，也是卡片动画的起始矩形。
- 起始状态（`remote/transition/a::b` / `b::b`，竖屏 rotation 0 / 2）：
  - 右键（`b`）：`top = h(477)`、`bottom = h(600)`、`left = right = widthPixels` —— 右边一条**零宽竖线**；
  - 左键（`a`）：同一条线在左边（`left = right = 0`）。
  - 横屏另有分支：rotation 1 放到上边（`top = bottom = 0`），rotation 3 放到下边（`top = bottom = heightPixels`），
    横向范围同样由 477 / 600 缩放得到。
- 状态里两个 `float` 都写 `48.0f`（`f$e.q/u`），在 `f::h` 里当作**圆角半径**填进 8 个 corner radii（前 4 个取 `k()`，
  后 4 个取 `e()`，两个值同为 48）。

### 47.4 自绘卡片与窗口逐帧

- `f::i` 建一个 **883 ms 的线性 `ValueAnimator`**（progress 0 → 1），每帧走 `f::h`：
  `GradientDrawable.setBounds(state.rect - overlay 的屏幕偏移)` + `setCornerRadii(...)` + `setAlpha(...)`。
- 卡片 alpha 的两段窗口：`[c() = 0, d() = 150 ms]` 与 `[a() = 150 ms, b() = 183 ms]`；后一段是
  `1 - interpolator.getInterpolation(t)` 的反向分支，另有一个用 `PorterDuffXfermode` 复制的
  `GradientDrawable` 副本作遮罩。
- 目标窗口（`RemoteAnimationTarget.getLeash()`）由 `ActivityTransitionAnimator$a` 通过
  `SyncRtSurfaceTransactionApplier` 逐帧写 `Matrix` / `Rect` / blur：
  - 终点取 `getScreenSpaceBounds()`，缩放比 `max(state.width / bounds.width, state.height / bounds.height)`，
    `Matrix.setScale(...)` 后 `postTranslate` 对齐，再把状态矩形反算成窗口内 `Rect`；
  - 内容 alpha 从 `t = 617 ms`（`e() - 266`）起、窗口长 **133 ms**，套 `(0, 0, 0, 1)`；反方向用 `(0.2, 0, 1, 1)`。
- `ActivityTransitionAnimator$Runner` 继承 `IRemoteAnimationRunner$Stub`，`ActivityTransitionAnimator$a` 才有
  `onAnimationStart` / `onAnimationEnd` 那套（`p(...)` / `w(...)`）。

### 47.5 COE 扫光特效

`d.n` 在启动前调 `d.l(context, ta/b)`：

- `new com.coe.syseffect.COESysEffect(context)` → `load("key_remote_sweep_anim.coz", false, false)`
  （`assets/key_remote_sweep_anim.coz`，43 KB，APK 里确实存在），并 `i(density)`；
- `sys_action` 传 `[256.0f]`；
- `_HaloStartPoint` / `_WaveStartPoint` 取 `ta/b.f()`：竖屏下是 `Pair(key_position[0], (y1 + y2) / 2)`，
  即**按键中心落在屏幕边缘的那一点**；
- `_HaloEndPoint` / `_WaveEndPoint` 取屏幕中心 `(widthPixels / 2, heightPixels / 2)`。

即：打开应用时先有一道从按键位置扫向屏幕中心的光晕 / 波纹，再由远端窗口动画接管。

### 47.6 复刻边界

- 这套东西全在 `com.oplus.gesture` 进程内，依赖 `startActivityAsUser` +
  `ActivityOptions.makeRemoteTransition(RemoteTransition(IRemoteAnimationRunner, ...))` +
  `SyncRtSurfaceTransactionApplier`（SurfaceControl leash）。注入侧（system_server）不适合复刻，
  要做得在宿主进程里用同一套 API。
- 未读实的两处：`f::h` 的 `v17` / `v18` 两个布尔在「打开应用」方向上的取值（决定卡片是否在
  150–333 ms 淡出、是否叠 Xfermode 副本），以及 `ActivityTransitionAnimator$a` 里状态插值的逐帧公式；
  这两处只影响观感细节，不影响上面的时间表与几何。

### 47.7 与目标应用无关，但本版恒不启用

是否套这套动画**不按应用区分**：

- `d.k` / `d.m` / `d.n` / `d.o` 与 `ActionRemoteStartContentProvider.call` 全都不读目标包名；三个 dex 里
  也没有 whitelist / blacklist / skip_anim 一类字符串。动画是挂在「这一次 `startActivityAsUser`」上的
  `RemoteAnimationAdapter`，目标应用不参与，也不需要它支持什么。
- 目标侧唯一的前置条件是 `PackageManager.getLaunchIntentForPackage(pkgName)` 非空
  （`ActionRemoteStartContentProvider.call` 的 `onRemoteStart` 分支）：拿不到就直接走 `super.call`，
  既不启动也不放动画 —— 纯后台服务、被停用 / 隐藏的应用属于这一类。
- 启动标志固定为 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`（`0x10200000`），
  另加 `android_action_key_launch_type=true`。目标已在栈顶时是 resume、不是新起任务，系统不会套这套进场。
- 打开侧键设置页走的是同一条路（`ActionButtonKeyService.e()` → `d.m(..., 2, false)` →
  `ActionButtonSettingsActivity`），同样不带包名判断。

但这版 APK 上 adapter 恒为 `null`：

- `ActivityTransitionAnimator$b$a`（R8 后的 `ActivityTransitionAnimator$b$DefaultImpls`）里 `a()` 与
  `b(f$g)` 都直接 `return false`，`d(runner)` 直接抛 `UnsupportedOperationException`；
  `d$b` 是接口 `ActivityTransitionAnimator$b` 的唯一实现，四个方法全部原样委派给它。
- `ActivityTransitionAnimator.t` 造 adapter 的条件是 `callback.a() && Z`；而 `d.n` 经
  `ActivityTransitionAnimator.u(..., mask = 2)` 把 `Z` 取默认值 `false`（调用点也显式传 `0`）
  ⇒ 恒走 `t` 的 `adapter = null` 分支。
- `d.o` 拿到 null 只打一条 `Log.i("SpruceLauncher", "startActivityIntentWithAnimation fail. RemoteAnimationAdapter is null.")`
  就 `return`，`startActivityAsUser` 根本不会执行 —— 也就是说这条路既不放那套 883 ms 卡片动画，
  也不会把应用启动起来。

因此在本机（`/system_ext/app/OplusGestureUI/OplusGestureUI.apk`，sha256
`95b5434da60582a3ae4a53103bc19c4ddf9810420335abdd0d5ce9efe780ade3`，与 `reference/gesture.apk` 一致）上，
上面 47.2–47.4 的卡片 / leash 动画属于**未启用的代码**；侧键打开应用时真正看得见的是 `d.l` 的 COE 扫光
（那一步在 adapter 判定之前，不依赖 adapter）+ 系统默认的启动过渡。真机复核方式：按一次侧键后抓
`SpruceLauncher`，应当只有 `startActivityWithRemoteAnim: threadId:` 一条，没有 adapter 相关行；
若能看到 `...RemoteAnimationAdapter is null` 即为此结论。若真机上确实出现过从按键位置展开的圆角卡片，
说明那条动画不在本章这条链路上（本章只覆盖 `com.oplus.gesture` 内的这套实现）。

## 四十八、`ActionRemoteStartContentProvider` 那条入口在本机是活的（实测，2026-11-14 / PMA110 / ColorOS 17.0.1）

§47 的结论（「adapter 恒 null、这条链不启用」）在这台机器上**不成立** —— 原版那条 provider 入口一按
就真的把远端动画播了出来。这条既是 §47 的更正，也是「我们自己转交时为什么没有动画」的判据。

调法（provider 是 `exported`，`permission = com.oppo.permission.safe.PRIVATE`，root 直接过）：

```
content call --uri content://com.oplus.gesture.actionbuttonkey.ActionRemoteStartContentProvider \
  --method onRemoteStart \
  --extra pkgName:s:com.coloros.ocrscanner \
  --extra className:s:com.oplus.scanner.ui.main.CameraActivity
```

抓到的行（`adb logcat`）：

```
ActionRemoteStartContentProvider: call:  method=onRemoteStart  bundle=…
ActionRemoteStartContentProvider: call:  pkgName=com.coloros.ocrscanner  activityName=…
SpruceLauncher: startActivityWithRemoteAnim: threadId: 6144          ← 主线程
WindowManagerShell: RemoteTransition directly requested for (#265) … debugName = SpruceLauncher
WindowManagerShell: Delegate animation for (#265) to RemoteTransition { … SpruceLauncher }
v: createLeash leashSurface=Surface(name=…Task=2209…_transition-leash…)
```

**它和我们那条路差三样**（`dexdump` 读 `ActionRemoteStartContentProvider.call` 的 `onRemoteStart` 分支，
一句一句对出来的）：

| 原版做的 | 我们原来做的 |
| --- | --- |
| `pm.getLaunchIntentForPackage(pkg)` | `AppEntry.intentOf`（同一份来源） |
| `intent.addFlags(270532608)` = `NEW_TASK \| RESET_TASK_IF_NEEDED` | 只有 `NEW_TASK` |
| `intent.putExtra("android_action_key_launch_type", true)` | 没有 |
| `mHandler.post(...)` → **主线程**再调 `d.m` | 在 `GestureExec` 的**工作线程**上直接调 |

缺 `RESET_TASK_IF_NEEDED` 的后果最直接：目标的任务已经在表里时，这一下只是「把那个任务提到前面」，
不是一次新的 OPEN 转场 —— 远端动画没有转场可挂，用户看到的是**完全没有动画**。
`com.coloros.ocrscanner` 正好是这一档（清单里 `alwaysRetainTaskState=true`，任务一直留着）。

**照这三样对齐过一次，结论是「不能照抄」**（同日真机）：补上 `RESET_TASK_IF_NEEDED` + `launch_type`、
转主线程、去掉 `oflg` 之后，**原本有原版动画的应用全都不动了**（用户口径「你改的本来能用原版动画
也都不行了」）。已整条还原。所以那三样是**原版 provider 那条入口的配套**，不是这条转场的前置条件：
`d.n` 里 `ActivityTransitionAnimator` 的 executor 本身就往主线程投递，我们再套一层 post 反而把
「启动」与「转场」之间多插一拍；两个标记也会把这条链路从「桌面启动」带偏。
**这条链路只认 `ACTION_MAIN` + `CATEGORY_LAUNCHER` 那一对**（`stockLaunchIntent` 现在只做这一件事）。

复核路径：`python tools/dexdump.py <OplusGestureUI classes.dex> method com.oplus.gesture.actionbuttonkey.ActionRemoteStartContentProvider call`。

**`oflg=0x800` 那一处也还原了**：`OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER` 是我们为「别弹分身选择器」
加的，原版那条入口没有 —— 但去掉它同样属于「照抄原版」，一并还原：装了分身的应用在主用户下启动
会被问「用哪一份」，选择器一盖上转场就断了，这一位是必需的。START 行里的 `oflg=0x800` 是**预期**。

判据行（还原后的正常形态）：`打开应用：已转交原版启动链路（由宿主建 RemoteTransition）` 之后，
`logcat` 里出现 `Delegate animation for (#…) to RemoteTransition { … SpruceLauncher }`；
START 行带 `oflg=0x800`、**不带** `android_action_key_launch_type`。

对齐前后都要看的一件事：**同一条路在「应用已经在最近任务里」时本来就没有可见转场** ——
`LAUNCH_SINGLE_TOP`（应用在前台）与「任务已存在」这两种都只把窗口提到前面，动画画的是已经画好的
窗口。真机验证必须先回桌面（或先切到别的应用）再按，否则会把「没有可动的东西」误判成「动画没播」。
