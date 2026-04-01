Let's re-read the PR reviewer's comment carefully:
1. `userRepository` is an interface. Casting it to `RealmRepository` is unsafe. The right fix is to either expose the generic bulk-insert through its own interface/method, or inject a `RealmRepository` instance separately for the generic fallback.
2. Reflection dispatch is still present, just moved `RealmRepository.bulkInsertFromSync` still uses `Constants.classList[table]?.methods` reflection... The stated goal is "refactor to domain repositories" but the generic fallback path is just relocated reflection... This is the core architectural risk and was not addressed.
3. `saveConcatenatedLinksToPrefs` called on every table now. Previously called only inside `insertDocs` (i.e. for non-chat tables).

So the correct approach:
- Remove reflection entirely.
- Add `bulkInsertFromSync(realm: Realm, jsonArray: JsonArray)` to **every** appropriate domain repository for all those tables!
- Inject ALL necessary repositories into `TransactionSyncManager` and call them via a giant `when(table)` block.
- For `saveConcatenatedLinksToPrefs`, ONLY call it for the tables where it was previously called (i.e., non-chat tables). Wait, previously `insertDocs` was called for ALL tables except `chat_history`.
```kotlin
                        if (table == "chat_history") {
                            insertToChat(arr, mRealm)
                        }
                        insertDocs(arr, mRealm, table)
```
Wait! `insertDocs` WAS called for `chat_history`? NO! Wait:
```kotlin
                        if (table == "chat_history") {
                            insertToChat(arr, mRealm)
                        } else {
                            insertDocs(arr, mRealm, table) // WAIT, is it an else?
                        }
```
Let me check `TransactionSyncManager.kt` BEFORE my changes!
