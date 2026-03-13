1. Modify `ReferencesFragment.kt`:
   - Create a new inner class `ReferenceAdapter` extending `ListAdapter<Reference, ReferencesFragment.ViewHolderReference>(ReferenceDiffCallback())`.
   - Implement `ReferenceDiffCallback` using `DiffUtil.ItemCallback`.
   - Refactor `setRecyclerAdapter` to initialize `ReferenceAdapter` once and use `submitList` to populate it.
2. Modify `UserProfileFragment.kt`:
   - Create a new inner class `StatsAdapter` extending `ListAdapter<Pair<String, String?>, UserProfileFragment.ViewHolderRowStat>(StatsDiffCallback())`.
   - Implement `StatsDiffCallback` using `DiffUtil.ItemCallback`.
   - Refactor `setupStatsRecycler` to initialize `StatsAdapter` once and use `submitList` to populate it. Ensure that the logic mapping the `keys` and `map` values is correctly transferred to handle the `Pair` correctly.
3. Complete pre commit steps
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
