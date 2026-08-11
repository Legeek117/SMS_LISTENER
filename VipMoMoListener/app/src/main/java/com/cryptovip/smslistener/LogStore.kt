package com.cryptovip.smslistener

import android.content.Context
import androidx.room.*
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════ ENTITÉS (TABLES) ════════════════════════════

@Entity(tableName = "sms_logs")
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "amount")         val amount: Int,
    @ColumnInfo(name = "sender")         val sender: String,
    @ColumnInfo(name = "status")         val status: String,  // SUCCESS / FAILED / PENDING
    @ColumnInfo(name = "detail")         val detail: String = "",
    @ColumnInfo(name = "created_at")     val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_queue")
data class PendingSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "retry_count")  val retryCount: Int = 0,
    @ColumnInfo(name = "created_at")   val createdAt: Long = System.currentTimeMillis()
)

// ═══════════════════════════════ DAO (REQUÊTES SQL) ══════════════════════════

@Dao
interface AppDao {
    @Insert
    suspend fun insertLog(l: SmsLog)

    @Query("SELECT * FROM sms_logs ORDER BY id DESC LIMIT 50")
    suspend fun getLast50Logs(): List<SmsLog>

    @Insert
    suspend fun enqueue(p: PendingSms)

    @Query("SELECT * FROM pending_queue ORDER BY id ASC LIMIT 50")
    suspend fun getPending(): List<PendingSms>

    @Query("DELETE FROM pending_queue WHERE id = :id")
    suspend fun deletePending(id: Long)
}

// ═══════════════════════════════ BASE DE DONNÉES ROOM ════════════════════════

@Database(
    entities = [SmsLog::class, PendingSms::class],
    version  = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "vip_listener_db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

// ═══════════════════════════════ HELPER GLOBAL ═══════════════════════════════

object LogStore {
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

    suspend fun log(
        ctx: Context,
        tid: String,
        amount: Int,
        sender: String,
        status: String,
        detail: String = ""
    ) {
        AppDatabase.get(ctx).dao().insertLog(
            SmsLog(
                transactionId = tid,
                amount        = amount,
                sender        = sender,
                status        = status,
                detail        = detail
            )
        )
    }

    suspend fun getLast50(ctx: Context): List<String> {
        return AppDatabase.get(ctx).dao().getLast50Logs().map { l ->
            val icon = when (l.status) {
                "SUCCESS" -> "✅"
                "FAILED"  -> "❌"
                "PENDING" -> "⏳"
                else      -> "•"
            }
            buildString {
                append(icon).append(" ").append(dateFmt.format(Date(l.createdAt)))
                append(" — ").append(String.format("%-8d", l.amount)).append("F")
                append(" — ID:").append(l.transactionId.take(10))
                append("\n   ").append(l.detail.take(100))
            }
        }
    }

    suspend fun enqueueRetry(ctx: Context, jsonPayload: String) {
        AppDatabase.get(ctx).dao().enqueue(PendingSms(payloadJson = jsonPayload))
    }

    suspend fun processPending(ctx: Context) {
        val dao   = AppDatabase.get(ctx).dao()
        val queue = dao.getPending()
        for (item in queue) {
            val success = NetworkHelper.retrySendRaw(ctx, item.payloadJson)
            if (success) {
                runCatching {
                    val o = org.json.JSONObject(item.payloadJson)
                    log(
                        ctx    = ctx,
                        tid    = o.optString("transactionId", "R-${item.id}"),
                        amount = o.optInt("amount", 0),
                        sender = o.optString("senderPhone", "?"),
                        status = "SUCCESS",
                        detail = "Renvoyé depuis la file d'attente hors ligne"
                    )
                }
                dao.deletePending(item.id)
            }
        }
    }
}
