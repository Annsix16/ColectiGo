package cl.conatacoch.colectigo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.android.gms.maps.model.LatLng

const val CANAL_PASAJERO = "pasajero-dirigencia"
const val CANAL_CONDUCTOR = "conductor-dirigencia"
const val TARIFA_DEMO = 1200

data class Parada(val id: Int, val nombre: String, val pos: LatLng)
data class VehiculoBase(val id: Int, val patente: String, val capacidad: Int, val asientos: Int)
data class ConductorInfo(
    val usuarioId: Int, val nombre: String, val rut: String, val licencia: String,
    val afiliado: Boolean, val vehiculoId: Int, val patente: String,
    val marca: String, val modelo: String, val capacidad: Int
)
data class ViajeItem(val id: Int, val fecha: String, val patente: String, val monto: Int, val estado: String, val estrellas: Int?)
data class MensajeItem(val id: Int, val emisor: String, val contenido: String, val fecha: String)
data class PagoItem(val id: Int, val monto: Int, val fecha: String, val haceMin: Int)
data class Ingresos(val hoy: Int, val semana: Int)

/**
 * Base de datos local (SQLite) con el modelo entidad-relación del informe (punto 8).
 * Es la "base lista para el futuro": hoy funciona 100% offline con datos de ejemplo y
 * mañana se puede reemplazar por Room o por llamadas a la API (PostgreSQL/MongoDB)
 * sin tocar las pantallas, porque la UI solo habla con los métodos de esta clase.
 *
 * Restricción de seguro (informe): los datos técnicos del vehículo (patente, marca,
 * modelo, capacidad) NO tienen ningún método de escritura. Solo se puede actualizar
 * el estado operativo (asientos disponibles).
 */
class ColectiDb(context: Context) : SQLiteOpenHelper(context, "colectigo.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usuarios(id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, correo TEXT UNIQUE, rol TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE conductores(
            usuario_id INTEGER PRIMARY KEY REFERENCES usuarios(id) ON DELETE RESTRICT,
            rut TEXT NOT NULL UNIQUE, licencia TEXT NOT NULL CHECK (licencia IN ('A2','A3')),
            afiliado INTEGER NOT NULL DEFAULT 0, validado INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE vehiculos(
            id INTEGER PRIMARY KEY AUTOINCREMENT, patente TEXT NOT NULL UNIQUE,
            marca TEXT NOT NULL, modelo TEXT NOT NULL, capacidad INTEGER NOT NULL,
            conductor_id INTEGER REFERENCES conductores(usuario_id) ON DELETE RESTRICT,
            estado TEXT NOT NULL DEFAULT 'ACTIVO', asientos_disponibles INTEGER NOT NULL)""")
        db.execSQL("CREATE TABLE recorridos(id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL)")
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
        // Prototipo: si cambia el esquema, se recrea. En producción usar migraciones.
        listOf("mensajes", "alertas", "calificaciones", "pagos", "viajes", "paraderos",
            "recorridos", "vehiculos", "conductores", "usuarios").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    /** Datos de EJEMPLO. Reemplazar por los reales de CONATACOCH. */
    private fun seed(db: SQLiteDatabase) {
        db.execSQL("""INSERT INTO usuarios(id,nombre,correo,rol) VALUES
            (1,'Pedro Muñoz','pedro.munoz@colectigo.cl','CONDUCTOR'),
            (2,'Luis Soto','luis.soto@colectigo.cl','CONDUCTOR'),
            (3,'Ana Rojas','ana.rojas@colectigo.cl','CONDUCTOR'),
            (4,'Dirigencia CONATACOCH','admin@conatacoch.cl','ADMIN')""")
        db.execSQL("""INSERT INTO conductores(usuario_id,rut,licencia,afiliado,validado) VALUES
            (1,'123456785','A2',1,1),(2,'111111111','A3',1,1),(3,'222222222','A2',1,1)""")
        db.execSQL("""INSERT INTO vehiculos(id,patente,marca,modelo,capacidad,conductor_id,estado,asientos_disponibles) VALUES
            (1,'ABCD12','Hyundai','Accent',4,1,'ACTIVO',3),
            (2,'EFGH34','Chevrolet','Sail',4,2,'ACTIVO',0),
            (3,'IJKL56','Kia','Rio 4',4,3,'ACTIVO',2)""")
        db.execSQL("INSERT INTO recorridos(id,nombre) VALUES (1,'Terminal — Centro')")
        listOf(
            Triple("Terminal", -33.4520, -70.6790),
            Triple("Paradero 2", -33.4492, -70.6700),
            Triple("Paradero 3", -33.4458, -70.6610),
            Triple("Paradero 4", -33.4430, -70.6540),
            Triple("Centro", -33.4411, -70.6440)
        ).forEachIndexed { i, (n, lat, lng) ->
            db.execSQL("INSERT INTO paraderos(recorrido_id,nombre,lat,lng,orden) VALUES (1,'$n',$lat,$lng,$i)")
        }
        // Viajes/pagos de ejemplo del vehículo 1 (ingresos del conductor)
        db.execSQL("""INSERT INTO viajes(id,recorrido_id,vehiculo_id,pasajero_local,fecha,estado,monto) VALUES
            (1,1,1,0,datetime('now','localtime','-2 minutes'),'COMPLETADO',1200),
            (2,1,1,0,datetime('now','localtime','-5 minutes'),'COMPLETADO',3600),
            (3,1,1,0,datetime('now','localtime','-8 minutes'),'COMPLETADO',4800),
            (4,1,1,0,datetime('now','localtime','-12 minutes'),'COMPLETADO',2400),
            (5,1,1,0,datetime('now','localtime','-15 minutes'),'COMPLETADO',6400),
            (6,1,1,0,datetime('now','localtime','-2 days'),'COMPLETADO',9600),
            (7,1,1,0,datetime('now','localtime','-3 days'),'COMPLETADO',8400),
            (8,1,1,0,datetime('now','localtime','-4 days'),'COMPLETADO',7000)""")
        // Historial de ejemplo del pasajero (este dispositivo)
        db.execSQL("""INSERT INTO viajes(id,recorrido_id,vehiculo_id,pasajero_local,fecha,estado,monto) VALUES
            (10,1,1,1,datetime('now','localtime','-1 days'),'COMPLETADO',1200),
            (11,1,3,1,datetime('now','localtime','-3 days'),'COMPLETADO',1200),
            (12,1,2,1,datetime('now','localtime','-5 days'),'COMPLETADO',1200)""")
        db.execSQL("""INSERT INTO pagos(viaje_id,monto,fecha,estado,metodo)
            SELECT id, monto, fecha, 'APROBADO', 'RUTPAY' FROM viajes""")
        db.execSQL("INSERT INTO calificaciones(viaje_id,estrellas) VALUES (10,5),(11,4),(12,5)")
        db.execSQL("""INSERT INTO mensajes(canal,emisor,contenido,fecha) VALUES
            ('$CANAL_CONDUCTOR','Dirigencia','Recordatorio: reunión mensual el viernes.',datetime('now','localtime','-1 days')),
            ('$CANAL_PASAJERO','Sistema','Bienvenido a ColectiGo: tu colectivo, en tiempo real.',datetime('now','localtime','-2 days')),
            ('$CANAL_PASAJERO','Dirigencia','Aviso: desvío temporal por trabajos en la Alameda.',datetime('now','localtime','-1 days'))""")
    }

    // ───────────── Recorridos y paraderos ─────────────
    fun nombreRecorrido(id: Int = 1): String =
        readableDatabase.rawQuery("SELECT nombre FROM recorridos WHERE id=?", arrayOf("$id")).use {
            if (it.moveToFirst()) it.getString(0) else "Recorrido"
        }

    fun paraderos(recorridoId: Int = 1): List<Parada> =
        readableDatabase.rawQuery(
            "SELECT id,nombre,lat,lng FROM paraderos WHERE recorrido_id=? ORDER BY orden", arrayOf("$recorridoId")
        ).use { c ->
            buildList { while (c.moveToNext()) add(Parada(c.getInt(0), c.getString(1), LatLng(c.getDouble(2), c.getDouble(3)))) }
        }

    // ───────────── Vehículos ─────────────
    fun vehiculosBase(): List<VehiculoBase> =
        readableDatabase.rawQuery(
            "SELECT id,patente,capacidad,asientos_disponibles FROM vehiculos WHERE estado='ACTIVO' ORDER BY id", null
        ).use { c ->
            buildList { while (c.moveToNext()) add(VehiculoBase(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3))) }
        }

    /** Único dato del vehículo que la app puede modificar (RF07). Nada técnico se edita. */
    fun actualizarAsientos(vehiculoId: Int, asientos: Int) {
        writableDatabase.execSQL("UPDATE vehiculos SET asientos_disponibles=? WHERE id=?", arrayOf<Any>(asientos, vehiculoId))
    }

    // ───────────── Conductor ─────────────
    fun loginConductor(rutNorm: String, patenteNorm: String): ConductorInfo? =
        readableDatabase.rawQuery(
            """SELECT u.id,u.nombre,c.rut,c.licencia,c.afiliado,v.id,v.patente,v.marca,v.modelo,v.capacidad
               FROM conductores c JOIN usuarios u ON u.id=c.usuario_id JOIN vehiculos v ON v.conductor_id=c.usuario_id
               WHERE c.rut=? AND v.patente=? AND c.validado=1 AND c.afiliado=1""",
            arrayOf(rutNorm, patenteNorm)
        ).use { c ->
            if (c.moveToFirst()) ConductorInfo(
                c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1,
                c.getInt(5), c.getString(6), c.getString(7), c.getString(8), c.getInt(9)
            ) else null
        }

    fun pagosConductor(vehiculoId: Int, limite: Int = 50): List<PagoItem> =
        readableDatabase.rawQuery(
            """SELECT p.id,p.monto,p.fecha,
                      CAST((strftime('%s','now','localtime')-strftime('%s',p.fecha))/60 AS INTEGER)
               FROM pagos p JOIN viajes v ON v.id=p.viaje_id
               WHERE v.vehiculo_id=? AND p.estado='APROBADO' ORDER BY p.fecha DESC LIMIT ?""",
            arrayOf("$vehiculoId", "$limite")
        ).use { c ->
            buildList { while (c.moveToNext()) add(PagoItem(c.getInt(0), c.getInt(1), c.getString(2), c.getInt(3))) }
        }

    fun ingresos(vehiculoId: Int): Ingresos =
        readableDatabase.rawQuery(
            """SELECT COALESCE(SUM(CASE WHEN date(p.fecha)=date('now','localtime') THEN p.monto END),0),
                      COALESCE(SUM(CASE WHEN p.fecha>=datetime('now','localtime','-7 days') THEN p.monto END),0)
               FROM pagos p JOIN viajes v ON v.id=p.viaje_id
               WHERE v.vehiculo_id=? AND p.estado='APROBADO'""",
            arrayOf("$vehiculoId")
        ).use { c -> if (c.moveToFirst()) Ingresos(c.getInt(0), c.getInt(1)) else Ingresos(0, 0) }

    fun registrarAlerta(conductorId: Int, lat: Double, lng: Double) {
        writableDatabase.execSQL(
            "INSERT INTO alertas(conductor_id,fecha,lat,lng) VALUES (?,datetime('now','localtime'),?,?)",
            arrayOf<Any>(conductorId, lat, lng)
        )
    }

    // ───────────── Pasajero ─────────────
    fun viajesPasajero(): List<ViajeItem> =
        readableDatabase.rawQuery(
            """SELECT v.id,v.fecha,ve.patente,v.monto,v.estado,c.estrellas
               FROM viajes v JOIN vehiculos ve ON ve.id=v.vehiculo_id
               LEFT JOIN calificaciones c ON c.viaje_id=v.id
               WHERE v.pasajero_local=1 ORDER BY v.fecha DESC, v.id DESC""", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    ViajeItem(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4),
                        if (c.isNull(5)) null else c.getInt(5))
                )
            }
        }

    /** Crea el viaje + un pago PENDIENTE (la integración real con Rut Pay sandbox va aquí, RF10). */
    fun crearViaje(vehiculoId: Int, monto: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO viajes(recorrido_id,vehiculo_id,pasajero_local,fecha,estado,monto) VALUES (1,?,1,datetime('now','localtime'),'SOLICITADO',?)",
                arrayOf<Any>(vehiculoId, monto)
            )
            db.execSQL(
                "INSERT INTO pagos(viaje_id,monto,fecha,estado,metodo) VALUES (last_insert_rowid(),?,datetime('now','localtime'),'PENDIENTE','RUTPAY')",
                arrayOf<Any>(monto)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ───────────── Mensajes ─────────────
    fun mensajes(canal: String): List<MensajeItem> =
        readableDatabase.rawQuery(
            "SELECT id,emisor,contenido,fecha FROM mensajes WHERE canal=? ORDER BY fecha ASC, id ASC", arrayOf(canal)
        ).use { c ->
            buildList { while (c.moveToNext()) add(MensajeItem(c.getInt(0), c.getString(1), c.getString(2), c.getString(3))) }
        }

    fun enviarMensaje(canal: String, emisor: String, contenido: String) {
        writableDatabase.execSQL(
            "INSERT INTO mensajes(canal,emisor,contenido,fecha) VALUES (?,?,?,datetime('now','localtime'))",
            arrayOf<Any>(canal, emisor, contenido)
        )
    }
}
