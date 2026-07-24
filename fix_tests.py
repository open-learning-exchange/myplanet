import os

file_path = "app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt"
with open(file_path, 'r') as f:
    content = f.read()

content = content.replace("            teamDao,\n        )", "            teamDao,\n            userSessionManager\n        )")

with open(file_path, 'w') as f:
    f.write(content)

file_path2 = "app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryLibrarySyncTest.kt"
with open(file_path2, 'r') as f:
    content2 = f.read()

content2 = content2.replace("            mockk<UserDao>(relaxed = true),\n            mockk<TeamDao>(relaxed = true),\n        )", "            mockk<UserDao>(relaxed = true),\n            mockk<TeamDao>(relaxed = true),\n            mockk<org.ole.planet.myplanet.services.UserSessionManager>(relaxed = true)\n        )")

with open(file_path2, 'w') as f:
    f.write(content2)
