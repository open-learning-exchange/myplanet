package org.ole.planet.myplanet.services.upload

import org.ole.planet.myplanet.repository.UploadedItemResult

/**
 * Database-agnostic upload configuration for models that have been migrated to Room.
 *
 * It mirrors [UploadConfig] but replaces the two Realm-bound hooks:
 *  - querying pending items goes through [fetchPendingItems] (a DAO-backed suspend function)
 *    instead of a `RealmQuery` builder;
 *  - persisting `_id`/`_rev` after a successful upload goes through [markUploaded] (DAO-backed)
 *    instead of the Realm `markUploaded` + `additionalUpdates(Realm, …)` path.
 *
 * The HTTP/batch/retry pipeline in [UploadCoordinator.uploadRoom] is shared with the Realm path in
 * spirit but operates on plain `T : Any` entities.
 */
data class RoomUploadConfig<T : Any>(
    val endpoint: String,
    val modelClassName: String,

    val fetchPendingItems: suspend () -> List<T>,

    val serializer: UploadSerializer<T>,

    val idExtractor: (T) -> String?,
    val dbIdExtractor: ((T) -> String?)? = null,

    val responseHandler: ResponseHandler = ResponseHandler.Standard,

    val batchSize: Int = 50,

    val beforeUpload: (suspend (T) -> Unit)? = null,
    val afterUpload: (suspend (T, UploadedItem) -> Unit)? = null,

    /**
     * Persists the results of successful uploads (typically setting `_id`/`_rev` via a DAO) and
     * returns the subset that could not be persisted locally (treated as failures).
     */
    val markUploaded: suspend (List<UploadedItemResult>) -> List<UploadedItemResult>,
)
