# Dead Code Review (myplanet Android)

## Methodology
- Ran Android Lint for the Lite debug variant to capture unused resources and other potential dead code (`./gradlew --no-daemon --console=plain :app:lintLiteDebug`).
- Reviewed lint XML output to locate `UnusedResources` warnings and cross-checked flagged items for dynamic or reflective access in source code.
- Searched the codebase with ripgrep for flagged identifiers to confirm actual usage patterns.

## Findings
### Kotlin/Java classes, methods, and properties
- Lint did not flag any unused Kotlin/Java classes or members. No additional unused code was identified during manual spot-checks.

### XML resources
| Resource | Type | Lint status | Notes |
| --- | --- | --- | --- |
| `@drawable/ic_mypersonals` | Drawable | Still flagged as unused after clean-up | **False positive.** Used dynamically via resource name lookups (e.g., `AdapterMyLife` uses `getIdentifier(myLife.imageId, "drawable", …)` with `imageId = "ic_mypersonals"`, and `BaseDashboardFragmentPlugin` populates `RealmMyLife` with this icon id). Removing would break MyLife navigation. |
| `@string/hello_blank_fragment` | String (all locales) | Flagged as unused → **Removed** | Template string not referenced anywhere. |
| `@string/no_items` | String (all locales) | Flagged as unused → **Removed** | Not referenced in code or layouts. |

### Gradle modules and plugins
- No unused modules or Gradle plugins were detected. Dependency review did not reveal clear unused artifacts without deeper feature knowledge; left unchanged.

## Recommendations
1. Retain `ic_mypersonals` because it is accessed dynamically; if future refactors make icon lookups static, the drawable can be revisited.
2. Unused template strings (`hello_blank_fragment`, `no_items`) removed from all locale files.
3. Re-run lint after future refactors to ensure no new unused resources appear and to validate subsequent clean-ups.
