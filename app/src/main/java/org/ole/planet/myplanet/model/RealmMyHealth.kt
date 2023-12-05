package org.ole.planet.myplanet.model

class RealmMyHealth {
    @JvmField
    var profile: RealmMyHealthProfile? = null
    @JvmField
    var userKey: String? = null
    @JvmField
    var lastExamination: Long = 0

    class RealmMyHealthProfile {
        @JvmField
        var emergencyContactName = ""
        @JvmField
        var emergencyContactType = ""
        @JvmField
        var emergencyContact = ""
        @JvmField
        var specialNeeds = ""
        @JvmField
        var notes = ""
    }
}