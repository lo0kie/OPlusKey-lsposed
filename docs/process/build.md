# 构建档位


| 档         | 落点允许猜 | 调试通道 | 详细日志 | 防篡改 | 签名 |
| ---------- | ---------- | -------- | -------- | ------ | ---- |
| debug      | 开         | 开       | 开       | 无     | 调试 |
| release    | 关         | 关       | 关       | 有     | 发布 |
| releaseLog | 关         | 关       | **开**   | 有     | 发布 |

`releaseLog` 是给用户排查用的「正式包 + 详细日志」：与 `release` 只差 `VERBOSE_LOG = true`
（`XLog.d` / `XLog.i` 重新进包，其余一样 —— `DEBUG_PROBE` / `STRICT_UI` 仍为 false）。产物名与
「应用信息」里的版本带 `-log` 后缀。它同样需要 `:core` 声明同名 buildType，否则变体匹配失败。

判据不是「运行时判断」而是**源集文件是否存在**：`core/src/{debug,release,releaseLog}/java/com/lookie/opluskey/core/BuildFlags.kt`
各一份同名常量，打包时只有一份进产物；`SealPin` 由 `:core` 的 `SealReleaseAssets` 为 `release` / `releaseLog` 生成
（`core/src/debug` 那份是 `SEAL_ENABLED=false` 的明文直通）。
复核：`ls core/src/{debug,release,releaseLog}/java/com/lookie/opluskey/core/BuildFlags.kt` 三份都在；
两档区分是否真的生效，`python tools/check_variants.py` 一次跑完（它按「该串只应出现在哪一档」逐项判定）。

- **两条 workflow，各管一段**：`.github/workflows/ci.yml`（推 master / PR）只做「构建 + 验证 +
  留产物」，里面没有任何发布步骤；`.github/workflows/release.yml`（打 tag）只做「取 + 验 + 发」。
    - `release.yml` 按**提交 SHA** 去 `ci.yml` 在 master 上那条 run 取产物（`gh run download`），
      本 job 里再用 `tools/verify-apk.sh` 把两个包解一遍才发 —— 同一份提交不构建两遍。
    - tag 常紧跟 master 推，两条 run 并发，所以取产物前先等：**那条 run 还在跑就一直等**（构建没跑完是常态，
      不该按分钟数掐断）；「没有这条 run」（给 60 秒宽限再判，刚推完 master 时 run 可能还没被创建出来）、
      那条 run 失败、或产物过保留期（release 包留 90 天）—— 一律**红**，不退回自己构建（构建只归 `ci.yml`）。
    - 查询带 `branch=master`：推 tag 本身也是 push 事件，不带它会把别的 ref 的 run 捞进来。
    - `release.yml` 的 `concurrency` 是 `cancel-in-progress: false`：发布半路被掐会留下
      「tag 推了、Release 没建」。
    - 版本号由 `tools/read-version.sh` 读（两条 workflow 共用一份，抓不到就 fail）；取完产物先判
      「必须有本版的 `*-release.apk`」—— 缺了就直接说「多半是 `signed=false`（Secrets 缺
      `KEYSTORE_*`）」，别等到建 Release 那一步才抛一句含糊的 `no files found`。
- 发到模块仓库的只有 `release`：`versionName` 带 `-` 的按预发布发，包体不变。没配签名密钥时只出
  debug —— 封存层依赖签名派生密钥，没有明文回退（这一档 `release.yml` 取不到 release 包，发不出去）。
