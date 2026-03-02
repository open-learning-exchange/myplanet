import re

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt", "r") as f:
    text = f.read()

text = text.replace("kotlinx.coroutines.isActive", "isActive")

if "import kotlinx.coroutines.isActive" not in text:
    text = text.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.isActive")

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt", "w") as f:
    f.write(text)

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt", "r") as f:
    text2 = f.read()

if "import org.ole.planet.myplanet.utils.Utilities" not in text2:
    text2 = text2.replace("import org.ole.planet.myplanet.utils.Utilities.toast", "import org.ole.planet.myplanet.utils.Utilities.toast\nimport org.ole.planet.myplanet.utils.Utilities")

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt", "w") as f:
    f.write(text2)

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", "r") as f:
    text3 = f.read()

text3 = text3.replace("protected fun trackDownloadUrls", "fun trackDownloadUrls")

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", "w") as f:
    f.write(text3)
