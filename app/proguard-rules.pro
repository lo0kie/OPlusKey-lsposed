# 模块入口类由 META-INF/xposed/java_init.list 声明，框架按名字实例化，必须保留。
-keep class com.lookie.opluskey.KeyModule { *; }

# 反射访问 system_server 的成员，保留自己的实现类与内部类名（便于排查）。
-keep class com.lookie.opluskey.** { *; }

# 故意不 keep `SourceFile` / `LineNumberTable`：正式版不带行号表与源文件名。
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-dontwarn kotlin.**
-dontwarn androidx.**
-dontwarn io.github.libxposed.**
