package com.gestores.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Estado de la detección en el celular, para mostrarlo en la pantalla. */
object PhoneBus {
    val running = MutableStateFlow(false)
    val lastGesture = MutableStateFlow<String?>(null)
    val result = MutableStateFlow("")
    val trainingTarget = MutableStateFlow<String?>(null)   // gesto IA que se está entrenando
    val trainCount = MutableStateFlow(0)
    val sensitivity = MutableStateFlow(1f)
}

/**
 * CELULAR EN LA MUÑECA
 * Usa el acelerómetro y el giroscopio del celular (los mismos sensores de un reloj)
 * para reconocer los gestos, ejecuta la acción y envía una notificación que
 * aparece y vibra en el reloj Honor (o cualquier reloj que reciba notificaciones).
 */
class PhoneGestureService : Service(), SensorEventListener {

    companion object {
        private const val ACTION_STOP = "stop"
        private const val CHANNEL_RUN = "detectando"
        private const val CHANNEL_RESULT = "resultados"   // ← este canal es el que llega al reloj

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, PhoneGestureService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, PhoneGestureService::class.java))
        fun loadSensitivity(ctx: Context) = ctx.prefs().getFloat("phone_sensitivity", 1f)
        fun saveSensitivity(ctx: Context, v: Float) {
            PhoneBus.sensitivity.value = v
            ctx.prefs().edit().putFloat("phone_sensitivity", v).apply()
        }

        /** Notificación que muestra el reloj. También sirve para probar sin hacer gestos. */
        @SuppressLint("MissingPermission")
        fun notifyWatch(ctx: Context, title: String, text: String) {
            createChannels(ctx)
            val n = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setTimeoutAfter(15_000)        // se borra sola para no llenar el reloj
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt() + 100, n) }
        }

        private fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_RUN, "Detección de gestos", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Resultado de gestos (reloj)", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sensors: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val segmenter = MotionSegmenter(::onMovement)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Learner.init(this)
        createChannels(this)
        PhoneBus.sensitivity.value = loadSensitivity(this)
        startForeground(3, buildOngoing())
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // Sigue detectando con la pantalla apagada
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gestores:muneca").apply { acquire(4 * 60 * 60 * 1000L) }
        PhoneBus.running.value = true
        PhoneBus.result.value = "Escuchando gestos…"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        PhoneBus.running.value = false
        PhoneBus.result.value = "Detección apagada"
        PhoneBus.trainingTarget.value = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(e: SensorEvent) {
        segmenter.sensitivity = PhoneBus.sensitivity.value
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> segmenter.onAccel(e.values[0], e.values[1], e.values[2], e.timestamp)
            Sensor.TYPE_GYROSCOPE -> segmenter.onGyro(e.values[0], e.values[1], e.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Llega un movimiento completo: se usa para entrenar la IA o se reconoce. */
    private fun onMovement(seg: List<Sample>) {
        val training = PhoneBus.trainingTarget.value
        if (training != null) {
            Learner.add(training, seg)
            PhoneBus.trainCount.value = Learner.count(training)
            PhoneBus.result.value = "Muestra ${Learner.count(training)} guardada"
            vibrate(longArrayOf(0, 40, 60, 40))
            return
        }
        // Primero la IA (gestos aprendidos), luego los gestos básicos
        Learner.match(seg)?.let { (id, _) -> onGesture(id); return }
        BasicClassifier.classify(seg, PhoneBus.sensitivity.value)?.let { onGesture(it) }
    }

    private fun onGesture(id: String) {
        vibrate(longArrayOf(0, 60))
        PhoneBus.lastGesture.value = id
        PhoneBus.result.value = "Ejecutando…"
        scope.launch {
            val result = ActionRunner.runGesture(this@PhoneGestureService, id)
            PhoneBus.result.value = result
            EventLog.add(this@PhoneGestureService, id, result)
            notifyWatch(this@PhoneGestureService, Gestures.label(id), result)   // aparece y vibra en el Honor
        }
    }

    private fun vibrate(pattern: LongArray) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
                          else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun buildOngoing(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, PhoneGestureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gestores · celular en la muñeca")
            .setContentText("Detectando tus gestos")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .build()
    }
}
