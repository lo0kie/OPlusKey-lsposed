"""分包后的 import 收尾：按符号索引规范化 + 靠编译反馈补齐。

三条规则：
  1. `import a.b.Symbol` 里 Symbol 的**当前**包是 P ⇒ 一律改成 `import P.Symbol`（搬家后旧路径会失效）。
  2. `private` 顶层声明不能被跨文件 import ⇒ 删掉那条 import（是上一轮误加的）。
  3. 剩下的 `Unresolved reference` 按索引补 import，直到编译通过。
"""

import io
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOTS = [
    os.path.join(ROOT, "app/src/main/java"),
    os.path.join(ROOT, "core/src/main/java"),
]
OWN = "com.lookie.opluskey."

DECL = re.compile(
    r"^(?P<mods>(?:(?:internal|private|public|protected|sealed|abstract|open|data|enum|annotation|"
    r"value|inline|operator|infix|suspend|tailrec|external|const|lateinit|expect|actual)\s+)*)"
    r"(?:fun|class|object|interface|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?"
    r"(?:[\w.]+\.)?"
    r"(?P<name>\w+)"
)
PKG = re.compile(r"^package\s+([\w.]+)\s*$", re.M)
IMPORT = re.compile(r"^import\s+([\w.]+)\s*$", re.M)
ERR = re.compile(r"^e: file:///(.+?\.kt):(\d+):(\d+) Unresolved reference '(\w+)'", re.M)


def read(path):
    return io.open(path, encoding="utf-8", newline="").read().replace("\r\n", "\n")


def write(path, text):
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)


def kt_files():
    for root in ROOTS:
        for dirpath, _dirs, files in os.walk(root):
            for f in files:
                if f.endswith(".kt"):
                    yield os.path.join(dirpath, f)


def build_index():
    """symbol -> (pkg, private?)；只收唯一归属的符号。"""
    seen = {}
    for path in kt_files():
        text = read(path)
        m = PKG.search(text)
        if not m:
            continue
        pkg = m.group(1)
        for line in text.split("\n"):
            d = DECL.match(line)
            if d:
                name = d.group("name")
                private = "private" in d.group("mods").split()
                seen.setdefault(name, []).append((pkg, private))
    index = {}
    for name, hits in seen.items():
        pkgs = {p for p, _ in hits}
        index[name] = (hits[0][0], all(pr for _, pr in hits)) if len(pkgs) == 1 else None
    return index


def package_of(text):
    m = PKG.search(text)
    return m.group(1) if m else None


def normalize_imports(index):
    """规则 1 / 2 + 去重。"""
    rewritten = removed = 0
    for path in kt_files():
        text = read(path)
        if OWN not in text:
            continue
        lines = text.split("\n")
        out = []
        seen = set()
        changed = False
        for line in lines:
            m = IMPORT.match(line)
            if not m:
                out.append(line)
                continue
            fq = m.group(1)
            if not fq.startswith(OWN):
                out.append(line)
                continue
            symbol = fq.rsplit(".", 1)[-1]
            hit = index.get(symbol)
            if hit is None:
                out.append(line)
                continue
            pkg, private = hit
            if private:
                removed += 1
                changed = True
                continue
            new = "import %s.%s" % (pkg, symbol)
            if new != line:
                rewritten += 1
                changed = True
            if new in seen:
                changed = True
                continue
            seen.add(new)
            out.append(new)
        if changed:
            write(path, "\n".join(out))
    print("规范化 import：改写 %d 条、删掉 %d 条（private）、去重若干" % (rewritten, removed))


def add_import(text, imp):
    lines = text.split("\n")
    if imp in lines:
        return text
    idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not idx:
        pkg_i = next(i for i, l in enumerate(lines) if l.startswith("package "))
        lines.insert(pkg_i + 1, "")
        lines.insert(pkg_i + 2, imp)
        lines.insert(pkg_i + 3, "")
        return "\n".join(lines)
    pos = None
    for i in idx:
        if lines[i] > imp:
            pos = i
            break
    if pos is None:
        pos = idx[-1] + 1
    lines.insert(pos, imp)
    return "\n".join(lines)


def compile_errors():
    r = subprocess.run(
        "gradlew.bat :app:compileReleaseKotlin --offline -Pfast "
        "-x lintVitalAnalyzeRelease",
        cwd=ROOT, shell=True, capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    return r.stdout + r.stderr


def main():
    index = build_index()
    normalize_imports(index)
    for round_no in range(1, 20):
        out = compile_errors()
        errs = ERR.findall(out)
        if not errs:
            print("第 %d 轮：编译通过（无 Unresolved reference）" % round_no)
            return
        fixed = 0
        skipped = {}
        for path, _l, _c, name in errs:
            path = path.replace("/", os.sep)
            hit = index.get(name)
            if hit is None or hit[1]:
                skipped[name] = "无唯一公开归属"
                continue
            target = hit[0]
            text = read(path)
            if package_of(text) == target:
                skipped[name] = "同包仍报错"
                continue
            new = add_import(text, "import %s.%s" % (target, name))
            if new != text:
                write(path, new)
                fixed += 1
        print("第 %d 轮：%d 条未解析，补 %d 个 import，跳过 %d 类"
              % (round_no, len(errs), fixed, len(skipped)))
        if fixed == 0:
            for k, v in sorted(skipped.items())[:25]:
                print("   %-34s %s" % (k, v))
            print(out[-5000:])
            return


if __name__ == "__main__":
    main()
