package org.ole.planet.myplanet.data

import io.realm.DynamicRealm
import io.realm.RealmMigration

class RealmMigrations : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        val schema = realm.schema
        var version = oldVersion

        if (version == 4L) {
            schema.get("RealmMyTeam")
                ?.addIndex("teamId")
                ?.addIndex("userId")
                ?.addIndex("docType")
            schema.get("RealmMyCourse")
                ?.addIndex("courseId")
            schema.get("RealmExamQuestion")
                ?.addIndex("examId")
            version++
        }

        if (version == 5L) {
            schema.get("RealmNotification")
                ?.addField("link", String::class.java)
                ?.setNullable("link", true)
                ?.addField("priority", Int::class.java)
                ?.addField("isFromServer", Boolean::class.java)
            version++
        }

        if (version == 6L) {
            schema.get("RealmNotification")
                ?.addField("rev", String::class.java)
                ?.setNullable("rev", true)
                ?.addField("needsSync", Boolean::class.java)
            version++
        }
    }
}
