1. **Add lazily cached SharedPrefManager in `NetworkUtils`**: Add a `private val sharedPrefManager: SharedPrefManager by lazy { ... }` in `NetworkUtils` mirroring how `coroutineScope` and `connectivityManager` are cached.
2. **Update `getCustomDeviceName` in `NetworkUtils`**: Modify `getCustomDeviceName` to use the cached `sharedPrefManager` instead of resolving it dynamically per method call. Leave the `context` parameter as-is for signature compatibility.
3. **Run network utils unit tests**: Run `./gradlew app:testDefaultDebugUnitTest --tests "*NetworkUtils*Test*"` to ensure we didn't break anything.
4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done**.
5. **Submit changes**.
