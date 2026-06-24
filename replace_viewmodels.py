import re

def replace_in_file(filepath, search_str, replace_str):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content.replace(search_str, replace_str)

    with open(filepath, 'w') as f:
        f.write(new_content)
