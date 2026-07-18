package org.ole.planet.myplanet.data.room.entity.legacy

import org.junit.Test
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LegacyRoomMappersTest {
    @Test
    fun `user mapper preserves identity roles and admin state`() {
        val user = RealmUser().apply {
            id = "local-user"
            _id = "remote-user"
            _rev = "2-rev"
            name = "learner"
            firstName = "Learner"
            lastName = "One"
            rolesList = mutableListOf("learner", "manager")
            userAdmin = true
            planetCode = "planet"
            parentCode = "parent"
            joinDate = 123L
        }

        val entity = assertNotNull(user.toRoomEntity())

        assertEquals("local-user", entity.id)
        assertEquals("remote-user", entity._id)
        assertEquals(listOf("learner", "manager"), entity.rolesList)
        assertEquals(true, entity.isUserAdmin)
        assertEquals(123L, entity.joined)
    }

    @Test
    fun `course mapper stores member and step ids as Room lists`() {
        val course = RealmMyCourse().apply {
            id = "course-local"
            courseId = "course-remote"
            courseRev = "1-course"
            courseTitle = "Math"
            description = "Numbers"
            courseSteps = mutableListOf(
                RealmCourseStep().apply { id = "step-1" },
                RealmCourseStep().apply { id = "step-2" },
            )
        }

        val entity = assertNotNull(course.toRoomEntity())

        assertEquals("course-local", entity.id)
        assertEquals("course-remote", entity.courseId)
        assertEquals(null, entity.userId)
        assertEquals(listOf("step-1", "step-2"), entity.steps)
    }

    @Test
    fun `exam mapper preserves survey upload fields`() {
        val exam = RealmStepExam().apply {
            id = "exam-1"
            _rev = null
            courseId = "course-1"
            stepId = "step-1"
            type = "surveys"
            sourceSurveyId = "source-1"
            isTeamShareAllowed = true
            noOfQuestions = 3
        }

        val entity = assertNotNull(exam.toRoomEntity())

        assertEquals("exam-1", entity.id)
        assertEquals("course-1", entity.courseId)
        assertEquals("step-1", entity.stepId)
        assertEquals("source-1", entity.sourceSurveyId)
        assertEquals(true, entity.isTeamShareAllowed)
        assertEquals(3, entity.noOfQuestions)
    }
}
