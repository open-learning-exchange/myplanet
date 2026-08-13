with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "r") as f:
    content = f.read()

# Update onBindViewHolder sharedTeamName
search_bind_1 = """    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VoicesViewHolder) {
            holder.bind(position)
            val news = getNews(holder, position)

            run {
                val sharedTeamName = JsonUtils.extractSharedTeamName(news)"""

replace_bind_1 = """    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VoicesViewHolder) {
            holder.bind(position)
            val news = getNews(holder, position)

            run {
                val sharedTeamName = news.parsedSharedTeamName ?: \"\""""

content = content.replace(search_bind_1, replace_bind_1)

# Update onBindViewHolder PAYLOAD_EDIT_ACTION sharedTeamName
search_bind_2 = """                    PAYLOAD_EDIT_ACTION -> {
                        val sharedTeamName = JsonUtils.extractSharedTeamName(news)
                        setMessageAndDate(holder, news, sharedTeamName)
                        configureEditDeleteButtons(holder, news)"""

replace_bind_2 = """                    PAYLOAD_EDIT_ACTION -> {
                        val sharedTeamName = news.parsedSharedTeamName ?: \"\"
                        setMessageAndDate(holder, news, sharedTeamName)
                        configureEditDeleteButtons(holder, news)"""

content = content.replace(search_bind_2, replace_bind_2)

# Update loadImage
search_load_image = """        news?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                if (imagesArray.size() == 1) {
                    val ob = imagesArray[0]?.asJsonObject
                    val resourceId = JsonUtils.getString("resourceId", ob)
                    loadLibraryImage(binding, resourceId)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (i in 0 until imagesArray.size()) {
                        val ob = imagesArray[i]?.asJsonObject
                        val resourceId = JsonUtils.getString("resourceId", ob)
                        addLibraryImageToContainer(binding, resourceId)
                    }
                }
            }
        }"""

replace_load_image = """        news?.parsedImagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                if (imagesArray.size() == 1) {
                    val ob = imagesArray[0]?.asJsonObject
                    val resourceId = JsonUtils.getString("resourceId", ob)
                    loadLibraryImage(binding, resourceId)
                } else {
                    binding.llNewsImages.visibility = View.VISIBLE
                    for (i in 0 until imagesArray.size()) {
                        val ob = imagesArray[i]?.asJsonObject
                        val resourceId = JsonUtils.getString("resourceId", ob)
                        addLibraryImageToContainer(binding, resourceId)
                    }
                }
            }
        }"""

content = content.replace(search_load_image, replace_load_image)

with open("app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt", "w") as f:
    f.write(content)
