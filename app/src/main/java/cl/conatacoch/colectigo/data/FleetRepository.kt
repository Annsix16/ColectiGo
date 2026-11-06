package cl.conatacoch.colectigo.data

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Geometría de la ruta: permite ubicar un punto a X metros del inicio y viceversa. */
class RoutePath(val points: List<LatLng>) {
    private val cum = DoubleArray(points.size)
    val total: Double

    init {
        for (i in 1 until points.size) cum[i] = cum[i - 1] + SphericalUtil.computeDistanceBetween(points[i - 1], points[i])
        total = if (points.isEmpty()) 0.0 else cum.last()
    }

    fun pointAt(meters: Double): LatLng {
        val m = meters.coerceIn(0.0, total)
        for (i in 1 until points.size) {
            if (cum[i] >= m) {
                val seg = cum[i] - cum[i - 1]
                val f = if (seg <= 0.0) 0.0 else (m - cum[i - 1]) / seg
                return SphericalUtil.interpolate(points[i - 1], points[i], f)
            }
        }
        return points.last()
    }

    /** Metros recorridos desde el inicio hasta el vértice de la ruta más cercano a [p]. */
    fun metersAlong(p: LatLng): Double {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in points.indices) {
            val d = SphericalUtil.computeDistanceBetween(points[i], p)
            if (d < bestD) { bestD = d; best = i }
        }
        return cum[best]
    }
}

data class RouteUi(
    val nombre: String,
    val paradas: List<Parada>,
    val info: RouteInfo,
    val path: RoutePath,
    val paradaMeters: List<Double>
)

data class VehiculoVivo(
    val id: Int, val patente: String, val capacidad: Int, val asientosLibres: Int,
    val pos: LatLng, val metros: Double, val real: Boolean
)

/**
 * Estado de la flota en "tiempo real".
 *
 * HOY (sin backend): los vehículos se simulan recorriendo la ruta; y si un conductor activa
 * "compartir ubicación", su vehículo usa el GPS real del teléfono (publishRealLocation).
 *
 * MAÑANA (con backend): reemplazar [simular] por un WebSocket/Firebase que entregue las posiciones
 * de todos los conductores, y hacer que [publishRealLocation] las envíe al servidor.
 * Las pantallas no cambian: solo observan [vehiculos] y [route].
 */
class FleetRepository(
    private val db: ColectiDb,
    private val directions: DirectionsRepository,
    private val scope: CoroutineScope
) {
    private val _route = MutableStateFlow<RouteUi?>(null)
    val route: StateFlow<RouteUi?> = _route.asStateFlow()

    private val _vehiculos = MutableStateFlow<List<VehiculoVivo>>(emptyList())
    val vehiculos: StateFlow<List<VehiculoVivo>> = _vehiculos.asStateFlow()

    private val base = db.vehiculosBase()
    private val asientos = ConcurrentHashMap<Int, Int>()
    private val offsets = ConcurrentHashMap<Int, Double>()
    private val real = ConcurrentHashMap<Int, LatLng>()

    init {
        base.forEach { asientos[it.id] = it.asientos }
        scope.launch {
            cargarRuta()
            simular()
        }
    }

    private suspend fun cargarRuta() {
        val paradas = db.paraderos()
        val info = directions.route(paradas.map { it.pos })
        val path = RoutePath(info.points)
        _route.value = RouteUi(db.nombreRecorrido(), paradas, info, path, paradas.map { path.metersAlong(it.pos) })
    }

    private suspend fun simular() {
        val r = _route.value ?: return
        if (r.path.total <= 0.0) return
        val speed = r.path.total / maxOf(r.info.totalSeconds, 60) // m/s coherente con el ETA
        val starts = listOf(0.05, 0.20, 0.35)
        base.forEachIndexed { i, v -> offsets[v.id] = r.path.total * starts.getOrElse(i) { 0.1 } }
        emitir()
        while (true) {
            delay(1000)
            base.forEach { v ->
                var m = (offsets[v.id] ?: 0.0) + speed
                if (m > r.path.total) m -= r.path.total
                offsets[v.id] = m
            }
            emitir()
        }
    }

    private fun emitir() {
        val r = _route.value ?: return
        _vehiculos.value = base.map { v ->
            val rp = real[v.id]
            val pos = rp ?: r.path.pointAt(offsets[v.id] ?: 0.0)
            val m = if (rp != null) r.path.metersAlong(rp) else (offsets[v.id] ?: 0.0)
            VehiculoVivo(v.id, v.patente, v.capacidad, asientos[v.id] ?: v.asientos, pos, m, rp != null)
        }
    }

    fun publishRealLocation(vehiculoId: Int, pos: LatLng) { real[vehiculoId] = pos; emitir() }
    fun clearRealLocation(vehiculoId: Int) { real.remove(vehiculoId); emitir() }

    fun setAsientos(vehiculoId: Int, n: Int) {
        val cap = base.firstOrNull { it.id == vehiculoId }?.capacidad ?: return
        val v = n.coerceIn(0, cap)
        asientos[vehiculoId] = v
        db.actualizarAsientos(vehiculoId, v)
        emitir()
    }
}
