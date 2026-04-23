# If `ProcessUserDataActivity.settings` is uninitialized when `saveUserInfoPref` is called, it might throw an exception, so `onLogin()` is never reached?
# If `ProcessUserDataActivity.settings` throws `UninitializedPropertyAccessException`, the coroutine catches it or crashes?
# `GuestLoginExtensions.kt` does:
# `saveUserInfoPref("", model)`
# Wait! In `GuestLoginExtensions.kt` `showGuestLoginDialog`, the context is `LoginActivity` (`this@showGuestLoginDialog`).
# `LoginActivity` extends `SyncActivity`, which extends `ProcessUserDataActivity`.
# In `LoginActivity.onCreate()`, it calls `super.onCreate()` which calls `SyncActivity.onCreate()` which calls `ProcessUserDataActivity.onCreate()`.
# In `ProcessUserDataActivity.onCreate()`:
# ```kotlin
#        settings = getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)
# ```
# So `settings` IS initialized.
# Why would guest login fail?
# The video shows: user types "a", presses Login. The dialog goes away, but the user is NOT logged in.
# BUT wait! If it doesn't log in, `SyncActivity` `onLogin()` isn't doing its job?
# Or maybe `ProcessUserDataActivity.settings` was supposed to be the `settings` from `LoginActivity`? But they are the SAME class (`LoginActivity` inherits `ProcessUserDataActivity`).
# Wait, look closely at `saveUserInfoPref` in `ProcessUserDataActivity`:
# ```kotlin
#    suspend fun saveUserInfoPref(password: String?, user: RealmUser?) {
#        withContext(dispatcherProvider.io) {
#            SecurePrefs.saveCredentials(this@ProcessUserDataActivity, settings, user?.name, password)
#        }
#        this.settings = settings
#        prefData.setUserId(user?.id ?: "")
# ```
# Notice `this.settings = settings`.
# Before, it was `suspend fun saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUser?)`.
# And we changed it to `suspend fun saveUserInfoPref(password: String?, user: RealmUser?)`.
# So `this.settings = settings` became `this.settings = this.settings`. That's harmless.
# BUT did we change `ProcessUserDataActivity.settings` anywhere?
# What if `ProcessUserDataActivity` didn't have `settings` initialized?
# Let's check `ProcessUserDataActivity.kt`!
