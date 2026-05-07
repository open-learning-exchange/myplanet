import os
import re

def is_class_import(import_line):
    parts = import_line.split('.')
    if len(parts) > 1 and parts[-1][0].isupper():
        return True
    return False

def extract_import_name(imp):
    match = re.match(r'^import ([\w\.]+)(?:\s+as\s+(\w+))?$', imp)
    if match:
        full_path = match.group(1)
        alias = match.group(2)
        parts = full_path.split('.')
        last_part = parts[-1]

        if alias:
            return alias
        if last_part == '*':
            return None
        return last_part
    return None

def optimize_imports(imports, content):
    used_imports = []

    # Extract all words from the content (excluding import block)
    content_words = set(re.findall(r'\b\w+\b', content))

    for imp in imports:
        name = extract_import_name(imp)

        if name is None:
            # Wildcard import, keep it
            used_imports.append(imp)
            continue

        if name in content_words:
            used_imports.append(imp)
        else:
            if not name[0].isupper():
                 used_imports.append(imp)
            else:
                 pass

    return used_imports

def organize_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    package_match = re.search(r'^package [^\n]+', content, re.MULTILINE)
    if not package_match:
        return

    lines = content.split('\n')

    # First, replace wildcard mockk imports
    for i, line in enumerate(lines):
        if line.startswith('import io.mockk.*'):
            lines[i] = "import io.mockk.coEvery\nimport io.mockk.every\nimport io.mockk.mockk\nimport io.mockk.verify"

    content = '\n'.join(lines)
    lines = content.split('\n')

    import_line_indices = [i for i, line in enumerate(lines) if line.startswith('import ')]
    if not import_line_indices:
        return

    first_idx = import_line_indices[0]
    last_idx = import_line_indices[-1]

    has_code_between = False
    for i in range(first_idx, last_idx + 1):
        line = lines[i].strip()
        if line and not line.startswith('import '):
            has_code_between = True
            break

    if has_code_between:
        return

    imports = [lines[i] for i in range(first_idx, last_idx + 1) if lines[i].startswith('import ')]

    unique_imports = sorted(list(set(imports)))

    code_content = '\n'.join(lines[:first_idx] + lines[last_idx+1:])
    optimized_imports = optimize_imports(unique_imports, code_content)

    new_lines = []

    for i in range(first_idx):
        new_lines.append(lines[i])

    while new_lines and not new_lines[-1].strip():
        new_lines.pop()

    # We must have EXACTLY one empty line before
    new_lines.append('')

    new_lines.extend(optimized_imports)

    # We must have EXACTLY one empty line after
    new_lines.append('')

    remaining_lines = lines[last_idx + 1:]
    while remaining_lines and not remaining_lines[0].strip():
        remaining_lines.pop(0)

    new_lines.extend(remaining_lines)

    new_content = '\n'.join(new_lines)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

for root, _, files in os.walk('.'):
    if 'build' in root or '.git' in root:
        continue
    for file in files:
        if file.endswith('.kt'):
            organize_file(os.path.join(root, file))
