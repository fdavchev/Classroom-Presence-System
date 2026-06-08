package com.example.studentapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// ─── Minimal API definitions (only what the student app needs) ───────────────

data class StudentLoginRequest(val email: String, val password: String)

data class StudentLoginResponse(
    val user_id: String,
    val email: String,
    val role: String,
    val server_token: String
)

interface StudentApiService {
    @POST("login")
    suspend fun login(@Body request: StudentLoginRequest): retrofit2.Response<StudentLoginResponse>
}

// ─── The login screen ────────────────────────────────────────────────────────

class LoginActivity : AppCompatActivity() {

    // ⚠️ Must match your computer's IP — same as teacher app
    private val BASE_URL = "http://YOUR_COMPUTER_IP:8000/api/"

    private val apiService: StudentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StudentApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail    = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etName     = findViewById<EditText>(R.id.etStudentName)
        val etCourse   = findViewById<EditText>(R.id.etCourse)
        val btnLogin   = findViewById<Button>(R.id.btnLogin)
        val tvStatus   = findViewById<TextView>(R.id.tvLoginStatus)

        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val name     = etName.text.toString().trim()
            val course   = etCourse.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Fill in email, password, and name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            tvStatus.text = "Logging in…"

            lifecycleScope.launch {
                val result = runCatching {
                    apiService.login(StudentLoginRequest(email, password))
                }.getOrNull()

                if (result?.isSuccessful == true && result.body() != null) {
                    val body = result.body()!!

                    // Save everything to SharedPreferences (the phone's tiny local storage)
                    // SharedPreferences is like a sticky note — it survives app restarts
                    val prefs = getSharedPreferences("STUDENT_PREFS", MODE_PRIVATE)
                    prefs.edit()
                        .putString("student_id",    body.user_id)   // Firebase UID
                        .putString("student_name",  name)
                        .putString("course_enrolled", course)
                        .putString("auth_token",    body.user_id)   // same UID = proof of identity
                        .apply()

                    // Go to the main "ready to tap" screen
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish() // close this screen so Back button doesn't return here
                } else {
                    tvStatus.text = "Login failed. Check credentials and server."
                    btnLogin.isEnabled = true
                }
            }
        }
    }
}