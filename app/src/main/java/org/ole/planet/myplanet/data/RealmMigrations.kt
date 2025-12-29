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
    }
}
