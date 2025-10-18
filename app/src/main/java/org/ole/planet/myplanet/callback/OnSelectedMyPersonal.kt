package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyPersonal

interface OnSelectedMyPersonal {
    fun onUpload(personal: RealmMyPersonal?)
    fun onAddedResource()
    fun onEditPersonal(personal: RealmMyPersonal)
    fun onDeletePersonal(personal: RealmMyPersonal)
}
