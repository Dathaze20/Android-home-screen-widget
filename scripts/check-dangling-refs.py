#!/usr/bin/env python3
"""Catch a definition removed while its callers survive.

Twice now an edit has deleted a function and left a call to it, which only the remote Kotlin
compiler noticed. Comparing declarations against a base revision finds it locally: any function
that used to exist, no longer does, and is still referenced is a dangling reference.
"""
import re, subprocess, sys, pathlib, glob

BASE = sys.argv[1] if len(sys.argv) > 1 else "origin/main"


def declared(text):
    return set(re.findall(r'\bfun\s+(\w+)\s*[(<]', text))


def sources_at(ref):
    listing = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", ref], capture_output=True, text=True
    ).stdout.split()
    out = {}
    for path in listing:
        if path.endswith(".kt") and path.startswith("app/src/main"):
            blob = subprocess.run(["git", "show", f"{ref}:{path}"], capture_output=True, text=True)
            out[path] = blob.stdout
    return out


base_decls = set()
for text in sources_at(BASE).values():
    base_decls |= declared(text)

now_text = {p: pathlib.Path(p).read_text() for p in glob.glob("app/src/main/**/*.kt", recursive=True)}
now_decls = set()
for text in now_text.values():
    now_decls |= declared(text)

removed = base_decls - now_decls
problems = []
for path, text in now_text.items():
    # Strip comments so a name mentioned only in prose does not count as a call.
    code = re.sub(r"//[^\n]*", "", text)
    code = re.sub(r"/\*(?:.|\n)*?\*/", "", code)
    for name in removed:
        if re.search(r'(?<![\w.])' + re.escape(name) + r'\s*\(', code):
            problems.append(f"{path}: calls {name}(), which no longer exists")

for p in sorted(set(problems)):
    print("DANGLING:", p)
print(f"{len(base_decls)} declarations at {BASE}, {len(removed)} removed, {len(set(problems))} dangling")
sys.exit(1 if problems else 0)
