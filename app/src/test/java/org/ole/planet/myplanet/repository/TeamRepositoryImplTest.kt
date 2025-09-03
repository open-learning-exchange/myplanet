package org.ole.planet.myplanet.repository

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam

class TeamRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: TeamRepositoryImpl

    @Before
    fun setup() {
        val context = Application()
        databaseService = DatabaseService(context)
        val config = RealmConfiguration.Builder()
            .inMemory()
            .name("team-test-realm")
            .schemaVersion(4)
            .allowWritesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(config)
        repository = TeamRepositoryImpl(databaseService)
    }

    @After
    fun tearDown() {
        Realm.getDefaultInstance().use { it.executeTransaction { realm -> realm.deleteAll() } }
    }

    @Test
    fun getTeamResources_returnsAssociatedLibraries() = runBlocking {
        databaseService.executeTransactionAsync { realm ->
            val lib1 = RealmMyLibrary().apply { id = "lib1"; title = "Library 1" }
            val lib2 = RealmMyLibrary().apply { id = "lib2"; title = "Library 2" }
            realm.copyToRealmOrUpdate(lib1)
            realm.copyToRealmOrUpdate(lib2)
            val team1 = RealmMyTeam().apply { _id = "1"; teamId = "team1"; resourceId = "lib1" }
            val team2 = RealmMyTeam().apply { _id = "2"; teamId = "team1"; resourceId = "lib2" }
            realm.copyToRealmOrUpdate(team1)
            realm.copyToRealmOrUpdate(team2)
        }

        val resources = repository.getTeamResources("team1")
        assertEquals(2, resources.size)
        assertEquals(setOf("lib1", "lib2"), resources.map { it.id }.toSet())
    }
}

