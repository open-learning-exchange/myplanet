package org.ole.planet.myplanet.callback

interface SuccessListener {
    fun onSuccess(success: String?)
    fun onFailure(message: String)
}
