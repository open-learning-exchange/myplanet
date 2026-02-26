package org.ole.planet.myplanet.model

data class Tag(
    val id: String?,
    val _id: String?,
    val _rev: String?,
    val name: String?,
    val linkId: String?,
    val tagId: String?,
    val attachedTo: List<String>?,
    val docType: String?,
    val db: String?,
    val isAttached: Boolean = false
)
