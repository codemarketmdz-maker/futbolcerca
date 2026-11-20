# Fútbol Cerca 2.1.0 — base de corrección

Proyecto Android Kotlin + Jetpack Compose + Firebase Authentication/Firestore + OpenStreetMap (osmdroid).

## Cambios incluidos
- Interfaz renovada con navegación inferior y tarjetas Material 3.
- Mapa OpenStreetMap integrado dentro de la app (no abre el navegador).
- Marcadores de jugadores y partidos que tengan coordenadas guardadas.
- Solicitud de permiso de ubicación y guardado de latitud/longitud aproximadas desde Perfil.
- Publicación de partidos en Firestore con fecha y hora mediante selectores nativos de calendario y reloj.
- Listado de partidos en tiempo real y postulaciones guardadas en Firestore.
- Selección de jugadores e invitaciones por partido; pantalla para responder invitaciones.
- Pantalla para revisar y aceptar/rechazar postulaciones.
- Confirmación transaccional de jugadores: al aceptar una postulación o invitación se crea el registro del plantel y se descuenta una vacante sin permitir duplicados ni superar los cupos.
- Plantel confirmado visible en cada partido y filtro de jugadores ya confirmados en otros partidos abiertos.
- Formularios desplazables, ajuste del teclado y opciones horizontales adaptables a pantallas angostas.
- Ícono de aplicación con cancha y pelota.
- Reglas Firestore para usuarios, partidos, postulaciones, invitaciones y plantel.
- WhatsApp CodeMarket: +54 9 261 511-7220.

## IMPORTANTE: publicar las reglas de Firestore
Subir `firestore.rules` a GitHub **no cambia automáticamente** las reglas activas del proyecto Firebase. Para que crear partidos, postularse e invitar jugadores funcione, hay que abrir Firebase Console → Firestore Database → Rules, reemplazar el contenido por `firestore.rules` y presionar **Publicar**. No cambies las reglas de Authentication.

## Mapa y privacidad
El mapa usa OpenStreetMap sin Google Maps ni facturación de Google. El usuario debe entrar en Perfil y tocar “Actualizar mi ubicación en el mapa”, aceptar el permiso y tener activada la ubicación del teléfono. Se guarda latitud/longitud del teléfono para mostrar un marcador; la interfaz pide además una zona/barrio aproximado. No se debe usar una dirección particular como zona pública.

Los marcadores solo pueden aparecer para cuentas que hayan guardado coordenadas. Los perfiles antiguos no tienen ubicación automáticamente: cada usuario debe actualizarla. Los mapas dependen de conexión a Internet para cargar las teselas de OpenStreetMap.

## Compilación
GitHub Actions ejecuta `gradle assembleDebug` y publica el artefacto `app-debug.apk`. La versión usa `applicationId` `com.futbolcerca.app`, `versionCode` 3 y `versionName` 2.1.0.

## Cambio importante de cupos
El campo `playersNeeded` representa ahora la capacidad total de jugadores que se buscan para el partido. `confirmedCount` guarda cuántos ya fueron aceptados y la app calcula las vacantes como `playersNeeded - confirmedCount`. Los partidos existentes sin `confirmedCount` se leen inicialmente como cero; sus aceptaciones históricas no se migran automáticamente al nuevo plantel.

## Límites y verificación pendiente
Este código se preparó para corregir los problemas reportados, pero **no fue compilado ni probado contra el Firebase real en este entorno**. Hay que comprobar el resultado de GitHub Actions y luego probar registro, publicación, mapa, postulación e invitaciones con dos cuentas distintas. La prueba de 7 días y el sistema de licencias siguen sin validación segura ni panel de administración; no se presentan como funciones terminadas. No incluye almacenamiento de fotos/videos ni notificaciones push.

No se necesita Firebase Blaze, Google Maps billing, Cloud Functions ni un servidor pago para estas funciones básicas.
