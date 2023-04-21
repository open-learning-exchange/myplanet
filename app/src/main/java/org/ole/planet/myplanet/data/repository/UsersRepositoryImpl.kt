package org.ole.planet.myplanet.data.repository

import io.realm.Realm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.ole.planet.myplanet.data.di.IoDispatcher
import org.ole.planet.myplanet.data.mappers.toLeader
import org.ole.planet.myplanet.domain.UsersRepository
import org.ole.planet.myplanet.domain.models.Leader
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import javax.inject.Inject

class UsersRepositoryImpl @Inject constructor(
    private val localDataSource: Realm,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UsersRepository {
    override fun getLeaders(): Flow<List<Leader>> = flow {
        val users = localDataSource.where(RealmMyTeam::class.java)
            .equalTo("isLeader", true).findAll()
        val leaders = users.mapNotNull { leader ->
            localDataSource.where(RealmUserModel::class.java).equalTo(
                "id",
                leader.user_id
            ).findFirst()
        }.distinctBy { it.id }
            .map(RealmUserModel::toLeader)
        emit(leaders)
    }.flowOn(ioDispatcher)
}