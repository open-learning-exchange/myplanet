package org.ole.planet.myplanet.services

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDownloadPolicyTest {

    @Test
    fun `a zero limit means no limit`() {
        assertFalse(SyncDownloadPolicy.exceedsLimit(totalBytes = 500L * 1024 * 1024, limitBytes = 0L))
    }

    @Test
    fun `only files strictly larger than the limit exceed it`() {
        val limit = 5L * 1024 * 1024
        assertFalse(SyncDownloadPolicy.exceedsLimit(limit, limit))
        assertTrue(SyncDownloadPolicy.exceedsLimit(limit + 1, limit))
    }

    @Test
    fun `a url is sync-owned only when sync queued it`() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getStringSet(DownloadService.SYNC_DOWNLOADS_KEY, any()) } returns setOf("http://x/resources/1/a.png")

        assertTrue(SyncDownloadPolicy.isSyncOwned(preferences, "http://x/resources/1/a.png"))
        assertFalse(SyncDownloadPolicy.isSyncOwned(preferences, "http://x/resources/2/b.pdf"))
    }
}
