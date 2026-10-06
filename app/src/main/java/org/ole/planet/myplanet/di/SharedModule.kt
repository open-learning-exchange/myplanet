package org.ole.planet.myplanet.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
object SharedModule
