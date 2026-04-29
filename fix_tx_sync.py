import re
tx_sync_manager_file = "./app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt"
with open(tx_sync_manager_file, 'r') as f:
    content = f.read()

content = content.replace("private val teamsRepository: dagger.Lazy<TeamSyncRepository>", "private val teamsRepository: dagger.Lazy<org.ole.planet.myplanet.repository.TeamSyncRepository>")
with open(tx_sync_manager_file, 'w') as f:
    f.write(content)
