file_path = 'app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

import re
match = re.search(r'Too many arguments', content, re.DOTALL)
# Error was:
# e: file:///app/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:153:63 Cannot infer type for type parameter 'T'. Specify it explicitly.
# e: file:///app/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:153:63 Too many arguments for 'suspend fun saveUser(jsonDoc: JsonObject?, key: String?, iv: String?): RealmUser?'.

lines = content.split('\n')
for i, line in enumerate(lines):
    if i == 152:
        print(f"{i+1}: {line}")
