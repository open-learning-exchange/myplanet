package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.repository.DictionaryFileReader
import org.ole.planet.myplanet.repository.DictionaryRepository
import org.ole.planet.myplanet.repository.DictionaryRepositoryImpl
import org.ole.planet.myplanet.repository.LifeCache
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.LifeRepositoryImpl
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KeyValueStore

/**
 * Hilt wiring for classes that live in the `:shared` module.
 *
 * The rule: code in `shared/src/commonMain` never imports `javax.inject`, `dagger` or `hilt` —
 * none of them exist off the JVM. Shared classes have plain constructors, and `:app` builds them
 * here with `@Provides` functions, scoped exactly as the class was before it moved (`@Singleton`
 * stays `@Singleton`, unscoped stays unscoped). An interface implemented in `:shared` is bound by
 * a `@Provides` function returning the interface, not by `@Binds`, because `@Binds` needs an
 * `@Inject` constructor on the implementation.
 *
 * Interfaces declared in `:shared` but implemented in `:app` (the `utils` seams such as
 * `TimeProvider`, `DispatcherProvider`, `KeyValueStore`) keep their providers next to their
 * Android implementations, in [TimeModule], [DispatcherModule], [ServiceModule] and
 * [SharedPreferencesModule].
 *
 * Shared types provided elsewhere, left where they were:
 * - `AppDatabase` and every DAO — [RoomModule]
 * - `PlanetApi` (`KtorPlanetApi`) and the Ktor `HttpClient` it uses — [NetworkModule]
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedModule {

    @Provides
    @Singleton
    fun provideLifeCache(
        @AppPreferences store: KeyValueStore,
        json: Json
    ): LifeCache {
        return LifeCache(store, json)
    }

    @Provides
    @Singleton
    fun provideLifeRepository(myLifeDao: MyLifeDao, lifeCache: LifeCache): LifeRepository {
        return LifeRepositoryImpl(myLifeDao, lifeCache)
    }

    @Provides
    @Singleton
    fun provideDictionaryRepository(
        dictionaryDao: DictionaryDao,
        dispatcherProvider: DispatcherProvider,
        dictionaryFileReader: DictionaryFileReader
    ): DictionaryRepository {
        return DictionaryRepositoryImpl(dictionaryDao, dispatcherProvider, dictionaryFileReader)
    }
}
