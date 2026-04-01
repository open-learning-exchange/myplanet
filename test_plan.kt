package org.ole.planet.myplanet.model

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.realm.Realm
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import android.content.Context
import org.ole.planet.myplanet.services.SharedPrefManager
import io.realm.RealmList
import io.realm.RealmQuery
import org.junit.After

class RealmSubmissionTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockSpm: SharedPrefManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(JsonUtils::class)
        mockkStatic(NetworkUtils::class)
        mockkStatic(RealmAnswer::class)
        mockkStatic(RealmStepExam::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `insert with attachments skips insertion`() {
        val submission = JsonObject()
        submission.addProperty("_attachments", "some_value")

        RealmSubmission.insert(mockRealm, submission)

        verify(exactly = 0) { mockRealm.beginTransaction() }
    }
}
