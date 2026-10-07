package org.ole.planet.myplanet.services

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.GsonUtils

/**
 * The saved-users preference codec against the app-wide Gson it replaced: the same text for the
 * same users, and the same users (or a failure) for the same text, including the copy
 * `getSavedUsers` makes of each entry.
 */
class SavedUsersCodecParityTest {

    private val gson = GsonUtils.appGson()
    private val userListType = object : TypeToken<List<User>>() {}.type

    private fun outcome(block: () -> List<User>?): String = try {
        block()?.map { it.copy() }.toString()
    } catch (e: Exception) {
        "threw"
    }

    @Test
    fun `encodes the bytes Gson wrote`() {
        val users = listOf(
            User(fullName = TRICKY, name = "learner", password = "p@ss=<word>&'", image = "https://x.org/a?b=1&c=2", source = "member"),
            User(name = "guest"),
            User(),
        )
        assertEquals(gson.toJson(users), SharedPrefManager.encodeSavedUsers(users))
        assertEquals(gson.toJson(emptyList<User>()), SharedPrefManager.encodeSavedUsers(emptyList()))
    }

    @Test
    fun `decodes what Gson decoded`() {
        val inputs = listOf(
            "[]", "[{}]", """[{"name":"a","extra":1}]""", """[{"name":5,"password":1.50,"image":true}]""", """[{"name":{"x":1}}]""",
            """[{"name":["a"]}]""", "[null]", "null", "", "  ", """[{"name":"a",}]""", """{"name":"a"}""", """[{name:a}]""",
            """[{'name':'a'}]""", """[{"name":null,"fullName":"<f> & 'g'"}]""", """[1]""", """["a"]""", gson.toJson(listOf(User(fullName = TRICKY, name = "n"))),
        )
        for (input in inputs) {
            assertEquals(
                "input $input",
                outcome { gson.fromJson<List<User>>(input, userListType) ?: emptyList() },
                outcome { SharedPrefManager.decodeSavedUsers(input) },
            )
        }
    }
}
