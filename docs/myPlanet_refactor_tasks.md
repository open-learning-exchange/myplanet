date: 2025-02-14 · base commit: HEAD · open PRs checked: could not check

### 1. replace member-count list load with the existing count query (roadmap 1+7)
context: `RequestsViewModel.kt` lines 39-40 load both `teamsRepository.getRequestedMembers(teamId)` and `teamsRepository.getJoinedMemberCount(teamId)`. This `getJoinedMemberCount` uses a `COUNT` query underneath. In `BellDashboardFragment.kt` lines 212/218, `pendingSurveys.size` is used to display a string count. `SubmissionDao` already has `countPendingSurveys`.
files: app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt
steps:
1. Update `BellDashboardViewModel.kt` to also emit a `count` or pre-formatted size in `SurveyPrompt` instead of passing the entire `Submission` list if only the count is needed, OR locally cache `val surveyCount = pendingSurveys.size` before passing it to formatting. Wait, better: use `val count = pendingSurveys.size` locally in `BellDashboardFragment` to avoid redundant property lookups.
2. In `BellDashboardFragment.kt:207`, declare `val surveyCount = pendingSurveys.size`.
3. Replace all 4 instances of `pendingSurveys.size` in the string formatting blocks with `surveyCount`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; the bell dashboard renders the survey reminder text accurately.
size budget: ~6 changed lines, 1 file.
out of scope: Do not touch view model logic or repositories.
---
### 2. use ApplicationContext to remove MainApplication.context from CourseDetailViewModel (roadmap 4, 9/10)
context: CourseDetailViewModel.kt:67 accesses `MainApplication.context` directly to resolve the external files directory. This violates dependency injection principles and prevents zero `android.*` imports in the core platform-free goals.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt. Do NOT touch CourseDetailFragment.
steps:
1. Inject `@ApplicationContext context: Context` via the constructor.
2. Add `import android.content.Context` and `import dagger.hilt.android.qualifiers.ApplicationContext`.
3. Replace `MainApplication.context` on line 67 with `context`.
4. Remove `import org.ole.planet.myplanet.MainApplication`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; course detail screens load markdown images correctly.
size budget: ~6 changed lines, 1 file.
out of scope: Do not modify CourseDetailFragment.
---
### 3. remove MainApplication.context from UserInformationFragment (roadmap 4)
context: UserInformationFragment.kt uses `MainApplication.context` for Toasts on lines 164, 167, 263, 275, 285. Fragments should access context via `requireContext()` instead of using the global static instance.
files: app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt.
steps:
1. Replace all usages of `MainApplication.context` with `requireContext()`.
2. Remove the import for `org.ole.planet.myplanet.MainApplication`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; updating user profile still shows toasts correctly.
size budget: ~10 changed lines, 1 file.
out of scope: Do not touch view model logic.
---
### 4. remove MainApplication.context from BecomeMemberActivity (roadmap 4)
context: BecomeMemberActivity.kt:127 references `MainApplication.context` to show a toast. Activities should simply use `this` or `applicationContext`.
files: app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt.
steps:
1. Replace `MainApplication.context` with `this`.
2. Remove `import org.ole.planet.myplanet.MainApplication`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; toast is successfully shown.
size budget: ~3 changed lines, 1 file.
out of scope: Do not touch any other activities.
---
### 5. optimize exam size property in CourseStepFragment (roadmap 1+7)
context: CourseStepFragment.kt evaluates `exams.size` multiple times in the `hideTestIfNoQuestion` function. Storing the size in a variable eliminates redundant calls.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt.
steps:
1. Add `val examCount = exams.size` to the start of `hideTestIfNoQuestion`.
2. Replace usages of `exams.size` with `examCount`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; take test UI string correctly renders.
size budget: ~5 changed lines, 1 file.
out of scope: Do not touch view models.
---
### 6. cache steps size in TakeCourseFragment (roadmap 1+7)
context: TakeCourseFragment.kt repeatedly calls `steps.size` inside navigation actions (`onClickNext`, `onClickPrevious`, etc). Caching it at the class level avoids multiple evaluations per interaction.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt.
steps:
1. Add `private var stepsCount = 0` to the class fields.
2. Assign `stepsCount = steps.size` right after `steps` is initialized.
3. Replace all `.size` calls on `steps` with `stepsCount` throughout the fragment.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; course navigation still proceeds step-by-step.
size budget: ~20 changed lines, 1 file.
out of scope: Do not touch TakeCourseViewModel.
---
### 7. optimize list allocations in CoursesFragment mapNotNull (roadmap 1+7)
context: CoursesFragment.kt maps and filters lists using intermediate allocations in multiple places, e.g. `selectedItems?.mapNotNull { it?.courseId } ?: emptyList()`.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt.
steps:
1. Replace `mapNotNull { it?.courseId }` with `mapNotNullTo(mutableListOf()) { it?.courseId }` on lines 264 and 272 to bypass intermediate list creation.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; courses select properly.
size budget: ~5 changed lines, 1 file.
out of scope: Do not touch CourseSelectionController.
---
### 8. cache categories size in StorageBreakdownFragment (roadmap 1+7)
context: StorageBreakdownFragment.kt creates primitive arrays sized by `categories.size` on lines 218 and 219. We can cache it once.
files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt.
steps:
1. Create `val categoriesSize = categories.size` inside `calculateStorageSizes`.
2. Replace both occurrences of `categories.size` with `categoriesSize`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; storage page calculates sizing.
size budget: ~5 changed lines, 1 file.
out of scope: Do not touch other fragments.
---
### 9. replace stepMistake.size redundant calls in CoursesProgressAdapter (roadmap 1+7)
context: CoursesProgressAdapter.kt line 49 accesses `stepMistake.size` into a `requiredChildCount` variable, but we should make sure we read it efficiently in the presence of nullable variables.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt.
steps:
1. Assign `val size = stepMistake.size` locally in `showStepMistakes`.
2. Use `size` for `requiredChildCount`.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; course progress grids still display steps accurately.
size budget: ~5 changed lines, 1 file.
out of scope: Do not touch view models.
---
### 10. remove redundant list size query in StorageCategoryDetailFragment (roadmap 7)
context: StorageCategoryDetailFragment.kt line 97 reads `val count = viewModel.uiState.value.items.size` but it isn't completely necessary to retrieve it again if we rely on checked count logic. However, since the method needs it for the confirm prompt, cache the value cleanly.
files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt.
steps:
1. Simplify line 97 by caching it at the top of the delete observer.
2. Ensure `items.size` is only calculated once.
acceptance: ./gradlew testDefaultDebugUnitTest stays green; deleting resources works.
size budget: ~3 changed lines, 1 file.
out of scope: Do not touch StorageCategoryViewModel.