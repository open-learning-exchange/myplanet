package org.ole.planet.myplanet.ui.health

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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
    fun initExamination_loadsSavedNotesIntoEveryField() {
        val activity = Robolectric.buildActivity(HealthExaminationActivity::class.java).create().get()
        val key = org.ole.planet.myplanet.utils.AndroidDecrypter.generateKey()
        val iv = org.ole.planet.myplanet.utils.AndroidDecrypter.generateIv()
        val user = org.ole.planet.myplanet.model.UserEntity(id = "u1").apply { this.key = key; this.iv = iv }
        val sign = org.ole.planet.myplanet.model.Examination().apply {
            notes = "n"; diagnosis = "d"; treatments = "t"; medications = "m"; immunizations = "i"
            allergies = "a"; xrays = "x"; tests = "lab"; referrals = "r"
        }
        val exam = HealthExamination().apply {
            data = org.ole.planet.myplanet.utils.AndroidDecrypter.encrypt(
                org.ole.planet.myplanet.utils.GsonUtils.gson.toJson(sign), key, iv
            )
        }
        activity.user = user
        HealthExaminationActivity::class.java.getDeclaredField("examination").apply { isAccessible = true }.set(activity, exam)

        HealthExaminationActivity::class.java.getDeclaredMethod("initExamination").apply { isAccessible = true }.invoke(activity)

        fun text(id: Int) = activity.findViewById<android.widget.EditText>(id).text.toString()
        assertEquals("n", text(R.id.et_observation))
        assertEquals("d", text(R.id.et_diag))
        assertEquals("t", text(R.id.et_treatments))
        assertEquals("m", text(R.id.et_medications))
        assertEquals("i", text(R.id.et_immunization))
        assertEquals("a", text(R.id.et_allergies))
        assertEquals("x", text(R.id.et_xray))
        assertEquals("lab", text(R.id.et_labtest))
        assertEquals("r", text(R.id.et_referrals))
    }
}
