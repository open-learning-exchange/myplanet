cat << 'INNER' > test_coro.kt
import kotlinx.coroutines.*

fun main() {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    try {
        scope.launch {
            throw RuntimeException("My Simulated Error")
        }
    } catch(e: Exception) {
        println("Caught: ${e.message}")
    }
}
INNER
kotlinc -cp ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.3/2b09627576f0989a436a00a4a54b55fa5026fb86/kotlinx-coroutines-core-jvm-1.7.3.jar test_coro.kt -include-runtime -d test_coro.jar || true
java -cp test_coro.jar:~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.3/2b09627576f0989a436a00a4a54b55fa5026fb86/kotlinx-coroutines-core-jvm-1.7.3.jar Test_coroKt || true
