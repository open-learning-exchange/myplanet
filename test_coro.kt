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
