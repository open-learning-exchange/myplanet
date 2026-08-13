with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "r") as f:
    content = f.read()

# getChangePayload changes
search_payload = """        getChangePayload = { oldItem, newItem ->
            val payloads = mutableListOf<String>()

            if (oldItem.labels?.toList() != newItem.labels?.toList()) {
                payloads.add(PAYLOAD_TEAM_LEADER_CHANGED)
            }
            if (oldItem.userId != newItem.userId || oldItem.userName != newItem.userName || oldItem.avatar != newItem.avatar || oldItem.imageUrls?.toList() != newItem.imageUrls?.toList() || oldItem.images != newItem.images || oldItem.parsedImageUrls != newItem.parsedImageUrls) {
                payloads.add(PAYLOAD_USER_FETCHED)
            }
            if (oldItem.message != newItem.message || oldItem.isEdited != newItem.isEdited || oldItem.time != newItem.time || oldItem.sharedBy != newItem.sharedBy || oldItem.replyTo != newItem.replyTo) {
                payloads.add(PAYLOAD_EDIT_ACTION)
            }

            // Every field checked in areContentsTheSame is covered by the buckets above.
            // If payloads is empty here, it means a future field was added to areContentsTheSame
            // without a corresponding bucket. We MUST return null to trigger a full rebind to prevent stale UI.
            if (payloads.isNotEmpty()) payloads else null
        }
    )
) {
    companion object {
        const val PAYLOAD_TEAM_LEADER_CHANGED = "PAYLOAD_TEAM_LEADER_CHANGED"
        const val PAYLOAD_CURRENT_USER_CHANGED = "PAYLOAD_CURRENT_USER_CHANGED"
        const val PAYLOAD_NON_TEAM_MEMBER_CHANGED = "PAYLOAD_NON_TEAM_MEMBER_CHANGED"
        const val PAYLOAD_REPLY_COUNT = "PAYLOAD_REPLY_COUNT"
        const val PAYLOAD_USER_FETCHED = "PAYLOAD_USER_FETCHED"
        const val PAYLOAD_EDIT_ACTION = "PAYLOAD_EDIT_ACTION"
    }"""

replace_payload = """        getChangePayload = { oldItem, newItem ->
            val payloads = mutableListOf<String>()

            if (oldItem.labels?.toList() != newItem.labels?.toList()) {
                payloads.add(PAYLOAD_LABELS_CHANGED)
            }
            if (oldItem.imageUrls?.toList() != newItem.imageUrls?.toList() || oldItem.images != newItem.images || oldItem.parsedImageUrls != newItem.parsedImageUrls) {
                payloads.add(PAYLOAD_IMAGES_CHANGED)
            }
            if (oldItem.userId != newItem.userId || oldItem.userName != newItem.userName || oldItem.avatar != newItem.avatar) {
                payloads.add(PAYLOAD_USER_FETCHED)
            }
            if (oldItem.message != newItem.message || oldItem.isEdited != newItem.isEdited || oldItem.time != newItem.time || oldItem.sharedBy != newItem.sharedBy || oldItem.replyTo != newItem.replyTo) {
                payloads.add(PAYLOAD_EDIT_ACTION)
            }

            // Every field checked in areContentsTheSame is covered by the buckets above.
            // If payloads is empty here, it means a future field was added to areContentsTheSame
            // without a corresponding bucket. We MUST return null to trigger a full rebind to prevent stale UI.
            if (payloads.isNotEmpty()) payloads else null
        }
    )
) {
    companion object {
        const val PAYLOAD_TEAM_LEADER_CHANGED = "PAYLOAD_TEAM_LEADER_CHANGED"
        const val PAYLOAD_CURRENT_USER_CHANGED = "PAYLOAD_CURRENT_USER_CHANGED"
        const val PAYLOAD_NON_TEAM_MEMBER_CHANGED = "PAYLOAD_NON_TEAM_MEMBER_CHANGED"
        const val PAYLOAD_REPLY_COUNT = "PAYLOAD_REPLY_COUNT"
        const val PAYLOAD_USER_FETCHED = "PAYLOAD_USER_FETCHED"
        const val PAYLOAD_EDIT_ACTION = "PAYLOAD_EDIT_ACTION"
        const val PAYLOAD_LABELS_CHANGED = "PAYLOAD_LABELS_CHANGED"
        const val PAYLOAD_IMAGES_CHANGED = "PAYLOAD_IMAGES_CHANGED"
    }"""

content = content.replace(search_payload, replace_payload)

# onBindViewHolder changes
search_bind = """            for (payload in flattenedPayloads) {
                when (payload) {
                    PAYLOAD_TEAM_LEADER_CHANGED -> {
                        configureEditDeleteButtons(holder, news)
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                    }
                    PAYLOAD_CURRENT_USER_CHANGED -> {
                        val userModel = configureUser(holder, news)
                        configureEditDeleteButtons(holder, news)
                        showShareButton(holder, news)
                        showReplyButton(holder, news, position)
                        updateReplyCount(holder, news, position)
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                        val currentLeader = getCurrentLeader(userModel, news)
                        setMemberClickListeners(holder, userModel, currentLeader)
                    }
                    PAYLOAD_NON_TEAM_MEMBER_CHANGED -> {
                        showReplyButton(holder, news, position)
                        showShareButton(holder, news)
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                    }
                    PAYLOAD_REPLY_COUNT -> updateReplyCount(holder, news, position)
                    PAYLOAD_USER_FETCHED -> {
                        val userModel = configureUser(holder, news)
                        val currentLeader = getCurrentLeader(userModel, news)
                        setMemberClickListeners(holder, userModel, currentLeader)
                        loadImage(holder.binding, news)
                        configureEditDeleteButtons(holder, news)
                    }
                    PAYLOAD_EDIT_ACTION -> {
                        val sharedTeamName = news.parsedSharedTeamName ?: ""
                        setMessageAndDate(holder, news, sharedTeamName)
                        configureEditDeleteButtons(holder, news)
                        showReplyButton(holder, news, position)
                        handleChat(holder, news)
                        loadImage(holder.binding, news)
                    }
                }
            }"""

replace_bind = """            for (payload in flattenedPayloads) {
                when (payload) {
                    PAYLOAD_TEAM_LEADER_CHANGED -> {
                        configureEditDeleteButtons(holder, news)
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                    }
                    PAYLOAD_LABELS_CHANGED -> {
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                    }
                    PAYLOAD_IMAGES_CHANGED -> {
                        loadImage(holder.binding, news)
                    }
                    PAYLOAD_CURRENT_USER_CHANGED -> {
                        val userModel = configureUser(holder, news)
                        configureEditDeleteButtons(holder, news)
                        showShareButton(holder, news)
                        showReplyButton(holder, news, position)
                        updateReplyCount(holder, news, position)
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                        val currentLeader = getCurrentLeader(userModel, news)
                        setMemberClickListeners(holder, userModel, currentLeader)
                    }
                    PAYLOAD_NON_TEAM_MEMBER_CHANGED -> {
                        showReplyButton(holder, news, position)
                        showShareButton(holder, news)
                        val canManageLabels = canAddLabel(news)
                        labelManager.setupAddLabelMenu(holder.binding, news, canManageLabels)
                        labelManager.showChips(holder.binding, news, canManageLabels)
                    }
                    PAYLOAD_REPLY_COUNT -> updateReplyCount(holder, news, position)
                    PAYLOAD_USER_FETCHED -> {
                        val userModel = configureUser(holder, news)
                        val currentLeader = getCurrentLeader(userModel, news)
                        setMemberClickListeners(holder, userModel, currentLeader)
                        configureEditDeleteButtons(holder, news)
                    }
                    PAYLOAD_EDIT_ACTION -> {
                        val sharedTeamName = news.parsedSharedTeamName ?: ""
                        setMessageAndDate(holder, news, sharedTeamName)
                        configureEditDeleteButtons(holder, news)
                        showReplyButton(holder, news, position)
                        if (news.chat) {
                            handleChat(holder, news)
                        }
                    }
                }
            }"""

content = content.replace(search_bind, replace_bind)

with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "w") as f:
    f.write(content)
