import re

with open("app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt", "r") as f:
    content = f.read()

# Replace batchInsertMyLibrary
batch_insert_my_lib_search = """    override suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int {
        var processedCount = 0
        documents.forEach { doc ->
            try {
                val resourceId = JsonUtils.getString("_id", doc)
                val existing = myLibraryDao.getById(resourceId)
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        userId = shelfId,
                        existing = existing
                    )
                )
                if (library != null) {
                    myLibraryDao.upsert(library)
                    processedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return processedCount
    }"""

batch_insert_my_lib_replace = """    override suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int {
        var processedCount = 0

        val resourceIds = documents.mapNotNull { JsonUtils.getString("_id", it).takeIf { id -> id.isNotBlank() } }
        val existingItems = mutableMapOf<String, MyLibrary>()
        if (resourceIds.isNotEmpty()) {
            resourceIds.chunked(900).forEach { chunk ->
                existingItems.putAll(myLibraryDao.getByIds(chunk).associateBy { it.id })
            }
        }

        documents.forEach { doc ->
            try {
                val resourceId = JsonUtils.getString("_id", doc)
                val existing = existingItems[resourceId]
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        userId = shelfId,
                        existing = existing
                    )
                )
                if (library != null) {
                    existingItems[resourceId] = library
                    myLibraryDao.upsert(library)
                    processedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return processedCount
    }"""

content = content.replace(batch_insert_my_lib_search, batch_insert_my_lib_replace)

# Replace batchInsertResources
batch_insert_res_search = """    override suspend fun batchInsertResources(documents: List<JsonObject>): List<String> {
        val savedIds = mutableListOf<String>()
        documents.forEach { doc ->
            try {
                val _id = JsonUtils.getString("_id", doc)
                if (_id.startsWith("_design")) return@forEach
                val existing = myLibraryDao.getById(_id)
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        existing = existing
                    )
                )
                if (library != null) {
                    myLibraryDao.upsert(library)
                    savedIds.add(_id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return savedIds
    }"""

batch_insert_res_replace = """    override suspend fun batchInsertResources(documents: List<JsonObject>): List<String> {
        val savedIds = mutableListOf<String>()

        val validDocs = documents.filter {
            val _id = JsonUtils.getString("_id", it)
            _id.isNotBlank() && !_id.startsWith("_design")
        }
        val resourceIds = validDocs.map { JsonUtils.getString("_id", it) }
        val existingItems = mutableMapOf<String, MyLibrary>()
        if (resourceIds.isNotEmpty()) {
            resourceIds.chunked(900).forEach { chunk ->
                existingItems.putAll(myLibraryDao.getByIds(chunk).associateBy { it.id })
            }
        }

        validDocs.forEach { doc ->
            try {
                val _id = JsonUtils.getString("_id", doc)
                val existing = existingItems[_id]
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        existing = existing
                    )
                )
                if (library != null) {
                    existingItems[_id] = library
                    myLibraryDao.upsert(library)
                    savedIds.add(_id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return savedIds
    }"""

content = content.replace(batch_insert_res_search, batch_insert_res_replace)

with open("app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt", "w") as f:
    f.write(content)
