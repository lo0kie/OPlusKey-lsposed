"""配置结构闸 —— 往配置里加字段，必须同一次改动抬 `ConfigCodec.VERSION`。

为什么要有这一道（真机上已经踩过两次，第二次就在今天）：

  `ConfigCodec` 校 md5 的口径是「把 md5 那一格置空后、**按当前 DTO 重新编码**得到的文本」。
  `Json` 是 `encodeDefaults = true`，所以每加一个字段，重编码出来的正文就多一行 ——
  **已有文件的 md5 当场全对不上**，每一份配置都被判「读不懂」：页面只读、注入侧不接管，
  用户看到的就是「配置读不出来」。而这件事没有任何编译期信号，只能靠记性；
  记性靠不住，所以用这道闸把「记性」变成 CI 里的一条红。

判据（三条，第 1、2 条是硬判据）：

  1. **结构变了、版本没抬** ⇒ 红。结构 = [ConfigDto] 里每个 `data class` 的字段清单
     （字段名 + `@SerialName` 别名），按类分组后取指纹。
  2. **版本往回退**（比基线里记的小）⇒ 红。版本号是单调的，改小等于让新旧文件撞号。
  3. 结构变了、版本也抬了 ⇒ 绿，但会提示跑 `--update` 刷新基线
     （不刷新的话，下一个人接着加字段时这道闸就看不出来）。

用法（仓库根目录）：

    python3 tools/audit-config-version.py            # 校验（CI 跑的这条）
    python3 tools/audit-config-version.py --update   # 确认抬了版本之后刷新基线
"""

import hashlib
import io
import os
import re
import sys

DTO = 'core/src/main/java/com/lookie/opluskey/core/config/ConfigDto.kt'
CODEC = 'core/src/main/java/com/lookie/opluskey/core/config/ConfigCodec.kt'
BASELINE = 'tools/config-shape.txt'

# `data class Xxx(` —— 名字后面紧跟括号（允许换行与修饰符）
CLASS_HEAD = re.compile(r'\bdata\s+class\s+(\w+)\s*\(')
# 构造参数里的一个字段：可带 `@SerialName("x")`，可带注解在前
FIELD = re.compile(r'^\s*(?:@SerialName\("([^"]+)"\)\s*)?val\s+(\w+)\s*:', re.M)


def read(path):
    return io.open(path, encoding='utf-8').read()


def class_bodies(text):
    """把每个 `data class` 的参数区切出来 —— 按括号配平，嵌套类各算各的。

    不用正则一把梭：构造参数里有默认值 `emptyMap()` / `listOf()` 与字符串，
    括号与引号都得配平，否则内层类的字段会被算到外层头上。
    """
    out = []
    for m in CLASS_HEAD.finditer(text):
        name = m.group(1)
        i = m.end()  # 指向 '(' 之后
        depth = 1
        start = i
        while i < len(text) and depth > 0:
            ch = text[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            i += 1
        out.append((name, text[start: i - 1]))
    return out


def shape_of(text):
    """结构指纹：`类.字段`（有 `@SerialName` 就用线格式上的那个名字）排好序再哈希。"""
    names = []
    for cls, body in class_bodies(text):
        for m in FIELD.finditer(body):
            wire, prop = m.group(1), m.group(2)
            names.append(f'{cls}.{wire or prop}')
    if not names:
        raise SystemExit(f'::error::{DTO} 里一个字段都没解析出来 —— 解析器要跟着改')
    names.sort()
    digest = hashlib.sha256('\n'.join(names).encode('utf-8')).hexdigest()[:16]
    return digest, len(names)


def version_of(text):
    m = re.search(r'const\s+val\s+VERSION\s*=\s*(\d+)', text)
    if not m:
        raise SystemExit(f'::error::{CODEC} 里找不到 `const val VERSION = N`')
    return int(m.group(1))


def load_baseline(path):
    if not os.path.exists(path):
        return None
    data = {}
    for line in read(path).split('\n'):
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, _, v = line.partition('=')
        data[k.strip()] = v.strip()
    return data


def write_baseline(path, version, shape, count):
    io.open(path, 'w', encoding='utf-8').write(
        '# 配置结构基线 —— 由 tools/audit-config-version.py --update 生成，别手改。\n'
        '#\n'
        '# 记的是「上一次抬版本时，ConfigDto 的字段清单长什么样」。加字段之后：\n'
        '#   1) 抬 ConfigCodec.VERSION；2) 跑一次 --update 把这一份刷新 —— 两步缺一不可，\n'
        '#   否则下一个人接着加字段时，这道闸看不出来（它只比「相对上一次抬版本」有没有变）。\n'
        f'version={version}\n'
        f'shape={shape}\n'
        f'fields={count}\n',
    )


def main():
    update = '--update' in sys.argv
    shape, count = shape_of(read(DTO))
    version = version_of(read(CODEC))

    if update:
        write_baseline(BASELINE, version, shape, count)
        print(f'基线已刷新：version={version} shape={shape}（{count} 个字段）→ {BASELINE}')
        return 0

    base = load_baseline(BASELINE)
    if base is None:
        print(f'::error::没有基线文件 {BASELINE} —— 先跑一次 --update 建基线')
        return 1

    base_version = int(base.get('version', '0'))
    base_shape = base.get('shape', '')
    print(f'配置结构：{count} 个字段 / shape={shape} / version={version}')
    print(f'基线：      shape={base_shape} / version={base_version}')

    fail = 0
    if version < base_version:
        print(
            f'::error::VERSION 往回退了（{base_version} → {version}）—— 版本号必须单调，'
            f'改小会让新旧文件撞号',
        )
        fail = 1

    if shape == base_shape:
        if version != base_version:
            print(f'提示：结构没变、版本抬了（{base_version} → {version}）—— 跑一次 --update 同步基线')
        if fail:
            return 1
        print('配置结构闸通过。')
        return 0

    if version == base_version:
        print(
            '::error::配置结构变了，但 ConfigCodec.VERSION 没抬 —— '
            '旧文件会被判「读不懂」（页面只读、注入侧不接管）',
        )
        print('    改法：抬 VERSION，再跑 python3 tools/audit-config-version.py --update')
        return 1

    print(f'配置结构变了，版本已抬（{base_version} → {version}）—— 记得跑一次 --update 刷新基线')
    return fail


if __name__ == '__main__':
    sys.exit(main())
