package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import io.realm.Realm
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyPersonal

class MyPersonalRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val preferences: SharedPreferences,
) : RealmRepository(databaseService), MyPersonalRepository {

    private val hasBackfilledUserIds = AtomicBoolean(false)

    override suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userIdentifier: String?,
        userName: String?,
        path: String?,
        description: String?
    ) {
        val resolvedUserIdentifier = resolveUserIdentifier(
            userId,
            userIdentifier,
            userName,
            preferences.getString("userId", null),
        )

        val personal = RealmMyPersonal().apply {
            id = UUID.randomUUID().toString()
            _id = id
            this.title = title
            val userIdentifierAlias = resolvedUserIdentifier
            this.userId = userIdentifierAlias
            this.userName = userName
            this.path = path
            this.date = Date().time
            this.description = description
        }
        save(personal)
    }

    override fun getPersonalResources(
        userId: String?,
        userIdentifier: String?,
        userName: String?
    ): Flow<List<RealmMyPersonal>> {
        ensurePersonalUserIdsBackfilled()

        val identifiers = buildList {
            listOf(userId, userIdentifier, preferences.getString("userId", null))
                .forEach { candidate ->
                    candidate?.takeUnless { it.isBlank() }?.trim()?.let { add(it) }
                }
        }.distinct()

        val supplementalName = userName?.takeUnless { it.isBlank() }?.trim()
        val queryValues = if (supplementalName != null) {
            (identifiers + supplementalName).distinct()
        } else {
            identifiers
        }

        if (queryValues.isEmpty()) {
            return flowOf(emptyList())
        }

        return queryListFlow(RealmMyPersonal::class.java) {
            beginGroup()
            queryValues.forEachIndexed { index, identifierValue ->
                if (index > 0) {
                    or()
                }
                equalTo("userId", identifierValue)
            }
            endGroup()
        }
    }

    private fun ensurePersonalUserIdsBackfilled() {
        if (!hasBackfilledUserIds.compareAndSet(false, true)) {
            return
        }

        val settingsUserId = preferences.getString("userId", null)

        Realm.getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { transactionRealm ->
                val records = transactionRealm.where(RealmMyPersonal::class.java)
                    .isNull("userId")
                    .or()
                    .equalTo("userId", "")
                    .findAll()

                if (records.isEmpty()) {
                    return@executeTransactionAsync
                }

                records.forEach { personal ->
                    val updatedIdentifier = resolveUserIdentifier(
                        personal.userId,
                        personal.userName,
                        settingsUserId,
                    )
                    if (!updatedIdentifier.isNullOrBlank()) {
                        personal.userId = updatedIdentifier
                    }
                }
            }
        }
    }

    private fun resolveUserIdentifier(vararg candidates: String?): String? {
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    override suspend fun deletePersonalResource(id: String) {
        delete(RealmMyPersonal::class.java, "_id", id)
        delete(RealmMyPersonal::class.java, "id", id)
    }

    override suspend fun updatePersonalResource(id: String, updater: (RealmMyPersonal) -> Unit) {
        update(RealmMyPersonal::class.java, "_id", id, updater)
        update(RealmMyPersonal::class.java, "id", id, updater)
    }
}
