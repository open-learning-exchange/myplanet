package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Test
import org.ole.planet.myplanet.model.RealmUser

class UserRepositoryImplBulkInsertBenchmarkTest {

    @Test
    fun `benchmark bulkInsertUsersFromSync`() {
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
            mockk(relaxed = true)
        )
        val realm = mockk<Realm>(relaxed = true)
        val realmQuery = mockk<RealmQuery<RealmUser>>(relaxed = true)
        val realmResults = mockk<RealmResults<RealmUser>>(relaxed = true)

        every { realm.isInTransaction } returns true
        every { realm.where(RealmUser::class.java) } returns realmQuery
        every { realmQuery.`in`(any<String>(), any<Array<String>>()) } returns realmQuery
        every { realmQuery.findAll() } returns realmResults
        every { realmResults.iterator() } returns mutableListOf<RealmUser>().iterator()

        val mockUser = mockk<RealmUser>(relaxed = true)
        every { realm.createObject(RealmUser::class.java, any<String>()) } returns mockUser

        val jsonArray = JsonArray()
        for (i in 1..10) {
            val jObj = JsonObject()
            val doc = JsonObject()
            doc.addProperty("_id", "user_$i")
            doc.addProperty("name", "User $i")
            jObj.add("doc", doc)
            jsonArray.add(jObj)
        }

        val settings = mockk<SharedPreferences>(relaxed = true)

        userRepository.bulkInsertUsersFromSync(realm, jsonArray, settings)

        // The query is done only ONCE using `in`!
        verify(exactly = 1) { realm.where(RealmUser::class.java) }
    }
}
