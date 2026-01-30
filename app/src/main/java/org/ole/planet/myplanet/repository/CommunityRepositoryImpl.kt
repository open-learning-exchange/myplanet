package org.ole.planet.myplanet.repository

import io.realm.Sort
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmCommunity
import javax.inject.Inject

class CommunityRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CommunityRepository {
    override suspend fun getAllCommunities(): List<RealmCommunity> =
        queryList(RealmCommunity::class.java) {
            sort("weight", Sort.ASCENDING)
        }

    override suspend fun addCommunities(communities: List<RealmCommunity>) {
        executeTransaction { realm ->
            realm.delete(RealmCommunity::class.java)
            realm.copyToRealmOrUpdate(communities)
        }
    }
}
