package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "health_examinations", indices = [Index("userId")])
class HealthExamination {
    @PrimaryKey
    var _id: String = ""
    var userId: String? = null
    var isUpdated = false
    var _rev: String? = null
    var data: String? = null
    var temperature = 0f
        private set
    var pulse = 0
    var bp: String? = null
    var height = 0f
    var weight = 0f
        private set
    var vision: String? = null
    var date: Long = 0
    var hearing: String? = null
    var conditions: String? = null
    var isSelfExamination = false
    var planetCode: String? = null
    var isHasInfo = false
    var profileId: String? = null
    var creatorId: String? = null
    var gender: String? = null
    var age = 0
    fun setTemperature(temperature: Float) {
        this.temperature = temperature
    }

    fun setWeight(weight: Float) {
        this.weight = weight
    }

    companion object
}
