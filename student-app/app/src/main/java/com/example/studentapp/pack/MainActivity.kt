package com.example.studentapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus      = findViewById<TextView>(R.id.tvStatus)
        val tvStudentInfo = findViewById<TextView>(R.id.tvStudentInfo)
        val btnLogout     = findViewById<Button>(R.id.btnLogout)   // add this button to your layout if you don't have it

        val prefs      = getSharedPreferences("STUDENT_PREFS", MODE_PRIVATE)
        val studentId  = prefs.getString("student_id",      "") ?: ""
        val name       = prefs.getString("student_name",    "") ?: ""
        val course     = prefs.getString("course_enrolled", "") ?: ""
        val authToken  = prefs.getString("auth_token",      "") ?: ""

        // Guard — should never trigger now, but kept as safety net
        if (studentId.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Build HCE payload
        val payload = """{"student_id":"$studentId","student_name":"$name","course_enrolled":"$course","auth_token":"$authToken","version":"1.0"}"""
        HceService.studentPayload = payload

        // Show student info
        tvStudentInfo.text = "👤  $name\n🆔  $studentId\n📚  $course"
        tvStatus.text      = "✅  Ready — hold phone near teacher's device to check in"

        // Logout button — clears saved data and goes back to login
        btnLogout.setOnClickListener {
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}