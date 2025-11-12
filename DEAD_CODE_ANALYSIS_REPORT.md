# Dead Code Analysis Report - myplanet Android Repository

**Generated:** 2025-11-12
**Repository:** github.com/open-learning-exchange/myplanet
**Branch:** claude/remove-dead-code-analysis-011CV4cdprqoN4moZXJRdGhZ
**Analyzed Files:** 328 Kotlin files, 166 layouts, 125 drawables, 852 strings

---

## Executive Summary

The myplanet Android codebase is **exceptionally well-maintained** with minimal dead code. This analysis found:

✅ **0 unused Kotlin classes**
✅ **0 unused XML layouts**
✅ **0 unused drawable resources**
✅ **0 completely unused string resources**
⚠️ **1 unused Gradle dependency** (JUnit - no tests exist)
💡 **30-40 duplicate string resources** (consolidation opportunity)
💡 **28 redundant PNG drawables** (optimization opportunity)

---

## Detailed Findings

### 1. Kotlin/Java Code Analysis

#### ✅ Unused Classes: NONE FOUND

**Analysis Scope:**
- 328 Kotlin files in `app/src/main/java/org/ole/planet/myplanet/`
- Excluded: Activities, Fragments, Services, Workers, Application classes, Hilt modules, Realm models, Repositories, ViewModels (used via framework/injection)
- Focused on: Utility classes, Helper classes, Callback interfaces

**Result:** All classes are actively referenced and used throughout the codebase.

**Notable Verified Classes:**
- All 16 callback interfaces in `/callback/` are used
- All 30+ utility classes in `/utilities/` are used
- All custom views are referenced in XML or Kotlin
- All helper/data classes have active references

#### ⚠️ Code Using Reflection (Appears Unused but Actually Used)

**IMPORTANT:** The following code uses reflection/dynamic loading and may appear unused in static analysis:

**Location 1: TransactionSyncManager.kt:174-188**
```kotlin
// Dynamically calls insert() methods on Realm models
private fun callMethod(mRealm: Realm, jsonDoc: JsonObject, type: String)
```

**Affected Classes (15 models with insert methods):**
- RealmNews, RealmTag, RealmOfflineActivity, RealmRating, RealmSubmission
- RealmMyCourse, RealmAchievement, RealmFeedback, RealmMyTeam, RealmTeamTask
- RealmMeetup, RealmMyHealthPojo, RealmCertification, RealmTeamLog, RealmCourseProgress

These `insert()` methods are called via reflection and would appear unused in static analysis.

**Location 2: PermissionActivity.kt:40-45**
```kotlin
// Reflects AppOpsManager methods for permission checking
AppOpsManager::class.java.getMethod("unsafeCheckOpNoThrow" or "checkOpNoThrow")
```

---

### 2. XML Resources Analysis

#### ✅ Layouts: 0 Unused (178 files analyzed)

**Breakdown:**
- 166 base layouts in `layout/`
- 12 variant layouts (night, landscape, tablet)
- All layouts referenced via ViewBinding (119), R.layout (25), or XML includes (12)

**Verification Method:**
- Checked ViewBinding class usage
- Checked R.layout.* references
- Checked @layout/ includes in XML
- Checked AndroidManifest.xml

**Result:** No unused layouts found. The codebase maintains excellent layout hygiene.

---

#### ✅ Drawables: 0 Unused (125 unique drawables, 191 files)

**Breakdown:**
- 66 XML drawables (vectors, selectors, backgrounds)
- 125 PNG images across multiple densities

**All drawables are referenced** via R.drawable or @drawable/ in layouts.

**💡 Optimization Opportunity: Redundant Density Files**

The following 7 drawables have **both** vector XML (anydpi) **and** PNG files for multiple densities. Since minSdkVersion=26 (Android 8.0), the PNGs are unnecessary:

**Redundant PNG files (28 files total, ~50-100 KB):**
```
drawable-hdpi/ic_down.png       → Use drawable-anydpi/ic_down.xml
drawable-hdpi/ic_download.png   → Use drawable-anydpi/ic_download.xml
drawable-hdpi/ic_edit.png       → Use drawable-anydpi/ic_edit.xml
drawable-hdpi/ic_eye.png        → Use drawable-anydpi/ic_eye.xml
drawable-hdpi/ic_mic.png        → Use drawable-anydpi/ic_mic.xml
drawable-hdpi/ic_star.png       → Use drawable-anydpi/ic_star.xml
drawable-hdpi/ic_up.png         → Use drawable-anydpi/ic_up.xml

(plus mdpi, xhdpi, xxhdpi versions of each)
```

**Recommendation:** Remove these 28 PNG files to reduce APK size. The anydpi XML vectors will handle all densities perfectly.

**Drawables Loaded Dynamically:**

These drawables are loaded via `Resources.getIdentifier()` and might appear unused:
- `ic_myhealth`
- `my_achievement`
- `ic_submissions`
- `ic_my_survey`
- `ic_references`
- `ic_calendar`
- `ic_mypersonals`

**Locations:**
- `BaseDashboardFragmentPlugin.kt:117`
- `AdapterMyLife.kt:52`

---

#### ⚠️ Strings: 0 Completely Unused (852 strings analyzed)

**Usage Statistics:**
- Strings used only once: 617 (72%)
- Strings used 2-3 times: 179 (21%)
- Strings used 4+ times: 56 (7%)

**💡 Consolidation Opportunity: Duplicate Strings**

**30-40 string resources have duplicate or near-identical values:**

| Current Strings | Values | Recommendation |
|----------------|--------|----------------|
| `allergies` + `allergy` | "Allergies" / "allergies" | Keep `allergies` |
| `diagno` + `diagnosis` | "diagnosis" / "Diagnosis" | Keep `diagnosis` |
| `medications` + `medicay` | "Medications" / "medications" | Keep `medications` |
| `treatments` + `treat` | "treatments" / "Treatments" | Keep `treatments` |
| `referrals` + `referral` | "referrals" / "Referrals" | Keep `referrals` |
| `btn_sync` + `sync` | "Sync" / "sync" | Keep `sync` |
| `community` + `menu_community` | Both "Community" | Keep `menu_community` |
| `courses` + `menu_courses` | Both "Courses" | Keep `menu_courses` |
| `library` + `menu_library` | Both "Library" | Keep `menu_library` |
| `logout` + `menu_logout` | Both "Logout" | Keep `menu_logout` |
| `name` + `name_normal` | Both "Name" | Keep `name` |
| `no_data_available` + `nodata` | Similar meaning | Keep `no_data_available` |
| `no_questions` + `no_questions_available` | Similar | Keep `no_questions_available` |
| `remove` + `btn_remove_lib` | Both "Remove" | Keep `remove` |
| `txt_cancel` + `btn_sync_cancel` | Both "Cancel" | Keep generic `txt_cancel` |

**Plus more pairs...**

**Suspicious Test/Temporary Strings:**
- `test_size`, `tests`, `retake_test`, `labtest` - May be legitimate exam features or dead code
- `float_placeholder`, `entEmptyDescription` - Check if needed
- `Q`, `Q1` - Consider more descriptive names

**⚠️ IMPORTANT:** When removing duplicate strings, also update localized versions:
- `values-ar/strings.xml` (Arabic)
- `values-es/strings.xml` (Spanish)
- `values-fr/strings.xml` (French)
- `values-ne/strings.xml` (Nepali)
- `values-so/strings.xml` (Somali)

---

### 3. Gradle Dependencies Analysis

#### ❌ Unused Dependency: JUnit

**Dependency:** `testImplementation("junit:junit:4.13.2")`

**Evidence:**
1. No test directories exist (`src/test/` or `src/androidTest/`)
2. Zero test files found across 328 Kotlin files
3. Zero `@Test` annotations found
4. Zero JUnit imports found

**Recommendation:** Remove this dependency. If tests are added in the future, it can be re-added.

---

#### ✅ All Other Dependencies: Verified as Used

**Sample Verified Dependencies:**
- **UI:** clans:fab (7 layouts), circleimageview (17 layouts), CardView (20+ layouts)
- **Charts:** MPAndroidChart (fragment_my_activity.xml)
- **Image Loading:** Glide (15+ files)
- **Markdown:** Markwon (2 files)
- **Maps:** osmdroid (OfflineMapActivity.kt)
- **Security:** androidx.security:security-crypto (SecurePrefs.kt)
- **CSV:** opencsv (CSVViewerActivity.kt)
- **Crypto:** de.rtner:PBKDF2 (AndroidDecrypter.kt)

---

#### ⚠️ Deprecated Dependency (Still in Use)

**Dependency:** `androidx.localbroadcastmanager:localbroadcastmanager:1.1.0`

**Status:** Used in 6 files but deprecated by Google

**Recommendation:** Consider migrating to LiveData, Flows, or event buses in future refactoring. Not urgent as it still functions correctly.

---

## Reflection & Dynamic Loading Summary

**5 locations use reflection/dynamic resource loading:**

1. **PermissionActivity.kt:40-45** - Reflects AppOpsManager methods
2. **TransactionSyncManager.kt:174-188** - Reflects Realm model insert methods (15 classes affected)
3. **BaseDashboardFragmentPlugin.kt:117** - Dynamic drawable loading by name
4. **DashboardActivity.kt:970, 1003** - Dynamic system resource loading
5. **AdapterMyLife.kt:52** - Dynamic drawable loading by name

**Classes/resources protected from "unused" analysis:**
- 15 Realm model classes with `insert()` methods
- 7 drawable resources loaded dynamically
- AppOpsManager permission check methods

---

## Recommended Actions

### Priority 1: Safe Removals (Confirmed Dead Code)

#### ✅ Remove unused Gradle dependency
```gradle
// Remove from app/build.gradle:
testImplementation("junit:junit:4.13.2")
```

**Estimated impact:** Minimal (~70 KB library, not included in release APK since it's testImplementation)

---

### Priority 2: Optimization Opportunities (Not Dead Code, But Redundant)

#### 💡 Remove redundant PNG drawables (28 files)

**Files to remove:**
```
app/src/main/res/drawable-hdpi/ic_down.png
app/src/main/res/drawable-hdpi/ic_download.png
app/src/main/res/drawable-hdpi/ic_edit.png
app/src/main/res/drawable-hdpi/ic_eye.png
app/src/main/res/drawable-hdpi/ic_mic.png
app/src/main/res/drawable-hdpi/ic_star.png
app/src/main/res/drawable-hdpi/ic_up.png

(repeat for mdpi, xhdpi, xxhdpi)
```

**Estimated impact:** ~50-100 KB APK size reduction

**Safety:** 100% safe since anydpi XML vectors exist and minSdkVersion=26

---

#### 💡 Consolidate duplicate string resources (30-40 strings)

**Process:**
1. Identify canonical string to keep (e.g., `diagnosis` over `diagno`)
2. Replace all references with canonical string
3. Remove duplicate from all 6 localized strings.xml files
4. Test app thoroughly

**Estimated impact:**
- Reduced maintenance burden
- Cleaner codebase
- ~5-10% reduction in strings.xml size

**Safety:** Medium risk. Requires thorough testing since string replacements could break UI.

---

### Priority 3: Future Considerations

#### 🔍 Investigate suspicious strings

Review these strings to determine if they're legitimate or legacy:
- `test_size`, `tests`, `retake_test`, `labtest`
- `float_placeholder`, `entEmptyDescription`
- `Q`, `Q1`

#### 🔧 Consider migrating from LocalBroadcastManager

The deprecated `androidx.localbroadcastmanager` is used in 6 files. Plan migration to modern alternatives:
- LiveData
- StateFlow/SharedFlow
- EventBus alternatives

---

## Analysis Methodology

### Tools Used
- **Grep/Pattern Matching:** Searched for import statements, R.layout references, @drawable references
- **ViewBinding Analysis:** Verified generated binding class usage
- **XML Parsing:** Checked for \<include\> tags and attribute references
- **Cross-referencing:** Verified resources across Kotlin, XML, and Gradle files

### Limitations
This analysis **cannot detect:**
- String-based resource names constructed at runtime (beyond the identified reflection cases)
- References in native code (JNI)
- References in JavaScript or WebView code
- Resources used in future/unmerged branches
- Code paths that are never executed but intentionally kept

### False Positive Prevention
The analysis was designed to be **conservative** and avoid false positives:
- Excluded framework-registered classes (Activities, Fragments, etc.)
- Excluded dependency-injected classes
- Excluded Realm models (used by ORM)
- Verified dynamic loading patterns
- Checked multiple reference patterns (R.* and @*/*)

---

## Conclusion

The myplanet Android repository demonstrates **excellent code quality and maintenance practices**. Very little true dead code exists:

✅ **0 unused classes**
✅ **0 unused layouts**
✅ **0 unused drawables**
✅ **0 completely unused strings**
❌ **1 unused test dependency**

The main opportunities for improvement are **optimizations rather than dead code removal**:
- Remove 28 redundant PNG files (50-100 KB savings)
- Consolidate 30-40 duplicate strings (maintenance improvement)
- Consider removing unused test dependency

This is a well-architected, actively maintained codebase with minimal technical debt.

---

## Next Steps

1. **Review this report** with the team
2. **Approve removals:**
   - Confirm removal of JUnit dependency
   - Confirm removal of 28 redundant PNG drawables
   - Select string duplicates to consolidate
3. **Execute removals** with clear commit messages
4. **Test thoroughly** especially after string consolidation
5. **Push changes** to branch

---

**Report prepared by:** Claude Code Analysis Agent
**Date:** 2025-11-12
**Contact:** For questions about this analysis, refer to the methodology section above.
