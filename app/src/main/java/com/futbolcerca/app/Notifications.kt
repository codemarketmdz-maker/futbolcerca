package com.futbolcerca.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CHANNEL = "futbolcerca_avisos"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de Fútbol Cerca", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun show(ctx: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_cancha)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, notification) } catch (_: SecurityException) { }
    }

    private fun reminderName(matchId: String, uid: String) = "recordatorio_${matchId}_$uid"

    /** Recordatorio ~3 h antes del partido (solo si falta al menos media hora). */
    fun scheduleReminder(ctx: Context, matchId: String, uid: String, title: String, eventAtMs: Long) {
        val delay = eventAtMs - 3L * 3600_000L - System.currentTimeMillis()
        val untilStart = eventAtMs - System.currentTimeMillis()
        if (untilStart < 30L * 60_000L) return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(maxOf(delay, 1_000L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("matchId" to matchId, "uid" to uid, "title" to title))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(reminderName(matchId, uid), ExistingWorkPolicy.KEEP, request)
    }

    fun cancelReminder(ctx: Context, matchId: String, uid: String) {
        WorkManager.getInstance(ctx).cancelUniqueWork(reminderName(matchId, uid))
    }

    /** Revisa avisos cada ~15 min aunque la app esté cerrada (límite mínimo de Android). */
    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("avisos_futbolcerca", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        return try {
            val snap = FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .collection("notifications").whereEqualTo("notified", false).get().await()
            snap.documents.forEach { d ->
                Notifier.show(applicationContext, d.id.hashCode(), d.getString("title") ?: "Fútbol Cerca", d.getString("body") ?: "")
                d.reference.update("notified", true)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

/** Muestra el recordatorio solo si el partido sigue en pie y la persona sigue confirmada (o es la organizadora). */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val matchId = inputData.getString("matchId") ?: return Result.success()
        val uid = inputData.getString("uid") ?: return Result.success()
        val current = FirebaseAuth.getInstance().currentUser?.uid
        if (current != uid) return Result.success()
        return try {
            val db = FirebaseFirestore.getInstance()
            val match = db.collection("matches").document(matchId).get().await()
            if (!match.exists() || match.getString("status") == "cancelled") return Result.success()
            if (match.getString("ownerUid") != uid) {
                val part = db.collection("matches").document(matchId).collection("participants").document(uid).get().await()
                if (!part.exists()) return Result.success()
            }
            val title = match.getString("title") ?: inputData.getString("title") ?: "Tu partido"
            val where = listOf(match.getString("venue"), match.getString("address")).filter { !it.isNullOrBlank() }.joinToString(" · ")
            Notifier.show(applicationContext, ("rec" + matchId).hashCode(), "⚽ Hoy jugás: $title", "${match.getString("dateLabel") ?: ""} · $where")
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
