package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyPersonal

interface MyPersonalsRepository {
    fun getMyPersonals(userId: String): List<RealmMyPersonal>
    fun deletePersonal(personalId: String)
    fun updatePersonal(personal: RealmMyPersonal)
}
