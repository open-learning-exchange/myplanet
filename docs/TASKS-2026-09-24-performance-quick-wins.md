# myPlanet refactor tasks — performance quick wins round

- date: 2026-09-24
- base commit: 14da1ba6bcec7b8affbf047ac78f16c511f5b357
- open PRs checked: 17435, 17254 (the only PRs opened within the last 7 days; files they touch are off-limits to every task below)

### 1. batch member-visit and membership writes in TeamsRepositoryImpl (roadmap 1+7)

context: `getJoinedMembersWithStats` aggregates team log visits into `visitStatsMap` in one pass, then still calls `activitiesRepository.getLastVisit(member.name)` and `activitiesRepository.getOfflineVisitCount(member.id)` inside `orderedMembers.map` — two extra DAO queries per member. `markMembershipsForLeave` likewise calls `teamDao.deleteById`/`teamDao.upsert` per membership in a `forEach`, while `deleteByIds` and `upsertAll` already exist on the DAO.
files: app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt (the `orderedMembers.map` block ~line 1023 and `markMembershipsForLeave` ~line 1286), app/src/main/java/org/ole/planet/myplanet/data/room/dao/OfflineActivityDao.kt, app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt, app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt. do NOT touch ui/teams/members/MembersAdapter.kt — it only renders the result.
steps: 1. add `suspend fun getLastVisits(userNames: List<String>): Map<String, Long>` and `suspend fun countByUserIdsAndType(userIds: List<String>, type: String): Map<String, Int>` (e.g. `GROUP BY` queries) to OfflineActivityDao. 2. expose matching suspend functions on ActivitiesRepository/Impl. 3. in TeamsRepositoryImpl, fetch both maps once before `orderedMembers.map` and read per-member values from them. 4. in `markMembershipsForLeave`, partition memberships by `_rev.isNullOrBlank()`, then one `deleteByIds` call and one `upsertAll` call. 5. remove now-unused imports.
acceptance: ./gradlew testDefaultDebugUnitTest green; team members screen still shows per-member visit counts and last-visit text; leaving a team still deletes or marks every membership row.
size budget: ~90 changed lines, 4 files
out of scope: no schema or column changes; do not alter JoinedMemberData's public shape — adapters and viewmodels consume it unchanged.

---

### 2. precompute the day-to-meetups index in CalendarFragment (roadmap 7)

context: every calendar-day tap re-runs `meetups.filter { Instant.ofEpochMilli(meetup.startDate).atZone(ZoneId.systemDefault()).toLocalDate() == clickedDate }` over the whole list (CalendarFragment.kt:58-64), allocating an Instant + ZonedDateTime per meetup per click. The collect block at :46-54 already iterates all meetups once per emission — it can build a `Map<LocalDate, List<Meetup>>` there for free.
files: app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt (collectWhenStarted block ~:46 and the OnCalendarDayClickListener ~:56-66). leave `binding.calendarView.setCalendarDays` and `showAgendaDialog` untouched.
steps: 1. add a `meetupsByDay` field next to `meetups`. 2. inside the collect block, populate it via `updatedMeetups.groupBy { Instant.ofEpochMilli(it.startDate).atZone(ZoneId.systemDefault()).toLocalDate() }`. 3. in `onClick`, look up `meetupsByDay[clickedDate]` instead of filtering. 4. keep the `dayMeetups.isNotEmpty()` guard.
acceptance: ./gradlew testDefaultDebugUnitTest green; tapping a calendar day still opens the agenda dialog with exactly that day's meetups.
size budget: ~20 changed lines, 1 file
out of scope: no changes to Meetup model, viewmodel, or CalendarDay construction; do not refactor showAgendaDialog.

---

### 3. replace the per-close-click label scan in VoicesLabelManager (roadmap 7)

context: inside `for (label in labels)` each chip's close listener resolves `Constants.LABELS[label] ?: labels.firstOrNull { getLabel(it) == text }` (VoicesLabelManager.kt:82) — an O(labels) scan with a `getLabel` call per element, run on every chip removal. A `reverseLabels` map (Constants.LABELS value→key) already exists at :135; the missing piece is display-text→raw-label, which can be computed once before the loop.
files: app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt (the `for (label in labels)` block ~:76-92). do not touch Constants.kt or getLabel.
steps: 1. before the loop, build `val labelByDisplay = labels.associateBy { getLabel(it) }`. 2. inside the close listener, replace the `firstOrNull` scan with `labelByDisplay[text]` (keep the `Constants.LABELS[label]` fast path first). 3. verify no second lookup of the same kind remains in the block.
acceptance: ./gradlew testDefaultDebugUnitTest green; removing a label chip on a voice post still calls removeLabel with the same selectedLabel value.
size budget: ~10 changed lines, 1 file
out of scope: no chip styling or listener-structure changes; do not move label rendering into the fragment.

---

### 4. drop redundant list copies and hoist a loop-invariant split in repository impls (roadmap 1+7)

context: several impls copy freshly-returned DAO lists with no-op identity maps — `examDao.getByStepIdAndType(stepId, "courses").map { it }` (CoursesRepositoryImpl.kt:553), the same for "surveys" (:554), `courseStepDao.getByCourseId(courseKey).map { it }` (:912), and `questionDao.getByExamId(it).map { question -> question }` (SubmissionsRepositoryImpl.kt:292 and :789). Separately, `parentId.split("@").firstOrNull()` runs inside `for (i in 0 until answersArray.size())` (SubmissionsRepositoryImpl.kt:743) though `parentId` never changes in the loop.
files: app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt, app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt. do NOT touch TeamsRepositoryImpl.kt — its `.map { it }` sites belong to a different task's file set and are deliberately left alone.
steps: 1. delete the identity `.map { it }` / `.map { question -> question }` calls so DAO results are used directly (Room already returns a new list per query; callers only read). 2. hoist `val examIdPart = parentId.split("@").firstOrNull() ?: parentId` above the answers loop in SubmissionsRepositoryImpl. 3. run tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; course step data still lists exams/surveys; submission upload payload still carries the same examIdPart in answers.
size budget: ~15 changed lines, 2 files
out of scope: no DAO signature changes; do not collapse the two getByStepIdAndType calls into one query.

---

### 5. lowercase correct choices once at write time, not per comparison (roadmap 7+8)

context: `ExamQuestion.setCorrectChoiceArray` already lowercases every parsed choice (`list.add(JsonUtils.getString(array, i).lowercase(defaultLocale))`, ExamQuestion.kt:44), but `setCorrectChoices` (:54) stores raw input and `ExamAnswerUtils` defensively re-lowercases each correct choice on every call — `checkSelectAnswer` `correctChoices.any { it.lowercase(locale) == normalizedAns }`, `checkTextAnswer` `normalizedAns.contains(it.lowercase(locale))`, `checkMultipleSelectAnswer` `correctChoices.map { it.lowercase(locale) }` (ExamAnswerUtils.kt:69-91). Normalizing inside `setCorrectChoices` makes every stored list lowercase, so the per-call lowercase becomes provably dead work.
files: app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt (the three private check* functions), app/src/main/java/org/ole/planet/myplanet/model/ExamQuestion.kt (`setCorrectChoices` only). do NOT touch ExamTakingFragment or SubmissionsRepositoryImpl.
steps: 1. in `setCorrectChoices`, store `choices?.map { it.lowercase(Locale.getDefault()) }` (check the file's existing imports for Locale). 2. in `checkSelectAnswer`, compare `it == normalizedAns`. 3. in `checkTextAnswer`, use `normalizedAns.contains(it)`. 4. in `checkMultipleSelectAnswer`, use `correctChoices.sorted()` without the lowercase map. 5. grep for other `setCorrectChoices` callers and confirm none relied on preserving case.
acceptance: ./gradlew testDefaultDebugUnitTest green; exam answer checking produces identical results for select, multi-select, and text answers.
size budget: ~15 changed lines, 2 files
out of scope: no grading-logic changes beyond the redundant lowercase; do not rename fields or alter the Room converter.

---

### 6. hoist styling lookups out of chat UI bind paths (roadmap 7)

context: `ChatShareTargetAdapter` re-applies `setTypeface(null, Typeface.BOLD)`, `setTextColor(textColor)` and `setBackgroundColor(backgroundColor)` on every `bind` even though the colors are already cached as ViewHolder init fields (ChatShareTargetAdapter.kt:48-68). `ChatDetailFragment.updateButtonStyles` calls `ContextCompat.getColor` twice per child inside `for (i in 0 until aiTableRow.childCount)` (:527-537). `ChatAdapter`'s AI holder calls `context.getString(R.string.empty_text)` on every animated bind (:75).
files: app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt (GroupViewHolder.bind/ChildViewHolder.bind), app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt (updateButtonStyles only), app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt (AiChatViewHolder.bind only). leave ChatHistoryAdapter and selectAI alone.
steps: 1. in ChatShareTargetAdapter move the typeface/color/background calls from both `bind` methods into each ViewHolder's `init`. 2. in updateButtonStyles hoist the four `ContextCompat.getColor` results into locals before the loop. 3. in ChatAdapter cache the `R.string.empty_text` string on the holder or adapter once and reuse it. 4. run tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; share-target list rows, AI provider button highlight, and typing animation look identical.
size budget: ~35 changed lines, 3 files
out of scope: no layout or style-resource changes; do not restructure selectAI or the provider button creation.

---

### 7. hoist getString calls out of finance, life, and exam adapters (roadmap 7)

context: several list rows pay a resource lookup plus `String.format` per `bind`/`getView` where the format string itself is constant — `context.getString(R.string.string_range, ...)`, `R.string.number_placeholder`, `R.string.report_date_details`, `R.string.message_placeholder` etc. run ~11 times per bind in EnterprisesReportsAdapter.kt:70-81, four times per bind in EnterprisesFinancesAdapter.kt:55-58, three times in LifeAdapter.kt:58-60, and twice in HealthExaminationAdapter.kt:104-107. Fetching each raw template once (e.g. in ViewHolder init or lazy fields) and formatting locally removes the repeated `Resources` lookups.
files: app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt, app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapter.kt, app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt, app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt. do NOT touch SurveysAdapter.kt or the enterprises fragments.
steps: 1. per adapter, hoist every reused `context.getString(R.string.<constant>)` template into a `private val`/holder field obtained from `itemView.context`. 2. keep per-item formatting via `context.getString(resId, args)` only where args differ — or `String.format(template, args)` with the cached template; pick one style per file matching surrounding code. 3. keep `TimeUtils.formatDate` calls as-is (formatters are already cached in TimeUtils). 4. run tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; finance rows, life list, and examination rows render identical text.
size budget: ~60 changed lines, 4 files
out of scope: do not change displayed strings or add new string resources; no ViewHolder restructuring beyond the hoists.

---

### 8. hoist getString/getColor calls out of member and user adapters (roadmap 7)

context: same per-bind lookup problem in the user-facing lists — `context.getString(R.string.member_description, ...)` and `(R.string.last_visit, ...)` run each bind in MembersAdapter.kt:105-117, `ContextCompat.getColor(context, R.color.daynight_textColor)` runs per row inside `MemberMenuAdapter.getView` (:210-213), `binding.root.context.getString` runs per bind in HealthUsersAdapter.kt:48-53, and `context.getString(R.string.two_strings/joined_colon, ...)` per bind in UserArrayAdapter.kt:62-65.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt (onBindViewHolder + inner MemberMenuAdapter), app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt, app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt. leave RequestsAdapter.kt untouched.
steps: 1. MembersAdapter: hoist the two format strings into holder fields; hoist the MemberMenuAdapter color into a `private val` initialized in the adapter constructor. 2. HealthUsersAdapter: hoist the `R.string.two_strings`/`R.string.joined_colon` templates (context is available from `binding.root.context` at holder init). 3. UserArrayAdapter: same template hoist. 4. run tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; member list, member overflow menu text color, health user list, and user list render unchanged.
size budget: ~45 changed lines, 3 files
out of scope: no popup-menu behavior changes; do not cache per-item formatted results, only the templates/colors.

---

### 9. precompute team-match patterns outside the row loop in countTopLevelByTeams (roadmap 1+7)

context: `countTopLevelByTeams` iterates every membership row and, for each row, loops all `teamIds`, rebuilding the needle string `"\"_id\":\"$teamId\""` (VoicesRepositoryImpl.kt:496-502) — O(rows × teams) string allocations plus repeated `equals(ignoreCase)` on the same two row fields. Both the per-team pattern and the row fields' normalized form are loop-invariant in one dimension each.
files: app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt (`countTopLevelByTeams` only). do NOT touch newsDao or teamIdPattern.
steps: 1. before the rows loop, build `val viewInPatterns = teamIds.map { it to "\"_id\":\"$it\"" }`. 2. inside the row loop, compute `row.viewableBy.equals("teams", true)` once and reuse. 3. iterate `viewInPatterns` to match `row.viewableId`/pattern and bump `counts[teamId]`. 4. keep the `matchesViewable || matchesViewIn` semantics identical. 5. run tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; per-team post counts identical to before for the same data.
size budget: ~15 changed lines, 1 file
out of scope: no query changes to newsDao; do not alter the `"_id":"…"` JSON matching semantics.

---

### 10. buffer the asset stream and compile the credential regex once (roadmap 7)

context: `WebViewActivity`'s PathHandler returns `WebResourceResponse(mimeType, "utf-8", FileInputStream(file))` — an unbuffered stream for every intercepted `/assets/` request (WebViewActivity.kt:180). In `UserRepositoryImpl.replacedUrl`, `url.replaceFirst("[^:]+:[^@]+@".toRegex(), ...)` compiles the same regex on every call (UserRepositoryImpl.kt:719).
files: app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt (externalPathHandler only), app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt (replacedUrl + companion). do NOT touch isWithinDirectory or other WebView handlers.
steps: 1. replace `FileInputStream(file)` with `file.inputStream().buffered()`. 2. in UserRepositoryImpl move the pattern to `private val CREDENTIALS_REGEX = "[^:]+:[^@]+@".toRegex()` in the companion object and use it in `replaceFirst`. 3. remove any now-unused imports. 4. run tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; HTML resources still load in the WebView viewer; user upload URL still embeds name:password credentials correctly.
size budget: ~10 changed lines, 2 files
out of scope: no WebViewAssetLoader configuration changes; do not alter the regex pattern itself.
