import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Change signature from saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUser?) to saveUserInfoPref(password: String?, user: RealmUser?)
content = content.replace(
    'suspend fun saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUser?) {',
    'suspend fun saveUserInfoPref(password: String?, user: RealmUser?) {'
)

# And similarly in SyncActivity if present.
with open(file_path, 'w') as file:
    file.write(content)

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt'
with open(file_path, 'r') as file:
    content = file.read()

content = content.replace(
    'saveUserInfoPref(this.settings, password, user)',
    'saveUserInfoPref(password, user)'
)

with open(file_path, 'w') as file:
    file.write(content)
