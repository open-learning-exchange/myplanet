package org.ole.planet.myplanet.ui.feedback

import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.coEvery
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.slot
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnBaseRealtimeSyncListener
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.junit.Rule
import org.junit.Assert.assertNotNull
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.shadows.ShadowLooper
import org.ole.planet.myplanet.MainApplication
import androidx.lifecycle.Lifecycle
import org.ole.planet.myplanet.services.sync.SyncManager
import androidx.test.core.app.ApplicationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.DatabaseModule
import io.realm.Realm
import dagger.hilt.android.AndroidEntryPoint
import androidx.appcompat.app.AppCompatActivity

@AndroidEntryPoint
class TestActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Black_NoTitleBar)
        super.onCreate(savedInstanceState)
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object FakeDatabaseModule {
    @Provides
    fun provideDatabaseService(): DatabaseService = mockk(relaxed = true)
}

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [28])
@HiltAndroidTest
class FeedbackListFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var syncManagerMock: RealtimeSyncManager
    private lateinit var fragment: FeedbackListFragment

    private lateinit var activityController: org.robolectric.android.controller.ActivityController<TestActivity>

    @Before
    fun setup() {
        hiltRule.inject()

        mockkStatic(io.realm.log.RealmLog::class)
        every { io.realm.log.RealmLog.getLevel() } returns 0
        every { io.realm.log.RealmLog.setLevel(any()) } answers {}

        mockkStatic(Realm::class)
        every { Realm.init(any<Context>()) } answers {}
        val mockRealm = mockk<Realm>(relaxed = true)
        every { Realm.getDefaultInstance() } returns mockRealm
        every { mockRealm.isClosed } returns false

        mockkObject(RealtimeSyncManager.Companion)
        syncManagerMock = mockk(relaxed = true)
        every { RealtimeSyncManager.getInstance() } returns syncManagerMock

        mockkObject(MainApplication.Companion)
        coEvery { MainApplication.isServerReachable(any()) } returns true
        every { MainApplication.context } returns ApplicationProvider.getApplicationContext<Context>()

        activityController = org.robolectric.Robolectric.buildActivity(TestActivity::class.java).setup()
        fragment = FeedbackListFragment()
        fragment.sharedPrefManager = mockk(relaxed = true)
        fragment.serverUrlMapper = mockk(relaxed = true)
        fragment.syncManager = mockk(relaxed = true)
        every { fragment.sharedPrefManager.getServerUrl() } returns "http://example.com"
        every { fragment.sharedPrefManager.getFastSync() } returns false
    }

    @After
    fun teardown() {
        activityController.destroy()
        clearAllMocks()
        unmockkAll()
    }

    private fun attach() {
        activityController.get().supportFragmentManager.beginTransaction()
            .add(fragment, "FeedbackListFragment")
            .commitNow()
    }

    private fun detach() {
        activityController.get().supportFragmentManager.beginTransaction()
            .remove(fragment)
            .commitNow()
    }

    @Test
    fun testListenerRegistrationHappensOnceWhenViewIsCreated() {
        attach()
        verify(exactly = 1) { syncManagerMock.addListener(any()) }
    }

    @Test
    fun testRemoveListenerCalledOnDestroyViewAndNoStaleCallbacks() {
        attach()

        val listenerSlot = slot<OnBaseRealtimeSyncListener>()
        verify { syncManagerMock.addListener(capture(listenerSlot)) }
        val registeredListener = listenerSlot.captured
        assertNotNull(registeredListener)

        detach()

        verify(exactly = 1) { syncManagerMock.removeListener(registeredListener) }
    }

    @Test
    fun testBackgroundServerChecksDoNotOutliveFragmentViewLifecycle() {
        val mockSync = mockk<SyncManager>(relaxed = true)
        fragment.syncManager = mockSync

        every { fragment.sharedPrefManager.getFastSync() } returns true
        every { fragment.sharedPrefManager.isFeedbackSynced() } returns false

        attach()

        val method = FeedbackListFragment::class.java.getDeclaredMethod("checkServerAndStartSync")
        method.isAccessible = true
        method.invoke(fragment)

        detach()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(exactly = 0) { mockSync.start(any(), any(), any()) }
    }
}
