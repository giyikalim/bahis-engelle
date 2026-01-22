package com.utility.calculator

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.utility.calculator.data.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kullanıcı Kayıt Ekranı
 *
 * Koruma ilk kez aktifleştirildiğinde gösterilir.
 * Ad, soyad ve email bilgilerini alır.
 */
class RegistrationActivity : AppCompatActivity() {

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etDeviceName: EditText
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var userRepository: UserRepository

    companion object {
        const val RESULT_REGISTERED = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        userRepository = UserRepository(this)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etDeviceName = findViewById(R.id.etDeviceName)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
    }

    private fun setupListeners() {
        btnRegister.setOnClickListener {
            if (validateForm()) {
                registerUser()
            }
        }
    }

    private fun validateForm(): Boolean {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        // Ad kontrolü
        if (firstName.isEmpty()) {
            showError("Ad alanı boş bırakılamaz")
            etFirstName.requestFocus()
            return false
        }

        if (firstName.length < 2) {
            showError("Ad en az 2 karakter olmalıdır")
            etFirstName.requestFocus()
            return false
        }

        // Soyad kontrolü
        if (lastName.isEmpty()) {
            showError("Soyad alanı boş bırakılamaz")
            etLastName.requestFocus()
            return false
        }

        if (lastName.length < 2) {
            showError("Soyad en az 2 karakter olmalıdır")
            etLastName.requestFocus()
            return false
        }

        // Email kontrolü
        if (email.isEmpty()) {
            showError("Email alanı boş bırakılamaz")
            etEmail.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Geçerli bir email adresi girin")
            etEmail.requestFocus()
            return false
        }

        hideError()
        return true
    }

    private fun registerUser() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim().ifEmpty { null }
        val deviceName = etDeviceName.text.toString().trim().ifEmpty { null }

        setLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = userRepository.registerUser(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phone = phone,
                    deviceName = deviceName
                )

                result.fold(
                    onSuccess = { userId ->
                        Toast.makeText(
                            this@RegistrationActivity,
                            "Kayıt başarılı!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // SecretPanelActivity'ye dön ve korumayı başlat
                        setResult(RESULT_REGISTERED)
                        finish()
                    },
                    onFailure = { error ->
                        showError("Kayıt hatası: ${error.message}")
                        setLoading(false)
                    }
                )
            } catch (e: Exception) {
                showError("Bağlantı hatası: ${e.message}")
                setLoading(false)
            }
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !loading
        etFirstName.isEnabled = !loading
        etLastName.isEnabled = !loading
        etEmail.isEnabled = !loading
        etPhone.isEnabled = !loading
        etDeviceName.isEnabled = !loading
    }

    override fun onBackPressed() {
        // Kayıt yapılmadan geri dönülemez
        Toast.makeText(this, "Korumayı aktifleştirmek için kayıt gerekli", Toast.LENGTH_SHORT).show()
    }
}
