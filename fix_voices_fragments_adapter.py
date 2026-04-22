import re

def fix_file(file_path):
    with open(file_path, 'r') as file:
        content = file.read()

    content = content.replace('userRepository = userRepository', 'userSyncHelper = userSyncHelper')

    with open(file_path, 'w') as file:
        file.write(content)

fix_file('app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt')
fix_file('app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt')
