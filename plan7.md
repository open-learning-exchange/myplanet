1. **Create `UserSyncHelper.kt`**
   - Use `write_file` to create `app/src/main/java/org/ole/planet/myplanet/repository/UserSyncHelper.kt` defining `UserSyncHelper` interface with `bulkInsertAchievementsFromSync`, `bulkInsertUsersFromSync`, `getShelfData`, `parseLeadersJson`, and `populateUser`.
2. **Verify `UserSyncHelper.kt`**
   - Use `run_in_bash_session` with `cat` to verify the contents of the newly created `UserSyncHelper.kt`.
3. **Modify `UserRepository.kt` Interface**
   - Use `run_in_bash_session` to run a python script to modify `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`. Remove the methods moved to `UserSyncHelper` and remove `SharedPreferences` from `createGuestUser` and `saveUser`.
4. **Verify `UserRepository.kt`**
   - Use `run_in_bash_session` with `cat` to verify the interface modifications.
5. **Update `UserRepositoryImpl.kt`**
   - Use `run_in_bash_session` to run a python script to edit `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`. Add `UserSyncHelper` to implemented interfaces, modify `createGuestUser` and `saveUser` signatures to remove `settings`, and change usages inside to `sharedPrefManager.rawPreferences` or the already injected `settings`. Remove `settings: SharedPreferences` from `populateUser` and `bulkInsertUsersFromSync` signatures and use injected `@AppPreferences val settings` inside.
6. **Verify `UserRepositoryImpl.kt`**
   - Use `run_in_bash_session` with `cat` to verify the file changes.
7. **Update `RepositoryModule.kt`**
   - Use `run_in_bash_session` to run a python script to insert `@Binds abstract fun bindUserSyncHelper(impl: UserRepositoryImpl): UserSyncHelper` into `RepositoryModule.kt`.
8. **Verify `RepositoryModule.kt`**
   - Use `run_in_bash_session` with `cat` to verify the modification was successful.
9. **Update Callers in `TransactionSyncManager`, `UploadToShelfService`, `LoginSyncManager`**
   - Use `run_in_bash_session` to modify these files to inject `UserSyncHelper` instead of or in addition to `UserRepository` where needed, and fix parameter lists.
10. **Verify Callers Update 1**
    - Use `run_in_bash_session` with `cat` to verify.
11. **Update Callers in UI and Utils (`LoginActivity`, `GuestLoginExtensions`, `LeadersFragment`, `VoicesAdapter`, `SyncActivity`, `ProcessUserDataActivity`)**
    - Use `run_in_bash_session` to modify these files to inject `UserSyncHelper` where needed (for `parseLeadersJson`), and fix parameter lists for `createGuestUser` and `saveUser` (remove `settings`). (Note: `UserProfileFragment` uses `populateUserFields`, not `populateUser`, so it is excluded, and `AuthUtils` does not use the affected methods directly).
12. **Verify Callers Update 2**
    - Use `run_in_bash_session` with `cat` to verify.
13. **Compile and test**
    - Use `run_in_bash_session` to execute relevant tests (e.g., `./gradlew testDebugUnitTest`) to ensure changes are correct and have not introduced regressions.
14. **Pre-commit step**
    - Complete pre commit steps to ensure proper testing, verification, review, and reflection are done.
