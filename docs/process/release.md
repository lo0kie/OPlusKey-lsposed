# 发版：版本号与 tag


版本号只有两处手写，其余全由 CI 读出来：

| 位置                                                    | 写什么                          | 什么时候动                             |
| ------------------------------------------------------- | ------------------------------- | -------------------------------------- |
| `app/build.gradle.kts` 的 `versionCode` / `versionName` | 用户看到的版本 + 单调递增的整数 | 每次发版                               |
| `core/ModuleInfo.kt` 的 `BUILD_ID`                      | `yyyy-MM-dd-NNNN`               | 每次出包（它是「换了包没重启」的尺子） |

**tag 必须是 `<versionCode>-<versionName>`（如 `3-1.0.0`）。** 不是为了好看，是因为管理器要拿它算更新：

`RepoLoader.updateLatestVersion()` 对模块仓库里那条 Release 的 tag 做 `split("-", 2)`，
第一段 `Long.parseLong` 当 **versionCode**、第二段当 **versionName**，再和手机上已装的比
（`upgradable()` 要求**严格大于**）。切不出两段或第一段不是数字时，那一行直接 `continue` ——
**模块能装，但管理器永远不会提示有新版本**。所以 `v1.0.0` / `1.0.0` 这种写法不要用。

- **更新公告是手写的一份文件**：`docs/release-notes/<versionName>.md`（如 `docs/release-notes/1.3.4.md`）。
  钉版（`release.yml`）与上架（`publish.yml`）都只读它，再各自接一段版本行与包清单 ——
  管理器弹「有新版本」时显示的正文就是这段字，本仓库存档那条也用同一份，两处不会有两种说法。
  缺这个文件两条流水线都直接红：公告是发版物的一部分，缺了弹窗里就是一片空白。
  不从提交里拼的理由：提交信息是写给维护者的（「收口」「对齐」），弹窗里要写给用户。
- **推 tag 只「钉版」，不推模块仓库**（`release.yml`，卡 `refs/tags/`）。推上去之前确认
  版本号已经提交：Release 的 `tag_name` 是 CI 从版本号算出来的，与手推的 tag 名字不一致就会分叉。
- **钉版与上架是两条流水线、两个时刻**：
    - `release.yml`（推 tag 触发）只把包存进**本仓库**的 Release —— **历史存档**，两个包都放
      （`release` + `debug` + `releaseLog`），tag 用**这次推上来的那个**（`github.ref_name`），
      不拿版本号重算：否则推 `v1.0.0` 时本仓库会凭空多出一条 `4-1.1.0` 指着同一个提交。
    - `publish.yml`（**手动触发**，填 tag）才把那一版推进
      `Xposed-Modules-Repo/io.github.lo0kie.opluskey` —— **更新通道**，管理器只读它，只放
      `release` + `releaseLog`。包从存档那条 Release 下，**不重新构建**：上架的就是当初钉住的那一份。
    - `release.yml` 存完再**同步一份到 Gitee**（`look1e/opluskey-lsposed`，国内镜像）—— 同一个 tag、
      同一批包，走 `tools/gitee-release.sh`。它**不进更新通道**：管理器不读它，Gitee 那份只是第二个
      下载点。同一个 tag 重跑只更新正文、已传过的附件跳过。
    - 为什么分开：管理器一旦读到模块仓库那条 Release，所有装过的人立刻收到更新提示 —— 那不该跟
      「我打了个版本」绑在同一刻发生。钉完版继续写新功能，择日再上架。
- 前置条件，缺了只是**静默跳过**、日志里不报错：Secrets 里 `KEYSTORE_BASE64` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`（属 `ci.yml`，缺则只出 debug，
  `signed != true` 时 `release.yml` 的「确认拿到的是这一版的正式签名包」直接红）；
  `MODULES_REPO_TOKEN` 对 `Xposed-Modules-Repo/io.github.lo0kie.opluskey` 有 `contents: write`
  （属 `publish.yml`，缺则「检查模块仓库的前置条件」跳过，只发存档）；
  `GITEE_TOKEN`（Gitee 私人令牌，勾 `projects` 权限，属 `release.yml` 最后一步，缺则
  「同步到 Gitee Release」整步跳过、只留一条 notice）。
- **推的 tag 必须与源码里的版本号对上**（`<versionCode>-<versionName>` 或 `v<versionName>`），
  对不上时 `校验 tag 与版本号一致` 这一步直接红。放宽的后果是「Release 挂在另一个名字上 ⇒
  发了新版而管理器不提示更新」，且流水线全绿、看日志看不出来。
- **`versionCode` 只增不减**，预发布也占号（不涨号测试通道收不到更新提示）；`versionName` 带 `-`
  （如 `1.1.0-beta.1`）会被自动标成 prerelease、不抢 latest。
- **tag 推上去就不再移动**：`git tag -f` + 强推会让历史版本指向漂移。打错了删远端 tag 重打
  （`git push origin :refs/tags/<tag>`），并把那两条 Release 一并处理。
- 当前基线（2026-11-02）：`versionName = 1.3.3`、`versionCode = 11`（见 `app/build.gradle.kts`）。
  历史：第一个进仓库的版本是 `1.0.0`（tag `3-1.0.0`），`versionCode` 取 3 —— 1 / 2 已被历史包
  （手动发的 `0.1.0`、本地出的 `0.2.0`）占用。此后每涨一次版本，两边各多一条 Release、一一对应。
