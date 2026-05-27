package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmMyLife

class LifeRepositoryTest {

    private lateinit var lifeRepository: LifeRepository

    @Before
    fun setup() {
        lifeRepository = mockk(relaxed = true)
    }

    @Test
    fun testUpdateVisibility() = runTest {
        val myLifeId = "123"
        val isVisible = true

        lifeRepository.updateVisibility(isVisible, myLifeId)

        coVerify(exactly = 1) { lifeRepository.updateVisibility(isVisible, myLifeId) }
    }

    @Test
    fun testUpdateMyLifeListOrder() = runTest {
        val list = listOf(RealmMyLife().apply { _id = "1" })

        lifeRepository.updateMyLifeListOrder(list)

        coVerify(exactly = 1) { lifeRepository.updateMyLifeListOrder(list) }
    }

    @Test
    fun testGetMyLifeByUserId() = runTest {
        val userId = "user1"
        val expectedList = listOf(RealmMyLife().apply { _id = "1" })
        coEvery { lifeRepository.getMyLifeByUserId(userId, any()) } returns expectedList

        val result = lifeRepository.getMyLifeByUserId(userId)

        assertEquals(expectedList, result)
        coVerify(exactly = 1) { lifeRepository.getMyLifeByUserId(userId, any()) }
    }

    @Test
    fun testSeedMyLifeIfEmpty() = runTest {
        val userId = "user1"
        val items = listOf(RealmMyLife().apply { _id = "1" })

        lifeRepository.seedMyLifeIfEmpty(userId, items)

        coVerify(exactly = 1) { lifeRepository.seedMyLifeIfEmpty(userId, items) }
    }
}
