# Dead Code Analysis Report

## Summary
A comprehensive analysis of the `myplanet` Android repository was conducted to identify dead code, including unused classes, resources, and dependencies.

## Findings

### Unused String Resources
The following string resources were found to be unused in the codebase (not referenced in Java/Kotlin or XML files):

1.  `planet_not_available`
2.  `planet_server_not_reachable`
3.  `checking_version`
4.  `failed_to_mark_as_read`

These strings were also present in localized values files (`values-ar`, `values-es`, `values-fr`, `values-ne`, `values-so`) and will be removed.

### Classes
- **Analysis Method**: Scanned all Kotlin/Java files and checked for usages of declared classes.
- **Result**: No confirmed unused classes were found. Potential candidates were verified and found to be used either directly, via Hilt/Dagger injection, or via reflection/framework contracts (e.g., `RealmConnectionPool`, `BroadcastServiceEntryPoint`, `ViewExtensions`).

### XML Resources (Layouts, Drawables, Colors)
- **Analysis Method**: Scanned `res/layout`, `res/drawable`, and `res/values/colors.xml` and searched for references (`R.type.name`, `@type/name`, Binding classes).
- **Result**: No unused layouts, drawables, or colors were found. The resource usage appears to be clean.

### Gradle Dependencies
- **Analysis Method**: Checked `app/build.gradle` and `libs.versions.toml` against imports and usages in the code.
- **Result**: Checked key libraries including `opencsv`, `slidingpanelayout`, `circular-progress-view`, `fab`, `materialdrawer`, `mpAndroidChart`, `osmdroid`, `security-crypto`, `pbkdf2`. All were found to be used.

## Conclusion
The codebase is well-maintained with minimal dead code. Only a few unused string resources were identified for removal.
