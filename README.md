# Fútbol Cerca – Android

## Firebase conectado
- Proyecto Firebase: `futbolcerca-e94f0`
- Paquete Android: `com.futbolcerca.app`
- Firebase Authentication (correo/contraseña) y Cloud Firestore.
- `google-services.json` está en `app/`.

## Antes de probar
1. En Firebase Console > Authentication > Sign-in method, habilitá **Correo electrónico/Contraseña**.
2. En Firestore Database, creá la base de datos.
3. Publicá las reglas de `firestore.rules` desde la sección Rules de Firestore.
4. Compilá con GitHub Actions.

## Registro y roles
El usuario elige Jugador, Capitán o Dueño/administrador de cancha al crear la cuenta. El rol queda guardado en `users/{uid}` y no se ofrece un selector para cambiar de rol en la pantalla principal. Las cuentas de capitán y cancha quedan con `verificationStatus: pending`; esta versión no implementa todavía el proceso de verificación documental.

## Limitaciones de esta entrega
Los partidos y postulaciones del panel siguen siendo datos de demostración. La autenticación y el guardado del tipo de cuenta sí usan Firebase. No publicar la app en producción hasta completar reglas específicas para partidos, solicitudes, perfiles públicos, almacenamiento y moderación.
