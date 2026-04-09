package com.example.datalayertest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var textView: TextView

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedText = intent?.getStringExtra(PhoneMessageListenerService.EXTRA_MESSAGE_TEXT)
            val receivedCount = intent?.getIntExtra(PhoneMessageListenerService.EXTRA_RECEIVED_COUNT, 0) ?: 0
            updateMessageText(receivedText, receivedCount)
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
        updateMessageText(loadLastMessage(), loadReceivedCount())
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(
            messageReceiver,
            IntentFilter(PhoneMessageListenerService.ACTION_MESSAGE_UPDATED),
            RECEIVER_NOT_EXPORTED
        )

        updateMessageText(loadLastMessage(), loadReceivedCount())
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(messageReceiver)
    }

    private fun loadLastMessage(): String? {
        return getSharedPreferences(PhoneMessageListenerService.PREFS_NAME, MODE_PRIVATE)
            .getString(PhoneMessageListenerService.PREF_LAST_MESSAGE, null)
    }

    private fun loadReceivedCount(): Int {
        return getSharedPreferences(PhoneMessageListenerService.PREFS_NAME, MODE_PRIVATE)
            .getInt(PhoneMessageListenerService.PREF_RECEIVED_COUNT, 0)
    }

    private fun updateMessageText(receivedText: String?, receivedCount: Int) {
        textView.text = if (receivedText.isNullOrBlank()) {
            "Aguardando JSON do relogio...\n\nRecebimentos: $receivedCount"
        } else {
            "Recebimentos: $receivedCount\n\nUltimo JSON recebido do relogio:\n$receivedText"
        }
    }
}
