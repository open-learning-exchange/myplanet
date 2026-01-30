### 1. Remove dead code block in RealmSubmission.insert

Lines 114–119 iterate through the `answers` JSON array but do nothing with each element. The identical check on line 121 immediately follows and actually processes the answers into `RealmAnswer` objects, making the first block redundant dead code.

:codex-file-citation[codex-file-citation]{line_range_start=114 line_range_end=119 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L114-L119"}

:::task-stub{title="Remove dead answer-iteration block in RealmSubmission.kt"}
1. Delete lines 114–119 (the no-op `if (submission.has("answers"))` block that iterates but discards every element)
2. Verify the functional answers block at lines 121–144 remains intact
:::

### 2. Fix overwritten field assignment in RealmRating.insert

Line 127 assigns `rating.parentCode` from the `"parentCode"` JSON key, but line 128 immediately overwrites it with the `"planetCode"` value. The second assignment should target `rating.planetCode`, matching the model property declared on line 27.

:codex-file-citation[codex-file-citation]{line_range_start=126 line_range_end=129 path=app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L126-L129"}

:::task-stub{title="Fix parentCode/planetCode assignment in RealmRating.kt"}
1. Change line 128 from `rating.parentCode = JsonUtils.getString("planetCode", act)` to `rating.planetCode = JsonUtils.getString("planetCode", act)`
:::

### 3. Rename getReferencesArray() to a val property in RealmAchievement

The `achievementsArray` on line 25 is already an idiomatic Kotlin `val` property with a custom getter, but `getReferencesArray()` on line 35 is defined as a function. Both compute a `JsonArray` from a `RealmList` in the same way, so the naming should be consistent.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/model/RealmAchievement.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmAchievement.kt#L35-L42"}

:::task-stub{title="Convert getReferencesArray() to val property in RealmAchievement.kt"}
1. Replace `fun getReferencesArray(): JsonArray` with `val referencesArray: JsonArray get() = ...` to match the `achievementsArray` pattern on line 25
2. Update the call site on line 74 from `sub.getReferencesArray()` to `sub.referencesArray`
:::

### 4. Remove duplicate serialization properties in RealmApkLog.serialize

Lines 61–62 add `"androidId"` with `log.createdOn` and `"createdOn"` with `log.createdOn`, then lines 63 and 62 immediately overwrite both keys with the correct values. The first pair of assignments on lines 61–62 are dead writes that obscure intent.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/model/RealmApkLog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmApkLog.kt#L60-L65"}

:::task-stub{title="Remove duplicate addProperty calls in RealmApkLog.serialize"}
1. Delete line 61 (`object.addProperty("androidId", log.createdOn)`) since it is immediately overwritten on line 63 with the correct `NetworkUtils.getUniqueIdentifier()` value
2. Delete the first `object.addProperty("createdOn", log.createdOn)` on line 62 since it is a duplicate of the one on line 60
:::

### 5. Rename backtick-escaped `object` variable to `jsonObject` in RealmTeamTask.serialize

The `serialize` method on line 59 uses Kotlin's backtick-escaped `` `object` `` as a local variable name 14 times. This is a Kotlin reserved keyword and hinders readability. Other serialization methods in the codebase use `jsonObject` or `ob`.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=76 path=app/src/main/java/org/ole/planet/myplanet/model/RealmTeamTask.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmTeamTask.kt#L59-L76"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmTeamTask.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in the `serialize` method (lines 60–75)
:::

### 6. Rename backtick-escaped `object` variable to `jsonObject` in RealmCourseProgress.serializeProgress

The `serializeProgress` method uses `` `object` `` as a local variable for the `JsonObject` being constructed. This should use a descriptive, non-keyword name for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=30 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L30-L41"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmCourseProgress.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in `serializeProgress` (lines 31–40)
2. Replace all occurrences of `` `object` `` with `jsonObject` in `getCourseProgress` (lines 49–53)
:::

### 7. Rename backtick-escaped `object` variable to `jsonObject` in RealmAnswer.createObject

The private `createObject` method uses `` `object` `` as a local variable name, conflicting with Kotlin's `object` keyword. This is the only occurrence in the file.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/model/RealmAnswer.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmAnswer.kt#L44-L57"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmAnswer.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in the `createObject` method (lines 45–56)
:::

### 8. Rename backtick-escaped `object` variable to `jsonObject` in RealmMeetup.serialize

The `serialize` method uses `` `object` `` as a local variable 19 times across lines 117–142. Renaming it improves readability without changing behavior.

:codex-file-citation[codex-file-citation]{line_range_start=116 line_range_end=143 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMeetup.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmMeetup.kt#L116-L143"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmMeetup.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in the `serialize` method (lines 117–142)
:::

### 9. Rename backtick-escaped `object` variable to `jsonObject` in RealmMyPersonal.serialize

The `serialize` method uses both `` `object` `` and the confusingly similar `object1` as local variable names, making the code hard to read.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyPersonal.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmMyPersonal.kt#L26-L44"}

:::task-stub{title="Rename backtick `object` and object1 in RealmMyPersonal.kt"}
1. Replace `` `object` `` with `jsonObject` throughout the `serialize` method (lines 27–43)
2. Rename `object1` on line 37 to `privateForJson` for clarity
:::

### 10. Rename backtick-escaped `object` variable to `jsonObject` in RealmStepExam.serializeExam

The `serializeExam` method uses `` `object` `` as a local variable across lines 96–119, which is a Kotlin reserved keyword.

:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=120 path=app/src/main/java/org/ole/planet/myplanet/model/RealmStepExam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmStepExam.kt#L95-L120"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmStepExam.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in the `serializeExam` method (lines 96–119)
:::

### 11. Rename backtick-escaped `object` variable in RealmExamQuestion.serializeQuestions

The `serializeQuestions` method on line 100 uses `` `object` `` in its loop body, and the `insertCourseStepsExams` caller in RealmMyCourse uses `` `object` `` for exam/survey JSON objects.

:codex-file-citation[codex-file-citation]{line_range_start=100 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/model/RealmExamQuestion.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmExamQuestion.kt#L100-L114"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmExamQuestion.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in the `serializeQuestions` method (lines 103–111)
:::

### 12. Convert getCorrectChoice() to val property in RealmExamQuestion

The `getCorrectChoice()` method on line 32 simply returns the private `correctChoice` field. In Kotlin, this should be exposed as a property rather than a getter function, matching the `correctChoiceArray` property pattern on line 36.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/model/RealmExamQuestion.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmExamQuestion.kt#L32-L34"}

:::task-stub{title="Convert getCorrectChoice() to val property in RealmExamQuestion.kt"}
1. Remove the `private` modifier from the `correctChoice` property on line 22 and make it publicly readable
2. Remove the `getCorrectChoice()` function on lines 32–34
3. Update any call sites that use `getCorrectChoice()` to use `correctChoice` directly
:::

### 13. Extract magic string constant for hardcoded course ID in TakeCourseFragment

The course ID `"4e6b78800b6ad18b4e8b0e1e38a98cac"` appears as a hardcoded string literal on lines 266 and 381, used to apply special step-completion and survey-completion logic. This magic string should be a named constant.

:codex-file-citation[codex-file-citation]{line_range_start=266 line_range_end=266 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L266-L266"}
:codex-file-citation[codex-file-citation]{line_range_start=381 line_range_end=381 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L381-L381"}

:::task-stub{title="Extract magic course ID to constant in TakeCourseFragment.kt"}
1. Add `private const val STEP_LOCKED_COURSE_ID = "4e6b78800b6ad18b4e8b0e1e38a98cac"` to the companion object
2. Replace both literal occurrences on lines 266 and 381 with `STEP_LOCKED_COURSE_ID`
:::

### 14. Rename backtick-escaped `object` parameter to `doc` in RealmCertification.insert

The `insert` method parameter is declared as `` `object`: JsonObject? `` on line 23, using a Kotlin keyword as the parameter name. All model `insert` methods use `act`, `doc`, or `obj` instead.

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCertification.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmCertification.kt#L23-L31"}

:::task-stub{title="Rename backtick `object` parameter to doc in RealmCertification.kt"}
1. Rename the `` `object` `` parameter on line 23 to `doc`
2. Update all references to `` `object` `` inside the method body (lines 24, 29, 30)
:::

### 15. Rename backtick-escaped `object` variable in RealmMyCourse companion methods

The `insertExam` and `insertSurvey` private methods on lines 161–176 use `` `object` `` for the local `JsonObject` variable, inconsistent with the rest of the companion object which uses descriptive names like `myMyCoursesDB`.

:codex-file-citation[codex-file-citation]{line_range_start=161 line_range_end=176 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L161-L176"}

:::task-stub{title="Rename backtick `object` to examJson/surveyJson in RealmMyCourse.kt"}
1. In `insertExam` (line 163), rename `` `object` `` to `examJson`
2. In `insertSurvey` (line 171), rename `` `object` `` to `surveyJson`
3. Update all references within each method body accordingly
:::

### 16. Rename backtick-escaped `object` variable in RealmFeedback.serializeFeedback

The `serializeFeedback` method uses `` `object` `` as its local `JsonObject` variable across 20 references (lines 85–105). Every other model serialization method benefits from renaming this.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=106 path=app/src/main/java/org/ole/planet/myplanet/model/RealmFeedback.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmFeedback.kt#L84-L106"}

:::task-stub{title="Rename backtick `object` to jsonObject in RealmFeedback.kt"}
1. Replace all occurrences of `` `object` `` with `jsonObject` in the `serializeFeedback` method (lines 85–105)
:::

### 17. Convert getRoleAsString, getFullName, and getFullNameWithMiddleName to val properties in RealmUser

Lines 153–167 define three no-argument functions that return computed strings: `getRoleAsString()`, `getFullName()`, and `getFullNameWithMiddleName()`. In Kotlin, these should be `val` properties with custom getters, consistent with the existing `toString()` pattern.

:codex-file-citation[codex-file-citation]{line_range_start=153 line_range_end=167 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/56cfc0d5519bc49022a4ddab114ab844859faf15/app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt#L153-L167"}

:::task-stub{title="Convert getter functions to val properties in RealmUser.kt"}
1. Convert `fun getRoleAsString(): String` (line 153) to `val roleAsString: String get() = ...`
2. Convert `fun getFullName(): String` (line 161) to `val fullName: String get() = ...`
3. Convert `fun getFullNameWithMiddleName(): String` (line 165) to `val fullNameWithMiddleName: String get() = ...`
4. Update all call sites within the same file from method call syntax to property access syntax
:::
