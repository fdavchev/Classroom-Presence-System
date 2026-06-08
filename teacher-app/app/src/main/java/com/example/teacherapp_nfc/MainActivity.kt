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
    private var activeSession: AttendanceSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        classInput = findViewById(R.id.classInput)
        statusText = findViewById(R.id.statusText)
        sessionText = findViewById(R.id.sessionText)
        lastTagText = findViewById(R.id.lastTagText)
        syncText = findViewById(R.id.syncText)

        repository = AttendanceRepository(
            AppDatabase.getInstance(applicationContext).attendanceDao(),
            ApiClient.nfcApiService
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        foregroundDispatchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            login()
        }
        findViewById<Button>(R.id.syncButton).setOnClickListener {
            syncNow()
        }
        findViewById<Button>(R.id.endSessionButton).setOnClickListener {
            endSession()
        }

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

   private fun login() {
    val email = emailInput.text.toString().trim()
    val password = passwordInput.text.toString()
    val className = classInput.text.toString().trim()

    if (email.isBlank() || password.isBlank() || className.isBlank()) {
        Toast.makeText(this, "Fill in all three fields.", Toast.LENGTH_SHORT).show()
        return
    }

    // Disable the button while we wait for the server to respond.
    findViewById<Button>(R.id.loginButton).isEnabled = false
    statusText.text = "Logging in…"

    lifecycleScope.launch {
        // Call the backend. This is the real login — not just saving email locally.
        val loginResult = repository.loginTeacher(email, password)

        if (loginResult == null) {
            // Something went wrong — wrong password, server not running, etc.
            Toast.makeText(
                this@MainActivity,
                "Login failed. Check your credentials and that the server is running.",
                Toast.LENGTH_LONG
            ).show()
            statusText.text = "Login failed."
            findViewById<Button>(R.id.loginButton).isEnabled = true
            return@launch
        }

        // Login worked! Now save who the teacher is and start the session.
        teacherId = loginResult.user_id
        passwordInput.text.clear()
        activeSession = repository.startSession(loginResult.email, className)
        renderState()
        syncNow()
    }
}

        lifecycleScope.launch {
            activeSession = repository.startSession(email, className)
            renderState()
            syncNow()
        }
    }

    private fun endSession() {
        val session = activeSession ?: return
        lifecycleScope.launch {
            activeSession = repository.endSession(session)
            nfcAdapter?.disableForegroundDispatch(this@MainActivity)
            renderState()
        }
    }

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

    private fun renderState() {
        statusText.text = when {
            activeSession == null -> getString(R.string.login_required)
            nfcAdapter == null -> getString(R.string.nfc_missing)
            nfcAdapter?.isEnabled != true -> getString(R.string.nfc_disabled)
            else -> getString(R.string.nfc_ready)
        }

        sessionText.text = activeSession?.let {
            getString(R.string.session_active, it.className, it.teacherId)
        } ?: getString(R.string.session_inactive)

        findViewById<Button>(R.id.endSessionButton).isEnabled = activeSession != null
        findViewById<Button>(R.id.syncButton).isEnabled = activeSession != null

        if (activeSession != null && nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, foregroundDispatchIntent, null, null)
        }
    }

    private fun handleNfcIntent(intent: Intent?) {
        val session = activeSession ?: return
        if (intent == null) return

        val action = intent.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            return
        }

        val payload = parseNdefPayload(intent)
        val parsed = parseAttendancePayload(payload)
        if (parsed == null) {
            lastTagText.text = "NFC tag found, but no NDEF payload was readable."
            return
        }

        val record = AttendanceRecord(
            sessionId = session.id,
            teacherId = session.teacherId,
            className = session.className,
            studentId = parsed.studentId,
            studentName = parsed.studentName,
            timestamp = System.currentTimeMillis(),
            rawPayload = payload
        )

        lifecycleScope.launch {
            repository.saveRecord(record)
            val syncedCount = repository.syncPendingRecords(activeSession)
            val syncedNow = syncedCount > 0 && isOnline()

            triggerFeedback()
            lastTagText.text = if (syncedNow) {
                getString(R.string.tap_saved_synced, parsed.studentName, parsed.studentId)
            } else {
                getString(R.string.tap_saved_local, parsed.studentName, parsed.studentId)
            }
            syncText.text = getString(R.string.sync_result, syncedCount)
        }
    }

    private fun parseNdefPayload(intent: Intent): String {
        val messages = getNdefMessages(intent)
        return messages
            .flatMap { it.records.asIterable() }
            .mapNotNull { parseRecord(it) }
            .joinToString(separator = "\n")
    }

    private fun getNdefMessages(intent: Intent): List<NdefMessage> {
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES,
                NdefMessage::class.java
            )
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
            val json = JSONObject(normalized)
            val studentId = json.optString("student_id")
                .ifBlank { json.optString("studentId") }
            val studentName = json.optString("student_name")
                .ifBlank { json.optString("studentName") }
            if (studentId.isNotBlank() && studentName.isNotBlank()) {
                return ParsedAttendance(studentId, studentName)
            }
        }

        val pipeParts = normalized.split("|", ",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (pipeParts.size >= 2) {
            return ParsedAttendance(pipeParts[0], pipeParts[1])
        }

        val idPrefix = Regex("""(?i)student[_ ]?id\s*[:=]\s*([A-Za-z0-9_-]+)""").find(normalized)
        val namePrefix = Regex("""(?i)student[_ ]?name\s*[:=]\s*([^,;|]+)""").find(normalized)
        if (idPrefix != null && namePrefix != null) {
            return ParsedAttendance(idPrefix.groupValues[1].trim(), namePrefix.groupValues[1].trim())
        }

        return ParsedAttendance(normalized, normalized)
    }

    private fun parseRecord(record: NdefRecord): String? {
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> parseTextRecord(record)

            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_URI) -> parseUriRecord(record)

            record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                val mimeType = record.type.toString(Charsets.US_ASCII)
                val body = record.payload.toString(Charsets.UTF_8)
                "$mimeType: $body"
            }

            else -> record.payload.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8)
        }
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        val payload = record.payload
        if (payload.isEmpty()) return null

        val isUtf16 = payload[0].toInt() and 0x80 != 0
        val languageCodeLength = payload[0].toInt() and 0x3F
        val charset = if (isUtf16) Charset.forName("UTF-16") else Charsets.UTF_8
        return payload.copyOfRange(1 + languageCodeLength, payload.size).toString(charset)
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

        val prefixIndex = payload[0].toInt()
        val prefix = prefixes.getOrElse(prefixIndex) { "" }
        return prefix + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
    }

    private fun triggerFeedback() {
        Toast.makeText(this, getString(R.string.student_registered), Toast.LENGTH_SHORT).show()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private data class ParsedAttendance(
        val studentId: String,
        val studentName: String
    )
}
