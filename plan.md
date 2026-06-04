1. Modify `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`:
   - Change `every { mockResults.iterator() } returns resultList.toMutableList().iterator()` to `every { mockResults.iterator() } answers { resultList.toMutableList().iterator() }`.
   - Add a test for `getAllLibraries` ensuring that no `equalTo` filter is applied: `verify(exactly = 0) { mockQuery.equalTo(any<String>(), any<Any>()) }`.
   - Add a test `search with isMyCourseLib true and userId null returns empty list` to cover the early return branch.
2. Check using `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest" --no-build-cache`.
3. Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
4. Submit the change.
