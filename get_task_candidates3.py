import os

candidates = []

for root, _, files in os.walk('app/src/main/java/org/ole/planet/myplanet'):
    for file in files:
        if file.endswith('.kt') and not file.endswith('Test.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                lines = f.readlines()
                for i, line in enumerate(lines):
                    if 'size' in line and ('ViewModel' in file or 'Repository' in file or 'Fragment' in file):
                        candidates.append(f"{filepath}:{i+1}: {line.strip()}")

# Look for places where a list is loaded but only size is used.
results = []
for c in candidates:
    if '.size' in c and ('tvMessage' in c or 'tvNodata' in c or 'getString(' in c or 'max = ' in c or 'count =' in c or 'text =' in c):
        results.append(c)

for r in results:
    print(r)
