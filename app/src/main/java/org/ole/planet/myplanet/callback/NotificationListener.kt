package org.ole.planet.myplanet.callback

interface NotificationListener {
    fun showPendingSurveyDialog()
    fun showUserResourceDialog()
    fun showResourceDownloadDialog()
    fun syncKeyId()
    fun forceDownloadNewsImages()
    fun downloadDictionary()
}
