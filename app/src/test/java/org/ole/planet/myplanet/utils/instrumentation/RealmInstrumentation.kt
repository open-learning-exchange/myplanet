package org.ole.planet.myplanet.utils.instrumentation

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.utils.instrumentation.TestConstants.TEST_REALM_NAME
import org.ole.planet.myplanet.utils.MockitoUtils.anyObject
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

object RealmInstrumentation {
    fun init(context: Context) {
        Realm.init(context)
        val testConfig = RealmConfiguration.Builder()
            .inMemory()
            .name(TEST_REALM_NAME)
            .build()
        Realm.setDefaultConfiguration(testConfig)
    }

    fun givenAMockRealm(): Realm {
        return mock(Realm::class.java)
    }

    fun givenARealmInstance(mockRealm: Realm, action: () -> Unit) {
        `when`(Realm.getDefaultInstance()).thenReturn(mockRealm)
        action()
    }

    fun givenAMockDatabaseService(testDispatcher: CoroutineDispatcher): DatabaseService {
        val mockDatabaseService = mock(DatabaseService::class.java)
        `when`(mockDatabaseService.ioDispatcher).thenReturn(testDispatcher)
        return mockDatabaseService
    }

    suspend fun <T> givenAUseRealmImplementation(
        mockDatabaseService: DatabaseService,
        mockRealm: Realm,
        testDispatcher: CoroutineDispatcher,
        action: suspend () -> T
    ): T {
        `when`(mockDatabaseService.withRealmAsync(anyObject<(Realm) -> T>()))
            .thenAnswer { invocation ->
                val operation = invocation.getArgument<(Realm) -> T>(0)
                operation(mockRealm)
            }
        return withContext(testDispatcher) {
            action()
        }
    }
}
