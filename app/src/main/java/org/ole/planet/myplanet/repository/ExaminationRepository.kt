package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel

interface ExaminationRepository {
    suspend fun getHealthAndUser(userId: String): Pair<RealmMyHealthPojo?, RealmUserModel?>
    suspend fun getDecryptedHealth(pojo: RealmMyHealthPojo?, user: RealmUserModel?): RealmMyHealth?
    suspend fun getExamination(examinationId: String): RealmMyHealthPojo?
    suspend fun saveExamination(
        examination: RealmMyHealthPojo?,
        health: RealmMyHealth?,
        user: RealmUserModel?,
        currentUser: RealmUserModel?,
        sign: RealmExamination,
        conditions: Map<String?, Boolean>,
        temperature: Float,
        pulse: Int,
        height: Float,
        weight: Float,
        vision: String,
        hearing: String,
        bp: String,
        hasInfo: Boolean
    ): Boolean
}
