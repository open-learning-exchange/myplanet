import kotlin.system.measureNanoTime

fun oldWay(isBluetoothEnabled: Boolean, isWifiEnabled: Boolean): String {
    var message = ""
    if (isBluetoothEnabled) message += "Bluetooth"
    if (isWifiEnabled) {
        if (message.isNotEmpty()) message += " and "
            message += "Wi-Fi"
    }
    if (message.isNotEmpty()) {
    message += " is on please turn off to save battery"
    } else {
    message = " is on please turn off to save battery"
    }
    return message
}

fun newWay(isBluetoothEnabled: Boolean, isWifiEnabled: Boolean): String {
    val message = StringBuilder()
    if (isBluetoothEnabled) message.append("Bluetooth")
    if (isWifiEnabled) {
        if (message.isNotEmpty()) message.append(" and ")
        message.append("Wi-Fi")
    }
    if (message.isNotEmpty()) {
        message.append(" is on please turn off to save battery")
    } else {
        message.append(" is on please turn off to save battery")
    }
    return message.toString()
}

fun main() {
    val iterations = 100000

    // Warm up
    for (i in 0..10000) {
        oldWay(true, true)
        newWay(true, true)
    }

    var oldTime = 0L
    for (i in 0..iterations) {
        oldTime += measureNanoTime { oldWay(true, true) }
    }

    var newTime = 0L
    for (i in 0..iterations) {
        newTime += measureNanoTime { newWay(true, true) }
    }

    println("Old time: $oldTime ns")
    println("New time: $newTime ns")
    println("Improvement: ${((oldTime - newTime).toDouble() / oldTime) * 100}%")
}
