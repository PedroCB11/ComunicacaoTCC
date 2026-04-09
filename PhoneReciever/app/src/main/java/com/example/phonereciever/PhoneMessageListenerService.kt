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
        val formattedJson = try {
            JSONObject(receivedText).toString(2)
        } catch (_: Exception) {
            receivedText
        }

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val receivedCount = preferences.getInt(PREF_RECEIVED_COUNT, 0) + 1

        preferences.edit()
            .putString(PREF_LAST_MESSAGE, formattedJson)
            .putInt(PREF_RECEIVED_COUNT, receivedCount)
            .apply()

        sendBroadcast(
            Intent(ACTION_MESSAGE_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE_TEXT, formattedJson)
                putExtra(EXTRA_RECEIVED_COUNT, receivedCount)
            }
        )
    }

    companion object {
        const val MESSAGE_PATH = "/json"
        const val PREFS_NAME = "wear_message_prefs"
        const val PREF_LAST_MESSAGE = "pref_last_message"
        const val PREF_RECEIVED_COUNT = "pref_received_count"
        const val ACTION_MESSAGE_UPDATED = "com.example.datalayertest.ACTION_MESSAGE_UPDATED"
        const val EXTRA_MESSAGE_TEXT = "extra_message_text"
        const val EXTRA_RECEIVED_COUNT = "extra_received_count"
    }
}
