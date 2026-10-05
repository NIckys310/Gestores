package com.gestores.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * BOTONES DEL RELOJ · funciona con cualquier reloj que controle la música del celular.
 *
 * Gestores se presenta como un reproductor de música. Cuando tocas play, siguiente,
 * anterior o volumen en el reloj, Android se lo entrega a Gestores y Gestores
 * ejecuta la acción que elegiste. En la pantalla de música del reloj aparece
 * el resultado como si fuera el nombre de la canción.
 */
class UniversalService : Service() {

    companion object {
        val active = MutableStateFlow(false)
        val lastTrigger = MutableStateFlow<String?>(null)
        val lastResult = MutableStateFlow("")
        private const val CHANNEL = "botones_reloj"
        private const val DOUBLE_WINDOW = 900L   // ms para contar un doble toque

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, UniversalService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, UniversalService::class.java))
        fun useVolume(ctx: Context) = ctx.prefs().getBoolean("uni_volume", false)
        fun setUseVolume(ctx: Context, on: Boolean) = ctx.prefs().edit().putBoolean("uni_volume", on).apply()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat
    private var silence: AudioTrack? = null
    private var pendingJob: Job? = null
    private var pendingId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "Gestores").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = press("w_play")
                override fun onPause() = press("w_play")
                override fun onStop() = press("w_play")
                override fun onSkipToNext() = press("w_next")
                override fun onSkipToPrevious() = press("w_prev")
            })
            // Volumen del reloj como botones (opcional: también captura los botones de volumen del celular)
            if (useVolume(this@UniversalService)) {
                setPlaybackToRemote(object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 10, 5) {
                    override fun onAdjustVolume(direction: Int) {
                        if (direction > 0) press("w_volup") else if (direction < 0) press("w_voldown")
                    }
                })
            }
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
        showOnWatch("Gestores listo", "Usa los botones de música")
        startForeground(2, buildNotification())
        startSilence()
        active.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        active.value = false
        silence?.run { runCatching { stop() }; release() }
        session.isActive = false
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Detecta toque simple o doble en "siguiente" y "anterior". */
    private fun press(base: String) {
        if (base == "w_next" || base == "w_prev") {
            if (pendingId == base) {
                pendingJob?.cancel(); pendingId = null
                fire(base + "2")
            } else {
                pendingJob?.cancel(); pendingId = base
                pendingJob = scope.launch { delay(DOUBLE_WINDOW); pendingId = null; fire(base) }
            }
        } else fire(base)
    }

    private fun fire(id: String) {
        lastTrigger.value = id
        showOnWatch(Gestures.label(id), "Ejecutando…")
        scope.launch {
            val result = ActionRunner.runGesture(this@UniversalService, id)
            lastResult.value = result
            EventLog.add(this@UniversalService, id, result)
            showOnWatch(Gestures.label(id), result)   // el reloj lo muestra como "canción"
        }
    }

    private fun showOnWatch(title: String, subtitle: String) {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Gestores")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 3_600_000)
                .build()
        )
    }

    /**
     * Android solo entrega los botones de música al último reproductor que sonó.
     * Por eso Gestores reproduce silencio mientras los botones del reloj están activos.
     */
    private fun startSilence() {
        runCatching {
            val rate = 8000
            val buffer = ShortArray(rate)   // 1 segundo de silencio en bucle
            silence = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(rate * 2)
                .build().apply {
                    write(buffer, 0, buffer.size)
                    setLoopPoints(0, buffer.size, -1)
                    play()
                }
        }
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Botones del reloj", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gestores · botones del reloj")
            .setContentText("Los botones de música de tu reloj controlan tus acciones")
            .setContentIntent(open)
            .setOngoing(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken))
            .build()
    }
}
