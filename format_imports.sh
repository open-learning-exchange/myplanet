#!/bin/bash

while IFS= read -r filepath; do
    if [ ! -f "$filepath" ]; then
        continue
    fi

    # Create temporary files for processing
    before_imports=$(mktemp)
    imports=$(mktemp)
    after_imports=$(mktemp)

    # Use awk to separate the file into three parts
    awk '
        BEGIN { part=1 }
        /^import / {
            if (part==1) { part=2 }
            print > "'$imports'"
            next
        }
        {
            if (part==2) { part=3 }
            if (part==1) { print > "'$before_imports'" }
            else if (part==3) { print > "'$after_imports'" }
        }
    ' "$filepath"

    # Check if there are any imports to process
    if [ -s "$imports" ]; then
        # Sort the imports
        sort -u "$imports" -o "$imports"

        # Trim leading/trailing blank lines from before/after parts
        sed -i -e '/./,$!d' -e :a -e '/^\n*$/{$d;N;};/\n$/ba' "$before_imports"
        sed -i -e '/./,$!d' -e :a -e '/^\n*$/{$d;N;};/\n$/ba' "$after_imports"

        # Reconstruct the file
        cat "$before_imports" > "$filepath"
        if [ -s "$before_imports" ]; then
            echo "" >> "$filepath"
        fi
        cat "$imports" >> "$filepath"
        if [ -s "$after_imports" ]; then
            echo "" >> "$filepath"
            cat "$after_imports" >> "$filepath"
        fi
    else
        # If no imports, just put the original content back
        cat "$before_imports" > "$filepath"
        cat "$after_imports" >> "$filepath"
    fi

    # Clean up temporary files
    rm "$before_imports" "$imports" "$after_imports"

done < kotlin_files.txt
