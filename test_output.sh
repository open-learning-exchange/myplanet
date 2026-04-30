#!/bin/bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.retry.RetryQueueWorkerTest.triggerImmediateRetry_enqueuesOneTimeWork" --no-build-cache --offline
