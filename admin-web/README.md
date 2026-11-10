# ColectiGo — panel administrativo

Panel web de ColectiGo para gestionar viajes, enviar avisos y revisar los participantes. Esta aplicación vive en `admin-web/`, separada del código Android.

## Ejecutar para la demo

Requiere Node.js 20 o superior.

Abre una terminal en `admin-web/` y ejecuta:

```bash
npm install
npm run dev
```

Sin variables de Firebase, el panel inicia en modo demostración. Incluye viajes de ejemplo y guarda altas, ediciones, cambios de estado y eliminaciones en el almacenamiento local del navegador. La demo no comparte esos datos con el teléfono ni con otros dispositivos.

## Conectar con Firebase

1. En Firebase Console, registra una aplicación web en el proyecto que usa la app móvil y habilita **Authentication > Correo electrónico/contraseña**.
2. Copia `admin-web/.env.example` como `admin-web/.env.local` y completa los valores de configuración web de Firebase. Son los valores de configuración de cliente; no agregues una clave de cuenta de servicio.
3. En Authentication, crea el usuario administrador que usará el panel.
4. Crea/usa la colección de Firestore `reservas`. Para la demo móvil también debes habilitar **Authentication > Anónimo**; para el panel, **Correo electrónico/contraseña**.
5. Reinicia `npm run dev`. Para volver a la demo local, cambia `VITE_DEMO_MODE=true`.

El panel y la app Android comparten `reservas`. El pasajero crea una solicitud, el conductor acepta/rechaza desde una escucha en tiempo real, y el panel administrativo puede ver y actualizar el mismo documento.

La sección **Mensajes** publica avisos para conductores, pasajeros o ambos en la colección `mensajes`; la app móvil muestra esos avisos en su pestaña Mensajes y puede enviar mensajes a la administración, que aparecen en la bandeja del panel. No son conversaciones privadas persona a persona. La app actual usa una sesión Firebase anónima para pasajero/conductor de demostración.

Las secciones **Conductores** y **Pasajeros** muestran los nombres y actividad que aparecen en las reservas. No administran cuentas ni perfiles: Android todavía guarda esos perfiles localmente y no existe una colección compartida de personas.

El panel y la app Android comparten `reservas`. Los documentos usan estos campos:

| Campo | Tipo | Ejemplo |
| --- | --- | --- |
| `passengerName` | string | `Valentina Rojas` |
| `passengerUid` | string | UID anónimo de Firebase de la instalación móvil |
| `driverName` | string | `Tomás Muñoz` o `Por asignar` |
| `vehicleId` | number | `1` (ID local del vehículo que usa la demo Android) |
| `patente` | string | `ABCD12` |
| `origin` | string | Nombre del recorrido |
| `destination` / `paradaNombre` | string | Nombre del paradero seleccionado |
| `monto` | number | `1200` |
| `estado` | string | `PENDIENTE`, `ACEPTADA`, `RECHAZADA`, `EN_CURSO`, `COMPLETADA`, `CANCELADA` |
| `fecha` / `createdAt` | string ISO 8601 | `2026-11-09T18:30:00.000Z` |

Al iniciar, Android usa autenticación anónima para el pasajero y conductor de demostración. La sesión identifica las solicitudes de esa instalación; no reemplaza cuentas reales ni separa los permisos de conductor y pasajero. La aplicación conserva SQLite para el resto de sus pantallas.

### Reglas de Firestore para la demostración

El archivo `../firestore.rules` en la raíz del repositorio incluye reglas para `reservas` y `mensajes` y usa el correo administrador configurado. Publícalas en **Firestore Database > Rules**. Si ya hay reglas para otras colecciones, consérvalas. Las reglas de ejemplo vencen el 13 de noviembre de 2026 (UTC) y solo son para la demo con datos ficticios; no las uses con información real. Para producción, reemplázalas por reglas con roles confiables y autenticación individual para pasajero y conductor. Nunca uses `allow read, write: if true`.

La configuración web se obtiene registrando una aplicación web en el mismo proyecto Firebase; `google-services.json` es para Android y no sustituye la configuración web. Firestore crea automáticamente `reservas` y `mensajes` al guardar sus primeros documentos.
