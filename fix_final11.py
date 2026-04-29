import re

teams_repository_file = "./app/src/main/java/org/ole/planet/myplanet/repository/TeamSyncRepository.kt"
with open(teams_repository_file, 'r') as f:
    content = f.read()

content = content.replace("fun insertMyTeam(realm: io.realm.Realm, doc: com.google.gson.JsonObject)", "fun insertMyTeam(doc: com.google.gson.JsonObject)")
content = content.replace("fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)", "fun bulkInsertFromSync(jsonArray: com.google.gson.JsonArray)")
content = content.replace("fun bulkInsertTasksFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)", "fun bulkInsertTasksFromSync(jsonArray: com.google.gson.JsonArray)")
content = content.replace("fun bulkInsertTeamActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)", "fun bulkInsertTeamActivitiesFromSync(jsonArray: com.google.gson.JsonArray)")

with open(teams_repository_file, 'w') as f:
    f.write(content)

# And in teams_repo_impl_file change `override fun insertMyTeam(realm: Realm, doc: JsonObject)` to `override fun insertMyTeam(doc: JsonObject)`
teams_repo_impl_file = "./app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt"
with open(teams_repo_impl_file, 'r') as f:
    content = f.read()

content = content.replace("override fun insertMyTeam(realm: Realm, doc: JsonObject) {", "override fun insertMyTeam(doc: JsonObject) {")
content = content.replace("override fun bulkInsertFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray) {", "override fun bulkInsertFromSync(jsonArray: com.google.gson.JsonArray) {")
content = content.replace("override fun bulkInsertTasksFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray) {", "override fun bulkInsertTasksFromSync(jsonArray: com.google.gson.JsonArray) {")
content = content.replace("override fun bulkInsertTeamActivitiesFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray) {", "override fun bulkInsertTeamActivitiesFromSync(jsonArray: com.google.gson.JsonArray) {")

with open(teams_repo_impl_file, 'w') as f:
    f.write(content)

# And in sync_manager_file
sync_manager_file = "./app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt"
with open(sync_manager_file, 'r') as f:
    content = f.read()
content = content.replace("teamsRepository.insertMyTeam(realmTx, doc)", "teamsRepository.insertMyTeam(doc)")
with open(sync_manager_file, 'w') as f:
    f.write(content)

# And in tx_sync_manager_file
tx_sync_manager_file = "./app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt"
with open(tx_sync_manager_file, 'r') as f:
    content = f.read()
content = content.replace("teamsRepository.get().bulkInsertTeamActivitiesFromSync(mRealm, arr)", "teamsRepository.get().bulkInsertTeamActivitiesFromSync(arr)")
content = content.replace("teamsRepository.get().bulkInsertFromSync(mRealm, arr)", "teamsRepository.get().bulkInsertFromSync(arr)")
content = content.replace("teamsRepository.get().bulkInsertTasksFromSync(mRealm, arr)", "teamsRepository.get().bulkInsertTasksFromSync(arr)")
with open(tx_sync_manager_file, 'w') as f:
    f.write(content)
