import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt'
with open(file_path, 'r') as file:
    content = file.read()

import re
match = re.search(r'fun insertIntoUsers[\s\S]*?val newEmail = JsonUtils.getString', content)
if match:
    print(match.group(0))
