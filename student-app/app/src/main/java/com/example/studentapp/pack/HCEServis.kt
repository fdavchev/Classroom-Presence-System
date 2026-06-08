package com.example.studentapp

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class HceService : HostApduService() {

    companion object {
        // This is the shared "whiteboard" where MainActivity writes the student data.
        // HceService reads from here when the teacher's phone connects.
        // It starts empty — nothing is set until the student logs in.
        var studentPayload: String = ""

        // The SELECT APDU command that the teacher's phone will send.
        // This is like a "knock knock" — the teacher's phone is asking:
        // "Is anyone there with AID F039414814810 0?"
        private val SELECT_APDU = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x07.toByte(),
            // This is the AID — must match apduservice.xml exactly
            0xF0.toByte(), 0x39.toByte(), 0x41.toByte(), 0x48.toByte(),
            0x14.toByte(), 0x81.toByte(), 0x00.toByte(),
            0x00.toByte()
        )

        // 0x90 0x00 = "OK, I'm here and here's my data"
        private val SUCCESS_SW = byteArrayOf(0x90.toByte(), 0x00.toByte())

        // 0x6A 0x82 = "Sorry, I don't understand that command"
        private val FAILURE_SW = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }

    // Android calls this function every time the teacher's phone sends a command.
    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d("HCE", "Received APDU: ${apdu.toHex()}")

        // Check if this is a SELECT command (bytes 0x00 and 0xA4 at the start)
        return if (apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte()) {
            Log.d("HCE", "SELECT received — sending payload: $studentPayload")

            if (studentPayload.isBlank()) {
                // No student is logged in yet — refuse the request
                Log.w("HCE", "No payload set — student not logged in")
                FAILURE_SW
            } else {
                // Convert our student data string into bytes and append the SUCCESS code
                // The teacher's app will read these bytes and decode them back to a string
                val dataBytes = studentPayload.toByteArray(Charsets.UTF_8)
                dataBytes + SUCCESS_SW
            }
        } else {
            // Some other NFC command we don't care about
            FAILURE_SW
        }
    }

    // Called when the NFC connection ends (phone moved away)
    override fun onDeactivated(reason: Int) {
        Log.d("HCE", "NFC connection ended. Reason: $reason")
        // reason 0 = link lost (phone moved away)
        // reason 1 = deselected (another app took over)
    }

    // Helper: converts a byte array to a readable hex string for logging
    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
}