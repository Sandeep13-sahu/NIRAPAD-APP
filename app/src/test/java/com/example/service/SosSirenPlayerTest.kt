package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileDescriptor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SosSirenPlayerTest {

    private class RecordingMediaPlayer : MediaPlayer() {
        var capturedAttributes: AudioAttributes? = null
        var dataSourceConfigured = false
        var loopingSet = false
        var leftVol = 0f
        var rightVol = 0f
        var prepared = false
        var started = false
        var stopped = false
        var resetCalled = false
        var released = false
        private var playingNow = false

        override fun setAudioAttributes(attributes: AudioAttributes?) {
            capturedAttributes = attributes
        }

        override fun setDataSource(fd: FileDescriptor, offset: Long, length: Long) {
            dataSourceConfigured = length > 0L
        }

        override fun setLooping(looping: Boolean) {
            loopingSet = looping
        }

        override fun setVolume(leftVolume: Float, rightVolume: Float) {
            leftVol = leftVolume
            rightVol = rightVolume
        }

        override fun prepare() {
            prepared = true
        }

        override fun start() {
            started = true
            playingNow = true
        }

        override fun isPlaying(): Boolean = playingNow

        override fun stop() {
            stopped = true
            playingNow = false
        }

        override fun reset() {
            resetCalled = true
        }

        override fun release() {
            released = true
            playingNow = false
        }
    }

    @Test
    fun startSiren_maximizesAlarmVolume_configuresLoopingAlarmPlayer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1.coerceAtMost(maxVol), 0)

        val player = RecordingMediaPlayer()
        val controller = SosSirenController(createMediaPlayer = { player })

        val started = controller.startSiren(context)

        assertTrue(started)
        assertTrue(controller.isPlaying.value)
        assertEquals(maxVol, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
        assertEquals(maxVol, controller.lastAppliedVolume.value)
        assertEquals(AudioAttributes.USAGE_ALARM, player.capturedAttributes?.usage)
        assertTrue(player.dataSourceConfigured)
        assertTrue(player.loopingSet)
        assertEquals(1.0f, player.leftVol, 0.001f)
        assertEquals(1.0f, player.rightVol, 0.001f)
        assertTrue(player.prepared)
        assertTrue(player.started)

        controller.stopSiren(context, restoreVolume = true)
    }

    @Test
    fun stopSiren_releasesPlayer_andRestoresPreviousAlarmVolume() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val initialVol = if (maxVol >= 2) 2 else maxVol
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, initialVol, 0)

        val player = RecordingMediaPlayer()
        val controller = SosSirenController(createMediaPlayer = { player })

        assertTrue(controller.startSiren(context))
        assertEquals(maxVol, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))

        controller.stopSiren(context, restoreVolume = true)

        assertFalse(controller.isPlaying.value)
        assertTrue(player.stopped)
        assertTrue(player.resetCalled)
        assertTrue(player.released)
        assertEquals(initialVol, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test
    fun setMuted_blocksStartWhenMuted_andResumesMaxVolumeWhenUnmutedDuringActiveSos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        val createdPlayers = mutableListOf<RecordingMediaPlayer>()
        val controller = SosSirenController(createMediaPlayer = {
            RecordingMediaPlayer().also { createdPlayers += it }
        })

        controller.setMuted(context, muted = true, resumeIfActive = false)
        assertTrue(controller.isMuted.value)
        assertFalse(controller.startSiren(context))
        assertFalse(controller.isPlaying.value)
        assertTrue(createdPlayers.isEmpty())

        controller.setMuted(context, muted = false, resumeIfActive = true)
        assertFalse(controller.isMuted.value)
        assertTrue(controller.isPlaying.value)
        assertEquals(1, createdPlayers.size)
        assertEquals(maxVol, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))

        controller.stopSiren(context, restoreVolume = true)
    }
}
