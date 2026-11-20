package com.futbolcerca.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp

private val Forest = Color(0xFF123B2D)
private val Lime = Color(0xFFC6F36B)
private val Pale = Color(0xFFF3F6F1)
private val Ink = Color(0xFF17251E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuthGate() }
    }
}


@Composable
private fun AuthGate() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var storedRole by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser; storedRole = null }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        loading = true
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc -> storedRole = doc.getString("role"); loading = false }
            .addOnFailureListener { e -> error = e.localizedMessage ?: "No se pudo leer el perfil"; loading = false }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Lime, background = Pale, surface = Color.White)) {
        when {
            currentUser == null -> AuthScreen(auth, db, onError = { error = it }, onLoading = { loading = it })
            loading -> Box(Modifier.fillMaxSize().background(Pale), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            storedRole == null -> RoleSetupScreen(currentUser!!.uid, db, onError = { error = it }, onLoading = { loading = it })
            else -> FutbolCercaApp(role = storedRole!!, onLogout = { auth.signOut(); storedRole = null })
        }
        error?.let { msg -> AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton(onClick = { error = null }) { Text("Cerrar") } }, title = { Text("Atención") }, text = { Text(msg) }) }
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth, db: FirebaseFirestore, onError: (String) -> Unit, onLoading: (Boolean) -> Unit) {
    var registering by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Jugador") }
    Column(Modifier.fillMaxSize().background(Pale).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.SportsSoccer, null, tint = Forest, modifier = Modifier.size(58.dp))
        Text("FÚTBOL CERCA", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Forest)
        Text(if (registering) "Creá tu cuenta" else "Bienvenido nuevamente", color = Color.Gray)
        Spacer(Modifier.height(22.dp))
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text("Tipo de cuenta", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Column(Modifier.fillMaxWidth()) {
                listOf("Jugador", "Capitán", "Cancha").forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == item, onClick = { role = item })
                        Text(when(item) { "Cancha" -> "Dueño / administrador de cancha"; else -> item })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mínimo 6 caracteres)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (email.isBlank() || password.length < 6 || (registering && name.isBlank())) { onError("Completá los datos. La contraseña debe tener al menos 6 caracteres."); return@Button }
            onLoading(true)
            val task = if (registering) auth.createUserWithEmailAndPassword(email.trim(), password) else auth.signInWithEmailAndPassword(email.trim(), password)
            task.addOnSuccessListener { result ->
                if (registering) {
                    val uid = result.user!!.uid
                    val data = hashMapOf("displayName" to name.trim(), "role" to role, "verificationStatus" to if (role == "Jugador") "not_required" else "pending", "createdAt" to Timestamp.now())
                    db.collection("users").document(uid).set(data).addOnSuccessListener { onLoading(false) }.addOnFailureListener { onLoading(false); onError(it.localizedMessage ?: "No se pudo guardar el perfil") }
                } else onLoading(false)
            }.addOnFailureListener { onLoading(false); onError(it.localizedMessage ?: "No se pudo iniciar sesión") }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (registering) "Crear cuenta" else "Ingresar") }
        TextButton(onClick = { registering = !registering }) { Text(if (registering) "Ya tengo cuenta · Iniciar sesión" else "Crear una cuenta nueva") }
        Text("Cada cuenta tiene un único tipo de acceso. Los capitanes y canchas quedan pendientes de verificación.", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun RoleSetupScreen(uid: String, db: FirebaseFirestore, onError: (String) -> Unit, onLoading: (Boolean) -> Unit) {
    var role by remember { mutableStateOf("Jugador") }
    Column(Modifier.fillMaxSize().background(Pale).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Completá tu tipo de cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Forest)
        Text("Esta elección define las pantallas y permisos de tu cuenta.", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        listOf("Jugador", "Capitán", "Cancha").forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(role == item, { role = item }); Text(if (item == "Cancha") "Dueño / administrador de cancha" else item) } }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            onLoading(true)
            val data = mapOf("role" to role, "verificationStatus" to if (role == "Jugador") "not_required" else "pending", "createdAt" to Timestamp.now())
            db.collection("users").document(uid).set(data, SetOptions.merge()).addOnSuccessListener { onLoading(false) }.addOnFailureListener { onLoading(false); onError(it.localizedMessage ?: "No se pudo guardar el tipo de cuenta") }
        }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
    }
}

data class Match(
    val title: String, val mode: String, val format: String, val time: String,
    val place: String, val distance: String, val missing: Int, val price: String
)

private val sampleMatches = listOf(
    Match("Partido de esta noche", "Mixto", "Fútbol 5", "Hoy · 23:30", "Cancha Centro", "1,2 km", 1, "$4.000"),
    Match("Buscamos dos jugadores", "Masculino", "Fútbol 7", "Mañana · 20:00", "Complejo Las Heras", "3,5 km", 2, "$3.500"),
    Match("Fútbol femenino", "Femenino", "Fútbol 5", "Sábado · 18:00", "Cancha Parque", "2,1 km", 2, "$3.000")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FutbolCercaApp(role: String, onLogout: () -> Unit) {
    var page by remember { mutableStateOf(if (role == "Jugador") "Partidos" else if (role == "Capitán") "Equipo" else "Gestión") }
    var selectedMode by remember { mutableStateOf("Todas") }
    var applied by remember { mutableStateOf(setOf<String>()) }
    var approved by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var playerName by remember { mutableStateOf("Martín González") }
    var position by remember { mutableStateOf("Delantero") }
    var availability by remember { mutableStateOf("Ahora mismo") }
    var gender by remember { mutableStateOf("Masculino") }
    val context = LocalContext.current

    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Lime, background = Pale, surface = Color.White)) {
        Scaffold(
            containerColor = Pale,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SportsSoccer, null, tint = Lime, modifier = Modifier.size(27.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("FÚTBOL CERCA", fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Forest)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val items = when (role) {
                        "Jugador" -> listOf("Partidos" to Icons.Default.SportsSoccer, "Mapa" to Icons.Default.Map, "Perfil" to Icons.Default.Person)
                        "Capitán" -> listOf("Equipo" to Icons.Default.Groups, "Buscar" to Icons.Default.PersonSearch, "Solicitudes" to Icons.Default.MarkEmailUnread)
                        else -> listOf("Gestión" to Icons.Default.Business, "Partidos" to Icons.Default.SportsSoccer, "Publicar" to Icons.Default.Add)
                    }
                    items.forEach { (label, icon) ->
                        NavigationBarItem(selected = page == label, onClick = { page = label }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Hola, ${playerName.substringBefore(" ")} 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                            Text("Encontrá tu próximo partido", color = Color.Gray)
                        }
                        Surface(color = Color(0xFFE3F5D3), shape = CircleShape) { Text("DEMO", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Forest, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                }
                if (role == "Jugador") {
                    when (page) {
                        "Partidos" -> {
                            item { Text("Partidos cerca de vos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Todas", "Masculino", "Femenino", "Mixto").forEach { mode ->
                                        FilterChip(selected = selectedMode == mode, onClick = { selectedMode = mode }, label = { Text(mode, fontSize = 11.sp) })
                                    }
                                }
                            }
                            items(sampleMatches.filter { selectedMode == "Todas" || it.mode == selectedMode }) { match ->
                                MatchCard(match, applied.contains(match.title), onApply = { applied = applied + match.title }, onWhatsApp = {
                                    val text = "Hola, me interesa sumarme al partido: ${match.title} - ${match.time}. ¿Hay lugar?"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(text)))
                                    context.startActivity(intent)
                                })
                            }
                        }
                        "Mapa" -> {
                            item { SectionTitle("Mapa de oportunidades", "Vista conceptual: próximamente mostrará canchas y jugadores según tu ubicación y permisos.") }
                            item { MapPlaceholder() }
                            item { Text("La ubicación exacta de los jugadores nunca será pública. Se mostrará una zona aproximada.", color = Color.Gray, fontSize = 13.sp) }
                        }
                        else -> {
                            item { SectionTitle("Mi perfil deportivo", "Completá tus datos para que los equipos puedan conocerte.") }
                            item {
                                Card(shape = RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(value = playerName, onValueChange = { playerName = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth())
                                        Text("Categoría", fontWeight = FontWeight.SemiBold)
                                        ChoiceRow(listOf("Masculino", "Femenino"), gender) { gender = it }
                                        Text("Posición", fontWeight = FontWeight.SemiBold)
                                        DropdownChoice(position, listOf("Arquero", "Defensor", "Mediocampista", "Delantero")) { position = it }
                                        Text("Disponibilidad", fontWeight = FontWeight.SemiBold)
                                        DropdownChoice(availability, listOf("Ahora mismo", "Esta noche", "Mañana", "Fines de semana")) { availability = it }
                                        Text("Foto, video, edad, habilidades y zona se incorporarán al formulario completo.", color = Color.Gray, fontSize = 12.sp)
                                        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Guardar perfil (demo)") }
                                    }
                                }
                            }
                        }
                    }
                } else if (role == "Capitán") {
                    when (page) {
                        "Buscar" -> {
                            item { SectionTitle("Buscar jugadores", "Resultados ficticios para mostrar el flujo de selección.") }
                            items(listOf("Federico Martínez · Delantero · 800 m", "Sofía López · Mediocampista · 1,1 km", "Lucas Pérez · Arquero · 1,6 km")) { p ->
                                Card(shape = RoundedCornerShape(16.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(42.dp), tint = Forest)
                                        Spacer(Modifier.width(10.dp)); Text(p, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                        TextButton(onClick = { approved = true }) { Text("Invitar") }
                                    }
                                }
                            }
                            if (approved) item { Text("Invitación preparada. El envío real requiere conexión y consentimiento del jugador.", color = Forest, fontSize = 12.sp) }
                        }
                        "Solicitudes" -> {
                            item { SectionTitle("Postulaciones", "Revisá el perfil antes de confirmar un ingreso.") }
                            item {
                                Card(shape = RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Federico Martínez", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("25 años · Delantero · Disponible ahora · A 800 m")
                                        Text("Habilidades: técnica 8/10 · velocidad 7/10 · resistencia 8/10", color = Color.Gray, fontSize = 12.sp)
                                        if (approved) Text("Jugador aprobado para el equipo", color = Forest, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { approved = true }, modifier = Modifier.weight(1f)) { Text("Aceptar") }
                                            OutlinedButton(onClick = { approved = false }, modifier = Modifier.weight(1f)) { Text("Rechazar") }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            item { SectionTitle("Panel del capitán", "Tu equipo, las vacantes y las solicitudes en un solo lugar.") }
                            item { StatRow("4 / 5", "Jugadores confirmados", "Falta 1 jugador") }
                            item { Text("Partido de hoy · 23:30 · Fútbol 5 · Mixto", fontWeight = FontWeight.SemiBold) }
                            item { Button(onClick = { page = "Buscar" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonSearch, null); Spacer(Modifier.width(8.dp)); Text("Buscar jugador faltante") } }
                            item { OutlinedButton(onClick = { page = "Solicitudes" }, modifier = Modifier.fillMaxWidth()) { Text("Revisar postulaciones") } }
                            item { Button(onClick = { urgent = !urgent }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB64A35))) { Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(8.dp)); Text(if (urgent) "Desactivar búsqueda urgente" else "¡Necesitamos jugadores!") } }
                            if (urgent) item { Text("Búsqueda urgente activada (simulación). En producción avisará a jugadores cercanos que estén disponibles.", color = Forest, fontSize = 12.sp) }
                        }
                    }
                } else {
                    item { SectionTitle("Administración de cancha", "Publicá horarios y partidos que necesitan completar equipos.") }
                    item { StatRow("6", "Partidos activos", "4 buscan jugadores") }
                    item { Text("Próximos encuentros", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                    items(sampleMatches) { m -> MatchCard(m, false, {}, {}) }
                    item { Button(onClick = { page = "Publicar" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Publicar partido") } }
                    if (page == "Publicar") item { Text("Formulario de publicación completo y calendario se implementarán en la siguiente etapa.", color = Color.Gray, fontSize = 12.sp) }
                }
                item { Spacer(Modifier.height(8.dp)); Text("FÚTBOL CERCA · Cuenta: $role", color = Color.Gray, fontSize = 10.sp); OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión") } }
            }
        }
    }
}

@Composable
private fun MatchCard(match: Match, isApplied: Boolean, onApply: () -> Unit, onWhatsApp: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFEAF5E3), shape = RoundedCornerShape(30.dp)) { Text("FALTAN ${match.missing}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Forest, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Text("A ${match.distance}", color = Color.Gray, fontSize = 12.sp)
            }
            Text(match.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SportsSoccer, null, tint = Forest, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("${match.format} · ${match.mode}", fontSize = 13.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(match.time, fontSize = 13.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(match.place, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${match.price} por jugador", color = Forest, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (isApplied) Text("Postulado ✓", color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply, modifier = Modifier.weight(1f), enabled = !isApplied) { Text(if (isApplied) "Enviado" else "Postularme") }
                OutlinedButton(onClick = onWhatsApp) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("WhatsApp") }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink); Text(subtitle, color = Color.Gray, fontSize = 13.sp) } }
@Composable private fun StatRow(number: String, label: String, detail: String) { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Forest)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(number, color = Lime, fontSize = 30.sp, fontWeight = FontWeight.Black); Text(label, color = Color.White, fontWeight = FontWeight.SemiBold) }; Text(detail, color = Color(0xFFD8E6DC), fontSize = 12.sp) } } }
@Composable private fun MapPlaceholder() { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDDE9D7))) { Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Map, null, tint = Forest, modifier = Modifier.size(52.dp)); Text("MAPA DE JUGADORES Y CANCHAS", color = Forest, fontWeight = FontWeight.Bold); Text("Integración de mapas en próxima etapa", color = Forest, fontSize = 11.sp) } } } }
@Composable private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { FilterChip(selected = selected == it, onClick = { onSelect(it) }, label = { Text(it) }) } } }
@Composable private fun DropdownChoice(value: String, options: List<String>, onSelect: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false }) } } } }
