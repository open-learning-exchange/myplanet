package org.ole.planet.myplanet.service.upload

import io.realm.RealmObject

/**
 * Test RealmObject for unit testing UploadCoordinator.
 *
 * This is a simplified Realm model used only in tests.
 * It must be in a separate file (not nested) because Realm annotation
 * processor doesn't support nested classes.
 */
open class TestRealmObject : RealmObject() {
    var id: String = ""
    var title: String = ""
    var userId: String? = null
    var dbId: String? = null
    var isUpdated: Boolean = false
}
