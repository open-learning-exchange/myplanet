# If guest login is broken, what does "username is null" mean?
# In `showGuestLoginDialog` (GuestLoginExtensions.kt):
# It takes `binding.etUserName.text.toString().trim()`.
# Then it passes it to `userRepository.createGuestUser(username)`.
# Then `saveUsers(username, "", "guest")`.
# Then `saveUserInfoPref("", model)`.
# Wait, `model` is returned by `userRepository.createGuestUser(username)`.
# Does `model` have a null `name`?
# In `createGuestUser`:
# ```kotlin
#             val `object` = JsonObject()
#             `object`.addProperty("_id", "guest_$username")
#             `object`.addProperty("name", username)
#             `object`.addProperty("firstName", username)
# ```
# `populateUser` uses `insertIntoUsers`.
# `insertIntoUsers` does:
# ```kotlin
#             _rev = JsonUtils.getString("_rev", jsonDoc)
#             _id = newId
#             name = JsonUtils.getString("name", jsonDoc)
# ```
# If `name` is null, then `guest_$username` or `username` might be empty?
# Wait! Look at `populateUser`!
# ```kotlin
#             val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
#             val userName = JsonUtils.getString("name", jsonDoc)
#             var user: RealmUser? = null
#
#             if (!mRealm.isInTransaction) {
#                 mRealm.executeTransaction { realm ->
#                     user = realm.where(RealmUser::class.java)
#                         .equalTo("_id", id)
#                         .findFirst()
#
#                     if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
#                         user = migrateGuestUser(realm, id, userName, this.settings)
#                     }
#
#                     if (user == null) {
#                         user = realm.createObject(RealmUser::class.java, id)
#                     }
#                     user?.let { insertIntoUsers(jsonDoc, it, this.settings) }
#                 }
# ```
# WAIT! `createGuestUser` DOES:
# ```kotlin
#            val startedTransaction = !realm.isInTransaction
#            if (startedTransaction) realm.beginTransaction()
#            val user = populateUser(`object`, realm)
#            if (startedTransaction && realm.isInTransaction) {
#                realm.commitTransaction()
#            }
# ```
# But `populateUser` checks `if (!mRealm.isInTransaction) { mRealm.executeTransaction ... } else { ... }`
# In the `else` block:
# ```kotlin
#             } else {
#                 user = mRealm.where(RealmUser::class.java)
#                     .equalTo("_id", id)
#                     .findFirst()
#
#                 if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
#                     user = migrateGuestUser(mRealm, id, userName, this.settings)
#                 }
#
#                 if (user == null) {
#                     user = mRealm.createObject(RealmUser::class.java, id)
#                 }
#                 user?.let { insertIntoUsers(jsonDoc, it, this.settings) }
#             }
# ```
# Is `insertIntoUsers` mutating `it` correctly? YES.
# Does `user` have `name`? Yes, `user.name = JsonUtils.getString("name", jsonDoc)`
# Wait! In `GuestLoginExtensions.kt`:
# `val model = userRepository.createGuestUser(username)`
# `model` is returned.
# `saveUsers(username, "", "guest")` saves to SharedPreferences.
# `saveUserInfoPref("", model)` calls `prefData.setUserName(user?.name ?: "")`.
# What does `model` contain?
# In `createGuestUser`:
# ```kotlin
#             user?.let { realm.copyFromRealm(it) }
# ```
# Wait! `realm.copyFromRealm(it)` copies the object from Realm.
# Why would `username` be null?
