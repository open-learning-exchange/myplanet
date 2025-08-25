package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmMyPersonal

interface OnSelectedMyPersonal {
    fun onUpload(personal: RealmMyPersonal?)
    fun onAddedResource()
    fun onEdit(personal: RealmMyPersonal)
    fun onDelete(personal: RealmMyPersonal)
}
