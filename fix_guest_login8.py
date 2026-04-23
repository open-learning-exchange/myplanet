file_path = 'app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt'
with open(file_path, 'r') as file:
    content = file.read()

import re
match = re.search(r'val newLevel = JsonUtils.getString[\s\S]*?fun getUserImage', content)
if match:
    print(match.group(0))
