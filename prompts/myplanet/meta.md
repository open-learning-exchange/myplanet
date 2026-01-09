# myPlanet Agentic Charter and Execution Framework

**Version**: 1.0
**Last Updated**: 2026-01-09

---

## Purpose

This document establishes the operational framework for AI agents contributing to the myPlanet Android application. It ensures consistent, high-quality contributions that align with the project's offline-first educational mission.

---

## Vision Directives

### 1. Offline-First Architecture

All features MUST function without internet connectivity. The application serves learners in remote areas with limited or no network access. Every component should:

- Store data locally using Realm database
- Queue operations for later synchronization
- Provide meaningful feedback when offline
- Never block user interaction waiting for network

### 2. Educational Accessibility

myPlanet democratizes access to educational resources. Features should:

- Support multiple languages (English, Arabic, Spanish, French, Nepali, Somali)
- Follow WCAG accessibility guidelines
- Minimize resource requirements for low-end devices
- Prioritize content consumption over content creation

### 3. Provider-Neutral Data Sync

The synchronization layer abstracts away server-specific details. When implementing sync:

- Use the repository pattern for data access
- Leverage existing SyncManager infrastructure
- Handle conflict resolution gracefully
- Support incremental synchronization

### 4. Android Platform Alignment

Follow modern Android development practices:

- Material Design 3 for UI components
- Kotlin-first with coroutines for async operations
- Hilt for dependency injection
- AndroidX Work for background tasks

---

## Agent Operating Constraints

### Pre-Coding Requirements

Before writing any code, the agent MUST:

1. **Read Required Documentation**
   - `docs/CODE_STYLE_GUIDE.md` - Kotlin/Android code conventions
   - `docs/UI_GUIDELINES.md` - Material Design implementation standards
   - `CLAUDE.md` - Project-specific guidance

2. **Understand Context**
   - Identify affected components and their dependencies
   - Review existing patterns in similar features
   - Check for relevant base classes to extend

3. **Produce Implementation Plan**
   - Create a multi-item task list using the TodoWrite tool
   - Break complex changes into atomic, testable steps
   - Identify files to be created, modified, or deleted
   - Wait for explicit task selection before execution

### Task Grammar

When proposing changes, use this structured format:

```
[CATEGORY] Action: Brief description
  - Details of the change
  - Affected files/components
  - Dependencies or prerequisites
```

**Categories:**
- `[MODEL]` - Realm data model changes
- `[REPO]` - Repository layer modifications
- `[UI]` - User interface changes
- `[SERVICE]` - Background service updates
- `[SYNC]` - Synchronization logic
- `[TEST]` - Testing additions
- `[REFACTOR]` - Code restructuring
- `[FIX]` - Bug fixes
- `[DOCS]` - Documentation updates

### Post-Coding Verification

After completing code changes, verify:

1. **Style Compliance**
   - Code follows Kotlin conventions
   - Naming matches project patterns
   - No hardcoded strings (use resources)

2. **Pattern Adherence**
   - Repository interface + implementation pairs
   - Hilt injection configured correctly
   - Base classes extended appropriately

3. **Build Validation**
   ```bash
   ./gradlew assembleDefaultDebug
   ```

4. **Manifest Registration**
   - New activities registered
   - Required permissions declared

---

## Prohibited Outputs

The agent MUST NOT produce:

### Code Violations

- Code that violates `CODE_STYLE_GUIDE.md`
- Changes made without reading existing implementations
- Hardcoded server URLs, credentials, or API keys
- Inline styles or hardcoded colors in layouts
- Non-null assertions (`!!`) without proper safeguards
- Blocking operations on the main thread

### Scope Violations

- High-level summaries without actionable items
- Documentation-only changes without implementation
- Single change execution without broader context
- Features that require network connectivity to function

### Security Violations

- Logging of sensitive user data
- Unencrypted storage of credentials
- Network requests without SSL/TLS
- Permissions requested without justification

---

## Domain-Specific Guidelines

### Realm Database Operations

```kotlin
// CORRECT: Transaction-safe writes
mRealm.executeTransaction { realm ->
    val model = realm.createObject(RealmMyModel::class.java, id)
    model.field = value
}

// INCORRECT: Non-transactional modifications
val model = mRealm.where(RealmMyModel::class.java).findFirst()
model?.field = value  // Will crash or corrupt data
```

### Repository Pattern

```kotlin
// Interface in repository/ package
interface FeatureRepository {
    suspend fun getData(): List<Model>
    suspend fun syncData(): Result<Unit>
}

// Implementation with @Inject constructor
class FeatureRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService
) : FeatureRepository {
    // Implementation
}

// Registration in RepositoryModule
@Binds
abstract fun bindFeatureRepository(
    impl: FeatureRepositoryImpl
): FeatureRepository
```

### Background Work

```kotlin
// Use CoroutineWorker for background tasks
class MyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Perform work
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
```

### UI State Management

```kotlin
// Prefer StateFlow for reactive UI
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// Collect in lifecycle-aware manner
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            updateUI(state)
        }
    }
}
```

---

## Continuous Improvement

### Identifying Technical Debt

When encountering code that:
- Violates current style guidelines
- Uses deprecated APIs
- Contains TODO comments
- Has code duplication

Create a separate improvement task rather than addressing inline, unless directly related to the current feature.

### Migration Paths

When Android or library versions update:
1. Document breaking changes
2. Propose migration strategy
3. Update affected code incrementally
4. Verify backward compatibility with minSdk

---

## Agent Workflow Summary

```
┌─────────────────────────────────────┐
│     1. Read Documentation           │
│  - CODE_STYLE_GUIDE.md              │
│  - UI_GUIDELINES.md                 │
│  - CLAUDE.md                        │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     2. Analyze Request              │
│  - Understand requirements          │
│  - Identify affected components     │
│  - Check existing patterns          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     3. Create Implementation Plan   │
│  - Use TodoWrite tool               │
│  - Break into atomic tasks          │
│  - Wait for approval                │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     4. Execute Tasks                │
│  - Follow style guides              │
│  - Use existing patterns            │
│  - Update todos as completed        │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     5. Verify & Validate            │
│  - Build passes                     │
│  - Style compliance                 │
│  - Commit with clear message        │
└─────────────────────────────────────┘
```

---

## References

- [CLAUDE.md](../../CLAUDE.md) - Project-specific AI guidance
- [CODE_STYLE_GUIDE.md](../../docs/CODE_STYLE_GUIDE.md) - Kotlin/Android conventions
- [UI_GUIDELINES.md](../../docs/UI_GUIDELINES.md) - Material Design standards
- [Android Developer Docs](https://developer.android.com/)
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
