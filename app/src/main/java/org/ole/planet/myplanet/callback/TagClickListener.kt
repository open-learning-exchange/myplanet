package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag

interface TagClickListener {
    @JvmSuppressWildcards
    fun onTagSelected(tag: RealmTag?)
    @JvmSuppressWildcards
    fun onOkClicked(list: List<RealmTag?>?)
}
