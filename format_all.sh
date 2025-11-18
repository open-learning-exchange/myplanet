#!/bin/bash
set -e

while IFS= read -r FILE_PATH; do
  if [ -f "$FILE_PATH" ]; then
    # Create temporary files for processing
    PACKAGE_FILE=$(mktemp)
    IMPORTS_FILE=$(mktemp)
    BODY_FILE=$(mktemp)
    TMP_FILE=$(mktemp)

    # 1. Isolate package, imports, and body
    grep "^package " "$FILE_PATH" > "$PACKAGE_FILE" || true # Continue if no package line
    grep "^import " "$FILE_PATH" > "$IMPORTS_FILE" || true # Continue if no imports

    # Isolate the body by removing package and import lines.
    # The sed command is used to remove leading blank lines.
    grep -v -e "^package " -e "^import " "$FILE_PATH" | sed '/./,$!d' > "$BODY_FILE"

    # 2. Sort imports
    sort -u "$IMPORTS_FILE" -o "$IMPORTS_FILE"

    # 3. Reconstruct the file with proper spacing
    if [ -s "$PACKAGE_FILE" ]; then
        cat "$PACKAGE_FILE" > "$TMP_FILE"
        echo "" >> "$TMP_FILE"
    fi

    if [ -s "$IMPORTS_FILE" ]; then
        cat "$IMPORTS_FILE" >> "$TMP_FILE"
        echo "" >> "$TMP_FILE"
    fi

    cat "$BODY_FILE" >> "$TMP_FILE"

    # 4. Replace the original file
    mv "$TMP_FILE" "$FILE_PATH"

    # 5. Clean up temporary files
    rm "$PACKAGE_FILE" "$IMPORTS_FILE" "$BODY_FILE"
  fi
done < kotlin_files.txt
