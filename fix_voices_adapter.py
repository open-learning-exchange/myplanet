import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Replace userRepository with userSyncHelper in ReplyActivity and VoicesAdapter where needed.
# VoicesAdapter takes it in constructor.
# class VoicesAdapter(
#    private val context: Context,
#    private val msgList: List<RealmNews>,
#    private val currentUser: RealmUser,
#    private val sharedPreferences: SharedPreferences,
#    private val userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper
# )
# ReplyActivity initializes VoicesAdapter.

content = content.replace(
    'lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository',
    'lateinit var userSyncHelper: org.ole.planet.myplanet.repository.UserSyncHelper'
)
content = content.replace(
    'adapter = VoicesAdapter(this, replyList, user, sharedPreferences, userRepository)',
    'adapter = VoicesAdapter(this, replyList, user, sharedPreferences, userSyncHelper)'
)

with open(file_path, 'w') as file:
    file.write(content)
