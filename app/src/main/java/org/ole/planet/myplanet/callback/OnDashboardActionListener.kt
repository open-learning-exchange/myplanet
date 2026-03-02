package org.ole.planet.myplanet.callback

interface OnDashboardActionListener {
    fun showUserResourceDialog()
    fun showResourceDownloadDialog()
    fun syncKeyId()
    fun forceDownloadNewsImages()
    fun downloadDictionary()
}
