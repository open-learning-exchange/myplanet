Wait, if `UserRepository` shouldn't have `SharedPreferences` in its signature, `saveUser` and `createGuestUser` should just not require `SharedPreferences` parameter.
Since `UserRepositoryImpl` already has `sharedPrefManager: SharedPrefManager` constructor parameter (let's verify this but I saw it earlier), we can use `sharedPrefManager.rawPreferences` inside `createGuestUser` and `saveUser` and `populateUser`.

Let's check `sharedPrefManager` in `UserRepositoryImpl.kt`.
