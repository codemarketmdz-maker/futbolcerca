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
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.google.firebase.firestore.Query
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
            listOf("Jugador", "Capitán").forEach { r -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(role == r, { role = r }); Text(if (r == "Capitán") "Capitán / Organizador" else r, Modifier.weight(1f)) } }
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
        Text("7 días de prueba · Soporte y licencias CodeMarket", color = Color(0xFF4A4A4A), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private data class MatchData(val id: String, val title: String, val mode: String, val format: String, val dateLabel: String, val venue: String, val address: String, val zone: String, val price: String, val ownerUid: String, val ownerName: String, val ownerWhatsapp: String?, val playersNeeded: Int, val status: String, val lat: Double?, val lon: Double?, val zoneLat: Double?, val zoneLon: Double?, val zoneRadius: Double?, val eventAtMs: Long)
private data class PlayerData(val uid: String, val name: String, val zone: String, val positions: String, val availability: String, val whatsapp: String?, val lat: Double?, val lon: Double?, val zoneLat: Double?, val zoneLon: Double?, val zoneRadius: Double?)

// ---------- Zonas en el mapa ----------
private const val DEFAULT_CITY = "Mendoza"
private const val MIN_ZONE_RADIUS = 600.0     // metros: nunca se muestra un punto exacto
private const val MAX_ZONE_RADIUS = 2500.0
private const val SNAP_DEGREES = 0.004         // ~440 m: el centro del círculo se "redondea" para no delatar una dirección

private data class GeoZone(val lat: Double, val lon: Double, val radius: Double)
private data class MapItem(val isMatch: Boolean, val id: String, val uid: String?, val title: String, val detail: String, val lat: Double, val lon: Double, val radius: Double, val whatsapp: String?)
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

private data class AppRow(val uid: String, val name: String, val status: String, val whatsapp: String? = null)
private data class FriendRow(val uid: String, val name: String)
private data class FriendInvite(val id: String, val fromUid: String, val fromName: String, val matchId: String, val matchTitle: String)
private data class Notice(val id: String, val title: String, val body: String, val read: Boolean, val at: Long)

private fun normalizeWhatsapp(raw: String): String {
    val d = raw.filter(Char::isDigit).trimStart('0')
    return when { d.isBlank() -> ""; d.startsWith("54") -> d; d.length <= 10 -> "549$d"; else -> d }
}

private fun dotIcon(context: Context, isMatch: Boolean): BitmapDrawable {
    val size = 44
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = if (isMatch) android.graphics.Color.rgb(251, 140, 0) else android.graphics.Color.rgb(30, 136, 229)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
    return BitmapDrawable(context.resources, bmp)
}

/** Avisa a otro usuario: queda en su bandeja y le llega como notificación del teléfono. */
private fun pushNotice(toUid: String, fromUid: String, title: String, body: String) {
    if (toUid.isBlank() || toUid == fromUid) return
    db.collection("users").document(toUid).collection("notifications")
        .add(mapOf("fromUid" to fromUid, "title" to title, "body" to body, "read" to false, "notified" to false, "createdAt" to Timestamp.now()))
}

private fun openWhatsApp(context: Context, number: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))) } catch (_: Exception) { }
}

@Composable
private fun Home(uid: String, initial: Map<String, Any>, showMessage: (String) -> Unit, logout: () -> Unit) {
    val context = LocalContext.current
    val role = if ((initial["role"] as? String ?: "Jugador") == "Jugador") "Jugador" else "Capitán"
    var tab by remember { mutableStateOf(if (role == "Jugador") "Mapa" else "Partidos") }
    var profile by remember { mutableStateOf(initial) }
    var allMatches by remember { mutableStateOf(listOf<MatchData>()) }
    var mapFilter by remember { mutableStateOf(MapFilter()) }
    var players by remember { mutableStateOf(listOf<PlayerData>()) }
    var friends by remember { mutableStateOf(listOf<FriendRow>()) }
    var notices by remember { mutableStateOf(listOf<Notice>()) }
    var selectedMatchId by remember { mutableStateOf<String?>(null) }
    var selectedZone by remember { mutableStateOf<ZoneCluster?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val myName = profile["displayName"] as? String ?: "Usuario"
    val myWhatsapp = profile["whatsapp"] as? String
    val friendIds = friends.map { it.uid }.toSet()
    val unread = notices.count { !it.read }
    val nowMs = System.currentTimeMillis()
    // Los partidos se ocultan 48 h después de su inicio.
    val matches = allMatches.filter { it.eventAtMs == 0L || it.eventAtMs > nowMs - 48L * 3600_000L }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(uid) {
        Notifier.ensureChannel(context)
        Notifier.schedule(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Autoreparación: zona escrita pero sin coordenadas.
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
            val g = geocodeZone("${m.address}, ${m.zone}", "${m.venue}, ${m.zone}", m.zone)
            if (g != null) db.collection("matches").document(m.id).set(mapOf("zoneLat" to g.lat, "zoneLon" to g.lon, "zoneRadius" to g.radius), SetOptions.merge())
        }
    }
    DisposableEffect(uid) {
        val regs = mutableListOf<ListenerRegistration>()
        regs += db.collection("matches").whereIn("status", listOf("open", "full")).addSnapshotListener { snap, e ->
            if (e != null) syncError = "Partidos: ${e.localizedMessage}" else {
                syncError = null
                allMatches = snap?.documents.orEmpty().map { d ->
                    MatchData(d.id, d.getString("title") ?: "Partido", d.getString("mode") ?: "Mixto", d.getString("format") ?: "Fútbol 5", d.getString("dateLabel") ?: d.getString("dateTime") ?: "", d.getString("venue") ?: "", d.getString("address") ?: "", d.getString("zone") ?: "", d.getString("price") ?: "", d.getString("ownerUid") ?: "", d.getString("ownerName") ?: "", d.getString("ownerWhatsapp"), (d.getLong("playersNeeded") ?: 1L).toInt(), d.getString("status") ?: "open", d.getDouble("lat"), d.getDouble("lon"), d.getDouble("zoneLat"), d.getDouble("zoneLon"), d.getDouble("zoneRadius"), d.getTimestamp("eventAt")?.toDate()?.time ?: 0L)
                }
            }
        }
        regs += db.collection("users").whereEqualTo("role", "Jugador").addSnapshotListener { snap, e ->
            if (e != null) syncError = "Jugadores: ${e.localizedMessage}" else players = snap?.documents.orEmpty().mapNotNull { d ->
                if (d.id == uid) null else PlayerData(d.id, d.getString("displayName") ?: "Jugador", d.getString("zone") ?: "Zona no indicada", d.getString("positions") ?: "Sin posición", d.getString("availability") ?: "Sin disponibilidad", d.getString("whatsapp"), d.getDouble("lat"), d.getDouble("lon"), d.getDouble("zoneLat"), d.getDouble("zoneLon"), d.getDouble("zoneRadius"))
            }
        }
        regs += db.collection("users").document(uid).collection("friends").addSnapshotListener { snap, e ->
            if (e == null) friends = snap?.documents.orEmpty().map { FriendRow(it.id, it.getString("name") ?: "Jugador") }
        }
        regs += db.collection("users").document(uid).collection("notifications").orderBy("createdAt", Query.Direction.DESCENDING).limit(50).addSnapshotListener { snap, e ->
            if (e == null && snap != null) {
                notices = snap.documents.map { Notice(it.id, it.getString("title") ?: "", it.getString("body") ?: "", it.getBoolean("read") == true, it.getTimestamp("createdAt")?.toDate()?.time ?: 0L) }
                snap.documents.filter { it.getBoolean("notified") != true && !it.metadata.hasPendingWrites() }.forEach { d ->
                    Notifier.show(context, d.id.hashCode(), d.getString("title") ?: "Fútbol Cerca", d.getString("body") ?: "")
                    d.reference.update("notified", true)
                }
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }
    val tabs = if (role == "Jugador") listOf("Mapa", "Partidos", "Invitaciones", "Amigos", "Perfil") else listOf("Partidos", "Mapa", "Solicitudes", "Publicar", "Perfil")
    fun tabLabel(t: String) = when (t) { "Solicitudes" -> "Pedidos"; "Publicar" -> "Crear"; "Invitaciones" -> "Invitac."; else -> t }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Canvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_cancha), null, tint = Color.Unspecified, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("FÚTBOL CERCA", fontWeight = FontWeight.Black, color = Color.White, maxLines = 1) } },
                actions = {
                    IconButton(onClick = { tab = "Avisos" }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.Default.Notifications, "Avisos", tint = Color.White) }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Green)
            )
        },
        bottomBar = {
            if (!imeVisible) NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t },
                        icon = { Icon(when (t) { "Mapa" -> Icons.Default.Map; "Perfil" -> Icons.Default.Person; "Amigos" -> Icons.Default.Groups; "Solicitudes" -> Icons.Default.MarkEmailUnread; "Publicar" -> Icons.Default.AddCircle; else -> Icons.Default.SportsSoccer }, null) },
                        label = { Text(tabLabel(t), fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) })
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            syncError?.let { err -> item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0DD))) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text("No se pueden leer los datos", fontWeight = FontWeight.Bold, color = Color(0xFF8A1C12)); Text(err, fontSize = 12.sp); Text("Si dice PERMISSION_DENIED: publicá las reglas de firestore.rules en Firebase Console → Firestore Database → Reglas.", fontSize = 12.sp, color = Color.DarkGray) } } } }
            item { Column { Text("Hola, ${myName.substringBefore(" ")} 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (role == "Capitán") "Organizá tus partidos" else "El fútbol está más cerca", color = Color(0xFF4A4A4A)) } }
            when (tab) {
                "Mapa" -> {
                    item { Text("Mapa en vivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { MapFilters(mapFilter) { mapFilter = it; selectedZone = null } }
                    item { InAppMap(players.filter { playerPasses(it, mapFilter) }, matches.filter { matchPasses(it, mapFilter, nowMs) }) { selectedZone = it } }
                    item {
                        val pz = players.count { (it.zoneLat ?: it.lat) != null }; val mz = matches.count { (it.zoneLat ?: it.lat) != null }
                        Text("Jugadores: ${players.size} (${pz} con zona) · Partidos: ${matches.size} (${mz} con zona)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
                        if (players.size > pz || matches.size > mz) Text("Los que no tienen zona no se dibujan: cada usuario debe cargar su barrio o zona en Perfil (o Crear).", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
                    }
                    selectedZone?.let { z ->
                        item(key = "zone-detail") {
                            ZoneDetail(z, { selectedZone = null }) { item ->
                                val pid = item.uid
                                if (pid != null) {
                                    if (role == "Capitán") InviteMenu(pid, item.title, matches.filter { it.ownerUid == uid && it.status == "open" }, uid, myName, showMessage)
                                    else if (friendIds.contains(pid)) Text("✓ Ya son amigos", fontWeight = FontWeight.Bold, color = Green)
                                    else Button(onClick = { sendFollowRequest(uid, myName, pid, showMessage) }, modifier = Modifier.fillMaxWidth()) { Text("Seguir a ${item.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                    item { Text(if (role == "Capitán") "Cada círculo es una zona y cada punto azul un jugador. Tocá un punto para ver sus datos e invitarlo a tu partido." else "Cada círculo es una zona: puntos azules son jugadores y naranjas partidos. Tocá un punto para ver sus datos; podés seguir a otros jugadores. Nunca se muestra una dirección exacta.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B)) }
                }
                "Perfil" -> item { ProfileEditor(uid, profile, { profile = it }, showMessage) }
                "Avisos" -> item { NotificationsScreen(uid, notices) }
                "Invitaciones" -> item { PlayerInvitations(matches, uid, myName, myWhatsapp, showMessage) }
                "Amigos" -> item { FriendsScreen(uid, myName, friends, players, matches, showMessage) }
                "Publicar" -> item { PublishForm(uid, profile, showMessage) }
                "Solicitudes" -> {
                    val ownMatches = matches.filter { it.ownerUid == uid }
                    item { Text("Pedidos y confirmados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (ownMatches.isEmpty()) item { EmptyState("No tenés partidos publicados", "Publicá un partido para recibir postulaciones e invitar jugadores desde el mapa.") }
                    items(ownMatches, key = { it.id }) { m -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(m.title, fontWeight = FontWeight.Bold); Text(if (m.status == "full") "COMPLETO" else "${m.playersNeeded} vacantes", color = Color(0xFF4A4A4A)); Button(onClick = { selectedMatchId = if (selectedMatchId == m.id) null else m.id }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedMatchId == m.id) "Ocultar" else "Ver pedidos y confirmados") } } } }
                    ownMatches.firstOrNull { it.id == selectedMatchId }?.let { m -> item(key = "panel-${m.id}") { MatchManagement(m, showMessage) } }
                }
                else -> {
                    item { Text(if (role == "Jugador") "Partidos disponibles" else "Mis partidos publicados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    val visible = if (role == "Jugador") matches else matches.filter { it.ownerUid == uid }
                    if (visible.isEmpty()) item { EmptyState("Todavía no hay partidos publicados", "Cuando alguien publique un encuentro, aparecerá acá.") }
                    items(visible, key = { it.id }) { m -> MatchCard(m, role == "Jugador", uid, myName, myWhatsapp, showMessage) }
                    if (role != "Jugador") item { Button(onClick = { tab = "Publicar" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear partido") } }
                }
            }
            item { Divider(); Text("CodeMarket · Licencias y soporte", color = Color(0xFF4A4A4A), fontSize = 12.sp); OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP))) }, modifier = Modifier.fillMaxWidth()) { Text("WhatsApp 2615117220") }; TextButton(onClick = logout) { Text("Cerrar sesión") } }
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
            if (lat != null && lon != null) items += MapItem(false, p.uid, p.uid, p.name, "${p.zone} · ${p.positions} · ${p.availability}", lat, lon, p.zoneRadius ?: 700.0, p.whatsapp)
        }
        matches.forEach { m ->
            val lat = m.zoneLat ?: m.lat; val lon = m.zoneLon ?: m.lon
            if (lat != null && lon != null) items += MapItem(true, m.id, null, m.title, "${m.dateLabel} · ${m.venue} · ${m.zone}", lat, lon, m.zoneRadius ?: 700.0, m.ownerWhatsapp)
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
            if (z.items.size > 1) {
                val badge = Marker(map)
                badge.position = center
                badge.icon = countIcon(context, z.items.size)
                badge.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                badge.setOnMarkerClickListener { _, _ -> onSelect(z); true }
                map.overlays.add(badge)
            }
            // Un punto por persona/partido, dentro de la zona (posición aproximada, no exacta).
            z.items.forEach { item ->
                val h = item.id.hashCode() and 0x7fffffff
                val ang = Math.toRadians((h % 360).toDouble())
                val rr = z.radius * (0.25 + ((h / 360) % 50) / 100.0)
                val dLat = rr * sin(ang) / 111320.0
                val dLon = rr * cos(ang) / (111320.0 * cos(Math.toRadians(z.lat)))
                val dot = Marker(map)
                dot.position = GeoPoint(z.lat + dLat, z.lon + dLon)
                dot.icon = dotIcon(context, item.isMatch)
                dot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                dot.setOnMarkerClickListener { _, _ -> onSelect(ZoneCluster(z.lat, z.lon, z.radius, listOf(item))); true }
                map.overlays.add(dot)
            }
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
private fun ZoneDetail(zone: ZoneCluster, onClose: () -> Unit, playerActions: @Composable (MapItem) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (zone.items.size == 1) "Detalle" else "En esta zona (${zone.items.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Cerrar") }
        }
        zone.items.forEach { item ->
            Text("${if (item.isMatch) "⚽ Partido" else "👤 Jugador"}: ${item.title}", fontWeight = FontWeight.Bold)
            Text(item.detail, color = Color(0xFF333333), fontSize = 13.sp)
            if (!item.isMatch && item.uid != null && zone.items.size <= 6) RatingBadge(item.uid)
            item.whatsapp?.let { w -> WhatsAppButton(w, if (item.isMatch) "Escribir al organizador" else "WhatsApp") }
            if (!item.isMatch) playerActions(item)
            Divider()
        }
    } }
}

@Composable
private fun WhatsAppButton(number: String, label: String = "WhatsApp") {
    val ctx = LocalContext.current
    OutlinedButton(onClick = { openWhatsApp(ctx, number) }) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** El organizador invita a un jugador (desde el mapa) a uno de sus partidos abiertos. */
@Composable
private fun InviteMenu(playerUid: String, playerName: String, ownMatches: List<MatchData>, myUid: String, myName: String, showMessage: (String) -> Unit) {
    var expanded by remember(playerUid) { mutableStateOf(false) }
    Button(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "Cerrar" else "Invitar a un partido") }
    if (expanded) {
        if (ownMatches.isEmpty()) Text("Primero publicá un partido abierto (pestaña Crear).", color = Color(0xFF333333), fontSize = 13.sp)
        ownMatches.forEach { m ->
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                val ref = db.collection("matches").document(m.id).collection("invitations").document(playerUid)
                ref.get().addOnSuccessListener { existing ->
                    if (existing.exists()) showMessage("Ya invitaste a $playerName a este partido (estado: ${statusLabel(existing.getString("status"))}).")
                    else {
                        val invite = mapOf("playerUid" to playerUid, "playerName" to playerName, "matchId" to m.id, "matchTitle" to m.title, "ownerUid" to myUid, "status" to "pending", "createdAt" to Timestamp.now())
                        ref.set(invite)
                            .addOnSuccessListener { showMessage("Invitación enviada a $playerName."); pushNotice(playerUid, myUid, "Te invitaron a un partido", "$myName te invitó a ${m.title}") }
                            .addOnFailureListener { showMessage("No se pudo invitar: ${it.localizedMessage}") }
                    }
                }.addOnFailureListener { showMessage("No se pudo invitar: ${it.localizedMessage}") }
            }) { Text("${m.title} · ${m.dateLabel}") }
        }
    }
}

private fun sendFollowRequest(myUid: String, myName: String, targetUid: String, showMessage: (String) -> Unit) {
    val ref = db.collection("users").document(targetUid).collection("friendRequests").document(myUid)
    ref.get().addOnSuccessListener { existing ->
        if (existing.exists()) showMessage("Ya enviaste una solicitud (estado: ${statusLabel(existing.getString("status"))}).")
        else ref.set(mapOf("fromUid" to myUid, "fromName" to myName, "status" to "pending", "createdAt" to Timestamp.now()))
            .addOnSuccessListener { showMessage("Solicitud enviada. Cuando la acepte podrán invitarse a partidos."); pushNotice(targetUid, myUid, "Nueva solicitud de amistad", "$myName quiere seguirte") }
            .addOnFailureListener { showMessage("No se pudo enviar: ${it.localizedMessage}") }
    }.addOnFailureListener { showMessage("No se pudo enviar: ${it.localizedMessage}") }
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
    var phone by remember { mutableStateOf(data["whatsapp"] as? String ?: "") }
    var showPhone by remember { mutableStateOf(data["whatsapp"] != null) }
    var saving by remember { mutableStateOf(false) }
    val isPlayer = (data["role"] as? String ?: "Jugador") == "Jugador"
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // El WhatsApp guardado en privado se recupera aunque no esté visible para otros.
    LaunchedEffect(uid) {
        if (phone.isBlank()) db.collection("users").document(uid).collection("private").document("contact").get()
            .addOnSuccessListener { it.getString("whatsapp")?.let { w -> if (phone.isBlank()) phone = w } }
    }
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
        Text(if (isPlayer) "Mi perfil deportivo" else "Mi perfil de organizador", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(2) }, label = { Text("Edad") }, modifier = Modifier.fillMaxWidth())
        Text("Género"); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Masculino", "Femenino").forEach { FilterChip(gender == it, { gender = it }, label = { Text(it) }) } }
        if (isPlayer) {
            OutlinedTextField(positions, { positions = it }, label = { Text("Posiciones") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(skills, { skills = it }, label = { Text("Habilidades / experiencia") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Disponibilidad"); listOf("Ahora mismo", "Esta noche", "Mañana", "Fines de semana").forEach { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(availability == it, { availability = it }); Text(it, Modifier.weight(1f)) } }
        }
        OutlinedTextField(zone, { zone = it }, label = { Text("Barrio, zona o dirección (se muestra como zona)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20) }, label = { Text("WhatsApp (ej. 261 555 1234)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = showPhone, onCheckedChange = { showPhone = it })
            Spacer(Modifier.width(10.dp))
            Text(if (isPlayer) "Mostrar mi WhatsApp a otros usuarios (en el mapa junto a mis datos)" else "Mostrar mi WhatsApp en mis partidos", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(if (showPhone) "Tu número será visible para los demás usuarios." else "Tu número queda guardado solo para vos; nadie más lo ve.", fontSize = 12.sp, color = Color(0xFF4A4A4A))
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
                val digits = normalizeWhatsapp(phone)
                db.collection("users").document(uid).collection("private").document("contact").set(mapOf("whatsapp" to digits))
                if (digits.isNotBlank() && showPhone) { remote["whatsapp"] = digits; local["whatsapp"] = digits }
                else { remote["whatsapp"] = FieldValue.delete(); local.remove("whatsapp") }
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
    var date by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(profile["zone"] as? String ?: "") }
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
        OutlinedTextField(venue, { venue = it }, label = { Text("Nombre de la cancha") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Dirección de la cancha (calle y número)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(zone, { zone = it }, label = { Text("Barrio o zona (es lo que se ve en el mapa)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("Costo por jugador (ej. \$5000)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(needed, { needed = it.filter(Char::isDigit).take(2) }, label = { Text("Jugadores que faltan") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving, onClick = {
            if (title.isBlank() || date.isBlank() || venue.isBlank() || address.isBlank() || zone.isBlank()) { showMessage("Completá título, fecha, cancha, dirección y zona."); return@Button }
            saving = true
            scope.launch {
                val data = hashMapOf<String, Any>("title" to title.trim(), "mode" to mode, "format" to format, "dateLabel" to date, "dateTime" to date, "eventAt" to Timestamp(cal.time), "venue" to venue.trim(), "address" to address.trim(), "zone" to zone.trim(), "price" to price.trim(), "playersNeeded" to (needed.toIntOrNull() ?: 1).coerceAtLeast(1), "ownerUid" to uid, "ownerName" to (profile["displayName"] as? String ?: "Organizador"), "status" to "open", "createdAt" to Timestamp.now())
                (profile["whatsapp"] as? String)?.let { data["ownerWhatsapp"] = it }
                val g = geocodeZone("${address.trim()}, ${zone.trim()}", "${venue.trim()}, ${zone.trim()}", zone.trim())
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
private fun MatchCard(m: MatchData, canApply: Boolean, uid: String, playerName: String, playerWhatsapp: String?, showMessage: (String) -> Unit) {
    var appStatus by remember(m.id) { mutableStateOf<String?>(null) }
    var joined by remember(m.id) { mutableStateOf(false) }
    var showChat by remember(m.id) { mutableStateOf(false) }
    var showRate by remember(m.id) { mutableStateOf(false) }
    var confirmLeave by remember(m.id) { mutableStateOf(false) }
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
        Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${m.mode} · ${m.dateLabel}")
        Text("📍 ${listOf(m.venue, m.address, m.zone).filter { it.isNotBlank() }.joinToString(" · ")}")
        Text("💰 Costo por jugador: ${m.price.ifBlank { "A confirmar" }}")
        Text("Organiza: ${m.ownerName}")
        m.ownerWhatsapp?.let { WhatsAppButton(it, "Escribir al organizador") }
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
                val values = hashMapOf<String, Any>("uid" to uid, "playerName" to playerName, "status" to "pending", "createdAt" to Timestamp.now())
                playerWhatsapp?.let { values["whatsapp"] = it }
                db.collection("matches").document(m.id).collection("applications").document(uid).set(values)
                    .addOnSuccessListener { showMessage("Postulación enviada. El organizador recibirá un aviso."); pushNotice(m.ownerUid, uid, "Nueva postulación", "$playerName quiere sumarse a ${m.title}") }
                    .addOnFailureListener { showMessage("No se pudo postular: ${it.localizedMessage}") }
            }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            if (joined) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { showChat = !showChat }) { Text(if (showChat) "Cerrar chat" else "Chat del partido") }
                    if (m.eventAtMs in 1..System.currentTimeMillis()) OutlinedButton(onClick = { showRate = !showRate }) { Text(if (showRate) "Cerrar" else "Valorar compañeros") }
                    TextButton(onClick = { confirmLeave = true }) { Text("Salir del partido", color = Color(0xFF8A1C12)) }
                }
                if (showChat) ChatPanel(m.id, uid, playerName, showMessage)
                if (showRate) RatePanel(m.id, uid, playerName, showMessage)
            }
        }
    } }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("¿Salir del partido?") },
        text = { Text("Vas a liberar tu lugar en ${m.title} y se le avisará al organizador.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; leaveMatch(m, uid, playerName, showMessage) }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") } }
    )
}

private fun statusLabel(status: String?) = when (status) { "accepted" -> "aceptada"; "rejected" -> "rechazada"; else -> "pendiente" }

/** Confirma a un jugador en el partido y descuenta una vacante, todo junto (o nada). */
private fun joinMatch(matchId: String, playerUid: String, playerName: String, playerWhatsapp: String?, via: String, responseRef: com.google.firebase.firestore.DocumentReference, responseFields: Map<String, Any>, okMessage: String, showMessage: (String) -> Unit, onDone: () -> Unit) {
    val matchRef = db.collection("matches").document(matchId)
    val partRef = matchRef.collection("participants").document(playerUid)
    db.runTransaction(Transaction.Function<Boolean> { tx ->
        val match = tx.get(matchRef)
        val part = tx.get(partRef)
        if (!part.exists()) {
            val needed = match.getLong("playersNeeded") ?: 0L
            if (needed <= 0L) throw FirebaseFirestoreException("Este partido ya está completo.", FirebaseFirestoreException.Code.ABORTED)
            val left = needed - 1L
            val person = hashMapOf<String, Any>("uid" to playerUid, "name" to playerName, "via" to via, "joinedAt" to Timestamp.now())
            if (!playerWhatsapp.isNullOrBlank()) person["whatsapp"] = playerWhatsapp
            tx.set(partRef, person)
            tx.update(matchRef, mapOf("playersNeeded" to left, "status" to if (left <= 0L) "full" else "open"))
        }
        tx.update(responseRef, responseFields)
        true
    }).addOnSuccessListener { showMessage(okMessage); onDone() }
        .addOnFailureListener { showMessage(it.localizedMessage ?: "No se pudo completar la acción") }
}

@Composable
private fun MatchManagement(m: MatchData, showMessage: (String) -> Unit) {
    var applications by remember(m.id) { mutableStateOf(listOf<AppRow>()) }
    var invitations by remember(m.id) { mutableStateOf(listOf<AppRow>()) }
    var participants by remember(m.id) { mutableStateOf(listOf<AppRow>()) }
    var showChat by remember(m.id) { mutableStateOf(false) }
    var showRate by remember(m.id) { mutableStateOf(false) }
    var confirmCancel by remember(m.id) { mutableStateOf(false) }
    DisposableEffect(m.id) {
        val base = db.collection("matches").document(m.id)
        val r1 = base.collection("applications").addSnapshotListener { snap, e ->
            if (e != null) showMessage("Error de solicitudes: ${e.localizedMessage}")
            else applications = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("playerName") ?: "Jugador", it.getString("status") ?: "pending", it.getString("whatsapp")) }
        }
        val r2 = base.collection("invitations").addSnapshotListener { snap, e ->
            if (e == null) invitations = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("playerName") ?: "Jugador", it.getString("status") ?: "pending") }
        }
        val r3 = base.collection("participants").addSnapshotListener { snap, e ->
            if (e == null) participants = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("name") ?: "Jugador", "accepted", it.getString("whatsapp")) }
        }
        onDispose { r1.remove(); r2.remove(); r3.remove() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${m.title} · ${if (m.status == "full") "completo" else "${m.playersNeeded} vacantes"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { showChat = !showChat }) { Text(if (showChat) "Cerrar chat" else "Chat del partido") }
            if (m.eventAtMs in 1..System.currentTimeMillis()) OutlinedButton(onClick = { showRate = !showRate }) { Text(if (showRate) "Cerrar" else "Valorar jugadores") }
            TextButton(onClick = { confirmCancel = true }) { Text("Anular partido", color = Color(0xFF8A1C12)) }
        }
        if (showChat) ChatPanel(m.id, m.ownerUid, m.ownerName, showMessage)
        if (showRate) RatePanel(m.id, m.ownerUid, m.ownerName, showMessage)

        Text("Confirmados (${participants.size})", fontWeight = FontWeight.Bold)
        if (participants.isEmpty()) Text("Todavía no hay jugadores confirmados.", color = Color(0xFF4A4A4A))
        participants.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { Text("✓ ${p.name}", Modifier.weight(1f)); p.whatsapp?.let { WhatsAppButton(it, "Chat") } } }

        Text("Postulaciones recibidas", fontWeight = FontWeight.Bold)
        if (applications.isEmpty()) Text("Todavía no hay postulaciones.", color = Color(0xFF4A4A4A))
        applications.sortedBy { it.status != "pending" }.forEach { a ->
            Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(a.name, fontWeight = FontWeight.Bold)
                Text("Estado: ${statusLabel(a.status)}", color = Color(0xFF4A4A4A))
                a.whatsapp?.let { WhatsAppButton(it) }
                if (a.status == "pending") FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = {
                        val ref = db.collection("matches").document(m.id).collection("applications").document(a.uid)
                        joinMatch(m.id, a.uid, a.name, a.whatsapp, "application", ref, mapOf("status" to "accepted", "decidedAt" to Timestamp.now()), "${a.name} quedó confirmado en el partido.", showMessage) {
                            pushNotice(a.uid, m.ownerUid, "¡Postulación aceptada!", "Quedaste confirmado en ${m.title}")
                        }
                    }) { Text("Aceptar") }
                    TextButton(onClick = {
                        db.collection("matches").document(m.id).collection("applications").document(a.uid)
                            .update(mapOf("status" to "rejected", "decidedAt" to Timestamp.now()))
                            .addOnSuccessListener { showMessage("Solicitud rechazada."); pushNotice(a.uid, m.ownerUid, "Postulación rechazada", "No pudiste sumarte a ${m.title} esta vez") }
                            .addOnFailureListener { showMessage(it.localizedMessage ?: "Error al rechazar") }
                    }) { Text("Rechazar") }
                }
            } }
        }

        Text("Invitaciones enviadas", fontWeight = FontWeight.Bold)
        if (invitations.isEmpty()) Text("No enviaste invitaciones para este partido.", color = Color(0xFF4A4A4A))
        invitations.forEach { i -> Text("${i.name} · ${statusLabel(i.status)}") }
    }
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("¿Anular el partido?") },
        text = { Text("Se le avisará a ${participants.size} confirmados y a quienes tengan pedidos o invitaciones pendientes.") },
        confirmButton = {
            TextButton(onClick = {
                confirmCancel = false
                val who = (participants.map { it.uid } + applications.filter { it.status == "pending" }.map { it.uid } + invitations.filter { it.status == "pending" }.map { it.uid }).toSet()
                cancelMatch(m, who, showMessage)
            }) { Text("Anular") }
        },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Volver") } }
    )
}

@Composable
private fun InvitationCard(match: MatchData, uid: String, playerName: String, playerWhatsapp: String?, showMessage: (String) -> Unit) {
    var team by remember(match.id) { mutableStateOf(listOf<AppRow>()) }
    DisposableEffect(match.id) {
        val reg = db.collection("matches").document(match.id).collection("participants").addSnapshotListener { snap, e ->
            if (e == null) team = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("name") ?: "Jugador", "accepted", it.getString("whatsapp")) }
        }
        onDispose { reg.remove() }
    }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(match.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("📅 ${match.dateLabel} · ${match.format} · ${match.mode}")
            Text("📍 ${listOf(match.venue, match.address, match.zone).filter { it.isNotBlank() }.joinToString(" · ")}")
            Text("💰 Costo por jugador: ${match.price.ifBlank { "A confirmar" }}", fontWeight = FontWeight.SemiBold)
            Text("Organiza: ${match.ownerName} · ${if (match.status == "full") "completo" else "${match.playersNeeded} vacantes"}")
            match.ownerWhatsapp?.let { WhatsAppButton(it, "Escribir al organizador") }
            Text("Ya confirmados en el equipo (${team.size})", fontWeight = FontWeight.Bold)
            if (team.isEmpty()) Text("Todavía no hay nadie confirmado. ¡Serías el primero!", color = Color(0xFF4A4A4A))
            team.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { Text("✓ ${p.name}", Modifier.weight(1f)); p.whatsapp?.let { WhatsAppButton(it, "Chat") } } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(enabled = match.status == "open", onClick = {
                    val ref = db.collection("matches").document(match.id).collection("invitations").document(uid)
                    joinMatch(match.id, uid, playerName, playerWhatsapp, "invitation", ref, mapOf("status" to "accepted", "respondedAt" to Timestamp.now()), "¡Listo! Quedaste confirmado en ${match.title}.", showMessage) {
                        pushNotice(match.ownerUid, uid, "Invitación aceptada", "$playerName aceptó tu invitación a ${match.title}")
                    }
                }) { Text("Aceptar") }
                TextButton(onClick = {
                    db.collection("matches").document(match.id).collection("invitations").document(uid)
                        .update(mapOf("status" to "rejected", "respondedAt" to Timestamp.now()))
                        .addOnSuccessListener { showMessage("Invitación rechazada."); pushNotice(match.ownerUid, uid, "Invitación rechazada", "$playerName no podrá jugar ${match.title}") }
                        .addOnFailureListener { showMessage(it.localizedMessage ?: "No se pudo rechazar") }
                }) { Text("Rechazar") }
            }
        }
    }
}

@Composable
private fun PlayerInvitations(matches: List<MatchData>, uid: String, playerName: String, playerWhatsapp: String?, showMessage: (String) -> Unit) {
    var statuses by remember { mutableStateOf(mapOf<String, String>()) }
    var friendInvites by remember { mutableStateOf(listOf<FriendInvite>()) }
    val ids = matches.map { it.id }
    DisposableEffect(ids.joinToString(","), uid) {
        val regs = ids.map { id ->
            db.collection("matches").document(id).collection("invitations").document(uid).addSnapshotListener { snap, _ ->
                statuses = if (snap != null && snap.exists()) statuses + (id to (snap.getString("status") ?: "pending")) else statuses - id
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }
    DisposableEffect(uid) {
        val reg = db.collection("users").document(uid).collection("matchInvites").whereEqualTo("status", "pending").addSnapshotListener { snap, e ->
            if (e == null) friendInvites = snap?.documents.orEmpty().map { FriendInvite(it.id, it.getString("fromUid") ?: "", it.getString("fromName") ?: "Un amigo", it.getString("matchId") ?: "", it.getString("matchTitle") ?: "un partido") }
        }
        onDispose { reg.remove() }
    }
    val pending = matches.filter { statuses[it.id] == "pending" }
    val answered = matches.filter { statuses[it.id] == "accepted" || statuses[it.id] == "rejected" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Invitaciones de organizadores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (pending.isEmpty()) Text("Todavía no tenés invitaciones pendientes.", color = Color(0xFF4A4A4A))
        pending.forEach { match -> InvitationCard(match, uid, playerName, playerWhatsapp, showMessage) }

        Text("Tus amigos te invitaron", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (friendInvites.isEmpty()) Text("Cuando un amigo te sugiera un partido, aparece acá.", color = Color(0xFF4A4A4A))
        friendInvites.forEach { inv ->
            val match = matches.firstOrNull { it.id == inv.matchId }
            Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${inv.fromName} te invitó a postularte", fontWeight = FontWeight.Bold)
                Text(inv.matchTitle, style = MaterialTheme.typography.titleMedium)
                if (match == null) Text("Este partido ya no está disponible.", color = Color(0xFF4A4A4A))
                else {
                    Text("📅 ${match.dateLabel}")
                    Text("📍 ${listOf(match.venue, match.address, match.zone).filter { it.isNotBlank() }.joinToString(" · ")}")
                    Text("💰 Costo por jugador: ${match.price.ifBlank { "A confirmar" }}", fontWeight = FontWeight.SemiBold)
                }
                val inviteRef = db.collection("users").document(uid).collection("matchInvites").document(inv.id)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(enabled = match != null && match.status == "open", onClick = {
                        val m = match ?: return@Button
                        val appRef = db.collection("matches").document(m.id).collection("applications").document(uid)
                        appRef.get().addOnSuccessListener { existing ->
                            if (existing.exists()) { inviteRef.update("status", "accepted"); showMessage("Ya estabas postulado a este partido.") }
                            else {
                                val values = hashMapOf<String, Any>("uid" to uid, "playerName" to playerName, "status" to "pending", "createdAt" to Timestamp.now())
                                playerWhatsapp?.let { values["whatsapp"] = it }
                                val batch = db.batch()
                                batch.set(appRef, values)
                                batch.update(inviteRef, mapOf("status" to "accepted"))
                                batch.commit()
                                    .addOnSuccessListener {
                                        showMessage("Postulación enviada.")
                                        pushNotice(m.ownerUid, uid, "Nueva postulación", "$playerName quiere sumarse a ${m.title} (invitado por ${inv.fromName})")
                                        pushNotice(inv.fromUid, uid, "Tu amigo se postuló", "$playerName se postuló a ${m.title}")
                                    }
                                    .addOnFailureListener { showMessage("No se pudo postular: ${it.localizedMessage}") }
                            }
                        }.addOnFailureListener { showMessage("No se pudo postular: ${it.localizedMessage}") }
                    }) { Text("Postularme") }
                    TextButton(onClick = { inviteRef.update("status", "dismissed") }) { Text("Ahora no") }
                }
            } }
        }

        if (answered.isNotEmpty()) {
            Text("Respondidas", fontWeight = FontWeight.Bold)
            answered.forEach { match -> Text("${match.title} · ${statusLabel(statuses[match.id])}", color = Color(0xFF4A4A4A)) }
        }
    }
}

@Composable
private fun FriendsScreen(uid: String, myName: String, friends: List<FriendRow>, players: List<PlayerData>, matches: List<MatchData>, showMessage: (String) -> Unit) {
    var requests by remember { mutableStateOf(listOf<AppRow>()) }
    DisposableEffect(uid) {
        val reg = db.collection("users").document(uid).collection("friendRequests").whereEqualTo("status", "pending").addSnapshotListener { snap, e ->
            if (e == null) requests = snap?.documents.orEmpty().map { AppRow(it.id, it.getString("fromName") ?: "Jugador", "pending") }
        }
        onDispose { reg.remove() }
    }
    val invitable = matches.filter { it.status == "open" && it.ownerUid != uid }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mis amigos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Buscá jugadores en el Mapa y tocá Seguir. Cuando acepten, pueden invitarse entre sí a postularse a un partido.", fontWeight = FontWeight.SemiBold, color = Color(0xFF1B1B1B), fontSize = 13.sp)

        Text("Solicitudes recibidas", fontWeight = FontWeight.Bold)
        if (requests.isEmpty()) Text("No tenés solicitudes pendientes.", color = Color(0xFF4A4A4A))
        requests.forEach { r ->
            Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("${r.name} quiere seguirte", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = {
                        val me = db.collection("users").document(uid)
                        val other = db.collection("users").document(r.uid)
                        val batch = db.batch()
                        batch.update(me.collection("friendRequests").document(r.uid), mapOf("status" to "accepted", "respondedAt" to Timestamp.now()))
                        batch.set(me.collection("friends").document(r.uid), mapOf("uid" to r.uid, "name" to r.name, "since" to Timestamp.now()))
                        batch.set(other.collection("friends").document(uid), mapOf("uid" to uid, "name" to myName, "since" to Timestamp.now()))
                        batch.commit()
                            .addOnSuccessListener { showMessage("Ahora ${r.name} y vos son amigos."); pushNotice(r.uid, uid, "Solicitud aceptada", "$myName aceptó tu solicitud. ¡Ya pueden invitarse a partidos!") }
                            .addOnFailureListener { showMessage("No se pudo aceptar: ${it.localizedMessage}") }
                    }) { Text("Aceptar") }
                    TextButton(onClick = {
                        db.collection("users").document(uid).collection("friendRequests").document(r.uid)
                            .update(mapOf("status" to "rejected", "respondedAt" to Timestamp.now()))
                            .addOnFailureListener { showMessage("No se pudo rechazar: ${it.localizedMessage}") }
                    }) { Text("Rechazar") }
                }
            } }
        }

        Text("Amigos (${friends.size})", fontWeight = FontWeight.Bold)
        if (friends.isEmpty()) EmptyState("Todavía no seguís a nadie", "Abrí el Mapa, tocá un punto azul y seguí a otros jugadores.")
        friends.forEach { f ->
            val info = players.firstOrNull { it.uid == f.uid }
            var expanded by remember(f.uid) { mutableStateOf(false) }
            Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(f.name, fontWeight = FontWeight.Bold)
                info?.let { Text("${it.zone} · ${it.positions} · ${it.availability}", color = Color(0xFF333333), fontSize = 13.sp) }
                RatingBadge(f.uid)
                info?.whatsapp?.let { WhatsAppButton(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { expanded = !expanded }) { Text(if (expanded) "Cerrar" else "Invitar a un partido") }
                    TextButton(onClick = {
                        val batch = db.batch()
                        batch.delete(db.collection("users").document(uid).collection("friends").document(f.uid))
                        batch.delete(db.collection("users").document(f.uid).collection("friends").document(uid))
                        batch.commit().addOnFailureListener { showMessage("No se pudo quitar: ${it.localizedMessage}") }
                    }) { Text("Dejar de seguir") }
                }
                if (expanded) {
                    if (invitable.isEmpty()) Text("No hay partidos abiertos para invitar ahora.", color = Color(0xFF4A4A4A))
                    invitable.forEach { m ->
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                            val data = mapOf("fromUid" to uid, "fromName" to myName, "matchId" to m.id, "matchTitle" to m.title, "status" to "pending", "createdAt" to Timestamp.now())
                            db.collection("users").document(f.uid).collection("matchInvites").document("${m.id}_$uid").set(data)
                                .addOnSuccessListener { showMessage("Invitación enviada a ${f.name}."); pushNotice(f.uid, uid, "Un amigo te invitó a jugar", "$myName te invitó a postularte a ${m.title}") }
                                .addOnFailureListener { showMessage("No se pudo invitar: ${it.localizedMessage}") }
                        }) { Text("${m.title} · ${m.dateLabel}") }
                    }
                }
            } }
        }
    }
}

@Composable
private fun NotificationsScreen(uid: String, notices: List<Notice>) {
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Avisos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { notices.filter { !it.read }.forEach { n -> db.collection("users").document(uid).collection("notifications").document(n.id).update("read", true) } }) { Text("Marcar leídos") }
        }
        if (notices.isEmpty()) EmptyState("Sin avisos", "Acá vas a ver invitaciones, postulaciones y confirmaciones.")
        notices.forEach { n ->
            Card(colors = CardDefaults.cardColors(containerColor = if (n.read) Color.White else Color(0xFFE7F4DD)), onClick = {
                if (!n.read) db.collection("users").document(uid).collection("notifications").document(n.id).update("read", true)
            }) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(n.title, fontWeight = FontWeight.Bold)
                Text(n.body)
                if (n.at > 0) Text(fmt.format(Date(n.at)), fontSize = 11.sp, color = Color(0xFF4A4A4A))
            } }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(painterResource(R.drawable.ic_cancha), null, tint = Green, modifier = Modifier.size(36.dp)); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Color(0xFF4A4A4A)) } } }

// ---------- Filtros del mapa ----------
private data class MapFilter(val show: String = "Todo", val format: String = "Todos", val day: String = "Todos", val maxPrice: Long? = null)

private fun matchPasses(m: MatchData, f: MapFilter, now: Long): Boolean {
    if (f.show == "Jugadores") return false
    if (f.format != "Todos" && m.format != f.format) return false
    if (f.day != "Todos") {
        if (m.eventAtMs == 0L) return false
        val cal = Calendar.getInstance().apply { timeInMillis = m.eventAtMs }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val tomorrow = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1) }
        fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val ok = when (f.day) {
            "Hoy" -> sameDay(cal, today)
            "Mañana" -> sameDay(cal, tomorrow)
            else -> cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        }
        if (!ok) return false
    }
    if (f.maxPrice != null) {
        val digits = m.price.filter(Char::isDigit)
        val price = digits.toLongOrNull() ?: return false
        if (price > f.maxPrice) return false
    }
    return true
}

private fun playerPasses(p: PlayerData, f: MapFilter): Boolean {
    if (f.show == "Partidos") return false
    return when (f.day) {
        "Todos" -> true
        "Hoy" -> p.availability == "Ahora mismo" || p.availability == "Esta noche"
        "Mañana" -> p.availability == "Mañana"
        else -> p.availability == "Fines de semana"
    }
}

@Composable
private fun MapFilters(f: MapFilter, onChange: (MapFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = f != MapFilter()
    Card(shape = RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (active) "Filtros activos" else "Filtros", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (active) TextButton(onClick = { onChange(MapFilter()) }) { Text("Limpiar") }
            TextButton(onClick = { open = !open }) { Text(if (open) "Ocultar" else "Mostrar") }
        }
        if (open) {
            Text("Ver", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Todo", "Jugadores", "Partidos").forEach { FilterChip(f.show == it, { onChange(f.copy(show = it)) }, label = { Text(it) }) } }
            Text("Formato (partidos)", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Todos", "Fútbol 5", "Fútbol 7").forEach { FilterChip(f.format == it, { onChange(f.copy(format = it)) }, label = { Text(it) }) } }
            Text("Cuándo", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Todos", "Hoy", "Mañana", "Fin de semana").forEach { FilterChip(f.day == it, { onChange(f.copy(day = it)) }, label = { Text(it) }) } }
            Text("Costo máximo (partidos)", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Pair<String, Long?>>("Todos" to null, "\$3.000" to 3000L, "\$6.000" to 6000L, "\$10.000" to 10000L).forEach { (label, value) -> FilterChip(f.maxPrice == value, { onChange(f.copy(maxPrice = value)) }, label = { Text(label) }) }
            }
        }
    } }
}

// ---------- Chat del partido ----------
private data class ChatMsg(val id: String, val uid: String, val name: String, val text: String)

@Composable
private fun ChatPanel(matchId: String, uid: String, myName: String, showMessage: (String) -> Unit) {
    var msgs by remember(matchId) { mutableStateOf(listOf<ChatMsg>()) }
    var text by remember(matchId) { mutableStateOf("") }
    DisposableEffect(matchId) {
        val reg = db.collection("matches").document(matchId).collection("messages").orderBy("createdAt").limitToLast(100).addSnapshotListener { snap, e ->
            if (e != null) showMessage("No se pudo abrir el chat: ${e.localizedMessage}")
            else msgs = snap?.documents.orEmpty().map { ChatMsg(it.id, it.getString("uid") ?: "", it.getString("name") ?: "Jugador", it.getString("text") ?: "") }
        }
        onDispose { reg.remove() }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F6F1)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Chat del partido", fontWeight = FontWeight.Bold)
            if (msgs.isEmpty()) Text("Todavía no hay mensajes. Coordiná acá con el equipo.", color = Color(0xFF4A4A4A), fontSize = 13.sp)
            msgs.forEach { m ->
                val mine = m.uid == uid
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Surface(color = if (mine) Lime else Color.White, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            if (!mine) Text(m.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green)
                            Text(m.text)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(500) }, placeholder = { Text("Escribí un mensaje") }, modifier = Modifier.weight(1f), maxLines = 3)
                IconButton(onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        text = ""
                        db.collection("matches").document(matchId).collection("messages").add(mapOf("uid" to uid, "name" to myName, "text" to t, "createdAt" to Timestamp.now()))
                            .addOnFailureListener { showMessage("No se pudo enviar: ${it.localizedMessage}") }
                    }
                }) { Icon(Icons.Default.Send, "Enviar", tint = Green) }
            }
        }
    }
}

// ---------- Valoraciones ----------
@Composable
private fun StarPicker(value: Int, onChange: (Int) -> Unit) {
    Row { (1..5).forEach { i -> Text(if (i <= value) "★" else "☆", fontSize = 30.sp, color = Color(0xFFF9A825), modifier = Modifier.clickable { onChange(i) }.padding(horizontal = 3.dp)) } }
}

@Composable
private fun RatingBadge(userUid: String) {
    var text by remember(userUid) { mutableStateOf<String?>(null) }
    LaunchedEffect(userUid) {
        db.collection("users").document(userUid).collection("ratings").limit(100).get().addOnSuccessListener { s ->
            val n = s.size()
            text = if (n == 0) "Sin valoraciones todavía" else {
                val p = s.documents.mapNotNull { it.getLong("punctuality") }.average()
                val l = s.documents.mapNotNull { it.getLong("level") }.average()
                String.format(Locale.US, "⭐ Puntualidad %.1f · Nivel %.1f (%d valoraciones)", p, l, n)
            }
        }
    }
    text?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B1B1B)) }
}

@Composable
private fun RateRow(matchId: String, myUid: String, myName: String, target: AppRow, alreadyRated: Boolean, showMessage: (String) -> Unit) {
    var punctuality by remember(target.uid) { mutableStateOf(0) }
    var level by remember(target.uid) { mutableStateOf(0) }
    var sent by remember(target.uid, alreadyRated) { mutableStateOf(alreadyRated) }
    Card { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(target.name, fontWeight = FontWeight.Bold)
        if (sent) Text("✓ Ya valoraste a ${target.name}", color = Green, fontWeight = FontWeight.SemiBold)
        else {
            Text("Puntualidad", fontSize = 12.sp); StarPicker(punctuality) { punctuality = it }
            Text("Nivel de juego", fontSize = 12.sp); StarPicker(level) { level = it }
            Button(enabled = punctuality > 0 && level > 0, onClick = {
                val data = mapOf("fromUid" to myUid, "fromName" to myName, "matchId" to matchId, "punctuality" to punctuality, "level" to level, "createdAt" to Timestamp.now())
                db.collection("users").document(target.uid).collection("ratings").document("${matchId}_$myUid").set(data)
                    .addOnSuccessListener { sent = true; showMessage("Valoración enviada.") }
                    .addOnFailureListener { showMessage("No se pudo valorar: ${it.localizedMessage}") }
            }) { Text("Enviar valoración") }
        }
    } }
}

@Composable
private fun RatePanel(matchId: String, myUid: String, myName: String, showMessage: (String) -> Unit) {
    var team by remember(matchId) { mutableStateOf(listOf<AppRow>()) }
    var rated by remember(matchId) { mutableStateOf(setOf<String>()) }
    DisposableEffect(matchId) {
        val reg = db.collection("matches").document(matchId).collection("participants").addSnapshotListener { snap, e ->
            if (e == null) team = snap?.documents.orEmpty().filter { it.id != myUid }.map { AppRow(it.id, it.getString("name") ?: "Jugador", "accepted") }
        }
        onDispose { reg.remove() }
    }
    LaunchedEffect(team.map { it.uid }.joinToString(",")) {
        team.forEach { t ->
            db.collection("users").document(t.uid).collection("ratings").document("${matchId}_$myUid").get().addOnSuccessListener { if (it.exists()) rated = rated + t.uid }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F6F1)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Valorá a los jugadores", fontWeight = FontWeight.Bold)
            if (team.isEmpty()) Text("No hay otros jugadores confirmados para valorar.", color = Color(0xFF4A4A4A), fontSize = 13.sp)
            team.forEach { t -> key(t.uid) { RateRow(matchId, myUid, myName, t, rated.contains(t.uid), showMessage) } }
        }
    }
}

// ---------- Salir de un partido / anular un partido ----------
private fun leaveMatch(m: MatchData, uid: String, name: String, showMessage: (String) -> Unit) {
    val matchRef = db.collection("matches").document(m.id)
    val partRef = matchRef.collection("participants").document(uid)
    val appRef = matchRef.collection("applications").document(uid)
    db.runTransaction(Transaction.Function<Boolean> { tx ->
        val match = tx.get(matchRef)
        val part = tx.get(partRef)
        val app = tx.get(appRef)
        if (part.exists()) {
            val needed = match.getLong("playersNeeded") ?: 0L
            tx.delete(partRef)
            tx.update(matchRef, mapOf("playersNeeded" to needed + 1L, "status" to "open"))
            if (app.exists()) tx.delete(appRef)
        }
        true
    }).addOnSuccessListener {
        showMessage("Saliste del partido. Tu lugar quedó libre.")
        pushNotice(m.ownerUid, uid, "Un jugador se bajó", "$name salió de ${m.title}. Se liberó una vacante.")
    }.addOnFailureListener { showMessage("No se pudo salir: ${it.localizedMessage}") }
}

private fun cancelMatch(m: MatchData, notify: Set<String>, showMessage: (String) -> Unit) {
    db.collection("matches").document(m.id).update(mapOf("status" to "cancelled", "cancelledAt" to Timestamp.now()))
        .addOnSuccessListener {
            notify.forEach { pushNotice(it, m.ownerUid, "Partido anulado", "${m.title} (${m.dateLabel}) fue anulado por el organizador.") }
            showMessage("Partido anulado. Avisamos a ${notify.size} personas.")
        }
        .addOnFailureListener { showMessage("No se pudo anular: ${it.localizedMessage}") }
}
