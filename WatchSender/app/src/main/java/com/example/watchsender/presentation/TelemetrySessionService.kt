package com.example.datalayertest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.startExercise
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationData
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale

class TelemetrySessionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }

    @Volatile
    private var latestHeartRateBpm: Double? = null

    @Volatile
    private var latestHeartRateAvailability: String = "Aguardando leitura"

    @Volatile
    private var latestLocation: LocationData? = null

    @Volatile
    private var exerciseActive: Boolean = false

    private var telemetryJob: Job? = null
    private var sessionSequence: Int = 0
    private var loopSequence: Int = 0
    private var sendAttempts: Int = 0

    private val exerciseUpdateCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() {
            updateTelemetryState(
                running = true,
                status = "Sessao registrada. Coletando GPS e batimentos..."
            )
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            updateTelemetryState(
                running = false,
                status = "Falha ao registrar telemetria: ${throwable.message}"
            )
            serviceScope.launch {
                stopTelemetryInternal(stopService = true)
            }
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val state = update.exerciseStateInfo.state
            exerciseActive = state == ExerciseState.ACTIVE || state == ExerciseState.USER_STARTING

            val heartRatePoint = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()
            if (heartRatePoint != null) {
                latestHeartRateBpm = heartRatePoint.value
                latestHeartRateAvailability = "Disponivel"
            }

            val locationPoint = update.latestMetrics.getData(DataType.LOCATION).lastOrNull()
            if (locationPoint != null) {
                latestLocation = locationPoint.value
            }

            updateTelemetryState(
                running = true,
                status = buildWatchStatusMessage(
                    cycle = loopSequence,
                    phase = "Atualizacao do sensor recebida"
                )
            )

            if (state.isEnded) {
                updateTelemetryState(
                    running = false,
                    status = "Telemetria encerrada pelo sistema."
                )
                serviceScope.launch {
                    stopTelemetryInternal(stopService = true, endExercise = false)
                }
            }
        }

        override fun onLapSummaryReceived(
            lapSummary: androidx.health.services.client.data.ExerciseLapSummary
        ) = Unit

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            when (dataType) {
                DataType.HEART_RATE_BPM -> {
                    latestHeartRateAvailability = availability.javaClass.simpleName
                    updateTelemetryState(
                        running = true,
                        status = buildWatchStatusMessage(
                            cycle = loopSequence,
                            phase = "Heart rate: ${availability.javaClass.simpleName}"
                        )
                    )
                }
                DataType.LOCATION -> {
                    updateTelemetryState(
                        running = true,
                        status = buildWatchStatusMessage(
                            cycle = loopSequence,
                            phase = "GPS: ${availability.javaClass.simpleName}"
                        )
                    )
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> startTelemetrySession()
            ACTION_STOP -> {
                serviceScope.launch {
                    stopTelemetryInternal(stopService = true)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startTelemetrySession() {
        startTelemetryForeground()

        serviceScope.launch {
            if (telemetryJob?.isActive == true) {
                updateTelemetryState(true, "Telemetria ja esta em andamento.")
                return@launch
            }

            try {
                exerciseClient.setUpdateCallback(exerciseUpdateCallback)

                val exerciseInfo = runCatching { exerciseClient.getCurrentExerciseInfo() }.getOrNull()
                if (exerciseInfo?.exerciseTrackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                    exerciseActive = true
                    updateTelemetryState(true, "Recuperando sessao de telemetria em andamento...")
                } else {
                    exerciseClient.startExercise(buildExerciseConfig())
                    exerciseActive = true
                    updateTelemetryState(true, "Sessao iniciada. Mantendo telemetria em segundo plano.")
                }

                startPeriodicTelemetryLoop()
            } catch (e: Exception) {
                updateTelemetryState(false, "Erro ao iniciar telemetria: ${e.message}")
                stopTelemetryInternal(stopService = true, endExercise = false)
            }
        }
    }

    private fun buildExerciseConfig(): ExerciseConfig {
        return ExerciseConfig(
            exerciseType = ExerciseType.RUNNING,
            dataTypes = setOf(DataType.HEART_RATE_BPM, DataType.LOCATION),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = true
        )
    }

    private fun startPeriodicTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            while (isActive) {
                delay(TELEMETRY_INTERVAL_MS)
                loopSequence += 1

                if (!exerciseActive) {
                    updateTelemetryState(
                        running = true,
                        status = "Ciclo #$loopSequence aguardando ativacao do exercicio..."
                    )
                    continue
                }

                updateTelemetryState(
                    running = true,
                    status = buildWatchStatusMessage(loopSequence, "Preparando envio")
                )

                val payloadJson = buildTelemetryPayload()
                val sendResult = sendPayloadToPhone(payloadJson)

                if (sendResult.sent) {
                    val location = latestLocation
                    val bpm = latestHeartRateBpm?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
                    updateTelemetryState(
                        running = true,
                        status = "Ciclo #$loopSequence enviado para ${sendResult.nodeCount} dispositivo(s). Seq $sessionSequence. GPS ${location?.latitude?.format(5) ?: "--"}, ${location?.longitude?.format(5) ?: "--"}. BPM $bpm."
                    )
                    updateNotification("Telemetria ativa (#$loopSequence)")
                } else {
                    updateTelemetryState(
                        running = true,
                        status = "Ciclo #$loopSequence sem celular conectado. Tentativas acumuladas: $sendAttempts."
                    )
                    updateNotification("Sem celular conectado (#$loopSequence)")
                }
            }
        }
    }

    private fun buildTelemetryPayload(): String {
        val location = latestLocation
        val gpsReady = location != null
        val bpmReady = latestHeartRateBpm != null
        sessionSequence += 1

        return JSONObject()
            .put("tipo", "telemetria")
            .put("origem", "relogio")
            .put("latitude", location?.latitude ?: JSONObject.NULL)
            .put("longitude", location?.longitude ?: JSONObject.NULL)
            .put("altitude", location?.altitude ?: JSONObject.NULL)
            .put("precisaoMetros", JSONObject.NULL)
            .put("batimentoBpm", latestHeartRateBpm ?: JSONObject.NULL)
            .put("batimentoStatus", latestHeartRateAvailability)
            .put("sequenciaTelemetria", sessionSequence)
            .put("cicloTelemetria", loopSequence)
            .put("tentativasEnvio", sendAttempts)
            .put("intervaloSegundos", TELEMETRY_INTERVAL_SECONDS)
            .put("gpsPronto", gpsReady)
            .put("gpsStatus", if (gpsReady) "Disponivel" else "Aguardando GPS")
            .put("bpmPronto", bpmReady)
            .put("sessionStatus", buildWatchStatusMessage(loopSequence, "Payload criado"))
            .put("timestamp", System.currentTimeMillis())
            .toString()
    }

    private suspend fun sendPayloadToPhone(payloadJson: String): SendResult {
        sendAttempts += 1

        val nodes = Wearable.getNodeClient(this)
            .connectedNodes
            .await()

        if (nodes.isEmpty()) {
            return SendResult(sent = false, nodeCount = 0)
        }

        for (node in nodes) {
            Wearable.getMessageClient(this)
                .sendMessage(node.id, MESSAGE_PATH, payloadJson.toByteArray(Charsets.UTF_8))
                .await()
        }

        return SendResult(sent = true, nodeCount = nodes.size)
    }

    private suspend fun stopTelemetryInternal(stopService: Boolean, endExercise: Boolean = true) {
        telemetryJob?.cancel()
        telemetryJob = null

        if (endExercise) {
            runCatching { exerciseClient.endExercise() }
        }

        exerciseActive = false
        latestHeartRateBpm = null
        latestLocation = null
        latestHeartRateAvailability = "Aguardando leitura"
        loopSequence = 0
        sessionSequence = 0
        sendAttempts = 0
        updateTelemetryState(false, "Telemetria parada.")
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (stopService) {
            stopSelf()
        }
    }

    private fun startTelemetryForeground() {
        createNotificationChannel()
        val notification = buildNotification("Preparando telemetria...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Telemetry Watch")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Parar", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Telemetry Session",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun updateTelemetryState(running: Boolean, status: String) {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        preferences.edit()
            .putBoolean(PREF_TELEMETRY_RUNNING, running)
            .putString(PREF_TELEMETRY_STATUS, status)
            .apply()

        sendBroadcast(
            Intent(ACTION_TELEMETRY_STATE_CHANGED).apply {
                setPackage(packageName)
            }
        )
    }

    private fun Double.format(decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", this)
    }

    private fun buildWatchStatusMessage(cycle: Int, phase: String): String {
        val gpsStatus = if (latestLocation == null) "GPS aguardando" else "GPS pronto"
        val bpmStatus = latestHeartRateBpm?.let { "BPM ${String.format(Locale.US, "%.0f", it)}" }
            ?: "BPM aguardando"
        return "Ciclo #$cycle. $phase. $gpsStatus. $bpmStatus. Tentativas: $sendAttempts."
    }

    companion object {
        private const val ACTION_START = "com.example.datalayertest.action.START_TELEMETRY"
        private const val ACTION_STOP = "com.example.datalayertest.action.STOP_TELEMETRY"
        const val ACTION_TELEMETRY_STATE_CHANGED =
            "com.example.datalayertest.action.TELEMETRY_STATE_CHANGED"
        private const val MESSAGE_PATH = "/telemetry"
        private const val TELEMETRY_INTERVAL_SECONDS = 5
        private const val TELEMETRY_INTERVAL_MS = TELEMETRY_INTERVAL_SECONDS * 1000L
        private const val NOTIFICATION_CHANNEL_ID = "telemetry_session_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "telemetry_watch_prefs"
        private const val PREF_TELEMETRY_RUNNING = "pref_telemetry_running"
        private const val PREF_TELEMETRY_STATUS = "pref_telemetry_status"

        fun createStartIntent(context: Context): Intent =
            Intent(context, TelemetrySessionService::class.java).setAction(ACTION_START)

        fun createStopIntent(context: Context): Intent =
            Intent(context, TelemetrySessionService::class.java).setAction(ACTION_STOP)

        fun isTelemetryRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_TELEMETRY_RUNNING, false)

        fun loadTelemetryStatus(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_TELEMETRY_STATUS, "") ?: ""
    }

    private data class SendResult(
        val sent: Boolean,
        val nodeCount: Int
    )
}
