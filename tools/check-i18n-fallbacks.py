import io
import json
import os
import re

# 兜底文案不只出现在界面层：`core` 里也有 `I18n.get(..., "兜底")`（命令 / 动作的名字、
# 失败提示那一族），漏扫它们等于那几处改错了没人拦。
#  允许磁盘上还没有的**变体源集**（debug / release）：它们不是每个 checkout 都有，
#  但 main 那两个目录**必须**在 —— 见 run() 开头那道检查。
ROOTS = ['app/src/main/java', 'core/src/main/java']
OPTIONAL_ROOTS = ['app/src/debug/java', 'app/src/release/java']
STRINGS = 'core/src/main/java/com/lookie/opluskey/core/i18n/Strings.kt'

# 源码里那句兜底是 **Kotlin 字面量**（`\n` / `\"` / `\\` 都还是转义写法），字典里那一条是
# **已经解析好的正文**。两边直接比，正文里带换行的条目会假红（`dialog.reset_module_message`
# 就分了两行）。所以先按字面量规则解回来再比 —— 只认这几种转义，其余原样。
ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\', '$': '$'}


def unescape(raw):
    out = []
    i = 0
    while i < len(raw):
        c = raw[i]
        if c == '\\' and i + 1 < len(raw):
            out.append(ESCAPES.get(raw[i + 1], raw[i + 1]))
            i += 2
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def run():
    src = io.open(STRINGS, encoding='utf-8').read()
    consts = dict(re.findall(r'const val ([A-Z0-9_]+) = "([^"]+)"', src))
    zh = json.load(io.open('app/src/main/assets/locales/zh-CN.json', encoding='utf-8'))

    # 兜底可以是单行，也可以是换行展开的（`I18n.get(\n Strings.X,\n "…",\n)`）
    pat = re.compile(
        r'I18n\s*\.\s*get\(\s*Strings\.([A-Z0-9_]+)\s*,\s*"((?:[^"\\]|\\.)*)"\s*,?\s*\)',
        re.S,
    )
    #  **目录不存在就报错**：以前 `os.walk` 遇到不存在的目录会静默返回空，
    #  于是「路径写错」的表现是「0 处调用、全部一致、通过」。
    for root in ROOTS:
        if not os.path.isdir(root):
            raise SystemExit('i18n 兜底审计：源码目录不存在：' + root)

    total = 0
    bad = []
    for root in ROOTS + [d for d in OPTIONAL_ROOTS if os.path.isdir(d)]:
        for dirpath, _, files in os.walk(root):
            for f in sorted(files):
                if not f.endswith('.kt'):
                    continue
                p = os.path.join(dirpath, f)
                text = io.open(p, encoding='utf-8').read()
                for m in pat.finditer(text):
                    total += 1
                    name, fallback = m.group(1), unescape(m.group(2))
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

    if total == 0:
        #  **0 处不能算通过**：`I18n.get(…)` 的写法一变（换行、加参数、换成别的入口），
        #  这条正则就一条也匹配不上，于是「664 处全部一致」变成「0 处全部一致」——
        #  闸还在，判据没了。这种静默失效必须当场变成红的。
        print('没有扫到任何「I18n.get(…, "兜底")」调用 —— 调用写法变了？这条闸已失效，按失败处理。')
        return 1

    print(f'带兜底的调用点：{total} 处；兜底与字典不一致：{len(bad)} 处')
    for p, line, name, key, fallback, value in bad:
        print(f'\n{p}:{line}\n  {name} ({key})\n  兜底: {fallback}\n  字典: {value}')
    return len(bad)


if __name__ == '__main__':
    raise SystemExit(0 if run() == 0 else 1)
