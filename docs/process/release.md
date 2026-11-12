# 发版：版本号与 tag


版本号只有两处手写，其余全由 CI 读出来：

| 位置                                                    | 写什么                          | 什么时候动                             |
| ------------------------------------------------------- | ------------------------------- | -------------------------------------- |
| `app/build.gradle.kts` 的 `versionCode` / `versionName` | 用户看到的版本 + 单调递增的整数 | 每次发版                               |
| `core/ModuleInfo.kt` 的 `BUILD_ID`                      | `yyyy-MM-dd-NNNN`               | 每次出包（它是「换了包没重启」的尺子） |

**tag 是 `<versionCode>-<versionName>`（如 `16-1.4.2`），不补前导零。** 两个都不是为了好看：

- 管理器要拿它算更新：`RepoLoader.updateLatestVersion()` 对模块仓库里那条 Release 的 tag 做
  `split("-", 2)`，第一段 `Long.parseLong` 当 **versionCode**、第二段当 **versionName**，再和手机上
  已装的比（`upgradable()` 要求**严格大于**）。切不出两段或第一段不是数字时，那一行直接 `continue`
  —— **模块能装，但管理器永远不会提示有新版本**。所以 `v1.0.0` / `1.0.0` 这种写法不要用。
- **不补前导零**：模块仓库的机器人会按 APK 二进制清单里的 versionCode 重算 tag 名。写 `00016-1.4.2`
  上去，它会把 Release 改成 `16-1.4.2`、`00016-1.4.2` 留成一条没有 Release 的孤儿 tag
  （`1.4.2` 上架时实测：release 挂在 `16-1.4.2`，`00016-1.4.2` 只剩一个 tag）。跟它写法一致最省事。
- **最新版本不看 tag 名字，看 Release 的创建时间**：管理器取的是模块仓库 Release 列表的第一条，
  那个列表按创建时间倒序 —— 刚上架的那条必然排第一。名字只在创建时间相同时当 tiebreak。
- **那个「创建时间」实际是 tag 所指 commit 的提交时间**（`created_at` 与 `git/commits` 的
  `committer.date` 逐秒相同）：`3-1.0.0` ~ `15-1.4.1` 这 12 个 tag 全指向模块仓库初始化时的同一个
  提交（`Update README.md`，10-13），于是创建时间并列、退化成按名字倒序 —— `9-1.3.1` 排最前，
  `14-1.4.0`、`15-1.4.1` 落到 `3-…` 后面。这就是「最新版停在 1.3.1、1.4.0 排在它后面」的成因，
  也是「补零能修排序」这个判断错的地方：补零改的是 tag 名字，改不动 commit 时间，还会被机器人打回。
- 上架时机器人会给这一版推一个独立提交（message 就是 tag 名，如 `16-1.4.2`），那条 Release 的
  创建时间才是真实的、才会排第一。机器人若退回「只换资产、不推提交」，tag 又会落在默认分支 HEAD
  （`d383532`，10-13 初始化后没再动过）上，时间并列、排序重新错乱。
  **每次上架后核一次**：`git/ref/tags/<tag>` 取 sha，再看 `git/commits/<sha>` 的 `committer.date`
  是不是当天 —— 是就没事，不是就说明机器人没建提交。
- 本仓库（存档）的 tag 同理只喂 `publish.yml` 的「留空取最新存档」，那条也按创建时间排。

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
- 前置条件：Secrets 里 `KEYSTORE_BASE64` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`（属 `ci.yml`，缺则只出 debug，
  `signed != true` 时 `release.yml` 的「确认拿到的是这一版的正式签名包」直接红）；
  `MODULES_REPO_TOKEN` 对 `Xposed-Modules-Repo/io.github.lo0kie.opluskey` 有 `contents: write`
  （属 `publish.yml`）；`GITEE_TOKEN`（Gitee 私人令牌，勾 `projects` 权限，属 `release.yml`
  最后一步，缺则「同步到 Gitee Release」整步跳过、只留一条 notice）。
- **`publish.yml` 的前置条件不满足是红的，不是跳过**：点「上架」就是要它推上去，
  令牌缺失或读不到模块仓库时那一步直接失败。跳过的话整条 run 绿着、模块仓库一条都没多 ——
  看起来上过架、实际没有，且事后只能靠翻那次 run 的步骤结论才发现。
- **推的 tag 必须与源码里的版本号对上**（`<versionCode>-<versionName>` 或 `v<versionName>`），
  对不上时 `校验 tag 与版本号一致` 这一步直接红。放宽的后果是「Release 挂在另一个名字上 ⇒
  发了新版而管理器不提示更新」，且流水线全绿、看日志看不出来。
- **`versionCode` 只增不减**，预发布也占号（不涨号测试通道收不到更新提示）；`versionName` 带 `-`
  （如 `1.1.0-beta.1`）会被自动标成 prerelease、不抢 latest。
- **tag 推上去就不再移动**：`git tag -f` + 强推会让历史版本指向漂移。打错了删远端 tag 重打
  （`git push origin :refs/tags/<tag>`），并把那两条 Release 一并处理。
- 当前基线（2026-11-12）：`versionName = 1.4.2`、`versionCode = 16`（见 `app/build.gradle.kts`）。
  历史：第一个进仓库的版本是 `1.0.0`（tag `3-1.0.0`），`versionCode` 取 3 —— 1 / 2 已被历史包
  （手动发的 `0.1.0`、本地出的 `0.2.0`）占用。此后每涨一次版本，两边各多一条 Release、一一对应。
