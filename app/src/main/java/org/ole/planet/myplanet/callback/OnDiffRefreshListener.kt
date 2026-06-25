package org.ole.planet.myplanet.callback

interface OnDiffRefreshListener {
    fun refreshWithDiff()
    fun refreshWithDiff(id: String) {
        refreshWithDiff()
    }
}
