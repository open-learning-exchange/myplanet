import re

news_dao_path = "app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt"
with open(news_dao_path, "r") as f:
    content = f.read()

content = content.replace(
    """@Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE) OR viewIn LIKE '%' || '"_id":"' || :teamId || '"' || '%') ORDER BY time DESC")""",
    """@Query("SELECT * FROM news WHERE (replyTo IS NULL OR replyTo = '') AND ((viewableBy = 'teams' COLLATE NOCASE AND viewableId = :teamId COLLATE NOCASE) OR viewIn LIKE :teamPattern ESCAPE '\\\\') ORDER BY time DESC")"""
)

content = content.replace("suspend fun getTopLevelByTeam(teamId: String): List<News>", "suspend fun getTopLevelByTeam(teamId: String, teamPattern: String): List<News>")
content = content.replace("fun getTopLevelByTeamFlow(teamId: String): Flow<List<News>>", "fun getTopLevelByTeamFlow(teamId: String, teamPattern: String): Flow<List<News>>")

with open(news_dao_path, "w") as f:
    f.write(content)


voices_repo_path = "app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt"
with open(voices_repo_path, "r") as f:
    content = f.read()

team_pattern_method = """
    private fun teamIdPattern(teamId: String): String {
        val escaped = teamId
            .replace("\\\\", "\\\\\\\\")
            .replace("%", "\\\\%")
            .replace("_", "\\\\_")
        return "%\\\"_id\\\":\\\"$escaped\\\"%"
    }
"""

if "teamIdPattern" not in content:
    content = content.replace("    override suspend fun getNewsByTeamId(teamId: String): List<News> {",
                              team_pattern_method + "\n    override suspend fun getNewsByTeamId(teamId: String): List<News> {")

content = content.replace("newsDao.getTopLevelByTeam(teamId)", "newsDao.getTopLevelByTeam(teamId, teamIdPattern(teamId))")
content = content.replace("newsDao.getTopLevelByTeamFlow(teamId)", "newsDao.getTopLevelByTeamFlow(teamId, teamIdPattern(teamId))")

with open(voices_repo_path, "w") as f:
    f.write(content)

test_path = "app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt"
with open(test_path, "r") as f:
    content = f.read()

content = content.replace("coEvery { newsDao.getTopLevelByTeam(any()) }", "coEvery { newsDao.getTopLevelByTeam(any(), any()) }")
content = content.replace("every { newsDao.getTopLevelByTeamFlow(any()) }", "every { newsDao.getTopLevelByTeamFlow(any(), any()) }")

with open(test_path, "w") as f:
    f.write(content)
