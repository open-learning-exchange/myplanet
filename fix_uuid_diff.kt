<<<<<<< SEARCH
        documentList.forEach { jsonDoc ->
            try {
                val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
                val userName = JsonUtils.getString("name", jsonDoc)
                var user = existingUsersMap[id]

                if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                    user = migrateGuestUser(realm, id, userName, settings)
                    user?.let { existingUsersMap[id] = it }
                }

                if (user == null) {
                    user = realm.createObject(RealmUser::class.java, id)
                    user?.let { existingUsersMap[id] = it }
                }
                user?.let { insertIntoUsers(jsonDoc, it, settings) }
            } catch (err: Exception) {
                err.printStackTrace()
            }
        }
=======
        // Iterate with pre-assigned IDs
        for (i in documentList.indices) {
            val jsonDoc = documentList[i]
            try {
                val id = ids[i]
                val userName = org.ole.planet.myplanet.utils.JsonUtils.getString("name", jsonDoc)
                var user = existingUsersMap[id]

                if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                    user = migrateGuestUser(realm, id, userName, settings)
                    user?.let { existingUsersMap[id] = it }
                }

                if (user == null) {
                    user = realm.createObject(RealmUser::class.java, id)
                    user?.let { existingUsersMap[id] = it }
                }
                user?.let { insertIntoUsers(jsonDoc, it, settings) }
            } catch (err: Exception) {
                err.printStackTrace()
            }
        }
>>>>>>> REPLACE
