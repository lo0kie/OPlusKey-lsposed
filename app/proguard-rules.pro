# 模块入口类由 META-INF/xposed/java_init.list 声明，框架按名字实例化，必须保留。
# 本模块自己的类：**改名 + 收缩都放开** —— 本文件不给自家类写 `-keepnames`：
# 那种写法（`-keep,allowshrinking`）只钉名字、允许收缩，等于 release 只剪枝、**一个符号都不改**。
#
# 敢放开的前提：本项目**没有任何一处按名字找自家类** —— 全仓唯一的 `Class.forName`
#  在 `core/reflect/Reflect.kt`（`findClass`，查的全是宿主 / 框架的类）；`inject/Framework.kt`
#  的候选表不自己写，走的就是 `Reflect.findClass`。真正要被框架按名字取到的只有入口类
#  （上面那条 `-keep`）与资源 id 类（下面两条）。
#  放开之后 `FileObfuscation` / `scramble` 这类名字在 dex 里就捞不到了 —— 配置外皮的
#  「搜一眼就会」这个口子跟着收掉。
#
# 排查代价：release 的堆栈里只剩 `a.b.c`。判据级日志（`XLog.w` 的字面量）不受影响、照旧可读；
#  要还原堆栈得留 `app/build/outputs/mapping/release/mapping.txt`（`retrace` 用）。
#  debug 包不混淆（`isMinifyEnabled` 只在 release 开），日常排查用 debug 包。
#
# R8 默认就把**所有**类重打包进默认包（无包名，`mapping.txt` 里形如 `FileObfuscation -> t50:`），
#  所以 `-repackageclasses ''` 是句废话；真要关掉得写 `-keeppackagenames`，这里**不关** ——
#  LSPosed 的 `LspModuleClassLoader` 是 `findClass` 先于 `parent.loadClass`，模块自己的类永远取自己那份，
#  宿主那边看不到模块的类，双向都不串。默认包撞名面实测量：`framework.jar` / `oplus-framework.jar`
#  0 个类、`services.jar` 7 个、`OplusGestureUI.apk` 4 个（`a`~`d`），与本包重名的只有后者那 4 个。
-keep class com.lookie.opluskey.KeyModule { *; }

# 资源 id 类：`build_id` 是**按名字**取的（`ui/screen/HomeScreen.kt` 的 `appVersionLabel`
# 走 `Resources.getIdentifier("build_id", "string", MODULE_PACKAGE)` —— 不能写
# `R.string.build_id`，页面跑在宿主进程里，`R` 会解析到宿主自己的资源表）。
#  名字一旦被 R8 收缩掉、或那份 string 被 `shrinkResources` 当成「没人引用」删掉，
#  界面底部那一行版本号就空了 —— 所以 `R` / `R$*` 单独钉死（与 `res/raw/keep.xml` 配套）。
-keep class com.lookie.opluskey.R { *; }
-keep class com.lookie.opluskey.R$* { *; }

# 反射访问 system_server 的成员，保留注解 / 签名 / 内部类信息（便于排查）。
# 故意不 keep `SourceFile` / `LineNumberTable`：正式版不带行号表与源文件名。
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-dontwarn kotlin.**
-dontwarn androidx.**
-dontwarn io.github.libxposed.**
