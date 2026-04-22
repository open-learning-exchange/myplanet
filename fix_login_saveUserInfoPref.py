import re

file_paths = ['app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt', 'app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt']
for path in file_paths:
    with open(path, 'r') as file:
        content = file.read()

    content = content.replace('saveUserInfoPref(settings, "", model)', 'saveUserInfoPref("", model)')

    with open(path, 'w') as file:
        file.write(content)
