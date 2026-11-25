package org.ole.planet.myplanet.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcesRepositoryPackagingJunkTest {

    private fun junk(name: String) = ResourcesRepositoryImpl.isPackagingJunk(name)

    @Test
    fun `git folder contents are skipped at any depth`() {
        assertTrue(junk(".git/HEAD"))
        assertTrue(junk(".git/objects/4b/825dc642cb6eb9a060e54bf8d69288fbee4904"))
        assertTrue(junk("math-game/.git/config"))
    }

    @Test
    fun `other version control and macOS leftovers are skipped`() {
        assertTrue(junk(".svn/entries"))
        assertTrue(junk("__MACOSX/._index.html"))
        assertTrue(junk("assets/.DS_Store"))
        assertTrue(junk(".gitignore"))
    }

    @Test
    fun `the page and its assets are kept`() {
        assertFalse(junk("index.html"))
        assertFalse(junk("css/style.css"))
        assertFalse(junk("js/game.js"))
        assertFalse(junk("images/git-logo.png"))
        assertFalse(junk("legit/page.html"))
    }

    @Test
    fun `other dot files are kept`() {
        assertFalse(junk(".well-known/manifest.json"))
        assertFalse(junk("assets/.nojekyll"))
    }
}
