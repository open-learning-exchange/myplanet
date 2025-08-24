package org.ole.planet.myplanet.di

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import org.ole.planet.myplanet.utilities.SharedPrefManager

@Singleton
class AuthInterceptor @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        requestBuilder.addHeader("Authorization", sharedPrefManager.header)
        return chain.proceed(requestBuilder.build())
    }
}
