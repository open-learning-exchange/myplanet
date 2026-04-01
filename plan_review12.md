So yes, I can delete `classList` entirely from `Constants.kt`.

Let's do the updates exactly as planned.
1. `Constants.kt`: Remove `classList` and `initClasses`.
2. `ServiceModule.kt`: Provide `RealmRepository`.
3. `TransactionSyncManager.kt`: Inject `realmRepository: RealmRepository`, update `sync` with `realmRepository.bulkInsertFromSync`, wrap `saveConcatenatedLinksToPrefs` in `if (table != "chat_history")`.
4. `RealmRepository.kt`: Replace reflection with explicit `when` dispatch.
