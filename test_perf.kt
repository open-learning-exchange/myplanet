import kotlinx.coroutines.*
import java.io.File
import kotlin.system.measureTimeMillis

fun fullyReadFileToBytes(file: File): ByteArray {
    return file.readBytes()
}

fun getMimeType(file: File): String {
    val connection = file.toURI().toURL().openConnection()
    return connection.contentType ?: "application/octet-stream"
}

fun main() = runBlocking {
    val file = File("test_dummy.txt")
    file.writeText("A".repeat(10 * 1024 * 1024)) // 10MB file

    val timeWithoutIOContext = measureTimeMillis {
        val mimeType = getMimeType(file)
        val body = fullyReadFileToBytes(file)
    }

    val timeWithIOContext = measureTimeMillis {
        withContext(Dispatchers.IO) {
            val mimeType = getMimeType(file)
            val body = fullyReadFileToBytes(file)
        }
    }

    println("Time without IO context: $timeWithoutIOContext ms")
    println("Time with IO context: $timeWithIOContext ms")

    file.delete()
}
