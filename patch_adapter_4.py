with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "r") as f:
    content = f.read()

# Add userIdPositions map and rebuild logic
search_fields = """    private val userCache = object : LinkedHashMap<String, UserEntity?>(64, 0.75f, true) { override fun removeEldestEntry(e: Map.Entry<String, UserEntity?>) = size > 128 }
    private val fetchingUserIds = mutableSetOf<String>()
    private val replyCountCache = mutableMapOf<String, Int>()"""

replace_fields = """    private val userCache = object : LinkedHashMap<String, UserEntity?>(64, 0.75f, true) { override fun removeEldestEntry(e: Map.Entry<String, UserEntity?>) = size > 128 }
    private val fetchingUserIds = mutableSetOf<String>()
    private val replyCountCache = mutableMapOf<String, Int>()
    private val userIdPositions = mutableMapOf<String, MutableList<Int>>()"""

content = content.replace(search_fields, replace_fields)

# onCurrentListChanged
search_override = """    override fun submitList(list: List<News>?, commitCallback: Runnable?) {
        super.submitList(prepareSubmitList(list), commitCallback)
    }

    private val externalFilesDir = """

replace_override = """    override fun submitList(list: List<News>?, commitCallback: Runnable?) {
        super.submitList(prepareSubmitList(list), commitCallback)
    }

    override fun onCurrentListChanged(previousList: List<News>, currentList: List<News>) {
        super.onCurrentListChanged(previousList, currentList)
        userIdPositions.clear()
        currentList.forEachIndexed { index, news ->
            val uId = news.userId
            if (!uId.isNullOrEmpty()) {
                userIdPositions.getOrPut(uId) { mutableListOf() }.add(index)
            }
        }
    }

    private val externalFilesDir = """

content = content.replace(search_override, replace_override)

# configureUser update loop
search_configure = """            if (!fetchingUserIds.contains(userId)) {
                fetchingUserIds.add(userId)
                getUserFn(userId) { userModel ->
                    userCache[userId] = userModel
                    fetchingUserIds.remove(userId)
                    currentList.forEachIndexed { index, item ->
                        if (item.userId == userId) {
                            safeNotifyItemChanged(index, PAYLOAD_USER_FETCHED)
                        }
                    }
                }
            }"""

replace_configure = """            if (!fetchingUserIds.contains(userId)) {
                fetchingUserIds.add(userId)
                getUserFn(userId) { userModel ->
                    userCache[userId] = userModel
                    fetchingUserIds.remove(userId)
                    userIdPositions[userId]?.forEach { index ->
                        safeNotifyItemChanged(index, PAYLOAD_USER_FETCHED)
                    }
                }
            }"""

content = content.replace(search_configure, replace_configure)

with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "w") as f:
    f.write(content)
