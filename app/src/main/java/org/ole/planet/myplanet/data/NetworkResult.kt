package org.ole.planet.myplanet.data

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int?, val message: String?) : NetworkResult<Nothing>()
    data class Exception(val exception: Throwable) : NetworkResult<Nothing>()
}
