with open("app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt", "r") as f:
    content = f.read()

search_1 = """                    } else {
                        currentList.map { notif ->
                            if (notif.id in markedIds && !notif.isRead) notif.copy(isRead = true) else notif
                        }
                    }"""

search_2 = """                        } else {
                            currentList.map {
                                if (it.id == notificationId) it.copy(isRead = true) else it
                            }
                        }"""

search_3 = """                    } else {
                        currentList.map {
                            if (it.id in markedIds && !it.isRead) it.copy(isRead = true) else it
                        }
                    }"""

search_4 = """    private fun buildGroupedList("""

print(f"search_1 found: {search_1 in content}")
print(f"search_2 found: {search_2 in content}")
print(f"search_3 found: {search_3 in content}")
print(f"search_4 found: {search_4 in content}")
