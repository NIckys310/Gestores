package com.gestores.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Filtro antes de ejecutar cualquier gesto:
 *  1. ¿Estamos dentro de los días y el horario elegidos?
 *  2. ¿Hay que preguntar primero? → aviso con "Sí / No" (también en el reloj).
 *     Para confirmar desde el reloj basta con REPETIR el mismo gesto antes de 8 segundos.
 */
object Gate {
    const val WINDOW_MS = 8000L
    val pending = MutableStateFlow<String?>(null)
    private var pendingUntil = 0L

    /** Recibe un gesto y devuelve el texto que se muestra en la app y en el reloj. */
    suspend fun handle(ctx: Context, id: String): String {
        val cfg = ConfigStore.load(ctx)
        val title = Gestures.label(id)
        if (!cfg.isActiveNow()) {
            return "⏸ Fuera de horario (${cfg.scheduleText()})".also { Notify.result(ctx, title, it) }
        }
        val rule = cfg.rules[id]
        if (rule == null || rule.type == "none") {
            return "⚠ Este gesto no tiene acción. Asígnala en la app.".also { Notify.result(ctx, title, it) }
        }
        val now = System.currentTimeMillis()
        val isRepeat = pending.value == id && now < pendingUntil
        if (cfg.confirm && !isRepeat) {
            pending.value = id
            pendingUntil = now + WINDOW_MS
            Notify.confirm(ctx, title, rule.label)
            return "❓ ¿${rule.label}? Repite el gesto o toca Sí"
        }
        return run(ctx, id)
    }

    /** "Sí" desde el aviso. */
    suspend fun confirm(ctx: Context): String? {
        val id = pending.value ?: return null
        if (System.currentTimeMillis() > pendingUntil) { cancel(ctx); return null }
        return run(ctx, id)
    }

    /** "No" desde el aviso, o se acabó el tiempo. */
    fun cancel(ctx: Context) {
        pending.value = null
        Notify.cancelConfirm(ctx)
        PhoneBus.result.value = "Cancelado"
    }

    private suspend fun run(ctx: Context, id: String): String {
        pending.value = null
        Notify.cancelConfirm(ctx)
        val result = ActionRunner.runGesture(ctx, id)
        EventLog.add(ctx, id, result)
        PhoneBus.result.value = result
        Notify.result(ctx, Gestures.label(id), result)
        return result
    }
}

/** Avisos que aparecen en el celular y que Honor Health envía al reloj. */
object Notify {
    private const val CH_RESULT = "resultados"
    private const val CH_CONFIRM = "confirmar"
    private const val TAG_RESULT = "resultado"
    private const val TAG_CONFIRM = "confirmar"
    private const val CONFIRM_ID = 777

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Resultado de gestos (reloj)", NotificationManager.IMPORTANCE_HIGH)
            .apply { enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel(CH_CONFIRM, "Confirmar gestos (reloj)", NotificationManager.IMPORTANCE_HIGH)
            .apply { enableVibration(true) })
    }

    @SuppressLint("MissingPermission")
    fun result(ctx: Context, title: String, text: String) {
        channels(ctx)
        val cfg = ConfigStore.load(ctx)
        val b = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)                                  // se puede deslizar para borrar
        if (cfg.clearAfterSec > 0) b.setTimeoutAfter(cfg.clearAfterSec * 1000L)   // se borra sola
        runCatching { NotificationManagerCompat.from(ctx).notify(TAG_RESULT, (System.currentTimeMillis() % 100000).toInt(), b.build()) }
    }

    @SuppressLint("MissingPermission")
    fun confirm(ctx: Context, title: String, actionLabel: String) {
        channels(ctx)
        fun pi(action: String, code: Int) = PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, ConfirmReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH_CONFIRM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("❓ $title")
            .setContentText("¿$actionLabel? Repite el gesto para confirmar")
            .setStyle(NotificationCompat.BigTextStyle().bigText("¿$actionLabel?\nRepite el mismo gesto o toca Sí antes de 8 segundos."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(Gate.WINDOW_MS)
            .addAction(0, "Sí", pi(ConfirmReceiver.ACTION_YES, 1))
            .addAction(0, "No", pi(ConfirmReceiver.ACTION_NO, 2))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(TAG_CONFIRM, CONFIRM_ID, n) }
    }

    fun cancelConfirm(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(TAG_CONFIRM, CONFIRM_ID)

    /** Borra todos los avisos de gestos (botón "Borrar avisos"). */
    fun clearAll(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.tag == TAG_RESULT || it.tag == TAG_CONFIRM }.forEach { nm.cancel(it.tag, it.id) }
    }
}

/** Botones "Sí" y "No" del aviso de confirmación. */
class ConfirmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_YES = "com.gestores.app.CONFIRMAR_SI"
        const val ACTION_NO = "com.gestores.app.CONFIRMAR_NO"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val done = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (intent.action == ACTION_YES) Gate.confirm(ctx) else Gate.cancel(ctx)
            } finally { done.finish() }
        }
    }
}
