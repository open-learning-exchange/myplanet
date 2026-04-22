import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Add userRepository back into ReplyActivity
# Because it needs it to call getUserById
if 'lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository' not in content:
    content = content.replace('lateinit var userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper', 'lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository\n    @javax.inject.Inject\n    lateinit var userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper')


with open(file_path, 'w') as file:
    file.write(content)
