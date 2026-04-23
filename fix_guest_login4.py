# The user issue "not able to login as guest if I login username is null"
# And showing a screenshot or video where the user types "a", and "username is null" (or it doesn't log in).
# Wait, look at `UserRepositoryImpl` and `createGuestUser`.
#
# `createGuestUser` originally took `settings`.
# ```kotlin
#    override suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser? {
#        return withRealm { realm ->
#            val `object` = JsonObject()
#            `object`.addProperty("_id", "guest_$username")
#            `object`.addProperty("name", username)
#            `object`.addProperty("firstName", username)
#            val rolesArray = JsonArray()
#            rolesArray.add("guest")
#            `object`.add("roles", rolesArray)
#            val startedTransaction = !realm.isInTransaction
#            if (startedTransaction) realm.beginTransaction()
#            val user = populateUser(`object`, realm, settings)
#            if (startedTransaction && realm.isInTransaction) {
#                realm.commitTransaction()
#            }
#            user?.let { realm.copyFromRealm(it) }
#        }
#    }
# ```
# Now `createGuestUser` calls `populateUser(object, realm)` which uses `this.settings`.
# `this.settings` in `UserRepositoryImpl` is injected via Dagger/Hilt:
# ```kotlin
#    @param:AppPreferences private val settings: SharedPreferences,
# ```
# Are we sure `this.settings` works exactly the same as the `settings` from `LoginActivity`?
# In `LoginActivity`, `settings` was `getSharedPreferences(PREFS_NAME, MODE_PRIVATE)`.
# Yes, `@AppPreferences` is exactly the same.
# Wait! In `populateUser`, does it actually save anything to `settings`? No, it just passes it to `insertIntoUsers`.
# Let's look at `insertIntoUsers`!
