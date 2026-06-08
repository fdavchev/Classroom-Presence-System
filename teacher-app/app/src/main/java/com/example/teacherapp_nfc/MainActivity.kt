package com.example.teacherapp_nfc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.teacherapp_nfc.data.AppDatabase
import com.example.teacherapp_nfc.data.AttendanceRecord
import com.example.teacherapp_nfc.data.AttendanceSession
import com.example.teacherapp_nfc.network.ApiClient
import com.example.teacherapp_nfc.repository.AttendanceRepository
import org.json.JSONObject
import java.nio.charset.Charset
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var foregroundDispatchIntent: PendingIntent
    private lateinit var repository: AttendanceRepository

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var classInput: EditText
    private lateinit var statusText: TextView
    private lateinit var sessionText: TextView
    private lateinit var lastTagText: TextView
    private lateinit var syncText: TextView

    private var teacherId: String? = null
    private var teacherEmail: String? = null
    private var activeSession: AttendanceSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailInput  = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        classInput  = findViewById(R.id.classInput)
        statusText  = findViewById(R.id.statusText)
        sessionText = findViewById(R.id.sessionText)
        lastTagText = findViewById(R.id.lastTagText)
        syncText    = findViewById(R.id.syncText)

        repository = AttendanceRepository(
            AppDatabase.getInstance(applicationContext).attendanceDao(),
            ApiClient.nfcApiService
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        foregroundDispatchIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        findViewById<Button>(R.id.loginButton).setOnClickListener { login() }
        findViewById<Button>(R.id.startSessionButton).setOnClickListener { startSession() }
        findViewById<Button>(R.id.syncButton).setOnClickListener { syncNow() }
        findViewById<Button>(R.id.endSessionButton).setOnClickListener { endSession() }

        renderState()
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (activeSession != null && nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, foregroundDispatchIntent, null, null)
        }
        renderState()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    // ── STEP 1: Login with email + password only ─────────────────────────────
private fun login() {
    val email    = emailInput.text.toString().trim()
    val password = passwordInput.text.toString()

    if (email.isBlank() || password.isBlank()) {
        Toast.makeText(this, "Enter email and password.", Toast.LENGTH_SHORT).show()
        return
    }

    findViewById<Button>(R.id.loginButton).isEnabled = false
    statusText.text = "Logging in…"

    // Step 1: Sign in with Firebase using email + password
    com.google.firebase.auth.FirebaseAuth.getInstance()
        .signInWithEmailAndPassword(email, password)
        .addOnSuccessListener { result ->
            // Step 2: Get the Firebase ID token
            result.user?.getIdToken(false)
                ?.addOnSuccessListener { tokenResult ->
                    val firebaseToken = tokenResult.token
                    if (firebaseToken == null) {
                        Toast.makeText(this, "Failed to get Firebase token.", Toast.LENGTH_LONG).show()
                        findViewById<Button>(R.id.loginButton).isEnabled = true
                        statusText.text = getString(R.string.login_required)
                        return@addOnSuccessListener
                    }
                    // Step 3: Send token to your backend
                    lifecycleScope.launch {
                        val loginResult = repository.loginTeacher(firebaseToken)
                        if (loginResult == null) {
                            Toast.makeText(
                                this@MainActivity,
                                "Login failed. Check credentials and that the server is running.",
                                Toast.LENGTH_LONG
                            ).show()
                            statusText.text = getString(R.string.login_required)
                            findViewById<Button>(R.id.loginButton).isEnabled = true
                            return@launch
                        }
                        teacherId    = loginResult.user_id
                        teacherEmail = loginResult.email
                        passwordInput.text.clear()
                        classInput.isEnabled = true
                        findViewById<Button>(R.id.startSessionButton).isEnabled = true
                        statusText.text = "Logged in as ${loginResult.email}. Now enter class name."
                        sessionText.text = getString(R.string.session_inactive)
                    }
                }
                ?.addOnFailureListener {
                    Toast.makeText(this, "Firebase error: ${it.message}", Toast.LENGTH_LONG).show()
                    findViewById<Button>(R.id.loginButton).isEnabled = true
                    statusText.text = getString(R.string.login_required)
                }
        }
        .addOnFailureListener {
            // Wrong email or password
            Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
            findViewById<Button>(R.id.loginButton).isEnabled = true
            statusText.text = getString(R.string.login_required)
        }
}

    // ── STEP 2: Teacher types class name and starts the session ──────────────
    private fun startSession() {
        val className = classInput.text.toString().trim()
        if (className.isBlank()) {
            Toast.makeText(this, "Enter a class name first.", Toast.LENGTH_SHORT).show()
            return
        }

        val email = teacherEmail ?: return
        lifecycleScope.launch {
            activeSession = repository.startSession(email, className)
            classInput.isEnabled = false
            findViewById<Button>(R.id.startSessionButton).isEnabled = false
            renderState()
            syncNow()
        }
    }

    // ── END SESSION ──────────────────────────────────────────────────────────
    private fun endSession() {
        val session = activeSession ?: return
        lifecycleScope.launch {
            activeSession = repository.endSession(session)
            nfcAdapter?.disableForegroundDispatch(this@MainActivity)
            // Allow teacher to start a new session with a different class
            classInput.isEnabled = true
            classInput.text.clear()
            findViewById<Button>(R.id.startSessionButton).isEnabled = true
            renderState()
        }
    }

    // ── SYNC ─────────────────────────────────────────────────────────────────
    private fun syncNow() {
        val session = activeSession
        if (session == null) {
            syncText.text = getString(R.string.sync_no_session)
            return
        }
        lifecycleScope.launch {
            val syncedCount = repository.syncPendingRecords(session)
            syncText.text = getString(R.string.sync_result, syncedCount)
        }
    }

    // ── RENDER STATE ─────────────────────────────────────────────────────────
    private fun renderState() {
        statusText.text = when {
            teacherId == null                 -> getString(R.string.login_required)
            activeSession == null             -> "Logged in. Enter class name to start."
            nfcAdapter == null                -> getString(R.string.nfc_missing)
            nfcAdapter?.isEnabled != true     -> getString(R.string.nfc_disabled)
            else                              -> getString(R.string.nfc_ready)
        }

        sessionText.text = activeSession?.let {
            getString(R.string.session_active, it.className, it.teacherId)
        } ?: getString(R.string.session_inactive)

        findViewById<Button>(R.id.endSessionButton).isEnabled = activeSession != null
        findViewById<Button>(R.id.syncButton).isEnabled       = activeSession != null

        if (activeSession != null && nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, foregroundDispatchIntent, null, null)
        }
    }

    // ── NFC HANDLING ─────────────────────────────────────────────────────────
    private fun handleNfcIntent(intent: Intent?) {
        val session = activeSession ?: return
        if (intent == null) return
        val action = intent.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED) return

        val payload = parseNdefPayload(intent)
        val parsed  = parseAttendancePayload(payload)
        if (parsed == null) {
            lastTagText.text = getString(R.string.no_tag)
            return
        }

        val record = AttendanceRecord(
            sessionId   = session.id,
            teacherId   = session.teacherId,
            className   = session.className,
            studentId   = parsed.studentId,
            studentName = parsed.studentName,
            timestamp   = System.currentTimeMillis(),
            rawPayload  = payload
        )

        lifecycleScope.launch {
            repository.saveRecord(record)
            val syncedCount = repository.syncPendingRecords(activeSession)
            val syncedNow   = syncedCount > 0 && isOnline()
            triggerFeedback()
            lastTagText.text = if (syncedNow)
                getString(R.string.tap_saved_synced, parsed.studentName, parsed.studentId)
            else
                getString(R.string.tap_saved_local, parsed.studentName, parsed.studentId)
            syncText.text = getString(R.string.sync_result, syncedCount)
        }
    }

    private fun parseNdefPayload(intent: Intent): String {
        return getNdefMessages(intent)
            .flatMap { it.records.asIterable() }
            .mapNotNull { parseRecord(it) }
            .joinToString(separator = "\n")
    }

    private fun getNdefMessages(intent: Intent): List<NdefMessage> {
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        return rawMessages?.mapNotNull { it as? NdefMessage }.orEmpty()
    }

    private fun parseAttendancePayload(rawPayload: String): ParsedAttendance? {
        val normalized = rawPayload.trim()
        if (normalized.isEmpty()) return null
        runCatching {
            val json        = JSONObject(normalized)
            val studentId   = json.optString("student_id").ifBlank { json.optString("studentId") }
            val studentName = json.optString("student_name").ifBlank { json.optString("studentName") }
            if (studentId.isNotBlank() && studentName.isNotBlank())
                return ParsedAttendance(studentId, studentName)
        }
        val pipeParts = normalized.split("|", ",", ";").map { it.trim() }.filter { it.isNotEmpty() }
        if (pipeParts.size >= 2) return ParsedAttendance(pipeParts[0], pipeParts[1])
        return ParsedAttendance(normalized, normalized)
    }

    private fun parseRecord(record: NdefRecord): String? {
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> parseTextRecord(record)
            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_URI)  -> parseUriRecord(record)
            record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                "${record.type.toString(Charsets.US_ASCII)}: ${record.payload.toString(Charsets.UTF_8)}"
            }
            else -> record.payload.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8)
        }
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        val payload = record.payload
        if (payload.isEmpty()) return null
        val isUtf16 = payload[0].toInt() and 0x80 != 0
        val langLen = payload[0].toInt() and 0x3F
        val charset = if (isUtf16) Charset.forName("UTF-16") else Charsets.UTF_8
        return payload.copyOfRange(1 + langLen, payload.size).toString(charset)
    }

    private fun parseUriRecord(record: NdefRecord): String {
        val payload = record.payload
        if (payload.isEmpty()) return ""
        val prefixes = arrayOf(
            "", "http://www.", "https://www.", "http://", "https://",
            "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.",
            "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
            "dav://", "news:", "telnet://", "imap:", "rtsp://",
            "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://",
            "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://",
            "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:",
            "urn:epc:raw:", "urn:epc:", "urn:nfc:"
        )
        val prefix = prefixes.getOrElse(payload[0].toInt()) { "" }
        return prefix + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
    }

    private fun triggerFeedback() {
        Toast.makeText(this, getString(R.string.student_registered), Toast.LENGTH_SHORT).show()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    }

    private fun isOnline(): Boolean {
        val mgr  = getSystemService(ConnectivityManager::class.java)
        val net  = mgr.activeNetwork ?: return false
        val caps = mgr.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private data class ParsedAttendance(val studentId: String, val studentName: String)
}