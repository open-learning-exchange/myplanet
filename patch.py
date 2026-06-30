import sys

with open('./app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'private val selectedItemsMap = LinkedHashMap<String, org.ole.planet.myplanet.model.RealmMyLibrary>()',
    'private val selectedItemsMap = LinkedHashMap<String, org.ole.planet.myplanet.model.ResourceItem>()'
)

with open('./app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt', 'w') as f:
    f.write(content)
