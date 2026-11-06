package cl.conatacoch.colectigo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

const val CANAL_PASAJERO = "pasajero-dirigencia"
const val CANAL_CONDUCTOR = "conductor-dirigencia"
const val TARIFA_DEMO = 1200

data class Parada(val id: Int, val nombre: String, val pos: LatLng)
data class VehiculoBase(
    val id: Int,
    val patente: String,
    val marca: String,
    val modelo: String,
    val capacidad: Int,
    val asientos: Int,
    val comuna: String = "Iquique",
    val permisoCirculacionVenc: String = "2026-12-31",
    val revisionTecnicaVenc: String = "2026-10-31",
    val plantaRevision: String = "SGS Iquique",
    val seguroObligatorioVenc: String = "2027-03-31"
)
data class ConductorInfo(
    val usuarioId: Int,
    val nombre: String,
    val rut: String,
    val licencia: String,
    val tipoLicencia: String,
    val vencimientoLicencia: String,
    val afiliado: Boolean,
    val vehiculoId: Int,
    val patente: String,
    val marca: String,
    val modelo: String,
    val capacidad: Int,
    val comuna: String = "Iquique",
    val permisoCirculacionVenc: String = "2026-12-31",
    val revisionTecnicaVenc: String = "2026-10-31",
    val plantaRevision: String = "SGS Iquique",
    val seguroObligatorioVenc: String = "2027-03-31"
) {
    fun diasHastaVencimientoLicencia(): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fechaVenc = format.parse(vencimientoLicencia) ?: return 365L
            val hoy = Date()
            val diff = fechaVenc.time - hoy.time
            TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            365L
        }
    }

    fun requiereAlertaLicencia(): Boolean = diasHastaVencimientoLicencia() in 0..30
}

data class ViajeItem(val id: Int, val fecha: String, val patente: String, val monto: Int, val estado: String, val estrellas: Int?)
data class ReservaItem(
    val id: Int,
    val pasajeroNombre: String,
    val vehiculoId: Int,
    val patente: String,
    val paradaNombre: String,
    val monto: Int,
    val estado: String,
    val fecha: String
)
data class MensajeItem(val id: Int, val emisor: String, val contenido: String, val fecha: String)
data class PagoItem(val id: Int, val monto: Int, val fecha: String, val haceMin: Int)
data class Ingresos(val hoy: Int, val semana: Int)

class ColectiDb(context: Context) : SQLiteOpenHelper(context, "colectigo.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usuarios(id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, correo TEXT UNIQUE, rol TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE conductores(
            usuario_id INTEGER PRIMARY KEY REFERENCES usuarios(id) ON DELETE RESTRICT,
            rut TEXT NOT NULL UNIQUE,
            licencia TEXT NOT NULL,
            tipo_licencia TEXT NOT NULL DEFAULT 'A2',
            vencimiento_licencia TEXT NOT NULL DEFAULT '2026-11-15',
            afiliado INTEGER NOT NULL DEFAULT 0,
            validado INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE vehiculos(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            patente TEXT NOT NULL UNIQUE,
            marca TEXT NOT NULL,
            modelo TEXT NOT NULL,
            capacidad INTEGER NOT NULL,
            comuna TEXT NOT NULL DEFAULT 'Iquique',
            permiso_circulacion_venc TEXT NOT NULL DEFAULT '2026-12-31',
            revision_tecnica_venc TEXT NOT NULL DEFAULT '2026-10-31',
            planta_revision TEXT NOT NULL DEFAULT 'SGS Iquique',
            seguro_obligatorio_venc TEXT NOT NULL DEFAULT '2027-03-31',
            conductor_id INTEGER REFERENCES conductores(usuario_id) ON DELETE RESTRICT,
            estado TEXT NOT NULL DEFAULT 'ACTIVO',
            asientos_disponibles INTEGER NOT NULL)""")
        db.execSQL("CREATE TABLE recorridos(id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, comuna TEXT NOT NULL DEFAULT 'Iquique')")
        db.execSQL("""CREATE TABLE paraderos(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recorrido_id INTEGER NOT NULL REFERENCES recorridos(id) ON DELETE CASCADE,
            nombre TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, orden INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE viajes(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recorrido_id INTEGER NOT NULL REFERENCES recorridos(id) ON DELETE RESTRICT,
            vehiculo_id INTEGER NOT NULL REFERENCES vehiculos(id) ON DELETE RESTRICT,
            pasajero_local INTEGER NOT NULL DEFAULT 0,
            fecha TEXT NOT NULL, estado TEXT NOT NULL, monto INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE reservas(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pasajero_nombre TEXT NOT NULL,
            vehiculo_id INTEGER NOT NULL REFERENCES vehiculos(id) ON DELETE RESTRICT,
            parada_nombre TEXT NOT NULL,
            monto INTEGER NOT NULL,
            estado TEXT NOT NULL DEFAULT 'PENDIENTE',
            fecha TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE pagos(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            viaje_id INTEGER NOT NULL UNIQUE REFERENCES viajes(id) ON DELETE RESTRICT,
            monto INTEGER NOT NULL, fecha TEXT NOT NULL, estado TEXT NOT NULL, metodo TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE calificaciones(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            viaje_id INTEGER NOT NULL UNIQUE REFERENCES viajes(id) ON DELETE RESTRICT,
            estrellas INTEGER NOT NULL CHECK (estrellas BETWEEN 1 AND 5))""")
        db.execSQL("""CREATE TABLE alertas(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            conductor_id INTEGER NOT NULL REFERENCES conductores(usuario_id) ON DELETE RESTRICT,
            fecha TEXT NOT NULL, lat REAL, lng REAL, estado TEXT NOT NULL DEFAULT 'ENVIADA')""")
        db.execSQL("""CREATE TABLE mensajes(
            id INTEGER PRIMARY KEY AUTOINCREMENT, canal TEXT NOT NULL, emisor TEXT NOT NULL,
            contenido TEXT NOT NULL, fecha TEXT NOT NULL, sincronizado INTEGER NOT NULL DEFAULT 1)""")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        listOf("reservas", "mensajes", "alertas", "calificaciones", "pagos", "viajes", "paraderos",
            "recorridos", "vehiculos", "conductores", "usuarios").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        db.execSQL("""INSERT INTO usuarios(id,nombre,correo,rol) VALUES
            (1,'Pedro Muñoz','pedro.munoz@colectigo.cl','CONDUCTOR'),
            (2,'Luis Soto','luis.soto@colectigo.cl','CONDUCTOR'),
            (3,'Ana Rojas','ana.rojas@colectigo.cl','CONDUCTOR'),
            (4,'Administrador Dirigencia','admin@conatacoch.cl','ADMIN')""")
        db.execSQL("""INSERT INTO conductores(usuario_id,rut,licencia,tipo_licencia,vencimiento_licencia,afiliado,validado) VALUES
            (1,'123456785','A2','A2','2026-11-05',1,1),
            (2,'111111111','A3','A3','2027-05-20',1,1),
            (3,'222222222','A2','A1','2026-10-28',1,1)""")
        db.execSQL("""INSERT INTO vehiculos(id,patente,marca,modelo,capacidad,comuna,permiso_circulacion_venc,revision_tecnica_venc,planta_revision,seguro_obligatorio_venc,conductor_id,estado,asientos_disponibles) VALUES
            (1,'ABCD12','Hyundai','Accent',4,'Iquique','2026-12-31','2026-10-31','SGS Iquique','2027-03-31',1,'ACTIVO',3),
            (2,'EFGH34','Chevrolet','Sail',4,'Alto Hospicio','2026-12-31','2026-11-15','PRT Hospicio','2027-03-31',2,'ACTIVO',0),
            (3,'IJKL56','Kia','Rio 4',4,'Antofagasta','2026-12-31','2026-10-25','Denso Antofagasta','2027-03-31',3,'ACTIVO',2)""")
        
        db.execSQL("INSERT INTO recorridos(id,nombre,comuna) VALUES (1,'Línea 1: Terminal Hospicio — Iquique Centro','Iquique')")
        db.execSQL("INSERT INTO recorridos(id,nombre,comuna) VALUES (2,'Línea 4: Antofagasta Norte — Centro','Antofagasta')")
        db.execSQL("INSERT INTO recorridos(id,nombre,comuna) VALUES (3,'Línea 111: Valparaíso — Viña del Mar','Valparaíso')")
        db.execSQL("INSERT INTO recorridos(id,nombre,comuna) VALUES (4,'Línea 20: Santiago Centro — Providencia','Santiago')")

        db.execSQL("""INSERT INTO paraderos(recorrido_id,nombre,lat,lng,orden) VALUES
            (1,'Terminal Alto Hospicio',-20.2721,-70.1030,0),
            (1,'Rotonda El Salitre',-20.2450,-70.1280,1),
            (1,'Av. Héroes de la Concepción',-20.2280,-70.1410,2),
            (1,'Plaza de Armas Iquique',-20.2139,-70.1506,3)""")

        db.execSQL("""INSERT INTO reservas(pasajero_nombre,vehiculo_id,parada_nombre,monto,estado,fecha) VALUES
            ('María González',1,'Av. Héroes de la Concepción',1200,'PENDIENTE','2026-10-19 14:30')""")
    }

    fun vehiculosBase(): List<VehiculoBase> {
        val db = readableDatabase
        val sql = "SELECT id, patente, marca, modelo, capacidad, asientos_disponibles, comuna, permiso_circulacion_venc, revision_tecnica_venc, planta_revision, seguro_obligatorio_venc FROM vehiculos WHERE estado = 'ACTIVO'"
        val c = db.rawQuery(sql, null)
        val list = mutableListOf<VehiculoBase>()
        c.use {
            while (it.moveToNext()) {
                list.add(
                    VehiculoBase(
                        id = it.getInt(0), patente = it.getString(1), marca = it.getString(2),
                        modelo = it.getString(3), capacidad = it.getInt(4), asientos = it.getInt(5),
                        comuna = it.getString(6), permisoCirculacionVenc = it.getString(7),
                        revisionTecnicaVenc = it.getString(8), plantaRevision = it.getString(9),
                        seguroObligatorioVenc = it.getString(10)
                    )
                )
            }
        }
        return list
    }

    fun paraderos(): List<Parada> {
        val db = readableDatabase
        val sql = "SELECT id, nombre, lat, lng FROM paraderos WHERE recorrido_id = 1 ORDER BY orden ASC"
        val c = db.rawQuery(sql, null)
        val list = mutableListOf<Parada>()
        c.use {
            while (it.moveToNext()) {
                list.add(Parada(it.getInt(0), it.getString(1), LatLng(it.getDouble(2), it.getDouble(3))))
            }
        }
        return list
    }

    fun nombreRecorrido(): String {
        val db = readableDatabase
        val c = db.rawQuery("SELECT nombre FROM recorridos WHERE id = 1", null)
        return c.use { if (it.moveToFirst()) it.getString(0) else "Línea 1: Terminal Hospicio — Iquique Centro" }
    }

    fun actualizarAsientos(vehiculoId: Int, asientos: Int) {
        val db = writableDatabase
        db.execSQL("UPDATE vehiculos SET asientos_disponibles = ? WHERE id = ?", arrayOf(asientos, vehiculoId))
    }

    fun loginConductor(rutNorm: String, patenteNorm: String): ConductorInfo? {
        val db = readableDatabase
        val sql = """SELECT c.usuario_id, u.nombre, c.rut, c.licencia, c.tipo_licencia, c.vencimiento_licencia,
            c.afiliado, v.id, v.patente, v.marca, v.modelo, v.capacidad, v.comuna, v.permiso_circulacion_venc,
            v.revision_tecnica_venc, v.planta_revision, v.seguro_obligatorio_venc
            FROM conductores c
            JOIN usuarios u ON u.id = c.usuario_id
            JOIN vehiculos v ON v.conductor_id = c.usuario_id
            WHERE c.rut = ? AND REPLACE(v.patente, '-', '') = ? AND c.validado = 1"""
        val c = db.rawQuery(sql, arrayOf(rutNorm, patenteNorm.replace("-", "")))
        return c.use {
            if (it.moveToFirst()) {
                ConductorInfo(
                    usuarioId = it.getInt(0), nombre = it.getString(1), rut = it.getString(2),
                    licencia = it.getString(3), tipoLicencia = it.getString(4), vencimientoLicencia = it.getString(5),
                    afiliado = it.getInt(6) == 1, vehiculoId = it.getInt(7), patente = it.getString(8),
                    marca = it.getString(9), modelo = it.getString(10), capacidad = it.getInt(11),
                    comuna = it.getString(12), permisoCirculacionVenc = it.getString(13),
                    revisionTecnicaVenc = it.getString(14), plantaRevision = it.getString(15),
                    seguroObligatorioVenc = it.getString(16)
                )
            } else null
        }
    }

    fun crearReserva(pasajeroNombre: String, vehiculoId: Int, paradaNombre: String, monto: Int) {
        val db = writableDatabase
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        db.execSQL(
            "INSERT INTO reservas(pasajero_nombre, vehiculo_id, parada_nombre, monto, estado, fecha) VALUES (?, ?, ?, ?, 'PENDIENTE', ?)",
            arrayOf(pasajeroNombre, vehiculoId, paradaNombre, monto, fecha)
        )
    }

    fun reservasPendientes(vehiculoId: Int): List<ReservaItem> {
        val db = readableDatabase
        val sql = """SELECT r.id, r.pasajero_nombre, r.vehiculo_id, v.patente, r.parada_nombre, r.monto, r.estado, r.fecha
            FROM reservas r JOIN vehiculos v ON v.id = r.vehiculo_id
            WHERE r.vehiculo_id = ? AND r.estado = 'PENDIENTE' ORDER BY r.id DESC"""
        val c = db.rawQuery(sql, arrayOf(vehiculoId.toString()))
        val res = mutableListOf<ReservaItem>()
        c.use {
            while (it.moveToNext()) {
                res.add(ReservaItem(it.getInt(0), it.getString(1), it.getInt(2), it.getString(3), it.getString(4), it.getInt(5), it.getString(6), it.getString(7)))
            }
        }
        return res
    }

    fun responderReserva(reservaId: Int, aceptar: Boolean, vehiculoId: Int) {
        val db = writableDatabase
        val nuevoEstado = if (aceptar) "ACEPTADA" else "RECHAZADA"
        db.execSQL("UPDATE reservas SET estado = ? WHERE id = ?", arrayOf(nuevoEstado, reservaId))
        if (aceptar) {
            db.execSQL("UPDATE vehiculos SET asientos_disponibles = MAX(0, asientos_disponibles - 1) WHERE id = ?", arrayOf(vehiculoId))
        }
    }

    fun adminConductores(): List<ConductorInfo> {
        val db = readableDatabase
        val sql = """SELECT c.usuario_id, u.nombre, c.rut, c.licencia, c.tipo_licencia, c.vencimiento_licencia,
            c.afiliado, v.id, v.patente, v.marca, v.modelo, v.capacidad, v.comuna, v.permiso_circulacion_venc,
            v.revision_tecnica_venc, v.planta_revision, v.seguro_obligatorio_venc
            FROM conductores c
            JOIN usuarios u ON u.id = c.usuario_id
            JOIN vehiculos v ON v.conductor_id = c.usuario_id"""
        val c = db.rawQuery(sql, null)
        val list = mutableListOf<ConductorInfo>()
        c.use {
            while (it.moveToNext()) {
                list.add(
                    ConductorInfo(
                        usuarioId = it.getInt(0), nombre = it.getString(1), rut = it.getString(2),
                        licencia = it.getString(3), tipoLicencia = it.getString(4), vencimientoLicencia = it.getString(5),
                        afiliado = it.getInt(6) == 1, vehiculoId = it.getInt(7), patente = it.getString(8),
                        marca = it.getString(9), modelo = it.getString(10), capacidad = it.getInt(11),
                        comuna = it.getString(12), permisoCirculacionVenc = it.getString(13),
                        revisionTecnicaVenc = it.getString(14), plantaRevision = it.getString(15),
                        seguroObligatorioVenc = it.getString(16)
                    )
                )
            }
        }
        return list
    }

    fun adminCrearConductor(nombre: String, rut: String, licencia: String, tipoLicencia: String, vencimiento: String, patente: String, marca: String, modelo: String, capacidad: Int, comuna: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT INTO usuarios(nombre, correo, rol) VALUES (?, ?, 'CONDUCTOR')", arrayOf(nombre, "$rut@colectigo.cl"))
            val cur = db.rawQuery("SELECT last_insert_rowid()", null)
            var userId = 0
            cur.use { if (it.moveToFirst()) userId = it.getInt(0) }
            db.execSQL(
                "INSERT INTO conductores(usuario_id, rut, licencia, tipo_licencia, vencimiento_licencia, afiliado, validado) VALUES (?, ?, ?, ?, ?, 1, 1)",
                arrayOf(userId, rut, licencia, tipoLicencia, vencimiento)
            )
            db.execSQL(
                "INSERT INTO vehiculos(patente, marca, modelo, capacidad, comuna, conductor_id, estado, asientos_disponibles) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVO', ?)",
                arrayOf(patente, marca, modelo, capacidad, comuna, userId, capacidad)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun adminEliminarConductor(usuarioId: Int) {
        val db = writableDatabase
        db.execSQL("DELETE FROM vehiculos WHERE conductor_id = ?", arrayOf(usuarioId))
        db.execSQL("DELETE FROM conductores WHERE usuario_id = ?", arrayOf(usuarioId))
        db.execSQL("DELETE FROM usuarios WHERE id = ?", arrayOf(usuarioId))
    }

    fun pagosConductor(vehiculoId: Int, limite: Int = 50): List<PagoItem> {
        val list = mutableListOf<PagoItem>()
        list.add(PagoItem(1, 1200, "19-10-2026 13:10", 12))
        list.add(PagoItem(2, 1200, "19-10-2026 12:45", 37))
        return list
    }

    fun ingresos(vehiculoId: Int): Ingresos = Ingresos(hoy = 14400, semana = 86400)

    fun registrarAlerta(conductorId: Int, lat: Double, lng: Double) {
        val db = writableDatabase
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        db.execSQL("INSERT INTO alertas(conductor_id, fecha, lat, lng, estado) VALUES (?, ?, ?, ?, 'ENVIADA')", arrayOf(conductorId, fecha, lat, lng))
    }

    fun viajesPasajero(): List<ViajeItem> {
        val list = mutableListOf<ViajeItem>()
        list.add(ViajeItem(1, "19-10-2026 12:30", "ABCD12", 1200, "COMPLETADO", 5))
        return list
    }

    fun crearViaje(vehiculoId: Int, monto: Int) {
        val db = writableDatabase
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        db.execSQL("INSERT INTO viajes(recorrido_id, vehiculo_id, pasajero_local, fecha, estado, monto) VALUES (1, ?, 1, ?, 'SOLICITADO', ?)", arrayOf(vehiculoId, fecha, monto))
    }

    fun mensajes(canal: String): List<MensajeItem> {
        val db = readableDatabase
        val c = db.rawQuery("SELECT id, emisor, contenido, fecha FROM mensajes WHERE canal = ? ORDER BY id ASC", arrayOf(canal))
        val res = mutableListOf<MensajeItem>()
        c.use {
            while (it.moveToNext()) {
                res.add(MensajeItem(it.getInt(0), it.getString(1), it.getString(2), it.getString(3)))
            }
        }
        if (res.isEmpty()) {
            res.add(MensajeItem(1, "Dirigencia CONATACOCH", "Bienvenido a ColectiGo. Canal activo.", "19-10-2026 10:00"))
        }
        return res
    }

    fun enviarMensaje(canal: String, emisor: String, contenido: String) {
        val db = writableDatabase
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        db.execSQL("INSERT INTO mensajes(canal, emisor, contenido, fecha) VALUES (?, ?, ?, ?)", arrayOf(canal, emisor, contenido, fecha))
    }
}
