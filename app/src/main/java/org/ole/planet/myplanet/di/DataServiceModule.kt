package org.ole.planet.myplanet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DataService
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UploadToShelfService

@Module
@InstallIn(SingletonComponent::class)
object DataServiceModule {

    @Provides
    @Singleton
    fun provideDataService(
        @ApplicationContext context: Context,
        apiInterface: ApiInterface,
        databaseService: DatabaseService,
        @ApplicationScope scope: CoroutineScope,
        userRepository: UserRepository,
        uploadToShelfService: UploadToShelfService
    ): DataService {
        return DataService(
            context,
            apiInterface,
            databaseService,
            scope,
            userRepository,
            uploadToShelfService
        )
    }
}
