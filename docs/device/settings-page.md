# 侧键设置页：换页劫持

## 八、侧键设置页（换页劫持用，实测）

### 8.1 类结构

```
com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity   (11 个内部类 $a~$k，混淆严重)
  └ extends BaseActionActivity                                  ← 页面骨架在这里
      └ extends com.oplus.gesture.BaseActivity
```

`BaseActionActivity` 关键成员：

```
字段  F : com.coui.appcompat.toolbar.COUIToolbar     ← 宿主工具栏
方法  onCreate(Bundle)                                ← 只有 70 个 16-bit code units
      onConfigurationChanged(Configuration)
      onDestroy()
```

`onCreate` 的全部动作（`dexdump -d` 读出）：

```
invoke-super BaseActivity.onCreate(savedInstanceState)
getWindow().getDecorView().setForceDarkAllowed(false)
getWindow().setNavigationBarContrastEnforced(false)
getWindow().setNavigationBarColor(0)
setContentView(R.layout.activity_base_action)          ← 整页只有一个布局
f0(); g0(); e0()                                       ← 空壳钩子，由子类覆写
```

**`ActionButtonSettingsActivity` 自己只覆写 `onResume`/`onPause`/`onStop`/`onDestroy`/ `onConfigurationChanged`，没有
`onCreate`** —— 所以换页 hook 要挂在**基类**上。

### 8.2 布局 `activity_base_action`（换页落点）

```xml
<androidx.constraintlayout.widget.ConstraintLayout>   <!-- 占满 -->
    <FrameLayout android:id="@+id/container"          <!-- 0x7f0900e9 内容容器 -->
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
    <include android:id="@+id/appbar_layout"          <!-- 0x7f09008d 顶部栏 -->
        layout="@layout/appbar_with_divider_layout"
        app:layout_constraintTop_toTopOf="parent"/>
</androidx.constraintlayout.widget.ConstraintLayout>
```

- `container` = **内容容器**（换页落点），`appbar_layout` = 顶部栏（含 `COUIToolbar`，**保留**）
- 主题：`style/ActionButtonThemeAppNoTitle`，**parent = `style/Theme.COUI.Main.Dark`** → 在 `container` 里建的 View
  **天然继承 COUI 配色/字体**（宿主样式免费拿到）
- Activity 声明：`android:process="com.oplus.gesture.ui"`（**独立进程**）启动 action：`com.oplus.gesture.StartActionButtonSettingsActivity`，受
  `oplus.permission.OPLUS_COMPONENT_SAFE` 保护（**adb shell 直接 `am start` 会被拒**，但 `su -c am start` 可以）

### 8.3 坑一：不能清空 `container`

最初的实现是 `container.removeAllViews()` 再塞自己的 View —— **直接崩**：

```
FATAL EXCEPTION: main
java.lang.RuntimeException: Unable to resume activity {…ActionButtonSettingsActivity}
	at ActionButtonSettingsActivity.W2(SourceFile:14)
	at ActionButtonSettingsActivity.onResume(SourceFile:127)
```

`W2()` 的字节码：

```
iget-object v0, …Activity.j0:Landroid/view/View;      // 缓存的 View
if-nez v0, +0a                                        // 缓存为空才查
  sget v0, L…/x;.transparent_view:I
  findViewById(v0) → v0
  iput-object v0, …Activity.j0                          // 写入缓存（可能是 null！）
iget-object v0, …Activity.j0
invoke-virtual {v0}, Landroid/view/View;.getLayoutParams:()   // ← 不判空
```

**`transparent_view` 就在 `container` 里。** 清空后 `findViewById` 返回 null → 下一行 `getLayoutParams()` → NPE → 崩在
`onResume`，页面直接退出。

**正确做法：不要动宿主的 View 树，改为「覆盖」。** 把模块页面作为 `container`
的**最后一个子 View**（FrameLayout 后加的画在上面）

- 不透明背景；同时模块根 View 要 `isClickable = true` **吃掉触摸** ——否则点在空白处会穿透到底下的
  `transparent_view`，它的点击链 `t2 → W1() + finish()` 会把页面关掉。

> 一般结论：**换页劫持不要删宿主 View**。宿主的 `findViewById`
> 结果往往被缓存进字段并在后续生命周期里直接解引用（不判空），删掉就会在 onResume 崩。

## 8.4 坑二：设置页读不了 `/data/system`

```
java.io.FileNotFoundException: /data/system/OPlusKey/config.conf: open failed: EACCES (Permission denied)
```

**不是权限位问题**：`/data/system` 是 `drwxrwxr-x system system`（0775）、文件是 `0644 root root` —— DAC 完全允许。

真正原因：设置页跑在宿主域 **`u:r:platform_app:s0`**，该域读不了 **`system_data_file`** （`/data/system`
下全是这个类型）。

**当时设想的解法（后来证伪，见下方批注）：用 LSPosed 官方 `RemotePreferences` 做跨进程共享存储。** 选它的关键理由 —— 它的实现**只走 binder**
（`RemotePreferences` → `IXposedService.requestRemotePreferences`）， **完全不用 `ContentResolver`**，因此在
`system_server` 里也能用（`ContentResolver` 那条路在 system_server 里必 NPE，见 `lsposed.md` 六之五）。

```
XposedServiceHelper.registerListener(object : OnServiceListener {
    override fun onServiceBind(service: XposedService) {
        prefs = service.getRemotePreferences("opluskey_config")
    }
})
```

binder 由框架异步下发，**拿不到时退回读本地文件**（文件通道只有 system_server 用得了）。

> **⚠ 这一段已被实测推翻，别再照着做。** `getRemotePreferences()` 拿到的那个容器在**注入侧是只读的**，
> 写入直接抛 `UnsupportedOperationException: Read only implementation`（实测见本节末那张通道对照表里
> `getRemotePreferences()` 那一行）；libxposed 的设计前提是「模块 App 写、注入侧读」，而我们的界面在
> **宿主进程**、注入侧在 `system_server` —— 两边都不是它预设的用法。`AGENTS.md` 一、9 也已明令禁止走它。
> 真正落地的是「宿主写文件 + 配置广播」，见「跨进程通道：四条里只有广播通」那一节。

宿主 UI 全用 COUI（`COUIRecyclerView` / `COUILinearLayoutManager` / `COUIButton` / `COUIToolbar` /
`ActionButtonOvalDrawable`），但**方法名被混淆**成 `a()` / `b()` / `c()`：

```
ActionButtonOvalDrawable  (extends Drawable)
  a()V  b()V  c()V  d()V  e(F)V  f(F)V  g(F)V  h(F)V  i(FF)V  j(RectF)V  k(F)V  l(FFF)V …
ActionButtonImageView     (extends AppCompatImageView)
  <init>(Context, AttributeSet)  ← 构造器强制要 AttributeSet
  d(FF)V  e(FFF)V  f(IF)V  g(FFFF)V  h(II)V  i(FFFF)V   ← 语义不可知
```

**靠反射调这些等于在猜**，且 ROM 一更新就碎。 **改为用宿主主题属性取色**（属性名是字符串，稳定）：

```
couiColorPrimary / couiColorPrimaryText / couiColorSecondary
couiColorCardBackground / couiColorDivider / couiColorPrimaryNeutral
couiRoundCornerLRadius / couiRoundCornerMRadius
couiTabBackground / couiColorPrimaryNeutral …
```

配合 `Theme.resolveAttribute()` /
`obtainStyledAttributes()`，拿到的是**和系统原页面逐字节相同的配色**（同一个主题），且不依赖任何混淆类。

> 记录这段是因为**猜不出来**：第一版手绘，第二版照抄原版布局，都被否了。

### 列表行：抄 COUI 自己的单选行 `layout/coui_select_dialog_singlechoice`（系统「设置」APK）

```
com.coui.appcompat.checklayout.CheckableLayout
  minHeight        = dimen/coui_delete_alert_dialog_button_height = 48dp
  paddingStart/End = alert_dialog_single_list_padding_left/right = 24dp（对话框版）
  ImageView（行首图标，默认 gone）marginEnd = coui_dialog_layout_margin_horizontal(16dp)
  FrameLayout(alignParentEnd) → RadioButton   background=@null、clickable=false、focusable=false
  LinearLayout(文字区) toStartOf=radio、toEndOf=icon
      TextView 标题  textAppearance = ?attr/couiTextAppearanceHeadline6
      TextView 副标题（可选）
  ImageView 分隔线  visibility=gone、height = coui_list_divider_height(0.33dp)
```

⇒ 行 = **图标 + 标题 + `RadioButton`**，**没有副标题、没有分隔线**（分隔线在 layout 里就是 `gone`）。标题字重来自
`style/couiTextAppearanceHeadline6`：**16sp + `sans-serif-medium`**（不是 bold）。

| 项         | 实测                                    |
| ---------- | --------------------------------------- |
| 行距       | 169px = **48.3dp**（行间无分隔线）      |
| 卡片       | x 56..1215 ⇒ 左右各 16dp、白底圆角 16dp |
| 标题左边界 | 距卡片左边缘 17.1dp（≈16dp 内边距）     |
| 单选圈     | 宽 69px ≈ **20dp**，右边缘距卡片右 18dp |

宿主那套行首图标（`ac_*`）是**给深色页面画的白色图**，放浅色卡片上就是白底白图，

**宿主 APK 里也有这张 layout，但别直接充气**：宿主那份是**对话框版** （左右内边距 24dp、行首图标
`layout_marginVertical=20dp` 且**没有固定尺寸**），直接塞进 48dp 的列表行后，图标会按 drawable 固有尺寸撑开 ⇒
**整行实测 ≈590dp**
（一屏放不下一行）。要用得把内边距/图标尺寸/图标纵向边距三样全改掉。列表行改用「同结构 + 列表尺寸」手搭，行背景取 COUI 那张 layout 里同款的主题 drawable
`?android:attr/selectableItemBackground`。

参照页是 `com.oplus.securitypermission/…PrivacySecuritySettingsDashboardActivity` （**不是** `com.android.settings` 的
`PrivacySettingsActivity`，那个要解锁图案）。它顶部那个「隐私 / 安全」是 COUI 的**分段控件**（胶囊轨道 + 圆角滑块），取值来自
`SecurityPermission.apk` 自带的那份 COUI：

```
style/SegmentButton : layout_height=40dp、android:padding=dimen/coui_segment_padding(4dp)
dimen/segment_button_height               = 32dp     ← 滑块高 = 40 - 4×2
color/coui_color_segment_button_background = #0f000000(浅) / #1fffffff(深)   ← 轨道
color/coui_color_segment_button_indicator  = #b2ffffff(浅) / #33ffffff(深)   ← 滑块
dimen/coui_segment_min_height = 28dp、coui_segment_min_width = 52dp
```

两个颜色都是**半透明覆盖色**：`0f000000` 压在页面底色 #f0f1f2 上 = **#e2e3e4**，

滑块 32dp 高、距轨道左/上/下各 4dp、宽 = (轨道宽 - 8) / 项数；文字字形高 ≈12.6dp ⇒ **14sp**。

**这个控件没法直接 new / 充气**：宿主进程那份 COUI（811 个类）里 **没有** `COUIChipTab*` / `COUITabLayout`
/ 任何分段控件类（它们只在 `SecurityPermission.apk` / `Settings.apk` 那份更新的 COUI 里），连
`attr/couiSegmentButtonStyle` 都没有 —— 宿主只有 `dimen/segment_button_height`
这几个尺寸。所以按它自己的尺寸与颜色搭出来。

> 历史记录：中间一版做的是「文字 + 底部指示线」，依据是宿主 `style/COUILargestTabLayoutStyle` 与
> `COUISlidingTabStrip#updateIndicatorPosition` （指示线取 tab 内 `TextView` 的
> `getLeft/getRight`、`coui_tablayout_indicator_padding`=0
> ⇒ 宽度=文字宽、画在条最底部）。**那套 tab 在这个页面里根本不存在**，

### 动效：按下态可以用「按住不放再截图」量出来

`input tap` 太快，截图抓不到按下态。用 **`input motionevent DOWN x y`** 卡住不放，截图后再
`UP`，就能量到真实的按下反馈：

| 控件                    | 按下时       | 量到的值                                                                          |
| ----------------------- | ------------ | --------------------------------------------------------------------------------- |
| 分段控件（隐私 / 安全） | 整条轨道缩放 | 轨道宽 1160→1126px、高 140→136px，中心不动 ⇒ **scale 0.97**                       |
| 列表行（经典铃声）      | 整行叠灰     | 行底色 白(255,255,255) → (224,224,224) ⇒ **叠 12% 黑**（#1f000000）；**行不缩放** |

分段控件的滑动动效参数（`SecurityPermission.apk` 的资源）：

```
dimen/coui_segment_regular_extra_width = 7dp   ← 滑动中滑块额外拉长
dimen/coui_segment_over_distance       = 4dp   ← 滑动时越过目标的量
dimen/coui_segment_switch_distance     = 5dp   ← 拖动判定为「换 tab」的位移
dimen/coui_segment_shadow_radius       = 1dp
dimen/coui_segment_shadow_offset       = 2dp
```

> **交叉验证（开源库）**：`suqi8/coui`（Apache-2.0，Compose Multiplatform）的 `TabRow` 文档自述「完整复刻 ColorOS
> 16 的分段按钮 `COUISegmentButtonLayout`」，其常量与上面每段最小宽
> **52dp**（宽度按文字自然宽算、至少 52dp 后铺满整行）。它的 `MarkPreference`（对应 COUI 的
> `COUIMarkPreference`）说明标记用 `coui_btn_check_mark` → radio 矢量
> **24dp**（选中=主色圆盘+白心、未选中=灰描边圆环），也与本模块的 24dp 单选圈一致。该库依赖 Compose
> Multiplatform，**不能引进本模块**：它自带一整套 Compose Multiplatform 运行时，与本项目 `ui/` 用的
> Jetpack Compose 撞车（两条运行时同时在场，`ComposeView` 那边会拿到对方的 `Recomposer`）—— 只能当规格参照。
> 另注：项目里另有一条「**注入侧不得 import 界面层依赖**」的边界（见 `AGENTS.md` 第一部分），
> 那条管的是 `inject/` 那几个包；`ui/` 用 Compose 是**当前实现**，不违反它。

COUI 里**没有**给分段控件滑动时长的 dimen（那套 `touchanimation` 类是混淆的， `COUITouchMotionAnimator` 的方法名是
`A/B/C/D`、字段 `j/k/l`，靠反射调等于猜 ——宿主进程里虽然有这个类，但**没用它**）。本模块自己定 260ms +
Decelerate 落位、110ms 做按下缩放，其余数值全部照上表。

**列表行的按压反馈会被「重建列表」掐掉**：点行时若 `removeAllViews()` 重建，只改勾选态、不重建，反馈才出得来。

### 主题：宿主把这一页写死成深色，且**运行配置是 night**

- Activity 清单主题 = `ActionButtonThemeAppNoTitle`，parent 写死 **`Theme.COUI.Main.Dark`**
  ⇒ 系统开着浅色，这一页（含顶栏）也是全黑。而**应用级配置**（`applicationContext`）一直是浅色。
- 后果：`coui_color_label_primary` / `coui_color_card_background` 这类 **DayNight 颜色资源**
  按配置取到夜间值 ⇒ 只换主题会得到「白底 + 白字 + 深色卡片」的混合体。
- 解法（两条并用）：
    1. `onCreate` **之前** `setTheme("AppNoTitleTheme")`（宿主自带、浅色、`windowActionBar=false`）；不能用
       `Theme.COUI.Main.Light`：它 `windowActionBar=true`，宿主 `BaseActionActivity#f0()` 的 `setSupportActionBar` 会抛
       `IllegalStateException` ⇒ 页面打不开；
    2. 页面用**强制浅色的 `ContextThemeWrapper`** 来建（Activity 上 `applyOverrideConfiguration` 已太晚，抛
       `getResources() or getAssets() has already been called`）。
- 顶栏标题要在浅色下重新上色：顶栏是宿主在 night 配置下建的（标题取到夜间白字），而顶栏底色 `couiColorBackground`
  **没有 night 变体、恒为 #ffffffff** ⇒ 白底白字。

### 配色：属性名对应表（写错就是整页亮蓝或白底白字）

|---|---|---| | 页面背景 | **`couiColorBackgroundWithCard`**（浅色 #f0f1f2 / 深色 #ffffff） | 用
`couiColorBackground`：那支**没有 night 变体、恒为 #ffffffff**，白卡片浮不起来； 用
`couiColorPrimary`（品牌强调色）→ 整页亮蓝 | | 卡片背景 | `couiColorCardBackground`（浅 #ffffffff
/ 深 #1affffff）／宿主列表那张 drawable 是 浅 #ffffffff / 深 #ff242424 | | | 主文字 |
`couiColorLabelPrimary`（浅 #e6000000 / 深 #e6ffffff） | | | 描边/分隔线 | `couiColorDivider`（浅 #1f000000
/ 深 #33ffffff） | | | **tab 选中** | **`couiColorPrimaryNeutral`**（浅 #e6000000 / 深 #e6ffffff） | 用了
`couiColorPrimary`（品牌蓝）→ 整条 tab 是 Material 味 | | **tab 未选中** | **`couiColorSecondNeutral`**（浅 #8a000000
/ 深 #8affffff） | 用了 `couiColorLabelSecondary` |

上表里带深浅两值的都是 **DayNight 颜色资源**（`@color/coui_color_*`，资源表里有 `(night)` 变体）——
**它们跟着「运行配置」走，不跟着主题走**：配置是 night 时，浅色主题也会取到夜间值。

依据是 `color/coui_tab_default_color` 这条 selector：选中/按下 → `?attr/couiColorPrimaryNeutral`，正常 →
`?attr/couiColorSecondNeutral`，禁用 → `?attr/couiColorDisabledNeutral`。即 **COUI 的 tab 是中性灰白，不是品牌蓝**。

COUI 是**随各 App 打包**的（`oplus-framework.jar` 里没有
`com/coui/`），而这个进程里能加载的只有宿主 APK 的那份 —— 它的控件**方法名与枚举常量都被混淆**：

```
COUIButton            extends AppCompatCheckBox…  方法全是 a() b() c()…
COUIButton$ButtonType  枚举常量 a / b / c / d / e / f / g
COUIStepperView       是「数字步进器」(getCurStep/setMaximum)，不是分段控件
COUIPageIndicator     是页码圆点，不是 tab
```

且**没有** `COUITabLayout` / `COUIRadioButton`。⇒ 只能走**主题属性名 / drawable 名字符串**这两条稳的路。

### 图标三套名字不统一（拼前缀会错）

```
中图标  ac_<f>_middle_icon      例：ac_take_screenshot_middle_icon
条图标  ac_tab_icon_<f>        例：ac_tab_icon_screen_shot
圆底    action_button_oval_<f> 例：action_button_oval_screenshot
```

⇒ `ui/model/ActionCatalog.kt` 里**直接写全名**，不拼前缀。

设置页侧（`u:r:platform_app:s0`）的结果：

| 路径                                                           | 写           | 读           |
| -------------------------------------------------------------- | ------------ | ------------ |
| `/data/user_de/0/com.oplus.gesture/files/opluskey_config.conf` | **OK**       | **OK**       |
| `/sdcard/OPlusKey/config.conf`                                 | FAIL(ENOENT) | FAIL         |
| `/storage/emulated/0/OPlusKey/config.conf`                     | FAIL(ENOENT) | FAIL         |
| `/data/system/OPlusKey/config.conf`                            | FAIL(EACCES) | FAIL(EACCES) |
| `/data/local/tmp/opluskey_config.conf`                         | FAIL(EACCES) | FAIL         |
| `/data/misc/opluskey/config.conf`                              | FAIL(ENOENT) | FAIL         |

⇒ 只能放**宿主 App 自己的私有目录**。写入时必须**逐级放开遍历权限**：

```
/data/user_de/0/com.oplus.gesture              0700 → 0711   ← 不放开 system uid 进不来
/data/data/com.oplus.gesture/files         0775
…/files/opluskey_config.conf              0644
```

`system_server` 能否读（SELinux 的 `app_data_file` 权限）**待重启后验证**。

| 入口                                     | 结果                                                                                                                                                                                                                |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `XposedServiceHelper.registerListener`   | binder **永远不来**。它靠**模块自己 App 进程**里的 `XposedProvider` 接收（`call("SendBinder")`），而本模块平时没有 App 进程（唯一组件是那颗跳板 Activity，用户不点就不起）⇒ binder 等不到。日志里一条相关记录都没有 |
| `XposedInterface.getRemotePreferences()` | 能拿到（日志 `远端配置通道已就绪`），但**注入侧是只读的** —— 写入抛 `UnsupportedOperationException: Read only implementation`                                                                                       |

libxposed 的设计前提是「**模块 App 写、注入侧读**」；我们的界面在**宿主 App 进程**里，既不是模块 App、也不是注入侧读的那一方 ⇒ 两条都不成立。

## 8.8 两个自己踩的坑（都是"看起来像功能坏了"的那种）

1. **诊断代码踩了真实数据路径**。`ConfigPathProbe` 的候选 #1 就是配置文件本身，每跑一次就把配置覆盖成 `probe-by-page`
   ⇒ 表现为「选了没反应 / 重开回到默认值」。⇒ **诊断用的临时路径必须和真实数据路径错开**，诊断完立刻删。
2. **选动作必须同时 `enabled=true`**。页面读不到旧配置时回落到 `Config.DEFAULT`，而默认
   `plus_enabled=0`（"装上模块不改变行为" 是刻意的默认值），

> **这一节是历史记录，不是当前实现**（2026-10-11「全量迁移」后已弃用）。设置页现在是「**宿主真·COUI 控件 + 平台组件**」的纯 View 树，见
> **第十节**； `Md3eSettingsPage.kt` 与当时那套 Compose 页面一起删了，那一刻的产物里 `Landroidx/` 为 0。
> **注意这条只记录 2026-10-11 那一刻**：界面后来迁回了 Compose（`ui/` 那一套，`ComposeView` 塞进宿主页），
> 现在产物里**有** android 依赖 —— 别拿这句当「不许用 Compose」的判据。

设置页最终改用 Compose + Material 3 渲染（`page/Md3eSettingsPage.kt`）。 `ComposeView` 的崩点在 `onAttachedToWindow` →
`ensureCompositionCreated` → `resolveParentCompositionContext` →
`getWindowRecomposer`，**这个时机在 try/catch 之外**，一旦抛就是整个设置页进程挂掉。要让它在宿主这棵树上跑起来，下面四条**缺一不可**：

|---|---|---| | 1 | `ComposeView` 的 `context` 是 `ContextThemeWrapper`，**不是** owner。`context as? LifecycleOwner`
静默返回 null | 从 `baseContext` 链里挖出 Activity（`Md3eSettingsPage.activity`） | | 2 | 宿主
`ActionButtonSettingsActivity` **自己也不实现**那三个 owner 接口（日志
`lifecycle=false viewModel=false savedState=false`）。它不是 AppCompat 系 | **自建**：`LifecycleRegistry` +
`ViewModelStore` + `SavedStateRegistryController` | | 3 | `resolveParentCompositionContext` 只沿 **parent 链**
找 owner（**不含自身**）。报错原文点名 `androidx.constraintlayout.widget.ConstraintLayout`（宿主布局根） | 在
`container.addView(page)` **之后**挂满整条祖先链（实测 8 层：ComposeView → container → ConstraintLayout → … →
decorView） | | 4 | `SavedStateRegistryController.performAttach()` **只能在 lifecycle 的 INITIALIZED 阶段**调用；先推
`RESUMED` 再 attach 会抛 `Restarter must be created only during owner's initialization stage`
| 顺序：`performAttach()` + `performRestore(null)` → **最后**才 `currentState = RESUMED`。 绝不能推到
`DESTROYED`（Compose 的 `Recomposer` 收到就永久取消） |

日志锚点（这四行齐了才算接通）：

```
Compose 可用性：宿主自备 owner=false（lifecycle=false viewModel=false savedState=false），宿主没有就用自建兜底 ⇒ 放行
宿主不提供 ViewTree owner（lifecycle=false viewModel=false savedState=false）⇒ 已自建 LifecycleRegistry + ViewModelStore + SavedStateRegistry 兜底
设置页用 Compose + Material 3 Expressive 渲染
ViewTree owner 已挂满祖先链 8 层（ComposeView → decorView）
```
