import re

with open('app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt', 'r') as file:
    content = file.read()

# Fix broken closing braces from the sed command mistake
content = content.replace("io.mockk.verify { dispatcherProvider.default \n    \n\n    @Test", "io.mockk.verify { dispatcherProvider.default }\n    }\n\n    @Test")
content = content.replace("block(mockRealm)\n        \n\n        val news1", "block(mockRealm)\n        }\n\n        val news1")
content = content.replace("viewIn = null\n        \n        val news2", "viewIn = null\n        }\n        val news2")
content = content.replace("viewIn = \"[{\\\"_id\\\":\\\"user1\\\"}]\"\n        \n        val news3", "viewIn = \"[{\\\"_id\\\":\\\"user1\\\"}]\"\n        }\n        val news3")
content = content.replace("viewIn = \"[{\\\"_id\\\":\\\"user2\\\"}]\"\n        \n\n        every", "viewIn = \"[{\\\"_id\\\":\\\"user2\\\"}]\"\n        }\n\n        every")
content = content.replace("org.junit.Assert.assertEquals(\"[{\\\"_id\\\":\\\"user1\\\"}]\", result[1].viewIn)\n    \n\n    @Test", "org.junit.Assert.assertEquals(\"[{\\\"_id\\\":\\\"user1\\\"}]\", result[1].viewIn)\n    }\n\n    @Test")
content = content.replace("viewableId = \"team1\"\n        \n        val news2", "viewableId = \"team1\"\n        }\n        val news2")
content = content.replace("viewIn = \"[{\\\"_id\\\":\\\"team1\\\"}]\"\n        \n        val news3", "viewIn = \"[{\\\"_id\\\":\\\"team1\\\"}]\"\n        }\n        val news3")
content = content.replace("viewIn = \"[{\\\"_id\\\":\\\"team2\\\"}]\"\n        \n\n        every", "viewIn = \"[{\\\"_id\\\":\\\"team2\\\"}]\"\n        }\n\n        every")
content = content.replace("org.junit.Assert.assertEquals(\"[{\\\"_id\\\":\\\"team1\\\"}]\", result[1].viewIn)\n    \n\n    @Test", "org.junit.Assert.assertEquals(\"[{\\\"_id\\\":\\\"team1\\\"}]\", result[1].viewIn)\n    }\n\n    @Test")

# Fix mockk verify
content = content.replace("io.mockk.verify(exactly = 1) { mockRealmQuery.beginGroup() \n        io.mockk.verify(exactly = 1) { mockRealmQuery.equalTo(\"viewableBy\", \"teams\", io.realm.Case.INSENSITIVE) \n        io.mockk.verify(exactly = 1) { mockRealmQuery.equalTo(\"viewableId\", \"team1\", io.realm.Case.INSENSITIVE) \n        io.mockk.verify(exactly = 1) { mockRealmQuery.endGroup() \n        io.mockk.verify(exactly = 1) { mockRealmQuery.or() \n        io.mockk.verify(exactly = 1) { mockRealmQuery.contains(\"viewIn\", \"\\\"_id\\\":\\\"team1\\\"\", io.realm.Case.INSENSITIVE) \n    \n\n    @Test", "io.mockk.verify(exactly = 1) { mockRealmQuery.beginGroup() }\n        io.mockk.verify(exactly = 1) { mockRealmQuery.equalTo(\"viewableBy\", \"teams\", io.realm.Case.INSENSITIVE) }\n        io.mockk.verify(exactly = 1) { mockRealmQuery.equalTo(\"viewableId\", \"team1\", io.realm.Case.INSENSITIVE) }\n        io.mockk.verify(exactly = 1) { mockRealmQuery.endGroup() }\n        io.mockk.verify(exactly = 1) { mockRealmQuery.or() }\n        io.mockk.verify(exactly = 1) { mockRealmQuery.contains(\"viewIn\", \"\\\"_id\\\":\\\"team1\\\"\", io.realm.Case.INSENSITIVE) }\n    }\n\n    @Test")

content = content.replace("every { iterator() } returns mutableListOf<RealmNews>().iterator()\n            \n        \n\n        every", "every { iterator() } returns mutableListOf<RealmNews>().iterator()\n            }\n        }\n\n        every")

content = content.replace("io.mockk.verify(exactly = 1) { reply2.deleteFromRealm() \n        io.mockk.verify(exactly = 1) { reply1.deleteFromRealm() \n        io.mockk.verify(exactly = 1) { realmResultsTarget.deleteAllFromRealm() \n    \n\n    @Test", "io.mockk.verify(exactly = 1) { reply2.deleteFromRealm() }\n        io.mockk.verify(exactly = 1) { reply1.deleteFromRealm() }\n        io.mockk.verify(exactly = 1) { realmResultsTarget.deleteAllFromRealm() }\n    }\n\n    @Test")

content = content.replace("io.mockk.verify(exactly = 1) { mockLabels.add(\"testLabel\") \n    \n\n    @Test", "io.mockk.verify(exactly = 1) { mockLabels.add(\"testLabel\") }\n    }\n\n    @Test")

content = content.replace("io.mockk.verify(exactly = 1) { mockLabels.remove(\"testLabel\") \n    \n\n    @Test", "io.mockk.verify(exactly = 1) { mockLabels.remove(\"testLabel\") }\n    }\n\n    @Test")

content = content.replace("block(mockRealm)\n        \n        coEvery { databaseService.realmInstance }", "block(mockRealm)\n        }\n        coEvery { databaseService.realmInstance }")

content = content.replace("assertEquals(testUserId, user?.id)\n    \n\n", "assertEquals(testUserId, user?.id)\n    }\n}")

with open('app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt', 'w') as file:
    file.write(content)
