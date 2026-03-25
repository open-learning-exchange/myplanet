package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmTag

@OptIn(ExperimentalCoroutinesApi::class)
class TagsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: TagsRepositoryImpl
    private lateinit var mockRealm: Realm

    @Before
    fun setup() {
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        coEvery { databaseService.withRealmAsync<List<RealmTag>>(any()) } answers {
            val operation = firstArg<(Realm) -> List<RealmTag>>()
            operation(mockRealm)
        }
        repository = TagsRepositoryImpl(databaseService)
    }

    private fun mockQueryResults(vararg results: List<RealmTag>): RealmQuery<RealmTag> {
        val mockQuery = mockk<RealmQuery<RealmTag>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmTag>>(relaxed = true)

        every { mockRealm.where(RealmTag::class.java) } returns mockQuery

        // Setup fluent return
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<Boolean>()) } returns mockQuery
        every { mockQuery.isNotEmpty(any<String>()) } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<String>>()) } returns mockQuery

        every { mockQuery.findAll() } returns mockResults

        // Map sequential calls to copyFromRealm to different results
        if (results.size == 1) {
             every { mockRealm.copyFromRealm(mockResults) } returns results[0]
        } else {
             every { mockRealm.copyFromRealm(mockResults) } returnsMany results.toList()
        }

        return mockQuery
    }

    @Test
    fun `getTags filters by dbType, non-empty name, and isAttached=false`() = runTest {
        val mockResult = listOf(RealmTag().apply { name = "Tag1" })
        val mockQuery = mockQueryResults(mockResult)

        val result = repository.getTags("resources")

        assertEquals(mockResult, result)
        verify {
            mockQuery.equalTo("db", "resources")
            mockQuery.isNotEmpty("name")
            mockQuery.equalTo("isAttached", false)
        }
    }

    @Test
    fun `buildChildMap correctly groups tags by their attachedTo parents`() = runTest {
        val parent1 = "parent1"
        val parent2 = "parent2"
        val child1 = RealmTag().apply {
            name = "Child1"
            attachedTo = RealmList(parent1)
        }
        val child2 = RealmTag().apply {
            name = "Child2"
            attachedTo = RealmList(parent1, parent2)
        }

        mockQueryResults(listOf(child1, child2))

        val result = repository.buildChildMap()

        assertEquals(2, result.size)
        assertTrue(result[parent1]!!.contains(child1))
        assertTrue(result[parent1]!!.contains(child2))
        assertEquals(2, result[parent1]!!.size)

        assertTrue(result[parent2]!!.contains(child2))
        assertEquals(1, result[parent2]!!.size)
    }

    @Test
    fun `getTagsForResource resolves linked tags through tagId lookup`() = runTest {
        val resourceId = "res1"
        val tagId = "tag1"
        val linkTag = RealmTag().apply {
            db = "resources"
            linkId = resourceId
            this.tagId = tagId
        }
        val parentTag = RealmTag().apply { id = tagId; name = "Parent Tag" }

        val mockQuery = mockQueryResults(listOf(linkTag), listOf(parentTag))

        val result = repository.getTagsForResource(resourceId)

        assertEquals(1, result.size)
        assertEquals("Parent Tag", result[0].name)

        verify { mockQuery.equalTo("db", "resources") }
        verify { mockQuery.equalTo("linkId", resourceId) }
        verify { mockQuery.`in`("id", arrayOf(tagId)) }
    }

    @Test
    fun `getTagsForResources returns correct per-resource tag map`() = runTest {
        val resourceIds = listOf("res1", "res2")
        val linkTag1 = RealmTag().apply {
            db = "resources"
            linkId = "res1"
            tagId = "tag1"
        }
        val linkTag2 = RealmTag().apply {
            db = "resources"
            linkId = "res2"
            tagId = "tag2"
        }
        val parentTag1 = RealmTag().apply { id = "tag1"; name = "Parent Tag 1" }
        val parentTag2 = RealmTag().apply { id = "tag2"; name = "Parent Tag 2" }

        val mockQuery = mockQueryResults(listOf(linkTag1, linkTag2), listOf(parentTag1, parentTag2))

        val result = repository.getTagsForResources(resourceIds)

        assertEquals(2, result.size)
        assertEquals(1, result["res1"]?.size)
        assertEquals("Parent Tag 1", result["res1"]?.get(0)?.name)
        assertEquals(1, result["res2"]?.size)
        assertEquals("Parent Tag 2", result["res2"]?.get(0)?.name)

        verify { mockQuery.equalTo("db", "resources") }
        verify { mockQuery.`in`("linkId", resourceIds.toTypedArray()) }
        verify { mockQuery.`in`("id", arrayOf("tag1", "tag2")) }
    }

    @Test
    fun `getTagsForResources returns empty map when linkIds is empty`() = runTest {
        val bulkResult = repository.getTagsForResources(emptyList())
        assertTrue(bulkResult.isEmpty())
    }

    @Test
    fun `getTagsForResources returns empty map when no links found`() = runTest {
        mockQueryResults(emptyList())
        val bulkResultEmptyLinks = repository.getTagsForResources(listOf("res1"))
        assertTrue(bulkResultEmptyLinks.isEmpty())
    }

    @Test
    fun `getTagsForResource returns empty list when no links found`() = runTest {
        mockQueryResults(emptyList())
        val singleResultEmptyLinks = repository.getTagsForResource("res1")
        assertTrue(singleResultEmptyLinks.isEmpty())
    }

    @Test
    fun `getTagsForResource returns empty list when tags have no tagIds`() = runTest {
        val linkWithoutTagId = RealmTag().apply {
            db = "resources"
            linkId = "res1"
            tagId = null
        }
        mockQueryResults(listOf(linkWithoutTagId))
        val singleResultEmptyTags = repository.getTagsForResource("res1")
        assertTrue(singleResultEmptyTags.isEmpty())
    }

    @Test
    fun `getTagsForResources returns empty map when tags have no tagIds`() = runTest {
        val linkWithoutTagId = RealmTag().apply {
            db = "resources"
            linkId = "res1"
            tagId = null
        }
        mockQueryResults(listOf(linkWithoutTagId))
        val bulkResultEmptyTags = repository.getTagsForResources(listOf("res1"))
        assertTrue(bulkResultEmptyTags.isEmpty())
    }
}
