package org.ole.planet.myplanet.utilities

import com.google.gson.JsonObject

object JsonExtractor {
    fun extractLibraryFields(doc: JsonObject): JsonObject {
        val result = JsonObject()

        // Extract only the fields we need
        val essentialFields = listOf(
            "_id", "_rev", "title", "description", "resourceId",
            "addedBy", "uploadDate", "createdDate", "openWith",
            "language", "author", "mediaType", "resourceType"
        )

        for (field in essentialFields) {
            if (doc.has(field)) {
                result.add(field, doc.get(field))
            }
        }

        // Handle special case for attachments - only extract metadata
        if (doc.has("_attachments")) {
            val attachments = JsonObject()
            val originalAttachments = doc.getAsJsonObject("_attachments")

            for ((key, value) in originalAttachments.entrySet()) {
                val attachmentObj = value.asJsonObject
                val minimalAttachment = JsonObject()

                // Only extract essential metadata
                val metadataFields = listOf("content_type", "length", "digest", "revpos")
                for (field in metadataFields) {
                    if (attachmentObj.has(field)) {
                        minimalAttachment.add(field, attachmentObj.get(field))
                    }
                }

                attachments.add(key, minimalAttachment)
            }

            result.add("_attachments", attachments)
        }

        // Extract list properties with simplified approach
        val listProperties = listOf("resourceFor", "subject", "level", "tags", "languages")
        for (prop in listProperties) {
            if (doc.has(prop)) {
                result.add(prop, doc.get(prop))
            }
        }

        return result
    }
}