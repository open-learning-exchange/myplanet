import re

with open("app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt", "r") as f:
    content = f.read()

dummy_methods = """
        @JvmStatic
        fun createGuestUser(username: String?, mRealm: Realm, settings: SharedPreferences): RealmUser? {
            throw UnsupportedOperationException("Moved to UserRepositoryImpl")
        }
        @JvmStatic
        fun populateUsersTable(jsonDoc: JsonObject?, mRealm: Realm?, settings: SharedPreferences): RealmUser? {
            throw UnsupportedOperationException("Moved to UserRepositoryImpl")
        }
        @JvmStatic
        fun isUserExists(realm: Realm, name: String?): Boolean {
            throw UnsupportedOperationException("Moved to UserRepositoryImpl")
        }
        fun updateUserDetails(realm: Realm, userId: String?, firstName: String?, lastName: String?, middleName: String?, email: String?, phoneNumber: String?, level: String?, language: String?, gender: String?, dob: String?, onSuccess: () -> Unit) {
            throw UnsupportedOperationException("Moved to UserRepositoryImpl")
        }
        @JvmStatic
        fun cleanupDuplicateUsers(realm: Realm, onSuccess: () -> Unit) {
            throw UnsupportedOperationException("Moved to UserRepositoryImpl")
        }
"""

methods_to_remove = [
    r"@JvmStatic\s+fun createGuestUser.*?return user\s+}",
    r"@JvmStatic\s+fun populateUsersTable.*?return null\s+}",
    r"private fun insertIntoUsers.*?}\n        }",
    r"@JvmStatic\s+fun isUserExists.*?count\(\) > 0\s+}",
    r"fun updateUserDetails.*?}\n        }",
    r"@JvmStatic\s+fun cleanupDuplicateUsers.*?}\n        }"
]

for pattern in methods_to_remove:
    content = re.sub(pattern, "", content, flags=re.DOTALL)

content = re.sub(r"companion object \{", "companion object {" + dummy_methods, content)

with open("app/src/main/java/org/ole/planet/myplanet/model/RealmUser.kt", "w") as f:
    f.write(content)
