@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.futbolcerca.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()
private const val WHATSAPP = "https://wa.me/542615117220"
private val Green = Color(0xFF123B2D)
private val Lime = Color(0xFFC6F36B)
private val Canvas = Color(0xFFF3F6F1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(18, 59, 45)))
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
    Column(Modifier.fillMaxSize().background(Canvas).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Surface(color = Green, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_cancha), null, tint = Color.Unspecified, modifier = Modifier.size(56.dp))
                Text("FÚTBOL CERCA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Encontrá equipo. Jugá cerca.", color = Color.White.copy(alpha = .8f))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(if (registering) "Crear cuenta" else "Bienvenido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth())
            Text("¿Cómo vas a usar la app?", fontWeight = FontWeight.SemiBold)
            listOf("Jugador", "Capitán", "Cancha").forEach { r -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(role == r, { role = r }); Text(if (r == "Cancha") "Dueño de cancha" else r, Modifier.weight(1f)) } }
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
        Text("7 días de prueba · Soporte y licencias CodeMarket", color = Color.Gray, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private data class MatchData(val id: String, val title: String, val mode: String, val format: String, val dateLabel: String, val venue: String, val zone: String, val price: String, val ownerUid: String, val ownerName: String, val playersNeeded: Int, val status: String, val lat: Double?, val lon: Double?, val zoneLat: Double?, val zoneLon: Double?, val zoneRadius: Double?)
private data class PlayerData(val uid: String, val name: String, val zone: String, val positions: String, val availability: String, val lat: Double?, val lon: Double?, val zoneLat: Double?, val zoneLon: Double?, val zoneRadius: Double?)

// ---------- Zonas en el mapa ----------
private const val DEFAULT_CITY = "Mendoza"
private const val MIN_ZONE_RADIUS = 600.0     // metros: nunca se muestra un punto exacto
private const val MAX_ZONE_RADIUS = 2500.0
private const val SNAP_DEGREES = 0.004         // ~440 m: el centro del círculo se "redondea" para no delatar una dirección

private data class GeoZone(val lat: Double, val lon: Double, val radius: Double)
private data class MapItem(val isMatch: Boolean, val title: String, val detail: String, val lat: Double, val lon: Double, val radius: Double)
private data class ZoneCluster(val lat: Double, val lon: Double, val radius: Double, val items: List<MapItem>)

private fun distMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
    return 2 * 6371000.0 * asin(sqrt(h))
}

/** Convierte un texto (barrio, zona o dirección) en un centro + radio usando OpenStreetMap Nominatim. */
private suspend fun geocodeZone(vararg queries: String): GeoZone? = withContext(Dispatchers.IO) {
    var attempts = 0
    for (raw in queries) {
        val q = raw.trim()
        if (q.isBlank()) continue
        if (attempts++ > 0) delay(1100) // Nominatim permite 1 consulta por segundo
        val full = if (q.contains(DEFAULT_CITY, ignoreCase = true)) q else "$q, $DEFAULT_CITY, Argentina"
        try {
            val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=ar&q=" + URLEncoder.encode(full, "UTF-8"))
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "FutbolCerca/2.2 (com.futbolcerca.app)")
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() > 0) {
                val o = arr.getJSONObject(0)
                val lat = o.getString("lat").toDouble(); val lon = o.getString("lon").toDouble()
                var radius = MIN_ZONE_RADIUS
                val bb = o.optJSONArray("boundingbox")
                if (bb != null && bb.length() == 4) {
                    val south = bb.getString(0).toDouble(); val north = bb.getString(1).toDouble(); val west = bb.getString(2).toDouble(); val east = bb.getString(3).toDouble()
                    val diag = distMeters(south, west, north, east)
                    radius = (diag / 2).coerceIn(MIN_ZONE_RADIUS, MAX_ZONE_RADIUS)
                }
                return@withContext GeoZone(lat, lon, radius)
            }
        } catch (_: Exception) { }
    }
    // Respaldo: si el servicio de mapas no responde, se reconocen los departamentos del Gran Mendoza.
    val text = java.text.Normalizer.normalize(queries.joinToString(" ").lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    FALLBACK_ZONES.entries.firstOrNull { text.contains(it.key) }?.value
}

private val FALLBACK_ZONES: Map<String, GeoZone> = linkedMapOf(
    "chacras de coria" to GeoZone(-33.0063, -68.8790, 1500.0),
    "dorrego" to GeoZone(-32.9172, -68.8031, 1200.0),
    "godoy cruz" to GeoZone(-32.9269, -68.8419, 2500.0),
    "guaymallen" to GeoZone(-32.8997, -68.7747, 2500.0),
    "las heras" to GeoZone(-32.8512, -68.8283, 2500.0),
    "lujan" to GeoZone(-33.0386, -68.8740, 2500.0),
    "maipu" to GeoZone(-32.9772, -68.7897, 2500.0),
    "lavalle" to GeoZone(-32.7216, -68.6000, 2500.0),
    "san martin" to GeoZone(-33.0812, -68.4700, 2500.0),
    "capital" to GeoZone(-32.8895, -68.8458, 2000.0),
    "ciudad de mendoza" to GeoZone(-32.8895, -68.8458, 2000.0),
    "mendoza" to GeoZone(-32.8895, -68.8458, 2500.0)
)

/** Agrupa personas/partidos que caen en la misma zona. Cada grupo se dibuja como un círculo con su cantidad. */
private fun buildZones(items: List<MapItem>): List<ZoneCluster> {
    class Group(var lat: Double, var lon: Double, var radius: Double, val members: MutableList<MapItem>)
    val groups = mutableListOf<Group>()
    items.forEach { item ->
        val g = groups.firstOrNull { distMeters(it.lat, it.lon, item.lat, item.lon) <= max(it.radius, item.radius) }
        if (g == null) groups += Group(item.lat, item.lon, item.radius, mutableListOf(item))
        else {
            g.members += item
            g.lat = g.members.map { it.lat }.average(); g.lon = g.members.map { it.lon }.average()
            g.radius = max(g.radius, item.radius)
        }
    }
    return groups.map { g ->
        val sLat = (g.lat / SNAP_DEGREES).roundToInt() * SNAP_DEGREES
        val sLon = (g.lon / SNAP_DEGREES).roundToInt() * SNAP_DEGREES
        val reach = g.members.maxOf { distMeters(sLat, sLon, it.lat, it.lon) + 350.0 }
        ZoneCluster(sLat, sLon, max(g.radius, reach).coerceAtMost(MAX_ZONE_RADIUS * 2), g.members.toList())
    }
}

private fun countIcon(context: Context, count: Int): BitmapDrawable {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Green.toArgb(); canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f, paint)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f, paint)
    paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 46f; paint.isFakeBoldText = true
    canvas.drawText(count.toString(), size / 2f, size / 2f + 16f, paint)
    return BitmapDrawable(context.resources, bmp)
}

private data class AppRow(val uid: String, val name: String, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(uid: String, initial: Map<String, Any>, showMessage: (String) -> Unit, logout: () -> Unit) {
    val context = LocalContext.current
    val role = initial["role"] as? String ?: "Jugador"
    var tab by remember { mutableStateOf(if (role == "Jugador") "Mapa" else "Partidos") }
    var profile by remember { mutableStateOf(initial) }
    var matches by remember { mutableStateOf(listOf<MatchData>()) }
    var players by remember { mutableStateOf(listOf<PlayerData>()) }
    var selectedMatchId by remember { mutableStateOf<String?>(null) }
    var selectedZone by remember { mutableStateOf<ZoneCluster?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Autoreparación: si hay zona escrita pero nunca se convirtió a coordenadas, se calcula ahora.
    LaunchedEffect(uid, profile["zone"]) {
        val z = (profile["zone"] as? String).orEmpty().trim()
        if (z.isNotBlank() && profile["zoneLat"] == null) {
            val g = geocodeZone(z)
            if (g != null) {
                db.collection("users").document(uid).set(mapOf("zoneLat" to g.lat, "zoneLon" to g.lon, "zoneRadius" to g.radius), SetOptions.merge())
                profile = profile + mapOf("zoneLat" to g.lat, "zoneLon" to g.lon, "zoneRadius" to g.radius)
            }
        }
    }
    val healedMatches = remember { mutableSetOf<String>() }
    LaunchedEffect(matches) {
        matches.filter { it.ownerUid == uid && it.zoneLat == null && it.zone.isNotBlank() && healedMatches.add(it.id) }.forEach { m ->
            val g = geocodeZone("${m.venue}, ${m.zone}", m.zone)
            if (g != null) db.collection("matches").document(m.id).set(mapOf("zoneLat" to g.lat, "zoneLon" to g.lon, "zoneRadius" to g.radius), SetOptions.merge())
        }
    }
    DisposableEffect(uid) {
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("matches").whereIn("status", listOf("open", "full")).addSnapshotListener { snap, e ->
            if (e != null) syncError = "Partidos: ${e.localizedMessage}" else { syncError = null; matches = snap?.documents.orEmpty().map { d ->
                MatchData(d.id, d.getString("title") ?: "Partido", d.getString("mode") ?: "Mixto", d.getString("format") ?: "Fútbol 5", d.getString("dateLabel") ?: d.getString("dateTime") ?: "", d.getString("venue") ?: "", d.getString("zone") ?: "", d.getString("price") ?: "", d.getString("ownerUid") ?: "", d.getString("ownerName") ?: "", (d.getLong("playersNeeded") ?: 1L).toInt(), d.getString("status") ?: "open", d.getDouble("lat"), d.getDouble("lon"), d.getDouble("zoneLat"), d.getDouble("zoneLon"), d.getDouble("zoneRadius"))
            } }
        }
        regs += db.collection("users").whereEqualTo("role", "Jugador").addSnapshotListener { snap, e ->
            if (e != null) syncError = "Jugadores: ${e.localizedMessage}" else players = snap?.documents.orEmpty().mapNotNull { d ->
                if (d.id == uid) null else PlayerData(d.id, d.getString("displayName") ?: "Jugador", d.getString("zone") ?: "Zona no indicada", d.getString("positions") ?: "Sin posición", d.getString("availability") ?: "Sin disponibilidad", d.getDouble("lat"), d.getDouble("lon"), d.getDouble("zoneLat"), d.getDouble("zoneLon"), d.getDouble("zoneRadius"))
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }
    val tabs = when (role) { "Jugador" -> listOf("Mapa", "Partidos", "Invitaciones", "Perfil"); "Capitán" -> listOf("Partidos", "Mapa", "Jugadores", "Solicitudes", "Publicar", "Perfil"); else -> listOf("Partidos", "Mapa", "Jugadores", "Publicar", "Solicitudes", "Perfil") }
    fun tabLabel(t: String) = when (t) { "Solicitudes" -> "Pedidos"; "Publicar" -> "Crear"; "Invitaciones" -> "Invitac."; else -> t }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Canvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_cancha), null, tint = Color.Unspecified, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("FÚTBOL CERCA", fontWeight = FontWeight.Black, color = Color.White, maxLines = 1) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Green)
            )
        },
        bottomBar = {
            if (!imeVisible) NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t },
                        icon = { Icon(when (t) { "Mapa" -> Icons.Default.Map; "Perfil" -> Icons.Default.Person; "Jugadores" -> Icons.Default.Groups; "Solicitudes" -> Icons.Default.MarkEmailUnread; "Publicar" -> Icons.Default.AddCircle; else -> Icons.Default.SportsSoccer }, null) },
                        label = { Text(tabLabel(t), fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) })
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            syncError?.let { err -> item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0DD))) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text("No se pueden leer los datos", fontWeight = FontWeight.Bold, color = Color(0xFF8A1C12)); Text(err, fontSize = 12.sp); Text("Si dice PERMISSION_DENIED: las reglas publicadas en Firebase no son las de firestore.rules. Publicalas en Firebase Console → Firestore Database → Reglas.", fontSize = 12.sp, color = Color.DarkGray) } } } }
            item { Column { Text("Hola, ${(profile["displayName"] as? String ?: "") .substringBefore(" ")} 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${if (role == "Cancha") "Administrá tus encuentros" else "El fútbol está más cerca"}", color = Color.Gray) } }
            when (tab) {
                "Mapa" -> {
                    item { Text("Mapa en vivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { InAppMap(players, matches) { selectedZone = it } }
                    item {
                        val pz = players.count { (it.zoneLat ?: it.lat) != null }; val mz = matches.count { (it.zoneLat ?: it.lat) != null }
                        Text("Jugadores: ${players.size} (${pz} con zona) · Partidos: ${matches.size} (${mz} con zona)", fontSize = 12.sp, color = Color.DarkGray)
                        if (players.size > pz || matches.size > mz) Text("Los que no tienen zona no se dibujan: cada usuario debe cargar su barrio o zona en Perfil (o Publicar).", fontSize = 12.sp, color = Color.Gray)
                    }
                    selectedZone?.let { z -> item { ZoneDetail(z) { selectedZone = null } } }
                    item { Text("Cada círculo es una zona. El número indica cuántos jugadores y partidos hay ahí; tocá el círculo para ver quiénes son. Nunca se muestra una dirección exacta.", color = Color.Gray, fontSize = 12.sp) }
                }
                "Perfil" -> item { ProfileEditor(uid, profile, { profile = it }, showMessage) }
                "Invitaciones" -> item { PlayerInvitations(matches, uid, profile["displayName"] as? String ?: "Jugador", showMessage) }
                "Publicar" -> item { PublishForm(uid, profile, showMessage) }
                "Jugadores" -> {
                    item { Text("Elegí jugadores para invitar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(players) { p -> PlayerInviteCard(p, matches.filter { it.ownerUid == uid && it.status == "open" }, showMessage) }
                }
                "Solicitudes" -> {
                    val ownMatches = matches.filter { it.ownerUid == uid }
                    item { Text("Postulaciones recibidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (ownMatches.isEmpty()) item { EmptyState("No tenés partidos publicados", "Publicá un partido para recibir postulaciones e invitar jugadores.") }
                    items(ownMatches) { m -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(m.title, fontWeight = FontWeight.Bold); Text(if (m.status == "full") "COMPLETO" else "${m.playersNeeded} vacantes", color = Color.Gray); Button(onClick = { selectedMatchId = if (selectedMatchId == m.id) null else m.id }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedMatchId == m.id) "Ocultar" else "Ver pedidos y confirmados") } } } }
                    ownMatches.firstOrNull { it.id == selectedMatchId }?.let { m -> item(key = "panel-${m.id}") { MatchManagement(m, showMessage) } }
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
private fun InAppMap(players: List<PlayerData>, matches: List<MatchData>, onSelect: (ZoneCluster?) -> Unit) {
    val context = LocalContext.current
    val zones = remember(players, matches) {
        val items = mutableListOf<MapItem>()
        players.forEach { p ->
            val lat = p.zoneLat ?: p.lat; val lon = p.zoneLon ?: p.lon
            if (lat != null && lon != null) items += MapItem(false, p.name, "${p.zone} · ${p.positions} · ${p.availability}", lat, lon, p.zoneRadius ?: 700.0)
        }
        matches.forEach { m ->
            val lat = m.zoneLat ?: m.lat; val lon = m.zoneLon ?: m.lon
            if (lat != null && lon != null) items += MapItem(true, m.title, "${m.dateLabel} · ${m.venue} · ${m.zone}", lat, lon, m.zoneRadius ?: 700.0)
        }
        buildZones(items)
    }
    AndroidView(modifier = Modifier.fillMaxWidth().height(380.dp), factory = {
        MapView(it).apply { setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }; setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(12.5); controller.setCenter(GeoPoint(-32.8895, -68.8458)) }
    }, update = { map ->
        map.overlays.clear()
        zones.forEach { z ->
            val center = GeoPoint(z.lat, z.lon)
            val circle = Polygon(map)
            circle.points = Polygon.pointsAsCircle(center, z.radius)
            circle.fillPaint.color = android.graphics.Color.argb(70, 18, 59, 45)
            circle.outlinePaint.color = android.graphics.Color.argb(200, 18, 59, 45)
            circle.outlinePaint.strokeWidth = 4f
            circle.setOnClickListener { _, _, _ -> onSelect(z); true }
            map.overlays.add(circle)
            val badge = Marker(map)
            badge.position = center
            badge.icon = countIcon(context, z.items.size)
            badge.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            badge.setOnMarkerClickListener { _, _ -> onSelect(z); true }
            map.overlays.add(badge)
        }
        if (zones.isNotEmpty() && map.tag != zones) {
            map.tag = zones
            val pts = zones.flatMap { z ->
                val dLat = z.radius / 111320.0; val dLon = z.radius / (111320.0 * cos(Math.toRadians(z.lat)))
                listOf(GeoPoint(z.lat + dLat, z.lon), GeoPoint(z.lat - dLat, z.lon), GeoPoint(z.lat, z.lon + dLon), GeoPoint(z.lat, z.lon - dLon))
            }
            map.post { try { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 60) } catch (_: Exception) { } }
        }
        map.invalidate()
    })
}

@Composable
private fun ZoneDetail(zone: ZoneCluster, onClose: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("En esta zona (${zone.items.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Cerrar") }
        }
        zone.items.forEach { it -> Text("${if (it.isMatch) "⚽ Partido" else "👤 Jugador"}: ${it.title}", fontWeight = FontWeight.SemiBold); Text(it.detail, color = Color.Gray, fontSize = 12.sp) }
    } }
}

@Composable
private fun ProfileEditor(uid: String, data: Map<String, Any>, onSaved: (Map<String, Any>) -> Unit, showMessage: (String) -> Unit) {
    var name by remember { mutableStateOf(data["displayName"] as? String ?: "") }
    var age by remember { mutableStateOf((data["age"] as? Number)?.toInt()?.toString() ?: "") }
    var gender by remember { mutableStateOf(data["gender"] as? String ?: "Masculino") }
    var positions by remember { mutableStateOf(data["positions"] as? String ?: "") }
    var skills by remember { mutableStateOf(data["skills"] as? String ?: "") }
    var availability by remember { mutableStateOf(data["availability"] as? String ?: "Ahora mismo") }
    var zone by remember { mutableStateOf(data["zone"] as? String ?: "") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
        Text("Género"); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Masculino", "Femenino").forEach { FilterChip(gender == it, { gender = it }, label = { Text(it) }) } }
        OutlinedTextField(positions, { positions = it }, label = { Text("Posiciones") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(skills, { skills = it }, label = { Text("Habilidades / experiencia") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Text("Disponibilidad"); listOf("Ahora mismo", "Esta noche", "Mañana", "Fines de semana").forEach { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(availability == it, { availability = it }); Text(it, Modifier.weight(1f)) } }
        OutlinedTextField(zone, { zone = it }, label = { Text("Barrio, zona o dirección (se muestra como zona)") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Usar mi ubicación actual", Modifier.weight(1f, fill = false)) }
        Button(enabled = !saving, onClick = {
            if (name.isBlank()) { showMessage("Ingresá tu nombre."); return@Button }
            saving = true
            scope.launch {
                val values = hashMapOf<String, Any>("displayName" to name.trim(), "gender" to gender, "positions" to positions.trim(), "skills" to skills.trim(), "availability" to availability, "zone" to zone.trim(), "profileUpdatedAt" to Timestamp.now())
                age.toIntOrNull()?.let { values["age"] = it }
                val local = HashMap(data); local.putAll(values)
                val remote = HashMap<String, Any>(values)
                var note = ""
                val zoneText = zone.trim()
                val zoneChanged = zoneText != (data["zone"] as? String ?: "") || data["zoneLat"] == null
                if (zoneText.isBlank()) {
                    remote["zoneLat"] = FieldValue.delete(); remote["zoneLon"] = FieldValue.delete(); remote["zoneRadius"] = FieldValue.delete()
                    local.remove("zoneLat"); local.remove("zoneLon"); local.remove("zoneRadius")
                } else if (zoneChanged) {
                    val g = geocodeZone(zoneText)
                    if (g != null) {
                        remote["zoneLat"] = g.lat; remote["zoneLon"] = g.lon; remote["zoneRadius"] = g.radius
                        local["zoneLat"] = g.lat; local["zoneLon"] = g.lon; local["zoneRadius"] = g.radius
                    } else {
                        remote["zoneLat"] = FieldValue.delete(); remote["zoneLon"] = FieldValue.delete(); remote["zoneRadius"] = FieldValue.delete()
                        local.remove("zoneLat"); local.remove("zoneLon"); local.remove("zoneRadius")
                        note = " No pude ubicar esa zona en el mapa: probá con barrio y ciudad (ej. \"Dorrego, Guaymallén\")."
                    }
                }
                db.collection("users").document(uid).set(remote, SetOptions.merge())
                    .addOnSuccessListener { saving = false; onSaved(local); showMessage("Perfil guardado.$note") }
                    .addOnFailureListener { saving = false; showMessage("No se pudo guardar el perfil: ${it.localizedMessage}") }
            }
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
    val scope = rememberCoroutineScope()
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
        Text("Modalidad"); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Masculino", "Femenino", "Mixto").forEach { FilterChip(mode == it, { mode = it }, label = { Text(it) }) } }
        Text("Formato"); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Fútbol 5", "Fútbol 7").forEach { FilterChip(format == it, { format = it }, label = { Text(it) }) } }
        OutlinedButton(onClick = { pickDateTime() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(if (date.isBlank()) "Elegir fecha y hora" else date) }
        OutlinedTextField(venue, { venue = it }, label = { Text("Nombre de cancha / lugar") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(zone, { zone = it }, label = { Text("Barrio, zona o dirección") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("Costo por jugador (opcional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(needed, { needed = it.filter(Char::isDigit).take(2) }, label = { Text("Jugadores que faltan") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving, onClick = {
            if (title.isBlank() || date.isBlank() || venue.isBlank() || zone.isBlank()) { showMessage("Completá título, fecha, cancha y zona."); return@Button }
            saving = true
            scope.launch {
                val data = hashMapOf<String, Any>("title" to title.trim(), "mode" to mode, "format" to format, "dateLabel" to date, "dateTime" to date, "eventAt" to Timestamp(cal.time), "venue" to venue.trim(), "zone" to zone.trim(), "price" to price.trim(), "playersNeeded" to (needed.toIntOrNull() ?: 1).coerceAtLeast(1), "ownerUid" to uid, "ownerName" to (profile["displayName"] as? String ?: "Organizador"), "status" to "open", "createdAt" to Timestamp.now())
                val g = geocodeZone("${venue.trim()}, ${zone.trim()}", zone.trim())
                if (g != null) { data["zoneLat"] = g.lat; data["zoneLon"] = g.lon; data["zoneRadius"] = g.radius }
                else { (profile["lat"] as? Double)?.let { data["lat"] = it }; (profile["lon"] as? Double)?.let { data["lon"] = it } }
                db.collection("matches").add(data)
                    .addOnSuccessListener { saving = false; title = ""; showMessage(if (g != null) "¡Partido publicado! Ya está visible en el mapa y para los demás." else "¡Partido publicado! No pude ubicar la zona en el mapa; probá con barrio y ciudad.") }
                    .addOnFailureListener { saving = false; showMessage("No se pudo publicar. Revisá las reglas de Firestore: ${it.localizedMessage}") }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Publicando…" else "Publicar partido") }
    } }
}

@Composable
private fun MatchCard(m: MatchData, canApply: Boolean, uid: String, playerName: String, showMessage: (String) -> Unit) {
    var appStatus by remember(m.id) { mutableStateOf<String?>(null) }
    var joined by remember(m.id) { mutableStateOf(false) }
    DisposableEffect(m.id, uid, canApply) {
        if (!canApply) return@DisposableEffect onDispose { }
        val base = db.collection("matches").document(m.id)
        val r1 = base.collection("applications").document(uid).addSnapshotListener { s, _ -> appStatus = if (s != null && s.exists()) s.getString("status") else null }
        val r2 = base.collection("participants").document(uid).addSnapshotListener { s, _ -> joined = s?.exists() == true }
        onDispose { r1.remove(); r2.remove() }
    }
    val full = m.status == "full"
    Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = if (full) Color(0xFFFFE0DD) else Color(0xFFE7F4DD), shape = CircleShape) { Text(if (full) "COMPLETO" else "${m.playersNeeded} VACANTES", Modifier.padding(8.dp), color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.weight(1f)); Text(m.format, color = Green, fontWeight = FontWeight.Bold, maxLines = 1) }
        Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${m.mode} · ${m.dateLabel}"); Text("${m.venue} · ${m.zone}"); Text("Organiza: ${m.ownerName}"); Text("Costo: ${m.price.ifBlank { "A confirmar" }}")
        if (canApply) {
            val label = when {
                joined || appStatus == "accepted" -> "✓ Confirmado en este partido"
                appStatus == "pending" -> "Postulación enviada · esperando respuesta"
                appStatus == "rejected" -> "Postulación rechazada"
                full -> "Partido completo"
                else -> "Postularme"
            }
            val enabled = !joined && appStatus == null && !full
            Button(enabled = enabled, onClick = {
                val values = mapOf("uid" to uid, "playerName" to playerName, "status" to "pending", "createdAt" to Timestamp.now())
                db.collection("matches").document(m.id).collection("applications").document(uid).set(values)
                    .addOnSuccessListener { showMessage("Postulación enviada. El organizador la verá en su pestaña Solicitudes.") }
                    .addOnFailureListener { showMessage("No se pudo postular: ${it.localizedMessage}") }
            }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    } }
}

@Composable
private fun PlayerInviteCard(player: PlayerData, ownMatches: List<MatchData>, showMessage: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(player.name, fontWeight = FontWeight.Bold); Text("${player.zone} · ${player.positions}"); Text("Disponible: ${player.availability}")
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Cerrar" else "Invitar a un partido") }
        if (expanded) {
            if (ownMatches.isEmpty()) Text("Necesitás un partido abierto para invitar.", color = Color.Gray)
            ownMatches.forEach { m ->
                TextButton(onClick = {
                    val ref = db.collection("matches").document(m.id).collection("invitations").document(player.uid)
                    ref.get().addOnSuccessListener { existing ->
                        if (existing.exists()) {
                            showMessage("Ya invitaste a ${player.name} a este partido (estado: ${statusLabel(existing.getString("status"))}).")
                        } else {
                            val invite = mapOf("playerUid" to player.uid, "playerName" to player.name, "matchId" to m.id, "matchTitle" to m.title, "ownerUid" to m.ownerUid, "status" to "pending", "createdAt" to Timestamp.now())
                            ref.set(invite)
                                .addOnSuccessListener { showMessage("Invitación enviada a ${player.name}.") }
                                .addOnFailureListener { showMessage("No se pudo invitar: ${it.localizedMessage}") }
                        }
                    }.addOnFailureListener { showMessage("No se pudo invitar: ${it.localizedMessage}") }
                }) { Text("${m.title} · Invitar") }
            }
        }
    } }
}

private fun statusLabel(status: String?) = when (status) { "accepted" -> "aceptada"; "rejected" -> "rechazada"; else -> "pendiente" }

/** Confirma a un jugador en el partido y descuenta una vacante, todo junto (o nada). */
private fun joinMatch(matchId: String, playerUid: String, playerName: String, via: String, responseRef: com.google.firebase.firestore.DocumentReference, responseFields: Map<String, Any>, okMessage: String, showMessage: (String) -> Unit) {
    val matchRef = db.collection("matches").document(matchId)
    val partRef = matchRef.collection("participants").document(playerUid)
    db.runTransaction(Transaction.Function<Boolean> { tx ->
        val match = tx.get(matchRef)
        val part = tx.get(partRef)
        if (!part.exists()) {
            val needed = match.getLong("playersNeeded") ?: 0L
            if (needed <= 0L) throw FirebaseFirestoreException("Este partido ya está completo.", FirebaseFirestoreException.Code.ABORTED)
            val left = needed - 1L
            tx.set(partRef, mapOf("uid" to playerUid, "name" to playerName, "via" to via, "joinedAt" to Timestamp.now()))
            tx.update(matchRef, mapOf("playersNeeded" to left, "status" to if (left <= 0L) "full" else "open"))
        }
        tx.update(responseRef, responseFields)
        true
    }).addOnSuccessListener { showMessage(okMessage) }
        .addOnFailureListener { showMessage(it.localizedMessage ?: "No se pudo completar la acción") }
}

@Composable
private fun MatchManagement(m: MatchData, showMessage: (String) -> Unit) {
    var applications by remember(m.id) { mutableStateOf(listOf<AppRow>()) }
    var invitations by remember(m.id) { mutableStateOf(listOf<AppRow>()) }
    var participants by remember(m.id) { mutableStateOf(listOf<AppRow>()) }
    DisposableEffect(m.id) {
        val base = db.collection("matches").document(m.id)
        val r1 = base.collection("applications").addSnapshotListener { snap, e ->
            if (e != null) showMessage("Error de solicitudes: ${e.localizedMessage}")
            else applications = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("playerName") ?: "Jugador", it.getString("status") ?: "pending") }
        }
        val r2 = base.collection("invitations").addSnapshotListener { snap, e ->
            if (e == null) invitations = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("playerName") ?: "Jugador", it.getString("status") ?: "pending") }
        }
        val r3 = base.collection("participants").addSnapshotListener { snap, e ->
            if (e == null) participants = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("name") ?: "Jugador", "accepted") }
        }
        onDispose { r1.remove(); r2.remove(); r3.remove() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${m.title} · ${if (m.status == "full") "completo" else "${m.playersNeeded} vacantes"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text("Confirmados (${participants.size})", fontWeight = FontWeight.Bold)
        if (participants.isEmpty()) Text("Todavía no hay jugadores confirmados.", color = Color.Gray)
        participants.forEach { p -> Text("✓ ${p.name}") }

        Text("Postulaciones recibidas", fontWeight = FontWeight.Bold)
        if (applications.isEmpty()) Text("Todavía no hay postulaciones.", color = Color.Gray)
        applications.sortedBy { it.status != "pending" }.forEach { a ->
            Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(a.name, fontWeight = FontWeight.Bold)
                Text("Estado: ${statusLabel(a.status)}", color = Color.Gray)
                if (a.status == "pending") FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = {
                        val ref = db.collection("matches").document(m.id).collection("applications").document(a.uid)
                        joinMatch(m.id, a.uid, a.name, "application", ref, mapOf("status" to "accepted", "decidedAt" to Timestamp.now()), "${a.name} quedó confirmado en el partido.", showMessage)
                    }) { Text("Aceptar") }
                    TextButton(onClick = {
                        db.collection("matches").document(m.id).collection("applications").document(a.uid)
                            .update(mapOf("status" to "rejected", "decidedAt" to Timestamp.now()))
                            .addOnSuccessListener { showMessage("Solicitud rechazada.") }
                            .addOnFailureListener { showMessage(it.localizedMessage ?: "Error al rechazar") }
                    }) { Text("Rechazar") }
                }
            } }
        }

        Text("Invitaciones enviadas", fontWeight = FontWeight.Bold)
        if (invitations.isEmpty()) Text("No enviaste invitaciones para este partido.", color = Color.Gray)
        invitations.forEach { i -> Text("${i.name} · ${statusLabel(i.status)}") }
    }
}

@Composable
private fun PlayerInvitations(matches: List<MatchData>, uid: String, playerName: String, showMessage: (String) -> Unit) {
    var statuses by remember { mutableStateOf(mapOf<String, String>()) }
    val ids = matches.map { it.id }
    DisposableEffect(ids.joinToString(","), uid) {
        val regs = ids.map { id ->
            db.collection("matches").document(id).collection("invitations").document(uid).addSnapshotListener { snap, _ ->
                statuses = if (snap != null && snap.exists()) statuses + (id to (snap.getString("status") ?: "pending")) else statuses - id
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }
    val pending = matches.filter { statuses[it.id] == "pending" }
    val answered = matches.filter { statuses[it.id] == "accepted" || statuses[it.id] == "rejected" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Invitaciones recibidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (pending.isEmpty()) Text("Todavía no tenés invitaciones pendientes.", color = Color.Gray)
        pending.forEach { match ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(match.title, fontWeight = FontWeight.Bold)
                    Text("${match.dateLabel} · ${match.venue}")
                    Text("Organiza: ${match.ownerName} · ${if (match.status == "full") "completo" else "${match.playersNeeded} vacantes"}", color = Color.Gray)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(enabled = match.status == "open", onClick = {
                            val ref = db.collection("matches").document(match.id).collection("invitations").document(uid)
                            joinMatch(match.id, uid, playerName, "invitation", ref, mapOf("status" to "accepted", "respondedAt" to Timestamp.now()), "¡Listo! Quedaste confirmado en ${match.title}.", showMessage)
                        }) { Text("Aceptar") }
                        TextButton(onClick = {
                            db.collection("matches").document(match.id).collection("invitations").document(uid)
                                .update(mapOf("status" to "rejected", "respondedAt" to Timestamp.now()))
                                .addOnSuccessListener { showMessage("Invitación rechazada.") }
                                .addOnFailureListener { showMessage(it.localizedMessage ?: "No se pudo rechazar") }
                        }) { Text("Rechazar") }
                    }
                }
            }
        }
        if (answered.isNotEmpty()) {
            Text("Respondidas", fontWeight = FontWeight.Bold)
            answered.forEach { match -> Text("${match.title} · ${statusLabel(statuses[match.id])}", color = Color.Gray) }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SportsSoccer, null, tint = Green, modifier = Modifier.size(36.dp)); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray) } } }
