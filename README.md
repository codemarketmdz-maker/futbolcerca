# Fútbol Cerca 2.0

Proyecto Android Kotlin + Jetpack Compose + Firebase Authentication + Cloud Firestore.

## Configuración necesaria
1. Conservar `app/google-services.json` del proyecto Firebase existente.
2. En Firebase Authentication, habilitar correo/contraseña.
3. Crear Cloud Firestore.
4. Publicar el contenido de `firestore.rules` en Firestore > Rules.
5. Subir el contenido del ZIP a la rama `version-2-funcional` (reemplazando los archivos del proyecto).
6. Ejecutar GitHub Actions > Compilar Fútbol Cerca APK.

## Funciones incluidas en esta base
- Registro e inicio de sesión con Firebase.
- Roles Jugador, Capitán y Cancha.
- Perfil deportivo guardado en Firestore.
- Publicación y listado en tiempo real de partidos.
- Postulaciones persistentes por jugador y partido.
- Revisión y aceptación/rechazo de postulaciones por el organizador.
- Búsqueda de perfiles de jugadores para capitanes.
- Acceso a OpenStreetMap mediante búsqueda de zona aproximada.
- Contacto de licencias con CodeMarket por WhatsApp: +54 9 261 511-7220.
- Número de versión Android 2.0.0.

## Límites y seguridad
Esta entrega no incorpora GPS en segundo plano, fotos/videos, notificaciones push, pagos automáticos ni servidor propio. OpenStreetMap se abre en el navegador; no es un mapa embebido ni calcula distancias. Las cuentas de capitán/cancha quedan pendientes de verificación, pero esta versión no implementa revisión documental.

La prueba de 7 días se muestra como condición comercial, pero todavía no existe un sistema antifraude completo ni un panel de emisión/validación de licencias. No se debe presentar esa protección como implementada. Los límites gratuitos de Firebase aplican; monitorear el consumo.

Las reglas permiten leer perfiles autenticados para búsqueda de jugadores; no publicar datos sensibles ni direcciones exactas. Esta base requiere pruebas funcionales reales en Firebase antes de distribuirse.
