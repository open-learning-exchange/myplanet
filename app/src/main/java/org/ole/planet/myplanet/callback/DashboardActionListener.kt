package org.ole.planet.myplanet.callback

interface DashboardActionListener {
    fun showPendingSurveyDialog()
    fun showUserResourceDialog()
    fun showResourceDownloadDialog()
    fun syncKeyId()
    fun forceDownloadNewsImages()
    fun downloadDictionary()
}
