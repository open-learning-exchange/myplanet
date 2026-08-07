package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.UserDao

class UserRepositoryBulkInsertTest {
    @Test
    fun `benchmark insertUsersFromSync`() = runTest {
        val userDao = mockk<UserDao>(relaxed = true)
        val userRepository = UserRepositoryImpl(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            userDao
        )
        coEvery { userDao.getAll() } returns emptyList()

        val jsonArray = JsonArray()
        for (i in 1..10) {
            val jObj = JsonObject()
            val doc = JsonObject()
            doc.addProperty("_id", "user_$i")
            doc.addProperty("name", "User $i")
            jObj.add("doc", doc)
            jsonArray.add(jObj)
        }


        val list = mutableListOf<JsonObject>()
        for (j in jsonArray) {
            list.add(j.asJsonObject)
        }
        userRepository.insertUsersFromSync(list)

        coVerify(exactly = 1) { userDao.upsertAll(match { it.size == 10 }) }
    }

    @Test
    fun `insertUsersFromSync handles existing user and guest promotion correctly`() = runTest {
        val userDao = mockk<UserDao>(relaxed = true)
        val userRepository = UserRepositoryImpl(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            userDao
        )

        val existingGuest = org.ole.planet.myplanet.model.UserEntity().apply {
            id = "guest_123"
            _id = "guest_123"
            name = "Guest User"
        }
        coEvery { userDao.getAll() } returns listOf(existingGuest)

        val list = mutableListOf<JsonObject>()
        val jObj = JsonObject()
        val doc = JsonObject()
        doc.addProperty("_id", "org.couchdb.user:Guest User")
        doc.addProperty("name", "Guest User")
        jObj.add("doc", doc)
        list.add(jObj)

        userRepository.insertUsersFromSync(list)

        coVerify(exactly = 1) { userDao.deleteByIds(listOf("guest_123")) }
        coVerify(exactly = 1) { userDao.upsertAll(match { it.size == 1 && it[0]._id == "org.couchdb.user:Guest User" }) }
    }

    @Test
    fun `insertUsersFromSync deduplicates ids correctly`() = runTest {
        val userDao = mockk<UserDao>(relaxed = true)
        val userRepository = UserRepositoryImpl(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            userDao
        )
        coEvery { userDao.getAll() } returns emptyList()

        val list = mutableListOf<JsonObject>()
        for (i in 1..2) {
            val jObj = JsonObject()
            val doc = JsonObject()
            doc.addProperty("_id", "user_1")
            doc.addProperty("name", "User 1 (version $i)")
            jObj.add("doc", doc)
            list.add(jObj)
        }

        userRepository.insertUsersFromSync(list)

        coVerify(exactly = 1) { userDao.upsertAll(match { it.size == 1 && it[0].name == "User 1 (version 2)" }) }
    }
}
