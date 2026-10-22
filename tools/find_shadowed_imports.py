"""列出「import 的短名在同一文件里**顶层**另有声明」的文件 —— ktlint / detekt 的 UnusedImports 都按
名字判定，遇到这种同名遮蔽会把死 import 当成「还在用」。这里只负责**找出来**，删不删由人看一眼决定。

判据只认**顶层**声明（行首无缩进）：只有顶层声明会把 import 在整个文件里遮住。写在
`object` / `class` 里的同名成员只遮蔽它自己那一层 —— 同一个文件别处仍可能用裸名引用 import 那一份，
按「文件里另有同名声明」判会报一堆假阳性（实测 19 条全是这一类，删掉直接编译不过）。

用法：python tools/find_shadowed_imports.py [源码根]
"""

import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java/com/lookie/opluskey'

#  **行首锚定**（`^` + 无缩进）就是「只认顶层」这条判据本身，别改成 `\b`。
#  `(?!\s*\.)` 排除扩展声明（`fun SettingsPage.HomeScreen` / `val SettingsPage.x`）——
#  那一条的「名字」是接收者类型，不是新声明，按它判会把每个 `fun SettingsPage.xxx` 都算成遮蔽。
DECL_RE = re.compile(
    r'^(?:internal |private |public |abstract |open |sealed |data )*'
    r'(?:(?:val|var|class|object|interface|typealias)\s+([A-Za-z_]\w*)(?!\s*\.)'
    r'|fun\s+([A-Za-z_]\w*)\s*[(<])',
    re.M,
)

hits = []
for dp, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith('.kt'):
            continue
        path = os.path.join(dp, fn)
        src = open(path, encoding='utf-8').read()
        imports = {}
        for line in src.splitlines():
            if line.startswith('import '):
                sym = line.strip().split('.')[-1].split(' as ')[-1].strip()
                if sym and sym != '*':
                    imports[sym] = line.strip()
        if not imports:
            continue
        body = '\n'.join(l for l in src.splitlines() if not l.startswith('import '))
        code = re.sub(r'//[^\n]*', '', body)
        code = re.sub(r'/\*.*?\*/', '', code, flags=re.S)
        code = re.sub(r'"(?:\\.|[^"\\])*"', '""', code)
        declared = {m.group(1) or m.group(2) for m in DECL_RE.finditer(code)}
        for sym, line in imports.items():
            if sym in declared:
                hits.append((path.replace('\\', '/'), sym, line))

for path, sym, line in sorted(hits):
    print('%s\n    %s   （文件顶层另有同名声明的 %s）' % (path, line, sym))
print('\ntotal: %d' % len(hits))
