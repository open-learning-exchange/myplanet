import re

file_path = 'app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

# The constructor in TransactionSyncManagerTest doesn't use named arguments.
# So `userRepository = mockUserRepository,\n            userSyncHelper = mockk(relaxed = true),` just caused a syntax error or we inserted it wrong.
# Let's see: `userRepository` is followed by `activitiesRepository`. So we need to insert `mockk<UserSyncHelper>(relaxed = true)` after `userRepository`!

content = content.replace(
    'userRepository,\n            activitiesRepository,',
    'userRepository,\n            mockk<org.ole.planet.myplanet.repository.UserSyncHelper>(relaxed = true),\n            activitiesRepository,'
)

with open(file_path, 'w') as file:
    file.write(content)
