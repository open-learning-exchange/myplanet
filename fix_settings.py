# If there is no `settings` in `LoginActivity`, then it inherited `settings` from `SyncActivity`/`ProcessUserDataActivity`.
# So `saveUserInfoPref(settings, "", model)` was literally just passing `this.settings` to `saveUserInfoPref(settings...)`, which did `this.settings = settings`.
# This confirms that removing `settings` from `saveUserInfoPref` changed absolutely nothing about the preferences.
# What else could cause "username is null"?
# Wait! Does `createGuestUser` return null?
# The video shows they clicked Login, the dialog closed, but they stayed on the Login screen!
# If it stayed on the Login screen, it means `onLogin()` didn't finish, or crashed, or `model` was null!
# Why would `model` be null?
# Let's check `UserRepositoryImpl.createGuestUser`.
#     override suspend fun createGuestUser(username: String): RealmUser? {
#         return withRealm { realm ->
#             val `object` = JsonObject()
#             `object`.addProperty("_id", "guest_$username")
#             `object`.addProperty("name", username)
#             `object`.addProperty("firstName", username)
#             val rolesArray = JsonArray()
#             rolesArray.add("guest")
#             `object`.add("roles", rolesArray)
#             val startedTransaction = !realm.isInTransaction
#             if (startedTransaction) realm.beginTransaction()
#             val user = populateUser(`object`, realm)
#             if (startedTransaction && realm.isInTransaction) {
#                 realm.commitTransaction()
#             }
#             user?.let { realm.copyFromRealm(it) }
#         }
#     }
# What did I change in `populateUser` or `createGuestUser`?
# I changed `populateUser` from `populateUser(object, realm, settings)` to `populateUser(object, realm)`.
# Inside `populateUser`:
# ```
#             if (!mRealm.isInTransaction) {
#                 mRealm.executeTransaction { realm ->
#                     user = realm.where(RealmUser::class.java) ...
# ```
# WAIT!
# `createGuestUser` starts a transaction (`realm.beginTransaction()`) IF `!realm.isInTransaction`.
# Then it calls `populateUser`.
# BUT `populateUser` also checks `if (!mRealm.isInTransaction)`.
# `mRealm` in `populateUser` is the SAME `realm`.
# So `mRealm.isInTransaction` is TRUE!
# So it enters the `else` block:
# ```
#             } else {
#                 user = mRealm.where(RealmUser::class.java) ...
#                 if (user == null) {
#                     user = mRealm.createObject(RealmUser::class.java, id)
#                 }
#                 user?.let { insertIntoUsers(jsonDoc, it, this.settings) }
#             }
# ```
# It creates the object, calls `insertIntoUsers`, and returns `user`.
# Wait!
# Does `insertIntoUsers` throw an exception?
# Let's check `insertIntoUsers` again.
