package com.futbolcerca.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()
private const val WHATSAPP = "https://wa.me/542615117220"
private val Green = Color(0xFF123B2D)
private val Lime = Color(0xFFC6F36B)
private val Canvas = Color(0xFFF3F6F1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Lime, background = Canvas)) { Root() } }
    }
}

@Composable
private fun Root() {
    var user by remember { mutableStateOf(auth.currentUser) }
    var profile by remember { mutableStateOf<Map<String, Any>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser; profile = null }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(user?.uid) {
        val u = user ?: return@LaunchedEffect
        loading = true
        db.collection("users").document(u.uid).get()
            .addOnSuccessListener { profile = it.data; loading = false }
            .addOnFailureListener { message = it.localizedMessage ?: "No se pudo cargar el perfil"; loading = false }
    }
    when {
        user == null -> AuthScreen({ loading = it }, { message = it })
        loading || profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> Home(user!!.uid, profile!!, { message = it }) { auth.signOut(); profile = null }
    }
    if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Fútbol Cerca") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("Aceptar") } })
}

@Composable
private fun AuthScreen(setLoading: (Boolean) -> Unit, showMessage: (String) -> Unit) {
    var registering by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Jugador") }
    Column(Modifier.fillMaxSize().background(Canvas).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Surface(color = Green, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SportsSoccer, null, tint = Lime, modifier = Modifier.size(48.dp))
                Text("FÚTBOL CERCA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Encontrá equipo. Jugá cerca.", color = Color.White.copy(alpha = .8f))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(if (registering) "Crear cuenta" else "Bienvenido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth())
            Text("¿Cómo vas a usar la app?", fontWeight = FontWeight.SemiBold)
            listOf("Jugador", "Capitán", "Cancha").forEach { r -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(role == r, { role = r }); Text(if (r == "Cancha") "Dueño de cancha" else r) } }
        }
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña · mínimo 6 caracteres") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (email.isBlank() || password.length < 6 || (registering && name.isBlank())) { showMessage("Completá los datos. La contraseña debe tener al menos 6 caracteres."); return@Button }
            setLoading(true)
            val task = if (registering) auth.createUserWithEmailAndPassword(email.trim(), password) else auth.signInWithEmailAndPassword(email.trim(), password)
            task.addOnSuccessListener { result ->
                if (registering) {
                    val data = hashMapOf<String, Any>("displayName" to name.trim(), "role" to role, "verificationStatus" to if (role == "Jugador") "not_required" else "pending", "createdAt" to Timestamp.now(), "trialStartedAt" to Timestamp.now(), "availability" to "Ahora mismo")
                    db.collection("users").document(result.user!!.uid).set(data)
                        .addOnSuccessListener { setLoading(false) }
                        .addOnFailureListener { setLoading(false); showMessage("Cuenta creada, pero no se guardó el perfil: ${it.localizedMessage}") }
                } else setLoading(false)
            }.addOnFailureListener { setLoading(false); showMessage(it.localizedMessage ?: "Error de acceso") }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (registering) "Crear mi cuenta" else "Ingresar") }
        TextButton(onClick = { registering = !registering }) { Text(if (registering) "Ya tengo cuenta · Iniciar sesión" else "Crear cuenta nueva") }
        Text("7 días de prueba · Soporte y licencias CodeMarket", color = Color.Gray, fontSize = 12.sp)
    }
}

private data class MatchData(val id: String, val title: String, val mode: String, val format: String, val dateLabel: String, val venue: String, val zone: String, val price: String, val ownerUid: String, val ownerName: String, val playersNeeded: Int, val lat: Double?, val lon: Double?)
private data class PlayerData(val uid: String, val name: String, val zone: String, val positions: String, val availability: String, val lat: Double?, val lon: Double?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(uid: String, initial: Map<String, Any>, showMessage: (String) -> Unit, logout: () -> Unit) {
    val context = LocalContext.current
    val role = initial["role"] as? String ?: "Jugador"
    var tab by remember { mutableStateOf(if (role == "Jugador") "Mapa" else "Partidos") }
    var profile by remember { mutableStateOf(initial) }
    var matches by remember { mutableStateOf(listOf<MatchData>()) }
    var players by remember { mutableStateOf(listOf<PlayerData>()) }
    var selectedMatch by remember { mutableStateOf<MatchData?>(null) }
    DisposableEffect(uid) {
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("matches").whereEqualTo("status", "open").addSnapshotListener { snap, e ->
            if (e != null) showMessage("Error al cargar partidos: ${e.localizedMessage}") else matches = snap?.documents.orEmpty().map { d ->
                MatchData(d.id, d.getString("title") ?: "Partido", d.getString("mode") ?: "Mixto", d.getString("format") ?: "Fútbol 5", d.getString("dateLabel") ?: d.getString("dateTime") ?: "", d.getString("venue") ?: "", d.getString("zone") ?: "", d.getString("price") ?: "", d.getString("ownerUid") ?: "", d.getString("ownerName") ?: "", (d.getLong("playersNeeded") ?: 1L).toInt(), d.getDouble("lat"), d.getDouble("lon"))
            }
        }
        regs += db.collection("users").whereEqualTo("role", "Jugador").addSnapshotListener { snap, e ->
            if (e != null) showMessage("Error al buscar jugadores: ${e.localizedMessage}") else players = snap?.documents.orEmpty().mapNotNull { d ->
                if (d.id == uid) null else PlayerData(d.id, d.getString("displayName") ?: "Jugador", d.getString("zone") ?: "Zona no indicada", d.getString("positions") ?: "Sin posición", d.getString("availability") ?: "Sin disponibilidad", d.getDouble("lat"), d.getDouble("lon"))
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }
    val tabs = when (role) { "Jugador" -> listOf("Mapa", "Partidos", "Invitaciones", "Perfil"); "Capitán" -> listOf("Partidos", "Mapa", "Jugadores", "Solicitudes", "Publicar", "Perfil"); else -> listOf("Partidos", "Mapa", "Jugadores", "Publicar", "Solicitudes", "Perfil") }
    Scaffold(containerColor = Canvas, topBar = { CenterAlignedTopAppBar(title = { Text("⚽ FÚTBOL CERCA", fontWeight = FontWeight.Black, color = Color.White) }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Green)) }, bottomBar = { NavigationBar(containerColor = Color.White) { tabs.forEach { t -> NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(when(t) { "Mapa" -> Icons.Default.Map; "Perfil" -> Icons.Default.Person; "Jugadores" -> Icons.Default.Groups; "Solicitudes" -> Icons.Default.MarkEmailUnread; "Publicar" -> Icons.Default.AddCircle; else -> Icons.Default.SportsSoccer }, null) }, label = { Text(t, fontSize = 10.sp) }) } } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Column { Text("Hola, ${(profile["displayName"] as? String ?: "") .substringBefore(" ")} 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${if (role == "Cancha") "Administrá tus encuentros" else "El fútbol está más cerca"}", color = Color.Gray) } }
            when (tab) {
                "Mapa" -> {
                    item { Text("Mapa en vivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { InAppMap(players, matches, showMessage) }
                    item { Text("Los marcadores aparecen cuando los usuarios guardan su ubicación desde Perfil. Se muestra una zona aproximada.", color = Color.Gray, fontSize = 12.sp) }
                }
                "Perfil" -> item { ProfileEditor(uid, profile, { profile = it }, showMessage) }
                "Invitaciones" -> item { PlayerInvitations(matches, uid, showMessage) }
                "Publicar" -> item { PublishForm(uid, profile, showMessage) }
                "Jugadores" -> {
                    item { Text("Elegí jugadores para invitar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(players) { p -> PlayerInviteCard(p, matches.filter { it.ownerUid == uid }, showMessage) }
                }
                "Solicitudes" -> {
                    val ownMatches = matches.filter { it.ownerUid == uid }
                    item { Text("Postulaciones recibidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(ownMatches) { m -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(m.title, fontWeight = FontWeight.Bold); Button(onClick = { selectedMatch = m }) { Text("Ver solicitudes") } } } }
                    selectedMatch?.let { m -> item { Applications(m.id, showMessage) } }
                }
                else -> {
                    item { Text(if (role == "Jugador") "Partidos disponibles" else "Mis partidos publicados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    val visible = if (role == "Jugador") matches else matches.filter { it.ownerUid == uid }
                    if (visible.isEmpty()) item { EmptyState("Todavía no hay partidos publicados", "Cuando alguien publique un encuentro, aparecerá acá.") }
                    items(visible) { m -> MatchCard(m, role == "Jugador", uid, profile["displayName"] as? String ?: "Jugador", showMessage) }
                    if (role != "Jugador") item { Button(onClick = { tab = "Publicar" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear partido") } }
                }
            }
            item { Divider(); Text("CodeMarket · Licencias y soporte", color = Color.Gray, fontSize = 12.sp); OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP))) }, modifier = Modifier.fillMaxWidth()) { Text("WhatsApp 2615117220") }; TextButton(onClick = logout) { Text("Cerrar sesión") } }
        }
    }
}

@Composable
private fun InAppMap(players: List<PlayerData>, matches: List<MatchData>, showMessage: (String) -> Unit) {
    val context = LocalContext.current
    AndroidView(modifier = Modifier.fillMaxWidth().height(420.dp), factory = {
        MapView(it).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(12.5); controller.setCenter(GeoPoint(-32.8895, -68.8458)) }
    }, update = { map ->
        map.overlays.clear()
        players.filter { it.lat != null && it.lon != null }.forEach { p ->
            Marker(map).apply { position = GeoPoint(p.lat!!, p.lon!!); title = "Jugador: ${p.name}"; snippet = "${p.zone} · ${p.availability}"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); map.overlays.add(this) }
        }
        matches.filter { it.lat != null && it.lon != null }.forEach { m ->
            Marker(map).apply { position = GeoPoint(m.lat!!, m.lon!!); title = m.title; snippet = "${m.dateLabel} · ${m.venue}"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); map.overlays.add(this) }
        }
        map.invalidate()
    })
}

@Composable
private fun ProfileEditor(uid: String, data: Map<String, Any>, onSaved: (Map<String, Any>) -> Unit, showMessage: (String) -> Unit) {
    var name by remember { mutableStateOf(data["displayName"] as? String ?: "") }
    var age by remember { mutableStateOf((data["age"] as? Long)?.toString() ?: "") }
    var gender by remember { mutableStateOf(data["gender"] as? String ?: "Masculino") }
    var positions by remember { mutableStateOf(data["positions"] as? String ?: "") }
    var skills by remember { mutableStateOf(data["skills"] as? String ?: "") }
    var availability by remember { mutableStateOf(data["availability"] as? String ?: "Ahora mismo") }
    var zone by remember { mutableStateOf(data["zone"] as? String ?: "") }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestCurrentLocation(context) { loc ->
                if (loc != null) {
                    saveLocation(uid, loc) { text, ok ->
                        showMessage(text)
                        if (ok) onSaved(data + mapOf("lat" to loc.latitude, "lon" to loc.longitude))
                    }
                } else showMessage("No se pudo obtener la ubicación. Activá la ubicación del teléfono y probá nuevamente.")
            }
        } else showMessage("Necesitamos permiso de ubicación aproximada para ubicarte en el mapa.")
    }
    Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Mi perfil deportivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(2) }, label = { Text("Edad") }, modifier = Modifier.fillMaxWidth())
        Text("Género"); Row { listOf("Masculino", "Femenino").forEach { FilterChip(gender == it, { gender = it }, label = { Text(it) }); Spacer(Modifier.width(6.dp)) } }
        OutlinedTextField(positions, { positions = it }, label = { Text("Posiciones") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(skills, { skills = it }, label = { Text("Habilidades / experiencia") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Text("Disponibilidad"); listOf("Ahora mismo", "Esta noche", "Mañana", "Fines de semana").forEach { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(availability == it, { availability = it }); Text(it) } }
        OutlinedTextField(zone, { zone = it }, label = { Text("Barrio o zona aproximada") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Actualizar mi ubicación en el mapa") }
        Button(enabled = !saving, onClick = {
            if (name.isBlank()) { showMessage("Ingresá tu nombre."); return@Button }
            saving = true
            val values = hashMapOf<String, Any>("displayName" to name.trim(), "gender" to gender, "positions" to positions.trim(), "skills" to skills.trim(), "availability" to availability, "zone" to zone.trim(), "profileUpdatedAt" to Timestamp.now())
            age.toIntOrNull()?.let { values["age"] = it }
            db.collection("users").document(uid).set(values, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { saving = false; onSaved(data + values); showMessage("Perfil guardado.") }
                .addOnFailureListener { saving = false; showMessage("No se pudo guardar el perfil: ${it.localizedMessage}") }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Guardando…" else "Guardar perfil") }
    } }
}

private fun requestCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        onResult(null); return
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    val token = CancellationTokenSource()
    try {
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
            .addOnSuccessListener { location -> onResult(location) }
            .addOnFailureListener { onResult(null) }
    } catch (_: SecurityException) { onResult(null) }
}
private fun saveLocation(uid: String, loc: Location, show: (String, Boolean) -> Unit) {
    db.collection("users").document(uid).set(mapOf("lat" to loc.latitude, "lon" to loc.longitude, "locationUpdatedAt" to Timestamp.now()), com.google.firebase.firestore.SetOptions.merge())
        .addOnSuccessListener { show("Ubicación actualizada. Ya podés aparecer en el mapa.", true) }
        .addOnFailureListener { show("No se pudo guardar la ubicación: ${it.localizedMessage}", false) }
}

@Composable
private fun PublishForm(uid: String, profile: Map<String, Any>, showMessage: (String) -> Unit) {
    var title by remember { mutableStateOf("") }; var mode by remember { mutableStateOf("Mixto") }; var format by remember { mutableStateOf("Fútbol 5") }
    var date by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var zone by remember { mutableStateOf(profile["zone"] as? String ?: "") }
    var price by remember { mutableStateOf("") }; var needed by remember { mutableStateOf("1") }; var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cal = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0) } }
    fun pickDateTime() {
        DatePickerDialog(context, { _, y, m, d ->
            cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(context, { _, h, min -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(cal.time) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
        }
    }
    Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Crear partido", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("Título de la convocatoria") }, modifier = Modifier.fillMaxWidth())
        Text("Modalidad"); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Masculino", "Femenino", "Mixto").forEach { FilterChip(mode == it, { mode = it }, label = { Text(it, fontSize = 11.sp) }) } }
        Text("Formato"); Row { listOf("Fútbol 5", "Fútbol 7").forEach { FilterChip(format == it, { format = it }, label = { Text(it) }); Spacer(Modifier.width(6.dp)) } }
        OutlinedButton(onClick = { pickDateTime() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(if (date.isBlank()) "Elegir fecha y hora" else date) }
        OutlinedTextField(venue, { venue = it }, label = { Text("Nombre de cancha / lugar") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(zone, { zone = it }, label = { Text("Barrio o zona") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("Costo por jugador (opcional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(needed, { needed = it.filter(Char::isDigit).take(2) }, label = { Text("Jugadores que faltan") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving, onClick = {
            if (title.isBlank() || date.isBlank() || venue.isBlank() || zone.isBlank()) { showMessage("Completá título, fecha, cancha y zona."); return@Button }
            saving = true
            val data = hashMapOf<String, Any>("title" to title.trim(), "mode" to mode, "format" to format, "dateLabel" to date, "dateTime" to date, "eventAt" to Timestamp(cal.time), "venue" to venue.trim(), "zone" to zone.trim(), "price" to price.trim(), "playersNeeded" to (needed.toIntOrNull() ?: 1), "ownerUid" to uid, "ownerName" to (profile["displayName"] as? String ?: "Organizador"), "status" to "open", "createdAt" to Timestamp.now())
            (profile["lat"] as? Double)?.let { data["lat"] = it }; (profile["lon"] as? Double)?.let { data["lon"] = it }
            db.collection("matches").add(data).addOnSuccessListener { saving = false; title = ""; showMessage("¡Partido publicado! Ya está visible para los demás.") }.addOnFailureListener { saving = false; showMessage("No se pudo publicar. Revisá las reglas de Firestore: ${it.localizedMessage}") }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Publicando…" else "Publicar partido") }
    } }
}

@Composable
private fun MatchCard(m: MatchData, canApply: Boolean, uid: String, playerName: String, showMessage: (String) -> Unit) {
    var applied by remember(m.id) { mutableStateOf(false) }
    LaunchedEffect(m.id, uid) { db.collection("matches").document(m.id).collection("applications").document(uid).get().addOnSuccessListener { applied = it.exists() } }
    Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = Color(0xFFE7F4DD), shape = CircleShape) { Text("${m.playersNeeded} VACANTES", Modifier.padding(8.dp), color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.weight(1f)); Text(m.format, color = Green, fontWeight = FontWeight.Bold) }
        Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${m.mode} · ${m.dateLabel}"); Text("${m.venue} · ${m.zone}"); Text("Organiza: ${m.ownerName}"); Text("Costo: ${m.price.ifBlank { "A confirmar" }}")
        if (canApply) Button(enabled = !applied, onClick = { val values = mapOf("uid" to uid, "playerName" to playerName, "status" to "pending", "createdAt" to Timestamp.now()); db.collection("matches").document(m.id).collection("applications").document(uid).set(values).addOnSuccessListener { applied = true; showMessage("Postulación enviada.") }.addOnFailureListener { showMessage("No se pudo postular: ${it.localizedMessage}") } }, modifier = Modifier.fillMaxWidth()) { Text(if (applied) "Postulación enviada" else "Postularme") }
    } }
}

@Composable
private fun PlayerInviteCard(player: PlayerData, ownMatches: List<MatchData>, showMessage: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(player.name, fontWeight = FontWeight.Bold); Text("${player.zone} · ${player.positions}"); Text("Disponible: ${player.availability}"); TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Cerrar" else "Invitar a un partido") }; if (expanded) ownMatches.forEach { m -> TextButton(onClick = { val invite = mapOf("playerUid" to player.uid, "playerName" to player.name, "matchId" to m.id, "matchTitle" to m.title, "ownerUid" to m.ownerUid, "status" to "pending", "createdAt" to Timestamp.now()); db.collection("matches").document(m.id).collection("invitations").document(player.uid).set(invite).addOnSuccessListener { showMessage("Invitación enviada a ${player.name}.") }.addOnFailureListener { showMessage("No se pudo invitar: ${it.localizedMessage}") } }) { Text("${m.title} · Invitar") } } } }
}

@Composable
private fun Applications(matchId: String, showMessage: (String) -> Unit) {
    var applications by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    DisposableEffect(matchId) {
        val reg = db.collection("matches").document(matchId).collection("applications")
            .addSnapshotListener { snapshot, error ->
                if (error != null) showMessage("Error de solicitudes: ${error.localizedMessage}")
                else applications = snapshot?.documents.orEmpty().mapNotNull { it.data }
            }
        onDispose { reg.remove() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Solicitudes recibidas", fontWeight = FontWeight.Bold)
        if (applications.isEmpty()) Text("Todavía no hay postulaciones.")
        applications.forEach { application ->
            val playerUid = application["uid"] as? String ?: ""
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(application["playerName"] as? String ?: "Jugador", fontWeight = FontWeight.Bold)
                    Text("Estado: ${application["status"] ?: "pending"}")
                    Row {
                        Button(onClick = {
                            db.collection("matches").document(matchId).collection("applications").document(playerUid)
                                .update("status", "accepted")
                                .addOnSuccessListener { showMessage("Jugador aceptado.") }
                                .addOnFailureListener { showMessage(it.localizedMessage ?: "Error al aceptar") }
                        }) { Text("Aceptar") }
                        TextButton(onClick = {
                            db.collection("matches").document(matchId).collection("applications").document(playerUid)
                                .update("status", "rejected")
                                .addOnSuccessListener { showMessage("Solicitud rechazada.") }
                                .addOnFailureListener { showMessage(it.localizedMessage ?: "Error al rechazar") }
                        }) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerInvitations(matches: List<MatchData>, uid: String, showMessage: (String) -> Unit) {
    var invitations by remember { mutableStateOf(listOf<MatchData>()) }
    LaunchedEffect(matches.map { it.id }.joinToString(), uid) {
        val found = mutableListOf<MatchData>()
        matches.forEach { match ->
            try {
                val snap = db.collection("matches").document(match.id).collection("invitations").document(uid).get().await()
                if (snap.exists() && snap.getString("status") == "pending") found.add(match)
            } catch (_: Exception) { }
        }
        invitations = found
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Invitaciones recibidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (invitations.isEmpty()) Text("Todavía no tenés invitaciones pendientes.", color = Color.Gray)
        invitations.forEach { match ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(match.title, fontWeight = FontWeight.Bold)
                    Text("${match.dateLabel} · ${match.venue}")
                    Row {
                        Button(onClick = {
                            db.collection("matches").document(match.id).collection("invitations").document(uid)
                                .update("status", "accepted")
                                .addOnSuccessListener { showMessage("Invitación aceptada.") }
                                .addOnFailureListener { showMessage(it.localizedMessage ?: "No se pudo aceptar") }
                        }) { Text("Aceptar") }
                        TextButton(onClick = {
                            db.collection("matches").document(match.id).collection("invitations").document(uid)
                                .update("status", "rejected")
                                .addOnSuccessListener { showMessage("Invitación rechazada.") }
                                .addOnFailureListener { showMessage(it.localizedMessage ?: "No se pudo rechazar") }
                        }) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SportsSoccer, null, tint = Green, modifier = Modifier.size(36.dp)); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray) } } }
