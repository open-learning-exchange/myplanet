import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.ZoneId

fun main() {
    val date = 1600000000000L
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy", Locale.US).withZone(ZoneId.of("UTC"))
    println(formatter.format(Instant.ofEpochMilli(date)))
}
