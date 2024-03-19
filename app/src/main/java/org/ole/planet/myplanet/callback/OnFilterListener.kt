package org.ole.planet.myplanet.callback

interface OnFilterListener {
    fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>)

    fun getData(): Map<String, Set<String>>

    fun getSelectedFilter(): Map<String, Set<String>>
}

