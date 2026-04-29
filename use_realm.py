import re

teams_repo_impl_file = "./app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt"
with open(teams_repo_impl_file, 'r') as f:
    content = f.read()

content = content.replace("class TeamsRepositoryImpl @Inject constructor(", "class TeamsRepositoryImpl @Inject constructor(")
content = content.replace(": RealmRepository(databaseService, realmDispatcher), TeamsRepository {", ": RealmRepository(databaseService, realmDispatcher), TeamsRepository, TeamSyncRepository {")

with open(teams_repo_impl_file, 'w') as f:
    f.write(content)
