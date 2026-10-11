package com.gestores.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.view.KeyEvent

/** Ejecuta dentro del celular la acción configurada para cada gesto. */
object ActionRunner {

    fun runGesture(ctx: Context, gestureId: String): String {
        val rule = ConfigStore.load(ctx).rules[gestureId] ?: return "Sin acción"
        return execute(ctx, rule)
    }

    fun execute(ctx: Context, r: Rule): String = runCatching {
        when (r.type) {
            "app" -> { WatchState.openApp(r.value); "✓ ${r.label.substringAfter(' ')}" }
            "face" -> { WatchState.nextFace.value++; "✓ Esfera nueva" }
            "chrono" -> { WatchState.toggleChrono(); if (WatchState.chronoRunning.value) "✓ Cronómetro andando" else "✓ Cronómetro en pausa" }
            "flashlight" -> toggleTorch(ctx)
            "media" -> media(ctx, r.value)
            "volume" -> volume(ctx, r.value == "up")
            "vibrate_mode" -> vibrateMode(ctx)
            else -> "Sin acción"
        }
    }.getOrElse { "⚠ ${it.message ?: "Error"}" }

    fun toggleTorch(ctx: Context): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "⚠ Este celular no tiene linterna"
        val on = !WatchState.torch.value
        cm.setTorchMode(id, on)
        WatchState.torch.value = on
        return if (on) "✓ Linterna encendida" else "✓ Linterna apagada"
    }

    /** Controla la música que suena en el celular (Spotify, YouTube Music…). */
    fun media(ctx: Context, what: String): String {
        val code = when (what) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "✓ " + when (what) { "next" -> "Siguiente canción"; "prev" -> "Canción anterior"; else -> "Play / pausa" }
    }

    fun volume(ctx: Context, up: Boolean): String {
        ctx.getSystemService(AudioManager::class.java).adjustStreamVolume(
            AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return if (up) "✓ Volumen +" else "✓ Volumen −"
    }

    /** Timbre ↔ solo vibrar (no necesita permisos especiales). */
    private fun vibrateMode(ctx: Context): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val toVibrate = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        am.ringerMode = if (toVibrate) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        return if (toVibrate) "✓ Celular en vibración" else "✓ Timbre activado"
    }
}
