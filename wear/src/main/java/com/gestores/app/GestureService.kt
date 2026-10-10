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
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Estado compartido entre el servicio y la pantalla del reloj. */
object Bus {
    val running = MutableStateFlow(false)
    val lastGesture = MutableStateFlow<String?>(null)
    val result = MutableStateFlow("")
    val trainingTarget = MutableStateFlow<String?>(null)   // gesto IA que se está entrenando
    val trainCount = MutableStateFlow(0)
    val sensitivity = MutableStateFlow(1f)
}

/**
 * Servicio que escucha los sensores del reloj aunque la pantalla esté apagada,
 * reconoce los gestos y los envía al celular (o directo al PC si no hay celular).
 */
class GestureService : Service(), SensorEventListener {

    companion object {
        private const val ACTION_SIMULATE = "simulate"
        private const val EXTRA_GESTURE = "gesture"
        private const val CHANNEL = "gestores"

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, GestureService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, GestureService::class.java))
        /** Para probar sin moverse (útil en el emulador). */
        fun simulate(ctx: Context, gestureId: String) = ContextCompat.startForegroundService(ctx,
            Intent(ctx, GestureService::class.java).setAction(ACTION_SIMULATE).putExtra(EXTRA_GESTURE, gestureId))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sensors: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val segmenter = MotionSegmenter(::onMovement)

    private val ackListener = MessageClient.OnMessageReceivedListener { ev ->
        if (ev.path == Proto.PATH_ACK) {
            val text = String(ev.data)
            Bus.result.value = text
            if (text.startsWith("⚠")) vibrate(longArrayOf(0, 80, 80, 80))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Learner.init(this)
        startForeground(1, buildNotification())
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // Mantiene los sensores activos con la pantalla apagada
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gestores:sensores").apply { acquire(4 * 60 * 60 * 1000L) }
        Wearable.getMessageClient(this).addListener(ackListener)
        Bus.running.value = true
        Bus.result.value = "Escuchando gestos…"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SIMULATE) intent.getStringExtra(EXTRA_GESTURE)?.let { onGesture(it, 1f) }
        return START_STICKY
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        Wearable.getMessageClient(this).removeListener(ackListener)
        Bus.running.value = false
        Bus.result.value = "Detección apagada"
        scope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(e: SensorEvent) {
        segmenter.sensitivity = Bus.sensitivity.value
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> segmenter.onAccel(e.values[0], e.values[1], e.values[2], e.timestamp)
            Sensor.TYPE_GYROSCOPE -> segmenter.onGyro(e.values[0], e.values[1], e.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Llega un movimiento completo: se usa para entrenar o se reconoce. */
    private fun onMovement(seg: List<Sample>) {
        val training = Bus.trainingTarget.value
        if (training != null) {
            Learner.add(training, seg)
            Bus.trainCount.value = Learner.count(training)
            Bus.result.value = "Muestra ${Learner.count(training)} guardada"
            vibrate(longArrayOf(0, 40, 60, 40))
            return
        }
        // Primero la IA (gestos aprendidos), luego los gestos básicos
        Learner.match(seg)?.let { (id, conf) -> onGesture(id, conf); return }
        BasicClassifier.classify(seg, Bus.sensitivity.value)?.let { onGesture(it, 1f) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onGesture(id: String, confidence: Float) {
        vibrate(longArrayOf(0, 60))
        Bus.lastGesture.value = id
        Bus.result.value = "Enviando…"
        scope.launch {
            val nodes = runCatching { Wearable.getNodeClient(this@GestureService).connectedNodes.await() }.getOrDefault(emptyList())
            if (nodes.isNotEmpty()) {
                // Hay celular: él ejecuta la acción y responde con un "ack"
                nodes.forEach { n ->
                    runCatching { Wearable.getMessageClient(this@GestureService).sendMessage(n.id, Proto.PATH_GESTURE, id.toByteArray()).await() }
                }
            } else {
                // Sin celular: el reloj llama directo al PC o al dispositivo inteligente por Wi-Fi
                Bus.result.value = runDirect(id)
            }
        }
    }

    private suspend fun runDirect(id: String): String {
        val cfg = ConfigStore.load(this)
        val rule = cfg.rules[id] ?: return "⚠ Sin acción para este gesto"
        return when (rule.type) {
            "pc" -> Http.call(Http.pcUrl(cfg, rule.value, ConfigStore.EMULATOR_PC)).let { (ok, msg) -> if (ok) "✓ ${rule.label}" else "⚠ $msg" }
            "webhook" -> Http.call(rule.value, rule.extra.ifBlank { "GET" }, if (rule.extra == "POST") "{}" else null)
                .let { (ok, msg) -> if (ok) "✓ ${rule.label}" else "⚠ $msg" }
            "none" -> "⚠ Sin acción para este gesto"
            else -> "⚠ “${rule.label}” necesita el celular conectado"
        }
    }

    private fun vibrate(pattern: LongArray) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
                          else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Detección de gestos", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gestores")
            .setContentText("Escuchando tus gestos")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
