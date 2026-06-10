import re

with open('./app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""                                emptyList<String>()""",
"""                                emptyList()"""
)

with open('./app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapterTest.kt', 'w') as f:
    f.write(content)
