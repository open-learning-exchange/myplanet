package org.ole.planet.myplanet.ui.user

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemGamificationBadgeBinding
import org.ole.planet.myplanet.model.gamification.BadgeCategory
import org.ole.planet.myplanet.model.gamification.GamificationBadge
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class GamificationBadgesAdapterTest {

    private lateinit var themedContext: Context
    private lateinit var adapter: GamificationBadgesAdapter

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        themedContext = ContextThemeWrapper(app, R.style.AppTheme_MaterialComponents)
        adapter = GamificationBadgesAdapter()
    }

    @Test
    fun testBadgeBinding_unlockedBadge() {
        val badge = GamificationBadge(
            id = "course_1",
            title = "Course Graduate",
            description = "Complete 1 full course",
            category = BadgeCategory.COURSES,
            iconEmoji = "🎓",
            currentProgress = 1,
            maxProgress = 1,
            isUnlocked = true
        )

        var committed = false
        adapter.submitList(listOf(badge)) { committed = true }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = FrameLayout(themedContext)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val binding = ItemGamificationBadgeBinding.bind(holder.itemView)
        assertEquals("Course Graduate", binding.tvBadgeTitle.text.toString())
        assertEquals("🎓", binding.tvBadgeEmoji.text.toString())
        assertEquals(View.VISIBLE, binding.ivBadgeCheck.visibility)
        assertEquals("Unlocked", binding.tvBadgeStatus.text.toString())
        assertEquals(100, binding.pbBadgeProgress.progress)
    }

    @Test
    fun testBadgeBinding_lockedBadge() {
        val badge = GamificationBadge(
            id = "streak_7",
            title = "Momentum",
            description = "Reach a 7-day study streak",
            category = BadgeCategory.STREAKS,
            iconEmoji = "⚡",
            currentProgress = 3,
            maxProgress = 7,
            isUnlocked = false
        )

        var committed = false
        adapter.submitList(listOf(badge)) { committed = true }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        val parent = FrameLayout(themedContext)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val binding = ItemGamificationBadgeBinding.bind(holder.itemView)
        assertEquals("Momentum", binding.tvBadgeTitle.text.toString())
        assertEquals("⚡", binding.tvBadgeEmoji.text.toString())
        assertEquals(View.GONE, binding.ivBadgeCheck.visibility)
        assertEquals("3 / 7", binding.tvBadgeStatus.text.toString())
        assertEquals(42, binding.pbBadgeProgress.progress) // 3/7 * 100
    }
}
