#!/bin/bash

# CoursesRepository.kt
sed -i '/suspend fun getCourseRatings/a \    suspend fun filterCoursesByTag(query: String, tags: List<RealmTag>, isMyCourseLib: Boolean, userId: String?): List<RealmMyCourse>' app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepository.kt

# CoursesRepositoryImpl.kt
cat << 'INNER_EOF' >> app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt

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
INNER_EOF

# Fix the trailing brace we just appended to
sed -i 's/^}$//' app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt | true
# Actually let's use replace_with_git_merge_diff for CoursesRepositoryImpl.kt and BaseRecyclerFragment.kt later to be safe
