package org.ole.planet.myplanet.callback

interface OnFilterListener {
    fun filter(subjects: Set<String>, languages: Set<String>, mediums: Set<String>, levels: Set<String>)

    fun getData(): Map<String, Set<String>>

    fun getSelectedFilter(): Map<String, Set<String>>
}

