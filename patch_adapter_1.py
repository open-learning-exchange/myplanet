with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "r") as f:
    content = f.read()

# Update preParseNews
search_preparse = """                val currentImageUrls = it.imageUrls?.toList()
                if (it.rawImageUrls != currentImageUrls) {
                    if (!currentImageUrls.isNullOrEmpty()) {
                        val parsed = parseImageUrls(currentImageUrls)
                        if (parsed != null) {
                            it.parsedImageUrls = parsed
                            it.rawImageUrls = currentImageUrls
                        }
                    } else {
                        it.parsedImageUrls = null
                        it.rawImageUrls = null
                    }
                }
            } catch (e: IllegalStateException) {"""

replace_preparse = """                val currentImageUrls = it.imageUrls?.toList()
                if (it.rawImageUrls != currentImageUrls) {
                    if (!currentImageUrls.isNullOrEmpty()) {
                        val parsed = parseImageUrls(currentImageUrls)
                        if (parsed != null) {
                            it.parsedImageUrls = parsed
                            it.rawImageUrls = currentImageUrls
                        }
                    } else {
                        it.parsedImageUrls = null
                        it.rawImageUrls = null
                    }
                }
                if (it.rawImages != it.images) {
                    it.parsedImagesArray = it.imagesArray
                    it.rawImages = it.images
                }

                it.parsedSharedTeamName = JsonUtils.extractSharedTeamName(it)
            } catch (e: IllegalStateException) {"""

content = content.replace(search_preparse, replace_preparse)

with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "w") as f:
    f.write(content)
