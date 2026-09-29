package org.ole.planet.myplanet.ui.health

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.HealthExamination
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HealthExaminationActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testPreloadCustomDiagnosis_conditionInDiagnosisListNotAdded_conditionNotInListAdded() {
        val controller = Robolectric.buildActivity(HealthExaminationActivity::class.java)
        val activity = controller.create().get()

        val diagnosisArray = activity.resources.getStringArray(R.array.diagnosis_list)
        assertTrue("diagnosis_list array should not be empty", diagnosisArray.isNotEmpty())
        val inListCondition = diagnosisArray[0]
        val notInListCondition = "Custom Non-Existent Diagnosis"

        val examinationField = HealthExaminationActivity::class.java.getDeclaredField("examination")
        examinationField.isAccessible = true
        examinationField.set(activity, HealthExamination())

        val conditionsMapField = HealthExaminationActivity::class.java.getDeclaredField("conditionsMap")
        conditionsMapField.isAccessible = true
        conditionsMapField.set(activity, mapOf(
            inListCondition to true,
            notInListCondition to true
        ))

        val preloadMethod = HealthExaminationActivity::class.java.getDeclaredMethod("preloadCustomDiagnosis")
        preloadMethod.isAccessible = true
        preloadMethod.invoke(activity)

        val customDiagField = HealthExaminationActivity::class.java.getDeclaredField("customDiag")
        customDiagField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val customDiag = customDiagField.get(activity) as Set<String?>

        assertFalse("Condition present in diagnosis_list should NOT be added to customDiag", customDiag.contains(inListCondition))
        assertTrue("Condition NOT present in diagnosis_list with value=true SHOULD be added to customDiag", customDiag.contains(notInListCondition))
    }

    @Test
    fun getFloat_commaDecimalLocale_keepsDecimalVitals() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            val activity = Robolectric.buildActivity(HealthExaminationActivity::class.java).create().get()
            val getFloat = HealthExaminationActivity::class.java.getDeclaredMethod("getFloat", String::class.java)
            getFloat.isAccessible = true

            assertEquals(36.6f, getFloat.invoke(activity, "36.6") as Float, 0f)
            assertEquals(36.6f, getFloat.invoke(activity, "36,6") as Float, 0f)
            assertEquals(72.5f, getFloat.invoke(activity, "72.46") as Float, 0f)
            assertEquals(170f, getFloat.invoke(activity, "170") as Float, 0f)
            assertEquals(0f, getFloat.invoke(activity, "") as Float, 0f)
            assertEquals(0f, getFloat.invoke(activity, "abc") as Float, 0f)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
