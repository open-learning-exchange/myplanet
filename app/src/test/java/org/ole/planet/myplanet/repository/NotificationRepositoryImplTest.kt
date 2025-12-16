package org.ole.planet.myplanet.repository

import io.realm.Realm
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.util.createTeamAndMember
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationRepositoryImplTest {
    private lateinit var a: NotificationRepository
    private lateinit var db: DatabaseService
    private lateinit var r: Realm

    @Before
    fun su() {
        db = DatabaseService()
        r = db.realmInstance
        a = NotificationRepositoryImpl(db)
    }

    @After
    fun cu() {
        r.close()
    }

    @Test
    fun `getTeamNotifications returns correct counts for chat and tasks`() = runTest {
        val (teamId, userId) = createTeamAndMember(r, "t1", "u1")

        r.executeTransaction {
            it.createObject(RealmNews::class.java).apply {
                viewableBy = "teams"
                viewableId = teamId
            }
            it.createObject(RealmTeamNotification::class.java).apply {
                parentId = teamId
                type = "chat"
                lastCount = 0
            }
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR, 1)
            it.createObject(RealmTeamTask::class.java).apply {
                this.teamId = teamId
                assignee = userId
                deadline = cal.timeInMillis
            }
        }
        val teamIds = listOf(teamId)
        val notifications = a.getTeamNotifications(teamIds, userId)
        val teamNotification = notifications[teamId]

        assertEquals(1, notifications.size)
        assertTrue(teamNotification!!.hasChat)
        assertTrue(teamNotification.hasTask)
    }

    @Test
    fun `getTeamNotifications handles no new chats correctly`() = runTest {
        val (teamId, userId) = createTeamAndMember(r, "t2", "u2")

        r.executeTransaction {
            it.createObject(RealmTeamNotification::class.java).apply {
                parentId = teamId
                type = "chat"
                lastCount = 1
            }
        }
        val teamIds = listOf(teamId)
        val notifications = a.getTeamNotifications(teamIds, userId)
        val teamNotification = notifications[teamId]

        assertFalse(teamNotification!!.hasChat)
    }

    @Test
    fun `getTeamNotifications handles no tasks correctly`() = runTest {
        val (teamId, userId) = createTeamAndMember(r, "t3", "u3")
        val teamIds = listOf(teamId)
        val notifications = a.getTeamNotifications(teamIds, userId)
        val teamNotification = notifications[teamId]

        assertFalse(teamNotification!!.hasTask)
    }

    @Test
    fun `getTeamNotifications handles empty team list`() = runTest {
        val notifications = a.getTeamNotifications(emptyList(), "anyUser")

        assertTrue(notifications.isEmpty())
    }
}
