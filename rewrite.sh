#!/bin/bash
sed -i '/override suspend fun validateUsername(username: String): String? {/i\
    override suspend fun isUserExists(name: String?): Boolean {\
        return withRealm { realm ->\
            realm.where(RealmUser::class.java)\
                .equalTo("name", name)\
                .not().beginsWith("_id", "guest").count() > 0\
        }\
    }\
' app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt

sed -i 's/suspend fun validateUsername(username: String): String?/suspend fun validateUsername(username: String): String?\n    suspend fun isUserExists(name: String?): Boolean/g' app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt

sed -i 's/RealmUser.createGuestUser(username, realm, settings)?.let { realm.copyFromRealm(it) }/\
            val `object` = JsonObject()\
            `object`.addProperty("_id", "guest_$username")\
            `object`.addProperty("name", username)\
            `object`.addProperty("firstName", username)\
            val rolesArray = JsonArray()\
            rolesArray.add("guest")\
            `object`.add("roles", rolesArray)\
            val startedTransaction = !realm.isInTransaction\
            if (startedTransaction) realm.beginTransaction()\
            val user = populateUsersTable(`object`, realm, settings)\
            if (startedTransaction \&\& realm.isInTransaction) {\
                realm.commitTransaction()\
            }\
            user?.let { realm.copyFromRealm(it) }/g' app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt

# Fix call in TransactionSyncManager
sed -i 's/populateUsersTable(jsonDoc, mRealm, sharedPrefManager.rawPreferences)/UserRepositoryImpl.populateUsersTable(jsonDoc, mRealm, sharedPrefManager.rawPreferences)/g' app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt
sed -i 's/import org.ole.planet.myplanet.repository.ChatRepository/import org.ole.planet.myplanet.repository.ChatRepository\nimport org.ole.planet.myplanet.repository.UserRepositoryImpl/g' app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt

cat << 'INLINE' > append.txt

    companion object {
        @JvmStatic
        fun populateUsersTable(jsonDoc: JsonObject?, mRealm: Realm?, settings: SharedPreferences): RealmUser? {
            if (jsonDoc == null || mRealm == null) return null
            try {
                val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
                val userName = JsonUtils.getString("name", jsonDoc)
                var user: RealmUser? = null

                if (!mRealm.isInTransaction) {
                    mRealm.executeTransaction { realm ->
                        user = realm.where(RealmUser::class.java)
                            .equalTo("_id", id)
                            .findFirst()

                        if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                            val guestUser = realm.where(RealmUser::class.java)
                                .equalTo("name", userName)
                                .beginsWith("_id", "guest_")
                                .findFirst()

                            if (guestUser != null) {
                                val tempData = JsonObject()
                                tempData.addProperty("_id", id)
                                tempData.addProperty("name", guestUser.name)
                                tempData.addProperty("firstName", guestUser.firstName)
                                tempData.addProperty("lastName", guestUser.lastName)
                                tempData.addProperty("middleName", guestUser.middleName)
                                tempData.addProperty("email", guestUser.email)
                                tempData.addProperty("phoneNumber", guestUser.phoneNumber)
                                tempData.addProperty("level", guestUser.level)
                                tempData.addProperty("language", guestUser.language)
                                tempData.addProperty("gender", guestUser.gender)
                                tempData.addProperty("birthDate", guestUser.dob)
                                tempData.addProperty("planetCode", guestUser.planetCode)
                                tempData.addProperty("parentCode", guestUser.parentCode)
                                tempData.addProperty("userImage", guestUser.userImage)
                                tempData.addProperty("joinDate", guestUser.joinDate)
                                tempData.addProperty("isShowTopbar", guestUser.isShowTopbar)
                                tempData.addProperty("isArchived", guestUser.isArchived)

                                val rolesArray = JsonArray()
                                guestUser.rolesList?.forEach { role ->
                                    rolesArray.add(role)
                                }
                                tempData.add("roles", rolesArray)
                                guestUser.deleteFromRealm()
                                user = realm.createObject(RealmUser::class.java, id)
                                user?.let { insertIntoUsers(tempData, it, settings) }
                            }
                        }

                        if (user == null) {
                            user = realm.createObject(RealmUser::class.java, id)
                        }
                        user?.let { insertIntoUsers(jsonDoc, it, settings) }
                    }
                } else {
                    user = mRealm.where(RealmUser::class.java)
                        .equalTo("_id", id)
                        .findFirst()

                    if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                        val guestUser = mRealm.where(RealmUser::class.java)
                            .equalTo("name", userName)
                            .beginsWith("_id", "guest_")
                            .findFirst()

                        if (guestUser != null) {
                            val tempData = JsonObject()
                            tempData.addProperty("_id", id)
                            tempData.addProperty("name", guestUser.name)
                            tempData.addProperty("firstName", guestUser.firstName)
                            tempData.addProperty("lastName", guestUser.lastName)
                            tempData.addProperty("middleName", guestUser.middleName)
                            tempData.addProperty("email", guestUser.email)
                            tempData.addProperty("phoneNumber", guestUser.phoneNumber)
                            tempData.addProperty("level", guestUser.level)
                            tempData.addProperty("language", guestUser.language)
                            tempData.addProperty("gender", guestUser.gender)
                            tempData.addProperty("birthDate", guestUser.dob)
                            tempData.addProperty("planetCode", guestUser.planetCode)
                            tempData.addProperty("parentCode", guestUser.parentCode)
                            tempData.addProperty("userImage", guestUser.userImage)
                            tempData.addProperty("joinDate", guestUser.joinDate)
                            tempData.addProperty("isShowTopbar", guestUser.isShowTopbar)
                            tempData.addProperty("isArchived", guestUser.isArchived)
                            val rolesArray = JsonArray()
                            guestUser.rolesList?.forEach { role ->
                                rolesArray.add(role)
                            }
                            tempData.add("roles", rolesArray)
                            guestUser.deleteFromRealm()
                            user = mRealm.createObject(RealmUser::class.java, id)
                            user?.let { insertIntoUsers(tempData, it, settings) }
                        }
                    }

                    if (user == null) {
                        user = mRealm.createObject(RealmUser::class.java, id)
                    }
                    user?.let { insertIntoUsers(jsonDoc, it, settings) }
                }
                return user
            } catch (err: Exception) {
                err.printStackTrace()
            }
            return null
        }

        @JvmStatic
        private fun insertIntoUsers(jsonDoc: JsonObject?, user: RealmUser, settings: SharedPreferences) {
            if (jsonDoc == null) return

            val planetCodes = JsonUtils.getString("planetCode", jsonDoc)
            val rolesArray = JsonUtils.getJsonArray("roles", jsonDoc)
            val newId = JsonUtils.getString("_id", jsonDoc)

            user.apply {
                _rev = JsonUtils.getString("_rev", jsonDoc)
                _id = newId
                name = JsonUtils.getString("name", jsonDoc)
                setRoles(RealmList<String?>().apply {
                    for (i in 0 until rolesArray.size()) {
                        add(JsonUtils.getString(rolesArray, i))
                    }
                })
                userAdmin = JsonUtils.getBoolean("isUserAdmin", jsonDoc)
                val newJoinDate = JsonUtils.getLong("joinDate", jsonDoc)
                if (newJoinDate != 0L || joinDate == 0L) {
                    joinDate = newJoinDate
                }

                val newFirstName = JsonUtils.getString("firstName", jsonDoc)
                if (newFirstName.isNotEmpty() || firstName.isNullOrEmpty()) {
                    firstName = newFirstName
                }

                val newLastName = JsonUtils.getString("lastName", jsonDoc)
                if (newLastName.isNotEmpty() || lastName.isNullOrEmpty()) {
                    lastName = newLastName
                }

                val newMiddleName = JsonUtils.getString("middleName", jsonDoc)
                if (newMiddleName.isNotEmpty() || middleName.isNullOrEmpty()) {
                    middleName = newMiddleName
                }

                val newEmail = JsonUtils.getString("email", jsonDoc)
                if (newEmail.isNotEmpty() || email.isNullOrEmpty()) {
                    email = newEmail
                }

                val newPhoneNumber = JsonUtils.getString("phoneNumber", jsonDoc)
                if (newPhoneNumber.isNotEmpty() || phoneNumber.isNullOrEmpty()) {
                    phoneNumber = newPhoneNumber
                }

                val newLevel = JsonUtils.getString("level", jsonDoc)
                if (newLevel.isNotEmpty() || level.isNullOrEmpty()) {
                    level = newLevel
                }

                val newLanguage = JsonUtils.getString("language", jsonDoc)
                if (newLanguage.isNotEmpty() || language.isNullOrEmpty()) {
                    language = newLanguage
                }

                val newGender = JsonUtils.getString("gender", jsonDoc)
                if (newGender.isNotEmpty() || gender.isNullOrEmpty()) {
                    gender = newGender
                }

                val newDob = JsonUtils.getString("birthDate", jsonDoc)
                if (newDob.isNotEmpty() || dob.isNullOrEmpty()) {
                    dob = newDob
                }

                val newBirthPlace = JsonUtils.getString("birthPlace", jsonDoc)
                if (newBirthPlace.isNotEmpty() || birthPlace.isNullOrEmpty()) {
                    birthPlace = newBirthPlace
                }

                val newAge = JsonUtils.getString("age", jsonDoc)
                if (newAge.isNotEmpty() || age.isNullOrEmpty()) {
                    age = newAge
                }
                planetCode = planetCodes
                parentCode = JsonUtils.getString("parentCode", jsonDoc)
                if (_id?.isEmpty() == true) {
                    password = JsonUtils.getString("password", jsonDoc)
                }
                password_scheme = JsonUtils.getString("password_scheme", jsonDoc)
                iterations = JsonUtils.getString("iterations", jsonDoc)
                derived_key = JsonUtils.getString("derived_key", jsonDoc)
                salt = JsonUtils.getString("salt", jsonDoc)
                isShowTopbar = true
                isArchived = JsonUtils.getBoolean("isArchived", jsonDoc)
                addImageUrl(jsonDoc)
            }

            if (planetCodes.isNotEmpty()) {
                settings.edit { putString("planetCode", planetCodes) }
            }
        }
    }
INLINE
sed -i -e '$d' app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt
cat append.txt >> app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt
echo "}" >> app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt
