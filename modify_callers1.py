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

# TransactionSyncManager
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt',
    'import org.ole.planet.myplanet.repository.UserRepository',
    'import org.ole.planet.myplanet.repository.UserSyncHelper',
    {
        'private val userRepository: UserRepository,': 'private val userRepository: UserRepository,\n    private val userSyncHelper: UserSyncHelper,',
        'userRepository.bulkInsertUsersFromSync(mRealm, arr, sharedPrefManager.rawPreferences)': 'userSyncHelper.bulkInsertUsersFromSync(mRealm, arr)',
        'userRepository.bulkInsertAchievementsFromSync(mRealm, arr)': 'userSyncHelper.bulkInsertAchievementsFromSync(mRealm, arr)'
    }
)

# UploadToShelfService
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt',
    'import org.ole.planet.myplanet.repository.UserRepository',
    'import org.ole.planet.myplanet.repository.UserSyncHelper',
    {
        'private val userRepository: UserRepository,': 'private val userRepository: UserRepository,\n    private val userSyncHelper: UserSyncHelper,',
        'userRepository.getShelfData(': 'userSyncHelper.getShelfData('
    }
)

# LoginSyncManager
modify_file(
    'app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt',
    'import org.ole.planet.myplanet.repository.UserRepository',
    '',
    {
        'userRepository.saveUser(jsonDoc, sharedPrefManager.rawPreferences)': 'userRepository.saveUser(jsonDoc)'
    }
)
