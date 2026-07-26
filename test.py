import re

content = open('app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt').read()

patch_1 = """<<<<<<< SEARCH
                    } else {
                        currentList.map { notif ->
                            if (notif.id in markedIds && !notif.isRead) notif.copy(isRead = true) else notif
                        }
                    }
=======
                    } else {
                        currentList.markAsRead(markedIds)
                    }
>>>>>>> REPLACE"""

patch_2 = """<<<<<<< SEARCH
                        } else {
                            currentList.map {
                                if (it.id == notificationId) it.copy(isRead = true) else it
                            }
                        }
=======
                        } else {
                            currentList.markAsRead(notificationId)
                        }
>>>>>>> REPLACE"""

patch_3 = """<<<<<<< SEARCH
                    } else {
                        currentList.map {
                            if (it.id in markedIds && !it.isRead) it.copy(isRead = true) else it
                        }
                    }
=======
                    } else {
                        currentList.markAsRead(markedIds)
                    }
>>>>>>> REPLACE"""

patch_4 = """<<<<<<< SEARCH
    private fun buildGroupedList(
=======
    private fun List<Notification>.markAsRead(id: String): List<Notification> {
        return map { if (it.id == id && !it.isRead) it.copy(isRead = true) else it }
    }

    private fun List<Notification>.markAsRead(ids: Set<String>): List<Notification> {
        return map { if (it.id in ids && !it.isRead) it.copy(isRead = true) else it }
    }

    private fun buildGroupedList(
>>>>>>> REPLACE"""

print("Testing patches...")
print(patch_1 in content)
