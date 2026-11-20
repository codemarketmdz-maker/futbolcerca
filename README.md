# Fútbol Cerca 2.2.0

Proyecto Android Kotlin + Jetpack Compose + Firebase Authentication/Firestore + OpenStreetMap (osmdroid).

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
