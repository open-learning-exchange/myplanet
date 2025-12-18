package org.ole.planet.myplanet.model

import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class RealmCourseProgressTest {

    @Mock
    private lateinit var mockRealm: Realm

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `getCurrentProgress should return correct progress with many steps`() {
        // Arrange
        val steps = (1..1000).map { createMockCourseStep() }
        val progresses = (1..1000).map { createMockCourseProgress(it) }
        val query = mock(io.realm.RealmQuery::class.java)

        `when`(mockRealm.where(RealmCourseProgress::class.java)).thenReturn(query)
        `when`(query.equalTo(anyString(), anyString())).thenReturn(query)
        `when`(query.findAll()).thenReturn(mock(io.realm.RealmResults::class.java).apply {
            `when`(iterator()).thenReturn(progresses.iterator())
        })

        // Act
        val currentProgress = RealmCourseProgress.getCurrentProgress(steps, mockRealm, "testUser", "testCourse")

        // Assert
        assertEquals(1000, currentProgress)
    }

    @Test
    fun `getCurrentProgress should return 0 when there are no steps`() {
        // Arrange
        val steps = emptyList<RealmCourseStep>()
        val progresses = emptyList<RealmCourseProgress>()
        val query = mock(io.realm.RealmQuery::class.java)
        `when`(mockRealm.where(RealmCourseProgress::class.java)).thenReturn(query)
        `when`(query.equalTo(anyString(), anyString())).thenReturn(query)
        `when`(query.findAll()).thenReturn(mock(io.realm.RealmResults::class.java).apply {
            `when`(iterator()).thenReturn(progresses.iterator())
        })

        // Act
        val currentProgress = RealmCourseProgress.getCurrentProgress(steps, mockRealm, "testUser", "testCourse")

        // Assert
        assertEquals(0, currentProgress)
    }

    @Test
    fun `getCurrentProgress should return correct progress when there is a gap`() {
        // Arrange
        val steps = (1..10).map { createMockCourseStep() }
        val progresses = (1..5).map { createMockCourseProgress(it) } + (7..10).map { createMockCourseProgress(it) }
        val query = mock(io.realm.RealmQuery::class.java)

        `when`(mockRealm.where(RealmCourseProgress::class.java)).thenReturn(query)
        `when`(query.equalTo(anyString(), anyString())).thenReturn(query)
        `when`(query.findAll()).thenReturn(mock(io.realm.RealmResults::class.java).apply {
            `when`(iterator()).thenReturn(progresses.iterator())
        })

        // Act
        val currentProgress = RealmCourseProgress.getCurrentProgress(steps, mockRealm, "testUser", "testCourse")

        // Assert
        assertEquals(5, currentProgress)
    }

    private fun createMockCourseStep(): RealmCourseStep {
        return mock(RealmCourseStep::class.java)
    }

    private fun createMockCourseProgress(stepNum: Int): RealmCourseProgress {
        return mock(RealmCourseProgress::class.java).apply {
            `when`(this.stepNum).thenReturn(stepNum)
        }
    }
}
