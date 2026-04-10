
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

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
