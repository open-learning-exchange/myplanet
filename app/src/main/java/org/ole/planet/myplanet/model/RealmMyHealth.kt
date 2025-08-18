package org.ole.planet.myplanet.model

class RealmMyHealth {
    var profile: RealmMyHealthProfile? = null
    var userKey: String? = null
    var lastExamination: Long = 0

    class RealmMyHealthProfile {
        var emergencyContactName = ""
        var emergencyContactType = ""
        var emergencyContact = ""
        var specialNeeds = ""
        var notes = ""
    }
}
