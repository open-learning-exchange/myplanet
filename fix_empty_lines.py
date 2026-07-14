#!/usr/bin/env python3
"""
Fix empty lines before and after import blocks in Kotlin files.
- Exactly one empty line after package declaration
- Exactly one empty line after the last import
"""

import os
import re

def get_all_kt_files(root_dir):
    """Find all .kt files recursively."""
    kt_files = []
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.kt'):
                kt_files.append(os.path.join(root, file))
    return kt_files

def fix_file(kt_file_path):
    """Fix empty lines around imports in a Kotlin file."""
    try:
        with open(kt_file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {kt_file_path}: {e}")
        return False
    
    lines = content.split('\n')
    
    # Find package line, import lines, and code start
    package_idx = -1
    first_import_idx = -1
    last_import_idx = -1
    
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith('package '):
            package_idx = i
        elif stripped.startswith('import '):
            if first_import_idx == -1:
                first_import_idx = i
            last_import_idx = i
    
    if package_idx == -1 or first_import_idx == -1:
        return False  # No package or no imports
    
    # Ensure exactly one empty line after package (line package_idx + 1)
    # Remove any existing empty lines between package and first import
    idx = package_idx + 1
    while idx < first_import_idx and lines[idx].strip() == '':
        lines.pop(idx)
        first_import_idx -= 1
        last_import_idx -= 1
    
    # Insert exactly one empty line after package
    lines.insert(package_idx + 1, '')
    first_import_idx += 1
    last_import_idx += 1
    
    # Now ensure exactly one empty line after last import
    # The last import is now at last_import_idx
    # Check what's at last_import_idx + 1
    after_import_idx = last_import_idx + 1
    
    # Remove consecutive empty lines after imports
    while after_import_idx < len(lines) and lines[after_import_idx].strip() == '':
        lines.pop(after_import_idx)
    
    # Insert exactly one empty line after imports
    lines.insert(after_import_idx, '')
    
    # Clean up multiple consecutive empty lines elsewhere (but preserve intentional ones in code)
    # Only clean up in the import region area
    new_lines = []
    prev_empty = False
    for i, line in enumerate(lines):
        is_empty = line.strip() == ''
        # Allow multiple empty lines in actual code (after the import block)
        if i <= after_import_idx + 2:
            if is_empty and prev_empty:
                continue  # Skip consecutive empty lines near imports
        new_lines.append(line)
        prev_empty = is_empty
    
    new_content = '\n'.join(new_lines)
    
    try:
        with open(kt_file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    except Exception as e:
        print(f"Error writing {kt_file_path}: {e}")
        return False

def main():
    root_dir = '/workspace'
    kt_files = get_all_kt_files(root_dir)
    
    print(f"Found {len(kt_files)} Kotlin files")
    
    success_count = 0
    fail_count = 0
    
    for i, kt_file in enumerate(kt_files):
        if fix_file(kt_file):
            success_count += 1
        
        if (i + 1) % 100 == 0:
            print(f"Processed {i + 1}/{len(kt_files)} files...")
    
    print(f"\nDone! Processed {success_count} files.")

if __name__ == '__main__':
    main()
