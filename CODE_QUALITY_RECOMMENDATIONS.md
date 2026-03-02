# Code Quality Recommendations - myPlanet Android

**Based on:** Dead Code Analysis (2026-03-02)  
**Status:** Codebase is clean - Focus on optimization and technical debt

---

## Executive Summary

The dead code analysis revealed that the myPlanet Android codebase is **remarkably clean** with **no significant dead code** to remove. However, several optimization opportunities and technical debt items were identified.

---

## Priority 1: Enable ProGuard/R8 Shrinking 🚀

### Current State
```groovy
// app/build.gradle
buildTypes {
    release {
        minifyEnabled = false  // ❌ Disabled
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

### Recommendation
```groovy
buildTypes {
    release {
        minifyEnabled = true           // ✅ Enable code shrinking
        shrinkResources = true          // ✅ Enable resource shrinking
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

### Expected Impact
- **APK size reduction:** 30-50%
- **Security:** Code obfuscation
- **Performance:** Dead code elimination at build time
- **Automatic cleanup:** Removes unused resources that static analysis misses

### Implementation Steps
1. Update `app/build.gradle` with changes above
2. Run: `./gradlew assembleDefaultRelease`
3. Test thoroughly on devices (especially older APIs)
4. Update `proguard-rules.pro` if ProGuard breaks reflection-based code
5. Monitor crash reports for ProGuard-related issues

### ProGuard Rules Reference
```proguard
# Keep Realm models
-keep class org.ole.planet.myplanet.model.** { *; }

# Keep Hilt components
-keep class * extends dagger.hilt.** { *; }

# Keep Retrofit interfaces
-keep interface org.ole.planet.myplanet.data.api.** { *; }

# Keep serialized classes
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
```

---

## Priority 2: Migrate Deprecated APIs 📋

### Problem
20+ deprecated methods are still actively used across 50+ call sites. This creates technical debt and prevents removal of outdated implementations.

### Deprecated Methods by Category

#### 1. UserSessionManager / UserRepository
**Migration:** `getUserModel()` → `getUserModelSuspending()`

**Affected Files (10+ call sites):**
- `ui/life/LifeFragment.kt`
- `ui/community/CommunityTabFragment.kt`
- `ui/courses/CoursesFragment.kt`
- `ui/courses/TakeCourseFragment.kt`
- `ui/courses/CourseDetailFragment.kt`
- `ui/courses/CourseStepFragment.kt`
- `ui/courses/CourseProgressActivity.kt`
- `ui/courses/ProgressViewModel.kt`
- And 2+ more...

**Example Migration:**
```kotlin
// Before (deprecated)
val user = userSessionManager.getUserModel()
if (user != null) {
    // Use user
}

// After (suspend function)
lifecycleScope.launch {
    val user = userSessionManager.getUserModelSuspending()
    user?.let {
        // Use user
    }
}
```

#### 2. CoursesRepository (from RealmMyCourse static methods)
**Migrations:**
- `RealmMyCourse.isMyCourse()` → `coursesRepository.isMyCourse()`
- `RealmMyCourse.getCourseByCourseId()` → `coursesRepository.getCourseByCourseId()`
- `RealmMyCourse.getMyCourseIds()` → `coursesRepository.getMyCourseIds()`

**Affected:** 32+ call sites

#### 3. TeamsRepository
**Migration:** `getTeamTransactions()` → `getTeamTransactionsWithBalance()`

#### 4. Other Repository Migrations
- `ChatRepository`: `insertNewsFromJson()`, `serializeNews()`
- `ProgressRepository`: `getCurrentProgress()`
- `SubmissionsRepository`: `getOrCreateSubmission()`, `getExamMap()`
- `ResourcesRepository`: `getMyLibIds()`

### Implementation Plan

**Phase 1: UserSessionManager (1 day)**
- Create helper extension function for migration
- Update all 10+ call sites
- Test user profile access flows

**Phase 2: RealmMyCourse → CoursesRepository (1 day)**
- Update all 32+ call sites
- Verify course enrollment flows
- Test my courses functionality

**Phase 3: Remaining APIs (0.5 day)**
- Update TeamsRepository calls
- Update other repository calls
- Comprehensive testing

**Total Estimated Effort:** 2-3 days

---

## Priority 3: Android Lint Analysis 🔍

### Why Run Lint
- Detects unused resources missed by static analysis
- Finds code quality issues
- Identifies potential bugs
- Detects security vulnerabilities

### Command
```bash
./gradlew lintDefaultRelease
```

### Review Results
```bash
# Report location
open app/build/reports/lint-results-defaultRelease.html

# Or text format
cat app/build/reports/lint-results-defaultRelease.txt
```

### Focus Areas
1. **UnusedResources:** String/color/dimen resources
2. **Deprecation:** Additional deprecated API usage
3. **Security:** Hardcoded secrets, insecure network
4. **Performance:** Inefficient layouts, memory leaks
5. **Correctness:** Missing translations, invalid XML

---

## Priority 4: Dependency Analysis 📦

### Install Plugin
Add to root `build.gradle.kts`:
```kotlin
plugins {
    id("com.autonomousapps.dependency-analysis") version "2.8.0"
}
```

### Run Analysis
```bash
./gradlew buildHealth

# Review report
open build/reports/dependency-analysis/build-health-report.html
```

### What It Detects
- Unused dependencies (declared but not used)
- Used transitive dependencies (should be declared)
- Incorrect dependency configurations (api vs implementation)
- Dependency version conflicts

---

## Priority 5: CI/CD Integration ⚙️

### Add to GitHub Actions Workflow

**File:** `.github/workflows/code-quality.yml`
```yaml
name: Code Quality

on:
  pull_request:
    branches: [ master ]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      
      - name: Run Android Lint
        run: ./gradlew lintDefaultRelease
      
      - name: Upload Lint Results
        uses: actions/upload-artifact@v4
        with:
          name: lint-results
          path: app/build/reports/lint-results-*.html
  
  dependency-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      
      - name: Dependency Analysis
        run: ./gradlew buildHealth
      
      - name: Upload Analysis
        uses: actions/upload-artifact@v4
        with:
          name: dependency-analysis
          path: build/reports/dependency-analysis/
```

---

## Quick Wins 🎯

### 1. Update .gitignore
Add build artifacts to avoid accidental commits:
```gitignore
# Android Studio / Gradle
*.iml
.gradle/
build/
captures/
.externalNativeBuild/
.cxx/

# Lint
lint-report.html
lint-results*.html
lint-results*.xml
```

### 2. Enable Strict Mode (Debug Builds)
In `MainApplication.kt`:
```kotlin
override fun onCreate() {
    super.onCreate()
    
    if (BuildConfig.DEBUG) {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }
}
```

### 3. Enable Kotlin Explicit API Mode
In `app/build.gradle`:
```kotlin
kotlin {
    explicitApi = ExplicitApiMode.Strict  // Forces public API documentation
}
```

---

## Long-Term Improvements 📅

### Quarterly Tasks
1. **Dead Code Analysis** (Every 3 months)
   - Run this analysis again
   - Check for accumulated technical debt
   
2. **Dependency Updates** (Monthly)
   - Review and update outdated dependencies
   - Check for security vulnerabilities
   
3. **Code Quality Metrics** (Ongoing)
   - Track lint warning count (goal: <10)
   - Track APK size (goal: <50MB)
   - Track deprecated API count (goal: 0)

### Future Considerations
1. **Modularization:** Break monolithic app into feature modules
2. **Compose Migration:** Migrate from XML layouts to Jetpack Compose
3. **Unit Testing:** Increase test coverage (current: minimal)
4. **Detekt:** Add Kotlin static analysis tool
5. **ktlint:** Add Kotlin code formatter

---

## Success Metrics 📊

### Current Baseline
- ✅ Unused classes: 0
- ✅ Unused layouts: 0
- ✅ Unused drawables: 0
- ⚠️ Deprecated APIs: 20+
- ❌ ProGuard enabled: No
- ❌ Resource shrinking: No
- ❓ Lint warnings: Unknown
- ❓ Unused dependencies: Unknown

### Target Goals (3 months)
- ✅ Unused classes: 0 (maintain)
- ✅ Unused layouts: 0 (maintain)
- ✅ Unused drawables: 0 (maintain)
- ✅ Deprecated APIs: 0 (from 20+)
- ✅ ProGuard enabled: Yes
- ✅ Resource shrinking: Yes
- ✅ Lint warnings: <10
- ✅ Unused dependencies: 0

### Measurement Commands
```bash
# Count deprecated usage
grep -r "@Deprecated" app/src/main/java --include="*.kt" | wc -l

# Check APK size
ls -lh app/build/outputs/apk/defaultRelease/*.apk

# Count lint warnings
./gradlew lintDefaultRelease | grep "warnings" | tail -1

# Check build time
./gradlew assembleDefaultRelease --profile
```

---

## Summary

**Status:** ✅ Codebase is clean - No dead code removal needed

**Priorities:**
1. 🚀 Enable ProGuard/R8 (biggest impact)
2. 📋 Migrate deprecated APIs (2-3 days)
3. 🔍 Run Android Lint (30 minutes)
4. 📦 Dependency analysis (30 minutes)
5. ⚙️ CI/CD integration (1 hour)

**Total Estimated Effort:** 3-4 days for all priorities

**Expected Outcome:**
- 30-50% smaller APK
- Zero deprecated API usage
- Automated code quality checks
- Cleaner, more maintainable codebase

---

**Document Version:** 1.0  
**Last Updated:** 2026-03-02  
**Next Review:** 2026-06-02 (3 months)
