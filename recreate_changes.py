import os
import re

# Step 1: ChatRepository
chat_repo = 'app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt'
with open(chat_repo, 'r') as f:
    content = f.read()
if 'fun insertChatHistoryBatch' not in content:
    content = content.replace(
        'suspend fun insertChatHistoryList(chats: List<JsonObject>)',
        'suspend fun insertChatHistoryList(chats: List<JsonObject>)\n    fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)'
    )
    with open(chat_repo, 'w') as f: f.write(content)

chat_impl = 'app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt'
with open(chat_impl, 'r') as f:
    content = f.read()
if 'override fun insertChatHistoryBatch' not in content:
    batch_method = """    override fun insertChatHistoryBatch(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val docs = mutableListOf<JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            docs.add(jsonDoc)
        }
        docs.forEach { insertChatHistory(realm, it) }
    }
"""
    content = content.replace('private fun insertChatHistory', batch_method + '\n    private fun insertChatHistory')
    with open(chat_impl, 'w') as f: f.write(content)

# Step 2: Domain Repositories
def insert_method_in_repo(interface_path, impl_path, method_signature, method_impl, impl_import=None):
    with open(interface_path, 'r') as f:
        ic = f.read()
    if method_signature.split('(')[0] not in ic:
        ic = re.sub(r'}\s*$', f'    {method_signature}\n}}\n', ic)
        with open(interface_path, 'w') as f: f.write(ic)
    with open(impl_path, 'r') as f:
        ic = f.read()
    if method_signature.split('(')[0] not in ic:
        if impl_import and impl_import not in ic:
            ic = ic.replace('import ', f'import {impl_import}\nimport ', 1)
        # Find the last closing brace of the class
        # Assuming typical structure, it's just before any top-level functions or EOF
        # So we can search for the last '}' that closes the class
        # This is a bit tricky, but we know the exact structures now

        # fallback generic approach:
        # let's just insert before the last '}' in the file, EXCEPT for certain files
        pass

def get_standard_impl(method_name, model_class):
    return f"""    override fun {method_name}(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {{
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {{
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {{
                documentList.add(jsonDoc)
            }}
        }}
        documentList.forEach {{ jsonDoc ->
            {model_class}.insert(realm, jsonDoc)
        }}
    }}"""

repos = {
    'Tags': ('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmTag'),
    'Submissions': ('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmSubmission'),
    'Health': ('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmHealthExamination'),
    'Progress': ('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmCourseProgress'),
    'Community': ('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmMeetup'),
    'Notifications': ('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmNotification')
}
base_path = 'app/src/main/java/org/ole/planet/myplanet/repository'

def do_insert_impl(path, code):
    with open(path, 'r') as f: c = f.read()
    class_end = c.rfind('}')
    c = c[:class_end] + code + c[class_end:]
    with open(path, 'w') as f: f.write(c)

for r, (m, c_val) in repos.items():
    interface_path = f'{base_path}/{r}Repository.kt'
    with open(interface_path, 'r') as f: ic = f.read()
    if m not in ic:
        ic = re.sub(r'}\s*$', f'    fun {m}(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n}}\n', ic)
        with open(interface_path, 'w') as f: f.write(ic)
    do_insert_impl(f'{base_path}/{r}RepositoryImpl.kt', '\n' + get_standard_impl(m, c_val) + '\n')

# RatingsRepository
rat_iface = f'{base_path}/RatingsRepository.kt'
with open(rat_iface, 'r') as f: ic = f.read()
if 'bulkInsertFromSync' not in ic[:ic.rfind('}')]:
    ic = ic[:ic.rfind('}')] + '    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n' + ic[ic.rfind('}'):]
    with open(rat_iface, 'w') as f: f.write(ic)
rat_impl = f'{base_path}/RatingsRepositoryImpl.kt'
with open(rat_impl, 'r') as f: c = f.read()
class_end = c.find('}\n\ninternal fun serializeRating')
if class_end != -1:
    c = c[:class_end] + '\n' + get_standard_impl('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmRating') + '\n' + c[class_end:]
    with open(rat_impl, 'w') as f: f.write(c)

# ActivitiesRepository
act_iface = f'{base_path}/ActivitiesRepository.kt'
with open(act_iface, 'r') as f: ic = f.read()
if 'bulkInsertLoginActivitiesFromSync' not in ic:
    ic = ic[:ic.rfind('}')] + '    fun bulkInsertLoginActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n' + ic[ic.rfind('}'):]
    with open(act_iface, 'w') as f: f.write(ic)
act_impl = f'{base_path}/ActivitiesRepositoryImpl.kt'
with open(act_impl, 'r') as f: c = f.read()
class_end = c.find('}\n\ninternal fun serializeResourceActivities')
act_code = """    override fun bulkInsertLoginActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            insertActivity(realm, jsonDoc)
        }
    }"""
if class_end != -1:
    c = c[:class_end] + '\n' + act_code + '\n' + c[class_end:]
    with open(act_impl, 'w') as f: f.write(c)

# UserRepository
user_iface = f'{base_path}/UserRepository.kt'
with open(user_iface, 'r') as f: ic = f.read()
if 'bulkInsertAchievementsFromSync' not in ic:
    ic = ic[:ic.rfind('}')] + '    fun bulkInsertAchievementsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n    fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences)\n' + ic[ic.rfind('}'):]
    with open(user_iface, 'w') as f: f.write(ic)
user_impl = f'{base_path}/UserRepositoryImpl.kt'
user_pop = """    override fun bulkInsertUsersFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray, settings: android.content.SharedPreferences) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            populateUser(jsonDoc, realm, settings)
        }
    }"""
do_insert_impl(user_impl, '\n' + get_standard_impl('bulkInsertAchievementsFromSync', 'org.ole.planet.myplanet.model.RealmAchievement') + '\n' + user_pop + '\n')

# TeamsRepository
teams_iface = f'{base_path}/TeamsRepository.kt'
with open(teams_iface, 'r') as f: ic = f.read()
if 'bulkInsertFromSync' not in ic:
    ic = ic[:ic.rfind('}')] + '    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n    fun bulkInsertTasksFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n    fun bulkInsertTeamActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n' + ic[ic.rfind('}'):]
    with open(teams_iface, 'w') as f: f.write(ic)
teams_impl = f'{base_path}/TeamsRepositoryImpl.kt'
teams_act = """    override fun bulkInsertTeamActivitiesFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            insertTeamLog(realm, jsonDoc)
        }
    }"""
do_insert_impl(teams_impl, '\n' + get_standard_impl('bulkInsertFromSync', 'org.ole.planet.myplanet.model.RealmMyTeam') + '\n' + get_standard_impl('bulkInsertTasksFromSync', 'org.ole.planet.myplanet.model.RealmTeamTask') + '\n' + teams_act + '\n')

# SurveysRepository
surveys_iface = f'{base_path}/SurveysRepository.kt'
with open(surveys_iface, 'r') as f: ic = f.read()
if 'bulkInsertExamsFromSync' not in ic:
    ic = ic[:ic.rfind('}')] + '    fun bulkInsertExamsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n' + ic[ic.rfind('}'):]
    with open(surveys_iface, 'w') as f: f.write(ic)
surveys_impl = f'{base_path}/SurveysRepositoryImpl.kt'
surveys_ex = """    override fun bulkInsertExamsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            org.ole.planet.myplanet.model.RealmStepExam.insertCourseStepsExams("", "", jsonDoc, realm)
        }
    }"""
do_insert_impl(surveys_impl, '\n' + surveys_ex + '\n')

# CoursesRepository
course_iface = f'{base_path}/CoursesRepository.kt'
with open(course_iface, 'r') as f: ic = f.read()
if 'bulkInsertFromSync' not in ic:
    ic = ic[:ic.rfind('}')] + '    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n    fun bulkInsertCertificationsFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray)\n' + ic[ic.rfind('}'):]
    with open(course_iface, 'w') as f: f.write(ic)

course_impl = f'{base_path}/CoursesRepositoryImpl.kt'
with open(course_impl, 'r') as f: c = f.read()
if 'private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager' not in c:
    c = c.replace(
        'private val ratingsRepository: RatingsRepository\n)',
        'private val ratingsRepository: RatingsRepository,\n    private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager\n)'
    )
    # The standard impl
    code_c1 = """    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            org.ole.planet.myplanet.model.RealmMyCourse.insert(realm, jsonDoc, sharedPrefManager)
        }
    }"""
    code_c2 = get_standard_impl('bulkInsertCertificationsFromSync', 'org.ole.planet.myplanet.model.RealmCertification')

    class_end = c.rfind('}')
    c = c[:class_end] + '\n' + code_c1 + '\n' + code_c2 + '\n' + c[class_end:]
    with open(course_impl, 'w') as f: f.write(c)

# Step 3 & 4: TransactionSyncManager and ServiceModule
sync_file = 'app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt'
with open(sync_file, 'r') as f: content = f.read()
deps = [
    "private val tagsRepository: org.ole.planet.myplanet.repository.TagsRepository",
    "private val ratingsRepository: org.ole.planet.myplanet.repository.RatingsRepository",
    "private val submissionsRepository: org.ole.planet.myplanet.repository.SubmissionsRepository",
    "private val coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository",
    "private val communityRepository: org.ole.planet.myplanet.repository.CommunityRepository",
    "private val healthRepository: org.ole.planet.myplanet.repository.HealthRepository",
    "private val progressRepository: org.ole.planet.myplanet.repository.ProgressRepository",
    "private val surveysRepository: org.ole.planet.myplanet.repository.SurveysRepository"
]
if "tagsRepository" not in content:
    match = re.search(r'(@ApplicationScope private val applicationScope: CoroutineScope\s*\))', content)
    if match:
        content = content.replace(match.group(1), ",\n    ".join(deps) + ",\n    " + match.group(1))

when_block = """                        when (table) {
                            "tablet_users" -> userRepository.bulkInsertUsersFromSync(mRealm, arr, sharedPrefManager.rawPreferences)
                            "exams" -> surveysRepository.bulkInsertExamsFromSync(mRealm, arr)
                            "team_activities" -> teamsRepository.get().bulkInsertTeamActivitiesFromSync(mRealm, arr)
                            "login_activities" -> activitiesRepository.bulkInsertLoginActivitiesFromSync(mRealm, arr)
                            "tags" -> tagsRepository.bulkInsertFromSync(mRealm, arr)
                            "ratings" -> ratingsRepository.bulkInsertFromSync(mRealm, arr)
                            "submissions" -> submissionsRepository.bulkInsertFromSync(mRealm, arr)
                            "courses" -> coursesRepository.bulkInsertFromSync(mRealm, arr)
                            "achievements" -> userRepository.bulkInsertAchievementsFromSync(mRealm, arr)
                            "teams" -> teamsRepository.get().bulkInsertFromSync(mRealm, arr)
                            "tasks" -> teamsRepository.get().bulkInsertTasksFromSync(mRealm, arr)
                            "meetups" -> communityRepository.bulkInsertFromSync(mRealm, arr)
                            "health" -> healthRepository.bulkInsertFromSync(mRealm, arr)
                            "certifications" -> coursesRepository.bulkInsertCertificationsFromSync(mRealm, arr)
                            "courses_progress" -> progressRepository.bulkInsertFromSync(mRealm, arr)
                            "notifications" -> notificationsRepository.bulkInsertFromSync(mRealm, arr)
                            else -> android.util.Log.e("SyncPerf", "Unknown table: $table")
                        }
                        org.ole.planet.myplanet.model.RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)"""
if 'insertDocs(arr, mRealm, table)' in content:
    content = content.replace('insertDocs(arr, mRealm, table)', when_block)

chat_history_old = """                } else if (table == "chat_history") {
                    val insertStartTime = System.currentTimeMillis()
                    val docs = mutableListOf<JsonObject>()
                    for (j in arr) {
                        var jsonDoc = j.asJsonObject
                        jsonDoc = getJsonObject("doc", jsonDoc)
                        docs.add(jsonDoc)
                    }
                    chatRepository.insertChatHistoryList(docs)
                    val insertDuration = System.currentTimeMillis() - insertStartTime
                    org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                        "insert_batch",
                        table,
                        insertDuration,
                        arr.size()
                    )"""

chat_history_new = """                } else if (table == "chat_history") {
                    databaseService.executeTransactionAsync { mRealm: Realm ->
                        val insertStartTime = System.currentTimeMillis()
                        chatRepository.insertChatHistoryBatch(mRealm, arr)
                        val insertDuration = System.currentTimeMillis() - insertStartTime
                        org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                            "insert_batch",
                            table,
                            insertDuration,
                            arr.size()
                        )
                    }"""
if chat_history_old in content:
    content = content.replace(chat_history_old, chat_history_new)

syncDb_end = "        }\n    }\n\n"
syncDb_idx = content.find("suspend fun syncDb")
end_of_syncDb = content.find(syncDb_end, syncDb_idx) + len(syncDb_end)
start_of_syncNotification = content.find("suspend fun syncNotificationReads")
if syncDb_idx != -1 and start_of_syncNotification != -1:
    content = content[:end_of_syncDb] + "    " + content[start_of_syncNotification:]

with open(sync_file, 'w') as f:
    f.write(content)

# ServiceModule
sm_file = 'app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt'
with open(sm_file, 'r') as f: c = f.read()
sm_deps = """        tagsRepository: org.ole.planet.myplanet.repository.TagsRepository,
        ratingsRepository: org.ole.planet.myplanet.repository.RatingsRepository,
        submissionsRepository: org.ole.planet.myplanet.repository.SubmissionsRepository,
        coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository,
        communityRepository: org.ole.planet.myplanet.repository.CommunityRepository,
        healthRepository: org.ole.planet.myplanet.repository.HealthRepository,
        progressRepository: org.ole.planet.myplanet.repository.ProgressRepository,
        surveysRepository: org.ole.planet.myplanet.repository.SurveysRepository,"""
if 'tagsRepository: org.ole.planet.myplanet.repository.TagsRepository' not in c:
    c = c.replace('notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository,', 'notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository,\n' + sm_deps)
    c = c.replace('notificationsRepository, scope', 'notificationsRepository, tagsRepository, ratingsRepository, submissionsRepository, coursesRepository, communityRepository, healthRepository, progressRepository, surveysRepository, scope')
    with open(sm_file, 'w') as f: f.write(c)

print("Recreate changes done")
