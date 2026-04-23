file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt'
with open(file_path, 'r') as file:
    content = file.read()

import re
match = re.search(r'saveUsers\(username, "", "guest"\)', content)
if match:
    print(match.group(0))
