import re

# 1. Create TeamSyncRepository.kt and extract methods from TeamsRepository.kt
teams_repository_file = "./app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt"
with open(teams_repository_file, 'r') as f:
    content = f.read()

interface_content = """interface TeamSyncRepository {
    suspend fun insertTeamLog(json: JsonObject)
    suspend fun insertTeamLogs(logs: List<JsonObject>)
    fun serializeTeamActivities(log: RealmTeamLog, context: Context): JsonObject
    fun insertMyTeam(doc: com.google.gson.JsonObject)
    fun bulkInsertFromSync(jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTasksFromSync(jsonArray: com.google.gson.JsonArray)
    fun bulkInsertTeamActivitiesFromSync(jsonArray: com.google.gson.JsonArray)
}"""

content = re.sub(r'    suspend fun insertTeamLog\(json: JsonObject\)\n', '', content)
content = re.sub(r'    suspend fun insertTeamLogs\(logs: List<JsonObject>\)\n', '', content)
content = re.sub(r'    fun serializeTeamActivities\(log: RealmTeamLog, context: Context\): JsonObject\n', '', content)
content = re.sub(r'    fun insertMyTeam\(realm: io\.realm\.Realm, doc: com\.google\.gson\.JsonObject\)\n', '', content)
content = re.sub(r'    fun bulkInsertFromSync\(realm: io\.realm\.Realm, jsonArray: com\.google\.gson\.JsonArray\)\n', '', content)
content = re.sub(r'    fun bulkInsertTasksFromSync\(realm: io\.realm\.Realm, jsonArray: com\.google\.gson\.JsonArray\)\n', '', content)
content = re.sub(r'    fun bulkInsertTeamActivitiesFromSync\(realm: io\.realm\.Realm, jsonArray: com\.google\.gson\.JsonArray\)\n', '', content)

with open(teams_repository_file, 'w') as f:
    f.write(content)

with open("./app/src/main/java/org/ole/planet/myplanet/repository/TeamSyncRepository.kt", 'w') as f:
    f.write("package org.ole.planet.myplanet.repository\n\nimport android.content.Context\nimport com.google.gson.JsonObject\nimport org.ole.planet.myplanet.model.RealmTeamLog\n\n" + interface_content + "\n")

# 2. Update TeamsRepositoryImpl.kt
teams_repo_impl_file = "./app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt"
with open(teams_repo_impl_file, 'r') as f:
    content = f.read()

content = content.replace("class TeamsRepositoryImpl @Inject constructor(", "class TeamsRepositoryImpl @Inject constructor(")
content = content.replace(": RealmRepository(databaseService, realmDispatcher), TeamsRepository {", ": RealmRepository(databaseService, realmDispatcher), TeamsRepository, TeamSyncRepository {")

methods_to_replace = {
    "    override fun insertMyTeam(realm: Realm, doc: JsonObject) {": """    override fun insertMyTeam(doc: JsonObject) {
        val currentRealm = io.realm.Realm.getDefaultInstance()
        try {
            if (currentRealm.isInTransaction) {
                insertMyTeamInternal(currentRealm, doc)
            } else {
                currentRealm.executeTransaction { insertMyTeamInternal(it, doc) }
            }
        } finally {
            currentRealm.close()
        }
    }
    private fun insertMyTeamInternal(realm: Realm, doc: JsonObject) {""",

    "    override fun bulkInsertFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray) {": """    override fun bulkInsertFromSync(jsonArray: com.google.gson.JsonArray) {
        val currentRealm = io.realm.Realm.getDefaultInstance()
        try {
            if (currentRealm.isInTransaction) {
                bulkInsertFromSyncInternal(currentRealm, jsonArray)
            } else {
                currentRealm.executeTransaction { bulkInsertFromSyncInternal(it, jsonArray) }
            }
        } finally {
            currentRealm.close()
        }
    }
    private fun bulkInsertFromSyncInternal(realm: Realm, jsonArray: com.google.gson.JsonArray) {""",

    "    override fun bulkInsertTasksFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray) {": """    override fun bulkInsertTasksFromSync(jsonArray: com.google.gson.JsonArray) {
        val currentRealm = io.realm.Realm.getDefaultInstance()
        try {
            if (currentRealm.isInTransaction) {
                bulkInsertTasksFromSyncInternal(currentRealm, jsonArray)
            } else {
                currentRealm.executeTransaction { bulkInsertTasksFromSyncInternal(it, jsonArray) }
            }
        } finally {
            currentRealm.close()
        }
    }
    private fun bulkInsertTasksFromSyncInternal(realm: Realm, jsonArray: com.google.gson.JsonArray) {""",

    "    override fun bulkInsertTeamActivitiesFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray) {": """    override fun bulkInsertTeamActivitiesFromSync(jsonArray: com.google.gson.JsonArray) {
        val currentRealm = io.realm.Realm.getDefaultInstance()
        try {
            if (currentRealm.isInTransaction) {
                bulkInsertTeamActivitiesFromSyncInternal(currentRealm, jsonArray)
            } else {
                currentRealm.executeTransaction { bulkInsertTeamActivitiesFromSyncInternal(it, jsonArray) }
            }
        } finally {
            currentRealm.close()
        }
    }
    private fun bulkInsertTeamActivitiesFromSyncInternal(realm: Realm, jsonArray: com.google.gson.JsonArray) {"""
}

for old, new in methods_to_replace.items():
    content = content.replace(old, new)

content = content.replace("insertMyTeam(realm, jsonDoc)", "insertMyTeamInternal(realm, jsonDoc)")

with open(teams_repo_impl_file, 'w') as f:
    f.write(content)

# 3. Update SyncManager, TransactionSyncManager, UploadConfigs, ServiceModule, RepositoryModule, tests
sync_manager_file = "./app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt"
with open(sync_manager_file, 'r') as f:
    content = f.read()
content = content.replace("teamsRepository.insertMyTeam(realmTx, doc)", "teamsRepository.insertMyTeam(doc)")
content = content.replace("private val teamsRepository: org.ole.planet.myplanet.repository.TeamsRepository", "private val teamsRepository: org.ole.planet.myplanet.repository.TeamSyncRepository")
with open(sync_manager_file, 'w') as f:
    f.write(content)

tx_sync_manager_file = "./app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt"
with open(tx_sync_manager_file, 'r') as f:
    content = f.read()
content = "import org.ole.planet.myplanet.repository.TeamSyncRepository\n" + content
content = content.replace("teamsRepository.get().bulkInsertTeamActivitiesFromSync(mRealm, arr)", "teamsRepository.get().bulkInsertTeamActivitiesFromSync(arr)")
content = content.replace("teamsRepository.get().bulkInsertFromSync(mRealm, arr)", "teamsRepository.get().bulkInsertFromSync(arr)")
content = content.replace("teamsRepository.get().bulkInsertTasksFromSync(mRealm, arr)", "teamsRepository.get().bulkInsertTasksFromSync(arr)")
content = content.replace("private val teamsRepository: Lazy<TeamsRepository>", "private val teamsRepository: dagger.Lazy<TeamSyncRepository>")
with open(tx_sync_manager_file, 'w') as f:
    f.write(content)

upload_configs_file = "./app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt"
with open(upload_configs_file, 'r') as f:
    content = f.read()
content = "import org.ole.planet.myplanet.repository.TeamSyncRepository\n" + content
content = content.replace("private val teamsRepository: Lazy<TeamsRepository>", "private val teamsRepository: dagger.Lazy<TeamSyncRepository>")
content = content.replace("serializer = UploadSerializer.WithContext { log, context -> serializeTeamActivities(log, context) },", "serializer = UploadSerializer.WithContext { log, context -> teamsRepository.get().serializeTeamActivities(log, context) },")
content = re.sub(r'    private fun serializeTeamActivities\(log: RealmTeamLog, context: android.content.Context\): com.google.gson.JsonObject \{[\s\S]*?return ob\n    \}\n', '', content)
with open(upload_configs_file, 'w') as f:
    f.write(content)

service_module_file = "./app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt"
with open(service_module_file, 'r') as f:
    content = f.read()
content = content.replace("teamsRepository: org.ole.planet.myplanet.repository.TeamsRepository\n    ): SyncManager {", "teamsRepository: org.ole.planet.myplanet.repository.TeamSyncRepository\n    ): SyncManager {")
content = content.replace("teamsRepository: dagger.Lazy<org.ole.planet.myplanet.repository.TeamsRepository>,\n        notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository,", "teamsRepository: dagger.Lazy<org.ole.planet.myplanet.repository.TeamSyncRepository>,\n        notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository,")
with open(service_module_file, 'w') as f:
    f.write(content)

repository_module_file = "./app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt"
with open(repository_module_file, 'r') as f:
    content = f.read()
content = content.replace("@Binds\n    @Singleton\n    abstract fun bindTeamsRepository(impl: TeamsRepositoryImpl): TeamsRepository", "@Binds\n    @Singleton\n    abstract fun bindTeamsRepository(impl: TeamsRepositoryImpl): TeamsRepository\n\n    @Binds\n    @Singleton\n    abstract fun bindTeamSyncRepository(impl: TeamsRepositoryImpl): org.ole.planet.myplanet.repository.TeamSyncRepository")
with open(repository_module_file, 'w') as f:
    f.write(content)

test1_file = "./app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt"
with open(test1_file, 'r') as f:
    content = f.read()
content = content.replace("private val teamsRepository: Lazy<TeamsRepository>", "private val teamsRepository: Lazy<org.ole.planet.myplanet.repository.TeamSyncRepository>")
with open(test1_file, 'w') as f:
    f.write(content)
