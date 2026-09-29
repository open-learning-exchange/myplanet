package org.ole.planet.myplanet.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class StoragePathResolverTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var resolver: StoragePathResolver

    @Before
    fun setUp() {
        mockkObject(FileUtils)
        resolver = StoragePathResolver(context)
    }

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
    }

    @Test
    fun `resolveTeamAttachment returns null when teamId is null or blank`() {
        assertNull(resolver.resolveTeamAttachment(null, "image.png"))
        assertNull(resolver.resolveTeamAttachment("", "image.png"))
        assertNull(resolver.resolveTeamAttachment("   ", "image.png"))
    }

    @Test
    fun `resolveTeamAttachment returns null when imageName is null or blank`() {
        assertNull(resolver.resolveTeamAttachment("team1", null))
        assertNull(resolver.resolveTeamAttachment("team1", ""))
        assertNull(resolver.resolveTeamAttachment("team1", "   "))
    }

    @Test
    fun `resolveTeamAttachment pins path when ole directory is non-empty`() {
        every { FileUtils.getOlePath(context) } returns "/sdcard/Android/data/org.ole.planet.myplanet/files/ole/"

        val file = resolver.resolveTeamAttachment("team123", "logo.png")

        assertEquals(
            "/sdcard/Android/data/org.ole.planet.myplanet/files/ole/team_attachments/team123/logo.png",
            file?.path
        )
    }

    @Test
    fun `resolveTeamAttachment returns relative path when ole directory is empty string`() {
        every { FileUtils.getOlePath(context) } returns ""

        val file = resolver.resolveTeamAttachment("team123", "logo.png")

        assertEquals(
            "team_attachments/team123/logo.png",
            file?.path
        )
    }
}
