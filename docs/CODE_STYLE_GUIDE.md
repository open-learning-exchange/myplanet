# Code Style Guide

## Language

All new code is **Kotlin**. The entire codebase is Kotlin — there is no legacy Java source. The build generates Java files via Hilt/kapt annotation processing but these are auto-generated, not hand-written.

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

- `camelCase` for functions and variables
- `PascalCase` for classes and objects
- `UPPER_SNAKE_CASE` for constants
- Avoid `!!` (non-null assertion) — use `?.`, `?:`, or explicit null checks
- Prefer Kotlin expressions over statements: `val x = if (...) a else b`
- Use scope functions (`apply`, `also`, `let`, `run`) where they improve clarity — don't chain them deeply

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Realm model | `Realm` prefix, `open class`, extends `RealmObject()` | `RealmMyCourse`, `RealmMyTeam` |
| ViewModel | `@HiltViewModel`, `ViewModel` suffix | `CoursesViewModel`, `TeamViewModel` |
| Repository interface | `Repository` suffix | `CoursesRepository` |
| Repository implementation | `RepositoryImpl` suffix | `CoursesRepositoryImpl` |
| Fragment | `@AndroidEntryPoint`, `Fragment` suffix | `CoursesFragment` |
| Activity | `@AndroidEntryPoint`, `Activity` suffix | `LoginActivity` |
| Worker | `Worker` suffix | `AutoSyncWorker` |
| Layout files | `fragment_`, `activity_`, `row_`/`item_`, `dialog_` prefix | `fragment_my_course.xml` |
| StateFlow backing fields | `_` prefix, private `MutableStateFlow` | `_state` exposed as `state` |

---

## Dependency Injection (Hilt)

This project uses Hilt. Always follow the established DI patterns — do not manually instantiate repositories or services.

**Fragments and Activities** — annotate with `@AndroidEntryPoint`, inject with `@Inject`:
```kotlin
@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>() {
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    private val viewModel: CoursesViewModel by viewModels()
}
```

**ViewModels** — annotate with `@HiltViewModel`, inject via constructor:
```kotlin
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() { ... }
```

**Workers** — cannot use constructor injection. Use `EntryPointAccessors`:
```kotlin
val entryPoint = EntryPointAccessors.fromApplication(
    applicationContext, NetworkDependenciesEntryPoint::class.java
)
val apiInterface = entryPoint.apiInterface()
```

**New dependencies** go in the appropriate module in `di/`:
- Network → `NetworkModule`
- Realm/DB → `DatabaseModule`
- Repository bindings → `RepositoryModule` (bind interface to impl here)
- Services → `ServiceModule`

---

## Architecture: Layer Boundaries

```
Fragment/Activity  →  ViewModel  →  Repository  →  Realm / ApiInterface
```

Never skip layers. Fragments don't touch Realm. Repositories don't reference ViewModels.

**ViewModels** expose state via `StateFlow`, one-shot events via `Channel` or `SharedFlow`. Use `viewModelScope.launch`. Never hold Fragment/Activity references.

**Use sealed classes for UI state:**
```kotlin
sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Success : SyncStatus()
    data class Failure(val message: String?) : SyncStatus()
}
```

**Fragments** collect with `repeatOnLifecycle`:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { updateUI(it) }
    }
}
```

---

## Realm Rules

This project uses **Realm Java SDK (io.realm, v10.19.0)** — not the newer Kotlin SDK.

**Model requirements:**
```kotlin
open class RealmMyCourse : RealmObject() {   // must be open, must extend RealmObject()
    @PrimaryKey
    var id: String? = null                   // must be var, not val
    var courseId: String? = null
}
```

**All mutations require a transaction.** Mutating outside one crashes at runtime:
```kotlin
// Via repository base class (preferred)
executeTransaction { realm ->
    realm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()?.courseTitle = newTitle
}
```

**Always detach objects before returning from a Realm block:**
```kotlin
withRealm { realm ->
    realm.where(RealmMyCourse::class.java).findFirst()?.let { realm.copyFromRealm(it) }
}
```

**Chunk IN queries at 1000 items** — Realm crashes on larger lists:
```kotlin
ids.chunked(1000).forEach { chunk ->
    realm.where(RealmMyCourse::class.java).`in`("courseId", chunk.toTypedArray()).findAll()
}
```

**Schema changes require a migration** — increment `schemaVersion` in `DatabaseService.kt` (currently 12) and add a step in `RealmMigrations.kt`. Skipping this crashes existing installs on launch.

Use the base class helpers in `RealmRepository` (`withRealm`, `executeTransaction`, `queryList`, `queryListFlow`) — repository implementations should not call `Realm.getDefaultInstance()` directly (none currently do). Opening Realm directly belongs in the data layer (`DatabaseService`), not in repositories.

---

## Coroutines & Threading

- Use `dispatcherProvider.io` (injected) not hardcoded `Dispatchers.IO`
- Use `dispatcherProvider.main` for UI
- `viewModelScope.launch` in ViewModels
- `lifecycleScope.launch` in Fragments/Activities
- `withContext(dispatcherProvider.io)` inside launch blocks for IO work

---

## UI Patterns

**View Binding — always, no `findViewById`:**
```kotlin
private var _binding: FragmentCoursesBinding? = null
private val binding get() = _binding!!

override fun onDestroyView() {
    binding.recycler.adapter = null  // prevent leak
    _binding = null
    super.onDestroyView()
}
```

**Base classes** — check before creating new Fragments:
- List screens → `BaseRecyclerFragment`
- Team sub-screens → `BaseTeamFragment`
- Permission handling → `BasePermissionActivity`
- Dialogs → `BaseDialogFragment`

**Adapters** — prefer `ListAdapter` for new adapters (the large majority of existing adapters use it, and it handles diffing automatically). Some older adapters still extend `RecyclerView.Adapter` directly; match `ListAdapter` for new code rather than converting existing ones as part of an unrelated change.

**User feedback** — use `Utilities.toast()` not `Toast.makeText()` directly. Use `Snackbar` for action-requiring feedback.

---

## Adding a New Feature

1. **Model** — `Realm*` class extending `RealmObject()`. Add `serialize()` and `insert()` companion methods if it syncs to server.
2. **Repository** — interface + impl extending `RealmRepository`. Bind in `RepositoryModule`.
3. **ViewModel** — `@HiltViewModel`, inject repo + `DispatcherProvider`, expose `StateFlow`.
4. **UI** — `@AndroidEntryPoint` Fragment/Activity, observe with `repeatOnLifecycle`.
5. **Manifest** — register new Activities.
6. **Migration** — if Realm model fields changed, increment schema version and add migration.

---

## What NOT to Do

- ❌ Mutate Realm objects outside a write transaction
- ❌ Return live Realm objects from a `withRealm` block without `copyFromRealm`
- ❌ IN queries on lists > 1000 without chunking
- ❌ Use `Dispatchers.IO` directly — use `dispatcherProvider.io`
- ❌ Use `GlobalScope` or `AsyncTask`
- ❌ Hardcode user-visible strings — all strings go in `res/values/strings.xml`
- ❌ Use `Toast.makeText()` directly — use `Utilities.toast()`
- ❌ Skip `@AndroidEntryPoint` on Fragments/Activities that use `@Inject`
- ❌ Add Realm model fields without updating both `serialize()` and `insert()` companion methods
- ❌ Change Realm schema without incrementing version and adding migration

---

## PR Standards

**Branch naming** — use prefix matching the agent or author:
- `claude/description`, `jules/description`, `codex/description`, `copilot/description` for AI agents (the CI build cache recognizes these prefixes)
- `feat/description`, `fix/description`, `refactor/description`, `chore/description` for humans

**Commit messages** follow conventional commits:
```
feat: add sort options to survey list
fix: prevent crash when meetupId is null on upload
```

**One PR = one issue.** Don't bundle unrelated fixes.

**PR description must include:** what changed and why, steps to reproduce (for bugs), manual test plan, any Realm schema changes.

**CI runs on every non-master branch** — `assembleDefaultDebug` and `assembleLiteDebug` must pass.
