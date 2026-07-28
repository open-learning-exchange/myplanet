import re

with open('app/src/main/java/org/ole/planet/myplanet/services/TaskNotificationWorker.kt', 'r') as f:
    content = f.read()

content = content.replace('import org.ole.planet.myplanet.utils.NotificationUtils', 'import org.ole.planet.myplanet.utils.NotificationUtils\nimport org.ole.planet.myplanet.utils.TimeProvider')
content = content.replace('private val notificationsRepository: NotificationsRepository', 'private val notificationsRepository: NotificationsRepository,\n    private val timeProvider: TimeProvider')
content = content.replace('formatDate(task.deadline),', 'formatDate(task.deadline),\n                        timeProvider')

with open('app/src/main/java/org/ole/planet/myplanet/services/TaskNotificationWorker.kt', 'w') as f:
    f.write(content)
