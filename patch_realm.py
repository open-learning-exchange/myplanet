import re

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", "r") as f:
    text = f.read()

# Find onCreate
if "mRealm = databaseService.createManagedRealmInstance()" in text and "override fun onCreate" in text:
    text = text.replace("mRealm = databaseService.createManagedRealmInstance()", "// mRealm initialized in onViewCreated")

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", "w") as f:
    f.write(text)
