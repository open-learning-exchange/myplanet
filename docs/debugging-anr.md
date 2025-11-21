# Debugging ANRs

This document provides instructions on how to debug Application Not Responding (ANR) errors in the myPlanet Android application.

## Capturing ANR Traces

When an ANR occurs, the Android system generates a traces file that contains stack traces of all threads running in the application. This file is crucial for identifying the cause of the ANR.

To capture the traces file, you will need to use the Android Debug Bridge (adb).

1.  Connect your device to your computer and ensure that USB debugging is enabled.
2.  Reproduce the ANR.
3.  Once the ANR occurs, run the following command to pull the traces file from the device:

    ```bash
    adb pull /data/anr/traces.txt
    ```

4.  The `traces.txt` file will be saved to your current directory.

## Interpreting Watchdog Logs

The application uses a custom `ANRWatchdog` to detect ANRs. When the watchdog detects an ANR, it logs a message to Logcat with the tag "ANR". The message includes the stack trace of the main thread, which is the thread that is most likely causing the ANR.

To view the watchdog logs, you can use the following `adb` command:

```bash
adb logcat | grep "ANR"
```

The output will show the ANR message, including the stack trace of the main thread. This information can be used to identify the code that is causing the ANR.
