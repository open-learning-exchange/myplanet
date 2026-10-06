package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import org.junit.After
import org.ole.planet.myplanet.di.NetworkModule

/**
 * Runs the [PlanetApiContractTest] fixtures against [KtorPlanetApi] wired exactly as the app
 * wires it ([NetworkModule.providePlanetApi] over [KtorHttpClients.create] and the app's Json
 * instance), minus the retry interceptor, which [PlanetApiRetryTest] covers.
 */
class KtorPlanetApiTest : PlanetApiContractTest() {
    private var client: HttpClient? = null

    override fun createApi(baseUrl: String): PlanetApi {
        val httpClient = NetworkModule.provideKtorHttpClient(OkHttpClient()).also { client = it }
        return NetworkModule.providePlanetApi(httpClient, NetworkModule.provideJson())
    }

    @After
    fun closeClient() {
        client?.close()
    }
}
