package com.example.saktahahathonv1.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.saktahahathonv1.MainActivity
import com.example.saktahahathonv1.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView

class RegisterActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    private lateinit var inputName: TextInputEditText
    private lateinit var inputEmail: TextInputEditText
    private lateinit var inputPhone: TextInputEditText
    private lateinit var inputPassword: TextInputEditText
    private lateinit var inputConfirmPassword: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        authManager = AuthManager(this)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        inputName = findViewById(R.id.inputName)
        inputEmail = findViewById(R.id.inputEmail)
        inputPhone = findViewById(R.id.inputPhone)
        inputPassword = findViewById(R.id.inputPassword)
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)
    }

    private fun setupListeners() {
        btnRegister.setOnClickListener {
            register()
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun register() {
        val name = inputName.text.toString().trim()
        val email = inputEmail.text.toString().trim()
        val phone = "+996" + inputPhone.text.toString().trim()
        val password = inputPassword.text.toString()
        val confirmPassword = inputConfirmPassword.text.toString()

        // Проверка совпадения паролей
        if (password != confirmPassword) {
            Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
            return
        }

        // Показываем прогресс
        btnRegister.isEnabled = false
        btnRegister.text = "Регистрация..."

        when (val result = authManager.register(name, email, phone, password)) {
            is AuthResult.Success -> {
                Toast.makeText(
                    this,
                    "Регистрация успешна! Добро пожаловать, ${result.user.name}!",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMain()
            }
            is AuthResult.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                btnRegister.isEnabled = true
                btnRegister.text = "Зарегистрироваться"
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
