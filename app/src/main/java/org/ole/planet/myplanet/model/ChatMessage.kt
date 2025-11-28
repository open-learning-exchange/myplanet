package org.ole.planet.myplanet.model

data class ChatMessage(
    val message: String,
    val viewType: Int,
    val source: Int = 0
) {
    companion object {
        const val QUERY = 1
        const val RESPONSE = 2
        const val RESPONSE_SOURCE_UNKNOWN = 0
        const val RESPONSE_SOURCE_SHARED_VIEW_MODEL = 1
        const val RESPONSE_SOURCE_NETWORK = 2
    }
}
