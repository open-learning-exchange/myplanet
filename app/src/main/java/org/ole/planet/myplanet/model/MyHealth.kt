package org.ole.planet.myplanet.model

class MyHealth {
    var profile: MyHealthProfile? = null
    var userKey: String? = null
    var lastExamination: Long = 0

    class MyHealthProfile {
        var emergencyContactName = ""
        var emergencyContactType = ""
        var emergencyContact = ""
        var specialNeeds = ""
        var notes = ""
    }
}
