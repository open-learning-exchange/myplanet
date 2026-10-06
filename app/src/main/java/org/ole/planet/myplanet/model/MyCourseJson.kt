package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

// App-side Gson helpers for MyCourse, kept out of the Room entity.

fun MyCourse.Companion.serialize(course: MyCourse, resourcesByStepId: Map<String?, List<MyLibrary>>): JsonObject = buildJsonObject {
    put("_id", course.courseId)
    put("_rev", course.courseRev)
    put("courseTitle", course.courseTitle)
    put("description", course.description)
    put("languageOfInstruction", course.languageOfInstruction)
    put("gradeLevel", course.gradeLevel)
    put("subjectLevel", course.subjectLevel)
    put("createdDate", course.createdDate)
    put("method", course.method)
    put("memberLimit", course.memberLimit)
    course.coverFileName?.let { put("coverFileName", it) }

    put("steps", buildJsonArray {
        course.courseSteps?.forEach { step ->
            add(buildJsonObject {
                put("stepTitle", step.stepTitle)
                put("description", step.description)
                put("id", step.id)

                val stepResources = resourcesByStepId[step.id] ?: emptyList()
                put("resources", buildJsonArray {
                    stepResources.forEach { resource ->
                        add(resource.serializeResource().toKotlinx())
                    }
                })
            })
        }
    })
    put("images", buildJsonArray { })
}.toGson()
