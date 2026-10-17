"""列出「import 的短名在同一文件里另有声明」的文件 —— ktlint / detekt 的 UnusedImports 都按名字判定，
遇到这种同名遮蔽会把死 import 当成「还在用」。这里只负责**找出来**，删不删由人看一眼决定。

用法：python tools/find_shadowed_imports.py [源码根]
"""

import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java/com/lookie/opluskey'

DECL_RE = re.compile(
    r'\b(?:val|var|fun)\s+([A-Za-z_]\w*)\s*[:=(]'
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
        declared = {m.group(1) for m in DECL_RE.finditer(code)}
        for sym, line in imports.items():
            if sym in declared:
                hits.append((path.replace('\\', '/'), sym, line))

for path, sym, line in sorted(hits):
    print('%s\n    %s   （文件里另有同名声明的 %s）' % (path, line, sym))
print('\ntotal: %d' % len(hits))
