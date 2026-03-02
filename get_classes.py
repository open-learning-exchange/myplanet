import re

files = [
    "app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()
        print(file)
        print(re.findall(r'class \w+.*? : .*? \{', content))
