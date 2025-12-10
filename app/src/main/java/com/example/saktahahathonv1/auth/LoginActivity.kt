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

class LoginActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    private lateinit var inputEmail: TextInputEditText
    private lateinit var inputPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvRegister: TextView
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager(this)

        // Проверяем авторизацию
        if (authManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        inputEmail = findViewById(R.id.inputEmail)
        inputPassword = findViewById(R.id.inputPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            login()
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Функция восстановления пароля в разработке", Toast.LENGTH_SHORT).show()
        }
    }

    private fun login() {
        val emailOrPhone = inputEmail.text.toString().trim()
        val password = inputPassword.text.toString()

        // Показываем прогресс
        btnLogin.isEnabled = false
        btnLogin.text = "Вход..."

        when (val result = authManager.login(emailOrPhone, password)) {
            is AuthResult.Success -> {
                Toast.makeText(
                    this,
                    "Добро пожаловать, ${result.user.name}!",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMain()
            }
            is AuthResult.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                btnLogin.isEnabled = true
                btnLogin.text = "Войти"
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
