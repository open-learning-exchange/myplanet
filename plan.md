1. **`SyncRepositoryImpl.kt`**:
   - Replace `gson.fromJson(gson.toJson(batch), JsonArray::class.java)` with `gson.toJsonTree(batch)`.
2. **`MyTeam.kt`**:
   - Replace `JsonParser.parseString(JsonUtils.gson.toJson(object)).asJsonObject` with `JsonUtils.gson.toJsonTree(object).asJsonObject` in two places.
3. **Pre-commit**:
   - Run compilation and tests to verify.
