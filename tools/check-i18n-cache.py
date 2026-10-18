#!/usr/bin/env python3
r"""i18n 缓存审计 —— 「`remember` 里缓存了字典文案、却没把语言档当键」。

为什么要有这条：`remember { … }` 的键只决定**什么时候重算**，而切语言不会重建页面 ——
键里没有语言档的 `remember` 会把旧语言的字符串原样留在界面上。

真机上就是这么发现的（2026-10-18，PLK110 / ColorOS 17）：切到 English 后整页都跟着变了，
**只有「关于」页的「签名 / 调试版本（不校验签名）」两行还是中文** —— 那两行是
`remember(context) { signatureSummary(context) }`，而 `signatureSummary` 内部读字典。

判据（两档，命中任一即报）：

1. `remember(…) { … }` 的**块体**里直接出现 `I18n.`，而键里没有语言档；
2. 块体里调用了**本文件内**某个「读了 `I18n.` 的函数」，而键里没有语言档。

「键里有语言档」的判据是键文本里出现 `Lang` / `lang` / `language` / `effectiveLanguage`
（`AppLanguage` 那一路都带着 `Lang`）。

三条**有意放行**的写法（命中不报）：

- 语句行尾带注释 `i18n-locked` —— 明确表示「这份字符串与语言无关」（例如版本号里的构建类型）；
- `remember` 的块体只拿字典做**非显示**用途（`I18n.isChinese` 这种布尔判定）：判据只认
  `I18n.get` / `I18n.format` 两个取值口；
- `remember` 里缓存的只是**入参**（如 `LocalContext.current`），字典文案在组合期现算。

用法：

    python3 tools/check-i18n-cache.py            # 扫仓里的 ui/
    python3 tools/check-i18n-cache.py --path .temp/probe   # 自测用

退出码：0 = 通过；1 = 有命中。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_ROOT = REPO / "app/src/main/java/com/lookie/opluskey/ui"

# 取字典的两个口 —— 只认这两个，`I18n.isChinese` 这类判定不算（它不出字符串）。
I18N_READ = re.compile(r"\bI18n\.(get|format)\s*\(")

# 「键里有语言档」的判据：`AppLanguage` / `currentLang` / `effectiveLanguage` 都落在这几个词上。
LANG_KEY = re.compile(r"(LANG|Lang|lang|language|effectiveLanguage)")

# 「这一份与语言无关」的显式放行（写在语句那一行上）。
LOCKED = "i18n-locked"

REMEMBER = re.compile(r"\bremember\s*\(")
REMEMBER_NO_KEY = re.compile(r"\bremember\s*\{")

# 表达式体函数的结束点：下一个顶层声明 / 注解 / 收类括号。
DECL_BOUNDARY = re.compile(r"\n(?=@|private |internal |public |protected |fun |class |object |enum |})")


def strip_strings_and_comments(src: str) -> str:
    """把字符串字面量与注释换成等长空白（保住下标与行号），避免把文案里的 `I18n.` 当真。"""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                out[i] = " "
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            out[i] = out[i + 1] = " "
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                if src[i] != "\n":
                    out[i] = " "
                i += 1
            if i + 1 < n:
                out[i] = out[i + 1] = " "
                i += 2
        elif c == '"':
            # 原始字符串（"""…"""）与普通串都按「跳到配对引号」处理；不追求完备，
            # 只要别把串里的花括号 / 括号算进结构即可。
            raw = src[max(0, i - 1) : i] == "R"
            out[i] = " "
            i += 1
            while i < n:
                if raw and src[i] == "\\":
                    out[i] = " "
                    if i + 1 < n:
                        out[i + 1] = " "
                    i += 2
                    continue
                if not raw and src[i] == "\\":
                    out[i] = " "
                    if i + 1 < n:
                        out[i + 1] = " "
                    i += 2
                    continue
                if src[i] == '"':
                    out[i] = " "
                    i += 1
                    break
                if src[i] != "\n":
                    out[i] = " "
                i += 1
        else:
            i += 1
    return "".join(out)


def match_close(text: str, start: int, open_ch: str, close_ch: str) -> int:
    """从 `start`（指向 `open_ch`）起做括号配对，返回配对的 `close_ch` 下标（找不到返回 -1）。"""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == open_ch:
            depth += 1
        elif text[i] == close_ch:
            depth -= 1
            if depth == 0:
                return i
    return -1


def i18n_functions(clean: str) -> set[str]:
    """本文件里「读了字典」的函数名 —— 用来把第 2 档（间接）也认出来。"""
    names: set[str] = set()
    for m in re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)\s*\(", clean):
        name = m.group(1)
        # 函数体：签名括号之后第一个 `{`（表达式体 `= …` 就取到行尾）
        paren = match_close(clean, clean.index("(", m.start()), "(", ")")
        if paren < 0:
            continue
        tail = clean[paren + 1 :]
        brace = tail.find("{")
        eq = tail.find("=")
        if brace >= 0 and (eq < 0 or brace < eq):
            end = match_close(clean, paren + 1 + brace, "{", "}")
            body = clean[paren + 1 : end + 1] if end > 0 else tail
        else:
            # 表达式体（`fun x() = …`）：从 `=` 一直取到**下一个声明**开始为止 ——
            #  只取到行尾是不够的：真机上踩的那一处正是
            #  `fun signatureSummary(...) = I18n.get(...).plus(...)` 这种折行写法，
            #  只取第一行就漏掉了 `I18n.`（自测时确实漏了，见 `--path` 那档）。
            eq_at = tail.find("=")
            rest = tail[eq_at + 1 :] if eq_at >= 0 else tail
            stop = DECL_BOUNDARY.search(rest)
            body = rest[: stop.start()] if stop else rest
        if I18N_READ.search(body):
            names.add(name)
    return names


def scan_file(path: Path) -> list[str]:
    src = path.read_text(encoding="utf-8", errors="replace")
    clean = strip_strings_and_comments(src)
    tainted = i18n_functions(clean)
    hits: list[str] = []
    for m in REMEMBER.finditer(clean):
        # 键：`remember(` 括号里那段（`remember {` 形式没有键）
        if m.group(0).startswith("remember {"):
            keys = ""
            body_start = clean.index("{", m.start())
        else:
            paren = match_close(clean, m.start() + len("remember"), "(", ")")
            if paren < 0:
                continue
            keys = clean[m.start() + len("remember") + 1 : paren]
            brace = clean.find("{", paren)
            if brace < 0:
                continue
            body_start = brace
        body_end = match_close(clean, body_start, "{", "}")
        if body_end < 0:
            continue
        body = clean[body_start : body_end + 1]

        reads = bool(I18N_READ.search(body))
        if not reads:
            for name in tainted:
                if re.search(r"\b" + re.escape(name) + r"\s*\(", body):
                    reads = True
                    break
        if not reads or LANG_KEY.search(keys):
            continue

        line_no = src.count("\n", 0, m.start()) + 1
        lines = src.splitlines()
        if 0 < line_no <= len(lines) and LOCKED in lines[line_no - 1]:
            continue
        snippet = " ".join(lines[line_no - 1].strip().split())[:110]
        hits.append(f"{path.relative_to(REPO).as_posix()}:{line_no}  {snippet}")
    return hits


def main(argv: list[str]) -> int:
    root = DEFAULT_ROOT
    if "--path" in argv:
        root = Path(argv[argv.index("--path") + 1]).resolve()
    files = sorted(root.rglob("*.kt"))
    if not files:
        print(f"找不到源码：{root}", file=sys.stderr)
        return 1
    hits: list[str] = []
    for f in files:
        hits += scan_file(f)
    print(f"扫了 {len(files)} 个 Kotlin 文件")
    if hits:
        print(f"\n✗ 有 {len(hits)} 处 `remember` 缓存了字典文案却没把语言档当键：")
        for h in hits:
            print(f"  {h}")
        print(
            "\n修法二选一：把语言档加进键（`remember(context, currentLang)`）；"
            f"确实与语言无关的，在那一行加注释 `{LOCKED}`。",
        )
        return 1
    print("✓ remember 里的字典文案都跟着语言档走")
    return 0


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, OSError):
            pass
    sys.exit(main(sys.argv[1:]))
