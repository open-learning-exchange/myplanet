# Wait, `settings` parameter is missing! But wait, `LoginActivity` passes it?
# In `LoginActivity` `saveUserInfoPref`, does it just say "saveUserInfoPref("", model)"? Yes.
# In `GuestLoginExtensions.kt` `saveUserInfoPref("", model)`? Yes.
# And `ProcessUserDataActivity.kt` has `suspend fun saveUserInfoPref(password: String?, user: RealmUser?)`.
# But wait, previously it was:
# `saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUser?)`
# And `LoginActivity` was passing `settings`, which was `getSharedPreferences(PREFS_NAME, MODE_PRIVATE)`.
# But `SyncActivity` also does `settings = prefData.rawPreferences` in `setUpChild()`, not `onCreate()`.
# Wait, `LoginActivity` inherits `SyncActivity`. Does it call `setUpChild()`? Yes, it probably does.
# But wait, look at `ProcessUserDataActivity.saveUserInfoPref`.
# ```kotlin
#         withContext(dispatcherProvider.io) {
#             SecurePrefs.saveCredentials(this@ProcessUserDataActivity, settings, user?.name, password)
#         }
# ```
# `SecurePrefs` uses `settings` which is `this.settings`.
# If `this.settings` is initialized, it works.
# What is the difference before and after?
# Ah! Look at `UserRepositoryImpl.createGuestUser`.
# ```kotlin
#            val `object` = JsonObject()
#            `object`.addProperty("_id", "guest_$username")
#            `object`.addProperty("name", username)
# ```
# Notice `firstName` used to be `username`.
# ```kotlin
#            `object`.addProperty("firstName", username)
# ```
# `insertIntoUsers` does:
# ```kotlin
#            val newFirstName = JsonUtils.getString("firstName", jsonDoc)
#            if (newFirstName.isNotEmpty() || firstName.isNullOrEmpty()) {
#                firstName = newFirstName
#            }
# ```
# Wait! In the video, the user entered "a". The drawer showed `guest_a` as the ID. The name field was blank!
# Why was the name blank?
# Because `saveUserInfoPref` does this:
# ```kotlin
#         prefData.rawPreferences.edit().apply {
#             remove("password")
#             putString("firstName", user?.firstName)
#             putString("lastName", user?.lastName)
#             putString("middleName", user?.middleName)
# ```
# It saves `firstName` to `SharedPreferences`.
# If `firstName` is null, it removes or sets null?
# Wait! What does `user?.name` have?
# In `insertIntoUsers`:
# ```kotlin
#             name = JsonUtils.getString("name", jsonDoc)
# ```
# Did `JsonUtils.getString("name", jsonDoc)` start returning `null`?
# NO, we didn't touch `JsonUtils`!
# So why would `name` or `firstName` be empty?
# BECAUSE `JsonUtils.getString` uses `Gson`.
# What did we change in `insertIntoUsers`?
# We changed `settings` to `this.settings`!
# That shouldn't break anything.
# Wait!
# In `populateUser`:
# ```kotlin
#            val userName = JsonUtils.getString("name", jsonDoc)
# ```
# If `mRealm.isInTransaction`, it uses `realm.createObject(RealmUser::class.java, id)`
# And then `insertIntoUsers(jsonDoc, it, this.settings)`.
# Everything looks completely identical.
