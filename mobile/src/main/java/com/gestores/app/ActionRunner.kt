package com.gestores.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.view.KeyEvent

/** Ejecuta dentro del celular la acción configurada para cada gesto. */
object ActionRunner {

    /** Las acciones con sonido piden confirmación antes de hacerse. */
    fun needsConfirm(r: Rule) = r.type in listOf("media", "volume", "vibrate_mode")

    /** La pregunta que se muestra antes de una acción con sonido. */
    fun question(r: Rule) = when (r.type) {
        "media" -> when (r.value) { "next" -> "¿Pasar a la siguiente canción?"; "prev" -> "¿Volver a la canción anterior?"; else -> "¿Deseas reproducir o pausar la música?" }
        "volume" -> if (r.value == "up") "¿Deseas subir el volumen?" else "¿Deseas bajar el volumen?"
        "vibrate_mode" -> "¿Cambiar el timbre del celular (sonido ↔ vibración)?"
        else -> "¿Deseas hacerlo?"
    }

    /**
     * Ejecuta el gesto: si la acción tiene sonido, primero pregunta (en el reloj o, si la app
     * está en segundo plano, con una notificación que tiene Sí / No). Lo demás se hace de una vez.
     */
    fun runGesture(ctx: Context, gestureId: String): String {
        val rule = ConfigStore.load(ctx).rules[gestureId] ?: return "Sin acción"
        if (rule.type == "none") return "Sin acción"
        if (needsConfirm(rule)) {
            WatchState.confirm.value = gestureId to rule
            if (!WatchState.visible) notifyConfirm(ctx, rule)
            return "Esperando tu respuesta…"
        }
        return execute(ctx, rule)
    }

    /** Respuesta a la pregunta "¿Deseas…?". */
    fun answer(ctx: Context, yes: Boolean) {
        val (id, rule) = WatchState.confirm.value ?: return
        WatchState.confirm.value = null
        NotificationManagerCompat.from(ctx).cancel(CONFIRM_ID)
        val result = if (yes) execute(ctx, rule) else "Cancelado"
        PhoneBus.result.value = result
        EventLog.add(ctx, id, result)
    }

    private const val CONFIRM_ID = 78

    @SuppressLint("MissingPermission")
    private fun notifyConfirm(ctx: Context, r: Rule) {
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("confirmar", "Preguntas antes de poner sonido", NotificationManager.IMPORTANCE_HIGH))
        fun btn(yes: Boolean) = PendingIntent.getBroadcast(ctx, if (yes) 21 else 22,
            Intent(ctx, ConfirmReceiver::class.java).putExtra("yes", yes), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "confirmar")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(question(r))
            .setContentText(r.label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(20_000)
            .addAction(0, "Sí", btn(true))
            .addAction(0, "No", btn(false))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(CONFIRM_ID, n) }
    }

    fun execute(ctx: Context, r: Rule): String = runCatching {
        when (r.type) {
            "app" -> { WatchState.openApp(r.value); "✓ ${r.label.substringAfter(' ')}" }
            "camera" -> launch(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "la cámara")
            "open_app" -> launch(ctx, ctx.packageManager.getLaunchIntentForPackage(r.value), r.label.removePrefix("📱 Abrir "))
            "face" -> { WatchState.nextFace.value++; "✓ Esfera nueva" }
            "chrono" -> { WatchState.toggleChrono(); if (WatchState.chronoRunning.value) "✓ Cronómetro andando" else "✓ Cronómetro en pausa" }
            "flashlight" -> toggleTorch(ctx)
            "media" -> media(ctx, r.value)
            "volume" -> volume(ctx, r.value == "up")
            "vibrate_mode" -> vibrateMode(ctx)
            else -> "Sin acción"
        }
    }.getOrElse { "⚠ ${it.message ?: "Error"}" }

    /** Abre una app del celular. Con la app en segundo plano necesita "Mostrar sobre otras apps". */
    private fun launch(ctx: Context, intent: Intent?, label: String): String {
        if (intent == null) return "⚠ No se encontró $label"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (WatchState.visible || Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(intent); "✓ Abriendo $label"
        } else {
            notifyToOpen(ctx, intent, label); "✓ Toca la notificación para abrir $label"
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyToOpen(ctx: Context, intent: Intent, label: String) {
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("abrir", "Abrir apps con gestos", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, label.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "abrir").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gesto recibido").setContentText("Toca para abrir $label")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(label.hashCode(), n) }
    }

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

/** Botones "Sí" / "No" de la notificación que pregunta antes de poner sonido. */
class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = ActionRunner.answer(ctx, intent.getBooleanExtra("yes", false))
}
