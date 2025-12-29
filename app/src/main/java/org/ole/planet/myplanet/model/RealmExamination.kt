package org.ole.planet.myplanet.model

import java.io.Serializable

class RealmExamination : Serializable {
    var notes: String? = null
    var diagnosis: String? = null
    var treatments: String? = null
    var medications: String? = null
    var immunizations: String? = null
    var allergies: String? = null
    var xrays: String? = null
    var tests: String? = null
    var referrals: String? = null
    var createdBy: String? = null
}
