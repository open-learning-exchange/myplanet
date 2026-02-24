package org.ole.planet.myplanet.data

import io.realm.DynamicRealm
import io.realm.FieldAttribute
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
            if (!schema.contains("RealmRetryOperation")) {
                schema.create("RealmRetryOperation")
                    .addField("id", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("uploadType", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("itemId", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("serializedPayload", String::class.java, FieldAttribute.REQUIRED)
                    .addField("endpoint", String::class.java, FieldAttribute.REQUIRED)
                    .addField("httpMethod", String::class.java, FieldAttribute.REQUIRED)
                    .addField("dbId", String::class.java)
                    .addField("status", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("attemptCount", Int::class.java)
                    .addField("maxAttempts", Int::class.java)
                    .addField("lastAttemptTime", Long::class.java)
                    .addField("nextRetryTime", Long::class.java)
                    .addField("createdTime", Long::class.java)
                    .addField("errorMessage", String::class.java)
                    .addField("httpCode", Int::class.javaObjectType)
                    .addField("modelClassName", String::class.java, FieldAttribute.REQUIRED)
                    .addField("userId", String::class.java)
            }
            version++
        }
        if (version == 7L) {
             if (!schema.contains("RealmRetryOperation")) {
                schema.create("RealmRetryOperation")
                    .addField("id", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("uploadType", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("itemId", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("serializedPayload", String::class.java, FieldAttribute.REQUIRED)
                    .addField("endpoint", String::class.java, FieldAttribute.REQUIRED)
                    .addField("httpMethod", String::class.java, FieldAttribute.REQUIRED)
                    .addField("dbId", String::class.java)
                    .addField("status", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("attemptCount", Int::class.java)
                    .addField("maxAttempts", Int::class.java)
                    .addField("lastAttemptTime", Long::class.java)
                    .addField("nextRetryTime", Long::class.java)
                    .addField("createdTime", Long::class.java)
                    .addField("errorMessage", String::class.java)
                    .addField("httpCode", Int::class.javaObjectType)
                    .addField("modelClassName", String::class.java, FieldAttribute.REQUIRED)
                    .addField("userId", String::class.java)
            }
            version++
        }
        if (version == 6L) {
            if (!schema.contains("RealmRetryOperation")) {
                schema.create("RealmRetryOperation")
                    .addField("id", String::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("uploadType", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("itemId", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("serializedPayload", String::class.java, FieldAttribute.REQUIRED)
                    .addField("endpoint", String::class.java, FieldAttribute.REQUIRED)
                    .addField("httpMethod", String::class.java, FieldAttribute.REQUIRED)
                    .addField("dbId", String::class.java)
                    .addField("status", String::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                    .addField("attemptCount", Int::class.java)
                    .addField("maxAttempts", Int::class.java)
                    .addField("lastAttemptTime", Long::class.java)
                    .addField("nextRetryTime", Long::class.java)
                    .addField("createdTime", Long::class.java)
                    .addField("errorMessage", String::class.java)
                    .addField("httpCode", Int::class.javaObjectType)
                    .addField("modelClassName", String::class.java, FieldAttribute.REQUIRED)
                    .addField("userId", String::class.java)
            }
            version++
        }
    }
}
