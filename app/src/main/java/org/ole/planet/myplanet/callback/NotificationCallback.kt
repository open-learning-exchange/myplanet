package org.ole.planet.myplanet.callback

interface NotificationCallback {
    fun showPendingSurveyDialog()
    fun showUserResourceDialog()
    fun showResourceDownloadDialog()
    fun syncKeyId()
    fun forceDownloadNewsImages()
    fun downloadDictionary()
}
