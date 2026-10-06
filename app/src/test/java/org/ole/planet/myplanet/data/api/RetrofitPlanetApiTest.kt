package org.ole.planet.myplanet.data.api

import okhttp3.OkHttpClient
import org.ole.planet.myplanet.di.NetworkModule

/**
 * Runs the [PlanetApiContractTest] fixtures against [RetrofitPlanetApi] wired exactly as the app
 * wired it before the switch to Ktor (same Retrofit converters, Gson and Json instances), minus
 * the retry interceptor, which [RetryInterceptorTest] covers on its own.
 */
class RetrofitPlanetApiTest : PlanetApiContractTest() {
    override fun createApi(baseUrl: String): PlanetApi {
        val retrofit = NetworkModule.provideStandardRetrofit(OkHttpClient(), NetworkModule.provideGson(), NetworkModule.provideJson())
        return RetrofitPlanetApi(NetworkModule.provideApiInterface(retrofit))
    }
}
