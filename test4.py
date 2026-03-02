import re
with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", 'r') as f:
    print(re.findall(r'fun \w+', f.read()))
