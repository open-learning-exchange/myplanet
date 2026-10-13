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

    @Test
    fun `calling requestAllPermissions twice queries packageManager getPackageInfo exactly once`() {
        mockkStatic(androidx.core.app.ActivityCompat::class)
        try {
            every { activity.requestAllPermissions() } answers { callOriginal() }
            every { activity["isPermissionDeclaredInManifest"](any<String>()) } answers { callOriginal() }
            val packageManager = mockk<PackageManager>()
            val packageInfo = android.content.pm.PackageInfo().apply {
                requestedPermissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.CAMERA)
            }
            every { activity.packageManager } returns packageManager
            every { activity.packageName } returns "org.ole.planet.myplanet"
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
            every { androidx.core.app.ActivityCompat.requestPermissions(any(), any(), any()) } returns Unit

            activity.requestAllPermissions()
            activity.requestAllPermissions()

            io.mockk.verify(exactly = 1) { packageManager.getPackageInfo(any<String>(), any<Int>()) }
        } finally {
            unmockkStatic(androidx.core.app.ActivityCompat::class)
        }
    }

    @Test
    fun `when first getPackageInfo throws first pass requests no storage permissions and second pass queries again`() {
        mockkStatic(androidx.core.app.ActivityCompat::class)
        try {
            every { activity.requestAllPermissions() } answers { callOriginal() }
            every { activity["isPermissionDeclaredInManifest"](any<String>()) } answers { callOriginal() }
            val packageManager = mockk<PackageManager>()
            val packageInfo = android.content.pm.PackageInfo().apply {
                requestedPermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            every { activity.packageManager } returns packageManager
            every { activity.packageName } returns "org.ole.planet.myplanet"
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws RuntimeException("Error")
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

            val slot1 = io.mockk.slot<Array<String>>()
            val slot2 = io.mockk.slot<Array<String>>()
            every { androidx.core.app.ActivityCompat.requestPermissions(any(), capture(slot1), any()) } returns Unit

            activity.requestAllPermissions()

            assertFalse(slot1.captured.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            assertFalse(slot1.captured.contains(Manifest.permission.READ_EXTERNAL_STORAGE))

            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
            every { androidx.core.app.ActivityCompat.requestPermissions(any(), capture(slot2), any()) } returns Unit

            activity.requestAllPermissions()

            assertTrue(slot2.captured.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            assertTrue(slot2.captured.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
            io.mockk.verify(exactly = 3) { packageManager.getPackageInfo(any<String>(), any<Int>()) }
        } finally {
            unmockkStatic(androidx.core.app.ActivityCompat::class)
        }
    }

    @Test
    fun `permissions missing from requestedPermissions are not requested`() {
        mockkStatic(androidx.core.app.ActivityCompat::class)
        try {
            every { activity.requestAllPermissions() } answers { callOriginal() }
            every { activity["isPermissionDeclaredInManifest"](any<String>()) } answers { callOriginal() }
            val packageManager = mockk<PackageManager>()
            val packageInfo = android.content.pm.PackageInfo().apply {
                requestedPermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            every { activity.packageManager } returns packageManager
            every { activity.packageName } returns "org.ole.planet.myplanet"
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

            val slot = io.mockk.slot<Array<String>>()
            every { androidx.core.app.ActivityCompat.requestPermissions(any(), capture(slot), any()) } returns Unit

            activity.requestAllPermissions()

            assertTrue(slot.captured.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            assertFalse(slot.captured.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        } finally {
            unmockkStatic(androidx.core.app.ActivityCompat::class)
        }
    }
}
