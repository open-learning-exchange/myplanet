package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.realm.RealmList
import io.realm.RealmQuery
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

    @Before
    fun setup() {
        databaseService = mockk(relaxed = true)
        repository = spyk(TagsRepositoryImpl(databaseService), recordPrivateCalls = true)
    }

    @Test
    fun `getTags filters by dbType, non-empty name, and isAttached=false`() = runTest {
        val mockQuery = mockk<RealmQuery<RealmTag>>(relaxed = true)
        val mockResult = listOf(RealmTag().apply { name = "Tag1" })

        val builderSlot = slot<RealmQuery<RealmTag>.() -> Unit>()
        coEvery {
            repository["queryList"](RealmTag::class.java, false, capture(builderSlot)) as List<RealmTag>
        } answers {
            builderSlot.captured.invoke(mockQuery)
            mockResult
        }

        val result = repository.getTags("resources")

        assertEquals(mockResult, result)
        io.mockk.verify {
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

        coEvery {
            repository["queryList"](RealmTag::class.java, false, any<RealmQuery<RealmTag>.() -> Unit>()) as List<RealmTag>
        } returns listOf(child1, child2)

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
        val linkTag = RealmTag().apply {
            db = "resources"
            linkId = resourceId
            tagId = "tag1"
        }
        val parentTag = RealmTag().apply { id = "tag1"; name = "Parent Tag" }

        val builderSlot1 = slot<RealmQuery<RealmTag>.() -> Unit>()
        val builderSlot2 = slot<RealmQuery<RealmTag>.() -> Unit>()
        val mockQuery = mockk<RealmQuery<RealmTag>>(relaxed = true)

        coEvery {
            repository["queryList"](RealmTag::class.java, false, capture(builderSlot1)) as List<RealmTag>
        } answers {
            builderSlot1.captured.invoke(mockQuery)
            listOf(linkTag) // First call returns the link
        } andThenAnswer {
            builderSlot2.captured = builderSlot1.captured // save second call
            builderSlot2.captured.invoke(mockQuery)
            listOf(parentTag) // Second call returns the parent tag
        }

        val result = repository.getTagsForResource(resourceId)

        assertEquals(1, result.size)
        assertEquals("Parent Tag", result[0].name)
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

        val builderSlot1 = slot<RealmQuery<RealmTag>.() -> Unit>()
        val builderSlot2 = slot<RealmQuery<RealmTag>.() -> Unit>()
        val mockQuery = mockk<RealmQuery<RealmTag>>(relaxed = true)

        coEvery {
            repository["queryList"](RealmTag::class.java, false, capture(builderSlot1)) as List<RealmTag>
        } answers {
            builderSlot1.captured.invoke(mockQuery)
            listOf(linkTag1, linkTag2) // First call returns the links
        } andThenAnswer {
            builderSlot2.captured = builderSlot1.captured // save second call
            builderSlot2.captured.invoke(mockQuery)
            listOf(parentTag1, parentTag2) // Second call returns the parent tags
        }

        val result = repository.getTagsForResources(resourceIds)

        assertEquals(2, result.size)
        assertEquals(1, result["res1"]?.size)
        assertEquals("Parent Tag 1", result["res1"]?.get(0)?.name)
        assertEquals(1, result["res2"]?.size)
        assertEquals("Parent Tag 2", result["res2"]?.get(0)?.name)
    }

    @Test
    fun `edge cases empty linkIds returns emptyMap, empty tagIds returns emptyList`() = runTest {
        // empty linkIds for bulk
        val bulkResult = repository.getTagsForResources(emptyList())
        assertTrue(bulkResult.isEmpty())

        // empty links for bulk
        val mockQuery = mockk<RealmQuery<RealmTag>>(relaxed = true)
        val builderSlot = slot<RealmQuery<RealmTag>.() -> Unit>()
        coEvery {
            repository["queryList"](RealmTag::class.java, false, capture(builderSlot)) as List<RealmTag>
        } answers {
            builderSlot.captured.invoke(mockQuery)
            emptyList<RealmTag>()
        }
        val bulkResultEmptyLinks = repository.getTagsForResources(listOf("res1"))
        assertTrue(bulkResultEmptyLinks.isEmpty())

        // empty links for single resource
        coEvery {
            repository["queryList"](RealmTag::class.java, false, capture(builderSlot)) as List<RealmTag>
        } answers {
            builderSlot.captured.invoke(mockQuery)
            emptyList<RealmTag>()
        }
        val singleResultEmptyLinks = repository.getTagsForResource("res1")
        assertTrue(singleResultEmptyLinks.isEmpty())

        // Links exist but no tagIds
        val linkWithoutTagId = RealmTag().apply {
            db = "resources"
            linkId = "res1"
            tagId = null
        }
        coEvery {
            repository["queryList"](RealmTag::class.java, false, capture(builderSlot)) as List<RealmTag>
        } answers {
            builderSlot.captured.invoke(mockQuery)
            listOf(linkWithoutTagId)
        }
        val singleResultEmptyTags = repository.getTagsForResource("res1")
        assertTrue(singleResultEmptyTags.isEmpty())

        val bulkResultEmptyTags = repository.getTagsForResources(listOf("res1"))
        assertTrue(bulkResultEmptyTags.isEmpty())
    }
}
