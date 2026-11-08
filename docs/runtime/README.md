# 模块运行时原理

模块**自己**怎么跑。ROM 那边是什么样在 `../device/`。

| 文件 | 收什么 |
| --- | --- |
| `key-to-gesture.md` | 接管方式与两级开关、按键怎么变成手势、执行时提示、为什么不提供「交还系统」 |
| `config-channel.md` | 配置从哪来、怎么到注入侧、注入侧引用红线、降级设计 |
| `debug-channel.md` | 注入侧反射通道：三条安全边界、七个 op、用法 |
| `adaptation.md` | 适配清单与多版本档位表 |
| `app-control.md` | 「应用控制」那一格与 `proc:stop:` |
| `ui-decisions.md` | 界面取舍（命令输入框的格式前缀等） |
| `known-limits.md` | 已知限制 |
