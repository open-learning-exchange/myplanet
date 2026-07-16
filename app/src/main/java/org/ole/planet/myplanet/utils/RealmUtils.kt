package org.ole.planet.myplanet.utils

import android.util.Log
import io.realm.RealmObject
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

object RealmUtils {
    private class FieldCacheEntry(val field: Field?)

    private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, FieldCacheEntry>()

    fun setRealmField(obj: RealmObject, fieldName: String, value: Any?) {
        try {
            val cacheKey = Pair(obj.javaClass, fieldName)
            var entry = fieldCache[cacheKey]

            if (entry == null) {
                var clazz: Class<*>? = obj.javaClass
                var field: Field? = null

                while (clazz != null && field == null) {
                    try {
                        field = clazz.getDeclaredField(fieldName)
                    } catch (e: NoSuchFieldException) {
                        clazz = clazz.superclass
                    }
                }

                if (field != null) {
                    field.isAccessible = true
                } else {
                    Log.w("RealmUtils", "Field $fieldName not found in class hierarchy of ${obj.javaClass.simpleName}")
                }

                entry = FieldCacheEntry(field)
                fieldCache[cacheKey] = entry
            } else if (entry.field == null) {
                Log.w("RealmUtils", "Field $fieldName not found in class hierarchy of ${obj.javaClass.simpleName}")
            }

            entry.field?.set(obj, value)
        } catch (e: Exception) {
            Log.w("RealmUtils", "Failed to set field $fieldName: ${e.message}")
        }
    }
}
