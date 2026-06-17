import re

with open('app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt', 'r') as f:
    content = f.read()

content = content.replace('import retrofit2.Response\n', '')
content = content.replace('override suspend fun sendNewChatRequest(\n        query: String,\n        user: String?,\n        aiProvider: AiProvider\n    ): Response<ChatResponse> {\n        val chatData = ChatRequest(data = ContentData(user ?: "", query, aiProvider), save = true)\n        val jsonContent = JsonUtils.gson.toJson(chatData)\n        val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())\n        return chatApiService.sendChatRequest(requestBody)\n    }', '''override suspend fun sendNewChatRequest(
        query: String,
        user: String?,
        aiProvider: AiProvider
    ): ChatResult {
        return try {
            val chatData = ChatRequest(data = ContentData(user ?: "", query, aiProvider), save = true)
            val jsonContent = JsonUtils.gson.toJson(chatData)
            val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
            val response = chatApiService.sendChatRequest(requestBody)
            val responseBody = response.body()
            if (response.isSuccessful && responseBody != null && responseBody.status == "Success") {
                val chatResponse = responseBody.chat ?: ""
                val id = responseBody.couchDBResponse?.id ?: ""
                val rev = responseBody.couchDBResponse?.rev ?: ""
                val jsonObject = JsonObject().apply {
                    addProperty("_rev", rev)
                    addProperty("_id", id)
                    addProperty("aiProvider", aiProvider.name)
                    addProperty("user", user)
                    addProperty("title", query)
                    addProperty("createdDate", java.util.Date().time)
                    addProperty("updatedDate", java.util.Date().time)
                    val conversationsArray = JsonArray()
                    val conversationObject = JsonObject().apply {
                        addProperty("query", query)
                        addProperty("response", chatResponse)
                    }
                    conversationsArray.add(conversationObject)
                    add("conversations", conversationsArray)
                }
                saveNewChat(jsonObject)
                ChatResult.Success(chatResponse, id, rev)
            } else {
                ChatResult.Error(responseBody?.message ?: response.message() ?: "Request failed")
            }
        } catch (e: Exception) {
            ChatResult.Error(e.message ?: "Request failed")
        }
    }''')

content = content.replace('override suspend fun sendContinueChatRequest(\n        message: String,\n        user: String?,\n        aiProvider: AiProvider,\n        id: String,\n        rev: String\n    ): Response<ChatResponse> {\n        val continueChatData = ContinueChatRequest(data = Data(user ?: "", message, aiProvider, id, rev), save = true)\n        val jsonContent = JsonUtils.gson.toJson(continueChatData)\n        val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())\n        return chatApiService.sendChatRequest(requestBody)\n    }', '''override suspend fun sendContinueChatRequest(
        message: String,
        user: String?,
        aiProvider: AiProvider,
        id: String,
        rev: String
    ): ChatResult {
        return try {
            val continueChatData = ContinueChatRequest(data = Data(user ?: "", message, aiProvider, id, rev), save = true)
            val jsonContent = JsonUtils.gson.toJson(continueChatData)
            val requestBody = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
            val response = chatApiService.sendChatRequest(requestBody)
            val responseBody = response.body()
            if (response.isSuccessful && responseBody != null && responseBody.status == "Success") {
                val chatResponse = responseBody.chat ?: ""
                val newRev = responseBody.couchDBResponse?.rev ?: rev
                continueConversation(id, message, chatResponse, newRev)
                ChatResult.Success(chatResponse, id, newRev)
            } else {
                continueConversation(id, message, "", rev)
                ChatResult.Error(responseBody?.message ?: response.message() ?: "Request failed")
            }
        } catch (e: Exception) {
            continueConversation(id, message, "", rev)
            ChatResult.Error(e.message ?: "Request failed")
        }
    }''')

content = content.replace('override suspend fun saveNewChat(chat: JsonObject) {\n        executeTransaction { realm ->\n            insertChatsBatchInternal(realm, listOf(chat))\n        }\n    }', '''private suspend fun saveNewChat(chat: JsonObject) {
        try {
            executeTransaction { realm ->
                insertChatsBatchInternal(realm, listOf(chat))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }''')

content = content.replace('override suspend fun continueConversation(id: String, query: String, response: String, rev: String) {\n        executeTransaction { realm ->\n            addConversation(realm, id, query, response, rev)\n        }\n    }', '''private suspend fun continueConversation(id: String, query: String, response: String, rev: String) {
        try {
            executeTransaction { realm ->
                addConversation(realm, id, query, response, rev)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }''')

with open('app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt', 'w') as f:
    f.write(content)
