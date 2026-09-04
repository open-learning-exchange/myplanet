import os

candidates = []

for root, _, files in os.walk('app/src/main/java/org/ole/planet/myplanet'):
    for file in files:
        if file.endswith('.kt') and not file.endswith('Test.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                lines = f.readlines()
                for i, line in enumerate(lines):
                    if 'fun count' in line and 'Dao' in file:
                        candidates.append(f"{filepath}:{i+1}: {line.strip()}")
                    if 'fun get' in line and 'Count' in line and 'Repository' in file:
                        candidates.append(f"{filepath}:{i+1}: {line.strip()}")
                    if 'Dao' in file and 'Query' in line and 'COUNT' in line:
                        candidates.append(f"{filepath}:{i+1}: {line.strip()}")

for c in candidates:
    print(c)
