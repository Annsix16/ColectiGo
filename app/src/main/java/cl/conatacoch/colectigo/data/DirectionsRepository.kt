package cl.conatacoch.colectigo.data

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RouteInfo(val points: List<LatLng>, val totalSeconds: Int, val totalMeters: Int, val fromApi: Boolean)

/**
 * Calcula la ruta por CALLES REALES con OSRM (Open Source Routing Machine) para OpenStreetMap.
 * Traza las curvas y giros exactos por la carretera y calles.
 */
class DirectionsRepository(private val apiKey: String = "") {

    suspend fun route(stops: List<LatLng>): RouteInfo = withContext(Dispatchers.IO) {
        try {
            val coords = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
            val url = "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "ColectiGoAndroidApp/1.0")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val code = json.optString("code")
            if (code == "Ok") {
                val routeObj = json.getJSONArray("routes").getJSONObject(0)
                val secs = routeObj.getDouble("duration").toInt()
                val meters = routeObj.getDouble("distance").toInt()
                val coordsArr = routeObj.getJSONObject("geometry").getJSONArray("coordinates")
                val pts = mutableListOf<LatLng>()
                for (i in 0 until coordsArr.length()) {
                    val pt = coordsArr.getJSONArray(i)
                    val lng = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    pts.add(LatLng(lat, lng))
                }
                if (pts.isNotEmpty()) {
                    return@withContext RouteInfo(pts, secs, meters, true)
                }
            }
            fallback(stops)
        } catch (e: Exception) {
            fallback(stops)
        }
    }

    private fun fallback(stops: List<LatLng>): RouteInfo {
        val streetWaypoints = listOf(
            LatLng(-20.2721, -70.1030), // Terminal Alto Hospicio
            LatLng(-20.2700, -70.1080), // Av. Ramón Pérez Opazo
            LatLng(-20.2650, -70.1150), // Av. Los Aromos
            LatLng(-20.2580, -70.1210), // Inicio Cuesta Ruta 16
            LatLng(-20.2510, -70.1260), // Curva Cuesta Ruta 16 (Bajada)
            LatLng(-20.2450, -70.1280), // Rotonda El Salitre
            LatLng(-20.2380, -70.1330), // Av. Las Parcelas
            LatLng(-20.2280, -70.1410), // Av. Héroes de la Concepción
            LatLng(-20.2210, -70.1460), // Av. Diego Portales
            LatLng(-20.2139, -70.1506)  // Plaza de Armas Iquique
        )
        var meters = 0.0
        for (i in 1 until streetWaypoints.size) {
            meters += SphericalUtil.computeDistanceBetween(streetWaypoints[i - 1], streetWaypoints[i])
        }
        val secs = (meters / 7.5).toInt()
        return RouteInfo(streetWaypoints, secs, meters.toInt(), false)
    }
}
