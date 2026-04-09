package com.example.datalayertest

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        enviarMensagemParaCelular()
    }

    private fun createContentView(): LinearLayout {
        val padding = (16 * resources.displayMetrics.density).toInt()

        titleTextView = TextView(this).apply {
            text = "Watch Sender"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusTextView = TextView(this).apply {
            text = "Abrindo conexao com o celular..."
            textSize = 14f
            setTextColor(Color.parseColor("#D6E4FF"))
            gravity = Gravity.CENTER
        }

        sendButton = Button(this).apply {
            text = "Enviar teste"
            setOnClickListener {
                enviarMensagemParaCelular()
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101828"))
            setPadding(padding, padding, padding, padding)
            addView(
                titleTextView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                statusTextView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = padding / 2
                    bottomMargin = padding
                }
            )
            addView(
                sendButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun enviarMensagemParaCelular() {
        sendButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                updateStatus("Tentando enviar mensagem para o celular...")

                val payloadJson = assets
                    .open(JSON_FILE_NAME)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                repeat(5) { tentativa ->
                    val nodes = Wearable.getNodeClient(this@MainActivity)
                        .connectedNodes
                        .await()

                    if (nodes.isNotEmpty()) {
                        for (node in nodes) {
                            Wearable.getMessageClient(this@MainActivity)
                                .sendMessage(
                                    node.id,
                                    MESSAGE_PATH,
                                    payloadJson.toByteArray(Charsets.UTF_8)
                                )
                                .await()
                        }

                        updateStatus("Mensagem enviada com sucesso para ${nodes.size} dispositivo(s).")
                        return@launch
                    }

                    updateStatus("Aguardando celular... tentativa ${tentativa + 1}/5")
                    delay(1_500)
                }

                updateStatus("Nenhum celular conectado encontrado.")
            } catch (e: Exception) {
                updateStatus("Erro ao enviar: ${e.message}")
            } finally {
                runOnUiThread {
                    sendButton.isEnabled = true
                }
            }
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
        }
    }

    companion object {
        private const val MESSAGE_PATH = "/json"
        private const val JSON_FILE_NAME = "watch_test_payload.json"
    }
}
