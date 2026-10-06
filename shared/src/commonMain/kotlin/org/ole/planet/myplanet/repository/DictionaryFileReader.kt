package org.ole.planet.myplanet.repository

interface DictionaryFileReader {
    fun exists(): Boolean
    fun readText(): String?
}
