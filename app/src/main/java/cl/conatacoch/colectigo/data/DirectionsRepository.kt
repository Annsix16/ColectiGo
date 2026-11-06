package cl.conatacoch.colectigo.data

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RouteInfo(val points: List<LatLng>, val totalSeconds: Int, val totalMeters: Int, val fromApi: Boolean)

/**
 * Calcula la ruta y su duración con Google Maps Platform (Directions API).
 * Si no hay API key o falla la red, usa una ruta de respaldo (líneas rectas entre paraderos)
 * para que la app nunca quede en blanco.
 *
 * Nota de seguridad (informe, punto 12): en producción esta llamada debería ir por el
 * backend (Node.js) para no exponer la key en el APK. Para el prototipo se llama directo.
 */
class DirectionsRepository(private val apiKey: String) {

    suspend fun route(stops: List<LatLng>): RouteInfo = withContext(Dispatchers.IO) {
        try {
            require(apiKey.isNotBlank()) { "Sin API key" }
            val origin = stops.first()
            val dest = stops.last()
            val via = stops.drop(1).dropLast(1).joinToString("|") { "via:${it.latitude},${it.longitude}" }
            val url = buildString {
                append("https://maps.googleapis.com/maps/api/directions/json")
                append("?origin=${origin.latitude},${origin.longitude}")
                append("&destination=${dest.latitude},${dest.longitude}")
                if (via.isNotEmpty()) append("&waypoints=${URLEncoder.encode(via, "UTF-8")}")
                append("&mode=driving&language=es&key=$apiKey")
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val status = json.getString("status")
            if (status != "OK") error(json.optString("error_message", status))
            val route = json.getJSONArray("routes").getJSONObject(0)
            val pts = PolyUtil.decode(route.getJSONObject("overview_polyline").getString("points"))
            var secs = 0
            var meters = 0
            val legs = route.getJSONArray("legs")
            for (i in 0 until legs.length()) {
                val leg = legs.getJSONObject(i)
                secs += leg.getJSONObject("duration").getInt("value")
                meters += leg.getJSONObject("distance").getInt("value")
            }
            RouteInfo(pts, secs, meters, true)
        } catch (e: Exception) {
            fallback(stops)
        }
    }

    private fun fallback(stops: List<LatLng>): RouteInfo {
        var meters = 0.0
        for (i in 1 until stops.size) meters += SphericalUtil.computeDistanceBetween(stops[i - 1], stops[i])
        val secs = (meters / 6.9).toInt() // ~25 km/h
        return RouteInfo(stops, secs, meters.toInt(), false)
    }
}
