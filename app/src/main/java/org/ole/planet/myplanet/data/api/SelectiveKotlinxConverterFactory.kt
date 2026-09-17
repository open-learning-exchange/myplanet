package org.ole.planet.myplanet.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.lang.reflect.Type
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * kotlinx.serialization's own converter factory claims every requested type unconditionally,
 * so it can't simply be appended alongside [retrofit2.converter.gson.GsonConverterFactory] -
 * it would either shadow Gson entirely or throw for the raw JsonObject/JsonArray endpoints it
 * has no serializer for. This factory only delegates to kotlinx.serialization for [handledTypes]
 * and returns null otherwise, letting the next factory in the chain (Gson) handle the rest.
 */
class SelectiveKotlinxConverterFactory(
    json: Json,
    private val handledTypes: Set<Type>
) : Converter.Factory() {
    private val delegate = json.asConverterFactory("application/json".toMediaType())

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (type !in handledTypes) return null
        return delegate.responseBodyConverter(type, annotations, retrofit)
    }
}
