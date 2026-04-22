import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt'
with open(file_path, 'r') as file:
    content = file.read()

# In ServiceModule, need to inject UserSyncHelper into UploadToShelfService and TransactionSyncManager
# UploadToShelfService
# @Provides fun provideUploadToShelfService(..., userRepository: UserRepository, healthRepository: HealthRepository, ...)
# Let's just find `userRepository: org.ole.planet.myplanet.repository.UserRepository,` and add `userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper,`

content = content.replace(
    'userRepository: org.ole.planet.myplanet.repository.UserRepository,',
    'userRepository: org.ole.planet.myplanet.repository.UserRepository,\n        userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper,'
)

content = content.replace(
    'userRepository, healthRepository,',
    'userRepository, userSyncHelper, healthRepository,'
)
content = content.replace(
    'userRepository, activitiesRepository,',
    'userRepository, userSyncHelper, activitiesRepository,'
)

with open(file_path, 'w') as file:
    file.write(content)
