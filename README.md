# Fútbol Cerca 3.1.0

Proyecto Android Kotlin + Jetpack Compose + Firebase Authentication/Firestore + OpenStreetMap (osmdroid).

## Novedades 3.1.0
- **Valoraciones**: terminado el partido, los confirmados se valoran entre sí (puntualidad y nivel, 1 a 5 estrellas; una vez por persona y partido, no se puede editar). El promedio se ve en el mapa y en Amigos.
- **Chat del partido** entre el organizador y los confirmados.
- **Salir de un partido**: el jugador libera su lugar (vuelve una vacante y se avisa al organizador).
- **Anular partido**: el organizador lo cancela y se avisa a confirmados, postulados e invitados.
- **Filtros del mapa**: ver jugadores/partidos, formato (F5/F7), cuándo (hoy, mañana, fin de semana) y costo máximo.
- Los partidos desaparecen de las listas y del mapa 48 h después de su inicio.
- Reglas nuevas: `ratings`, `messages` y salida de partido (hay que volver a publicar `firestore.rules`).

## Novedades 3.0.0
- **Roles**: solo *Jugador* y *Capitán / Organizador* (se quitó "dueño de cancha"; las cuentas viejas de cancha pasan a Capitán).
- **Capitán**: ya no ve una lista de jugadores. Los ve en el mapa como puntos azules dentro de su zona; al tocar uno ve sus datos (y su WhatsApp si lo hizo público) y puede **invitarlo** a un partido suyo. Los jugadores que se postulan aparecen en *Pedidos*.
- **Red de jugadores**: desde el mapa se puede **seguir** a otro jugador; al aceptar son amigos. Desde *Amigos* se invitan entre sí a postularse a un partido; el invitado lo ve en *Invitaciones → Tus amigos te invitaron*.
- **Invitación completa**: muestra costo, dirección de la cancha, organizador, vacantes y **quiénes ya están confirmados** en el equipo, con Aceptar / Rechazar.
- **Avisos**: campana con contador + notificación del teléfono cuando llega una invitación, postulación, confirmación o solicitud de amistad. Con la app abierta o en segundo plano llegan al instante; con la app cerrada se revisan cada ~15 min (WorkManager). El aviso instantáneo con la app cerrada requiere Firebase Cloud Messaging + Cloud Functions (plan Blaze), no incluido.
- **WhatsApp**: cada usuario puede cargarlo en Perfil y elegir con un interruptor si se muestra a otros (mapa, partidos, equipo). Si está apagado queda guardado solo para él (`users/{uid}/private/contact`).
- Al publicar un partido se pide la **dirección** de la cancha (solo se muestra la zona en el mapa).
- Texto bajo el mapa en negrita y color oscuro; textos secundarios más legibles.

## Novedades 2.3.0
- Ícono de la app (cancha de fútbol) y recursos `res/` que faltaban.
- Pantalla solo vertical, pensada para celulares: chips y botones se acomodan en varias líneas, barra inferior con etiquetas cortas.
- Los formularios suben con el teclado y se pueden desplazar; la barra inferior se oculta mientras se escribe.
- El mapa se ajusta solo para mostrar todas las zonas, indica cuántos jugadores/partidos tienen zona y completa solas las zonas que quedaron sin coordenadas.
- Si Firestore rechaza una lectura (reglas sin publicar), aparece un cartel rojo con el error real en vez de quedar el mapa vacío.

## Novedades 2.2.0
- **Aceptar/rechazar ya no da PERMISSION_DENIED** (hay que publicar las reglas nuevas, ver abajo).
- **Conexión real jugador ↔ capitán/dueño**: al aceptar una postulación o invitación el jugador queda *confirmado* (`matches/{id}/participants`) y se descuenta una vacante; cuando llega a 0 el partido pasa a "COMPLETO".
  - El jugador ve en cada partido el estado de su postulación (pendiente / aceptada / rechazada) en tiempo real y un historial de invitaciones.
  - El organizador, en *Solicitudes*, ve por partido: confirmados, postulaciones (aceptar/rechazar) e invitaciones enviadas con su estado.
- **Mapa por zonas**: lo que se escribe en "Barrio, zona o dirección" se convierte (OpenStreetMap Nominatim) en un círculo. Los jugadores y partidos de la misma zona se agrupan en un solo círculo con un número; al tocarlo se ven quiénes son. Nunca se dibuja la dirección exacta (el círculo es de ≥ 600 m y el centro se redondea).

## Cambios anteriores
- Interfaz renovada con navegación inferior y tarjetas Material 3.
- Mapa OpenStreetMap integrado dentro de la app (no abre el navegador).
- Marcadores de jugadores y partidos que tengan coordenadas guardadas.
- Solicitud de permiso de ubicación y guardado de latitud/longitud aproximadas desde Perfil.
- Publicación de partidos en Firestore con fecha y hora mediante selectores nativos de calendario y reloj.
- Listado de partidos en tiempo real y postulaciones guardadas en Firestore.
- Selección de jugadores e invitaciones por partido; pantalla para responder invitaciones.
- Pantalla para revisar y aceptar/rechazar postulaciones.
- Reglas Firestore para usuarios, partidos, postulaciones e invitaciones.
- WhatsApp CodeMarket: +54 9 261 511-7220.

## IMPORTANTE: publicar las reglas de Firestore
Subir `firestore.rules` a GitHub **no cambia automáticamente** las reglas activas del proyecto Firebase. (Alternativa por consola: `firebase deploy --only firestore:rules`; el repo ya trae `firebase.json`.) Para que crear partidos, postularse e invitar jugadores funcione, hay que abrir Firebase Console → Firestore Database → Rules, reemplazar el contenido por `firestore.rules` y presionar **Publicar**. No cambies las reglas de Authentication.

## Mapa y privacidad
El mapa usa OpenStreetMap sin Google Maps ni facturación de Google. El usuario debe entrar en Perfil y tocar “Actualizar mi ubicación en el mapa”, aceptar el permiso y tener activada la ubicación del teléfono. Se guarda latitud/longitud del teléfono para mostrar un marcador; la interfaz pide además una zona/barrio aproximado. No se debe usar una dirección particular como zona pública.

Los marcadores solo pueden aparecer para cuentas que hayan guardado coordenadas. Los perfiles antiguos no tienen ubicación automáticamente: cada usuario debe actualizarla. Los mapas dependen de conexión a Internet para cargar las teselas de OpenStreetMap.

## Compilación
GitHub Actions ejecuta `gradle assembleDebug` y publica el artefacto `app-debug.apk`. La versión usa `applicationId` `com.futbolcerca.app`, `versionCode` 3 y `versionName` 2.1.0.

## Límites y verificación pendiente
Este código se preparó para corregir los problemas reportados, pero **no fue compilado ni probado contra el Firebase real en este entorno**. Hay que comprobar el resultado de GitHub Actions y luego probar registro, publicación, mapa, postulación e invitaciones con dos cuentas distintas. La prueba de 7 días y el sistema de licencias siguen sin validación segura ni panel de administración; no se presentan como funciones terminadas. No incluye almacenamiento de fotos/videos ni notificaciones push.

No se necesita Firebase Blaze, Google Maps billing, Cloud Functions ni un servidor pago para estas funciones básicas.
