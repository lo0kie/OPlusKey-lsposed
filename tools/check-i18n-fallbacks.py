import io
import json
import os
import re

ROOT = 'app/src/main/java'
STRINGS = 'core/src/main/java/com/lookie/opluskey/core/i18n/Strings.kt'


def run():
    src = io.open(STRINGS, encoding='utf-8').read()
    consts = dict(re.findall(r'const val ([A-Z0-9_]+) = "([^"]+)"', src))
    zh = json.load(io.open('app/src/main/assets/locales/zh-CN.json', encoding='utf-8'))

    # 兜底可以是单行，也可以是换行展开的（`I18n.get(\n Strings.X,\n "…",\n)`）
    pat = re.compile(
        r'I18n\s*\.\s*get\(\s*Strings\.([A-Z0-9_]+)\s*,\s*"((?:[^"\\]|\\.)*)"\s*,?\s*\)',
        re.S,
    )
    total = 0
    bad = []
    for dirpath, _, files in os.walk(ROOT):
        for f in sorted(files):
            if not f.endswith('.kt'):
                continue
            p = os.path.join(dirpath, f)
            text = io.open(p, encoding='utf-8').read()
            for m in pat.finditer(text):
                total += 1
                name, fallback = m.group(1), m.group(2)
                line = text[: m.start()].count('\n') + 1
                key = consts.get(name)
                if key is None:
                    bad.append((p, line, name, '<常量不在分类表里>', fallback, 'const?'))
                    continue
                value = zh.get(key)
                if value is None:
                    bad.append((p, line, name, key, fallback, 'dict?'))
                elif value != fallback:
                    bad.append((p, line, name, key, fallback, value))

    print(f'带兜底的调用点：{total} 处；兜底与字典不一致：{len(bad)} 处')
    for p, line, name, key, fallback, value in bad:
        print(f'\n{p}:{line}\n  {name} ({key})\n  兜底: {fallback}\n  字典: {value}')
    return len(bad)


if __name__ == '__main__':
    raise SystemExit(0 if run() == 0 else 1)
