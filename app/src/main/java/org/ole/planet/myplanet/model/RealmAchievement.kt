package org.ole.planet.myplanet.model

import android.text.TextUtils
import android.widget.EditText
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.runBlocking
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmAchievement : RealmObject {
    @PrimaryKey
    var _id: String = ""
    var _rev: String? = null
    var achievements: RealmList<String> = realmListOf()
    var references: RealmList<String> = realmListOf()
    var purpose: String? = null
    var achievementsHeader: String? = null
    var sendToNation: String? = null
    var goals: String? = null

    val achievementsArray: JsonArray
        get() {
            val array = JsonArray()
            for (s in achievements) {
                val ob = Gson().fromJson(s, JsonElement::class.java)
                array.add(ob)
            }
            return array
        }

    fun getReferencesArray(): JsonArray {
        val array = JsonArray()
        for (s in references) {
            val ob = Gson().fromJson(s, JsonElement::class.java)
            array.add(ob)
        }
        return array
    }

    fun setAchievements(ac: JsonArray) {
        achievements.clear()
        for (el in ac) {
            val achievement = Gson().toJson(el)
            if (!achievements.contains(achievement)) {
                achievements.add(achievement)
            }
        }
    }

    fun setReferences(of: JsonArray?) {
        references.clear()
        if (of == null) return
        for (el in of) {
            val e = Gson().toJson(el)
            if (!references.contains(e)) {
                references.add(e)
            }
        }
    }

    companion object {
        private val achievementDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun serialize(sub: RealmAchievement): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("_id", sub._id)
            if (!TextUtils.isEmpty(sub._rev)) `object`.addProperty("_rev", sub._rev)
            `object`.addProperty("goals", sub.goals)
            `object`.addProperty("purpose", sub.purpose)
            `object`.addProperty("achievementsHeader", sub.achievementsHeader)
            `object`.add("references", sub.getReferencesArray())
            `object`.add("achievements", sub.achievementsArray)
            return `object`
        }

        @JvmStatic
        fun createReference(name: String?, relation: EditText, phone: EditText, email: EditText): JsonObject {
            val ob = JsonObject()
            ob.addProperty("name", name)
            ob.addProperty("phone", phone.text.toString())
            ob.addProperty("relationship", relation.text.toString())
            ob.addProperty("email", email.text.toString())
            return ob
        }

        @JvmStatic
        fun insert(mRealm: Realm, act: JsonObject?) = runBlocking {
            mRealm.write {
                val achievementId = JsonUtils.getString("_id", act)
                var achievement = this.query<RealmAchievement>("_id == $0", achievementId).first().find()

                if (achievement == null) {
                    achievement = RealmAchievement().apply {
                        _id = achievementId
                    }
                    achievement = this.copyToRealm(achievement)
                }

                achievement._rev = JsonUtils.getString("_rev", act)
                achievement.purpose = JsonUtils.getString("purpose", act)
                achievement.goals = JsonUtils.getString("goals", act)
                achievement.achievementsHeader = JsonUtils.getString("achievementsHeader", act)
                achievement.setReferences(JsonUtils.getJsonArray("references", act))
                achievement.setAchievements(JsonUtils.getJsonArray("achievements", act))

                val csvRow = arrayOf(
                    JsonUtils.getString("_id", act),
                    JsonUtils.getString("_rev", act),
                    JsonUtils.getString("purpose", act),
                    JsonUtils.getString("goals", act),
                    JsonUtils.getString("achievementsHeader", act),
                    JsonUtils.getJsonArray("references", act).toString(),
                    JsonUtils.getJsonArray("achievements", act).toString()
                )
                achievementDataList.add(csvRow)
            }
        }


        @JvmStatic
        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("achievementId", "achievement_rev", "purpose", "goals", "achievementsHeader", "references", "achievements"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun achievementWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/achievements.csv", achievementDataList)
        }
    }
}

