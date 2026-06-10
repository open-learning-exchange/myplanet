import os

with open('app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt', 'r') as f:
    text = f.read()

old_test = '''    @Test
    fun testHasUserCompletedSync() = testScope.runTest {
        coEvery {
            repository invoke "count" withArguments listOf(org.ole.planet.myplanet.model.RealmUserChallengeActions::class.java, any<Function1<RealmQuery<org.ole.planet.myplanet.model.RealmUserChallengeActions>, Unit>>())
        } returns 1L

        val result = repository.hasUserCompletedSync("user1")
        advanceUntilIdle()

        assertEquals(true, result)

        coEvery {
            repository invoke "count" withArguments listOf(org.ole.planet.myplanet.model.RealmUserChallengeActions::class.java, any<Function1<RealmQuery<org.ole.planet.myplanet.model.RealmUserChallengeActions>, Unit>>())
        } returns 0L

        val result2 = repository.hasUserCompletedSync("user1")
        advanceUntilIdle()

        assertEquals(false, result2)
    }'''

new_test = '''    @Test
    fun testHasUserCompletedSync() = testScope.runTest {
        val activitiesRepo = mockk<ActivitiesRepository>()
        val localRepository = ProgressRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider,
            { mockCoursesRepository },
            { activitiesRepo }
        )

        coEvery { activitiesRepo.hasUserCompletedSync("user1") } returns true

        val result = localRepository.hasUserCompletedSync("user1")
        advanceUntilIdle()

        assertEquals(true, result)

        coEvery { activitiesRepo.hasUserCompletedSync("user1") } returns false

        val result2 = localRepository.hasUserCompletedSync("user1")
        advanceUntilIdle()

        assertEquals(false, result2)
    }'''

text = text.replace(old_test, new_test)

with open('app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt', 'w') as f:
    f.write(text)
