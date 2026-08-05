package com.neo.dataflow

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import kotlin.math.max

val Context.dataStore by preferencesDataStore("dataflow")

enum class TrackingMode { AUTOMATIC, MANUAL, HYBRID }
enum class DataOrigin { MEASURED, MANUAL, CALCULATED, ESTIMATED, UNAVAILABLE }
data class Plan(val totalBytes:Long=5_000_000_000, val start:LocalDate=LocalDate.now(), val end:LocalDate=LocalDate.now().plusDays(13), val safety:Int=20, val mode:TrackingMode=TrackingMode.MANUAL)
data class Reading(val at:Long, val remaining:Long, val origin:DataOrigin)
data class Daily(val date:String,val used:Long,val budget:Long,val origin:DataOrigin)
data class Snapshot(val plan:Plan=Plan(),val manualRemaining:Long?=null,val demo:Boolean=false,val notifications:Boolean=false,val reduceEffects:Boolean=false,val onboardingDone:Boolean=false,val lastRefresh:Long=0,val todayUsed:Long?=null,val measuredPeriod:Long?=null)

object Units {
    const val KB=1_000L; const val MB=1_000_000L; const val GB=1_000_000_000L; const val TB=1_000_000_000_000L
    fun bytes(value:Double, unit:String):Long { require(value>=0 && value.isFinite()); val m=when(unit){"Ko"->KB;"Mo"->MB;"Go"->GB;"To"->TB;else->1}; return (value*m).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong() }
    fun format(v:Long):String { val n=max(0,v); return when { n>=TB -> "%.2f To".format(n.toDouble()/TB); n>=GB -> "%.2f Go".format(n.toDouble()/GB); n>=MB -> "%.0f Mo".format(n.toDouble()/MB); n>=KB -> "%.0f Ko".format(n.toDouble()/KB); else -> "$n o" } }
}
object Engine {
    fun daysRemaining(p:Plan, now:LocalDate=LocalDate.now())=max(0, Duration.between(now.atStartOfDay(),p.end.plusDays(1).atStartOfDay()).toDays().toInt())
    fun reserve(p:Plan)=(p.totalBytes*(p.safety.coerceIn(0,100)/100.0)).toLong()
    fun usable(p:Plan)=max(0,p.totalBytes-reserve(p))
    fun budget(remaining:Long,p:Plan,now:LocalDate=LocalDate.now()):Long { val d=daysRemaining(p,now); return if(d<=0) 0 else max(0,remaining-reserve(p))/d }
    fun projectedRemaining(remaining:Long,avgDaily:Long,p:Plan)=max(0,remaining-avgDaily*daysRemaining(p))
    fun status(used:Long,budget:Long)=when { budget<=0L -> "Données insuffisantes"; used<budget*.5 -> "Très large"; used<budget*.85 -> "Dans le rythme"; used<budget -> "Un peu au-dessus"; used<budget*1.2 -> "Ralentis aujourd’hui"; else -> "Budget quotidien dépassé" }
}
class Store(private val c:Context){
    private object K { val total=longPreferencesKey("total"); val start=longPreferencesKey("start"); val end=longPreferencesKey("end"); val safety=intPreferencesKey("safety"); val mode=stringPreferencesKey("mode"); val manual=longPreferencesKey("manual"); val demo=booleanPreferencesKey("demo"); val notif=booleanPreferencesKey("notif"); val effects=booleanPreferencesKey("effects"); val onboard=booleanPreferencesKey("onboard"); val refresh=longPreferencesKey("refresh") }
    val flow:Flow<Snapshot> = c.dataStore.data.map { p -> Snapshot(Plan(p[K.total]?:5*Units.GB, LocalDate.ofEpochDay(p[K.start]?:LocalDate.now().toEpochDay()),LocalDate.ofEpochDay(p[K.end]?:LocalDate.now().plusDays(13).toEpochDay()),p[K.safety]?:20,runCatching{TrackingMode.valueOf(p[K.mode]?:"MANUAL")}.getOrDefault(TrackingMode.MANUAL)),p[K.manual],p[K.demo]?:false,p[K.notif]?:false,p[K.effects]?:false,p[K.onboard]?:false,p[K.refresh]?:0) }
    suspend fun save(s:Snapshot)=c.dataStore.edit{p->p[K.total]=s.plan.totalBytes;p[K.start]=s.plan.start.toEpochDay();p[K.end]=s.plan.end.toEpochDay();p[K.safety]=s.plan.safety;p[K.mode]=s.plan.mode.name;s.manualRemaining?.let{p[K.manual]=it}?:p.remove(K.manual);p[K.demo]=s.demo;p[K.notif]=s.notifications;p[K.effects]=s.reduceEffects;p[K.onboard]=s.onboardingDone;p[K.refresh]=s.lastRefresh}
    fun export(s:Snapshot):String=JSONObject().put("formatVersion",1).put("totalBytes",s.plan.totalBytes).put("start",s.plan.start.toString()).put("end",s.plan.end.toString()).put("safety",s.plan.safety).put("mode",s.plan.mode.name).put("manualRemaining",s.manualRemaining).put("demo",s.demo).toString(2)
    fun import(text:String):Snapshot { val o=JSONObject(text); require(o.optInt("formatVersion") == 1); val plan=Plan(o.getLong("totalBytes"),LocalDate.parse(o.getString("start")),LocalDate.parse(o.getString("end")),o.getInt("safety"),TrackingMode.valueOf(o.getString("mode"))); require(plan.totalBytes>=0 && !plan.end.isBefore(plan.start) && plan.safety in 0..100); return Snapshot(plan,if(o.isNull("manualRemaining"))null else o.getLong("manualRemaining"),o.optBoolean("demo")) }
    fun csv(days:List<Daily>):String=buildString{appendLine("date,used_bytes,budget_bytes,origin");days.forEach{appendLine("${it.date},${it.used},${it.budget},${it.origin}")}}
}
class Stats(private val c:Context){
    fun hasAccess():Boolean { val a=c.getSystemService(AppOpsManager::class.java); val mode=if(Build.VERSION.SDK_INT>=29)a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.packageName) else @Suppress("DEPRECATION") a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.packageName); return mode==AppOpsManager.MODE_ALLOWED }
    fun mobile(start:Long,end:Long):Long?=query(ConnectivityManager.TYPE_MOBILE,start,end)
    fun wifi(start:Long,end:Long):Long?=query(ConnectivityManager.TYPE_WIFI,start,end)
    private fun query(type:Int,start:Long,end:Long):Long?=try { if(!hasAccess()) return null; val n=c.getSystemService(NetworkStatsManager::class.java); val b=n.querySummaryForDevice(type,null,start,end); (b.rxBytes+b.txBytes).takeIf{it>=0} } catch(_:Throwable){ null }
}
object Alerts { fun channel(c:Context){ if(Build.VERSION.SDK_INT>=26)c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("budget","Alertes de budget",NotificationManager.IMPORTANCE_DEFAULT)) } }
