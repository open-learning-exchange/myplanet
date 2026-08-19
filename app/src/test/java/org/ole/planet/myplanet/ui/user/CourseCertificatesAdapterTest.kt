package org.ole.planet.myplanet.ui.user

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.databinding.ItemCourseCertificateBinding
import org.ole.planet.myplanet.model.gamification.CourseCertificate
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class CourseCertificatesAdapterTest {

    private lateinit var themedContext: Context
    private lateinit var adapter: CourseCertificatesAdapter
    private var clickedCert: CourseCertificate? = null

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        themedContext = ContextThemeWrapper(app, org.ole.planet.myplanet.R.style.AppTheme_MaterialComponents)
        adapter = CourseCertificatesAdapter { cert ->
            clickedCert = cert
        }
    }

    @Test
    fun testCertificateBindingAndClick() {
        val certificate = CourseCertificate(
            courseId = "c101",
            courseTitle = "Basic Health and Nutrition",
            learnerName = "John Doe",
            completionDate = "August 19, 2026",
            certificateId = "OLE-CERT-C101-J123"
        )

        var committed = false
        adapter.submitList(listOf(certificate)) { committed = true }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = FrameLayout(themedContext)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val binding = ItemCourseCertificateBinding.bind(holder.itemView)
        assertEquals("Basic Health and Nutrition", binding.tvCertCourseTitle.text.toString())
        assertEquals("August 19, 2026", binding.tvCertDate.text.toString())

        binding.btnViewCertificate.performClick()
        assertNotNull(clickedCert)
        assertEquals("c101", clickedCert?.courseId)
    }
}
