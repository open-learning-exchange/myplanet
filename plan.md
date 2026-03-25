1. Add `suspend fun resetDatabase()` to `ConfigurationsRepository` interface.
2. Implement it in `ConfigurationsRepositoryImpl` by calling `databaseService.clearAll()`.
3. In `SyncActivity`, replace `databaseService.clearAll()` with `configurationsRepository.resetDatabase()`. Remove `@Inject lateinit var databaseService: DatabaseService` as it's no longer needed.
4. In `SettingsActivity`, replace `@Inject lateinit var databaseService: DatabaseService` with `@Inject lateinit var configurationsRepository: ConfigurationsRepository`. Replace `databaseService.clearAll()` with `configurationsRepository.resetDatabase()`.
5. Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
6. Submit the change.
