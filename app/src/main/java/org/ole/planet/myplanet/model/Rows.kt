package org.ole.planet.myplanet.model

class Value {
    var rev: String? = null
}

class Rows {
    var id: String? = null
    var key: String? = null
    var value: Value? = null
    override fun toString(): String {
        return "ClassPojo [id = $id, value = $value , key = $key]"
    }
}
