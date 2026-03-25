sed -i 's/users = fetchAndSaveTeamMembers(selectedTeamId!!)/users = teamsRepository.getJoinedMembersAndSave(selectedTeamId!!)/' app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt
sed -i '/private suspend fun fetchAndSaveTeamMembers(teamId: String) = withContext(Dispatchers.IO) {/,/    }/d' app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt
