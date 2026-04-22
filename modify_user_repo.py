import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Remove methods
methods_to_remove = [
    r'\s*fun populateUser\(.*?\): RealmUser\?',
    r'\s*fun parseLeadersJson\(.*?\): List<RealmUser>',
    r'\s*fun bulkInsertAchievementsFromSync\(.*?\)',
    r'\s*fun bulkInsertUsersFromSync\(.*?\)',
    r'\s*suspend fun getShelfData\(.*?\): JsonObject'
]

for pattern in methods_to_remove:
    content = re.sub(pattern, '', content)

# Modify createGuestUser
content = re.sub(r'suspend fun createGuestUser\(username: String, settings: SharedPreferences\): RealmUser\?', r'suspend fun createGuestUser(username: String): RealmUser?', content)

# Modify saveUser
content = re.sub(r'suspend fun saveUser\(jsonDoc: JsonObject\?, settings: SharedPreferences, key: String\? = null, iv: String\? = null\): RealmUser\?', r'suspend fun saveUser(jsonDoc: JsonObject?, key: String? = null, iv: String? = null): RealmUser?', content)

with open(file_path, 'w') as file:
    file.write(content)
