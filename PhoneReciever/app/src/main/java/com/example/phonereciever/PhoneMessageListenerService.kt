package com.example.datalayertest

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class PhoneMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path != MESSAGE_PATH) {
            return
        }

        val receivedText = messageEvent.data.toString(Charsets.UTF_8)
        val displayText = try {
            val payload = JSONObject(receivedText)
            val latitude = payload.opt("latitude")
            val longitude = payload.opt("longitude")
            val altitude = payload.opt("altitude")
            val accuracy = payload.opt("precisaoMetros")
            val heartRate = payload.opt("batimentoBpm")
            val heartRateStatus = payload.optString("batimentoStatus")
            val telemetrySequence = payload.optInt("sequenciaTelemetria")
            val telemetryCycle = payload.optInt("cicloTelemetria")
            val sendAttempts = payload.optInt("tentativasEnvio")
            val gpsStatus = payload.optString("gpsStatus")
            val sessionStatus = payload.optString("sessionStatus")
            val timestamp = payload.optLong("timestamp")

            buildString {
                appendLine("Status da sessao: $sessionStatus")
                appendLine("Ciclo atual: $telemetryCycle")
                appendLine("Latitude: $latitude")
                appendLine("Longitude: $longitude")
                appendLine("Altitude: $altitude")
                appendLine("Status do GPS: $gpsStatus")
                appendLine("Precisao (m): $accuracy")
                appendLine("Sequencia da sessao: $telemetrySequence")
                appendLine("Tentativas de envio: $sendAttempts")
                appendLine("Batimento (BPM): $heartRate")
                appendLine("Status do batimento: $heartRateStatus")
                append("Timestamp: $timestamp")
            }
        } catch (_: Exception) {
            receivedText
        }

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val receivedCount = preferences.getInt(PREF_RECEIVED_COUNT, 0) + 1
        val receivedAt = System.currentTimeMillis()

        preferences.edit()
            .putString(PREF_LAST_MESSAGE, displayText)
            .putInt(PREF_RECEIVED_COUNT, receivedCount)
            .putLong(PREF_LAST_RECEIVED_AT, receivedAt)
            .apply()

        sendBroadcast(
            Intent(ACTION_MESSAGE_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE_TEXT, displayText)
                putExtra(EXTRA_RECEIVED_COUNT, receivedCount)
                putExtra(EXTRA_LAST_RECEIVED_AT, receivedAt)
            }
        )
    }

    companion object {
        const val MESSAGE_PATH = "/telemetry"
        const val PREFS_NAME = "wear_message_prefs"
        const val PREF_LAST_MESSAGE = "pref_last_message"
        const val PREF_RECEIVED_COUNT = "pref_received_count"
        const val PREF_LAST_RECEIVED_AT = "pref_last_received_at"
        const val ACTION_MESSAGE_UPDATED = "com.example.datalayertest.ACTION_MESSAGE_UPDATED"
        const val EXTRA_MESSAGE_TEXT = "extra_message_text"
        const val EXTRA_RECEIVED_COUNT = "extra_received_count"
        const val EXTRA_LAST_RECEIVED_AT = "extra_last_received_at"
    }
}
