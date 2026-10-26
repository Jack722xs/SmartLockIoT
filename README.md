# SmartLockIoT (Proyecto 03 - Smart Lock RFID)

## Puesta en marcha
1. Abrir la carpeta en Android Studio (Koala+ / JDK 17). Aceptar la creacion del Gradle wrapper.
2. Firebase Console: crear proyecto, registrar app `com.example.smartlockiot`, descargar `google-services.json` y REEMPLAZAR `app/google-services.json` (el incluido es un placeholder).
3. Habilitar Authentication (correo/contrasena) y Firestore. Pegar `firestore.rules`.
4. Crear usuario admin en Authentication y un documento `admins/{uid}` (cualquier campo) en Firestore. (Desactivar con `Constants.REQUIRE_ADMIN_DOC = false`).

## Modelo de datos Firestore
- `access_logs/{auto}`: timestamp (Timestamp), userId (String UID RFID), status ("SUCCESS"|"DENIED"), statusColor ("GREEN"|"RED" o hex).
- `authorized_uids/{UID}`: uid, active, addedBy, addedAt. El Arduino R4 sincroniza esta lista a su cache local.
- `door/state`: locked (Boolean), updatedBy, updatedAt, pendingCommand ("lock"|"unlock"). Luz roja = locked true; luz verde = locked false.

## Canales de comando
- Nube: documento `door/state` (ambos celulares admin se sincronizan en tiempo real; el Arduino lo escucha/consulta).
- HTTPS POST: `{"action":"unlock|lock","source":"android_admin"}` con `Authorization: Bearer <token>` (config cifrada con EncryptedSharedPreferences).
- BLE: servicio `19B10000-E8F2-537E-4F6C-D104768A1214`, caracteristica `19B10001-E8F2-537E-4F6C-D104768A1214`, escribe "UNLOCK" / "LOCK".

## Notificaciones
Cada nuevo documento en `access_logs` genera notificacion local: "Apertura exitosa" / "Apertura no exitosa".
