import re

file_path = 'app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Let's fix TransactionSyncManager constructor arguments in TransactionSyncManagerTest.kt
# TransactionSyncManager now takes userSyncHelper
content = content.replace(
    'userRepository = mockUserRepository,',
    'userRepository = mockUserRepository,\n            userSyncHelper = mockk(),'
)
# If it's already there but the order is wrong because I used replace without looking at the exact text, let's fix it by parsing or manually writing it.
# Actually let's look at the constructor in TransactionSyncManager.
# class TransactionSyncManager @Inject constructor(
#     ...
#     private val userRepository: UserRepository,
#     private val userSyncHelper: UserSyncHelper,
#     private val activitiesRepository: ActivitiesRepository,
#     private val teamsRepository: Lazy<TeamsRepository>,
#     ...
# )

with open(file_path, 'w') as file:
    file.write(content)
