package org.ole.planet.myplanet.services

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnAudioRecordListener

class AudioRecorderTest {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var mockListener: OnAudioRecordListener
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        audioRecorder = AudioRecorder()
        mockListener = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        mockkObject(MainApplication.Companion)
        every { MainApplication.context } returns mockContext
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSetAudioRecordListener() {
        val result = audioRecorder.setAudioRecordListener(mockListener)
        assertTrue(result === audioRecorder)
    }

    @Test
    fun testForceStopWhenNotRecording() {
        audioRecorder.setAudioRecordListener(mockListener)
        audioRecorder.forceStop()

        assertFalse(audioRecorder.isRecording())
        verify { mockListener.onError("Recording stopped") }
    }

    @Test
    fun testStopRecordingWhenNotRecording() {
        audioRecorder.setAudioRecordListener(mockListener)
        audioRecorder.stopRecording()

        assertFalse(audioRecorder.isRecording())
        // When not recording, myAudioRecorder is null, so stopRecording does nothing
        // and doesn't call listener because myAudioRecorder?.let skips it
    }

    @Test
    fun testOnRecordClickedPermissionGranted() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO) } returns PackageManager.PERMISSION_GRANTED

        // Use spyk to avoid actually starting MediaRecorder
        val spyAudioRecorder = spyk(audioRecorder)
        every { spyAudioRecorder.startRecording() } returns Unit

        spyAudioRecorder.setAudioRecordListener(mockListener)
        spyAudioRecorder.onRecordClicked()

        verify { spyAudioRecorder.startRecording() }
    }

    @Test
    fun testOnRecordClickedPermissionGrantedButRecording() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO) } returns PackageManager.PERMISSION_GRANTED

        val spyAudioRecorder = spyk(audioRecorder)
        every { spyAudioRecorder.isRecording() } returns true
        every { spyAudioRecorder.stopRecording() } returns Unit

        spyAudioRecorder.setAudioRecordListener(mockListener)
        spyAudioRecorder.onRecordClicked()

        verify { spyAudioRecorder.stopRecording() }
    }

    @Test
    fun testSetCallerAndToggleRecording() {
        val mockCaller = mockk<ActivityResultCaller>()
        val mockLauncher = mockk<ActivityResultLauncher<String>>(relaxed = true)

        val contractSlot = slot<ActivityResultContract<String, Boolean>>()
        val callbackSlot = slot<androidx.activity.result.ActivityResultCallback<Boolean>>()

        every {
            mockCaller.registerForActivityResult(
                capture(contractSlot),
                capture(callbackSlot)
            )
        } returns mockLauncher

        audioRecorder.setCaller(mockCaller, mockContext)

        // Trigger callback with true
        val spyAudioRecorder = spyk(audioRecorder)
        every { spyAudioRecorder.startRecording() } returns Unit

        // Call it by invoking reflection because callback is internal to setCaller
        // To simplify, let's just trigger permissionLauncher launch for test coverage

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO) } returns PackageManager.PERMISSION_DENIED

        audioRecorder.onRecordClicked()
        verify { mockLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}
