with open("app/src/main/java/org/ole/planet/myplanet/model/News.kt", "r") as f:
    content = f.read()

import re

search_block = """    @Ignore
    var rawViewIn: String? = null
    @Ignore
    var rawConversations: String? = null
    @Ignore
    var rawImageUrls: List<String>? = null"""

replace_block = """    @Ignore
    var rawViewIn: String? = null
    @Ignore
    var rawConversations: String? = null
    @Ignore
    var rawImageUrls: List<String>? = null
    @Ignore
    var rawImages: String? = null
    @Ignore
    var parsedImagesArray: JsonArray? = null
    @Ignore
    var parsedSharedTeamName: String? = null"""

content = content.replace(search_block, replace_block)

with open("app/src/main/java/org/ole/planet/myplanet/model/News.kt", "w") as f:
    f.write(content)
