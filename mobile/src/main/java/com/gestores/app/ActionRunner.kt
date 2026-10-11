package com.gestores.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Ejecuta en el celular la acción configurada para cada gesto. */
object ActionRunner {
    private var torchOn = false

    suspend fun runGesture(ctx: Context, gestureId: String): String {
        val cfg = ConfigStore.load(ctx)
        val rule = cfg.rules[gestureId] ?: return "⚠ ${Gestures.label(gestureId)} no tiene acción"
        return execute(ctx, rule, cfg)
    }

    suspend fun execute(ctx: Context, r: Rule, cfg: Config = ConfigStore.load(ctx)): String = runCatching {
        when (r.type) {
            "open_app" -> launch(ctx, ctx.packageManager.getLaunchIntentForPackage(r.value), r.label)
            "open_url" -> launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(r.value)), r.label)
            "camera" -> launch(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Cámara")
            "flashlight" -> toggleTorch(ctx)
            "media" -> media(ctx, r.value)
            "volume" -> volume(ctx, r.value == "up")
            "call" -> call(ctx, r)
            "whatsapp" -> launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://wa.me/${r.value.filter { it.isDigit() }}?text=${Uri.encode(r.extra)}")), "WhatsApp (toca enviar)")
            "dnd" -> doNotDisturb(ctx)
            "vibrate_mode" -> vibrateMode(ctx)
            "webhook" -> Http.call(r.value, r.extra.ifBlank { "GET" }, if (r.extra == "POST") "{}" else null)
                .let { (ok, msg) -> if (ok) "✓ ${r.label}" else "⚠ $msg" }
            "pc" -> {
                if (cfg.pcAddress.isBlank()) "⚠ Configura la dirección del PC en la app del celular"
                else Http.call(Http.pcUrl(cfg, r.value)).let { (ok, msg) -> if (ok) "✓ ${r.label}" else "⚠ $msg" }
            }
            else -> "⚠ Sin acción"
        }
    }.getOrElse { "⚠ ${it.message ?: "Error"}" }

    /** Abrir apps desde segundo plano requiere el permiso "Mostrar sobre otras apps". */
    private fun launch(ctx: Context, intent: Intent?, label: String): String {
        if (intent == null) return "⚠ No se encontró $label"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(intent); "✓ Abriendo $label"
        } else {
            notifyToOpen(ctx, intent, label)
            "✓ Toca la notificación para abrir $label"
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyToOpen(ctx: Context, intent: Intent, label: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("acciones", "Acciones de gestos", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, label.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "acciones")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gesto recibido")
            .setContentText("Toca para abrir $label")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(label.hashCode(), n) }
    }

    private fun toggleTorch(ctx: Context): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "⚠ Este celular no tiene linterna"
        torchOn = !torchOn
        cm.setTorchMode(id, torchOn)
        return if (torchOn) "✓ Linterna encendida" else "✓ Linterna apagada"
    }

    private fun media(ctx: Context, what: String): String {
        // Con los botones del reloj activos, Gestores es el "reproductor": reenviar la tecla volvería a Gestores
        if (UniversalService.active.value) return "⚠ Con los botones del reloj activos, la música la controla Gestores"
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

    /** Llama directo si diste el permiso; si no, abre el marcador con el número listo. */
    private fun call(ctx: Context, r: Rule): String {
        val uri = Uri.parse("tel:" + r.value.filter { it.isDigit() || it == '+' })
        val direct = ctx.has(Manifest.permission.CALL_PHONE)
        return launch(ctx, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri), r.label.removePrefix("Llamar a "))
            .replace("Abriendo", "Llamando a")
    }

    /** "No molestar": prende / apaga. Android pide permiso una sola vez. */
    private fun doNotDisturb(ctx: Context): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            launch(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "el permiso de No molestar")
            return "⚠ Activa Gestores en “Acceso a No molestar” y repite el gesto"
        }
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return if (on) "✓ No molestar apagado" else "✓ No molestar encendido"
    }

    /** Timbre ↔ solo vibrar (no necesita permisos especiales). */
    private fun vibrateMode(ctx: Context): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val toVibrate = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        am.ringerMode = if (toVibrate) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        return if (toVibrate) "✓ Celular en vibración" else "✓ Timbre activado"
    }

    private fun volume(ctx: Context, up: Boolean): String {
        ctx.getSystemService(AudioManager::class.java).adjustStreamVolume(
            AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return if (up) "✓ Volumen +" else "✓ Volumen −"
    }
}
