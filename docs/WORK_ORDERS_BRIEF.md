# Work Orders Brief: myPlanet Refactor Round

Date: 2026-08-19
Base commit: 9c54a0341557a7e7ae4bdc313fd1c97c0cc23b32
Open PRs checked: could not check open PRs

---

### 1. replace member-count list load with the existing count query (roadmap 1+7)
context: RequestsViewModel.kt:39 calls `teamsRepository.getJoinedMembers(teamId).size`, which loads every joined membership row plus one user query per member from Room to produce an Int. The repository already exposes `getJoinedMemberCount(teamId)` backed by SQL COUNT.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt (line 39). Do NOT touch TeamsRepositoryImpl.kt or TeamsRepository.kt.
steps:
1. Open RequestsViewModel.kt at line 39.
2. In RequestsViewModel.kt, replace `teamsRepository.getJoinedMembers(teamId).size` with `teamsRepository.getJoinedMemberCount(teamId)`.
3. Clean up any unused imports if present.
4. Run `./gradlew testDefaultDebugUnitTest` to verify that `RequestsViewModelTest` passes.
5. Confirm that the team requests UI displays the correct joined member count.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; requests screen still shows the correct joined-member count.
size budget: ~2 changed lines, 1 file
out of scope: Do not touch DAO or repository implementations.

---

### 2. optimize hex string formatting in AndroidDecrypter (roadmap 7)
context: AndroidDecrypter.kt:49 uses `String.format("%02x", b)` inside a for loop in `bytesToHex`. `String.format` parses format strings dynamically on every byte iteration, causing high CPU allocation and GC pressure on cryptographic hot paths.
files: app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt (lines 48-52).
steps:
1. Open AndroidDecrypter.kt at line 48.
2. Inspect the `bytesToHex` private function inside the companion object.
3. Replace `String.format("%02x", b)` with direct bitwise character appending: `sb.append(Character.forDigit((b.toInt() shr 4) and 0x0f, 16)).append(Character.forDigit(b.toInt() and 0x0f, 16))`.
4. Run unit tests to confirm encryption and decryption hex outputs remain identical and pass.
5. Verify that `AndroidDecrypterTest` continues to run and pass cleanly.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; hex string generation produces identical lowercase hex results without runtime format string parsing.
size budget: ~5 changed lines, 1 file
out of scope: Do not rewrite AES encryption algorithms or alter method signatures.

---

### 3. replace blocking pref commit with async apply in TransactionSyncManager (roadmap 5+7)
context: TransactionSyncManager.kt:326 calls `sharedPrefManager.rawPreferences.edit().remove(checkpointKey).commit()` on completion of a table sync checkpoint cleanup. Calling `.commit()` synchronously writes preferences to disk on the sync thread, causing unnecessary I/O blocking.
files: app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt (line 326). Do NOT touch SharedPrefManager.kt.
steps:
1. Open TransactionSyncManager.kt at line 326.
2. Locate the checkpoint removal code block in the `syncDb` method.
3. Replace `.commit()` with `.apply()` for asynchronous preference persistence.
4. Run unit tests to verify sync flow behavior.
5. Confirm that sync completion removes checkpoint keys asynchronously without disk read/write blocking.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; sync completion removes checkpoint key asynchronously without disk read/write blocking.
size budget: ~1 changed line, 1 file
out of scope: Do not modify other shared preference logic or alter sync loop structures.

---

### 4. optimize course rating text formatting in CourseRatingUtils (roadmap 7+8)
context: CourseRatingUtils.kt:22 and 46 call `String.format(Locale.getDefault(), "%.2f", averageRating ?: 0f)` to format average course ratings. Using `Locale.getDefault()` creates locale-dependent string output bugs in regions with non-ASCII decimal separators, while `String.format` parses format strings dynamically on UI render paths.
files: app/src/main/java/org/ole/planet/myplanet/utils/CourseRatingUtils.kt (lines 22, 46).
steps:
1. Open CourseRatingUtils.kt at lines 22 and 46.
2. In both `showRating` functions, replace `String.format(Locale.getDefault(), "%.2f", averageRating ?: 0f)` with `String.format(Locale.US, "%.2f", averageRating ?: 0f)`.
3. Verify that rating average formatting produces consistent ASCII decimal formatted floats across all locale configurations.
4. Run unit tests to ensure course rating utilities compile and pass cleanly.
5. Check that course rating displays in course UI elements render formatted float values as expected.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; average rating formatting is locale-invariant and uses Locale.US.
size budget: ~2 changed lines, 1 file
out of scope: Do not modify rating bar views, layout bindings, or rating calculation logic.

---

### 5. replace String.format in EventsDetailFragment time display (roadmap 6+7)
context: EventsDetailFragment.kt:190 formats hours and minutes using `String.format("%02d:%02d", hour, minute)`. Parsing format strings in UI picker callbacks introduces unnecessary overhead when simple string interpolation with `padStart` can be used.
files: app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDetailFragment.kt (line 190).
steps:
1. Open EventsDetailFragment.kt at line 190.
2. Locate the `pickTime` function near line 190.
3. Replace `String.format("%02d:%02d", hour, minute)` with Kotlin string interpolation using `padStart`: `"${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"`.
4. Run unit tests to ensure event time formatting works cleanly.
5. Verify that event detail time pickers display correctly padded time strings in the UI.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; event detail time picker displays correctly formatted time strings.
size budget: ~2 changed lines, 1 file
out of scope: Do not rewrite the event detail UI or convert the fragment to Compose.

---

### 6. replace String.format in TeamCalendarFragment time picker (roadmap 6+7)
context: TeamCalendarFragment.kt:202 uses `String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)` inside a time picker callback. Formatting time digits via string format parsing adds runtime string allocation overhead during time updates.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarFragment.kt (line 202).
steps:
1. Open TeamCalendarFragment.kt at line 202.
2. Locate the `setTimePicker` function near line 202.
3. Replace `String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)` with string interpolation using `padStart`: `"${hourOfDay.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"`.
4. Run unit tests to verify calendar event time updates.
5. Verify that time selection in team calendar event creation dialogs displays properly formatted times.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; calendar time picker updates cleanly without format parsing.
size budget: ~2 changed lines, 1 file
out of scope: Do not touch calendar event data models or view logic outside line 202.

---

### 7. optimize date formatting in MyHealthFragment date picker (roadmap 6+7)
context: MyHealthFragment.kt:90 uses `String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)` to construct date strings in UI date pickers. Replacing `String.format` with padded Kotlin string interpolation eliminates format parsing overhead.
files: app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt (line 90).
steps:
1. Open MyHealthFragment.kt at line 90.
2. Locate the DatePickerDialog callback in `onViewCreated`.
3. Replace `String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)` with Kotlin string interpolation using `padStart`: `"${dayOfMonth.toString().padStart(2, '0')}-${(month + 1).toString().padStart(2, '0')}-${year.toString().padStart(4, '0')}"`.
4. Run unit tests to ensure health date filter formatting is intact.
5. Verify that health record date filters continue to display dates in dd-MM-yyyy format.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; health date picker formats date strings identically.
size budget: ~2 changed lines, 1 file
out of scope: Do not alter health record repositories or health data models.

---

### 8. optimize date formatting in AddHealthActivity (roadmap 6+7)
context: AddHealthActivity.kt:51 formats user-selected dates with `String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)`. Replacing `String.format` with zero-padded string interpolation avoids format parsing overhead.
files: app/src/main/java/org/ole/planet/myplanet/ui/health/AddHealthActivity.kt (line 51).
steps:
1. Open AddHealthActivity.kt at line 51.
2. Locate the `datePickerClickListener` initialization in `onCreate`.
3. Replace `String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)` with Kotlin string interpolation using `padStart`: `"${dayOfMonth.toString().padStart(2, '0')}-${(month + 1).toString().padStart(2, '0')}-${year.toString().padStart(4, '0')}"`.
4. Run unit tests to verify health record creation.
5. Verify that the birth date input field in the health record creation screen displays zero-padded date strings correctly.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; date string formatting in health activity remains accurate.
size budget: ~2 changed lines, 1 file
out of scope: Do not touch form validation or health activity lifecycle methods.

---

### 9. optimize ISO date formatting in EditAchievementFragment (roadmap 3+7)
context: EditAchievementFragment.kt:325 and 415 use `String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)` to construct ISO date strings. Using direct Kotlin string padding removes runtime format string parsing in achievement date selectors.
files: app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt (lines 325, 415).
steps:
1. Open EditAchievementFragment.kt at lines 325 and 415.
2. Locate `showAddAchievementAlert` (line 325) and `onDateSet` (line 415).
3. Replace `String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)` in both places with string interpolation using `padStart`: `"${i.toString().padStart(4, '0')}-${(i1 + 1).toString().padStart(2, '0')}-${i2.toString().padStart(2, '0')}"`.
4. Run unit tests to ensure achievement date handling passes.
5. Verify that ISO date fields in achievement forms format as yyyy-MM-dd.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; ISO achievement date strings match exact original format.
size budget: ~4 changed lines, 1 file
out of scope: Do not modify achievement sync logic or UserEntity models.

---

### 10. optimize ISO date formatting in BecomeMemberActivity (roadmap 3+7)
context: BecomeMemberActivity.kt:60 formats date of birth using `String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)`. Direct string padding avoids runtime format parsing when users select birth dates during registration.
files: app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt (line 60).
steps:
1. Open BecomeMemberActivity.kt at line 60.
2. Locate `showDatePickerDialog` near line 60.
3. Replace `String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)` with Kotlin string interpolation using `padStart`: `"${i.toString().padStart(4, '0')}-${(i1 + 1).toString().padStart(2, '0')}-${i2.toString().padStart(2, '0')}"`.
4. Run unit tests to ensure member registration date formatting works seamlessly.
5. Verify that date of birth in new member registration displays formatted ISO date strings.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; date of birth formatting remains exact ISO yyyy-MM-dd.
size budget: ~2 changed lines, 1 file
out of scope: Do not touch member registration network calls or form submit logic.
