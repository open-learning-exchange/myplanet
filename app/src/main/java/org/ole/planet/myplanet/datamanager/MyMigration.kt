package org.ole.planet.myplanet.datamanager

import io.realm.DynamicRealm
import io.realm.RealmMigration
import org.ole.planet.myplanet.utilities.JsonUtils.normalizeText

class MyMigration : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var oldVersion = oldVersion
        val schema = realm.schema

        if (oldVersion == 4L) {
            val conversationSchema = schema.get("Conversation")
            conversationSchema?.let {
                it.addField("normalizedQuery", String::class.java)
                    .addField("normalizedResponse", String::class.java)
                    .transform { obj ->
                        val query = obj.getString("query")
                        val response = obj.getString("response")
                        obj.setString("normalizedQuery", query?.let { normalizeText(it) })
                        obj.setString("normalizedResponse", response?.let { normalizeText(it) })
                    }
            }
            oldVersion++
        }
    }
}
