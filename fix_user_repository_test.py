import re

with open('app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt', 'r') as f:
    content = f.read()

# Replace the specific UserRepositoryImpl constructor parameters
old_constructor = """        userRepository = UserRepositoryImpl(
            databaseService,
            Dispatchers.Unconfined,
            mockSettings,
            mockSharedPrefManager,
            mockApiInterface,
            mockUploadToShelfService,
            mockContext,
            mockConfigurationsRepository,
            mockAppScope,
            mockDispatcherProvider
        )"""

new_constructor = """        val mockResourcesRepository = mockk<dagger.Lazy<org.ole.planet.myplanet.repository.ResourcesRepository>>(relaxed = true)
        val mockCoursesRepository = mockk<dagger.Lazy<org.ole.planet.myplanet.repository.CoursesRepository>>(relaxed = true)

        userRepository = UserRepositoryImpl(
            databaseService,
            Dispatchers.Unconfined,
            mockSettings,
            mockSharedPrefManager,
            mockApiInterface,
            mockResourcesRepository,
            mockCoursesRepository,
            mockUploadToShelfService,
            mockContext,
            mockConfigurationsRepository,
            mockAppScope,
            mockDispatcherProvider
        )"""

content = content.replace(old_constructor, new_constructor)

with open('app/src/androidTest/java/org/ole/planet/myplanet/model/RealmUserTest.kt', 'w') as f:
    f.write(content)
