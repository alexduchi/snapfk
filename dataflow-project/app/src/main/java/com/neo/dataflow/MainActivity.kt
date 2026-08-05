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
import androidx.compose.animation.AnimatedContent
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

    init {
        viewModelScope.launch {
            store.flow.collectLatest {
                _state.value = it
                refresh()
            }
        }
    }

    fun update(transform: (Snapshot) -> Snapshot) {
        viewModelScope.launch {
            val next = transform(_state.value)
            _state.value = next
            store.save(next)
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
            val next = current.copy(
                todayUsed = today,
                measuredPeriod = period,
                lastRefresh = if (today != null) now else current.lastRefresh,
            )
            _state.value = next
            store.save(next)
        }
    }

    fun hasUsageAccess(): Boolean = stats.hasAccess()
    fun exportJson(): String = store.export(_state.value)
    fun exportCsv(): String = store.csv(
        listOf(Daily(LocalDate.now().toString(), usedToday(), dailyBudget(), origin())),
    )

    fun importJson(text: String) {
        update { store.import(text).copy(onboardingDone = true) }
    }

    fun remaining(): Long {
        val snapshot = _state.value
        if (snapshot.demo) return 3_420 * Units.MB
        return snapshot.manualRemaining
            ?: snapshot.measuredPeriod?.let { maxOf(0, snapshot.plan.totalBytes - it) }
            ?: snapshot.plan.totalBytes
    }

    fun usedToday(): Long = if (_state.value.demo) 84 * Units.MB else _state.value.todayUsed ?: 0

    fun origin(): DataOrigin = when {
        _state.value.demo -> DataOrigin.ESTIMATED
        _state.value.todayUsed != null -> DataOrigin.MEASURED
        _state.value.manualRemaining != null -> DataOrigin.MANUAL
        else -> DataOrigin.UNAVAILABLE
    }

    fun dailyBudget(): Long = Engine.budget(remaining(), _state.value.plan)
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
    } else {
        lightColorScheme(primary = Color(0xFF006A6D))
    }

    MaterialTheme(colors) {
        if (snapshot.onboardingDone) {
            MainNavigation(vm, snapshot, dark, onDarkChange = { dark = it })
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
    var page by remember { mutableIntStateOf(0) }
    var total by remember { mutableStateOf("5") }
    var days by remember { mutableStateOf("14") }
    var safety by remember { mutableIntStateOf(20) }
    val context = LocalContext.current

    Backdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "DataFlow",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(28.dp))
                AnimatedContent(targetState = page, label = "onboarding") { currentPage ->
                    when (currentPage) {
                        0 -> OnboardingText(
                            "Gardez le contrôle de votre forfait sans graphiques compliqués.",
                            "Tout reste sur votre appareil.",
                        )
                        1 -> NumberInput("Volume disponible en Go", total) { total = it }
                        2 -> NumberInput("Durée en jours", days) { days = it }
                        3 -> Column {
                            OnboardingText("Marge de sécurité", "Elle protège une réserve finale.")
                            Slider(
                                value = safety.toFloat(),
                                onValueChange = { safety = it.toInt() },
                                valueRange = 0f..100f,
                            )
                            Text("$safety %")
                        }
                        4 -> Column {
                            OnboardingText(
                                "Accès Android",
                                "DataFlow lit uniquement les statistiques réseau accessibles. Il ne peut ni couper Internet ni voir votre contenu.",
                            )
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }) { Text("Ouvrir les réglages") }
                            Text(
                                if (vm.hasUsageAccess()) "Accès accordé"
                                else "Accès non accordé — le mode manuel reste disponible",
                            )
                        }
                        else -> OnboardingText(
                            "Prêt à commencer",
                            "Budget et projections seront recalculés localement.",
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (page < 5) {
                        page++
                    } else {
                        val dayCount = (days.toLongOrNull() ?: 14L).coerceAtLeast(1L)
                        val plan = Plan(
                            totalBytes = Units.bytes(total.toDoubleOrNull() ?: 5.0, "Go"),
                            start = LocalDate.now(),
                            end = LocalDate.now().plusDays(dayCount - 1),
                            safety = safety,
                            mode = if (vm.hasUsageAccess()) TrackingMode.AUTOMATIC else TrackingMode.MANUAL,
                        )
                        vm.update { it.copy(plan = plan, onboardingDone = true) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (page == 0) "Commencer" else if (page == 5) "Lancer le suivi" else "Continuer")
            }
        }
    }
}

@Composable
private fun OnboardingText(title: String, body: String) {
    Column {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NumberInput(title: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        OnboardingText(title, "Saisissez une valeur positive.")
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Valeur") },
            singleLine = true,
        )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
        ),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun TodayScreen(vm: DataFlowViewModel, snapshot: Snapshot) {
    val remaining = vm.remaining()
    val budget = vm.dailyBudget()
    val used = vm.usedToday()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aujourd’hui", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    if (snapshot.demo) {
                        AssistChip(onClick = {}, label = { Text("Données de démonstration") })
                    }
                }
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Rafraîchir")
                }
            }
        }
        item {
            DataCard {
                Text("Tu peux utiliser environ", style = MaterialTheme.typography.titleMedium)
                Text(
                    Units.format(budget),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(14.dp))
                ProgressArc(if (budget > 0) used.toFloat() / budget else 0f)
                Text("${Units.format(used)} utilisés aujourd’hui")
                Text(Engine.status(used, budget), fontWeight = FontWeight.SemiBold)
                Text("Origine : ${vm.origin().name.lowercase()}")
            }
        }
        item {
            DataCard {
                Text("Projection", fontWeight = FontWeight.Bold)
                val elapsed = maxOf(
                    1L,
                    Duration.between(
                        snapshot.plan.start.atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay(),
                    ).toDays(),
                )
                val average = snapshot.measuredPeriod?.div(elapsed) ?: 0
                Text(
                    if (average > 0) {
                        "À ce rythme, il resterait environ ${Units.format(Engine.projectedRemaining(remaining, average, snapshot.plan))} le dernier jour."
                    } else {
                        "Les données sont insuffisantes pour établir une projection fiable."
                    },
                )
            }
        }
        item {
            DataCard {
                Text("Résumé", fontWeight = FontWeight.Bold)
                Text("Restant : ${Units.format(remaining)}")
                Text("Jours restants : ${Engine.daysRemaining(snapshot.plan)}")
                Text("Réserve : ${Units.format(Engine.reserve(snapshot.plan))}")
                val refreshText = if (snapshot.lastRefresh == 0L) {
                    "jamais"
                } else {
                    java.text.DateFormat.getDateTimeInstance().format(snapshot.lastRefresh)
                }
                Text("Dernière actualisation : $refreshText")
            }
        }
        item { SimulatorCard() }
    }
}

@Composable
private fun ProgressArc(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .semantics {
                contentDescription = "Progression du budget quotidien ${(progress * 100).toInt()} pour cent"
            },
    ) {
        val strokeWidth = 18.dp.toPx()
        drawArc(
            color = Color.Gray.copy(alpha = .25f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = primary,
            startAngle = 180f,
            sweepAngle = 180f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun SimulatorCard() {
    var minutes by remember { mutableStateOf("60") }
    var activity by remember { mutableStateOf("YouTube 720p") }
    var menuOpen by remember { mutableStateOf(false) }
    val rates = mapOf(
        "YouTube 720p" to Triple(500, 900, 1500),
        "Musique" to Triple(40, 100, 180),
        "Navigation GPS" to Triple(5, 15, 40),
        "ChatGPT texte" to Triple(1, 5, 20),
    )
    DataCard {
        Text("Simuler", fontWeight = FontWeight.Bold)
        Button(onClick = { menuOpen = true }) { Text(activity) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            rates.keys.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { activity = name; menuOpen = false },
                )
            }
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it },
            label = { Text("Minutes") },
            singleLine = true,
        )
        val rate = rates.getValue(activity)
        val factor = (minutes.toDoubleOrNull() ?: 0.0) / 60.0
        Text(
            "Estimation : ${Units.format((rate.first * factor * Units.MB).toLong())} à ${Units.format((rate.third * factor * Units.MB).toLong())}",
        )
        Text("Valeur centrale : ${Units.format((rate.second * factor * Units.MB).toLong())}")
    }
}

@Composable
private fun ApplicationsScreen(vm: DataFlowViewModel) {
    Column(Modifier.padding(20.dp)) {
        Text("Applications", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        DataCard {
            Text("Détail par application", fontWeight = FontWeight.Bold)
            Text("Les données détaillées par application ne sont pas disponibles de façon fiable sur tous les appareils. DataFlow n’invente aucun nom ni volume.")
            Text(
                if (vm.hasUsageAccess()) "Accès aux statistiques accordé."
                else "L’accès aux statistiques d’utilisation est nécessaire.",
            )
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
            Text("Consommation quotidienne", fontWeight = FontWeight.Bold)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics {
                        contentDescription = "Aujourd’hui ${Units.format(used)}, budget ${Units.format(budget)}"
                    },
            ) {
                val maximum = maxOf(1L, used, budget).toFloat()
                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, size.height * (1 - budget / maximum)),
                    end = Offset(size.width, size.height * (1 - budget / maximum)),
                    strokeWidth = 3f,
                )
                drawRect(
                    color = primary,
                    topLeft = Offset(size.width * .35f, size.height * (1 - used / maximum)),
                    size = Size(size.width * .3f, size.height * used / maximum),
                )
            }
            Text("Aujourd’hui : ${Units.format(used)} — budget ${Units.format(budget)}")
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
    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { output ->
                output.write(vm.exportJson().toByteArray())
            }
        }
    }
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { output ->
                output.write(vm.exportCsv().toByteArray())
            }
        }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                    ?: error("Fichier illisible")
            }.onSuccess(vm::importJson)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Réglages", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item {
            DataCard {
                Text("Forfait", fontWeight = FontWeight.Bold)
                Text("${Units.format(snapshot.plan.totalBytes)} · ${snapshot.plan.safety}% de réserve · ${snapshot.plan.mode}")
                Button(onClick = {
                    vm.update {
                        it.copy(
                            manualRemaining = maxOf(0, vm.remaining() - 100 * Units.MB),
                            plan = it.plan.copy(mode = TrackingMode.MANUAL),
                        )
                    }
                }) { Text("Ajouter un relevé manuel (-100 Mo)") }
            }
        }
        item {
            DataCard {
                Text("Affichage", fontWeight = FontWeight.Bold)
                SettingSwitch("Thème sombre", dark, onDarkChange)
                SettingSwitch("Réduire les effets visuels", snapshot.reduceEffects) {
                    vm.update { state -> state.copy(reduceEffects = it) }
                }
            }
        }
        item {
            DataCard {
                Text("Notifications", fontWeight = FontWeight.Bold)
                SettingSwitch("Alertes locales", snapshot.notifications) { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.update { it.copy(notifications = enabled) }
                }
                Text("Android peut retarder les vérifications en arrière-plan.")
            }
        }
        item {
            DataCard {
                Text("Données", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createJson.launch("DataFlow-backup.json") }) { Text("Export JSON") }
                    Button(onClick = { createCsv.launch("DataFlow-history.csv") }) { Text("CSV") }
                }
                Button(onClick = { openJson.launch(arrayOf("application/json")) }) {
                    Text("Importer JSON")
                }
                SettingSwitch("Mode démonstration", snapshot.demo) {
                    vm.update { state -> state.copy(demo = it) }
                }
            }
        }
        item {
            DataCard {
                Text("Vie privée", fontWeight = FontWeight.Bold)
                Text("Données stockées uniquement sur l’appareil. Aucune vente, synchronisation externe, collecte analytique ou transmission à un serveur. DataFlow ne déclare pas la permission INTERNET.")
            }
        }
        item {
            DataCard {
                Text("Limites Android", fontWeight = FontWeight.Bold)
                Text("Les compteurs peuvent être retardés ou indisponibles selon le constructeur, la SIM et la version Android. DataFlow ne peut pas couper les données d’autres applications.")
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) { Text("Accès aux statistiques") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
