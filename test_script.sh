#!/bin/bash
# Move from ChatRepository.kt to VoicesRepository.kt
sed -i '/getPlanetNewsMessages/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt
sed -i '/insertNewsFromJson/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt
sed -i '/insertNewsList/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt
sed -i '/serializeNews/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt

cat << 'INNER_EOF' >> app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt
    suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews>
    suspend fun insertNewsFromJson(doc: com.google.gson.JsonObject)
    suspend fun insertNewsList(docs: List<com.google.gson.JsonObject>)
    fun serializeNews(news: RealmNews): com.google.gson.JsonObject
}
INNER_EOF
sed -i '/^}$/d' app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt
echo "}" >> app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt

# Check if methods are there
grep "getPlanetNewsMessages" app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt
