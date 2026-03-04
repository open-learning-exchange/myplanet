import re

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt", "r") as f:
    text = f.read()

if "mRealm = databaseService.createManagedRealmInstance()" in text:
    text = text.replace("mRealm = databaseService.createManagedRealmInstance()", "mRealm = requireRealmInstance()")

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt", "w") as f:
    f.write(text)
