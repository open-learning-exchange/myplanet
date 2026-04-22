import re

def modify_file(file_path, old_import, new_import, replacement_dict):
    with open(file_path, 'r') as file:
        content = file.read()

    if old_import and new_import and new_import not in content:
        content = content.replace(old_import, f"{old_import}\n{new_import}")

    for old_str, new_str in replacement_dict.items():
        content = content.replace(old_str, new_str)

    with open(file_path, 'w') as file:
        file.write(content)

# LeadersFragment
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersFragment.kt',
    'import org.ole.planet.myplanet.repository.UserRepository',
    'import org.ole.planet.myplanet.repository.UserSyncHelper',
    {
        'lateinit var userRepository: UserRepository': 'lateinit var userSyncHelper: UserSyncHelper',
        'userRepository.parseLeadersJson(leaders)': 'userSyncHelper.parseLeadersJson(leaders)'
    }
)

# VoicesAdapter
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt',
    '',
    '',
    {
        'private val userRepository: org.ole.planet.myplanet.repository.UserRepository': 'private val userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper',
        'userRepository.parseLeadersJson(raw)': 'userSyncHelper.parseLeadersJson(raw)'
    }
)

# TeamsRepositoryImpl (where parseLeadersJson is used)
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt',
    'import org.ole.planet.myplanet.repository.UserRepository',
    'import org.ole.planet.myplanet.repository.UserSyncHelper',
    {
        'private val userRepository: UserRepository,': 'private val userRepository: UserRepository,\n    private val userSyncHelper: UserSyncHelper,',
        'userRepository.parseLeadersJson(communityLeadersJson)': 'userSyncHelper.parseLeadersJson(communityLeadersJson)'
    }
)

# LoginActivity
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt',
    '',
    '',
    {
        'userRepository.createGuestUser(user.name ?: "", settings)': 'userRepository.createGuestUser(user.name ?: "")',
        'userRepository.createGuestUser(username, settings)': 'userRepository.createGuestUser(username)'
    }
)

# GuestLoginExtensions
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt',
    '',
    '',
    {
        'userRepository.createGuestUser(username, settings)': 'userRepository.createGuestUser(username)'
    }
)
