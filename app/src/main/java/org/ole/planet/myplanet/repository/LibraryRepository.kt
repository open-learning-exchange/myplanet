package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary

interface LibraryRepository {
    fun getAllLibraryItems(): List<RealmMyLibrary>
    fun getLibraryItemById(id: String): RealmMyLibrary?
    fun getOfflineLibraryItems(): List<RealmMyLibrary>
    fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    fun getAllLibraryList(): List<RealmMyLibrary>
}
