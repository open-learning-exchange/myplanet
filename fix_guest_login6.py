file_path = 'app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt'
with open(file_path, 'r') as file:
    content = file.read()

import re
match = re.search(r'val newEmail = JsonUtils.getString[\s\S]*?\}', content)
if match:
    print(match.group(0))
