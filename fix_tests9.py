file_path = 'app/src/test/java/org/ole/planet/myplanet/repository/TeamsRepositoryImplTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

content = content.replace(
    'mockUserRepository\n        )',
    'mockUserRepository,\n            mockk<UserSyncHelper>(relaxed = true)\n        )'
)
# Make sure UserSyncHelper is imported
if 'import org.ole.planet.myplanet.repository.UserSyncHelper' not in content:
    content = content.replace(
        'import org.ole.planet.myplanet.repository.UserRepository',
        'import org.ole.planet.myplanet.repository.UserRepository\nimport org.ole.planet.myplanet.repository.UserSyncHelper'
    )

with open(file_path, 'w') as file:
    file.write(content)
