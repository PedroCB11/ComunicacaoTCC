package com.example.datalayertest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var textView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val connectionStatusUpdater = object : Runnable {
        override fun run() {
            updateMessageText(loadLastMessage(), loadReceivedCount(), loadLastReceivedAt())
            handler.postDelayed(this, CONNECTION_CHECK_INTERVAL_MS)
        }
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedText = intent?.getStringExtra(PhoneMessageListenerService.EXTRA_MESSAGE_TEXT)
            val receivedCount = intent?.getIntExtra(PhoneMessageListenerService.EXTRA_RECEIVED_COUNT, 0) ?: 0
            val lastReceivedAt = intent?.getLongExtra(PhoneMessageListenerService.EXTRA_LAST_RECEIVED_AT, 0L) ?: 0L
            updateMessageText(receivedText, receivedCount, lastReceivedAt)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textView = TextView(this).apply {
            text = "Aguardando mensagem do relogio..."
            textSize = 20f
            setPadding(40, 80, 40, 40)
        }

        setContentView(textView)
        updateMessageText(loadLastMessage(), loadReceivedCount(), loadLastReceivedAt())
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(
            messageReceiver,
            IntentFilter(PhoneMessageListenerService.ACTION_MESSAGE_UPDATED),
            RECEIVER_NOT_EXPORTED
        )

        updateMessageText(loadLastMessage(), loadReceivedCount(), loadLastReceivedAt())
        handler.post(connectionStatusUpdater)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(messageReceiver)
        handler.removeCallbacks(connectionStatusUpdater)
    }

    private fun loadLastMessage(): String? {
        return getSharedPreferences(PhoneMessageListenerService.PREFS_NAME, MODE_PRIVATE)
            .getString(PhoneMessageListenerService.PREF_LAST_MESSAGE, null)
    }

    private fun loadReceivedCount(): Int {
        return getSharedPreferences(PhoneMessageListenerService.PREFS_NAME, MODE_PRIVATE)
            .getInt(PhoneMessageListenerService.PREF_RECEIVED_COUNT, 0)
    }

    private fun loadLastReceivedAt(): Long {
        return getSharedPreferences(PhoneMessageListenerService.PREFS_NAME, MODE_PRIVATE)
            .getLong(PhoneMessageListenerService.PREF_LAST_RECEIVED_AT, 0L)
    }

    private fun updateMessageText(receivedText: String?, receivedCount: Int, lastReceivedAt: Long) {
        val connectionStatus = when {
            lastReceivedAt == 0L -> "Aguardando conexao com o relogio"
            System.currentTimeMillis() - lastReceivedAt <= CONNECTION_TIMEOUT_MS -> "Conectado ao relogio"
            else -> "Perda de conexao com o relogio"
        }

        textView.text = if (receivedText.isNullOrBlank()) {
            "Status de conexao: $connectionStatus\n\nRecebimentos: $receivedCount\n\nSem telemetria recebida ainda."
        } else {
            "Status de conexao: $connectionStatus\n\nRecebimentos: $receivedCount\n\nProgresso da telemetria:\n$receivedText"
        }
    }

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val CONNECTION_CHECK_INTERVAL_MS = 2_000L
    }
}
