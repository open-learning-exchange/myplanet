import sys, re, os

def process_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.read().splitlines()
    start = None
    end = None
    for i, line in enumerate(lines):
        if line.strip().startswith('import '):
            if start is None:
                start = i
            end = i
        else:
            if start is not None:
                break
    if start is None:
        return False
    header = lines[:start]
    imports = lines[start:end+1]
    body = lines[end+1:]

    # Clean header: remove trailing blank lines
    while header and header[-1].strip() == '':
        header.pop()
    header.append('')

    # Clean body: remove leading blank lines
    while body and body[0].strip() == '':
        body.pop(0)
    body.insert(0, '')

    # Normalize imports
    imp_clean = []
    for line in imports:
        line = line.strip()
        if not line:
            continue
        if not line.startswith('import '):
            continue
        imp_clean.append(line)
    # Remove duplicates
    imp_clean = sorted(set(imp_clean))

    # Attempt to remove unused imports
    body_text = '\n'.join(body)
    used_imports = []
    for imp in imp_clean:
        # extract alias or class name
        m = re.match(r'import\s+([\w.]+)(?:\s+as\s+(\w+))?', imp)
        if not m:
            used_imports.append(imp)
            continue
        fqname, alias = m.groups()
        name = alias if alias else fqname.split('.')[-1]
        pattern = r'\b' + re.escape(name) + r'\b'
        if re.search(pattern, body_text):
            used_imports.append(imp)
    imp_clean = used_imports

    changed = (imports != imp_clean)
    new_lines = header + imp_clean + body
    new_content = '\n'.join(new_lines)
    if lines and lines[-1].strip() == '':
        original_content = '\n'.join(lines)
    else:
        original_content = '\n'.join(lines) + '\n'
    if not new_content.endswith('\n'):
        new_content += '\n'
    if new_content != original_content:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return changed

if __name__ == '__main__':
    changed_any = False
    for path in sys.argv[1:]:
        if process_file(path):
            changed_any = True
    sys.exit(0 if changed_any else 0)
