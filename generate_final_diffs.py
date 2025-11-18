
import sys

def generate_diff(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            original_content = "".join(lines)
    except Exception:
        return

    before_imports = []
    imports = []
    after_imports = []

    # State machine to parse the file
    state = 0 # 0: before imports, 1: in imports, 2: after imports
    for line in lines:
        stripped = line.strip()
        if state == 0:
            if stripped.startswith('import '):
                state = 1
                imports.append(stripped)
            else:
                before_imports.append(line)
        elif state == 1:
            if stripped.startswith('import '):
                imports.append(stripped)
            else:
                state = 2
                after_imports.append(line)
        elif state == 2:
            after_imports.append(line)

    if not imports:
        return

    # Sort and unique the imports
    sorted_imports = sorted(list(set(imports)))

    # Trim blank lines
    while before_imports and not before_imports[-1].strip():
        before_imports.pop()
    while after_imports and not after_imports[0].strip():
        after_imports.pop(0)

    new_content = "".join(before_imports)
    if before_imports:
        new_content += "\n\n"
    new_content += "\n".join(sorted_imports)
    if after_imports:
        new_content += "\n\n"
    new_content += "".join(after_imports)

    if original_content != new_content:
        print(f"--- DIFF FOR {filepath} ---")
        print("<<<<<<< SEARCH")
        print(original_content, end='')
        print("=======")
        print(new_content, end='')
        print(">>>>>>> REPLACE")
        print("\n\n")


if __name__ == "__main__":
    with open('kotlin_files.txt', 'r') as f:
        for line in f:
            filepath = line.strip()
            if filepath:
                generate_diff(filepath)
