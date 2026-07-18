package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao

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
}
