package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.model.TagEntity

@OptIn(ExperimentalCoroutinesApi::class)
class TagsRepositoryImplTest {

    private lateinit var tagDao: TagDao
    private lateinit var repository: TagsRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        tagDao = mockk(relaxed = true)
        repository = TagsRepositoryImpl(tagDao)
    }

    @Test
    fun `getTags delegates to getParentTags`() = runTest {
        val mockResult = listOf(TagEntity().apply { name = "Tag1" })
        coEvery { tagDao.getParentTags("resources") } returns mockResult

        val result = repository.getTags("resources")

        assertEquals(mockResult, result)
    }

    @Test
    fun `getTagsWithChildren correctly maps children to parent tags`() = runTest {
        val parentTag1 = TagEntity().apply {
            id = "parent1"
            name = "Parent 1"
        }
        val parentTag2 = TagEntity().apply {
            id = "parent2"
            name = "Parent 2"
        }
        val childlessParentTag = TagEntity().apply {
            id = "parent3"
            name = "Parent 3"
        }
        val child1 = TagEntity().apply {
            name = "Child1"
            attachedTo = listOf("parent1")
        }
        val child2 = TagEntity().apply {
            name = "Child2"
            attachedTo = listOf("parent1", "parent2")
        }
        val unattachedChild = TagEntity().apply {
            name = "UnattachedChild"
        }

        coEvery { tagDao.getParentTags("resources") } returns
            listOf(parentTag1, parentTag2, childlessParentTag)
        coEvery { tagDao.getAll() } returns
            listOf(parentTag1, parentTag2, childlessParentTag, child1, child2, unattachedChild)

        val result = repository.getTagsWithChildren("resources")

        assertEquals(3, result.size)
        assertTrue(result.containsKey(parentTag1))
        assertTrue(result.containsKey(parentTag2))
        assertTrue(result.containsKey(childlessParentTag))

        val childrenOfParent1 = result[parentTag1]
        assertEquals(2, childrenOfParent1?.size)
        assertTrue(childrenOfParent1!!.contains(child1))
        assertTrue(childrenOfParent1.contains(child2))

        val childrenOfParent2 = result[parentTag2]
        assertEquals(1, childrenOfParent2?.size)
        assertTrue(childrenOfParent2!!.contains(child2))

        val childrenOfChildlessParent = result[childlessParentTag]
        assertEquals(0, childrenOfChildlessParent?.size)
    }

    @Test
    fun `getTagsForResource resolves linked tags through tagId lookup`() = runTest {
        val resourceId = "res1"
        val tagId = "tag1"
        val linkTag = TagEntity().apply {
            db = "resources"
            linkId = resourceId
            this.tagId = tagId
        }
        val parentTag = TagEntity().apply { id = tagId; name = "Parent Tag" }

        coEvery { tagDao.getByDbAndLinkId("resources", resourceId) } returns listOf(linkTag)
        coEvery { tagDao.getByIds(listOf(tagId)) } returns listOf(parentTag)

        val result = repository.getTagsForResource(resourceId)

        assertEquals(1, result.size)
        assertEquals("Parent Tag", result[0].name)
    }

    @Test
    fun `getTagsForResources returns correct per-resource tag map`() = runTest {
        val resourceIds = listOf("res1", "res2")
        val linkTag1 = TagEntity().apply {
            db = "resources"
            linkId = "res1"
            tagId = "tag1"
        }
        val linkTag2 = TagEntity().apply {
            db = "resources"
            linkId = "res2"
            tagId = "tag2"
        }
        val parentTag1 = TagEntity().apply { id = "tag1"; name = "Parent Tag 1" }
        val parentTag2 = TagEntity().apply { id = "tag2"; name = "Parent Tag 2" }

        coEvery { tagDao.getByDbAndLinkIds("resources", resourceIds) } returns
            listOf(linkTag1, linkTag2)
        coEvery { tagDao.getByIds(listOf("tag1", "tag2")) } returns
            listOf(parentTag1, parentTag2)

        val result = repository.getTagsForResources(resourceIds)

        assertEquals(2, result.size)
        assertEquals(1, result["res1"]?.size)
        assertEquals("Parent Tag 1", result["res1"]?.get(0)?.name)
        assertEquals(1, result["res2"]?.size)
        assertEquals("Parent Tag 2", result["res2"]?.get(0)?.name)
    }

    @Test
    fun `getTagsForResources returns empty map when linkIds is empty`() = runTest {
        val bulkResult = repository.getTagsForResources(emptyList())
        assertTrue(bulkResult.isEmpty())
    }

    @Test
    fun `getTagsForResources returns empty map when no links found`() = runTest {
        coEvery { tagDao.getByDbAndLinkIds(any(), any()) } returns emptyList()
        val bulkResultEmptyLinks = repository.getTagsForResources(listOf("res1"))
        assertTrue(bulkResultEmptyLinks.isEmpty())
    }

    @Test
    fun `getTagsForResource returns empty list when no links found`() = runTest {
        coEvery { tagDao.getByDbAndLinkId(any(), any()) } returns emptyList()
        val singleResultEmptyLinks = repository.getTagsForResource("res1")
        assertTrue(singleResultEmptyLinks.isEmpty())
    }

    @Test
    fun `getTagsForResource returns empty list when tags have no tagIds`() = runTest {
        val linkWithoutTagId = TagEntity().apply {
            db = "resources"
            linkId = "res1"
            tagId = null
        }
        coEvery { tagDao.getByDbAndLinkId("resources", "res1") } returns listOf(linkWithoutTagId)
        val singleResultEmptyTags = repository.getTagsForResource("res1")
        assertTrue(singleResultEmptyTags.isEmpty())
    }

    @Test
    fun `getTagsForResources returns empty map when tags have no tagIds`() = runTest {
        val linkWithoutTagId = TagEntity().apply {
            db = "resources"
            linkId = "res1"
            tagId = null
        }
        coEvery { tagDao.getByDbAndLinkIds("resources", listOf("res1")) } returns
            listOf(linkWithoutTagId)
        val bulkResultEmptyTags = repository.getTagsForResources(listOf("res1"))
        assertTrue(bulkResultEmptyTags.isEmpty())
    }

    @Test
    fun `insert parses json objects and upserts tags`() = runTest {
        val documentList = listOf(
            com.google.gson.JsonObject().apply {
                addProperty("_id", "tag_1")
                addProperty("name", "Tag One")
                val attachedToArray = com.google.gson.JsonArray().apply {
                    add("parent1")
                    add("parent2")
                }
                add("attachedTo", attachedToArray)
            },
            com.google.gson.JsonObject().apply {
                addProperty("_id", "tag_2")
                addProperty("name", "Tag Two")
                addProperty("attachedTo", "parent3")
            }
        )

        repository.insert(documentList)

        coVerify {
            tagDao.upsertAll(match { list ->
                if (list.size != 2) return@match false
                val tag1 = list.find { it.id == "tag_1" }
                val tag2 = list.find { it.id == "tag_2" }

                tag1?.name == "Tag One" &&
                    tag1.attachedTo?.size == 2 &&
                    tag1.attachedTo?.contains("parent1") == true &&
                    tag1.attachedTo?.contains("parent2") == true &&
                    tag1.isAttached &&
                    tag2?.name == "Tag Two" &&
                    tag2.attachedTo?.size == 1 &&
                    tag2.attachedTo?.contains("parent3") == true &&
                    tag2.isAttached
            })
        }
    }

    @Test
    fun `insert ignores design docs`() = runTest {
        val documentList = listOf(
            com.google.gson.JsonObject().apply {
                addProperty("_id", "tag_123")
                addProperty("name", "Test Tag")
            },
            com.google.gson.JsonObject().apply {
                addProperty("_id", "_design/tags")
            }
        )

        repository.insert(documentList)

        coVerify {
            tagDao.upsertAll(match { list ->
                list.size == 1 && list.first().id == "tag_123" && list.first().name == "Test Tag"
            })
        }
    }

    @Test
    fun `insert with empty list does nothing`() = runTest {
        repository.insert(emptyList())

        coVerify(exactly = 0) { tagDao.upsertAll(any()) }
    }
}
