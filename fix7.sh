#!/bin/bash
sed -i 's/service.onStartCommand(intent, 0, 1)/try { service.onStartCommand(intent, 0, 1) } catch (e: Exception) { println("Caught by try-catch") }/g' app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.DownloadServiceTest"
