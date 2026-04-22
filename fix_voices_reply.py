import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt'
with open(file_path, 'r') as file:
    content = file.read()

content = content.replace('userRepository = userRepository', 'userSyncHelper = userSyncHelper')
content = content.replace('adapter = VoicesAdapter(this, replyList, user, sharedPreferences, userRepository)', 'adapter = VoicesAdapter(this, replyList, user, sharedPreferences, userSyncHelper)')
content = content.replace('adapter = VoicesAdapter(\n            context = this@ReplyActivity,\n            currentUser = user,\n            parentNews = news,\n            teamName = "",\n            teamId = null,\n            msgList = list,\n            sharedPreferences = sharedPreferences,\n            shareNewsFn = ::shareNews,\n            getLibraryResourceFn = ::getLibraryResource,\n            launchCoroutine = ::launchCoroutine,\n            labelManager = labelManager,\n            voicesRepository = voicesRepository,\n            userRepository = userRepository\n        )', 'adapter = VoicesAdapter(\n            context = this@ReplyActivity,\n            currentUser = user,\n            parentNews = news,\n            teamName = "",\n            teamId = null,\n            msgList = list,\n            sharedPreferences = sharedPreferences,\n            shareNewsFn = ::shareNews,\n            getLibraryResourceFn = ::getLibraryResource,\n            launchCoroutine = ::launchCoroutine,\n            labelManager = labelManager,\n            voicesRepository = voicesRepository,\n            userSyncHelper = userSyncHelper\n        )')

with open(file_path, 'w') as file:
    file.write(content)
