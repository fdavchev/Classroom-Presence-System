package com.example.studentapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus      = findViewById<TextView>(R.id.tvStatus)
        val tvStudentInfo = findViewById<TextView>(R.id.tvStudentInfo)

        // Read the student's info that was saved during login
        val prefs      = getSharedPreferences("STUDENT_PREFS", MODE_PRIVATE)
        val studentId  = prefs.getString("student_id", "") ?: ""
        val name       = prefs.getString("student_name", "") ?: ""
        val course     = prefs.getString("course_enrolled", "") ?: ""
        val authToken  = prefs.getString("auth_token", "") ?: ""

        if (studentId.isEmpty()) {
            // Something went wrong — no login data found
            tvStatus.text = "Error: not logged in. Please restart the app."
            return
        }

        // Build the JSON payload that the teacher's phone will receive.
        // This exact format is what the backend's NfcPayload schema expects.
        val payload = """{"student_id":"$studentId","student_name":"$name","course_enrolled":"$course","auth_token":"$authToken","version":"1.0"}"""

        // Write it to the shared HceService variable.
        // From this moment on, any NFC scan will return this data.
        HceService.studentPayload = payload

        // Show the student's info on screen
        tvStudentInfo.text = "👤  $name\n🆔  $studentId\n📚  $course"
        tvStatus.text = "✅  Ready — hold phone near teacher's device to check in"
    }
}