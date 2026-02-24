# Quick Reference: 10 Refactoring Tasks

## Focus Areas
- Repository boundary enforcement
- Moving data logic from UI/service to repositories
- Lifecycle-safe Flow collection
- Dependency injection improvements
- MVVM pattern adherence

---

## Task List

**1. Move Health Examination Transactions to HealthRepository**
- File: `AddExaminationActivity.kt` lines 262-348
- Create: `HealthRepository.saveExamination()` method
- Remove: Direct mRealm usage from Activity

**2. Fix SyncActivity Flow Collection Lifecycle**
- File: `SyncActivity.kt` line 773-781
- Add: `repeatOnLifecycle(STARTED)` wrapper
- Track: Job for explicit cancellation

**3. Replace applicationScope with lifecycleScope in SyncActivity**
- File: `SyncActivity.kt` line 654-669
- Replace: `MainApplication.applicationScope` → `lifecycleScope`
- Fix: Operations continuing after Activity destroyed

**4. Convert InlineResourceAdapter to ListAdapter+DiffUtil**
- File: `InlineResourceAdapter.kt` line 27-30
- Change: Extend `ListAdapter` with `DiffUtils.itemCallback()`
- Remove: Manual list management

**5. Create LifeViewModel**
- Files: New `LifeViewModel.kt` + update `LifeFragment.kt`
- Add: `@HiltViewModel` with `LifeRepository` injection
- Remove: Direct repository access from Fragment

**6. Create PersonalsViewModel**
- Files: New `PersonalsViewModel.kt` + update `PersonalsFragment.kt`
- Add: `@HiltViewModel` with multiple repository injections
- Remove: Business logic from Fragment lifecycle

**7. Tighten TeamResourcesFragment Repository Boundary**
- File: `TeamResourcesFragment.kt` line 100-125
- Create: `TeamsRepository.getAvailableResourcesToAdd()` method
- Remove: Direct `resourcesRepository` access from Fragment

**8. Move Resource Batch Sync to ResourcesRepository**
- File: `SyncManager.kt` line 664-712
- Create: `ResourcesRepository.batchInsertResources()` method
- Remove: Direct Realm transactions from SyncManager

**9. Inject ServerUrlMapper via Hilt**
- Files: 14+ locations with manual instantiation
- Add: `@Singleton` + `@Inject constructor()` to ServerUrlMapper
- Replace: All `ServerUrlMapper()` → `@Inject lateinit var`

**10. Move Chat Sync to ChatRepository**
- File: `TransactionSyncManager.kt` line 240-312
- Create: `ChatRepository.syncInsertChatHistory()` method
- Remove: Direct Realm operations from TransactionSyncManager

---

## Recommended Order (minimize conflicts)

1. **Task 9** - ServerUrlMapper DI (touches many files, simple)
2. **Task 2, 3** - SyncActivity fixes (one file, isolated)
3. **Task 4** - InlineResourceAdapter (isolated component)
4. **Task 1** - Health transactions (isolated feature)
5. **Task 5, 6** - ViewModels (new files, minimal conflicts)
6. **Task 7** - Team resources (depends on patterns)
7. **Task 8** - Resource batch sync
8. **Task 10** - Chat sync

---

## Key Patterns to Follow

### Repository Method Pattern
```kotlin
// In Repository Interface
suspend fun operationName(params): Result<ReturnType>

// In RepositoryImpl
override suspend fun operationName(params): Result<ReturnType> = 
    withRealm { realm ->
        // Transaction logic here
        Result.success(data)
    }
```

### ViewModel Pattern
```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {
    private val _state = MutableStateFlow<State>(Initial)
    val state: StateFlow<State> = _state.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _state.value = repository.getData()
        }
    }
}
```

### Flow Collection Pattern
```kotlin
// In Fragment/Activity
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state ->
            // Update UI
        }
    }
}
```

### Hilt Injection Pattern
```kotlin
// Service class
@Singleton
class MyService @Inject constructor(
    private val dependency: Dependency
) {
    // Implementation
}

// Module (if needed)
@Module
@InstallIn(SingletonComponent::class)
object MyModule {
    @Provides
    @Singleton
    fun provideMyService(dep: Dependency): MyService = MyService(dep)
}

// Usage in Fragment/Activity
@AndroidEntryPoint
class MyFragment : Fragment() {
    @Inject lateinit var myService: MyService
}
```

---

## Estimated Time per Task

- Task 1: 2-3 hours
- Task 2: 30-45 minutes
- Task 3: 30-45 minutes
- Task 4: 1-2 hours
- Task 5: 2-3 hours
- Task 6: 2-3 hours
- Task 7: 1-2 hours
- Task 8: 2-3 hours
- Task 9: 2-3 hours
- Task 10: 2-3 hours

Total: ~15-25 hours of work across 10 PRs

---

## Testing Checklist per Task

- [ ] App builds successfully
- [ ] Feature works as before (no regressions)
- [ ] No Realm transaction errors in logcat
- [ ] No memory leaks on lifecycle transitions
- [ ] Repository methods use proper dispatchers
- [ ] No threading issues (main thread database access)
- [ ] Proper error handling maintained
- [ ] Code follows existing patterns
