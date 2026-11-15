"""把模块配置文件在「外皮」与明文之间互转 —— 排障与手改用。

用法（仓库根目录）：

    python3 tools/config-plain.py <file.conf>                          # 解成明文，打到 stdout
    python3 tools/config-plain.py <file.conf> -o config.json           # 解出来写文件
    python3 tools/config-plain.py config.json --wrap -o out.conf       # 改完包回去
    python3 tools/config-plain.py <file.conf> --version 67 -o out.conf # 解出来改版本号、再包回去

外皮是两层：先「UTF-8 字节与密钥循环异或 + 标准 Base64」，再把 Base64 按替身字母表换写、
并按字符位置错开（实现见 `core/src/main/java/com/lookie/opluskey/core/config/FileObfuscation.kt`）。
**这两层不是加密** —— 密钥与字母表都在包里，它挡的是「打开文件顺手改一改」的人。

参数（异或密钥、替身字母表的取法、位置偏移、魔数）**从那份 Kotlin 源码现读**，不在这里
复制一份：那边一改，这个脚本自动跟上；读不出来就报错退出，不会静默按旧参数解出一堆垃圾。

文件在哪（真机，需要 root）：

    /data/user_de/0/com.oplus.gesture/files/opluskey_config.conf      # 主配置
    /data/user_de/0/com.oplus.gesture/files/opluskey_commands.conf    # 命令库

写完推回去记得还原属主与权限（`chown u0_a277:u0_a277` / `chmod 644`），否则宿主读不到。
"""

import argparse
import base64
import io
import json
import re
import sys

SOURCE = 'core/src/main/java/com/lookie/opluskey/core/config/FileObfuscation.kt'

# 第一层的标准 Base64 字母表（第二层要从它取回原始下标）
STD_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
PAD = '='


def _kotlin_string(raw):
    """Kotlin 字符串字面量里的转义 —— 这里只会出现 ASCII，与 Python 的规则一致。"""
    return raw.encode('utf-8').decode('unicode_escape')


def load_params():
    """从 Kotlin 源码读出四样参数：密钥、字符池剔除表、取法（起点 / 步长 / 字数）、位置偏移。"""
    text = io.open(SOURCE, encoding='utf-8').read()

    mask_raw = [int(n) for n in re.findall(r'\d+', re.search(r'byteArrayOf\(([^)]*)\)', text).group(1))]
    # 源码里存的是「逐字节 +1」的形态，初始化时还原（见 MASK 的注释）
    mask = [(b - 1) & 0xFF for b in mask_raw]

    excluded = _kotlin_string(
        re.search(r'POOL_EXCLUDED\s*=\s*"((?:[^"\\]|\\.)*)"', text).group(1)
    )
    offset = int(re.search(r'POOL_OFFSET\s*=\s*(-?\d+)', text).group(1))
    step = int(re.search(r'POOL_STEP\s*=\s*(-?\d+)', text).group(1))
    size = int(re.search(r'ALPHABET_SIZE\s*=\s*(\d+)', text).group(1))
    rot = [int(n) for n in re.findall(r'-?\d+', re.search(r'ROT\s*=\s*intArrayOf\(([^)]*)\)', text).group(1))]
    magic = re.search(r'MAGIC_RUNTIME\s*=\s*"([^"]*)"', text).group(1)

    # 替身字母表：可见 ASCII 剔掉那几个，按固定步长跳着取（与 SCRAMBLE 同一套算法）
    pool = [chr(c) for c in range(33, 127) if chr(c) not in excluded]
    if not pool:
        raise SystemExit(f'{SOURCE}：字符池是空的，参数没读对')
    alphabet = [pool[(i * step + offset) % len(pool)] for i in range(size)]
    index = {c: i for i, c in enumerate(alphabet)}

    if len(index) != size:
        raise SystemExit(f'{SOURCE}：替身字母表有重复字符（步长与池子不互质），参数没读对')
    if not mask or not rot:
        raise SystemExit(f'{SOURCE}：密钥或位置偏移没读到')

    return mask, alphabet, index, rot, magic


def unwrap(text, mask, index, rot, magic):
    """外皮 → 明文。首行不是魔数、或正文认不出，都抛 SystemExit（不返回半截结果）。"""
    lines = text.removeprefix('\ufeff').splitlines()
    head = lines[0].strip() if lines else ''
    if head != magic:
        raise SystemExit(f'首行不是 {magic}（实际 {head!r}）—— 不是这一档的文件')

    # 正文本来是一行；join 是为了兼容被编辑器折过行的文件（折行只影响排版，不算进下标）
    body = ''.join(lines[1:])
    body = ''.join(c for c in body if not c.isspace() and c not in ('\ufeff', '\u00a0'))

    out = []
    for at, ch in enumerate(body):
        if ch == PAD:
            out.append(PAD)
            continue
        idx = index.get(ch)
        if idx is None:
            raise SystemExit(f'正文第 {at} 个字符 {ch!r} 不在替身字母表里 —— 文件被改过或截断了')
        out.append(STD_ALPHABET[(idx - rot[at % len(rot)]) % 64])

    try:
        masked = base64.b64decode(''.join(out))
    except Exception as e:  # noqa: BLE001 - 排障脚本，报出原文即可
        raise SystemExit(f'第一层解不开（Base64 坏了）：{e}') from e

    plain = bytes(masked[i] ^ mask[i % len(mask)] for i in range(len(masked))).decode('utf-8')
    if not plain.strip():
        raise SystemExit('解出来是空的 —— 文件可能被截断了')
    return plain


def wrap(plain, mask, alphabet, rot, magic):
    """明文 → 外皮（与 unwrap 互为逆运算）。"""
    raw = plain.encode('utf-8')
    masked = bytes(raw[i] ^ mask[i % len(mask)] for i in range(len(raw)))
    b64 = base64.b64encode(masked).decode('ascii')

    out = []
    for at, ch in enumerate(b64):
        if ch == PAD:
            out.append(PAD)
        else:
            out.append(alphabet[(STD_ALPHABET.index(ch) + rot[at % len(rot)]) % 64])
    return f'{magic}\n{"".join(out)}\n'


def main():
    parser = argparse.ArgumentParser(
        description='模块配置的外皮 ↔ 明文互转（参数从 FileObfuscation.kt 现读）',
    )
    parser.add_argument('path', help='输入文件：外皮（默认）或明文（--wrap）')
    parser.add_argument('-o', '--out', help='输出文件（不给就打到 stdout）')
    parser.add_argument('--wrap', action='store_true', help='输入是明文，输出外皮')
    parser.add_argument('--version', type=int, metavar='N', help='包回去之前把 version 改成 N')
    args = parser.parse_args()

    mask, alphabet, index, rot, magic = load_params()
    text = io.open(args.path, encoding='utf-8', newline='').read()

    if args.wrap:
        plain = text
        if args.version is not None:
            plain, hit = re.subn(r'("version"\s*:\s*)\d+', rf'\g<1>{args.version}', plain, count=1)
            if not hit:
                raise SystemExit('明文里没有 "version" 那一格，改不了版本号')
        try:
            json.loads(plain)
        except Exception as e:  # noqa: BLE001 - 包回去之前先确认它是合法 JSON
            raise SystemExit(f'明文不是合法 JSON，没包（{e}）') from e
        result = wrap(plain, mask, alphabet, rot, magic)
    else:
        if args.version is not None:
            raise SystemExit('--version 只和 --wrap 一起用：先解出来（-o），改完再 --wrap')
        result = unwrap(text, mask, index, rot, magic)

    if args.out:
        io.open(args.out, 'w', encoding='utf-8', newline='\n').write(result)
    else:
        sys.stdout.write(result if result.endswith('\n') else result + '\n')


if __name__ == '__main__':
    main()
