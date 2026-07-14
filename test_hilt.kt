import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.Module
import dagger.Provides
import io.mockk.mockk
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.DatabaseModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object FakeDatabaseModule {
    @Provides
    fun provideDatabaseService(): DatabaseService = mockk(relaxed = true)
}
