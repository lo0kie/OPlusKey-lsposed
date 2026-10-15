"""离线读 dex：按 类名#方法名 打 smali（或列类成员 / 按字符串找用法）。

用法:
  python dexdump.py <dex> class <类名>
  python dexdump.py <dex> method <类名> <方法名>
  python dexdump.py <dex> find <子串>                 # 按**类名**子串找类
  python dexdump.py <dex> uses <子串> [<子串> ...]     # 按**字符串常量**找用法
  python dexdump.py <dex> refs <方法名> [<方法名> ...] # 按**被调方法名**找调用点

`uses` / `refs` 是为「读 ROM 私有实现」补的两档（见 `docs/zoom-window.md` §12.4）。
判「能不能调」看方法体（`method`），判「谁在用这个 key / 谁在调这个方法」就得全 dex 扫 ——
单看一个类的成员列表看不出来。两档都只打命中那一行，不打整个方法体。

注意：**退出码**：命中 0 条 / 找不到类或方法 ⇒ 打到 stderr 并 **exit 1**。
「ROM 里真没有」与「这份 dex 没解析成功」从此可以用退出码分开 —— 原来一律 exit 0，
脚本没法区分，而 `docs/device-findings.md` 里那些结论正依赖这个工具。
"""

import sys

from loguru import logger

# 注意：**不要把日志整档掐掉**：原来这里只有 `logger.remove()`，
# androguard 解析 dex 时的告警（截断 / 结构异常 …）被一起静音，于是「解析失败」看起来
# 和「解析成功、只是没这个类」一模一样。改成把 WARNING 以上送到 stderr —— 正常跑不受影响，
# 出问题时看得见。
logger.remove()
logger.add(sys.stderr, level="WARNING")

from androguard.core.dex import DEX


def fail(msg):
    """
    没命中 / 解析不出来 —— **打到 stderr 并以非 0 退出**。

    注意：原来这些分支只 `print(...)` 然后 `return`，退出码恒为 0：
    调用方（人、或脚本）没法从退出码区分「ROM 里真没这个类」与「这份 dex 压根没解析成功」，
    而 `docs/device-findings.md` 里那些「本机没有 X」的结论正依赖这个工具。
    """
    print(msg, file=sys.stderr)
    sys.exit(1)


def norm(name):
    """`com.foo.Bar` 与 `Lcom/foo/Bar;` 都接受，统一成 androguard 的写法。"""
    if name.startswith("L") and name.endswith(";"):
        return name
    return "L" + name.replace(".", "/") + ";"


def load(path):
    with open(path, "rb") as f:
        return DEX(f.read())


def iter_methods(cls):
    """兼容不同版本的取方法接口。"""
    for attr in ("get_methods", "get_encoded_methods"):
        fn = getattr(cls, attr, None)
        if fn is None:
            continue
        try:
            got = list(fn())
        except Exception:
            continue
        if got:
            return got
    return []


def mname(m):
    fn = getattr(m, "get_name", None)
    return fn() if fn else str(m)


def mdesc(m):
    """`name(参数类型)返回类型` —— 反射按这个找方法。"""
    for attr in ("get_descriptor", "get_signature"):
        fn = getattr(m, attr, None)
        if fn is None:
            continue
        try:
            return fn()
        except Exception:
            continue
    return "?"


def smali_of(m):
    code = None
    for attr in ("get_code",):
        fn = getattr(m, attr, None)
        if fn is not None:
            try:
                code = fn()
            except Exception:
                code = None
    if code is None:
        return "(无方法体)"
    bc = None
    for attr in ("get_bc",):
        fn = getattr(code, attr, None)
        if fn is not None:
            try:
                bc = fn()
            except Exception:
                bc = None
    if bc is None:
        return "(取不到指令流)"
    ins = None
    for attr in ("get_instructions",):
        fn = getattr(bc, attr, None)
        if fn is not None:
            try:
                ins = list(fn())
            except Exception:
                ins = None
    if ins is None:
        return "(取不到指令列表)"
    out = []
    for i in ins:
        name = getattr(i, "get_name", lambda: "?")()
        outp = getattr(i, "get_output", lambda: "")()
        out.append(f"{name:<18} {outp}")
    return "\n".join(out)


def main():
    path, mode = sys.argv[1], sys.argv[2]
    dx = load(path)
    classes = list(dx.get_classes())

    if mode == "find":
        needle = sys.argv[3]
        hits = [c.get_name() for c in classes if needle in c.get_name()]
        for n in hits:
            print(n)
        if not hits:
            fail(f"没有类名含 {needle!r} 的类")
        return

    # 全 dex 扫的两档：只打命中那一行（不打整个方法体，否则输出会被刷爆）
    if mode in ("uses", "refs"):
        needles = sys.argv[3:]
        hits = 0
        for c in classes:
            cn = c.get_name()
            for m in iter_methods(c):
                body = smali_of(m)
                if not body:
                    continue
                for n in needles:
                    if n in body:
                        print(f"### {cn} :: {mname(m)}   <- {n}")
                        for line in body.splitlines():
                            if n in line:
                                print(f"      {line.strip()}")
                        hits += 1
                        break
        if hits == 0:
            fail(f"全 dex 扫完，{needles} 一处都没命中")
        return

    target = norm(sys.argv[3])
    cls = None
    for c in classes:
        if c.get_name() == target:
            cls = c
            break
    if cls is None:
        fail(f"找不到类: {target}")
        return

    if mode == "class":
        print("类:", cls.get_name(), " super:", cls.get_superclassname())
        for fn in ("get_fields", "get_instance_fields", "get_static_fields"):
            f = getattr(cls, fn, None)
            if f is None:
                continue
            try:
                got = list(f())
            except Exception:
                continue
            for x in got:
                nm = getattr(x, "get_name", lambda: str(x))()
                ty = getattr(x, "get_type", lambda: "")()
                print(f"  字段: {ty} {nm}")
        for m in iter_methods(cls):
            print("  方法:", mname(m), "  ", mdesc(m))
        return

    want = sys.argv[4]
    hit = [m for m in iter_methods(cls) if mname(m) == want]
    if not hit:
        fail(f"找不到方法: {want}（类 {target} 上）")
        return
    for m in hit:
        print("=" * 20, cls.get_name(), mname(m))
        print(smali_of(m))


if __name__ == "__main__":
    main()
