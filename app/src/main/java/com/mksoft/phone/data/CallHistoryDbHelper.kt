package com.mksoft.phone.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class CallHistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "CallHistoryDbHelper"
        private const val DATABASE_NAME = "call_history.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_CALLS = "call_ledger"
        const val COLUMN_ID = "id"
        const val COLUMN_NUMBER = "number"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_DURATION = "duration"
        const val COLUMN_IS_INCOMING = "is_incoming"
        const val COLUMN_WAS_ANSWERED = "was_answered"
        const val COLUMN_IS_THIRD_PARTY = "is_third_party"

        private const val CREATE_TABLE_CALLS = """
            CREATE TABLE $TABLE_CALLS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_NUMBER TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_DURATION INTEGER NOT NULL,
                $COLUMN_IS_INCOMING INTEGER NOT NULL,
                $COLUMN_WAS_ANSWERED INTEGER NOT NULL,
                $COLUMN_IS_THIRD_PARTY INTEGER NOT NULL DEFAULT 0
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            db.execSQL(CREATE_TABLE_CALLS)
            Log.d(TAG, "Database table created successfully: $TABLE_CALLS")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating database table: ${e.message}", e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "onUpgrade: oldVersion=$oldVersion, newVersion=$newVersion")
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_CALLS ADD COLUMN $COLUMN_IS_THIRD_PARTY INTEGER NOT NULL DEFAULT 0")
                Log.d(TAG, "Successfully added $COLUMN_IS_THIRD_PARTY column to $TABLE_CALLS")
            } catch (e: Exception) {
                Log.e(TAG, "Error upgrading database: ${e.message}", e)
            }
        }
    }

    fun insertCall(entry: CallHistoryEntry): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_ID, entry.id)
                put(COLUMN_NUMBER, entry.number)
                put(COLUMN_TIMESTAMP, entry.timestamp)
                put(COLUMN_DURATION, entry.duration)
                put(COLUMN_IS_INCOMING, if (entry.isIncoming) 1 else 0)
                put(COLUMN_WAS_ANSWERED, if (entry.wasAnswered) 1 else 0)
                put(COLUMN_IS_THIRD_PARTY, if (entry.isThirdParty) 1 else 0)
            }
            val result = db.insert(TABLE_CALLS, null, values)
            if (result == -1L) {
                Log.e(TAG, "Failed to insert call record: ${entry.id}")
                false
            } else {
                Log.d(TAG, "Successfully inserted call record: ${entry.id}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting call record: ${e.message}", e)
            false
        }
    }

    fun getAllCalls(): List<CallHistoryEntry> {
        val list = mutableListOf<CallHistoryEntry>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_CALLS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC"
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow(COLUMN_ID)
                val numIdx = c.getColumnIndexOrThrow(COLUMN_NUMBER)
                val timeIdx = c.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
                val durIdx = c.getColumnIndexOrThrow(COLUMN_DURATION)
                val incIdx = c.getColumnIndexOrThrow(COLUMN_IS_INCOMING)
                val ansIdx = c.getColumnIndexOrThrow(COLUMN_WAS_ANSWERED)
                val tpIdx = c.getColumnIndexOrThrow(COLUMN_IS_THIRD_PARTY)

                while (c.moveToNext()) {
                    list.add(
                        CallHistoryEntry(
                            id = c.getString(idIdx),
                            number = c.getString(numIdx),
                            timestamp = c.getLong(timeIdx),
                            duration = c.getLong(durIdx),
                            isIncoming = c.getInt(incIdx) == 1,
                            wasAnswered = c.getInt(ansIdx) == 1,
                            isThirdParty = c.getInt(tpIdx) == 1
                        )
                    )
                }
            }
            Log.d(TAG, "Retrieved ${list.size} call records from database")
        } catch (e: Exception) {
            Log.e(TAG, "Error query database call records: ${e.message}", e)
        }
        return list
    }

    fun clearAllCalls(): Boolean {
        return try {
            val db = writableDatabase
            db.delete(TABLE_CALLS, null, null)
            Log.d(TAG, "Cleared all records from $TABLE_CALLS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing database call records: ${e.message}", e)
            false
        }
    }
}
