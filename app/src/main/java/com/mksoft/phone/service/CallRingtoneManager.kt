package com.mksoft.phone.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class CallRingtoneManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    @Suppress("DEPRECATION")
    private fun getVibratorService(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun startRinging() {
        if (mediaPlayer != null) {
            Log.d("CallRingtoneManager", "Ringtone already playing")
            return
        }

        Log.d("CallRingtoneManager", "Starting ringtone playback and vibration")
        try {
            // 1. Play default ringtone
            val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("CallRingtoneManager", "Error playing ringtone: ${e.message}", e)
        }

        try {
            // 2. Start vibration (pattern: vibrate 1s, off 1s, repeat)
            vibrator = getVibratorService()
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    val pattern = longArrayOf(0, 1000, 1000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        v.vibrate(pattern, 0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CallRingtoneManager", "Error starting vibration: ${e.message}", e)
        }
    }

    fun stopRinging() {
        Log.d("CallRingtoneManager", "Stopping ringtone and vibration")
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("CallRingtoneManager", "Error stopping ringtone: ${e.message}", e)
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("CallRingtoneManager", "Error stopping vibration: ${e.message}", e)
        } finally {
            vibrator = null
        }
    }
}
