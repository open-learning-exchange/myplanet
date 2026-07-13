# Test Dispatcher Migration Guide

## Issue #7: Standardize Test Dispatcher Usage

### Problem
The codebase had inconsistent test dispatcher patterns:
- Some tests used the centralized `MainDispatcherRule`
- Many tests created inline `StandardTestDispatcher` or `UnconfinedTestDispatcher` instances
- This led to 168 lines of duplicated dispatcher setup code across 50+ test files

### Solution Implemented

#### 1. Enhanced MainDispatcherRule
Updated `/app/src/test/java/org/ole/planet/myplanet/utils/MainDispatcherRule.kt` with:
- Comprehensive KDoc documentation
- Usage examples for basic and advanced scenarios
- Clear explanation of when to use different dispatcher types

#### 2. Migrated Test Files
Two example migrations completed:

**AuthSessionUpdaterTest.kt:**
- Removed inline `UnconfinedTestDispatcher` and `TestScope`
- Added `@get:Rule val mainDispatcherRule = MainDispatcherRule()`
- Updated all references to use `mainDispatcherRule.testDispatcher`

**SurveysViewModelTest.kt:**
- Removed inline `StandardTestDispatcher` 
- Removed manual `Dispatchers.setMain()` and `Dispatchers.resetMain()` calls
- Removed `@After` teardown method (handled by rule)
- Added `@get:Rule val mainDispatcherRule = MainDispatcherRule()`
- Updated all `testDispatcher.scheduler` references

### Migration Pattern

For each test file with inline dispatcher setup:

**Before:**
```kotlin
private val testDispatcher = StandardTestDispatcher()

@Before
fun setup() {
    Dispatchers.setMain(testDispatcher)
    // ...
}

@After
fun tearDown() {
    Dispatchers.resetMain()
}
```

**After:**
```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()

@Before
fun setup() {
    // No dispatcher setup needed!
    // ...
}
// No @After needed for dispatcher cleanup
```

### Remaining Files to Migrate

Run this command to find remaining files:
```bash
grep -rl "Dispatchers\.setMain\|UnconfinedTestDispatcher()\|StandardTestDispatcher()" \
  app/src/test --include="*.kt" | grep -v "MainDispatcherRule.kt"
```

Approximately 50 files still need migration following the same pattern.

### Benefits

1. **Consistency**: All tests use the same dispatcher setup pattern
2. **Less Code**: Eliminates ~168 lines of duplicated setup/teardown code
3. **Reliability**: JUnit Rule ensures proper cleanup even on test failures
4. **Maintainability**: Single source of truth for dispatcher configuration
5. **Clarity**: Self-documenting test setup with clear intent

### Next Steps

1. Migrate remaining test files using the established pattern
2. Consider adding a lint rule to prevent future inline dispatcher usage
3. Update TESTING.md documentation to reflect the standard pattern
