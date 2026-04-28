package org.ole.planet.myplanet.base

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat

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
        every { activity.getNotificationPermissionStatus() } answers { callOriginal() }
        every { activity.handleFilePermissionsResult(any(), any()) } answers { callOriginal() }
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

    @Test
    fun `getNotificationPermissionStatus returns GRANTED when SDK is below TIRAMISU and notifications enabled`() {
        mockkStatic(NotificationManagerCompat::class)
        val notificationManager = mockk<NotificationManagerCompat>()
        every { NotificationManagerCompat.from(activity) } returns notificationManager
        every { notificationManager.areNotificationsEnabled() } returns true

        val status = activity.getNotificationPermissionStatus()

        org.junit.Assert.assertEquals(BasePermissionActivity.NotificationPermissionStatus.GRANTED, status)
        unmockkStatic(NotificationManagerCompat::class)
    }

    @Test
    fun `getNotificationPermissionStatus returns DISABLED_IN_SETTINGS when SDK is below TIRAMISU and notifications disabled`() {
        mockkStatic(NotificationManagerCompat::class)
        val notificationManager = mockk<NotificationManagerCompat>()
        every { NotificationManagerCompat.from(activity) } returns notificationManager
        every { notificationManager.areNotificationsEnabled() } returns false

        val status = activity.getNotificationPermissionStatus()
        assertTrue(status == BasePermissionActivity.NotificationPermissionStatus.DISABLED_IN_SETTINGS)
        unmockkStatic(NotificationManagerCompat::class)
    }



    @Test
    fun `handleFilePermissionsResult grants media permissions correctly`() {
        val permissions = arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        val grantResults = intArrayOf(PackageManager.PERMISSION_DENIED)

        activity.handleFilePermissionsResult(permissions, grantResults)

        io.mockk.verify { activity.showMediaPermissionsDeniedDialog(listOf(Manifest.permission.READ_MEDIA_IMAGES)) }
    }
}
