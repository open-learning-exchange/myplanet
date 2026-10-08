package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.Personal

interface OnPersonalSelectedListener {
    fun onUpload(personal: Personal?)
    fun onAddedResource()
    fun onEditPersonal(personal: Personal)
    fun onDeletePersonal(personal: Personal)
}
