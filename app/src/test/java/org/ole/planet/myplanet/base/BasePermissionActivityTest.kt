package org.ole.planet.myplanet.base

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BasePermissionActivityTest {

    private lateinit var activity: BasePermissionActivity

    @Before
    fun setup() {
        activity = mockk<BasePermissionActivity>(relaxed = true)
        every { activity.checkPermission(any()) } answers { callOriginal() }
        mockkStatic(ContextCompat::class)
    }

    @After
    fun teardown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `checkPermission returns true when permission is granted`() {
        val permission = Manifest.permission.CAMERA
        every { ContextCompat.checkSelfPermission(activity, permission) } returns PackageManager.PERMISSION_GRANTED

        val result = activity.checkPermission(permission)

        assertTrue(result)
    }

    @Test
    fun `checkPermission returns false when permission is denied`() {
        val permission = Manifest.permission.CAMERA
        every { ContextCompat.checkSelfPermission(activity, permission) } returns PackageManager.PERMISSION_DENIED

        val result = activity.checkPermission(permission)

        assertFalse(result)
    }

    @Test
    fun `checkPermission returns false when permission string is null`() {
        val result = activity.checkPermission(null)

        assertFalse(result)
    }
}
