# ColectiGo — panel administrativo

Panel web de ColectiGo para gestionar reservas, enviar avisos y revisar los participantes. Está dentro de `admin-web/`, separado de la aplicación Android.

## 1. Requisitos

- Node.js 20 o superior (incluye npm).
- Acceso al proyecto Firebase `colectigo-6ad5c`.
- Para probar sincronización móvil: Android Studio y un emulador/teléfono con la app de ColectiGo.

## 2. Obtener el proyecto y abrir la carpeta correcta

Si todavía no tienes el repositorio:

```powershell
git clone --branch admin-web-panel https://github.com/Annsix16/ColectiGo.git
cd ColectiGo\admin-web
```

Si ya abriste el repositorio en VS Code, abre una terminal en la carpeta `admin-web`. Comprueba que están `package.json`, `src/` y este `README.md` antes de continuar.

## 3. Instalar dependencias y ejecutar en modo local

Desde `admin-web/`:

```powershell
npm ci
npm run dev
```

Abre en el navegador la dirección local que muestra Vite, normalmente `http://localhost:5173`. Sin credenciales de Firebase configuradas, el panel usa datos de demostración guardados únicamente en ese navegador. **Ese modo local no se sincroniza con el teléfono ni con otros dispositivos.**

Para comprobar la compilación de producción:

```powershell
npm run build
```

## 4. Registrar la aplicación web en Firebase

1. En Firebase Console, selecciona el proyecto **ColectiGo** (`colectigo-6ad5c`).
2. Abre **Configuración del proyecto → General → Tus aplicaciones**.
3. Si no existe una app web, agrégala con el icono `</>` y un nombre, por ejemplo `ColectiGo Admin`.
4. En **Configuración del SDK**, selecciona **npm** y copia el objeto `firebaseConfig`.
5. No uses `google-services.json` como configuración web. Ese archivo es solo para Android.

La configuración web de Firebase es de cliente; **no copies claves privadas de cuentas de servicio** ni contraseñas a este proyecto.

## 5. Crear la configuración local del panel

Copia la plantilla y edita el archivo nuevo. En PowerShell:

```powershell
Copy-Item .env.example .env.local
notepad .env.local
```

Completa cada valor con el correspondiente del objeto `firebaseConfig`:

```dotenv
VITE_FIREBASE_API_KEY=valor_de_apiKey
VITE_FIREBASE_AUTH_DOMAIN=valor_de_authDomain
VITE_FIREBASE_PROJECT_ID=colectigo-6ad5c
VITE_FIREBASE_STORAGE_BUCKET=valor_de_storageBucket
VITE_FIREBASE_MESSAGING_SENDER_ID=valor_de_messagingSenderId
VITE_FIREBASE_APP_ID=valor_de_appId
VITE_DEMO_MODE=false
```

No escribas comillas ni comas. Guarda `.env.local` dentro de `admin-web/`. Está excluido de Git; no lo subas a GitHub ni compartas capturas que muestren contraseñas.

## 6. Habilitar autenticación

En Firebase Console:

1. Abre **Authentication → Método de acceso**.
2. Habilita **Correo electrónico/contraseña** para iniciar sesión al panel.
3. Habilita **Anónimo** para la identidad de demostración de la app móvil.
4. En **Authentication → Usuarios**, crea el usuario administrador con correo y contraseña. Usa el correo exacto que permiten las reglas de Firestore.

La regla incluida en `../firestore.rules` actualmente reconoce `admin@admin123.cl`. Si usas otro correo, reemplaza el correo en la función `adminUser()` antes de publicar las reglas.

## 7. Publicar las reglas de Firestore

1. Abre **Firestore Database → Reglas** en el mismo proyecto Firebase.
2. Conserva cualquier regla existente para otras colecciones.
3. Integra los bloques para `reservas`, `mensajes` y `vehiculos` desde `../firestore.rules`. Si esa base de datos aún no tiene reglas ni colecciones necesarias, puedes publicar el archivo completo.
4. Revisa el correo de `adminUser()` y pulsa **Publicar**.

Las reglas de este repositorio son únicamente para una **demo con datos ficticios** y dejan de permitir acceso después del 13 de noviembre de 2026 (UTC). No las uses en producción. Nunca abras la base con `allow read, write: if true`. Para una aplicación real se necesitan cuentas/roles confiables y reglas revisadas para cada rol.

Firestore crea automáticamente las colecciones al guardar el primer documento. No hace falta crear `reservas` ni `mensajes` manualmente.

## 8. Iniciar el panel conectado a Firebase

Detén cualquier instancia anterior de Vite y vuelve a iniciar para que lea `.env.local`:

```powershell
npm run dev
```

Inicia sesión con el usuario administrador de Firebase. El panel tiene estas secciones:

- **Viajes:** lista, búsqueda, filtro, cambios de estado y edición/eliminación de reservas.
- **Mensajes:** avisos para conductores, pasajeros o ambos; bandeja de mensajes que llegan desde la app.
- **Conductores y Pasajeros:** directorios construidos a partir de las reservas. Son de consulta; todavía no administran perfiles/cuentas.

Los mensajes son avisos grupales, no conversaciones privadas entre dos usuarios.

## 9. Probar la sincronización con Android

1. Compila e instala en un emulador o teléfono la versión Android que incluye Firebase y la integración de `reservas`/`mensajes`. Si estás trabajando desde Android Studio, sincroniza Gradle y ejecuta la variante `debug`.
2. Mantén el panel abierto e inicia sesión como administrador.
3. En Android, abre **Mensajes** y envía un mensaje: debe aparecer en la bandeja del panel.
4. Desde el panel, envía un mensaje a **Pasajeros**, **Conductores** o **Conductores y pasajeros**: debe aparecer en la pestaña **Mensajes** correspondiente del móvil.
5. Crea una reserva desde el flujo de pasajero. Comprueba que aparezca en el panel y que el conductor pueda aceptarla/rechazarla.

Espera unos segundos después de publicar las reglas y confirma que el panel y el móvil usan el mismo `projectId`.

## Solución de problemas

### `auth/api-key-not-valid`

- Confirma que `VITE_FIREBASE_API_KEY` pertenece a la app **web** del proyecto `colectigo-6ad5c`; no uses la clave de Android.
- Revisa `authDomain`, `projectId` y `appId`.
- Reinicia `npm run dev` después de editar `.env.local`.
- Si restringiste la API key en Google Cloud, permite el origen local del panel, como `localhost:5173`.

### `permission-denied` / `Missing or insufficient permissions`

- Confirma que publicaste reglas que incluyan `reservas` y `mensajes`.
- Verifica que Authentication Anónimo esté habilitado para Android.
- Verifica que el panel esté autenticado y que su correo coincida con `adminUser()` en las reglas.
- Revisa la fecha límite temporal de las reglas.

### El mensaje o la reserva no aparecen en otro dispositivo

- Si el panel muestra **Modo demostración**, revisa `VITE_DEMO_MODE=false` y reinicia Vite.
- Confirma que ambos clientes usan el mismo proyecto Firebase.
- Los datos de demo en `localStorage` no se sincronizan con Firestore.
- Solo se sincronizan los módulos implementados en Firestore; otros datos de la app todavía pueden permanecer locales.

## Estructura de datos compartida

### `reservas`

La app móvil y el panel intercambian reservas en esta colección. Los estados que escribe Android son `PENDIENTE`, `ACEPTADA` y `RECHAZADA`; el panel también reconoce los demás estados definidos en el modelo.

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `passengerName` | string | Nombre mostrado del pasajero |
| `passengerUid` | string | UID anónimo de Firebase de la instalación |
| `driverName` | string | Conductor mostrado o `Por asignar` |
| `vehicleId` | number | ID local del vehículo de la demo Android |
| `patente` | string | Patente del vehículo |
| `origin` | string | Nombre del recorrido |
| `destination`, `paradaNombre` | string | Paradero solicitado |
| `monto` | number | Tarifa |
| `estado` | string | Estado de la reserva |
| `fecha`, `createdAt` | string ISO 8601 | Fecha de creación |

### `mensajes`

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `audience` | string | `CONDUCTOR`, `PASAJERO`, `TODOS` o `ADMIN` |
| `content` | string | Texto del mensaje (máximo 500 caracteres) |
| `senderName` | string | Nombre mostrado del emisor |
| `senderRole` | string | `ADMIN`, `CONDUCTOR` o `PASAJERO` |
| `senderUid` | string | UID Firebase del emisor |
| `createdAt` | string o timestamp | Fecha de envío |
