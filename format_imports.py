
import sys

def format_kotlin_file(file_path):
    try:
        with open(file_path, 'r') as f:
            original_content = f.read()
    except FileNotFoundError:
        return

    # Normalize line endings to prevent diff issues
    normalized_content = original_content.replace('\r\n', '\n').replace('\r', '\n')
    lines = normalized_content.split('\n')

    package_line = ""
    import_lines = []
    code_lines = []

    imports_started = False
    imports_finished = False

    # Partition the file into three parts: package, imports, and code
    temp_code_lines = []
    for line in lines:
        stripped_line = line.strip()
        if stripped_line.startswith('package '):
            package_line = line
        elif stripped_line.startswith('import '):
            import_lines.append(line)
            imports_started = True
        elif imports_started:
            if stripped_line != "":
                imports_finished = True
            if imports_finished:
                code_lines.append(line)
        else: # Lines before any imports have started
            temp_code_lines.append(line)

    for line in temp_code_lines:
        if not line.strip().startswith('package '):
            code_lines.insert(0, line)


    if not import_lines:
        return

    sorted_imports = sorted(list(set(import_lines)))

    new_lines = []
    if package_line:
        new_lines.append(package_line)
        new_lines.append("")

    new_lines.extend(sorted_imports)
    new_lines.append("")

    first_code_line_index = 0
    while first_code_line_index < len(code_lines) and code_lines[first_code_line_index].strip() == "":
        first_code_line_index += 1

    new_lines.extend(code_lines[first_code_line_index:])

    modified_content = '\n'.join(new_lines)

    if normalized_content.strip() == modified_content.strip() and original_content.split() == modified_content.split():
        return

    diff_output = f"""<<<<<<< SEARCH
{original_content}
=======
{modified_content}
>>>>>>> REPLACE"""

    with open('diff.txt', 'w') as f:
        f.write(diff_output)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(1)
    file_path = sys.argv[1]
    format_kotlin_file(file_path)
