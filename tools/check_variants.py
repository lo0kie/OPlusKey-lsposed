"""三档 APK 的区分度检查：每个判别项都必须只在它该出现的档里出现。"""

import os
import re
import subprocess
import sys

OUT = 'app/build/outputs/apk'
VARIANTS = ['debug', 'beta', 'release']

# (说明, 只该在这些档里出现)
PROBES = [
    ('详细日志 VERBOSE_LOG', '侧键手势已补发', {'debug', 'beta'}),
    ('机型闸关 ENFORCE_DEVICE_GATE', '本包不做适配清单这道闸', {'debug', 'beta'}),
    ('调试通道 DEBUG_PROBE', 'probe/state：拿不到任务', {'debug'}),
    ('明文资产（未封存）', 'assets/donate/alipay.webp', {'debug'}),
    ('封存资产 sealed.bin', 'assets/sealed.bin', {'beta', 'release'}),
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
    for v in VARIANTS:
        p = os.path.join(OUT, v, 'app-%s.apk' % v)
        if not os.path.exists(p):
            print('缺产物:', p)
            return 1
        blob, n = dex_texts(p)
        size = os.path.getsize(p)
        extra = []
        listing = subprocess.run(
            ['unzip', '-l', p], capture_output=True, text=True, errors='ignore'
        ).stdout
        for line in listing.splitlines():
            pass
        apks[v] = {'blob': blob, 'dexes': n, 'size': size, 'listing': listing}

    print('%-30s %-8s %-8s %-8s' % ('判据', 'debug', 'beta', 'release'))
    ok = True
    for title, needle, expect in PROBES:
        row = []
        for v in VARIANTS:
            hit = needle.encode() in apks[v]['blob'] or needle in apks[v]['listing']
            should = v in expect
            mark = '有' if hit else '无'
            if hit != should:
                mark += ' ✗'
                ok = False
            row.append(mark)
        print('%-30s %-8s %-8s %-8s' % (title, row[0], row[1], row[2]))

    print()
    for v in VARIANTS:
        print('%-8s dex 数=%d  包体=%.2f MB' % (v, apks[v]['dexes'], apks[v]['size'] / 1048576))
    print()
    print('全部符合预期' if ok else '有判据不符合预期')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
