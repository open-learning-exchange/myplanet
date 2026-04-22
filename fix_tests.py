import re

# Fix TeamsRepositoryImplTest.kt
file_path = 'app/src/test/java/org/ole/planet/myplanet/repository/TeamsRepositoryImplTest.kt'
with open(file_path, 'r') as file:
    content = file.read()
# Replace `userRepository = mockUserRepository,` with `userRepository = mockUserRepository,\n            userSyncHelper = mockk(),`
content = content.replace('userRepository = mockUserRepository,', 'userRepository = mockUserRepository,\n            userSyncHelper = mockk(relaxed = true),')
with open(file_path, 'w') as file:
    file.write(content)

# Fix UserRepositoryImplTest.kt
file_path = 'app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt'
with open(file_path, 'r') as file:
    content = file.read()
# Replace `repository.saveUser(jsonDoc, sharedPreferences)` with `repository.saveUser(jsonDoc)`
content = content.replace('repository.saveUser(jsonDoc, sharedPreferences)', 'repository.saveUser(jsonDoc)')
with open(file_path, 'w') as file:
    file.write(content)

# Fix TransactionSyncManagerTest.kt
file_path = 'app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt'
with open(file_path, 'r') as file:
    content = file.read()
# Replace `userRepository = mockUserRepository,` with `userRepository = mockUserRepository,\n            userSyncHelper = mockk(),`
content = content.replace('userRepository = mockUserRepository,', 'userRepository = mockUserRepository,\n            userSyncHelper = mockk(relaxed = true),')
with open(file_path, 'w') as file:
    file.write(content)
