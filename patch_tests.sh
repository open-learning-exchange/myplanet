cat << 'INNER' > app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt.patch
--- app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt
+++ app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt
@@ -10,6 +10,8 @@
 import io.mockk.mockkObject
 import io.mockk.mockkStatic
 import io.mockk.spyk
+import io.mockk.slot
+import io.mockk.Runs
 import io.mockk.unmockkObject
 import io.mockk.unmockkStatic
 import kotlinx.coroutines.CoroutineScope
@@ -19,6 +21,7 @@
 import kotlinx.coroutines.test.UnconfinedTestDispatcher
 import kotlinx.coroutines.test.advanceUntilIdle
 import kotlinx.coroutines.test.runTest
+import io.mockk.verify
 import org.junit.After
 import org.junit.Assert.assertEquals
 import org.junit.Assert.assertFalse
@@ -28,6 +31,7 @@
 import org.ole.planet.myplanet.data.DatabaseService
 import org.ole.planet.myplanet.data.api.ApiInterface
 import org.ole.planet.myplanet.model.RealmUser
+import org.ole.planet.myplanet.model.User
 import org.ole.planet.myplanet.services.SharedPrefManager
 import org.ole.planet.myplanet.services.UploadToShelfService
 import org.ole.planet.myplanet.utils.DispatcherProvider
@@ -181,4 +185,63 @@
         val result = repository.hasAtLeastOneUser()
         assertEquals(false, result)
     }
+
+    @Test
+    fun \`saveSavedUser adds a new guest\`() = runTest {
+        every { sharedPrefManager.getSavedUsers() } returns emptyList()
+        val savedSlot = slot<List<User>>()
+        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs
+
+        repository.saveSavedUser("guest1", "encrypted", "guest", null, null)
+
+        assertEquals(1, savedSlot.captured.size)
+        assertEquals("guest1", savedSlot.captured[0].name)
+        assertEquals("guest", savedSlot.captured[0].source)
+    }
+
+    @Test
+    fun \`saveSavedUser replaces existing guest with the same name\`() = runTest {
+        val existing = User("", "guest1", "oldPwd", "", "guest")
+        every { sharedPrefManager.getSavedUsers() } returns listOf(existing)
+        val savedSlot = slot<List<User>>()
+        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs
+
+        repository.saveSavedUser("guest1", "newPwd", "guest", null, null)
+
+        assertEquals(1, savedSlot.captured.size)
+        assertEquals("newPwd", savedSlot.captured[0].password)
+    }
+
+    @Test
+    fun \`saveSavedUser replaces existing member with the same username\`() = runTest {
+        val existing = User("user1", "Full Name", "oldPwd", "old.jpg", "member")
+        every { sharedPrefManager.getSavedUsers() } returns listOf(existing)
+        val savedSlot = slot<List<User>>()
+        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs
+
+        repository.saveSavedUser("Full Name", "newPwd", "member", "new.jpg", "user1")
+
+        assertEquals(1, savedSlot.captured.size)
+        assertEquals("newPwd", savedSlot.captured[0].password)
+        assertEquals("new.jpg", savedSlot.captured[0].image)
+    }
+
+    @Test
+    fun \`resetGuestAsMember removes saved users matching the username\`() = runTest {
+        val guest = User("", "guest1", "pwd", "", "guest")
+        val other = User("Full Name", "user2", "pwd", "", "member")
+        every { sharedPrefManager.getSavedUsers() } returns listOf(guest, other)
+        val savedSlot = slot<List<User>>()
+        every { sharedPrefManager.setSavedUsers(capture(savedSlot)) } just Runs
+
+        repository.resetGuestAsMember("guest1")
+
+        assertEquals(listOf(other), savedSlot.captured)
+    }
+
+    @Test
+    fun \`resetGuestAsMember does nothing when the username is not saved\`() = runTest {
+        every { sharedPrefManager.getSavedUsers() } returns listOf(User("Full Name", "user2", "pwd", "", "member"))
+        repository.resetGuestAsMember("guest1")
+        verify(exactly = 0) { sharedPrefManager.setSavedUsers(any()) }
+    }
 }
INNER
patch -p0 < app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt.patch
rm app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt.patch
