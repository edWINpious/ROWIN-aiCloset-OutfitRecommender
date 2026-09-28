package com.example.rowin_adlproject.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.rowin_adlproject.MainActivity
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.ClothingItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Practical #10 - AI-based alerts & notifications.
 *
 * [AlertsEngine] computes smart, wardrobe-aware suggestions as a pure
 * function (unit-testable, no network); [AlertsManager] raises them as real
 * system notifications on Android 13+ (with POST_NOTIFICATIONS permission)
 * and keeps an in-app history feed for the Alerts screen.
 */
object AlertsEngine {

    /**
     * A computed suggestion. [dedupeKey] keeps repeats quiet for 24h;
     * [actionRoute] names the ROWIN route to deep-link into.
     */
    data class Alert(
        val id: Long,
        val title: String,
        val message: String,
        val dedupeKey: String,
        val actionRoute: String,
    )

    /** Messages for the AI chatbot preseeding (practical #8). */
    fun welcomeMessages(userName: String?): List<ChatMessage> = listOf(
        ChatMessage(
            role = "assistant",
            text = "Hi ${userName?.split(" ")?.firstOrNull() ?: "there"}! I'm your ROWIN stylist. " +
                "Ask me what to wear, how to combine pieces, or what's missing from your closet.",
        ),
    )

    /**
     * Wardrobe-aware suggestions:
     *  - season drift: wardrobe is dominated by another season than today's
     *  - gaps: a category the recommender needs is empty
     *  - rotation: an item not worn/added in 30+ days
     */
    fun compute(nowMillis: Long = System.currentTimeMillis()): List<Alert> = computeInternal(null, nowMillis)

    fun compute(wardrobe: List<ClothingItemEntity>, nowMillis: Long = System.currentTimeMillis()): List<Alert> =
        computeInternal(wardrobe, nowMillis)

    private fun computeInternal(wardrobe: List<ClothingItemEntity>?, nowMillis: Long): List<Alert> {
        val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val month = today.get(Calendar.MONTH)
        val currentSeason = when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Season.WINTER
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Season.SPRING
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Season.SUMMER
            else -> Season.AUTUMN
        }
        val alerts = mutableListOf<Alert>()
        var id = 1L

        val items = wardrobe
        if (items != null && items.isNotEmpty()) {
            val dominant = items.groupingBy { it.season }.eachCount()
                .maxByOrNull { it.value }?.key
            if (dominant != null && dominant != currentSeason && dominant != Season.ALL_SEASON) {
                alerts += Alert(
                    id = id++,
                    title = "Season switch ahead",
                    message = "Most of your closet is ${dominant.name.lowercase().replace('_', ' ')}-heavy, " +
                        "but it's ${currentSeason.name.lowercase()} now. Time for a rotation?",
                    dedupeKey = "season_$currentSeason",
                    actionRoute = "closet",
                )
            }

            val missing = mutableListOf<String>()
            if (items.none { it.category == Category.SHOES }) missing += "shoes"
            if (items.none { it.category == Category.BOTTOMS }) missing += "bottoms"
            if (items.none { it.category == Category.TOPS }) missing += "tops"
            if (missing.isNotEmpty()) {
                alerts += Alert(
                    id = id++,
                    title = "Fill the gap",
                    message = "Your closet has no ${missing.joinToString()} yet - the AI stylist " +
                        "needs them to build complete outfits.",
                    dedupeKey = "gap_${missing.joinToString("_")}",
                    actionRoute = "add_clothing",
                )
            }

            val staleCutoff = nowMillis - TimeUnit.DAYS.toMillis(30)
            val stale = items.filter { it.createdAt < staleCutoff }
            if (stale.isNotEmpty()) {
                val item = stale.random()
                alerts += Alert(
                    id = id++,
                    title = "Unworn favorite",
                    message = "\"${item.name}\" hasn't been styled in a while. Want the AI to build " +
                        "a look around it?",
                    dedupeKey = "stale_${item.id}",
                    actionRoute = "planner",
                )
            }
        } else if (items != null) {
            alerts += Alert(
                id = id++,
                title = "Start your closet",
                message = "Add your first clothing item and let the AI stylist build outfits from it.",
                dedupeKey = "empty_closet",
                actionRoute = "add_clothing",
            )
        }
        return alerts
    }
}

/** A chat turn (practical #8 - AI chatbot). Persisted in-memory per session. */
data class ChatMessage(
    val role: String, // "user" | "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Raises alerts as system notifications and holds the in-app feed.
 * The pure [AlertsEngine] logic stays free of Android dependencies.
 */
class AlertsManager(private val context: Context) {

    private val _alerts = MutableStateFlow<List<AlertsEngine.Alert>>(emptyList())
    val alerts: StateFlow<List<AlertsEngine.Alert>> = _alerts

    private val lastShown = mutableMapOf<String, Long>()

    /** Recomputes from the live wardrobe and notifies new alerts (24h quiet per key). */
    fun refresh(wardrobe: List<ClothingItemEntity>) {
        val computed = AlertsEngine.compute(wardrobe)
        _alerts.value = computed
        val now = System.currentTimeMillis()
        computed.forEach { alert ->
            val last = lastShown[alert.dedupeKey] ?: 0L
            if (now - last > QUIET_PERIOD) {
                lastShown[alert.dedupeKey] = now
                notify(alert)
            }
        }
    }

    fun notificationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun notify(alert: AlertsEngine.Alert) {
        if (!notificationsEnabled()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ROWIN AI stylist",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "AI wardrobe alerts and outfit suggestions" },
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, alert.actionRoute)
        }
        val pending = PendingIntent.getActivity(
            context,
            alert.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.wordmark_rowin)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(alert.id.toInt(), notification) }
    }

    companion object {
        private const val CHANNEL_ID = "rowin_ai_alerts"
        const val EXTRA_ROUTE = "rowin_route"
        private val QUIET_PERIOD = TimeUnit.HOURS.toMillis(24)
    }
}
