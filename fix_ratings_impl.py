import re
rat_impl = 'app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt'
with open(rat_impl, 'r') as f: c = f.read()

impl_code = """
    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            org.ole.planet.myplanet.model.RealmRating.insert(realm, jsonDoc)
        }
    }
"""

if 'override fun bulkInsertFromSync' not in c:
    class_end = c.rfind('}')
    c = c[:class_end] + impl_code + c[class_end:]
    with open(rat_impl, 'w') as f: f.write(c)

print("Fixed RatingsRepositoryImpl")
