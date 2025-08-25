package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyPersonal

import org.ole.planet.myplanet.model.RealmMyPersonal

interface MyPersonalsRepository {
    fun getMyPersonals(userId: String): List<org.ole.planet.myplanet.model.RealmMyPersonal>
    fun deletePersonal(personalId: String)
    fun updatePersonal(personal: org.ole.planet.myplanet.model.RealmMyPersonal)
}
