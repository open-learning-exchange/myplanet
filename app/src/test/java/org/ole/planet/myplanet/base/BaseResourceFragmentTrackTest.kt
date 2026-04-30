package org.ole.planet.myplanet.base

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.utils.Constants
import java.lang.reflect.Field

class BaseResourceFragmentTrackTest {

    @Test
    fun `trackDownloadUrls should store urls in pendingDownloadUrls and format correctly in SharedPreferences`() {
        // Mock SharedPreferences
        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val context = mockk<Context>(relaxed = true)

        every { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.getString("downloaded_urls", "") } returns "url1,url2"
        every { sharedPreferences.edit() } returns editor
        every { editor.putString("downloaded_urls", any()) } returns editor

        val fragment = object : BaseResourceFragment() {
            // override getContext() to return mockk
            override fun getContext(): Context? {
                return context
            }
            // Expose protected method
            fun callTrack(urls: Collection<String>) {
                trackDownloadUrls(urls)
            }
        }

        val initialUrls = listOf("http://example.com/old_file")
        val newUrls = listOf("url3", "url4")

        val field: Field = BaseResourceFragment::class.java.getDeclaredField("pendingDownloadUrls")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val pendingDownloadUrls = field.get(fragment) as MutableSet<String>

        // Populate initial data to test the clear() functionality
        pendingDownloadUrls.addAll(initialUrls)

        // Act
        fragment.callTrack(newUrls)

        // Assert pendingDownloadUrls logic
        assertEquals(2, pendingDownloadUrls.size)
        assertTrue(pendingDownloadUrls.contains("url3"))
        assertTrue(pendingDownloadUrls.contains("url4"))
        assertEquals(false, pendingDownloadUrls.contains("http://example.com/old_file"))

        // Assert SharedPreferences logic
        verify {
            editor.putString("downloaded_urls", "url1,url2,url3,url4")
            editor.apply()
        }
    }

    @Test
    fun `trackDownloadUrls should handle empty initial string in SharedPreferences`() {
        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val context = mockk<Context>(relaxed = true)

        every { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.getString("downloaded_urls", "") } returns ""
        every { sharedPreferences.edit() } returns editor
        every { editor.putString("downloaded_urls", any()) } returns editor

        val fragment = object : BaseResourceFragment() {
            // override getContext() to return mockk
            override fun getContext(): Context? {
                return context
            }
            fun callTrack(urls: Collection<String>) {
                trackDownloadUrls(urls)
            }
        }

        fragment.callTrack(listOf("url1", "url2"))

        verify {
            editor.putString("downloaded_urls", "url1,url2")
            editor.apply()
        }
    }
}
