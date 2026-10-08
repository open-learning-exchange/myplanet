package org.ole.planet.myplanet.callback

interface OnRatingChangeListener {
    fun onRatingChanged()
    fun onRatingChanged(type: String, id: String) {}
}
