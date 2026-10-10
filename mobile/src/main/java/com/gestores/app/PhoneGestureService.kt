package com.gestores.app

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
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
 * para reconocer los gestos. Cada gesto pasa por el filtro (horario + confirmación)
 * y el resultado llega como aviso al reloj Honor.
 */
class PhoneGestureService : Service(), SensorEventListener {

    companion object {
        private const val ACTION_STOP = "stop"
        private const val CHANNEL_RUN = "detectando"

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, PhoneGestureService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, PhoneGestureService::class.java))

        /** Aviso de prueba para comprobar que el reloj recibe las notificaciones. */
        fun notifyWatch(ctx: Context, title: String, text: String) = Notify.result(ctx, title, text)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sensors: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val segmenter = MotionSegmenter(::onMovement)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Learner.init(this)
        val cfg = ConfigStore.load(this)
        PhoneBus.sensitivity.value = cfg.sensitivity
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_RUN, "Detección de gestos", NotificationManager.IMPORTANCE_LOW))
        startForeground(3, buildOngoing())
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // Sigue detectando con la pantalla apagada
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gestores:muneca").apply { acquire(4 * 60 * 60 * 1000L) }
        PhoneBus.running.value = true
        PhoneBus.result.value = if (cfg.isActiveNow()) "Escuchando gestos…" else "Encendido, pero fuera de horario"
        // Aviso de estado en el reloj
        Notify.result(this, "✅ Gestores activo", cfg.scheduleText() + if (cfg.confirm) " · pregunta antes de actuar" else "")
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
        Notify.result(this, "⏹ Gestores detenido", "Los gestos ya no se detectan")
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
            vibrateLevel(this, 2, double = true)
            return
        }
        // Primero la IA (gestos aprendidos), luego los gestos básicos
        Learner.match(seg)?.let { (id, _) -> onGesture(id); return }
        BasicClassifier.classify(seg, PhoneBus.sensitivity.value)?.let { onGesture(it) }
    }

    private fun onGesture(id: String) {
        val cfg = ConfigStore.load(this)
        vibrateLevel(this, cfg.vibration)
        PhoneBus.lastGesture.value = id
        scope.launch { PhoneBus.result.value = Gate.handle(this@PhoneGestureService, id) }
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
