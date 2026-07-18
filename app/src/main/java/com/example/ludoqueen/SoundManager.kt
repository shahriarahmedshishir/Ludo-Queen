package com.example.ludoqueen

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val rollSoundId = loadSoundIfExists("sound_roll")
    private val captureSoundId = loadSoundIfExists("sound_capture")
    private val finishSoundId = loadSoundIfExists("sound_finish")
    private val winSoundId = loadSoundIfExists("sound_win")

    private fun loadSoundIfExists(rawName: String): Int? {
        val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
        return if (resId != 0) soundPool.load(context, resId, 1) else null
    }

    private fun play(id: Int?) {
        id?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun playRoll() = play(rollSoundId)
    fun playCapture() = play(captureSoundId)
    fun playFinish() = play(finishSoundId)
    fun playWin() = play(winSoundId)

    fun release() {
        soundPool.release()
    }
}