package org.ole.planet.myplanet.utils

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.di.DispatcherModule

class DispatcherProviderGuardTest {

    @Test
    fun testDispatcherModuleAnnotations() {
        val annotations = DispatcherModule::class.java.annotations
        val moduleAnnotation = annotations.find { it.annotationClass == Module::class }
        assertTrue("DispatcherModule should be annotated with @Module", moduleAnnotation != null)

        val provideMethod = DispatcherModule::class.java.declaredMethods.find {
            it.name == "provideDispatcherProvider"
        }
        assertTrue("provideDispatcherProvider should be a singleton",
            provideMethod?.getAnnotation(Singleton::class.java) != null)

        assertTrue("provideDispatcherProvider should be annotated with @Provides",
            provideMethod?.getAnnotation(Provides::class.java) != null)
    }
}
