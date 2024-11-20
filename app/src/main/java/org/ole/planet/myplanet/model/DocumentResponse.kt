package org.ole.planet.myplanet.model

class DocumentResponse {
    private var totalRows: String? = null
    private var offset: String? = null
    var rows: List<Rows>? = null
    override fun toString(): String {
        return "ClassPojo [total_rows = $totalRows, offset = $offset, rows = $rows]"
    }
}
