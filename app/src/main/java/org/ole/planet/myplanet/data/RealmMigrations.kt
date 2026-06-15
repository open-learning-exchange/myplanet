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

        if (version == 7L) {
            schema.get("RealmAchievement")
                ?.addRealmListField("links", String::class.java)
                ?.addRealmListField("otherInfo", String::class.java)
                ?.addField("dateSortOrder", String::class.java)
                ?.setNullable("dateSortOrder", true)
                ?.addField("createdOn", String::class.java)
                ?.setNullable("createdOn", true)
                ?.addField("username", String::class.java)
                ?.setNullable("username", true)
                ?.addField("parentCode", String::class.java)
                ?.setNullable("parentCode", true)
                ?.addField("isUpdated", Boolean::class.java)
            version++
        }

        if (version == 8L) {
            schema.get("RealmMyTeam")
                ?.addField("isDeletePending", Boolean::class.java)
            version++
        }

        if (version == 9L) {
            schema.get("RealmTag")
                ?.addIndex("name")
                ?.addIndex("tagId")
                ?.addIndex("db")
            schema.get("RealmRating")
                ?.addIndex("item")
                ?.addIndex("type")
            schema.get("RealmMyCourse")
                ?.addIndex("gradeLevel")
                ?.addIndex("subjectLevel")
            version++
        }

        if (version == 10L) {
            schema.get("RealmOfflineActivity")
                ?.addIndex("userId")
                ?.addIndex("type")
            schema.get("RealmCourseActivity")
                ?.addIndex("courseId")
                ?.addIndex("type")
            schema.get("RealmCourseProgress")
                ?.addIndex("userId")
                ?.addIndex("courseId")
            schema.get("RealmStepExam")
                ?.addIndex("courseId")
            schema.get("RealmRating")
                ?.addIndex("userId")
            schema.get("RealmHealthExamination")
                ?.addIndex("userId")
            schema.get("RealmSubmission")
                ?.addIndex("type")
                ?.addIndex("userId")
            schema.get("RealmMyLife")
                ?.addIndex("userId")
            schema.get("RealmNotification")
                ?.addIndex("userId")
                ?.addIndex("type")
            schema.get("RealmRemovedLog")
                ?.addIndex("userId")
                ?.addIndex("type")
            schema.get("RealmTeamLog")
                ?.addIndex("teamId")
                ?.addIndex("type")
            schema.get("RealmTeamTask")
                ?.addIndex("teamId")
            schema.get("RealmTeamNotification")
                ?.addIndex("type")
            schema.get("RealmResourceActivity")
                ?.addIndex("type")
            schema.get("RealmNews")
                ?.addIndex("userId")
            version++
        }

        if (version == 11L) {
            schema.get("RealmMyCourse")
                ?.addField("courseTitleNormal", String::class.java)
                ?.addIndex("courseTitleNormal")
            version++
        }
    }
}
