import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt'
with open(file_path, 'r') as file:
    content = file.read()

if 'userSyncHelper' not in content:
    content = content.replace(
        'lateinit var userRepository: UserRepository',
        'lateinit var userRepository: UserRepository\n\n    @javax.inject.Inject\n    lateinit var userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper'
    )

with open(file_path, 'w') as file:
    file.write(content)
