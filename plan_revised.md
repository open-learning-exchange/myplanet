1. **Update `CoursesRepository.kt`:** Add the `filterCoursesByTag` method signature to the interface:
```bash
sed -i '/suspend fun getCourseRatings/a \    suspend fun filterCoursesByTag(query: String, tags: List<RealmTag>, isMyCourseLib: Boolean, userId: String?): List<RealmMyCourse>' app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt
```

2. **Verify `CoursesRepository.kt` modification:** Check that the signature was inserted:
```bash
grep -n "filterCoursesByTag" app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt
```

3. **Update `CoursesRepositoryImpl.kt`:** Add the `filterCoursesByTag` implementation and `normalizeText` helper using `replace_with_git_merge_diff` with this exact block:
```
<<<<<<< SEARCH
    override suspend fun getCourseRatings(userId: String?): HashMap<String?, com.google.gson.JsonObject> {
        return ratingsRepository.getCourseRatings(userId)
    }
}
=======
    override suspend fun getCourseRatings(userId: String?): HashMap<String?, com.google.gson.JsonObject> {
        return ratingsRepository.getCourseRatings(userId)
    }

    override suspend fun filterCoursesByTag(
        query: String,
        tags: List<RealmTag>,
        isMyCourseLib: Boolean,
        userId: String?
    ): List<RealmMyCourse> {
        return withRealm { realm ->
            val data = realm.where(RealmMyCourse::class.java).findAll()

            var list: List<RealmMyCourse> = if (query.isEmpty()) {
                realm.copyFromRealm(data)
            } else {
                val queryParts = query.split(" ").filterNot { it.isEmpty() }
                val normalizedQueryParts = queryParts.map { normalizeText(it) }
                val normalizedQuery = normalizeText(query)
                val startsWithQuery = mutableListOf<RealmMyCourse>()
                val containsQuery = mutableListOf<RealmMyCourse>()

                for (item in data) {
                    val title = item.courseTitle?.let { normalizeText(it) } ?: continue

                    if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                        startsWithQuery.add(item)
                    } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                        containsQuery.add(item)
                    }
                }
                val filteredData = startsWithQuery + containsQuery
                realm.copyFromRealm(filteredData)
            }

            list = if (isMyCourseLib) {
                getMyCourses(userId, list)
            } else {
                RealmMyCourse.getAllCourses(userId, list)
            }

            if (tags.isEmpty()) {
                return@withRealm list
            }

            val tagIds = tags.mapNotNull { it.id }.toTypedArray()
            val linkedCourseIds = realm.where(RealmTag::class.java)
                .equalTo("db", "courses")
                .`in`("tagId", tagIds)
                .findAll()
                .mapNotNull { it.linkId }
                .toSet()

            val courses = mutableListOf<RealmMyCourse>()
            list.forEach { course ->
                if (linkedCourseIds.contains(course.courseId) && !courses.contains(course)) {
                    courses.add(course)
                }
            }
            courses
        }
    }

    private fun normalizeText(str: String): String {
        return java.text.Normalizer.normalize(str.lowercase(java.util.Locale.getDefault()), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
>>>>>>> REPLACE
```

4. **Verify `CoursesRepositoryImpl.kt` modification:**
```bash
grep -n "filterCoursesByTag" app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt
```

5. **Update `BaseRecyclerFragment.kt`:** Simplify `filterCourseByTag` to delegate logic to `CoursesRepository`:
```
<<<<<<< SEARCH
    suspend fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
        if (tags.isEmpty() && s.isEmpty()) {
            return applyCourseFilter(filterRealmMyCourseList(getList(RealmMyCourse::class.java) as List<RealmMyCourse>))
        }
        var list = getData(s, RealmMyCourse::class.java)
        list = if (isMyCourseLib) {
            coursesRepository.getMyCourses(model?.id, list)
        } else {
            getAllCourses(model?.id, list)
        }
        if (tags.isEmpty()) {
            return list
        }

        val tagIds = tags.mapNotNull { it.id }.toTypedArray()
        val linkedCourseIds = mRealm.where(RealmTag::class.java)
            .equalTo("db", "courses")
            .`in`("tagId", tagIds)
            .findAll()
            .mapNotNull { it.linkId }
            .toSet()

        val courses = RealmList<RealmMyCourse>()
        list.forEach { course ->
            if (linkedCourseIds.contains(course.courseId) && !courses.contains(course)) {
                courses.add(course)
            }
        }
        return applyCourseFilter(courses)
    }
=======
    suspend fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
        if (tags.isEmpty() && s.isEmpty()) {
            return applyCourseFilter(filterRealmMyCourseList(getList(RealmMyCourse::class.java) as List<RealmMyCourse>))
        }
        val list = coursesRepository.filterCoursesByTag(s, tags, isMyCourseLib, model?.id)
        return applyCourseFilter(list)
    }
>>>>>>> REPLACE
```

6. **Verify `BaseRecyclerFragment.kt` modification:**
```bash
cat app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt | grep -A 10 "filterCourseByTag"
```

7. **Compile and Test:**
```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.*"
```

8. **Pre-commit checks:** Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
9. **Submit changes:** Commit and submit code.
