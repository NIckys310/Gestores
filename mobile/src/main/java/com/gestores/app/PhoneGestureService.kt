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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado de la detección de gestos, para mostrarlo en el reloj. */
object PhoneBus {
    val running = MutableStateFlow(false)
    val lastGesture = MutableStateFlow<String?>(null)
    /** Sube en 1 cada vez que se detecta un gesto (para animarlo en la esfera). */
    val gestureEvent = MutableStateFlow(0L)
    val result = MutableStateFlow("")
    val trainingTarget = MutableStateFlow<String?>(null)   // gesto que se está entrenando
    val trainCount = MutableStateFlow(0)
    val sensitivity = MutableStateFlow(1f)
    /** Confianza del último gesto de la IA (0 a 1); null si fue un gesto básico. */
    val confidence = MutableStateFlow<Float?>(null)
    // Calibración guiada
    val calibTarget = MutableStateFlow<String?>(null)
    val calibCount = MutableStateFlow(0)
    val calibFactor = MutableStateFlow<Float?>(null)
    val calibSamples = mutableListOf<List<Sample>>()
    /** Gesto dudoso esperando tu "Sí" o "No". */
    @Volatile var pending: Pair<String, List<Sample>>? = null
}

/**
 * GESTOS CON EL CELULAR
 * Usa el acelerómetro y el giroscopio del celular (los mismos sensores de un reloj)
 * para reconocer los gestos y ejecutar la acción dentro del reloj simulado.
 * Todo pasa en el celular: no se conecta con ningún otro aparato.
 */
class PhoneGestureService : Service(), SensorEventListener {

    companion object {
        private const val ACTION_STOP = "stop"
        private const val CHANNEL_RUN = "detectando"
        /** Por debajo de esta confianza el reloj pregunta "¿Fue este gesto?". */
        const val MIN_CONFIDENCE = 0.55f

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, PhoneGestureService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, PhoneGestureService::class.java))
        fun loadSensitivity(ctx: Context) = ctx.prefs().getFloat("phone_sensitivity", 1f)
        fun saveSensitivity(ctx: Context, v: Float) {
            PhoneBus.sensitivity.value = v
            ctx.prefs().edit().putFloat("phone_sensitivity", v).apply()
        }

        /** Ejecuta la acción del gesto y la guarda en el historial. */
        fun execute(ctx: Context, id: String, confidence: Float?) {
            PhoneBus.lastGesture.value = id
            PhoneBus.confidence.value = confidence
            PhoneBus.gestureEvent.value = PhoneBus.gestureEvent.value + 1
            val pct = confidence?.let { " · ${(it * 100).toInt()} %" } ?: ""
            val result = ActionRunner.runGesture(ctx, id)
            PhoneBus.result.value = result + pct
            EventLog.add(ctx, id, result + pct)
        }

        /** Respuesta a "¿Fue este gesto?" desde la esfera. */
        fun answer(ctx: Context, yes: Boolean) {
            val ask = WatchState.ask.value ?: return
            WatchState.ask.value = null
            val pending = PhoneBus.pending
            PhoneBus.pending = null
            if (yes) {
                pending?.let { (id, seg) -> Learner.add(id, seg) }   // la IA aprende de tu respuesta
                execute(ctx, ask.first, 1f)
            } else PhoneBus.result.value = "Ignorado: no era ${Gestures.label(ask.first)}"
        }

        private fun createChannel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_RUN, "Detección de gestos", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private lateinit var sensors: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val segmenter = MotionSegmenter(::onMovement)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Learner.init(this)
        createChannel(this)
        PhoneBus.sensitivity.value = loadSensitivity(this)
        startForeground(3, buildOngoing())
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // Sigue detectando con la pantalla apagada
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gestores:gestos").apply { acquire(4 * 60 * 60 * 1000L) }
        PhoneBus.running.value = true
        PhoneBus.result.value = "Escuchando gestos…"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WatchState.gestoresOn.value = false                       // el interruptor de la app queda igual que el servicio
            prefs().edit().putBoolean("gestores_on", false).apply()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        PhoneBus.running.value = false
        PhoneBus.result.value = "Detección apagada"
        PhoneBus.trainingTarget.value = null
        PhoneBus.calibTarget.value = null
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

    /** Llega un movimiento completo: se usa para entrenar, calibrar o se reconoce. */
    private fun onMovement(seg: List<Sample>) {
        val training = PhoneBus.trainingTarget.value
        if (training != null) {
            Learner.add(training, seg)
            PhoneBus.trainCount.value = Learner.count(training)
            PhoneBus.result.value = "Muestra ${Learner.count(training)} guardada"
            vibrate(longArrayOf(0, 40, 60, 40))
            return
        }
        // Calibración guiada: se guardan 3 repeticiones y se ajusta el umbral
        val calib = PhoneBus.calibTarget.value
        if (calib != null) {
            if (PhoneBus.calibSamples.size >= 3) return
            PhoneBus.calibSamples += seg
            PhoneBus.calibCount.value = PhoneBus.calibSamples.size
            vibrate(longArrayOf(0, 40, 60, 40))
            if (PhoneBus.calibSamples.size == 3) {
                PhoneBus.calibFactor.value = Calibration.calibrate(calib, PhoneBus.calibSamples.toList())
                vibrate(longArrayOf(0, 80, 80, 80, 80, 160))
            }
            return
        }
        // Primero la IA (gestos aprendidos), luego los gestos básicos
        Learner.match(seg)?.let { (id, conf) ->
            if (conf < MIN_CONFIDENCE) {
                if (!WatchState.visible) return                       // nadie vería la pregunta: se ignora
                PhoneBus.pending = id to seg
                WatchState.ask.value = id to conf          // la esfera pregunta "¿Fue este gesto?"
                vibrate(longArrayOf(0, 30, 80, 30))
            } else {
                Learner.learnFromUse(id, seg, conf)       // aprende mientras lo usas
                onGesture(id, conf)
            }
            return
        }
        BasicClassifier.classify(seg, PhoneBus.sensitivity.value)?.let { onGesture(it, null) }
    }

    private fun onGesture(id: String, confidence: Float?) {
        vibrate(pattern(id))
        execute(this, id, confidence)
    }

    /** Cada gesto vibra distinto (1, 2 o 3 toques, cortos o largos). */
    private fun pattern(id: String): LongArray = when (id) {
        "alzar" -> longArrayOf(0, 60)
        "bajar" -> longArrayOf(0, 160)
        "girar" -> longArrayOf(0, 50, 70, 50)
        "doble_giro" -> longArrayOf(0, 50, 70, 50, 70, 50)
        "sacudir" -> longArrayOf(0, 200, 80, 60)
        "doble_golpe" -> longArrayOf(0, 120, 80, 120)
        "toques" -> longArrayOf(0, 40, 50, 40, 50, 40, 50, 40)
        else -> longArrayOf(0, 60, 60, 200)   // dibujos y gestos IA
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
            .setContentTitle("Gestores · detectando gestos")
            .setContentText("Toca para abrir el reloj")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .build()
    }
}
