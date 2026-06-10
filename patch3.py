import re

with open('app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt', 'r') as f:
    text = f.read()

pattern = r'    @Test\n    fun testHasUserCompletedSync\(\) = testScope\.runTest \{.*?assertEquals\(false, result2\)\n    \}'

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

text = re.sub(pattern, new_test, text, flags=re.DOTALL)

with open('app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt', 'w') as f:
    f.write(text)
