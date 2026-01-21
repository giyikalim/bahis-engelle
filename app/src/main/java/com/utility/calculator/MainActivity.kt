package com.utility.calculator

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.utility.calculator.admin.DeviceAdminReceiver
import com.utility.calculator.service.BlockerVpnService

/**
 * Ana ekran - Normal bir hesap makinesi gibi görünür
 * Gizli aktivasyon: "159753" yazıp "=" basınca koruma paneli açılır
 */
class MainActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var resultDisplay: TextView
    private var currentInput = StringBuilder()
    private var secretCode = ""

    companion object {
        private const val SECRET_ACTIVATION_CODE = "159753"
        private const val VPN_REQUEST_CODE = 100
        private const val ADMIN_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        display = findViewById(R.id.display)
        resultDisplay = findViewById(R.id.resultDisplay)

        setupCalculatorButtons()

        // Uygulama açıldığında korumayı otomatik başlat (ilk kurulumdan sonra)
        if (isProtectionEnabled()) {
            startProtection()
        }
    }

    private fun setupCalculatorButtons() {
        // Rakam butonları
        val numberButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        numberButtons.forEachIndexed { index, buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                appendToDisplay(index.toString())
                secretCode += index.toString()
            }
        }

        // İşlem butonları
        findViewById<Button>(R.id.btnPlus).setOnClickListener { appendToDisplay("+"); secretCode = "" }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { appendToDisplay("-"); secretCode = "" }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { appendToDisplay("×"); secretCode = "" }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { appendToDisplay("÷"); secretCode = "" }
        findViewById<Button>(R.id.btnDot).setOnClickListener { appendToDisplay("."); secretCode = "" }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            clearDisplay()
            secretCode = ""
        }

        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            backspace()
            secretCode = ""
        }

        findViewById<Button>(R.id.btnEquals).setOnClickListener {
            if (secretCode == SECRET_ACTIVATION_CODE) {
                openSecretPanel()
                secretCode = ""
            } else {
                calculateResult()
            }
            secretCode = ""
        }
    }

    private fun appendToDisplay(value: String) {
        currentInput.append(value)
        display.text = currentInput.toString()
    }

    private fun clearDisplay() {
        currentInput.clear()
        display.text = "0"
        resultDisplay.text = ""
    }

    private fun backspace() {
        if (currentInput.isNotEmpty()) {
            currentInput.deleteCharAt(currentInput.length - 1)
            display.text = if (currentInput.isEmpty()) "0" else currentInput.toString()
        }
    }

    private fun calculateResult() {
        try {
            val expression = currentInput.toString()
                .replace("×", "*")
                .replace("÷", "/")

            val result = evaluateExpression(expression)
            resultDisplay.text = "= $result"
        } catch (e: Exception) {
            resultDisplay.text = "Hata"
        }
    }

    private fun evaluateExpression(expr: String): Double {
        // Basit hesap makinesi mantığı
        return try {
            val cleanExpr = expr.replace(" ", "")
            when {
                cleanExpr.contains("+") -> {
                    val parts = cleanExpr.split("+")
                    parts.sumOf { evaluateExpression(it) }
                }
                cleanExpr.contains("-") && cleanExpr.indexOf("-") > 0 -> {
                    val idx = cleanExpr.lastIndexOf("-")
                    evaluateExpression(cleanExpr.substring(0, idx)) - evaluateExpression(cleanExpr.substring(idx + 1))
                }
                cleanExpr.contains("*") -> {
                    val parts = cleanExpr.split("*")
                    parts.map { evaluateExpression(it) }.reduce { acc, d -> acc * d }
                }
                cleanExpr.contains("/") -> {
                    val parts = cleanExpr.split("/")
                    parts.map { evaluateExpression(it) }.reduce { acc, d -> acc / d }
                }
                else -> cleanExpr.toDouble()
            }
        } catch (e: Exception) {
            0.0
        }
    }

    // ==================== GİZLİ PANEL ====================

    private fun openSecretPanel() {
        // Gizli kontrol panelini aç
        val intent = Intent(this, SecretPanelActivity::class.java)
        startActivity(intent)
    }

    private fun isProtectionEnabled(): Boolean {
        val prefs = getSharedPreferences("calc_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("protection_enabled", false)
    }

    private fun startProtection() {
        // VPN servisini başlat
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, BlockerVpnService::class.java)
        startForegroundService(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            VPN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    startVpnService()
                }
            }
            ADMIN_REQUEST_CODE -> {
                // Device admin aktivasyonu
            }
        }
    }
}
