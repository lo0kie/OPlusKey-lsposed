#!/usr/bin/env python3
"""`patches/repo` 里那几份**被 CI 直接解析**的二进制 / 元数据的完整性校验。

为什么需要：这一个 `coui-ui-android` 的 aar 是打过补丁再重新发布的（见
`patches/coui/build-patched.sh`），它不在任何上游仓库里，也没有上游签名可对 —— 仓库里那一份
就是唯一来源。没有校验和时，被换掉 / 传坏都**看不出来**，而它最终会进用户的包。

判据：每个 `.aar` / `.pom` / `.module` 旁边必须有一份 `.sha256`，且内容与文件一致。
  `build-patched.sh` 在版本改写之后会重新生成它们；这里只负责核对。

用法（仓库根）：`python3 tools/verify-patches.py`
"""

import hashlib

import _common as c

REPO = c.ROOT / 'patches' / 'repo'
SUFFIXES = ('.aar', '.pom', '.module')


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    checks = c.Checks('补丁产物校验')
    count = 0

    for path in sorted(REPO.rglob('*')):
        if not path.is_file() or path.suffix not in SUFFIXES:
            continue
        count += 1
        rel = path.relative_to(c.ROOT)
        sumfile = path.with_name(path.name + '.sha256')

        if not sumfile.is_file():
            checks.bad(f'{rel} 没有 .sha256（补丁产物必须带校验和）')
            continue

        # 只比第一段（`sha256sum` 的格式是「哈希 + 两个空格 + 文件名」）
        want = sumfile.read_text(encoding='utf-8').split(' ')[0].strip()
        got = sha256_of(path)
        if want != got:
            checks.bad(f'{rel} 的校验和对不上（记录 {want} / 实际 {got}）—— 文件被改过或没重新生成')

    if count == 0:
        # 这一条是「闸在空转」的守卫：目录被改名 / 产物被挪走时 rglob 静默给空，照样打「通过」。
        checks.bad('patches/repo 里没有产物 —— 这条校验会失效')

    return checks.finish(f'patches/repo：{count} 份产物的校验和全部一致')


if __name__ == '__main__':
    c.main(main)
