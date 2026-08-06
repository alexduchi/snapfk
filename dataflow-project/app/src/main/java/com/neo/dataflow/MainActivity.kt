package com.neo.dataflow

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alerts.channel(this)
        setContent { DataFlowApp() }
    }
}

class DataFlowViewModel(
    private val store: Store,
    private val stats: Stats,
) : ViewModel() {
    private val _state = MutableStateFlow(Snapshot())
    val state = _state.asStateFlow()
    private var loadedOnce = false

    init {
        viewModelScope.launch {
            store.flow.collectLatest { saved ->
                val runtime = _state.value
                _state.value = saved.copy(
                    todayUsed = runtime.todayUsed,
                    measuredPeriod = runtime.measuredPeriod,
                    lastRefresh = runtime.lastRefresh,
                )
                if (!loadedOnce) {
                    loadedOnce = true
                    refresh()
                }
            }
        }
    }

    fun update(transform: (Snapshot) -> Snapshot, refreshAfter: Boolean = false) {
        viewModelScope.launch {
            val next = transform(_state.value)
            _state.value = next
            store.save(next)
            if (refreshAfter) refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val periodStart = current.plan.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val today = stats.mobile(todayStart, now)
            val period = stats.mobile(periodStart, now)
            _state.value = current.copy(
                todayUsed = today,
                measuredPeriod = period,
                lastRefresh = if (today != null || period != null) now else current.lastRefresh,
            )
        }
    }

    fun hasUsageAccess(): Boolean = stats.hasAccess()

    fun remaining(): Long {
        val s = _state.value
        if (s.demo) return 3_420 * Units.MB
        return when (s.plan.mode) {
            TrackingMode.MANUAL -> s.manualRemaining ?: s.plan.totalBytes
            TrackingMode.AUTOMATIC -> s.measuredPeriod?.let { maxOf(0, s.plan.totalBytes - it) }
                ?: s.manualRemaining ?: s.plan.totalBytes
            TrackingMode.HYBRID -> s.manualRemaining
                ?: s.measuredPeriod?.let { maxOf(0, s.plan.totalBytes - it) }
                ?: s.plan.totalBytes
        }
    }

    fun usedSinceStart(): Long {
        val s = _state.value
        if (s.demo) return maxOf(0, s.plan.totalBytes - remaining())
        return s.measuredPeriod ?: s.manualRemaining?.let { maxOf(0, s.plan.totalBytes - it) } ?: 0
    }

    fun usedToday(): Long = if (_state.value.demo) 84 * Units.MB else _state.value.todayUsed ?: 0

    fun origin(): DataOrigin = when {
        _state.value.demo -> DataOrigin.ESTIMATED
        _state.value.manualRemaining != null && _state.value.plan.mode != TrackingMode.AUTOMATIC -> DataOrigin.MANUAL
        _state.value.measuredPeriod != null -> DataOrigin.MEASURED
        _state.value.manualRemaining != null -> DataOrigin.MANUAL
        else -> DataOrigin.UNAVAILABLE
    }

    fun dailyBudget(): Long = Engine.budget(remaining(), _state.value.plan)

    fun exportJson(): String = store.export(_state.value)

    fun exportCsv(): String = store.csv(
        listOf(Daily(LocalDate.now().toString(), usedToday(), dailyBudget(), origin())),
    )

    fun importJson(text: String) {
        update({ store.import(text).copy(onboardingDone = true) }, refreshAfter = true)
    }
}

@Composable
fun DataFlowApp() {
    val context = LocalContext.current
    val vm: DataFlowViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DataFlowViewModel(Store(context.applicationContext), Stats(context.applicationContext)) as T
        },
    )
    val snapshot by vm.state.collectAsState()
    var dark by remember { mutableStateOf(true) }
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF55DDE0),
            secondary = Color(0xFF9B8CFF),
            background = Color(0xFF06101D),
            surface = Color(0xFF101E2D),
        )
    } else lightColorScheme(primary = Color(0xFF006A6D))

    MaterialTheme(colors) {
        if (snapshot.onboardingDone) {
            MainNavigation(vm, snapshot, dark) { dark = it }
        } else {
            Onboarding(vm)
        }
    }
}

@Composable
private fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface),
                ),
            ),
        content = content,
    )
}

@Composable
private fun Onboarding(vm: DataFlowViewModel) {
    val context = LocalContext.current
    var total by remember { mutableStateOf("5") }
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1).toString()) }
    var remaining by remember { mutableStateOf("") }
    var safety by remember { mutableFloatStateOf(20f) }
    var error by remember { mutableStateOf<String?>(null) }

    Backdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("DataFlow", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Configure ton cycle actuel, même s’il a déjà commencé.")
            }
            item {
                OutlinedTextField(total, { total = it }, label = { Text("Forfait total en Go") }, singleLine = true)
            }
            item {
                OutlinedTextField(start, { start = it }, label = { Text("Début du cycle (AAAA-MM-JJ)") }, singleLine = true)
            }
            item {
                OutlinedTextField(end, { end = it }, label = { Text("Fin du cycle (AAAA-MM-JJ)") }, singleLine = true)
            }
            item {
                OutlinedTextField(
                    remaining,
                    { remaining = it },
                    label = { Text("Restant actuel en Go (facultatif)") },
                    supportingText = { Text("Laisse vide pour laisser Android mesurer depuis la date de début.") },
                    singleLine = true,
                )
            }
            item {
                Text("Marge de sécurité : ${safety.toInt()} %")
                Slider(value = safety, onValueChange = { safety = it }, valueRange = 0f..50f)
            }
            item {
                DataCard {
                    Text("Détection automatique", fontWeight = FontWeight.Bold)
                    Text("DataFlow additionne les données mobiles depuis la date de début du cycle. Selon le téléphone et l’opérateur, le compteur Android peut différer du compteur officiel.")
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("Autoriser les statistiques")
                    }
                    Text(if (vm.hasUsageAccess()) "Accès accordé" else "Accès non accordé : la valeur manuelle sera utilisée")
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runCatching {
                            val totalBytes = Units.bytes(total.replace(',', '.').toDouble(), "Go")
                            val startDate = LocalDate.parse(start.trim())
                            val endDate = LocalDate.parse(end.trim())
                            require(!endDate.isBefore(startDate)) { "La date de fin doit être après le début." }
                            val manual = remaining.trim().takeIf { it.isNotEmpty() }?.let {
                                Units.bytes(it.replace(',', '.').toDouble(), "Go").coerceIn(0, totalBytes)
                            }
                            val mode = when {
                                manual != null && vm.hasUsageAccess() -> TrackingMode.HYBRID
                                manual != null -> TrackingMode.MANUAL
                                else -> TrackingMode.AUTOMATIC
                            }
                            Snapshot(
                                plan = Plan(totalBytes, startDate, endDate, safety.toInt(), mode),
                                manualRemaining = manual,
                                onboardingDone = true,
                            )
                        }.onSuccess { configured ->
                            error = null
                            vm.update({ configured }, refreshAfter = true)
                        }.onFailure { error = it.message ?: "Vérifie les valeurs saisies." }
                    },
                ) { Text("Lancer le suivi") }
            }
        }
    }
}

@Composable
private fun MainNavigation(
    vm: DataFlowViewModel,
    snapshot: Snapshot,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val destinations = listOf(
        Triple("Aujourd’hui", Icons.Outlined.Home, 0),
        Triple("Applications", Icons.Outlined.Apps, 1),
        Triple("Historique", Icons.Outlined.BarChart, 2),
        Triple("Réglages", Icons.Outlined.Settings, 3),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination.third,
                        onClick = { tab = destination.third },
                        icon = { Icon(destination.second, contentDescription = destination.first) },
                        label = { Text(destination.first) },
                    )
                }
            }
        },
    ) { padding ->
        Backdrop {
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> TodayScreen(vm, snapshot)
                    1 -> ApplicationsScreen(vm)
                    2 -> HistoryScreen(vm)
                    else -> SettingsScreen(vm, snapshot, dark, onDarkChange)
                }
            }
        }
    }
}

@Composable
private fun DataCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .88f)),
    ) { Column(Modifier.padding(20.dp), content = content) }
}

@Composable
private fun TodayScreen(vm: DataFlowViewModel, snapshot: Snapshot) {
    val remaining = vm.remaining()
    val budget = vm.dailyBudget()
    val usedToday = vm.usedToday()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aujourd’hui", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Cycle du ${snapshot.plan.start} au ${snapshot.plan.end}")
                }
                IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Rafraîchir") }
            }
        }
        item {
            DataCard {
                Text("Budget disponible aujourd’hui")
                Text(Units.format(budget), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                ProgressArc(if (budget > 0) usedToday.toFloat() / budget else 0f)
                Text("${Units.format(usedToday)} utilisés aujourd’hui")
                Text(Engine.status(usedToday, budget), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            DataCard {
                Text("Depuis le début du cycle", fontWeight = FontWeight.Bold)
                Text("Consommé : ${Units.format(vm.usedSinceStart())}")
                Text("Restant : ${Units.format(remaining)}")
                Text("Source : ${vm.origin().name.lowercase()}")
                if (snapshot.measuredPeriod == null && snapshot.manualRemaining == null) {
                    Text("Aucune mesure disponible. Autorise les statistiques ou saisis le restant actuel dans Réglages.")
                }
            }
        }
        item {
            DataCard {
                Text("Projection", fontWeight = FontWeight.Bold)
                val elapsed = maxOf(1L, Duration.between(snapshot.plan.start.atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()).toDays())
                val average = vm.usedSinceStart() / elapsed
                Text(
                    if (average > 0) "À ce rythme, il resterait ${Units.format(Engine.projectedRemaining(remaining, average, snapshot.plan))} à la fin."
                    else "Pas encore assez de données pour une projection.",
                )
            }
        }
    }
}

@Composable
private fun ProgressArc(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().height(130.dp).semantics {
            contentDescription = "Progression quotidienne ${(progress * 100).toInt()} pour cent"
        },
    ) {
        val stroke = 18.dp.toPx()
        drawArc(Color.Gray.copy(.25f), 180f, 180f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(primary, 180f, 180f * progress.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun ApplicationsScreen(vm: DataFlowViewModel) {
    Column(Modifier.padding(20.dp)) {
        Text("Applications", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        DataCard {
            Text("Mesure Android", fontWeight = FontWeight.Bold)
            Text("Le total mobile du cycle est mesuré automatiquement quand Android l’autorise. Le détail fiable par application n’est pas disponible sur tous les appareils.")
            Text(if (vm.hasUsageAccess()) "Accès accordé" else "Accès non accordé")
        }
    }
}

@Composable
private fun HistoryScreen(vm: DataFlowViewModel) {
    val used = vm.usedToday()
    val budget = vm.dailyBudget()
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(20.dp)) {
        Text("Historique", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        DataCard {
            Text("Aujourd’hui", fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val maximum = maxOf(1L, used, budget).toFloat()
                drawLine(Color.Gray, Offset(0f, size.height * (1 - budget / maximum)), Offset(size.width, size.height * (1 - budget / maximum)), strokeWidth = 3f)
                drawRect(primary, Offset(size.width * .35f, size.height * (1 - used / maximum)), Size(size.width * .3f, size.height * used / maximum))
            }
            Text("Utilisé : ${Units.format(used)} — budget : ${Units.format(budget)}")
        }
    }
}

@Composable
private fun SettingsScreen(
    vm: DataFlowViewModel,
    snapshot: Snapshot,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var total by remember(snapshot.plan.totalBytes) { mutableStateOf((snapshot.plan.totalBytes.toDouble() / Units.GB).toString()) }
    var start by remember(snapshot.plan.start) { mutableStateOf(snapshot.plan.start.toString()) }
    var end by remember(snapshot.plan.end) { mutableStateOf(snapshot.plan.end.toString()) }
    var remaining by remember(snapshot.manualRemaining) {
        mutableStateOf(snapshot.manualRemaining?.let { (it.toDouble() / Units.GB).toString() } ?: "")
    }
    var message by remember { mutableStateOf<String?>(null) }

    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> output.write(vm.exportJson().toByteArray()) } }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> output.write(vm.exportCsv().toByteArray()) } }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } ?: error("Fichier illisible") }
                .onSuccess(vm::importJson)
                .onFailure { message = it.message }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Réglages", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item {
            DataCard {
                Text("Cycle et consommation", fontWeight = FontWeight.Bold)
                OutlinedTextField(total, { total = it }, label = { Text("Forfait total en Go") }, singleLine = true)
                OutlinedTextField(start, { start = it }, label = { Text("Début (AAAA-MM-JJ)") }, singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("Fin (AAAA-MM-JJ)") }, singleLine = true)
                OutlinedTextField(
                    remaining,
                    { remaining = it },
                    label = { Text("Restant actuel en Go") },
                    supportingText = { Text("Vide = automatique. Une valeur = correction manuelle prioritaire.") },
                    singleLine = true,
                )
                Button(onClick = {
                    runCatching {
                        val totalBytes = Units.bytes(total.replace(',', '.').toDouble(), "Go")
                        val startDate = LocalDate.parse(start.trim())
                        val endDate = LocalDate.parse(end.trim())
                        require(!endDate.isBefore(startDate))
                        val manual = remaining.trim().takeIf { it.isNotEmpty() }?.let {
                            Units.bytes(it.replace(',', '.').toDouble(), "Go").coerceIn(0, totalBytes)
                        }
                        val mode = when {
                            manual != null && vm.hasUsageAccess() -> TrackingMode.HYBRID
                            manual != null -> TrackingMode.MANUAL
                            else -> TrackingMode.AUTOMATIC
                        }
                        snapshot.copy(
                            plan = snapshot.plan.copy(totalBytes = totalBytes, start = startDate, end = endDate, mode = mode),
                            manualRemaining = manual,
                        )
                    }.onSuccess {
                        vm.update({ it }, refreshAfter = true)
                        message = "Configuration enregistrée"
                    }.onFailure { message = "Valeurs invalides" }
                }) { Text("Enregistrer") }
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Accès aux statistiques Android")
                }
                message?.let { Text(it) }
            }
        }
        item {
            DataCard {
                Text("Affichage", fontWeight = FontWeight.Bold)
                StableSwitch("Thème sombre", dark, onDarkChange)
                StableSwitch("Réduire les effets visuels", snapshot.reduceEffects) { enabled ->
                    vm.update(transform = { it.copy(reduceEffects = enabled) })
                }
            }
        }
        item {
            DataCard {
                Text("Notifications", fontWeight = FontWeight.Bold)
                StableSwitch("Alertes locales", snapshot.notifications) { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.update(transform = { it.copy(notifications = enabled) })
                }
            }
        }
        item {
            DataCard {
                Text("Données", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createJson.launch("DataFlow-backup.json") }) { Text("JSON") }
                    Button(onClick = { createCsv.launch("DataFlow-history.csv") }) { Text("CSV") }
                }
                Button(onClick = { openJson.launch(arrayOf("application/json")) }) { Text("Importer") }
                StableSwitch("Mode démonstration", snapshot.demo) { enabled ->
                    vm.update(transform = { it.copy(demo = enabled) })
                }
            }
        }
        item {
            DataCard {
                Text("Vie privée", fontWeight = FontWeight.Bold)
                Text("Tout reste sur l’appareil. Aucune permission Internet, aucun compte et aucun tracker.")
            }
        }
    }
}

@Composable
private fun StableSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
