package com.futbolcerca.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.net.URLEncoder

private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()
private const val WHATSAPP = "https://wa.me/542615117220"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Root() } }
    }
}

@Composable
private fun Root() {
    var user by remember { mutableStateOf(auth.currentUser) }
    var profile by remember { mutableStateOf<Map<String, Any>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { user = it.currentUser; profile = null }
        auth.addAuthStateListener(l)
        onDispose { auth.removeAuthStateListener(l) }
    }
    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        busy = true
        db.collection("users").document(uid).get()
            .addOnSuccessListener { profile = it.data; busy = false }
            .addOnFailureListener { error = it.localizedMessage ?: "Error de perfil"; busy = false }
    }

    if (user == null) AuthPage(
        onBusy = { busy = it },
        onError = { error = it },
        onReady = { profile = it }
    ) else if (busy || profile == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Text("Cargando cuenta…")
        }
    } else {
        MainPage(
            uid = user!!.uid,
            profile = profile!!,
            onError = { error = it },
            onLogout = { auth.signOut(); profile = null }
        )
    }
    if (error.isNotBlank()) AlertDialog(
        onDismissRequest = { error = "" },
        confirmButton = { TextButton2("Cerrar") { error = "" } },
        title = { Text("Fútbol Cerca") },
        text = { Text(error) }
    )
}

@Composable
private fun TextButton2(label: String, click: () -> Unit) {
    TextButton(onClick = click) { Text(label) }
}

@Composable
private fun AuthPage(onBusy: (Boolean) -> Unit, onError: (String) -> Unit,
                     onReady: (Map<String, Any>) -> Unit) {
    var registering by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Jugador") }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
        Text("⚽ FÚTBOL CERCA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(if (registering) "Creá tu cuenta" else "Ingresá")
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            Text("Tipo de cuenta")
            listOf("Jugador", "Capitán", "Cancha").forEach {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(role == it, { role = it })
                    Text(if (it == "Cancha") "Dueño de cancha" else it)
                }
            }
        }
        OutlinedTextField(email, { email = it }, label = { Text("Correo") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña (6 caracteres mínimo)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (email.isBlank() || password.length < 6 || (registering && name.isBlank())) {
                onError("Completá los datos. La contraseña debe tener al menos 6 caracteres."); return@Button
            }
            onBusy(true)
            val task = if (registering) auth.createUserWithEmailAndPassword(email.trim(), password)
                       else auth.signInWithEmailAndPassword(email.trim(), password)
            task.addOnSuccessListener { result ->
                if (registering) {
                    val uid = result.user!!.uid
                    val data = hashMapOf<String, Any>(
                        "displayName" to name.trim(), "role" to role,
                        "verificationStatus" to if (role == "Jugador") "not_required" else "pending",
                        "createdAt" to Timestamp.now(), "trialStartedAt" to Timestamp.now()
                    )
                    db.collection("users").document(uid).set(data)
                        .addOnSuccessListener { onBusy(false); onReady(data) }
                        .addOnFailureListener { onBusy(false); onError(it.localizedMessage ?: "No se guardó el perfil") }
                } else {
                    db.collection("users").document(result.user!!.uid).get()
                        .addOnSuccessListener { onBusy(false); onReady(it.data ?: emptyMap()) }
                        .addOnFailureListener { onBusy(false); onError(it.localizedMessage ?: "No se pudo cargar la cuenta") }
                }
            }.addOnFailureListener { onBusy(false); onError(it.localizedMessage ?: "Error de autenticación") }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (registering) "Crear cuenta" else "Ingresar") }
        TextButton2(if (registering) "Ya tengo cuenta" else "Crear cuenta") { registering = !registering }
        Text("Prueba gratuita anunciada: 7 días. La activación comercial se solicita a CodeMarket.")
    }
}

private data class MatchItem(
    val id: String, val title: String, val mode: String, val format: String,
    val dateTime: String, val venue: String, val zone: String, val price: String,
    val ownerUid: String, val ownerName: String, val status: String
)

@Composable
private fun MainPage(uid: String, profile: Map<String, Any>, onError: (String) -> Unit,
                     onLogout: () -> Unit) {
    val role = profile["role"] as? String ?: "Jugador"
    val name = profile["displayName"] as? String ?: "Usuario"
    var tab by remember { mutableStateOf(if (role == "Jugador") "Partidos" else if (role == "Capitán") "Equipo" else "Gestión") }
    var matches by remember { mutableStateOf(listOf<MatchItem>()) }
    var people by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var apps by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var selectedMatch by remember { mutableStateOf<MatchItem?>(null) }
    var nameEdit by remember { mutableStateOf(name) }
    var age by remember { mutableStateOf((profile["age"] as? Long)?.toString() ?: "") }
    var gender by remember { mutableStateOf(profile["gender"] as? String ?: "Masculino") }
    var positions by remember { mutableStateOf(profile["positions"] as? String ?: "") }
    var skills by remember { mutableStateOf(profile["skills"] as? String ?: "") }
    var availability by remember { mutableStateOf(profile["availability"] as? String ?: "Ahora mismo") }
    var zone by remember { mutableStateOf(profile["zone"] as? String ?: "") }

    DisposableEffect(role) {
        val registrations = mutableListOf<ListenerRegistration>()
        val m = db.collection("matches").whereEqualTo("status", "open")
            .addSnapshotListener { snap, e ->
                if (e != null) onError(e.localizedMessage ?: "Error cargando partidos")
                else matches = snap?.documents.orEmpty().map { d ->
                    MatchItem(d.id, d.getString("title") ?: "Partido",
                        d.getString("mode") ?: "Mixto", d.getString("format") ?: "Fútbol 5",
                        d.getString("dateTime") ?: "", d.getString("venue") ?: "",
                        d.getString("zone") ?: "", d.getString("price") ?: "",
                        d.getString("ownerUid") ?: "", d.getString("ownerName") ?: "",
                        d.getString("status") ?: "open")
                }
            }
        registrations.add(m)
        if (role == "Capitán") {
            registrations.add(db.collection("users").whereEqualTo("role", "Jugador")
                .addSnapshotListener { snap, e ->
                    if (e != null) onError(e.localizedMessage ?: "Error buscando jugadores")
                    else people = snap?.documents.orEmpty().filter { it.id != uid }.mapNotNull { it.data }
                })
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    val context = LocalContext.current
    Scaffold(bottomBar = {
        NavigationBar {
            val tabs = when (role) {
                "Jugador" -> listOf("Partidos", "Mapa", "Perfil")
                "Capitán" -> listOf("Equipo", "Buscar", "Solicitudes", "Publicar")
                else -> listOf("Gestión", "Partidos", "Publicar")
            }
            tabs.forEach { t -> NavigationBarItem(tab == t, { tab = t }, icon = { Text("⚽") }, label = { Text(t) }) }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Hola, ${name.substringBefore(" ")}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("FÚTBOL CERCA · $role")
            }

            if (role == "Jugador" && tab == "Perfil") {
                item { Text("Mi perfil deportivo", style = MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(nameEdit, { nameEdit = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(age, { age = it.filter(Char::isDigit).take(2) }, label = { Text("Edad") }, modifier = Modifier.fillMaxWidth()) }
                item { Text("Género") }
                item { Row { listOf("Masculino", "Femenino", "Otro").forEach { FilterChip(gender == it, { gender = it }, label = { Text(it) }) } } }
                item { OutlinedTextField(positions, { positions = it }, label = { Text("Posiciones") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(skills, { skills = it }, label = { Text("Habilidades") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(availability, { availability = it }, label = { Text("Disponibilidad") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(zone, { zone = it }, label = { Text("Zona o barrio aproximado") }, modifier = Modifier.fillMaxWidth()) }
                item { Button(onClick = {
                    val data = hashMapOf<String, Any>("displayName" to nameEdit.trim(), "gender" to gender,
                        "positions" to positions.trim(), "skills" to skills.trim(), "availability" to availability.trim(),
                        "zone" to zone.trim(), "profileUpdatedAt" to Timestamp.now())
                    age.toIntOrNull()?.let { data["age"] = it }
                    db.collection("users").document(uid).set(data, SetOptions.merge())
                        .addOnSuccessListener { onError("Perfil guardado") }
                        .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo guardar") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar perfil") } }
            } else if (tab == "Mapa") {
                item { Text("Mapa de OpenStreetMap", style = MaterialTheme.typography.titleLarge) }
                item { Text("El mapa abre en el navegador usando la zona aproximada del perfil. No publica tu domicilio.") }
                item { Button(onClick = {
                    val q = URLEncoder.encode(zone.ifBlank { "Mendoza Argentina" }, "UTF-8")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/search?query=$q")))
                }) { Text("Ver mi zona en el mapa") } }
            } else if (tab == "Publicar") {
                item { PublishMatch(uid, name, zone, onError) }
            } else if (tab == "Buscar") {
                item { Text("Jugadores registrados", style = MaterialTheme.typography.titleLarge) }
                items(people) { p ->
                    Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(p["displayName"] as? String ?: "Jugador", fontWeight = FontWeight.Bold)
                        Text("Zona: ${p["zone"] ?: "No indicada"} · ${p["positions"] ?: "Posición no indicada"}")
                        Text("Disponibilidad: ${p["availability"] ?: "No indicada"}")
                    } }
                }
            } else if (tab == "Solicitudes") {
                item { Text("Elegí uno de tus partidos para revisar postulaciones.") }
                items(matches.filter { it.ownerUid == uid }) { m ->
                    Button(onClick = { selectedMatch = m }) { Text(m.title) }
                }
                selectedMatch?.let { m ->
                    item { ApplicationList(m.id, uid, onError) }
                }
            } else if (tab == "Equipo" || tab == "Gestión" || tab == "Partidos" || tab == "Mapa") {
                item { Text(if (role == "Jugador") "Partidos disponibles" else "Partidos publicados", style = MaterialTheme.typography.titleLarge) }
                items(if (role == "Jugador") matches else matches.filter { it.ownerUid == uid }) { m ->
                    MatchCard(m, role == "Jugador", uid, name, onError)
                }
                if (role != "Jugador") item { Button(onClick = { tab = "Publicar" }, modifier = Modifier.fillMaxWidth()) { Text("Crear partido") } }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Licencias y soporte: CodeMarket", fontWeight = FontWeight.Bold)
                Text("Prueba gratuita: 7 días. Para solicitar licencia mensual o anual:")
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP))) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Contactar por WhatsApp · 2615117220")
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
            }
        }
    }
}

@Composable
private fun PublishMatch(uid: String, owner: String, defaultZone: String, onError: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Mixto") }
    var format by remember { mutableStateOf("Fútbol 5") }
    var date by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(defaultZone) }
    var price by remember { mutableStateOf("") }
    var places by remember { mutableStateOf("1") }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Publicar partido", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it }, label = { Text("Título / convocatoria") }, modifier = Modifier.fillMaxWidth())
            Text("Modalidad")
            Row { listOf("Masculino", "Femenino", "Mixto").forEach { FilterChip(mode == it, { mode = it }, label = { Text(it) }) } }
            Text("Formato")
            Row { listOf("Fútbol 5", "Fútbol 7").forEach { FilterChip(format == it, { format = it }, label = { Text(it) }) } }
            OutlinedTextField(date, { date = it }, label = { Text("Fecha y hora (ej. 22/11 20:00)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(venue, { venue = it }, label = { Text("Cancha / lugar") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(zone, { zone = it }, label = { Text("Zona aproximada") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price, { price = it }, label = { Text("Costo por jugador (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(places, { places = it.filter(Char::isDigit).take(2) }, label = { Text("Jugadores que faltan") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                if (title.isBlank() || date.isBlank() || venue.isBlank()) { onError("Completá título, fecha y cancha."); return@Button }
                val data = hashMapOf<String, Any>(
                    "title" to title.trim(), "mode" to mode, "format" to format,
                    "dateTime" to date.trim(), "venue" to venue.trim(), "zone" to zone.trim(),
                    "price" to price.trim(), "playersNeeded" to (places.toIntOrNull() ?: 1),
                    "ownerUid" to uid, "ownerName" to owner, "status" to "open",
                    "createdAt" to Timestamp.now()
                )
                db.collection("matches").add(data)
                    .addOnSuccessListener { onError("Partido publicado correctamente.") }
                    .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo publicar el partido") }
            }, modifier = Modifier.fillMaxWidth()) { Text("Publicar") }
        }
    }
}

@Composable
private fun MatchCard(m: MatchItem, allowApply: Boolean, uid: String, playerName: String, onError: (String) -> Unit) {
    val context = LocalContext.current
    var already by remember(m.id) { mutableStateOf(false) }
    LaunchedEffect(m.id, uid) {
        db.collection("matches").document(m.id).collection("applications").document(uid).get()
            .addOnSuccessListener { already = it.exists() }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${m.format} · ${m.mode}")
            Text("Fecha: ${m.dateTime}")
            Text("Lugar: ${m.venue} · Zona: ${m.zone}")
            Text("Organiza: ${m.ownerName}")
            Text("Costo: ${m.price.ifBlank { "A confirmar" }}")
            if (allowApply) {
                Button(enabled = !already, onClick = {
                    val data = mapOf("uid" to uid, "playerName" to playerName, "status" to "pending", "createdAt" to Timestamp.now())
                    db.collection("matches").document(m.id).collection("applications").document(uid).set(data)
                        .addOnSuccessListener { already = true; onError("Postulación enviada.") }
                        .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo postular") }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (already) "Ya te postulaste" else "Postularme") }
            }
            OutlinedButton(onClick = {
                val msg = URLEncoder.encode("Hola, me interesa el partido ${m.title} (${m.dateTime}).", "UTF-8")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$msg")))
            }, modifier = Modifier.fillMaxWidth()) { Text("Consultar por WhatsApp") }
        }
    }
}

@Composable
private fun ApplicationList(matchId: String, ownerUid: String, onError: (String) -> Unit) {
    var applications by remember(matchId) { mutableStateOf(listOf<Map<String, Any>>()) }
    DisposableEffect(matchId) {
        val reg = db.collection("matches").document(matchId).collection("applications")
            .addSnapshotListener { snap, e ->
                if (e != null) onError(e.localizedMessage ?: "Error de solicitudes")
                else applications = snap?.documents.orEmpty().mapNotNull { it.data }
            }
        onDispose { reg.remove() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Postulaciones", style = MaterialTheme.typography.titleLarge)
        if (applications.isEmpty()) Text("Todavía no hay postulaciones.")
        applications.forEach { a ->
            Card { Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(a["playerName"] as? String ?: "Jugador", fontWeight = FontWeight.Bold)
                Text("Estado: ${a["status"] ?: "pending"}")
                val applicant = a["uid"] as? String ?: ""
                Row {
                    Button(onClick = {
                        db.collection("matches").document(matchId).collection("applications").document(applicant)
                            .update("status", "accepted")
                            .addOnSuccessListener { onError("Postulación aceptada.") }
                            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo aceptar") }
                    }) { Text("Aceptar") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        db.collection("matches").document(matchId).collection("applications").document(applicant)
                            .update("status", "rejected")
                            .addOnSuccessListener { onError("Postulación rechazada.") }
                            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo rechazar") }
                    }) { Text("Rechazar") }
                }
            } }
        }
    }
}
