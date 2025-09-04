package org.ole.planet.myplanet.repository

import android.content.Context
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.UploadManager

interface TeamRepository {
    suspend fun getTeamResources(teamId: String): List<RealmMyLibrary>
    fun syncTeamActivities(context: Context, uploadManager: UploadManager)
}

