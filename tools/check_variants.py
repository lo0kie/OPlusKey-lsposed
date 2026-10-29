"""三档 APK 的区分度检查：每个判别项都必须只在它该出现的档里出现。"""

import os
import re
import subprocess
import sys

OUT = 'app/build/outputs/apk'
# 三档都要查：`releaseLog` 与 release 同签名、同封存，只把 VERBOSE_LOG 打开 ——
#   漏掉它，那两档之间配反了（比如 releaseLog 里带上 DEBUG_PROBE）没人拦。
VARIANTS = ['debug', 'releaseLog', 'release']

# (说明, 只该在这些档里出现)
PROBES = [
    ('详细日志 VERBOSE_LOG', '侧键手势已补发', {'debug', 'releaseLog'}),
    ('调试通道 DEBUG_PROBE', 'probe/state：拿不到任务', {'debug'}),
    ('明文资产（未封存）', 'assets/donate/alipay.webp', {'debug'}),
    ('封存资产 sealed.bin', 'assets/sealed.bin', {'release', 'releaseLog'}),
]


def dex_texts(apk):
    names = subprocess.run(
        ['unzip', '-l', apk], capture_output=True, text=True, errors='ignore'
    ).stdout
    dexes = re.findall(r'classes\d*\.dex', names)
    blob = b''
    for d in dexes:
        blob += subprocess.run(
            ['unzip', '-p', apk, d], capture_output=True
        ).stdout
    return blob, len(dexes)


def main():
    apks = {}
    missing = []
    for v in VARIANTS:
        p = os.path.join(OUT, v, 'app-%s.apk' % v)
        if not os.path.exists(p):
            missing.append(v)
            continue
        blob, n = dex_texts(p)
        size = os.path.getsize(p)
        extra = []
        listing = subprocess.run(
            ['unzip', '-l', p], capture_output=True, text=True, errors='ignore'
        ).stdout
        apks[v] = {'blob': blob, 'dexes': n, 'size': size, 'listing': listing}

    if not apks:
        print('缺产物（一档都没有）:', ', '.join(missing))
        return 1
    #  **缺哪一档就只跳过哪一档**，不整步失败：PR 轮没有签名就没有 release / releaseLog 产物，
    #  以前那会让整条判据 skip（档配反了要等到签名轮才拦）。每一条 PROBES 都是
    #  「只该在 X 档出现」，单档就能判，缺档不影响其余几条。
    #  档配反了仍然拦得住的方向：debug 里冒出封存资产、release 里冒出调试通道 —— 都只需要**一档**。
    if missing:
        print('这一轮没有产物，跳过这几档:', ', '.join(missing))

    shown = [v for v in VARIANTS if v in apks]
    print('%-30s ' % '判据' + ' '.join('%-8s' % v for v in shown))
    ok = True
    for title, needle, expect in PROBES:
        row = []
        for v in shown:
            hit = needle.encode() in apks[v]['blob'] or needle in apks[v]['listing']
            should = v in expect
            mark = '有' if hit else '无'
            if hit != should:
                mark += ' ✗'
                ok = False
            row.append(mark)
        print('%-30s ' % title + ' '.join('%-8s' % c for c in row))

    print()
    for v in shown:
        print('%-8s dex 数=%d  包体=%.2f MB' % (v, apks[v]['dexes'], apks[v]['size'] / 1048576))
    print()
    print('全部符合预期' if ok else '有判据不符合预期')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
