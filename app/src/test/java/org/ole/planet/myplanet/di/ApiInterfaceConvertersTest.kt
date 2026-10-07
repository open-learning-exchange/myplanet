package org.ole.planet.myplanet.di

import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import retrofit2.Response
import retrofit2.http.Body

/**
 * With Gson gone, the kotlinx allowlist is the only converter the app's Retrofit has: every
 * [ApiInterface] body type must be one it handles, or one Retrofit converts itself.
 */
class ApiInterfaceConvertersTest {
    private val builtIn: Set<Type> = setOf(RequestBody::class.java, ResponseBody::class.java)

    @Test
    fun `every endpoint resolves its converters without Gson`() {
        val retrofit = NetworkModule.provideStandardRetrofit(OkHttpClient(), NetworkModule.provideJson())
        val eager = retrofit.newBuilder().validateEagerly(true).build()

        assertNotNull(eager.create(ApiInterface::class.java))
        assertEquals(1, eager.converterFactories().count { it::class.java.simpleName == "AllowlistFactory" })
        assertTrue(eager.converterFactories().none { it::class.java.name.contains("gson", ignoreCase = true) })
    }

    @Test
    fun `every body and response type is kotlinx handled or built in`() {
        val handled = NetworkModule.kotlinxHandledTypes + builtIn
        val endpoints = ApiInterface::class.java.declaredMethods.filter { Modifier.isAbstract(it.modifiers) }
        assertEquals(15, endpoints.size)
        for (method in endpoints) {
            val continuation = method.genericParameterTypes.last() as ParameterizedType
            assertEquals("${method.name} is a suspend function", Continuation::class.java, continuation.rawType)
            val response = lowerBound(continuation.actualTypeArguments[0]) as ParameterizedType
            assertEquals("${method.name} returns a Response", Response::class.java, response.rawType)
            val responseType = response.actualTypeArguments[0]
            assertTrue("${method.name} response $responseType", responseType in handled)
            method.parameterAnnotations.forEachIndexed { index, annotations ->
                if (annotations.any { it is Body }) {
                    val bodyType = method.genericParameterTypes[index]
                    assertTrue("${method.name} body $bodyType", bodyType in handled)
                }
            }
        }
    }

    private fun lowerBound(type: Type): Type =
        if (type is WildcardType) type.lowerBounds.firstOrNull() ?: type.upperBounds[0] else type
}
