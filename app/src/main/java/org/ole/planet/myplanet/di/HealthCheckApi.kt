package org.ole.planet.myplanet.di

import retrofit2.Response
import retrofit2.http.HEAD
import retrofit2.http.Url

interface HealthCheckApi {
    @HEAD
    suspend fun checkServerHealth(@Url url: String): Response<Void>
}
