import re

teams_repository_file = "./app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt"
with open(teams_repository_file, 'r') as f:
    content = f.read()

content = re.sub(r'    fun serializeTeamActivities\(log: RealmTeamLog, context: Context\): JsonObject\n', '', content)
content = re.sub(r'    fun insertMyTeam\(realm: io\.realm\.Realm, doc: com\.google\.gson\.JsonObject\)\n', '', content)
content = re.sub(r'    fun bulkInsertFromSync\(realm: io\.realm\.Realm, jsonArray: com\.google\.gson\.JsonArray\)\n', '', content)
content = re.sub(r'    fun bulkInsertTasksFromSync\(realm: io\.realm\.Realm, jsonArray: com\.google\.gson\.JsonArray\)\n', '', content)
content = re.sub(r'    fun bulkInsertTeamActivitiesFromSync\(realm: io\.realm\.Realm, jsonArray: com\.google\.gson\.JsonArray\)\n', '', content)

with open(teams_repository_file, 'w') as f:
    f.write(content)
