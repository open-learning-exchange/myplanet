package org.ole.planet.myplanet.model

class DocumentResponse {
    var total_rows: String? = null
    var offset: String? = null
    @JvmField
    var rows: List<Rows>? = null
    override fun toString(): String {
        return "ClassPojo [total_rows = $total_rows, offset = $offset, rows = $rows]"
    }
}