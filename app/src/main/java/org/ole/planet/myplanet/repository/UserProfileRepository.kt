package org.ole.planet.myplanet.repository

import io.realm.RealmList
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

interface UserProfileRepository {
    fun getProfile(): RealmUserModel?
    fun getAchievements(): List<RealmAchievement>
    fun getTeams(): List<RealmMyTeam>
    fun getOtherInfo(): RealmList<String>
    fun getStats(): LinkedHashMap<String, String?>
    fun updateProfile(user: RealmUserModel)
}
