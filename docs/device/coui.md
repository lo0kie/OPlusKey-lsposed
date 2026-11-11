# COUI 组件库的实测行为与观感

## 8.10 COUI 的颜色多是「半透明覆盖层」，Compose 不做自动叠加

```
couiColorBackgroundWithCard → #ff000000   页面底（实心）
couiColorCardBackground     → #1affffff   卡片 = 10% 白  ← 覆盖层，不是最终色
couiColorLabelPrimary       → #e6ffffff   主文字 = 90% 白 ← 覆盖层
couiColorDivider            → #33ffffff   分隔线 = 20% 白 ← 覆盖层
```

`#1a1a1a`）。**Compose 不会** —— 半透明色直接画上去会透出底下 / 被父级裁掉。

⇒ `page/Md3eSettingsPage.kt` 里有个 `overlay(overlayArgb, baseArgb)`
做标准 alpha 合成，把 COUI 的覆盖色算成实心色再喂给 MD3 色板。分段控件的两级底色也是这么算的

## 8.11 MD3 组件的取色链（覆盖 `primary` 没用）

`#4A4458`（MD3 默认 `surfaceContainerHighest` 的紫灰调）、卡片底 `#1A1A1A` （MD3 默认
`surfaceContainerLow`）。实际取值链：

| 组件                           | 取的颜色角色              |
| ------------------------------ | ------------------------- |
| `SegmentedButton` **未选中**底 | `surfaceContainerHighest` |
| `SegmentedButton` **选中**底   | `secondaryContainer`      |
| `Card` 默认底                  | `surfaceContainerLow`     |
| `ListItem`                     | 走外层 `Card`，自身透明   |

⇒ 要改外观必须覆盖 `surfaceContainer*` 与 `secondaryContainer`，只改 `primary` 不够。

## 8.12 `SingleChoiceSegmentedButtonRow` 的等分与图标位（「tab 被截」的成因）

两个坑叠在一起：

1. 给 `SingleChoiceSegmentedButtonRow` 加 `fillMaxWidth()` **不会**让各段等分 ——它内部是 `Row(wrap_content)`
   语义，各段仍按内容取宽，宽度不均、末段顶出父宽被裁。⇒ 每段显式 `Modifier.weight(1f)`。
2. `SegmentedButton` **默认为选中态的勾选图标预留宽度**（约 18dp），三段平分本就紧，再被占掉一截，文字就被裁。⇒ 传空的
   `icon = {}` 收掉。

## 8.13 Compose 版的最终观感（深色模式实测）

| 元素             | 量到的值                                                       |
| ---------------- | -------------------------------------------------------------- |
| 页面底           | `#000000`                                                      |
| 顶栏底           | 刷色前 `#101010`（宿主 `couiColorBackground`）→ 刷后 `#000000` |
| 卡片底           | `#1a1a1a`（`#1affffff` 叠黑，与 COUI 一致）                    |
| 分段按钮选中底   | `#333333`（`#33ffffff` 叠黑）                                  |
| 分段按钮未选中底 | `#000000`（轨道 `#1fffffff` 极淡）                             |
| 单选圈选中色     | `onSurface` 合成后约 `#e6e6e6`（亮白实心圈）                   |
| 行文字左边距     | 252px（有图标 / 无图标行**一致**，`uiautomator` 量证）         |

## 8.14 图标：宿主那套不够用，改用 SVG 图标库

**宿主只有一部分动作有图标**（逐项核对 `com.oplus.gesture` 资源表）：

| 动作                                         | 宿主图标                                             |
| -------------------------------------------- | ---------------------------------------------------- |
| 不操作 / 保持原样                            | `ac_no_operation_middle_icon`、`ac_tab_icon_nothing` |
| 打开相机                                     | `ac_camera_middle_icon`、`ac_tab_icon_camera`        |
| 打开设置 / 联系人 / 时钟 / 微信              | **无**（只能取目标 App 的真实图标）                  |
| 返回键 / 主页键 / 上一首 / 下一首 / 播放暂停 | **无**（`keyevent:` 类动作，宿主没有对应资源）       |

⇒ 混搭的结果是"有的有图、有的空着"，而且宿主那套是**给深色页面画的白色描边图** （浅色下要 `mutate().setTint()`
重新上色）。**统一改用图标库**。

| 方案                            | debug 包 | release 包（R8） |
| ------------------------------- | -------- | ---------------- |
| 不引入库（宿主图标 + App 图标） | 25.6MB   | —                |
| `material-icons-extended:1.7.8` | **66MB** | **1.6MB**        |

**debug 包会打进全部几千个图标**（+40MB），但 R8 在 release 里能精确裁到只留实际引用的那几个 ⇒
**交付物（release）反而更小**。所以这个库可以放心用。

该库里**没有 `PlayPause`**（Material Symbols 才有）。可用的是 `Icons.Outlined.PlayArrow` / `Pause` /
`PlayCircle`。选图标前先在 jar 里核一遍类名：

```bash
unzip -l <material-icons-*-classes.jar> | grep -iE "outlined/<IconName>"
```

部分图标只在 `automirrored` 包下有 RTL 版本（如 `Undo` / `Chat` / `ArrowBack`），但**普通 `outlined`
版本也在**，中文（LTR）场景用 `Icons.Outlined.*` 即可。

实现上的防呆：`ActionCatalog.Spec.icon` 是**无默认值的必填字段**
——新增动作时漏填图标会**编译不过**，从机制上保证「每一个都有」。

## 8.15 顶栏底色：与页面底**不是同一个值**，要自己刷

```
顶栏底 #101010 左右   ← 宿主的 couiColorBackground（而且不均匀，9~16）
页面底 #000000        ← couiColorBackgroundWithCard（我们给页面用的）
```

两者在深色下不是一个值，接缝处有一道看得出的浅灰条。**改顶栏**（而不是改页面底）——页面底要留给卡片做对比，且顶栏那个值是宿主主题给的、跟着 ROM 变。

顶栏结构（`appbar_with_divider_layout`）实测刷色时遍历到的容器层：

```
AppBarLayout / COUIToolbar / COUIActionMenuView     ← 这三层刷背景色
  └── 返回键（ImageButton，自带圆形背景）/ 标题 TextView / 分隔线 View   ← 叶子，不碰
```

只刷 **`ViewGroup`（容器层）**，叶子一律不动 —— 否则会把返回键的圆形背景刷成方块。只刷最外层没用，会被 `COUIToolbar`
自己那份不透明底色盖住 ⇒ **两层都刷**。

宿主 APK 的**资源路径被混淆**（`res/-K.9.png`、`res/1I.ogg` 这种），按名字 `aapt2 dump xmltree --file res/layout/...` 会
`failed to find file` ——想读宿主的 layout 只能靠运行时遍历 + 打日志，别在反编译上耗时间。

## 8.16 MD3 `RadioButton` 默认拿 `primary` 当选中色（会看不清）

MD3 的 `RadioButton` 选中色默认是 `primary`。而我们的 `primary` 被设成了 COUI 的**分段滑块色**
`#333333`（很暗的中性灰）—— 压在 `#1a1a1a` 卡片上只亮一点点，

⇒ 必须显式给：`RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface)`。

**一般规律**：MD3 组件的默认取色角色（`primary` / `secondaryContainer` /
`surfaceContainer*`）在自定义色板里都要**逐个核对**，不能只改 `primary` 就以为全套生效。

## 8.17 状态栏「不是沉浸式」的真相：系统在状态栏 inset 上叠了一层渐变（实测）

（日志为证），可截图逐行取色仍是：

| 位置                 | 改前                            | 改后      |
| -------------------- | ------------------------------- | --------- |
| `y=0..50`（状态栏）  | `(8,8,8)`→`(10,10,10)` **渐变** | `(0,0,0)` |
| `y=110..140`（顶栏） | `(13,13,13)`                    | `(0,0,0)` |
| `y≥160`（页面）      | `(0,0,0)`                       | `(0,0,0)` |

- 顶栏自己已经是纯黑、却量到 `(13,13,13)` ⇒
  **那层渐变画在内容之上**，改任何 View 的背景都盖不住；刷背景自然「看不见效果」。
- 渐变的**范围正好是状态栏 inset**：模块量到 `statusBarInset = 141px`，而渐变的边界也在 141px 左右。
- 成因是 edge-to-edge 强制之后（宿主 `targetSdk=37`，`setStatusBarColor()` 被忽略）系统给状态栏加的**对比度薄纱**（AOSP
  `mStatusBarContrastWhenTransparent`，对 targetSdk ≥ Q 默认开）。旁证：宿主自己的 `BaseActionActivity#onCreate`
  只对**导航栏**关了这个（`setNavigationBarContrastEnforced(false)`），漏了状态栏。

**修法（`ui/HostChrome.kt`）**：

1. `Window.setStatusBarContrastEnforced(false)` —— 关掉系统那层薄纱；调用点必须在 `setContentView`
   **之后**（默认值来自主题属性 `android:enforceStatusBarContrast`，首次建 `DecorView` 时会按主题重算一遍）；
2. 原先还有一条「在 `decorView` 最上层 addView 一块实心 View」的兜底，**已撤**（见下面「原版页那一条不归我们画」）。

**一般规律**：状态栏/顶栏这一带的「颜色不对」先按**逐行取色**量一遍再动手 —— `(8,8,8)`
这种「比页面亮一点点」的值就是有半透明覆盖层的特征， `(0,0,0)` 才是真的没人盖。

- **那一条本来就在模块页面的画布里**：页面 edge-to-edge（`Scaffold(contentWindowInsets = 0)`）、顶栏 107dp 含状态栏 inset、底色铺到
  `y=0`；
- **§8.17 里那块 `decorView` 最上层的实心遮罩恰好把它挡在模块控制之外**
  ——顶栏的模糊在那条上断掉、浮层的全屏 dim 也够不到（于是另开了一条 `SettingsPage.onStatusBarDim`
  回调手动推颜色，本次一并删掉）；是它 / 它子树里的叶子 View 在那条上作画；2026-10-11 顶栏改自绘后宿主顶栏整条
  `GONE`，那一条就只剩模块页面在画；
- 所以现在**两档都不盖**：模块页面在显示时那一条完全归页面（模糊 / dim / 深浅切换全是它自己的事），
  「打开自带的快捷键界面」露出宿主原版页时也**不刷任何颜色**（见下一条）。

### 原版页那一条不归我们画（宿主 8.6.0，`aapt2 dump xmltree` + 截图像素实测）

- 宿主顶栏 `R.id.appbar_layout` = `include` 的 `appbar_with_divider_layout`：外层 `AppBarLayout` 的
  `android:background` 是 **`@android:color/transparent`**（`0x0106000d`），里面那层 `COUIToolbar`
  的背景是 `drawable/coui_with_card_toolbar_bg` —— 一张 17×17 的**纯色**九宫格（无 marker 边框），
  `nodpi` 那张每个像素都是 `(240,241,242)` = `#F0F1F2`，`night-nodpi` 那张是纯白（运行时按主题着色）。
- 但**状态栏那一截不是它画的**：`getLocationOnScreen` 实测 `appbar top=141`（正好是状态栏 inset）、
  `container top=0` —— 条子在 appbar 之上，由原版页自己的内容铺（截图 `y=0..140` 是
  `(8,8,8)`→`(14,14,14)` 的渐变）。
- 结论：露原版页时任何写死的底色都是在猜（黑 / 白各试过一次，都表现为「顶上一条白带」）。
  现在 `HostChrome.paintStockChrome` 不刷颜色，只关薄纱 + 把图标刷成**浅色**。
- **图标恒为浅色**：这一页恒为深色（清单主题 parent 写死 `Theme.COUI.Main.Dark`，页面自己又铺近黑的底），
  系统是浅色时也一样。按系统深浅判会得到「黑底 + 深色图标」—— 截图里时钟与信号只剩 `(10,10,10)` 的灰影。



（§8.17 里两条是一起上的，「光关薄纱」那次没单独验过）。判据是一行取色 ——状态栏那一条应当与紧邻其下的顶栏**完全同色**；若仍比顶栏**亮一点点**（半透明覆盖层的特征），说明薄纱还在，得在「薄纱」与「受控」之间取舍（见
`coverStatusBar` 的注释）。

`dumpsys activity recents` + 宿主清单（`aapt2 dump xmltree`，宿主 8.6.0）实测：

```
* Recent #0: Task{… #23989 type=standard A=10277:com.oplus.gesture nonResizable}
  intent={act=com.oplus.gesture.StartActionButtonSettingsActivity
          id=oplus.intent.category.START_FROM_SETTINGS_MAIN_PAGE
          flg=0x10000000  pkg=com.oplus.gesture mCallingUid=1000}
  Activities=[ActivityRecord{… ActionButtonSettingsActivity t23989}]   ← 任务里就这一个
* Recent #1: Task{… A=1000:com.android.settings.root}                   ← 设置自己那条
```

- 清单：`ActionButtonSettingsActivity` 声明了 **`android:launchMode="singleInstance"`（值 3）**、
  `android:process="com.oplus.gesture.ui"`、`android:resizeableActivity="false"`、
  `screenOrientation="1"`（竖屏锁定），**没有** `android:taskAffinity` （⇒ affinity 取默认值 = 包名
  `com.oplus.gesture`）。
- **`singleInstance` 意味着这一页天生独占一个任务**，无论谁拉、怎么拉，都不可能并进「设置」的 task；设置侧又是用
  `FLAG_ACTIVITY_NEW_TASK` （`flg=0x10000000`，`mCallingPackage=com.android.settings`）拉它的，两条叠加 = 独立任务。
- 卡片标题取的是**根 Activity 所属应用**的标签，而 `com.oplus.gesture` 的 `application-label-zh-CN`
  就是「**手势体感**」⇒ 最近任务里显示「手势体感」。
- **与本模块无关**：模块不碰任务 / 启动模式，`ui/` 下没有任何 `startActivity` /
  `setTaskDescription`（`FLAG_ACTIVITY_NEW_TASK` 只出现在 `host/action/ActionRunner.kt`，
- 真要改只能改**卡片语义**、改不了任务归属：`Activity.setTaskDescription()` 换标题与图标，或
  `AppTask.setExcludeFromRecents(true)` 从最近任务里去掉。

## 十八、COUI `SnackbarHost` 的裁切边界 = **item 自己的盒子**（实测，2026-10-14，PLK110）

### 18.1 现象

首页那条常驻提示（`SnackbarHost`）的胶囊，**顶上那道影子被齐平整切**：不只「看着淡」，而是有一条一眼可见的直边。用户 2026-10-14 报的原文是「这个 snackbar 的阴影被裁切了」「阴影还是被切断」「切断和 padding 没关系，肯定是有容器设置了不外溢」——**最后一句是对的**。

### 18.2 取证手法（可复用）：逐列扫像素、找**硬跳变**

整屏截图（`adb exec-out screencap -p`）后用 PIL 逐列扫，判据不是「颜色深浅」而是**相邻像素的跳变**：

- 真影子的衰减是**平滑**的：沿中心列从胶囊边往外，每 ~4px 掉 1 个灰阶（实测斜率 ≈0.2~0.27 灰阶/px）；
- 被裁掉的地方是**一步跳变**：底色 `240` 直接到 `230`（10 个灰阶）或 `0` 直接到 `3`。

实测数据（PLK110，1272×2772，density 560 —— **1dp = 3.5px**）：

| 位置         | 数据                                                               | 结论                                                                        |
| ------------ | ------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| 胶囊白底     | y ∈ [2473, 2603]，高 131px = 37.4dp                                | 单行胶囊                                                                    |
| 胶囊**上方** | y=2459 处 240→230（**跳 10**），此后每 4px 掉 1 阶，到 2470 才 227 | **顶上被切**，可见只剩 14px = **4dp**                                       |
| 胶囊**下方** | y=2604 起 220 → 2709 平滑回到 240，全程无跳变                      | 底下**没**被切，有 30dp 可用                                                |
| 胶囊**左侧** | y=2500/2538/2560 三条线上，影子起点都精确落在 x=229                | 距胶囊左边 285 恰好 **56px = 16.0dp**，且起点是 0→3 的跳变 ⇒ **横向也被切** |

「横切在 16.0dp、上切在 4.0dp」这两个数**正好等于库给胶囊的内边距** —— 见下。

### 18.3 机制：`SnackbarHost` 是「内容多高就多高」的 `LazyColumn`，视窗 = item 盒子

反编译 `coui-ui` 的 `SnackbarKt`（`javap -c`，`Snackbar.kt:272` / `Snackbar.kt:279`）得到结构：

- `SnackbarHost(state, modifier, canSwipeToDismiss, content)`：外面一个 `Box`（挂调用方的 `modifier`），里面是
  **`LazyColumn`** —— `reverseLayout = true`、`verticalArrangement = Arrangement.Top`、
  `horizontalAlignment = CenterHorizontally`、`userScrollEnabled = false`、 **`contentPadding` 底部 28dp**；
- item 内容 = `Snackbar(data, modifier, …)`，而 `Snackbar` 自己第一层就是
  **`Modifier.padding(horizontal = 16.dp, vertical = 4.dp)`**，然后才
  `dropShadow(shape, Shadow(elevation = 26.dp, offset = DpOffset(0.dp, 4.dp), alpha = 0.14f))` + `Surface`。

于是几何完全对上：

```
影子需要的余量（elevation 26dp、下偏 4dp）  ： 上 ~22dp、下 ~30dp、左右 ~26dp
库实际给的（胶囊外面）                      ： 上 4dp、下 4dp+28dp=32dp、左右 16dp
                                              ↑ 只有这一档不够，就是被切的那条边
```

**视窗 = item 盒子向下多出 `contentPadding` 的 28dp**，所以底下 32dp 刚好够、四周只够库里给的那点。 `dropShadow`
本身**不裁**（影子能画到胶囊下方 30dp，远超自己的 layout bounds）—— 裁的是 item 外面那层。

### 18.4 试错过的那条路（别再走）

给 `SnackbarHost` 自己加 `Modifier.padding(bottom = 16.dp)` —— **没用**： `padding`
是从视窗**往里抠**，而视窗跟着内容一起缩，切痕会**跟着胶囊一起上移**（实测两次截图的切痕位置差正好等于所加的 padding）。这条也解释了为什么用户会说「和 padding 没关系」。

### 18.5 正解

走 `SnackbarHost` 的 `content` 槽位（签名是 `content: @Composable (SnackbarData) -> Unit`， **不带 `Modifier`
参数**），把胶囊包进一层留白：

```kotlin
SnackbarHost(state = …, modifier = …) { data ->
    Box(Modifier.padding(SNACKBAR_SHADOW_ROOM)) { Snackbar(data = data) }
}
```

裁切边界是 **item 的盒子**，所以只有撑大 item 才会把边界一起撑出去。`SNACKBAR_SHADOW_ROOM = 20.dp` （配上库自己的 4dp /
16dp ⇒ 上 24dp、下 52dp、左右 36dp，四周都够）。

**改后复测（同一台机、同一判据）**：胶囊上方从「距顶 4dp 处 240→230 一步跳」变成
**连续 77px（22dp）的平滑衰减**（每 ~4px 掉 1 阶，全程无跳变），下方仍 105px 平滑 —— 顶上那一刀没了。胶囊整体比改前高 4dp（去掉外层 16dp
inset、补 20dp item 留白 ⇒ 净 +4dp），符合预期。

### 18.6 顺带否掉的嫌疑人

页面根那个 `Box(Modifier.fillMaxSize().padding(padding).clipToBounds())`（`SettingsPage.kt`） **不是**元凶：它是
`fillMaxSize`、底边就是屏幕底边，而切痕在 `y=2459`（屏幕中部偏下），它物理上切不出这条线。那一句 `clipToBounds()`
继续留着裁 push/pop 转场出画的部分。

## 二十、COUI 浮层列表：**滑动会被当成「选行」**（实测，2026-10-15，PLK110）

用户报「添加命令滑动时偶尔会直接触发点击」：在「添加命令」那张长列表里滑动，偶尔会选中手指下面那一行（加了一步命令 / 开了二级菜单 / 菜单自己关了）。

**取现场的办法**（这一段是新增 `ui/GestureProbe` 的由来）：行的点击由库在**抬指那一刻**
派发（`PopupListGestureHost.onUp` →
`handle.onClick()`），出问题时日志里只剩「某一行被点了」这个结果，看不出那一下其实滑了多远。给整列外面挂一层只记账、**不消费事件**的手势探针（`PointerEventPass.Initial`
读，抢在任何消费之前），把「位移 / 峰值 / 移动次数 / 用时 / 列表内容移位」连同结果一起打出来。

**现场（用户真机，00:22:29 与 00:22:51 两笔）**：

```
浮层列表：这一段是滑动 —— 位移 261px / 峰值 261px（阈值 28px），移动 64 次，用时 764ms，列表内容移位 261px
浮层点选：创建自定义命令 —— 位移 261px / 峰值 261px（阈值 28px），移动 64 次，用时 764ms，列表内容移位 261px

浮层列表：这一段是滑动 —— 位移 273px / 峰值 273px（阈值 28px），移动 32 次，用时 418ms，列表内容移位 273px
浮层点选：截图 —— 位移 273px / …
多条命令：加第 1 步 → shell:service call color_screenshot 1
```

判据一句话：**「点选」那一行的「位移」是 0~几 px 才是真点击；带着几百 px 就是滑动被当成了点击。**
（两笔的列表内容也确实跟着滚了 261 / 273px —— 不是「列表没动能滚」那种，是**滚着也在选**。）

**机制**：库的判据只有内部那个
`isDynamicSelection`（源码：`!scrollState.canScrollForward && !scrollState.canScrollBackward`，等于「这一列量不出滚动范围」时才允许**拖拽选行**）。长列表显然能滚，可那一瞬间它仍被判成了「拖拽选行」⇒ 整段滑动期间按压高亮跟着手指走，抬指就选中。同一次会话里 30 次滑动只中 2 次（我这边用
`input swipe` 复现不到，只有「点开菜单后紧接着滑」中过一次）——
**是竞态，不是手势形状**：位移 / 时长 / 移动次数三档在有问题的与没问题的之间完全重叠。

**收口（本项目侧，不动库）**：`MenuPopup.kt` 的 `MenuRow` 在派发那一行之前，按**这一段手势自己的位移**
兜一道 —— 越过阈值（`peak > touchSlop`）就是滑动，**吞掉这次选中**。阈值取库同一个
`LocalViewConfiguration.touchSlop`；真点击是 0~几 px，不会误吞。副产物：滑动不再「选中」，短菜单上那套「按住滑到某行再松手＝选它」也一并没了 —— 用户要的是「滑就是滑，点就是点」。⚠️ 只挂在浮层行上：页面行（「添加命令」那一行、各步骤行）的点击是 Compose
`clickable` 给的，拖动会被页面滚动消费掉，没有这个毛病（同一次会话的日志里它们的位移都是 0px）。

## 二十六、COUI 的弹窗浮层**和页面同一个窗口**（源码核对，2026-10-19）

这一条是为纠正一次错判记的 —— 它让「弹窗输入框叫不出键盘」多绕了两轮。

- **事实**：`OverlayDialog` → `DialogContentLayout` → `popupHost`，最终落在
  `COUIPopupUtils.COUIPopupHost()`，而它由根 `Scaffold` 直接调用（`Scaffold.kt` 里
  `LocalDialogStates provides dialogStates` 那一处）。也就是说弹窗内容**没有独立的
  Android `Dialog` / 独立 window**，它和页面共用同一棵 Compose 视图树、同一套
  `LocalXxx`。
- **推论**：`LocalSoftwareKeyboardController` / `LocalWindowInfo` / `LocalView` 在弹窗内容里
  与在页面里读到的是**同一个对象**。库自己在 `DialogEntry` 里收键盘用的就是这个控制器。
- **踩过的坑**：误以为「弹窗是另一个窗口，页面那侧的控制器 show() 会服务错窗口」，于是把
  「叫键盘」从 `FocusOnShow` 挪进输入框的 `onFocusChanged`。挪过去之后那段代码**拿不到
  `show`**，弹窗关掉后协程还在跑，真机就是「弹窗消失又把键盘拉起来」。
- **正确归属**：凡是要跟着 `show` 生灭的动作（`requestFocus`、`keyboard.show()`）都放
  `FocusOnShow` —— 它是 `LaunchedEffect(show, windowFocused)`，`show` 一变 false 立刻取消。
  输入框那一侧只留「聚焦后把光标挪到末尾」这种一次性、无副作用的动作。
- **另一个必须知道的时序**：`DialogEntry` / `DialogContentLayout` 在 `showState` 变 false 时
  **无条件** `keyboardController.hide()`。「收集按键 → 添加按键」是两个独立弹窗交替，
  一关一开挤在同一帧 ⇒ 新弹窗里叫一次键盘会被那一下 `hide()` 盖掉，要连叫几帧
  （`IME_KICK_FRAMES`，取 6）。

## 四十三、弹窗里把 `LocalView` 换成脱离窗口的 `View`：Compose 的 `Popup` 崩宿主，输入框光标也挪不动（真机 logcat + 字节码核对，2026-11-04，PLK110 / ColorOS 17.0.0）

名字弹窗里点一下输入框（光标手柄一露）必崩，崩的是宿主进程 `com.oplus.gesture.ui`，三次复现同一栈：

```
java.lang.IllegalStateException: Composed into the View which doesn't propagate ViewTreeLifecycleOwner!
  at androidx.compose.ui.platform.AbstractComposeView.resolveComposeViewContext(ComposeView.android.kt:355)
  at androidx.compose.ui.platform.AbstractComposeView.attachedToWindow(ComposeView.android.kt:464)
  at androidx.compose.ui.window.PopupLayout.onAttachedToWindow(AndroidPopup.android.kt:721)
```

- **手柄是 Compose `Popup` 画的**：`foundation/text/selection/AndroidSelectionHandles_androidKt.HandlePopup`
  → `AndroidPopup_androidKt.Popup`（1.12.1 字节码核对）。所以只要浮层里有输入框，光标 / 选区手柄
  一露就会新建一个 `Popup` 窗口。
- **`PopupLayout` 的三个 owner 来自 `LocalView`**：构造里 `ViewTreeLifecycleOwner.get(composeView)`
  然后 `set` 到自己身上（`ViewModelStore` / `SavedStateRegistry` 同样三条），`composeView` 就是
  建这个 `Popup` 时的 `LocalView.current`。窗口 token 也从它身上取。
- **踩点**：弹窗内容外层那层触觉作用域（`ui/Haptics.kt` 的 `TickHapticScope`）把 `LocalView`
  顶替成 `View(context)`。那个 View 不在窗口里、也没有 owner tag ⇒ 复制到 `PopupLayout` 上的三个
  owner 全是 null ⇒ 挂窗口那一刻抛上面的异常，宿主进程直接死。
- **同一个坑的第二面**：光标挪到末尾是往 `LocalView` 派发 `KEYCODE_MOVE_END`。发给假 View 等于
  没发 —— 表现为「自动聚焦后光标停在开头」，而且**一行日志都没有**。判据只有一条：
  `LocalView.isAttachedToWindow == false` 时别派发，直接记一行 w。
- **处置**：弹窗那一层不再套触觉作用域（库会自己振的几处各自收口：开关在 `SwitchRow`、滑杆在
  `TrackSlider` 里关成 `None`、勾选框 `onClick = null`）；触觉作用域只留给浮层菜单那种
  「没有输入框、也不开新窗口」的内容。

## 四十四、Monet 的「系统取色」来自 framework 资源，不是 settings 里那个 `system_palette`（实测 + 资源表核对，2026-11-04，PLK110 / ColorOS 17.0.0）

排查「选了蓝色，实际用的是深蓝」时先踩了一次误判，值得记下来。

- **`theme_customization_overlay_packages` 里的 `system_palette` / `accent_color`（本机是 `ff0066ff`）
  不是 `platformDynamicColors` 的输入**。它只是 ROM 自己的主题配置；`platformDynamicColors`
  读的是 framework 的动态色资源（`system_accent1_100…900` / `system_neutral1|2`）。
- 本机 `cmd overlay list` 里**没有**任何 `com.android.theme.color.*` 覆盖，也就是这套资源是
  **framework 基线**：`system_accent1_500_light = #FF6476A5` 那一族（灰蓝），不是壁纸那个 #0066FF。
  所以「默认」档看到的是**深蓝/灰蓝**，与「蓝色」档的种子本来就不是同一个值。
- 真正的 bug 在预设色那一档：它把种子交给**系统那一档调色风格**（本机 `TONAL_SPOT`）重推，
  TonalSpot 把种子彩度压到 50 上下，`#0066FF` 出 tone 40 也只是灰蓝 —— 与「默认」档肉眼不分。
  处置：预设色固定走 `ThemePaletteStyle.Fidelity`（保留种子彩度），`colorSpec` 仍按 SDK 取。
- 判据：模块 W 日志「页面配色：主题=monet 强调色=… 深色=… 主色=#…」两档主色是否同值；
  以及 `aapt2 dump resources /system/framework/framework-res.apk` 里 `system_accent1_*` 的实际色值。
