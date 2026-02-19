package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser

interface TeamResourceRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    suspend fun getTeamCourses(teamId: String): List<RealmMyCourse>
    suspend fun addCoursesToTeam(teamId: String, courseIds: List<String>)
    suspend fun addResourceLinks(teamId: String, resources: List<RealmMyLibrary>, user: RealmUser?)
    suspend fun removeResourceLink(teamId: String, resourceId: String)
}
