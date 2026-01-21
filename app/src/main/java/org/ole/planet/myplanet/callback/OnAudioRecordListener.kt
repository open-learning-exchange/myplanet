package org.ole.planet.myplanet.callback

interface OnAudioRecordListener {
    fun onRecordStarted()
    fun onRecordStopped(outputFile: String?)
    fun onError(error: String?)
}
