package org.ole.planet.myplanet.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.lang.reflect.Type
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

class AllowlistFactory(
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

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        if (type !in handledTypes) return null
        return delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
    }
}
