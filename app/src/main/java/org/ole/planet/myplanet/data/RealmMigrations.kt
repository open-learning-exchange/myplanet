package org.ole.planet.myplanet.data

import io.realm.DynamicRealm
import io.realm.RealmMigration
import java.text.Normalizer
import java.util.Locale

class RealmMigrations : RealmMigration {

    companion object {
        const val MINIMUM_SUPPORTED_VERSION = 4L
    }

    class UnsupportedSchemaVersionException(version: Long) : IllegalStateException(
        "Realm schema version $version is below the minimum supported version " +
            "$MINIMUM_SUPPORTED_VERSION; the local database must be recreated"
    )

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        if (oldVersion < MINIMUM_SUPPORTED_VERSION) {
            throw UnsupportedSchemaVersionException(oldVersion)
        }
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

        if (version == 12L) {
            schema.get("RealmMyTeam")
                ?.addField("imageName", String::class.java)
                ?.setNullable("imageName", true)
            version++
        }

        if (version == 13L) {
            schema.get("RealmMyCourse")
                ?.addField("coverFileName", String::class.java)
                ?.setNullable("coverFileName", true)
            version++
        }

        if (version == 14L) {
            schema.get("RealmMyCourse")?.transform { obj ->
                val title = obj.getString("courseTitle")
                if (title != null && obj.getString("courseTitleNormal") == null) {
                    val lowercased = title.lowercase(Locale.ROOT)
                    val normalized = Normalizer.normalize(lowercased, Normalizer.Form.NFD)
                    val sb = StringBuilder(normalized.length)
                    for (i in 0 until normalized.length) {
                        val c = normalized[i]
                        if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                            sb.append(c)
                        }
                    }
                    obj.setString("courseTitleNormal", sb.toString())
                }
            }
            schema.get("RealmMyLibrary")
                ?.addField("titleNormal", String::class.java)
                ?.addIndex("titleNormal")
                ?.transform { obj ->
                    val title = obj.getString("title")
                    if (title != null) {
                        val normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                            .lowercase(Locale.ROOT)
                        obj.setString("titleNormal", normalized)
                    }
                }
            version++
        }

        // Version 16 shipped without schema changes relative to 15.
        if (version == 15L) {
            version++
        }

        if (version == 16L) {
            schema.get("RealmMyPersonal")
                ?.addIndex("userId")
            schema.get("RealmNews")
                ?.addIndex("replyTo")
            schema.get("RealmSubmission")
                ?.addIndex("parentId")
            version++
        }
    }
}
