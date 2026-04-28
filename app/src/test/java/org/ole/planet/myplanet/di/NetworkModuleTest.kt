package org.ole.planet.myplanet.di

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    private class TestModel {
        var normalField: String = "normal"
        var nullField: String? = null
        val finalField: String = "final"
        @Transient
        var transientField: String = "transient"

        companion object {
            @JvmStatic
            var staticField: String = "static"
        }
    }

    @Test
    fun `provideGson returns Gson instance with correct configuration`() {
        val gson: Gson = NetworkModule.provideGson()

        assertNotNull(gson)

        val model = TestModel()
        val json = gson.toJson(model)

        assertTrue("JSON should contain normalField", json.contains("\"normalField\":\"normal\""))
        assertTrue("JSON should contain nullField due to serializeNulls()", json.contains("\"nullField\":null"))
        assertFalse("JSON should NOT contain transientField", json.contains("transientField"))
        assertFalse("JSON should NOT contain staticField", json.contains("staticField"))
        assertFalse("JSON should NOT contain finalField", json.contains("finalField"))
    }
}
