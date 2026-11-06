package cl.conatacoch.colectigo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.conatacoch.colectigo.data.RouteUi
import cl.conatacoch.colectigo.data.VehiculoVivo
import cl.conatacoch.colectigo.ui.theme.Ambar
import cl.conatacoch.colectigo.ui.theme.Borde
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.GrisClaro
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde
import cl.conatacoch.colectigo.ui.theme.VerdeClaro
import cl.conatacoch.colectigo.ui.theme.VerdeOscuro
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import java.text.NumberFormat
import java.util.Locale

// ───────────── Formatos ─────────────
fun formatPatente(p: String): String =
    if (p.length == 6) "${p.substring(0, 2)}·${p.substring(2, 4)}-${p.substring(4)}" else p

fun formatRut(r: String): String {
    if (r.length < 2) return r
    val cuerpo = r.dropLast(1)
    val dv = r.last()
    val conPuntos = cuerpo.reversed().chunked(3).joinToString(".").reversed()
    return "$conPuntos-$dv"
}

fun normalizar(s: String): String = s.filter { it.isLetterOrDigit() }.uppercase()

fun clp(n: Int): String = "\$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(n)

fun asientosTexto(n: Int): String = if (n == 0) "Lleno" else if (n == 1) "1 asiento" else "$n asientos"

// ───────────── Permiso de ubicación ─────────────
class LocationPermission(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberLocationPermission(): LocationPermission {
    val ctx = LocalContext.current
    fun check(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    return LocationPermission(granted) {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

// ───────────── Componentes Accesibles (Adultos Mayores) ─────────────
@Composable
fun Pill(text: String, bg: Color = VerdeClaro, fg: Color = VerdeOscuro) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = BorderStroke(1.5.dp, Borde)
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tinta, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}

@Composable
fun BottomTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(color = Borde, thickness = 1.5.dp)
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            items.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).height(64.dp).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(
                        label, fontSize = 16.sp,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == selected) Verde else Gris
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleCard(index: Int, v: VehiculoVivo, eta: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Verde else Borde, shape)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (v.asientosLibres > 0) Ambar else GrisClaro),
            contentAlignment = Alignment.Center
        ) { Text("$index", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Tinta) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(formatPatente(v.patente), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Tinta)
            Text(eta, color = Gris, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (v.asientosLibres > 0) Pill(asientosTexto(v.asientosLibres)) else Pill("Lleno", GrisClaro, Gris)
    }
}

// ───────────── Mapa Principal con Fallback OSM ─────────────
@Composable
fun RouteMap(
    route: RouteUi,
    vehiculos: List<VehiculoVivo>,
    modifier: Modifier = Modifier,
    myLocation: Boolean = false,
    selectedParada: Int? = null,
    followVehiculo: Int? = null,
    onParadaClick: (Int) -> Unit = {}
) {
    var useOsm by remember { mutableStateOf(false) }

    Box(modifier) {
        if (!useOsm) {
            val camera = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(route.paradas.first().pos, 13f)
            }
            var loaded by remember { mutableStateOf(false) }

            LaunchedEffect(loaded, route) {
                if (loaded && route.path.points.isNotEmpty()) {
                    val b = LatLngBounds.Builder()
                    route.path.points.forEach { b.include(it) }
                    camera.move(CameraUpdateFactory.newLatLngBounds(b.build(), 100))
                }
            }
            val seguido = vehiculos.firstOrNull { it.id == followVehiculo && it.real }
            LaunchedEffect(seguido?.pos) {
                seguido?.let { camera.animate(CameraUpdateFactory.newLatLng(it.pos)) }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(isMyLocationEnabled = myLocation),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = myLocation,
                    scrollGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                    rotationGesturesEnabled = true,
                    tiltGesturesEnabled = true
                ),
                onMapLoaded = { loaded = true }
            ) {
                Polyline(points = route.path.points, color = Verde, width = 14f)
                route.paradas.forEachIndexed { i, p ->
                    key(p.id) {
                        Marker(
                            state = remember { MarkerState(p.pos) },
                            title = p.nombre,
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (i == selectedParada) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_GREEN
                            ),
                            onClick = { onParadaClick(i); false }
                        )
                    }
                }
                vehiculos.forEach { v ->
                    key(v.id) {
                        val st = remember { MarkerState(v.pos) }
                        SideEffect { st.position = v.pos }
                        Marker(
                            state = st,
                            title = formatPatente(v.patente),
                            snippet = if (v.real) "GPS en vivo · ${asientosTexto(v.asientosLibres)}" else asientosTexto(v.asientosLibres),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                            zIndex = 1f
                        )
                    }
                }
            }
        } else {
            OsmRouteMap(
                route = route,
                vehiculos = vehiculos,
                modifier = Modifier.fillMaxSize(),
                selectedParada = selectedParada,
                onParadaClick = onParadaClick
            )
        }

        // Botón para alternar a OpenStreetMap si la clave de Google Maps no carga mapa raster
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.9f))
                .clickable { useOsm = !useOsm }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                if (useOsm) "Usar Google Maps" else "Usar Mapa OSM (Libre)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Tinta
            )
        }
    }
}

@Composable
fun OsmRouteMap(
    route: RouteUi,
    vehiculos: List<VehiculoVivo>,
    modifier: Modifier = Modifier,
    selectedParada: Int? = null,
    onParadaClick: (Int) -> Unit = {}
) {
    val ctx = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            if (route.paradas.isNotEmpty()) {
                val p = route.paradas.first().pos
                controller.setCenter(GeoPoint(p.latitude, p.longitude))
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()
            val line = OsmPolyline().apply {
                outlinePaint.color = android.graphics.Color.parseColor("#0F9D58")
                outlinePaint.strokeWidth = 12f
                setPoints(route.path.points.map { GeoPoint(it.latitude, it.longitude) })
            }
            map.overlays.add(line)

            route.paradas.forEachIndexed { i, p ->
                val m = OsmMarker(map).apply {
                    position = GeoPoint(p.pos.latitude, p.pos.longitude)
                    title = p.nombre
                    setOnMarkerClickListener { _, _ ->
                        onParadaClick(i)
                        true
                    }
                }
                map.overlays.add(m)
            }

            vehiculos.forEach { v ->
                val vm = OsmMarker(map).apply {
                    position = GeoPoint(v.pos.latitude, v.pos.longitude)
                    title = formatPatente(v.patente)
                    snippet = asientosTexto(v.asientosLibres)
                }
                map.overlays.add(vm)
            }
            map.invalidate()
        }
    )
}
