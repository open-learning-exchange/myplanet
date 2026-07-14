package org.ole.planet.myplanet.callback



interface OnTaskCompletedListener {
    fun onCheckChange(id: String, completed: Boolean)
    fun onEdit(id: String)
    fun onDelete(id: String)
    fun onClickMore(id: String)
}
