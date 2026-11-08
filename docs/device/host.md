# 十、宿主进程里能**安全**使用哪些系统组件（离线逆向，2026-10-11）

> 起因：设置页原先用 Compose /
> MD3 组件 + 从宿主主题取色 ⇒ 观感是「MD 与 COUI 混用」。要「用系统自己的组件样式」，先得把「宿主进程里到底能用什么」摸清。结论如下。

## 10.1 宿主 `com.oplus.gesture` 自带的 COUI（`reference/gesture.apk`）

`com/coui/appcompat` **733 个类**，另加 `com/coui/animatekit`（53）、`com/coui/component`（48）。与设置页相关的：

| 类别                | 类                                                                                                                                                                                                                                                                                            |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 开关                | `com.coui.appcompat.couiswitch.COUISwitch`                                                                                                                                                                                                                                                    |
| 勾选/单选           | `com.coui.appcompat.checkbox.COUICheckBox`                                                                                                                                                                                                                                                    |
| 按钮                | `com.coui.appcompat.button.COUIButton` / `COUILoadingButton`                                                                                                                                                                                                                                  |
| 列表                | `com.coui.appcompat.list.COUIListView`、`cardlist.*`、`input.*`                                                                                                                                                                                                                               |
| 弹窗                | `com.coui.appcompat.dialog.*`（`COUIDialogTitle` / `COUIAlertDialogMessageView` / `COUIAlertDialogOverlayHelper` …）                                                                                                                                                                          |
| **Preference 全套** | `com.coui.appcompat.preference.*`：`COUIMarkPreference`、`COUISwitchPreference`、`COUIJumpPreference`、`COUIInputPreference`、`COUIListPreference`、`COUICheckBoxPreference`、`COUISlideSelectPreference`、`COUIStepperPreference`、`COUIStatusSwitchingPreference`、`COUIBottomPreference` … |

**但这些类的方法名全被 R8 混淆**（实测 `COUISwitchPreference` 的成员名是 `b0` / `c0` / `d0` …）⇒
**不能 new 它们、也不能调它们的 API**（一改 ROM 就碎）。

**可以安全用的是它们的「资源名」**（字符串稳定，按名字查 id 即可）：

```
style/COUISwitchStyle           0x7f12027c   （另有 .Light / .Dark 两个变体）
style/Preference.COUI.SwitchPreference              0x7f1202d9
style/Preference.COUI.COUISwitchWithDividerPreference
attr/couiSwitchStyle / couiMarkPreferenceStyle / couiJumpPreferenceStyle
attr/couiInputPreferenceStyle / couiStepperPreferenceStyle / couiSlideSelectPreferenceStyle
layout/coui_preference / coui_preference_listview / coui_mark_with_divider_preference
layout/coui_select_dialog_singlechoice（对话框版单选行，见 `settings-page.md` 8.6）
style/COUIAlertDialog 系列（COUIAlertDialog.Bottom / .Center / .List / .SingleEdit …）
```

## 10.2 分段控件（手势 tab）宿主里**没有**

- 宿主 APK 里 `COUISegmentButton*` / `SegmentButtonLayout` / `COUITabLayout` 命中数 **0**，样式 `style/SegmentButton`
  也不存在（只有 `dimen/segment_button_height` 等几个尺寸）。
- 它只在 **Settings.apk** 里： `com.coui.appcompat.segmentbutton.COUISegmentButtonLayout` （伴生
  `ISegmentButtonLayout`、`OnSegmentSelectedListener`、`OnSelectedSegmentChangeListener`、
  `$SegmentButtonDrawDelegate`、`$SmoothRoundCornerHelper` …）。
- **跨 APK 用不了**：类的资源 id 属于 Settings.apk 的资源表，用宿主的 Context 建出来必然取到错资源（而且 dex 也不是宿主 classloader 的）⇒ 只能**照规格手搭**，规格见 `settings-page.md` 8.6（轨道 40dp
  / 内缩 4dp / 滑块 32dp / 圆角胶囊 / 轨道与滑块那两支配色 / 动效 extra_width 7dp、over_distance 4dp、switch_distance
  5dp、阴影 1dp+2dp）。

## 10.3 结论：安全的做法

1. **控件用框架的**（`android.widget.Switch` / `CheckBox` / `RadioButton` / `TextView` / `ImageView` / `LinearLayout`
   …）—— 它们的 API 由平台保证，**永不会被混淆**；
2. **外观用宿主的 COUI 资源名**套上去：例如 `android.widget.Switch(ctx, null, 0, R.style.COUISwitchStyle)`（style id 用
   `resources.getIdentifier("COUISwitchStyle", "style", "com.oplus.gesture")` 取）、列表行照
   `coui_select_dialog_singlechoice` 的结构与尺寸、卡片/分隔线用宿主 drawable、顶栏沿用宿主 `COUIToolbar`、弹窗按
   `COUIAlertDialog.*` 的规格搭；
3. **不要**
   new 宿主的 COUI 类（方法名混淆）、**不要**从别的 APK 加载类（资源表不匹配）。—— 这两条是「安全」的边界：越界了就会在 ROM 更新时碎，或者直接取错资源。

## 10.4 「无障碍」页那个 tab 条 = COUI 的**大号文字 tab**（实测）

```
com.android.settings:id/tabs            HorizontalScrollView     y 323..491（168px = 48dp）
  └ LinearLayout（内容宽 1096px = 313dp，左侧留 25dp）
      ├ androidx.appcompat.app.ActionBar$Tab  ×4（通用 / 视觉 / 听觉 / 互动，每格 78dp）
      └ 选中格底部一条指示线：y 484..490 ≈ 2dp
```

取色（暗色，PLK110 1272×2772 / 560dpi）：

| 元素            | 实测                                          |
| --------------- | --------------------------------------------- |
| 选中 tab 文字   | `#e6e6e6`（= `couiColorLabelPrimary` 叠黑）   |
| 未选中 tab 文字 | `#8a8a8a`（= `couiColorLabelSecondary` 叠黑） |
| 指示线          | `#e6e6e6`，高 ≈2dp，画在 tab 底部             |
| 卡片底 / 页面底 | `#1a1a1a` / `#000000`（与本模块页面一致）     |

它是 **AppCompat 的 ActionBar tab**（不是 `COUIChipTabGroup` 那个胶囊分段控件），而 AppCompat /
COUI 的类都**不在宿主里能安全调用**（方法名混淆，见 10.1）。

**但宿主有这套 tab 的全部「资源」**，可以照资源名安全手搭：

```
style/COUILargestTabLayoutStyle      0x7f1201bb  parent=COUITabLayoutBaseStyle
style/COUITabLayoutBaseStyle         0x7f12027f
color/coui_tab_default_color         0x7f06090c  （selector：选中 couiColorPrimaryNeutral / 常态 couiColorSecondNeutral）
color/coui_tab_background_dark       0x7f06090b  #ff000000
dimen/tablayout_large_layout_height  0x7f070990  40dp
dimen/tablayout_large_text_size      0x7f070993  16（sp）
dimen/coui_tab_layout_* / coui_tab_* （各档边距、文字高、指示线高）
```

⇒ 手势 tab 若要换成这套「文字 + 底部指示线」，做法与 10.3 一致： **框架 `TextView`
×N + 一条 2dp 指示线 View**，文字色直接用 `color/coui_tab_default_color` 这个 selector（`getIdentifier` 取 id，套成
`ColorStateList`），尺寸取上表那几个 dimen —— 全程不碰任何混淆类，也不会跨 APK。（胶囊分段控件 `COUIChipTabGroup`
只在 Settings.apk，宿主没有，见 10.2。）

## 10.5 「设置 → 隐私与安全」那个分段控件：逐像素实测（2026-10-11）

> `com.oplus.securitypermission/...PrivacySecuritySettingsDashboardActivity`（暗色 / 1272×2772 / 560dpi）：

```
android.widget.Button  隐私   [70,358][636,470]      ← 两段各 566px = 162dp
android.widget.Button  安全   [636,358][1202,470]
轨道（胶囊）          x 64..1207（1144px = 327dp） y 344..483（140px = 40dp）
滑块（选中段）        x 82..623  y 358..469（112px = 32dp）
```

| 元素       | 实测值                 | 换算                                                              |
| ---------- | ---------------------- | ----------------------------------------------------------------- |
| 轨道底     | `#353535`（53,53,53）  | **20% 白叠页面底**（`#33ffffff` 叠 `#000000`）                    |
| 滑块底     | `#5d5d5d`（93,93,93）  | **20% 白叠「轨道」**（`#33ffffff` 叠 `#353535`）← 不是叠页面底    |
| 选中文字   | ≈`#efefef`（峰值 239） | `#e6ffffff`（90% 白）叠滑块底 = 239                               |
| 未选中文字 | ≈`#ebebeb`（峰值 235） | 与选中**同一个字色**（`couiColorLabelPrimary`），选中只靠滑块区分 |

**修正 8.6 里的两处数值**：那份写的是「轨道 深 `#1fffffff` / 滑块 深 `#33ffffff`」——实测轨道是
**20% 白**（不是 12%），且**滑块叠在轨道上**（不是叠页面底）。按旧值做出来的控件两级都偏暗，跟系统那个不像（本模块已按实测值改，见
`Md3eSettingsPage` 里 `trackColor` / `thumbColor` 的合成）。浅色下沿用 8.6 记的实测值（轨道 `#e2e3e4` = 6% 黑叠页、滑块
`#f6f6f6` = 70% 白叠轨道）。

差 2 个色阶（四舍五入），肉眼一致。

```
COUI 探测：com.coui.appcompat.couiswitch.COUISwitch 造出来了，平台 API 可用（setChecked → isChecked=true）
COUI 探测：com.coui.appcompat.checkbox.COUICheckBox 造出来了
COUI 探测：com.coui.appcompat.button.COUIButton 造出来了
```

1. **类名没被混淆** —— 它们被宿主自己的 XML 布局按名字引用，R8 必须保留类名（`COUISwitch` / `COUICheckBox` /
   `COUIButton` / `COUIListView` / `com.coui.appcompat.dialog.*` 都是原名）。
2. **构造器是标准的 View 构造器** —— 一律 `(Context, AttributeSet[, int])` **没有单参 `(Context)`**（只试单参会得到
   `NoSuchMethodException`，那是探针写错，不是控件不可用）。
3. **平台那部分方法名不会被改** —— 它们 override 的是 `android.view.View` / `TextView` / `CompoundButton`
   的方法，R8 改不了：`setText` / `setOnClickListener` / `setChecked` / `isChecked` / `setEnabled` / `performClick` /
   `setVisibility` … 全都在
4. 监听器也用**平台接口**：`View.OnClickListener` / `CompoundButton.OnCheckedChangeListener` —— 都是平台类，签名稳定。

**哪些不能用**：

- **AndroidX Preference 那套全部不可用**：`androidx/preference/Preference` / `SwitchPreference` / `PreferenceScreen`
  以及 `com.coui.appcompat.preference.*` （`COUIMarkPreference` / `COUISwitchPreference`）**方法名全被 R8 改成
  `A`/`A0`/`B`/`B0` …** ⇒ 没法 `setTitle` / `setSummary` / 挂监听；宿主里连 `PreferenceFragmentCompat`
  都找不到（按名）。 `COUICheckBox.setOnStateChangeListener` / `setState` / `getState`； `COUIButton.getSolidColor` /
  `getStrokeWidth` / `getButtonTypeFromStyle` / `getCornerParams`
  …）⇒ 设计上**只依赖平台 API**，COUI 专有方法当彩蛋，不当依赖。

⇒ 所以「完完全全用系统的组件」是可行的：**开关用 `COUISwitch`、单选/勾选行用 `COUICheckBox`、按钮/分段用
`COUIButton`、列表用
`COUIListView`**（都反射造 + 平台 API 驱动），只有**分段控件的胶囊底**宿主没有对应类（见 10.2），仍按 10.5 的实测规格手搭。

## 10.7 第二批能力边界：滑块、对话框、列表项尺寸（离线核对 `reference/gesture.apk`，2026-10-11）

> 结论先放这里：**滑块可以反射造真的 COUI 控件；对话框的类拿不到、但它的样式与资源名全在**。

**① 滑块（SeekBar）—— 可以反射造**

宿主里这两支的**类名没被混淆**（被 XML 按名引用 ⇒ R8 必须保留），构造器是标准 View 构造器：

| 类名                                                  | 说明                            |
| ----------------------------------------------------- | ------------------------------- |
| `com.coui.appcompat.seekbar.core.COUISeekBarAdvanced` | 首选（带 thumb 缩放等那套动效） |
| `com.coui.appcompat.seekbar.COUISectionSeekBar`       | 备选（分段滑块）                |

- 主题里的样式 attr：**`couiSeekBarStyle`**（还有 `couiSectionSeekBarStyle` / `couiIntentSeekBarStyle`）。
- 三参构造器 `(Context, AttributeSet, int)`
  的第三个参数要传**这个 attr 的 id**，传 0 等于「没样式」⇒ 造出来是裸控件（外观就不对了）。
- 尺寸/颜色资源名一大把（按需取）：`coui_seekbar_view_min_height`、
  `coui_seekbar_progress_padding_horizontal`、`coui_seekbar_progress_height`、
  `coui_seekbar_background_height`、`coui_seekbar_thumb_radius`、
  `coui_seekbar_progress_color_normal`、`coui_seekbar_background_color_normal`。

**② 对话框（AlertDialog）—— 类拿不到，样式与资源名全在**

- **`COUIAlertDialog` 那一族被 R8 混淆了**：`com/coui/appcompat/dialog/a`（含
  `a$a`~`a$f`）。它没有被 XML 引用（dialog 都是代码创建），所以类名保不住 ⇒
  **不能反射使用**。幸存的只有零件类名：`COUIAlertDialogRootLayout` / `COUIDialogTitle` / `COUIAlertDialogMessageView` /
  `COUIAlertDialogOverlayHelper`（当线索可以，当依赖太脆）。
- 但**属性和资源名都在**，于是可以「`Dialog` + 宿主对话框主题 + 按资源名自绘内容」：
- 主题 attr：**`couiAlertDialogTheme`**（还有 `couiAlertDialogStyle`）—— `theme.obtainStyledAttributes → getResourceId`
  取到对话框该用的主题，传给 `Dialog(ctx, id)`。
- 背景/圆角：`drawable/coui_alert_dialog_background`、`dimen/coui_alert_dialog_bg_radius`、
  `dimen/coui_alert_dialog_bg_padding_left`（窗口到屏幕边的距离）。
- 字色：`color/coui_alert_dialog_title_text_color`、 `color/coui_alert_dialog_content_text_color`（各有 `_dark`/`_light`
  变体）。
- 按钮：`dimen/coui_alert_dialog_button_height`、 `dimen/coui_alert_dialog_button_horizontal_padding`、
  `color/coui_alert_dialog_button_color_pressed`；COUI 的按钮是**左右均分的文字按钮**（无底色 + 波纹），强调色用
  `couiColorPrimary`。

**③ 列表项尺寸 —— 之前的 48dp 是量错了对象**

| dimen 名                                                           | 用途                                                                                     |
| ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `coui_list_item_normal_height` / `coui_list_item_preferred_height` | **列表项**行高（首选口径）                                                               |
| `coui_list_item_left_padding` / `_right_padding`                   | 列表项左右内边距                                                                         |
| `coui_delete_alert_dialog_button_height`                           | 这是**对话框按钮**的高（48dp），**不是列表行高** —— 之前拿它当行高，页面偏挤就是这里来的 |

⇒ 一般规律：尺寸**运行时按名字取**（`res.getIdentifier(name, "dimen", "com.oplus.gesture")`），不要把我离线量到的某个数写死进代码 —— 换个字号档就偏了。

不是猜的。涉及的文件：`inject/KeyHooks.kt`（system_server 侧）、`host/action/ActionRunner.kt`（宿主侧）。

## 10.8 宿主的包可见性：声明了 `QUERY_ALL_PACKAGES`（离线核对 `reference/gesture.apk`，2026-10-13）

要列**系统应用**（`ui/screen/AppPicker.kt`
里「显示系统应用」那一项）先得回答「看得见哪些包」，答案就在宿主自己的清单里：

```bash
aapt2 dump xmltree --file AndroidManifest.xml reference/gesture.apk | grep -A1 QUERY_ALL_PACKAGES
#   E: uses-permission (line=29)
#     android:name="android.permission.QUERY_ALL_PACKAGES"
```

- 我们的界面代码跑在**宿主进程**里 ⇒ 包可见性**按宿主算**：`getInstalledApplications(0)`
  拿得到全量（含系统应用），`getLaunchIntentForPackage` / `getApplicationInfo` 对任意包都通。
- ⇒ **模块自己的清单不要加这条权限**：权限按 uid 判，而页面代码的 uid 是宿主（同 `AndroidManifest.xml` 里 `INTERNET`
  那条注释的道理）。
- ⚠️ 这条推翻了一处旧文案：「选择应用」页的空态写着「系统限制了本页可见的应用列表」——那个前提不成立（真出现空列表只会是别的原因）。（2026-10-14 该空态已按此改成「没有找到可以打开的应用」，见
  `docs/changelog/2026-10-14-结构重构.md`。）
- ⚠️ 另一笔：`getLaunchIntentForPackage` 认的入口是 `CATEGORY_INFO` /
  `CATEGORY_LAUNCHER`，对**没有这两个入口**的系统应用一律返回 null
  —— 拿它当「能不能列出来」的判据，会把追加集合筛成空集（2026-10-13 踩过：开关打开了，列表毫无变化）。要「只列拉得起来的」才用它，要「列出装着的系统应用」就别用它。
