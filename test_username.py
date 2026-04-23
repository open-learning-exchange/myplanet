import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Wait, `val userName = JsonUtils.getString("name", jsonDoc)`
# In `createGuestUser`:
#             val `object` = JsonObject()
#             `object`.addProperty("_id", "guest_$username")
#             `object`.addProperty("name", username)
#             `object`.addProperty("firstName", username)
# ...
#             val user = populateUser(`object`, realm)
# In `populateUser`:
#             val userName = JsonUtils.getString("name", jsonDoc)
# That's all fine.
# Where is the bug?
