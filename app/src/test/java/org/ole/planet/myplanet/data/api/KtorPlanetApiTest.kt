package org.ole.planet.myplanet.data.api

import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import org.junit.After
import org.ole.planet.myplanet.di.NetworkModule

/**
 * Runs the [PlanetApiContractTest] fixtures against [KtorPlanetApi] on the client the app would
 * build ([KtorHttpClients.create] over an OkHttpClient) and the app's Json instance, minus the
 * retry interceptor, which [PlanetApiRetryParityTest] covers.
 */
class KtorPlanetApiTest : PlanetApiContractTest() {
    private var client: HttpClient? = null

    override fun createApi(baseUrl: String): PlanetApi {
        val httpClient = NetworkModule.provideKtorHttpClient(OkHttpClient()).also { client = it }
        return KtorPlanetApi(httpClient, NetworkModule.provideJson(), JvmKtorPlatform)
    }

    @After
    fun closeClient() {
        client?.close()
    }
}
