package org.ole.planet.myplanet.model

class SourceFile {
    var title: String? = null
    private var mResType: String? = null
    private var mURL: String? = null
    var date: String? = null
    private var mSubjects: List<*> = ArrayList<Any?>()
    private var mLevels: List<*> = ArrayList<Any?>()

    constructor()
    constructor(title: String?, resType: String?, URL: String?, date: String?, subjects: List<*>, levels: List<*>) {
        this.title = title
        mResType = resType
        mURL = URL
        this.date = date
        mSubjects = subjects
        mLevels = levels
    }
}
