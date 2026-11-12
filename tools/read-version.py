#!/usr/bin/env python3
"""从源码里读出三个版本值（`ci.yml` / `publish.yml` / `release.yml` 共用这一份）。

为什么必须是字面量：`versionCode = base + 1` 这类表达式正则抓不到。原先的兜底
  （unknown / 0.0.0 / 0）会让 job 照常跑完，再发出一个 tag 叫 `0-0.0.0` 的 release；
  而模块仓库的机器人是从 APK 的二进制清单读真值算 tag 的，两边不一致它就再改一次 tag，
  仓库里凭空多一个 tag，且没人会注意到版本号根本没进包。所以抓不到就 **fail**。

用法（仓库根）：`python3 tools/read-version.py`
  - 设了 `$GITHUB_OUTPUT` 时，把 build_id / version_name / version_code / tag 追加进去；
  - 无论如何都往 stdout 打一行人读的摘要。
"""

import re

import _common as c

MODULE_INFO = 'core/src/main/java/com/lookie/opluskey/core/ModuleInfo.kt'
BUILD_GRADLE = 'app/build.gradle.kts'


def first_match(path, pattern):
    """先剔掉**整行**注释再取第一条匹配。

    这两个文件里都留着「示例：versionName = "1.0.0"」那种注释，只取第一条匹配就会抓到注释里
    的旧值 —— 而 version_name 直接决定发版的 tag。

    与原来的 `sed` 同一口径：**第一条命中的行**，行内取**最后**一次出现（sed 的 `.*` 是贪婪的）。
    """
    regex = re.compile(pattern)
    for line in (c.ROOT / path).read_text(encoding='utf-8').splitlines():
        if line.lstrip().startswith('//'):
            continue
        found = regex.findall(line)
        if found:
            return found[-1]
    return ''


def main():
    build_id = first_match(MODULE_INFO, r'BUILD_ID\s*=\s*"([^"]*)"')
    version_name = first_match(BUILD_GRADLE, r'versionName\s*=\s*"([^"]*)"')
    version_code = first_match(BUILD_GRADLE, r'versionCode\s*=\s*(\d+)')

    missing = []
    if not build_id:
        missing.append(f'BUILD_ID（{MODULE_INFO}）')
    if not version_name:
        missing.append(f'versionName（{BUILD_GRADLE}）')
    if not version_code:
        missing.append(f'versionCode（{BUILD_GRADLE}）')
    if missing:
        c.err('抓不到 ' + '、'.join(missing) + '；不要把空的版本值发出去（会建成 00000-0.0.0 的 tag）')
        return 1

    # tag 直接用 `<versionCode>-<versionName>`，**不补前导零**：模块仓库的机器人会按 APK 二进制
    #   清单里的 versionCode 重算 tag 名，补零写的 `00016-1.4.2` 会被它改成 `16-1.4.2`、Release
    #   一并挪过去，旧名字留成孤儿 tag。与它写法一致，才不会每次上架都多一个垃圾 tag。
    #   管理器拿它算更新时是 `split("-", 2)` + `Long.parseLong`，两种写法都认。
    tag = f'{version_code}-{version_name}'

    c.set_output({
        'build_id': build_id,
        'version_name': version_name,
        'version_code': version_code,
        'tag': tag,
    })
    print(f'版本 {version_name}（{version_code}），tag {tag}，构建 {build_id}')
    return 0


if __name__ == '__main__':
    c.main(main)
