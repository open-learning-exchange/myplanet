import re
upload_configs_file = "./app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt"
with open(upload_configs_file, 'r') as f:
    content = f.read()

content = content.replace("private val teamsRepository: dagger.Lazy<TeamSyncRepository>", "private val teamsRepository: dagger.Lazy<org.ole.planet.myplanet.repository.TeamSyncRepository>")
with open(upload_configs_file, 'w') as f:
    f.write(content)
