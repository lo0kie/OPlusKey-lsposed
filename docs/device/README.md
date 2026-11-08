# 目标 ROM 的事实

> 系统镜像里读出来的。实现必须对齐这里；与代码冲突时以这里为准，并回头修代码。

> ⚠️ **这批文件是按时间累积的实测记录。** 2026-10-14 做过一次包结构重构与配置位置迁移，早于该日的章节里包名（`hook/` `page/`
> `gesture/` `config/`）与配置路径（CE）按**当时**的写法保留 —— 今天的对应关系见 `../architecture/structure.md`
> 的映射表，配置位置见 `storage.md` 9.9；指向**现存文件**的引用已更新为新路径。

## 文件

| 文件 | 收什么 |
| --- | --- |
| `platform.md` | 目标机型与系统版本 |
| `side-key.md` | 侧键接管点、框架与宿主的分工、手势判定、权威调用链、放行与自持循环 |
| `settings-page.md` | 换页劫持进「设置 → 快捷键」：类结构、布局落点、两个坑 |
| `key-codes.md` | 侧键与压感拍照键的键码族、中间态码的处置 |
| `lsposed.md` | LSPosed 作用域、重装不加载、注入侧能碰什么、API 的坑 |
| `host.md` | 宿主进程能用哪些系统组件 |
| `coui.md` | COUI 组件库的实测行为与观感 |
| `storage.md` | 配置存在哪、CE / DE 的区别、改配置外皮的前提 |
| `actions/` | 各类动作的系统配方 |

## 节号索引

节号沿用拆分前的编号（`AGENTS.md` 与代码注释按它引用），**不连续是正常的**：

| 节 | 文件 |
| --- | --- |
| 一 | `platform.md` |
| 二、四、五、七、十四、十六、二十五、三十九 | `side-key.md` |
| 三、二十一 | `storage.md` |
| 六之一~六之七、十五、四十一 | `lsposed.md` |
| 六之八 | `actions/toggles.md` |
| 八（8.1~8.4、8.8） | `settings-page.md` |
| 8.10~8.17、十八、二十、二十六、四十三、四十四 | `coui.md` |
| 9.1~9.7.1 | `side-key.md` |
| 9.8、9.9 | `storage.md` |
| 十（10.1~10.8） | `host.md` |
| 11.1、11.2、11.4 | `lsposed.md` |
| 12.2~12.6 | `actions/audio.md` |
| 13.1~13.6 | `actions/launch.md` |
| 十七、17.2~17.5 | `actions/system.md` |
| 17.6、附二~附十三 | `../features/zoom-window/api.md` |
| 附十三之二~之五 | `../features/zoom-window/api-extra.md` |
| 17.7、三十、三十五、三十六 | `actions/capture.md` |
| 十九、四十 | `actions/feedback.md` |
| 二十二、二十九、四十七 | `actions/launch.md` |
| 二十三、二十四、三十四 | `actions/toggles.md` |
| 二十七、三十三 | `actions/audio.md` |
| 二十八、三十一、四十二 | `key-codes.md` |
| 三十二、三十七、四十六 | `actions/voice.md` |
| 三十八 | `actions/brightness.md` |
| 四十五 | `actions/lock-screen.md` |
