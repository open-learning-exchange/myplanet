import sys
import os

def check_empty_lines(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    lines = content.split('\n')
    import_line_indices = [i for i, line in enumerate(lines) if line.startswith('import ')]
    if not import_line_indices:
        return True

    first_idx = import_line_indices[0]
    last_idx = import_line_indices[-1]

    # Check line before first import
    if first_idx > 0:
        if lines[first_idx - 1].strip() != '':
            print(f"Error: No empty line before imports in {filepath}")
            return False

    # Check line after last import
    if last_idx < len(lines) - 1:
        if lines[last_idx + 1].strip() != '':
            print(f"Error: No empty line after imports in {filepath}")
            return False

    return True

all_good = True
for root, _, files in os.walk('.'):
    if 'build' in root or '.git' in root:
        continue
    for file in files:
        if file.endswith('.kt'):
            if not check_empty_lines(os.path.join(root, file)):
                all_good = False

if all_good:
    print("All files have empty lines before and after imports.")
