package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmTag

class TagRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), TagRepository {

    override suspend fun getTags(dbType: String?): List<RealmTag> {
        return queryList(RealmTag::class.java) {
            dbType?.let { equalTo("db", it) }
            isNotEmpty("name")
            equalTo("isAttached", false)
        }
    }

    override suspend fun buildChildMap(): HashMap<String, List<RealmTag>> {
        val allTags = queryList(RealmTag::class.java)
        val childMap = HashMap<String, List<RealmTag>>()
        allTags.forEach { t ->
            t.attachedTo?.forEach { parent ->
                val list = childMap[parent]?.toMutableList() ?: mutableListOf()
                if (!list.contains(t)) {
                    list.add(t)
                }
                childMap[parent] = list
            }
        }
        return childMap
    }
}

