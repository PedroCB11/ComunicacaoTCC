package com.example.datalayertest

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var toggleButton: Button

    private val telemetryStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncUiFromStoredState()
        }
    }

    private val requestBasePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val baseGranted = requiredBasePermissions().all { permissions[it] == true || hasPermission(it) }
        if (!baseGranted) {
            updateStatus("Permissoes de localizacao e batimento sao necessarias.")
            return@registerForActivityResult
        }

        if (needsBackgroundHealthPermission() && !hasPermission(BACKGROUND_HEALTH_PERMISSION)) {
            requestBackgroundHealthPermission.launch(BACKGROUND_HEALTH_PERMISSION)
        } else {
            iniciarTelemetria()
        }
    }

    private val requestBackgroundHealthPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            iniciarTelemetria()
        } else {
            updateStatus("Permissao em segundo plano negada. Ative nas configuracoes para manter a medicao com a tela apagada.")
            syncUiFromStoredState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        syncUiFromStoredState()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            telemetryStateReceiver,
            IntentFilter(TelemetrySessionService.ACTION_TELEMETRY_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED
        )
        syncUiFromStoredState()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(telemetryStateReceiver)
    }

    private fun createContentView(): LinearLayout {
        val padding = (16 * resources.displayMetrics.density).toInt()

        titleTextView = TextView(this).apply {
            text = "Watch Sender Telemetry"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusTextView = TextView(this).apply {
            text = "Toque para iniciar a telemetria."
            textSize = 14f
            setTextColor(Color.parseColor("#D6E4FF"))
            gravity = Gravity.CENTER
        }

        toggleButton = Button(this).apply {
            setOnClickListener {
                if (TelemetrySessionService.isTelemetryRunning(this@MainActivity)) {
                    pararTelemetria()
                } else if (hasAllRequiredPermissions()) {
                    iniciarTelemetria()
                } else {
                    requestBasePermissions.launch(requiredBasePermissions())
                }
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
                toggleButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun hasAllRequiredPermissions(): Boolean {
        val baseGranted = requiredBasePermissions().all(::hasPermission)
        val backgroundGranted = !needsBackgroundHealthPermission() || hasPermission(BACKGROUND_HEALTH_PERMISSION)
        return baseGranted && backgroundGranted
    }

    private fun requiredBasePermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            if (Build.VERSION.SDK_INT >= 36) HEART_RATE_PERMISSION else Manifest.permission.BODY_SENSORS
        )
    }

    private fun needsBackgroundHealthPermission(): Boolean {
        return Build.VERSION.SDK_INT >= 36
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun iniciarTelemetria() {
        ContextCompat.startForegroundService(
            this,
            TelemetrySessionService.createStartIntent(this)
        )
        updateStatus("Iniciando telemetria em segundo plano...")
        syncUiFromStoredState()
    }

    private fun pararTelemetria() {
        startService(TelemetrySessionService.createStopIntent(this))
        updateStatus("Solicitando parada da telemetria...")
        syncUiFromStoredState()
    }

    private fun syncUiFromStoredState() {
        val running = TelemetrySessionService.isTelemetryRunning(this)
        val status = TelemetrySessionService.loadTelemetryStatus(this)

        toggleButton.text = if (running) "Parar telemetria" else "Iniciar telemetria"
        statusTextView.text = status.ifBlank {
            if (running) {
                "Telemetria em andamento."
            } else {
                "Toque para iniciar a telemetria."
            }
        }
    }

    private fun updateStatus(message: String) {
        statusTextView.text = message
    }

    companion object {
        private const val HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
        private const val BACKGROUND_HEALTH_PERMISSION =
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}
