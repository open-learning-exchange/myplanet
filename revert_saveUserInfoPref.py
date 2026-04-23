import re

def process_file(file_path, old_str, new_str):
    with open(file_path, 'r') as file:
        content = file.read()
    content = content.replace(old_str, new_str)
    with open(file_path, 'w') as file:
        file.write(content)

# Revert ProcessUserDataActivity
process_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt',
    'suspend fun saveUserInfoPref(password: String?, user: RealmUser?) {\n        withContext(dispatcherProvider.io) {\n            SecurePrefs.saveCredentials(this@ProcessUserDataActivity, settings, user?.name, password)\n        }\n        this.settings = settings',
    'suspend fun saveUserInfoPref(settings: android.content.SharedPreferences, password: String?, user: org.ole.planet.myplanet.model.RealmUser?) {\n        this.settings = settings\n        withContext(dispatcherProvider.io) {\n            SecurePrefs.saveCredentials(this@ProcessUserDataActivity, settings, user?.name, password)\n        }'
)

# Revert LoginActivity
process_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt',
    'saveUserInfoPref("", model)',
    'saveUserInfoPref(settings, "", model)'
)

# Revert GuestLoginExtensions
process_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt',
    'saveUserInfoPref("", model)',
    'saveUserInfoPref(settings, "", model)'
)

# Revert SyncActivity
process_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt',
    'saveUserInfoPref(password, user)',
    'saveUserInfoPref(this.settings, password, user)'
)
