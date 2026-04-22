import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Add UserSyncHelper to interface
content = content.replace('UserRepository {', 'UserRepository, UserSyncHelper {')

# Modify createGuestUser signature and usages
content = content.replace(
    'override suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser? {',
    'override suspend fun createGuestUser(username: String): RealmUser? {'
)
content = content.replace('val user = populateUser(`object`, realm, settings)', 'val user = populateUser(`object`, realm)')

# Modify saveUser signature and usages
content = content.replace(
    'override suspend fun saveUser(\n        jsonDoc: JsonObject?,\n        settings: SharedPreferences,\n        key: String?,\n        iv: String?,\n    ): RealmUser? {',
    'override suspend fun saveUser(\n        jsonDoc: JsonObject?,\n        key: String?,\n        iv: String?,\n    ): RealmUser? {'
)
content = content.replace('val managedUser = populateUser(jsonDoc, realm, settings)', 'val managedUser = populateUser(jsonDoc, realm)')

content = content.replace('response.body()?.let { saveUser(it, settings) }', 'response.body()?.let { saveUser(it) }')
content = content.replace('saveUser(obj, settings, keyString, iv)', 'saveUser(obj, keyString, iv)')

# Modify populateUser signature and usages
content = content.replace(
    'override fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?, settings: SharedPreferences): RealmUser? {',
    'override fun populateUser(jsonDoc: JsonObject?, mRealm: io.realm.Realm?): RealmUser? {'
)
content = content.replace('migrateGuestUser(realm, id, userName, settings)', 'migrateGuestUser(realm, id, userName, this.settings)')
content = content.replace('migrateGuestUser(mRealm, id, userName, settings)', 'migrateGuestUser(mRealm, id, userName, this.settings)')
content = content.replace('insertIntoUsers(jsonDoc, it, settings)', 'insertIntoUsers(jsonDoc, it, this.settings)')

# Modify bulkInsertUsersFromSync signature and usages
content = content.replace(
    'override fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences) {',
    'override fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {'
)
content = content.replace('populateUser(jsonDoc, realm, settings)', 'populateUser(jsonDoc, realm)')

with open(file_path, 'w') as file:
    file.write(content)
