import re
file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Let's check `saveUserInfoPref`
match = re.search(r'suspend fun saveUserInfoPref[\s\S]*?\}', content)
if match:
    print(match.group(0))
