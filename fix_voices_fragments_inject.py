import re

def fix_file(file_path):
    with open(file_path, 'r') as file:
        content = file.read()

    if '@javax.inject.Inject' not in content.split('userSyncHelper')[0][-100:]:
        content = content.replace(
            '    lateinit var userRepository: UserRepository',
            '    lateinit var userRepository: UserRepository\n    @javax.inject.Inject\n    lateinit var userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper'
        )

    with open(file_path, 'w') as file:
        file.write(content)

fix_file('app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt')
fix_file('app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt')
