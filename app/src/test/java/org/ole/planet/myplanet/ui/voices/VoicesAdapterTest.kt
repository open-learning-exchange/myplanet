package org.ole.planet.myplanet.ui.voices

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.VoicesLabelManager
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository

@RunWith(AndroidJUnit4::class)
class VoicesAdapterTest {

    @Test
    fun `setNonTeamMember updates membership payload without full rebind`() {
        val context = mockk<Context>(relaxed = true)
        val userSessionManager = mockk<UserSessionManager>(relaxed = true)
        val labelManager = mockk<VoicesLabelManager>(relaxed = true)
        val voicesRepository = mockk<VoicesRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)

        val adapter = spyk(VoicesAdapter(
            context = context,
            currentUser = mockk<RealmUser>(relaxed = true),
            parentNews = mockk<RealmNews>(relaxed = true),
            userSessionManager = userSessionManager,
            isTeamLeaderFn = {},
            getUserFn = { _, _ -> },
            getReplyCountFn = { _, _ -> {} },
            deletePostFn = {},
            shareNewsFn = { _, _, _, _, _ -> },
            getLibraryResourceFn = { _, _ -> },
            onEditAction = {},
            onAnimateTyping = { _, _, _ -> null },
            labelManager = labelManager,
            voicesRepository = voicesRepository,
            userRepository = userRepository,
            getCommunityLeadersFn = { "[]" },
            setRepliedNewsIdFn = {}
        ))

        adapter.setNonTeamMember(true)

        verify { adapter.notifyItemRangeChanged(0, adapter.itemCount, "PAYLOAD_MEMBERSHIP_CHANGED") }
    }
}
