package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MediaPlayer-backed emergency siren utility that maximizes the system alarm stream volume
 * and plays a continuous looping siren alert when SOS is triggered.
 */
open class SosSirenController(
    private val createMediaPlayer: () -> MediaPlayer = { MediaPlayer() },
    private val rawSirenResId: Int = R.raw.sos_emergency_siren
) {
    private val lock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var previousAlarmVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    @Suppress("DEPRECATION")
    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { /* Keep siren active during SOS */ }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _lastAppliedVolume = MutableStateFlow<Int?>(null)
    val lastAppliedVolume: StateFlow<Int?> = _lastAppliedVolume.asStateFlow()

    /**
     * Raises the alarm audio stream to maximum system volume and begins looping the emergency siren.
     */
    fun startSiren(context: Context, forceUnmute: Boolean = false): Boolean {
        val appCtx = context.applicationContext ?: context
        synchronized(lock) {
            if (forceUnmute) {
                _isMuted.value = false
            }
            if (_isMuted.value) {
                return false
            }
            if (_isPlaying.value && mediaPlayer != null) {
                maximizeAlarmVolume(appCtx)
                return true
            }

            releasePlayerLocked()

            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            maximizeAlarmVolume(appCtx)
            requestAlarmAudioFocus(appCtx, alarmAttributes)

            val player = createMediaPlayer()
            return try {
                player.setAudioAttributes(alarmAttributes)
                configureDataSource(appCtx, player)
                player.isLooping = true
                player.setVolume(1.0f, 1.0f)
                player.setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer siren error (what=$what, extra=$extra)")
                    synchronized(lock) {
                        if (mediaPlayer === mp) {
                            releasePlayerLocked()
                            _isPlaying.value = false
                        }
                    }
                    true
                }
                player.prepare()
                player.start()
                mediaPlayer = player
                _isPlaying.value = true
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start emergency siren MediaPlayer: ${e.message}")
                try {
                    player.release()
                } catch (_: Exception) {
                }
                mediaPlayer = null
                _isPlaying.value = false
                abandonAlarmAudioFocus(appCtx)
                false
            }
        }
    }

    /**
     * Stops the emergency siren, releases MediaPlayer resources, and optionally restores the
     * system alarm volume that was present before SOS activation.
     */
    fun stopSiren(context: Context? = null, restoreVolume: Boolean = true) {
        val appCtx = context?.applicationContext ?: context
        synchronized(lock) {
            releasePlayerLocked()
            _isPlaying.value = false
            if (appCtx != null) {
                abandonAlarmAudioFocus(appCtx)
                if (restoreVolume) {
                    restoreAlarmVolume(appCtx)
                }
            }
        }
    }

    /**
     * Mutes or unmutes the siren. When [resumeIfActive] is true and unmuting, playback resumes
     * immediately at maximum system volume.
     */
    fun setMuted(context: Context, muted: Boolean, resumeIfActive: Boolean = false) {
        synchronized(lock) {
            _isMuted.value = muted
            if (muted) {
                stopSiren(context, restoreVolume = true)
            } else if (resumeIfActive) {
                startSiren(context, forceUnmute = true)
            }
        }
    }

    fun resetMuteState() {
        synchronized(lock) {
            _isMuted.value = false
        }
    }

    private fun configureDataSource(context: Context, player: MediaPlayer) {
        var loadedRaw = false
        try {
            val afd = context.resources.openRawResourceFd(rawSirenResId)
            if (afd != null) {
                afd.use { descriptor ->
                    player.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                }
                loadedRaw = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Raw siren asset unavailable, falling back to system alarm URI: ${e.message}")
        }

        if (!loadedRaw) {
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: throw IllegalStateException("No alarm audio source available")
            player.setDataSource(context, fallbackUri)
        }
    }

    private fun maximizeAlarmVolume(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (previousAlarmVolume == null) {
                previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (maxVolume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                _lastAppliedVolume.value = maxVolume
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Unable to modify alarm stream volume due to DND policy: ${se.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to maximize alarm volume: ${e.message}")
        }
    }

    private fun restoreAlarmVolume(context: Context) {
        val saved = previousAlarmVolume ?: return
        previousAlarmVolume = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val clamped = saved.coerceIn(0, maxVolume.coerceAtLeast(0))
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, clamped, 0)
            _lastAppliedVolume.value = clamped
        } catch (se: SecurityException) {
            Log.w(TAG, "Unable to restore alarm volume due to DND policy: ${se.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to restore alarm volume: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAlarmAudioFocus(context: Context, attributes: AudioAttributes) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(false)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                audioManager.requestAudioFocus(
                    legacyFocusListener,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request skipped: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAlarmAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                audioManager.abandonAudioFocus(legacyFocusListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon skipped: ${e.message}")
        }
    }

    private fun releasePlayerLocked() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        try {
            if (player.isPlaying) {
                player.stop()
            }
        } catch (_: Exception) {
        }
        try {
            player.reset()
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "SosSirenController"
    }
}

/**
 * Process-wide emergency siren utility shared across SOS UI and SosLocationSharingService.
 */
object SosSirenPlayer : SosSirenController()
