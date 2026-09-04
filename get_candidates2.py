import os

candidates = []

for root, _, files in os.walk('app/src/main/java/org/ole/planet/myplanet/ui'):
    for file in files:
        if file.endswith('.kt') and not file.endswith('Test.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                lines = f.readlines()
                for i, line in enumerate(lines):
                    if 'size' in line and 'showNoData' in line:
                        candidates.append(f"{filepath}:{i+1}: {line.strip()}")

for c in candidates:
    print(c)
