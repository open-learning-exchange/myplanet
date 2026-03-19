1. **Create/Update StringUtils.kt**:
   Move `normalizeText` into a central location. Since `StringUtils` doesn't exist, I will use `org.ole.planet.myplanet.utils.Utilities`. Let's check if `Utilities` exists. Wait, it's used as `Utilities.toast(...)`.
2. **Move normalizeText to Utilities.kt**:
   ```bash
   # Add to Utilities.kt
   ```
3. **Remove `filterCourseByTag` & `normalizeText` from `BaseRecyclerFragment.kt`**.
4. **Remove orphaned import in `BaseRecyclerFragment.kt`**.
5. **Update `CoursesRepositoryImpl.kt` to use `Utilities.normalizeText`**.
