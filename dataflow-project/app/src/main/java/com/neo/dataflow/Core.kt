package com.neo.dataflow

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import kotlin.math.max

val Context.dataStore by preferencesDataStore("dataflow")

enum class TrackingMode { AUTOMATIC, MANUAL, HYBRID }
enum class DataOrigin { MEASURED, MANUAL, CALCULATED, ESTIMATED, UNAVAILABLE }

data class Plan(
    val totalBytes: Long = 5_000_000_000,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now().plusDays(13),
    val safety: Int = 20,
    val mode: TrackingMode = TrackingMode.MANUAL,
)

data class Daily(val date: String, val used: Long, val budget: Long, val origin: DataOrigin)

data class Snapshot(
    val plan: Plan = Plan(),
    val manualRemaining: Long? = null,
    val demo: Boolean = false,
    val notifications: Boolean = false,
    val reduceEffects: Boolean = false,
    val onboardingDone: Boolean = false,
    val lastRefresh: Long = 0,
    val todayUsed: Long? = null,
    val measuredPeriod: Long? = null,
)

object Units {
    const val KB = 1_000L
    const val MB = 1_000_000L
    const val GB = 1_000_000_000L
    const val TB = 1_000_000_000_000L

    fun bytes(value: Double, unit: String): Long {
        require(value >= 0 && value.isFinite())
        val multiplier = when (unit) {
            "Ko" -> KB
            "Mo" -> MB
            "Go" -> GB
            "To" -> TB
            else -> 1L
        }
        return (value * multiplier).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    }

    fun format(value: Long): String {
        val bytes = max(0, value)
        return when {
            bytes >= TB -> "%.2f To".format(bytes.toDouble() / TB)
            bytes >= GB -> "%.2f Go".format(bytes.toDouble() / GB)
            bytes >= MB -> "%.0f Mo".format(bytes.toDouble() / MB)
            bytes >= KB -> "%.0f Ko".format(bytes.toDouble() / KB)
            else -> "$bytes o"
        }
    }
}

object Engine {
    fun daysRemaining(plan: Plan, now: LocalDate = LocalDate.now()): Int =
        max(0, Duration.between(now.atStartOfDay(), plan.end.plusDays(1).atStartOfDay()).toDays().toInt())

    fun reserve(plan: Plan): Long =
        (plan.totalBytes * (plan.safety.coerceIn(0, 100) / 100.0)).toLong()

    fun usable(plan: Plan): Long = max(0, plan.totalBytes - reserve(plan))

    fun budget(remaining: Long, plan: Plan, now: LocalDate = LocalDate.now()): Long {
        val days = daysRemaining(plan, now)
        return if (days <= 0) 0 else max(0, remaining - reserve(plan)) / days
    }

    fun projectedRemaining(remaining: Long, averageDaily: Long, plan: Plan): Long =
        max(0, remaining - averageDaily * daysRemaining(plan))

    fun status(used: Long, budget: Long): String = when {
        budget <= 0L -> "Données insuffisantes"
        used < budget * .5 -> "Très large"
        used < budget * .85 -> "Dans le rythme"
        used < budget -> "Un peu au-dessus"
        used < budget * 1.2 -> "Ralentis aujourd’hui"
        else -> "Budget quotidien dépassé"
    }
}

class Store(private val context: Context) {
    private object Keys {
        val total = longPreferencesKey("total")
        val start = longPreferencesKey("start")
        val end = longPreferencesKey("end")
        val safety = intPreferencesKey("safety")
        val mode = stringPreferencesKey("mode")
        val manual = longPreferencesKey("manual")
        val demo = booleanPreferencesKey("demo")
        val notifications = booleanPreferencesKey("notifications")
        val effects = booleanPreferencesKey("effects")
        val onboarding = booleanPreferencesKey("onboarding")
        val refresh = longPreferencesKey("refresh")
    }

    val flow: Flow<Snapshot> = context.dataStore.data.map { preferences ->
        Snapshot(
            plan = Plan(
                totalBytes = preferences[Keys.total] ?: 5 * Units.GB,
                start = LocalDate.ofEpochDay(preferences[Keys.start] ?: LocalDate.now().toEpochDay()),
                end = LocalDate.ofEpochDay(preferences[Keys.end] ?: LocalDate.now().plusDays(13).toEpochDay()),
                safety = preferences[Keys.safety] ?: 20,
                mode = runCatching {
                    TrackingMode.valueOf(preferences[Keys.mode] ?: TrackingMode.MANUAL.name)
                }.getOrDefault(TrackingMode.MANUAL),
            ),
            manualRemaining = preferences[Keys.manual],
            demo = preferences[Keys.demo] ?: false,
            notifications = preferences[Keys.notifications] ?: false,
            reduceEffects = preferences[Keys.effects] ?: false,
            onboardingDone = preferences[Keys.onboarding] ?: false,
            lastRefresh = preferences[Keys.refresh] ?: 0,
        )
    }

    suspend fun save(snapshot: Snapshot) {
        context.dataStore.edit { preferences ->
            preferences[Keys.total] = snapshot.plan.totalBytes
            preferences[Keys.start] = snapshot.plan.start.toEpochDay()
            preferences[Keys.end] = snapshot.plan.end.toEpochDay()
            preferences[Keys.safety] = snapshot.plan.safety
            preferences[Keys.mode] = snapshot.plan.mode.name
            snapshot.manualRemaining?.let { preferences[Keys.manual] = it } ?: preferences.remove(Keys.manual)
            preferences[Keys.demo] = snapshot.demo
            preferences[Keys.notifications] = snapshot.notifications
            preferences[Keys.effects] = snapshot.reduceEffects
            preferences[Keys.onboarding] = snapshot.onboardingDone
            preferences[Keys.refresh] = snapshot.lastRefresh
        }
    }

    fun export(snapshot: Snapshot): String = JSONObject()
        .put("formatVersion", 1)
        .put("totalBytes", snapshot.plan.totalBytes)
        .put("start", snapshot.plan.start.toString())
        .put("end", snapshot.plan.end.toString())
        .put("safety", snapshot.plan.safety)
        .put("mode", snapshot.plan.mode.name)
        .put("manualRemaining", snapshot.manualRemaining)
        .put("demo", snapshot.demo)
        .toString(2)

    fun import(text: String): Snapshot {
        val json = JSONObject(text)
        require(json.optInt("formatVersion") == 1)
        val plan = Plan(
            totalBytes = json.getLong("totalBytes"),
            start = LocalDate.parse(json.getString("start")),
            end = LocalDate.parse(json.getString("end")),
            safety = json.getInt("safety"),
            mode = TrackingMode.valueOf(json.getString("mode")),
        )
        require(plan.totalBytes >= 0)
        require(!plan.end.isBefore(plan.start))
        require(plan.safety in 0..100)
        return Snapshot(
            plan = plan,
            manualRemaining = if (json.isNull("manualRemaining")) null else json.getLong("manualRemaining"),
            demo = json.optBoolean("demo"),
        )
    }

    fun csv(days: List<Daily>): String = buildString {
        appendLine("date,used_bytes,budget_bytes,origin")
        days.forEach { appendLine("${it.date},${it.used},${it.budget},${it.origin}") }
    }
}

class Stats(private val context: Context) {
    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun mobile(start: Long, end: Long): Long? =
        query(ConnectivityManager.TYPE_MOBILE, start, end)

    fun wifi(start: Long, end: Long): Long? =
        query(ConnectivityManager.TYPE_WIFI, start, end)

    private fun query(type: Int, start: Long, end: Long): Long? {
        if (!hasAccess()) return null
        return try {
            val manager = context.getSystemService(NetworkStatsManager::class.java)
            val bucket = manager.querySummaryForDevice(type, null, start, end)
            (bucket.rxBytes + bucket.txBytes).takeIf { it >= 0 }
        } catch (_: Throwable) {
            null
        }
    }
}

object Alerts {
    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "budget",
                    "Alertes de budget",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
