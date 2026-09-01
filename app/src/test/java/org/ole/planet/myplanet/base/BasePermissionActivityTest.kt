package org.ole.planet.myplanet.base

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
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
        every { activity.getUsagesPermission(any()) } answers { callOriginal() }
        every { activity.handleFilePermissionsResult(any(), any()) } answers { callOriginal() }
        mockkStatic(ContextCompat::class)
        mockkStatic(Process::class)
        every { Process.myUid() } returns 1000
    }

    @After
    fun teardown() {
        unmockkStatic(Process::class)
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

    @Test
    fun `getUsagesPermission returns true when app ops mode is ALLOWED`() {
        val context = mockk<Context>(relaxed = true)
        val appOps = mockk<AppOpsManager>()
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOps
        every { context.packageName } returns "org.ole.planet.myplanet"
        every { appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, any(), any()) } returns AppOpsManager.MODE_ALLOWED
        every { appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, any(), any()) } returns AppOpsManager.MODE_ALLOWED

        val result = activity.getUsagesPermission(context)

        assertTrue(result)
    }

    @Test
    fun `getUsagesPermission returns false when app ops mode is ERRORED`() {
        val context = mockk<Context>(relaxed = true)
        val appOps = mockk<AppOpsManager>()
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOps
        every { context.packageName } returns "org.ole.planet.myplanet"
        every { appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, any(), any()) } returns AppOpsManager.MODE_ERRORED
        every { appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, any(), any()) } returns AppOpsManager.MODE_ERRORED

        val result = activity.getUsagesPermission(context)

        assertFalse(result)
    }

    @Test
    fun `getUsagesPermission checks permission when app ops mode is DEFAULT`() {
        val context = mockk<Context>(relaxed = true)
        val appOps = mockk<AppOpsManager>()
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOps
        every { context.packageName } returns "org.ole.planet.myplanet"
        every { appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, any(), any()) } returns AppOpsManager.MODE_DEFAULT
        every { appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, any(), any()) } returns AppOpsManager.MODE_DEFAULT
        every { context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) } returns PackageManager.PERMISSION_GRANTED

        val result = activity.getUsagesPermission(context)

        assertTrue(result)
    }
}
