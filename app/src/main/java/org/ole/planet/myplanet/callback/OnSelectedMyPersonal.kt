package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyPersonal

interface OnSelectedMyPersonal {
    fun onUpload(personal: RealmMyPersonal?)
    fun onAddedResource()
    fun onDeletePersonal(personal: RealmMyPersonal)
    fun onEditPersonal(personal: RealmMyPersonal, title: String, description: String)
}
