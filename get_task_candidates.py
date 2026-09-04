import os
import re

candidates = []

# Walk through ui directories to find list-based size checks that could be counts
for root, _, files in os.walk('app/src/main/java/org/ole/planet/myplanet/ui'):
    for file in files:
        if file.endswith('.kt') and not file.endswith('Test.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                lines = f.readlines()
                for i, line in enumerate(lines):
                    if '.size' in line and ('ViewModel' in file or 'Repository' in file or 'Fragment' in file):
                        candidates.append(f"{filepath}:{i+1}: {line.strip()}")

for c in candidates[:50]:
    print(c)
