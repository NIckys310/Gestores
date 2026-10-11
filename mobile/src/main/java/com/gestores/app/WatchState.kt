package com.gestores.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Estado del reloj compartido entre las apps, los gestos y la esfera. */
object WatchState {
    /** App que hay que abrir dentro del reloj (la pide un gesto). */
    val openRequest = MutableStateFlow<String?>(null)
    fun openApp(id: String) { openRequest.value = id }

    val nextFace = MutableStateFlow(0)

    /** Siempre encendido (pantalla oscura que no se apaga). */
    val aod = MutableStateFlow(false)
    fun setAod(ctx: Context, v: Boolean) { aod.value = v; ctx.prefs().edit().putBoolean("aod", v).apply() }
    fun init(ctx: Context) { aod.value = ctx.prefs().getBoolean("aod", false) }
    val torch = MutableStateFlow(false)

    // Cronómetro: sigue contando aunque cambies de app
    val chronoRunning = MutableStateFlow(false)
    val chronoAccum = MutableStateFlow(0L)
    val chronoStart = MutableStateFlow(0L)
    val laps = MutableStateFlow<List<Long>>(emptyList())
    fun chronoNow() = chronoAccum.value + if (chronoRunning.value) System.currentTimeMillis() - chronoStart.value else 0L
    fun toggleChrono() {
        if (chronoRunning.value) { chronoAccum.value = chronoNow(); chronoRunning.value = false }
        else { chronoStart.value = System.currentTimeMillis(); chronoRunning.value = true }
    }
    fun resetChrono() { chronoRunning.value = false; chronoAccum.value = 0; laps.value = emptyList() }

    // Temporizador: hora a la que termina (0 = apagado)
    val timerEnd = MutableStateFlow(0L)

    // Ejercicio
    val workoutStart = MutableStateFlow(0L)
    val workoutSteps0 = MutableStateFlow(-1)

    /** Gesto dudoso: la esfera pregunta "¿Fue este gesto?". */
    val ask = MutableStateFlow<Pair<String, Float>?>(null)
}

@Serializable
data class Alarm(val id: Int, val hour: Int, val minute: Int, val on: Boolean = true)

/** Alarmas, temporizador y recordatorio de agua. Suenan aunque la app esté cerrada. */
object Alarms {
    const val CH_ALARM = "alarma"
    const val CH_WATER = "agua"
    private const val TIMER_ID = 900
    private const val WATER_ID = 901
    private val ser = ListSerializer(Alarm.serializer())

    fun list(ctx: Context): List<Alarm> =
        ctx.prefs().getString("alarms", null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()

    fun save(ctx: Context, l: List<Alarm>) {
        ctx.prefs().edit().putString("alarms", json.encodeToString(ser, l)).apply()
        l.forEach { if (it.on) schedule(ctx, it) else cancel(ctx, it.id) }
    }

    fun nextTime(a: Alarm): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun rescheduleAll(ctx: Context) {
        list(ctx).filter { it.on }.forEach { schedule(ctx, it) }
        if (ctx.prefs().getBoolean("water_on", false)) waterReminder(ctx, true)
    }

    private fun pi(ctx: Context, id: Int, label: String) = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, AlarmReceiver::class.java).putExtra("id", id).putExtra("label", label),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun exact(ctx: Context, at: Long, op: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    fun schedule(ctx: Context, a: Alarm) = exact(ctx, nextTime(a), pi(ctx, a.id, "⏰ Alarma %02d:%02d".format(a.hour, a.minute)))
    fun cancel(ctx: Context, id: Int) = ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id, ""))

    fun startTimer(ctx: Context, ms: Long) {
        WatchState.timerEnd.value = System.currentTimeMillis() + ms
        exact(ctx, WatchState.timerEnd.value, pi(ctx, TIMER_ID, "⏲️ ¡Se acabó el tiempo!"))
    }
    fun stopTimer(ctx: Context) { WatchState.timerEnd.value = 0; cancel(ctx, TIMER_ID) }

    /** Recordatorio de agua cada 2 horas (solo de 8 a. m. a 9 p. m.). */
    fun waterReminder(ctx: Context, on: Boolean) {
        ctx.prefs().edit().putBoolean("water_on", on).apply()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val op = pi(ctx, WATER_ID, "💧 Hora de tomar agua")
        if (on) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2 * 3600_000L, 2 * 3600_000L, op)
        else am.cancel(op)
    }

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "Alarmas y temporizador", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(alarmSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
        })
        nm.createNotificationChannel(NotificationChannel(CH_WATER, "Recordatorio de agua", NotificationManager.IMPORTANCE_DEFAULT))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val label = intent.getStringExtra("label") ?: "Gestores"
        if (id == 901) {   // agua: solo de día
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (h < 8 || h >= 21) return
        }
        Alarms.channels(ctx)
        val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = id != 901
        val n = NotificationCompat.Builder(ctx, if (alarm) Alarms.CH_ALARM else Alarms.CH_WATER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(if (alarm) SimpleDateFormat("HH:mm", Locale("es")).format(Date()) else "Un vaso ahora y anótalo en el reloj")
            .setPriority(if (alarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { if (alarm) setFullScreenIntent(open, true) }
            .build()
        if (Build.VERSION.SDK_INT < 33 || ctx.has(Manifest.permission.POST_NOTIFICATIONS))
            runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
        if (id == 900) WatchState.timerEnd.value = 0
        // La alarma se repite cada día
        Alarms.list(ctx).firstOrNull { it.id == id && it.on }?.let { Alarms.schedule(ctx, it) }
    }
}
