package com.example.studentapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class StudentLoginRequest(
    val email: String,
    val password: String,
    val firebase_id_token: String
)

// Use JsonObject so we can safely read whatever the backend returns
interface StudentApiService {
    @POST("api/login")
    suspend fun login(@Body request: StudentLoginRequest): Response<JsonObject>
}

class LoginActivity : AppCompatActivity() {

    // ⚠️  Change this to your actual backend IP and port
    private val BASE_URL = "http://YOUR_IP/8000/"

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etStudentName: EditText
    private lateinit var etCourse: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvStatus: TextView

    private lateinit var apiService: StudentApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in, skip straight to MainActivity
        val prefs = getSharedPreferences("STUDENT_PREFS", MODE_PRIVATE)
        if (!prefs.getString("student_id", "").isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail       = findViewById(R.id.etEmail)
        etPassword    = findViewById(R.id.etPassword)
        etStudentName = findViewById(R.id.etStudentName)
        etCourse      = findViewById(R.id.etCourse)
        btnLogin      = findViewById(R.id.btnLogin)
        tvStatus      = findViewById(R.id.tvLoginStatus)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(StudentApiService::class.java)

        btnLogin.setOnClickListener {
            val email       = etEmail.text.toString().trim()
            val password    = etPassword.text.toString().trim()
            val localName   = etStudentName.text.toString().trim()
            val localCourse = etCourse.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                tvStatus.text = "Please enter both email and password."
                return@setOnClickListener
            }

            tvStatus.text = "Logging in…"
            btnLogin.isEnabled = false

            lifecycleScope.launch {
                try {
                    // Step 1 – Firebase sign-in
                    val authResult = FirebaseAuth.getInstance()
                        .signInWithEmailAndPassword(email, password)
                        .await()

                    val tokenResult = authResult.user?.getIdToken(true)?.await()
                    val firebaseToken = tokenResult?.token

                    if (firebaseToken == null) {
                        tvStatus.text = "Firebase token generation failed."
                        btnLogin.isEnabled = true
                        return@launch
                    }

                    // Step 2 – Call your backend
                    val response = apiService.login(
                        StudentLoginRequest(email, password, firebaseToken)
                    )

                    Log.d("LOGIN_DEBUG", "HTTP ${response.code()}: ${response.body()}")

                    if (response.isSuccessful && response.body() != null) {
                        val json = response.body()!!

                        // Grab student_id — try common field names the backend might use
                        val studentId = listOf("user_id", "student_id", "studentId", "uid", "id")
                            .firstNotNullOfOrNull { key ->
                                json.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
                            }

                        if (studentId == null) {
                            Log.e("LOGIN_DEBUG", "Backend response has no student_id. Full body: $json")
                            tvStatus.text = "Login error: server did not return a student ID.\nCheck Logcat → LOGIN_DEBUG for details."
                            btnLogin.isEnabled = true
                            return@launch
                        }

                        // Grab optional fields gracefully
                        val serverToken = json.get("token")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val role        = json.get("role")?.takeIf { !it.isJsonNull }?.asString ?: ""

                        prefs.edit().apply {
                            putString("student_id",      studentId)
                            putString("student_name",    localName.ifEmpty { "Registered Student" })
                            putString("course_enrolled", localCourse.ifEmpty { "Mobile Course" })
                            putString("auth_token",      serverToken.ifEmpty { studentId })
                            putString("role",            role)
                            apply()
                        }

                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()

                    } else {
                        val errorMsg = response.errorBody()?.string() ?: "No error body"
                        Log.e("LOGIN_DEBUG", "Backend rejected login: $errorMsg")
                        tvStatus.text = "Login failed (${response.code()}): $errorMsg"
                        btnLogin.isEnabled = true
                    }

                } catch (e: Exception) {
                    Log.e("LOGIN_DEBUG", "Exception during login", e)
                    tvStatus.text = "Error: ${e.localizedMessage}"
                    btnLogin.isEnabled = true
                }
            }
        }
    }
}
