# Refactor Tasks - 10 Quick Wins

document := { finding_section } [ testing_section ]

finding_section :=
  "### " title "\n"
  rationale_paragraph "\n"
  { "\n" citation_line }
  "\n"
  task_stub_block "\n"

title := <short text, no trailing period>

rationale_paragraph := <1–3 sentences, plain text>

citation_line :=
  ":codex-file-citation[codex-file-citation]{"
  "line_range_start=" int " "
  "line_range_end=" int " "
  "path=" path " "
  "git_url=\"" url "#L" int "-L" int "\}"
  
task_stub_block :=
  ":::task-stub{title=\"" task_title "\"}\n"
  step_line
  { "\n" step_line }
  "\n:::" 

step_line := int "." space step_text

---

### ChatShareTargetAdapter uses raw DiffUtil instead of project utility

The ChatShareTargetAdapter directly implements DiffUtil.ItemCallback instead of using the project's DiffUtils.itemCallback helper. This creates inconsistency with other adapters and bypasses centralized diff configuration.

:codex-file-citation[codex-file-citation]{line_range_start=69 line_range_end=82 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L69-L82"}

:::task-stub{title="Migrate ChatShareTargetAdapter to use DiffUtils.itemCallback"}
1. Replace inline DiffUtil.ItemCallback with DiffUtils.itemCallback helper
2. Move DIFF_CALLBACK to companion object following project pattern
3. Verify diff behavior matches original implementation
:::

---

### TeamsSelectionAdapter has incomplete areContentsTheSame comparison

The areContentsTheSame function only compares the name field, missing other relevant fields like _id, teamType, etc. This can cause incorrect diff calculations when only non-name fields change.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt#L18-L22"}

:::task-stub{title="Fix TeamsSelectionAdapter areContentsTheSame to compare all relevant fields"}
1. Review TeamSummary data class fields
2. Update areContentsTheSame to compare all significant fields
3. Test with sample data to verify correct diff behavior
:::

---

### RequestsAdapter weak areContentsTheSame comparison

The areContentsTheSame only compares name field for RealmUser objects. This is insufficient since other fields like email, id could change without triggering updates.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt#L17-L21"}

:::task-stub{title="Fix RequestsAdapter areContentsTheSame to include more fields"}
1. Review which RealmUser fields are displayed in the adapter
2. Update areContentsTheSame to include displayed fields (name, email, id)
3. Add relevant fields that could change during lifecycle
:::

---

### CommunityLeadersAdapter areContentsTheSame missing field comparisons

The adapter only compares firstName, lastName, and email. Other fields like level, phoneNumber, etc. displayed in the UI are not compared.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityLeadersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityLeadersAdapter.kt#L19-L27"}

:::task-stub{title="Fix CommunityLeadersAdapter areContentsTheSame to include all displayed fields"}
1. Review CommunityLeadersAdapter ViewHolder bind method
2. Identify all fields displayed in the UI (firstName, lastName, email)
3. Update areContentsTheSame to compare all displayed fields
:::

---

### CoursesRepositoryImpl.getCoursesByIds bypasses queryList helper

The method directly queries realm instead of using the inherited queryList helper from RealmRepository. This creates inconsistency and bypasses the centralized query pattern.

:codex-file-citation[codex-file-citation]{line_range_start=87 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L87-L93"}

:::task-stub{title="Refactor CoursesRepositoryImpl.getCoursesByIds to use queryList helper"}
1. Review queryList helper signature and behavior
2. Refactor getCoursesByIds to use queryList with `in` clause
3. Verify behavior matches current implementation
:::

---

### LoginViewModel launches coroutines on main dispatcher unnecessarily

Methods like loadTeamsAsync and getTeamMembers launch on Main dispatcher with comment "Launch on main to safely emit Realm objects" but should emit on main after fetching on IO.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt#L38-L46"}

:::task-stub{title="Fix LoginViewModel coroutine dispatchers for proper threading"}
1. Move repository calls to dispatcherProvider.io
2. Only emit to StateFlow on main dispatcher
3. Verify thread safety with Realm objects
:::

---

### BaseRecyclerFragment has nested lifecycleScope launches

The onViewCreated method has nested viewLifecycleOwner.lifecycleScope.launch calls which can cause issues with coroutine execution order and cancellation.

:codex-file-citation[codex-file-citation]{line_range_start=76 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L76-L95"}

:::task-stub{title="Flatten nested lifecycleScope.launch calls in BaseRecyclerFragment"}
1. Identify all nested launch calls in onViewCreated
2. Flatten to single sequential coroutine block
3. Ensure proper error handling and cancellation
:::

---

### UserArrayAdapter not using ListAdapter pattern

The UserArrayAdapter extends ArrayAdapter instead of ListAdapter, missing benefits of DiffUtil and proper list management.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt#L1-L30"}

:::task-stub{title="Convert UserArrayAdapter to ListAdapter with DiffUtil"}
1. Convert UserArrayAdapter to extend ListAdapter<User, ViewHolder>
2. Add DiffUtils.itemCallback implementation
3. Update usages to call submitList instead of adapter constructor
:::

---

### AchievementsAdapter could use better DiffUtil payload

The AchievementsAdapter doesn't use change payloads for partial updates, causing full rebinds when only specific fields change.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementsAdapter.kt#L1-L50"}

:::task-stub{title="Add DiffUtil change payloads to AchievementsAdapter"}
1. Review which fields can change independently
2. Add getChangePayload to DiffUtils.itemCallback
3. Implement onBindViewHolder with payloads parameter
:::

---

### TeamsAdapter DIFF_CALLBACK should be private in companion

The TeamDiffCallback is public in the companion object but doesn't need to be accessed outside the class. Making it private follows encapsulation best practices.

:codex-file-citation[codex-file-citation]{line_range_start=142 line_range_end=146 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt#L142-L146"}

:::task-stub{title="Make TeamsAdapter.TeamDiffCallback private"}
1. Change TeamDiffCallback from public to private
2. Verify no external usages need updating
3. Ensure consistent with other adapter patterns in codebase
:::
