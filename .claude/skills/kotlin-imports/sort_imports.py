#!/usr/bin/env python3
"""Sort Kotlin imports, remove unused imports, and normalize blank lines.

Usage:
    python3 sort_imports.py [ROOT ...]      # defaults to app/src
    python3 sort_imports.py --check [ROOT]  # report only, do not modify

For each *.kt file this:
  * sorts the import block alphabetically by import path (ASCII, ktlint-compatible),
  * removes imports whose simple name never appears in the rest of the file,
  * enforces exactly one blank line before and after the import block,
  * collapses any blank lines inside the block.

Unused-import removal is deliberately conservative (it only ever over-keeps,
never over-removes) so the result still compiles:
  * usage matching strips backticks, so `when` (Mockito) and other escaped
    identifiers are detected,
  * a dot counts as a word boundary, so extension calls like `.map` / `.collect`
    count as usage of the imported name,
  * imports whose simple name is a Kotlin operator/convention function
    (plus, get, getValue, componentN, ...) are never removed, since they can be
    invoked via operator syntax without the name appearing textually.

Note: this codebase has no wildcard (`import x.*`) imports. If any are ever
added, this script reports them but does NOT expand them (expansion needs a
compiler); resolve those by hand or via the IDE.
"""
import re
import sys
import os

# Kotlin operator / convention function names: an import whose simple name is one
# of these may be invoked via operator syntax (no textual name), so never remove.
OPERATORS = {
    "plus", "minus", "times", "div", "rem", "mod", "plusAssign", "minusAssign",
    "timesAssign", "divAssign", "remAssign", "inc", "dec", "unaryPlus", "unaryMinus",
    "not", "get", "set", "invoke", "contains", "iterator", "next", "hasNext",
    "rangeTo", "rangeUntil", "compareTo", "equals", "hashCode", "getValue",
    "setValue", "provideDelegate", "and", "or", "shl", "shr", "ushr", "xor", "inv",
}
OPERATORS.update(f"component{i}" for i in range(1, 30))

IMPORT_RE = re.compile(r'^import\s+([A-Za-z0-9_.`]+)(?:\s+as\s+([A-Za-z0-9_`]+))?\s*$')


def process(path, check_only):
    with open(path, encoding='utf-8') as f:
        text = f.read()
    newline = '\r\n' if '\r\n' in text else '\n'
    lines = [l.rstrip('\r') for l in text.split('\n')]

    import_idx = [i for i, l in enumerate(lines) if IMPORT_RE.match(l)]
    if not import_idx:
        return None
    first, last = import_idx[0], import_idx[-1]
    before, after = lines[:first], lines[last + 1:]

    imports = []
    wildcards = []
    for i in import_idx:
        m = IMPORT_RE.match(lines[i])
        path_tok, alias = m.group(1), m.group(2)
        if path_tok.endswith('.*'):
            wildcards.append(path_tok)
        name = alias if alias else path_tok.split('.')[-1].strip('`')
        imports.append((lines[i], path_tok, alias, name))

    # Body used for usage detection; strip backticks so escaped identifiers match.
    body_text = '\n'.join(before + after).replace('`', '')

    kept, removed = [], []
    for line, path_tok, alias, name in imports:
        clean = name.strip('`')
        if not alias and clean in OPERATORS:
            kept.append((line, path_tok))
            continue
        if path_tok.endswith('.*'):  # never auto-remove wildcards
            kept.append((line, path_tok))
            continue
        if re.search(r'(?<![\w])' + re.escape(clean) + r'(?![\w])', body_text):
            kept.append((line, path_tok))
        else:
            removed.append(line)

    kept.sort(key=lambda t: t[1])
    sorted_imports = [t[0] for t in kept]

    b = before[:]
    while b and b[-1].strip() == '':
        b.pop()
    a = after[:]
    while a and a[0].strip() == '':
        a.pop(0)

    out = []
    if b:
        out.extend(b)
        out.append('')
    out.extend(sorted_imports)
    if a:
        out.append('')
        out.extend(a)

    new_text = newline.join(out)
    if not new_text.endswith(newline):
        new_text += newline

    changed = new_text.replace('\r\n', '\n') != (
        text if text.endswith('\n') else text + '\n').replace('\r\n', '\n')

    if changed and not check_only:
        with open(path, 'w', encoding='utf-8', newline='') as f:
            f.write(new_text)

    return {'changed': changed, 'removed': removed, 'wildcards': wildcards}


def main():
    args = [a for a in sys.argv[1:] if a != '--check']
    check_only = '--check' in sys.argv
    roots = args or ['app/src']

    changed = removed_total = 0
    removed_log, wildcard_log = [], []
    for root in roots:
        for dp, _, fs in os.walk(root):
            for fn in fs:
                if not fn.endswith('.kt'):
                    continue
                p = os.path.join(dp, fn)
                r = process(p, check_only)
                if not r:
                    continue
                if r['changed']:
                    changed += 1
                for rem in r['removed']:
                    removed_total += 1
                    removed_log.append(f"{p}: {rem}")
                for w in r['wildcards']:
                    wildcard_log.append(f"{p}: import {w}")

    verb = 'would change' if check_only else 'changed'
    print(f"Files {verb}: {changed}")
    print(f"Unused imports removed: {removed_total}")
    if removed_log:
        print("--- removed ---")
        print('\n'.join(removed_log))
    if wildcard_log:
        print("--- wildcard imports left for manual review (not expanded) ---")
        print('\n'.join(wildcard_log))


if __name__ == '__main__':
    main()
