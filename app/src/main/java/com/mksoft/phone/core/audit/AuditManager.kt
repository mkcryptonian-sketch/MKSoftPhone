package com.mksoft.phone.core.audit

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AuditManager"
        private const val AUDIT_FILE_NAME = "audit_log.txt"

        @Volatile
        private var instance: AuditManager? = null

        fun getInstance(context: Context): AuditManager {
            return instance ?: synchronized(this) {
                instance ?: AuditManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val auditFile = File(context.filesDir, AUDIT_FILE_NAME)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun logOutgoingCall(accountId: String, destUri: String, isThirdParty: Boolean) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] OUTGOING CALL - Account: $accountId, Destination: $destUri, Third-Party: $isThirdParty\n"
        
        try {
            FileOutputStream(auditFile, true).use { output ->
                output.write(logEntry.toByteArray())
            }
            Log.d(TAG, "Audit log written: $logEntry")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log: ${e.message}", e)
        }
    }
}
