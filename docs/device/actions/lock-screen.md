# 四十五、锁屏左下 / 右下那两颗快捷功能：相机与计算器各自的启动配方（反编译 SystemUI + 目标 APK 清单，2026-11-05，PLK110 / ColorOS 17.0.0）

锁屏两角的「快捷功能」在 SystemUI 里是 AOSP 的 **quick affordance**（`KeyguardQuickAffordanceConfig`
那一族），ColorOS 自己实现了两支：

- `com.oplus.systemui.keyguard.data.quickaffordance.OplusCameraQuickAffordanceConfig`
- `com.oplus.systemui.keyguard.data.quickaffordance.OplusCalculatorKeyguardQuickAffordanceConfig`

（`classes3.dex`；按字符串 `STILL_IMAGE_CAMERA_SECURE` / `oplus.intent.action.CALCULATOR` 反查命中点即可复现。）

## 45.1 相机

`onTriggered` 只是转发：`CameraGestureHelper.launchCamera(3)`（3 = `CAMERA_SOURCE_KEYGUARD`）。

```
CameraGestureHelper.getStartCameraIntent()
  ├─ keyguard 正在显示 && 是安全锁屏 && 不能直接划开 ⇒ cameraIntents.getSecureCameraIntent(userId)
  └─ 其余                                       ⇒ getInsecureCameraIntent(userId)

CameraIntents.getSecureCameraIntent(ctx, userId)
  = Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE")
      .addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)        // 0x00800000
      [+ setPackage(overlay 里的 override 包名)，本机没配 ⇒ 不设]

launchCamera(source)
  intent.putExtra("com.android.systemui.camera_launch_source", source)
  source == 3 ⇒ intent.putExtra("StartFromKeyguard", true)
```

- 收口方：`cmd package query-activities -a android.media.action.STILL_IMAGE_CAMERA_SECURE` ⇒
  **`com.oplus.camera/com.oplus.camera.Camera`**（`exported=true`；清单里
  `android:showWhenLocked="true"` ⇒ 锁屏下任何进程起它都显示在键盘锁之上）。
- 相机读那两个标记：`com.oplus.camera.entry.CameraEntry` 的 `C0` 判
  「`StartFromKeyguard` 为真，**或**带了 `camera_launch_source` 且当前是安全锁屏」⇒ 按「从锁屏进来」那一支收界面。
  少发这两个 extra 不会打不开，只是走成「普通启动」，锁屏上那一支界面会不一样。
- 模块侧只在**安全锁屏上**带这两个标记（`RomProfile.BuiltIn.keyguardExtras` + 执行侧现读
  `KeyguardManager`）：解锁状态下按同一颗键是一次普通启动，不该对相机说「我是从锁屏来的」。
- **熄屏那一档要自己点屏**：侧键在熄屏时照常被拦住（三十九），而 `startActivity` 不会点亮屏幕 ——
  界面在幕后起来了、用户看到的仍是黑屏。两档（相机 / 计算器）因此登记 `wakeScreen`，起界面前
  调一次 `PowerManager.wakeUp`（`@hide`，靠宿主的 `DEVICE_POWER`，与「电源键」那一档同一支）。
  录屏不点屏：按它不是为了看那一屏。

## 45.2 计算器

`onTriggered` 现拼 intent，而不是起某个固定组件：

```
Intent("oplus.intent.action.CALCULATOR")
  .putExtra("source", "from_control_center")
  // 解析不到时退成「显式组件 + 空 action」（getPackageName() 那个类）
keyguard 显示 && 安全锁屏 ⇒ setFlags(0x34808000)
                              NEW_TASK | SINGLE_TOP | CLEAR_TOP | CLEAR_TASK | EXCLUDE_FROM_RECENTS
                              putExtra("comeFrom", "mFromScreen")
其余                     ⇒ addFlags(0x34000000)  NEW_TASK | SINGLE_TOP | CLEAR_TOP
                              putExtra("comeFrom", "mFromDesktop")
ActivityOptions.makeCustomAnimation(…) + putInt("android:activity.mZoomLaunchFlags", 8)
  ├─ 锁屏可直接划开 ⇒ activityStarter.startActivity(intent, false)
  └─ 否则           ⇒ context.startActivityAsUser(intent, bundle, UserHandle.CURRENT)
```

- 收口方：`oplus.intent.action.CALCULATOR` ⇒ **`com.coloros.calculator/com.android.calculator2.Calculator`**
  （清单里**没有** `showWhenLocked`）。
- 锁屏下能显示的全部依据是它自己按 action 现判：`Calculator.f1` 只在 action 为
  `oplus.intent.action.CALCULATOR` / `coloros.intent.action.CALCULATOR` 时
  `setShowWhenLocked(true)`；`com.oplus.calculator.EVALUATE_EXPRESSION` 那一支反而显式置 false。
  也就是说**普通启动（`app:com.coloros.calculator`）在锁屏上会被键盘锁盖住**，只有这条 action 能直接出界面。
- `comeFrom` 不是装饰：`BaseActivity.onCreate` / `onRestart` / `startActivity` 都读它。
- 模块少抄了一位 `FLAG_ACTIVITY_CLEAR_TASK`：系统那颗只在锁屏上出现，按一次清一次任务没有代价；
  模块这颗键在解锁状态下也会被按到，带上它会把用户正在算的式子清掉。

## 45.3 复现用命令

```
adb pull /system_ext/priv-app/SystemUI/SystemUI.apk
python tools/dexdump.py <dex> uses "STILL_IMAGE_CAMERA_SECURE" "oplus.intent.action.CALCULATOR"
python tools/dexdump.py <dex> method com.oplus.systemui.keyguard.data.quickaffordance.OplusCalculatorKeyguardQuickAffordanceConfig onTriggered
aapt2 dump xmltree --file AndroidManifest.xml <目标 apk>   # showWhenLocked / intent-filter
```
