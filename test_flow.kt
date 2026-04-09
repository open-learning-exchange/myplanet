import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.*

fun main() = runBlocking {
    val f = callbackFlow {
        for (i in 1..5) {
            val res = trySend(i)
            println("Sent $i, result: $res")
        }
        close()
        awaitClose()
    }.conflate().map {
        println("Mapping $it")
        delay(100)
        it * 2
    }

    f.collect {
        println("Collected $it")
    }
}
