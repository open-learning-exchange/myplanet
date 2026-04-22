import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt'
with open(file_path, 'r') as file:
    content = file.read()

import_statement = "import org.ole.planet.myplanet.repository.UserSyncHelper\n"
if "UserSyncHelper" not in content:
    content = content.replace("import org.ole.planet.myplanet.repository.UserRepositoryImpl\n",
                              "import org.ole.planet.myplanet.repository.UserRepositoryImpl\n" + import_statement)

binds_statement = """
    @Binds
    @Singleton
    abstract fun bindUserSyncHelper(impl: UserRepositoryImpl): UserSyncHelper
"""
if "bindUserSyncHelper" not in content:
    content = content.replace("abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository\n",
                              "abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository\n" + binds_statement)

with open(file_path, 'w') as file:
    file.write(content)
