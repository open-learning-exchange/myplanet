import re

def fix_file(file_path):
    with open(file_path, 'r') as file:
        content = file.read()

    content = content.replace(
        'lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository',
        'lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository\n    @javax.inject.Inject\n    lateinit var userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper'
    )

    # In VoicesFragment.kt and TeamsVoicesFragment.kt, replace userRepository with userSyncHelper in VoicesAdapter instantiation
    content = content.replace('userRepository = userRepository', 'userSyncHelper = userSyncHelper')

    with open(file_path, 'w') as file:
        file.write(content)

fix_file('app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt')
fix_file('app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt')
