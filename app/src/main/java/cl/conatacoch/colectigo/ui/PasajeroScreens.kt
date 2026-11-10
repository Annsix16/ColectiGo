package cl.conatacoch.colectigo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.conatacoch.colectigo.AppContainer
import cl.conatacoch.colectigo.R
import cl.conatacoch.colectigo.data.ReservationRepository
import cl.conatacoch.colectigo.data.RouteUi
import cl.conatacoch.colectigo.data.TARIFA_DEMO
import cl.conatacoch.colectigo.data.VehiculoVivo
import cl.conatacoch.colectigo.ui.theme.Ambar
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private val TABS = listOf("Mapa", "Historial", "Mensajes", "Perfil")

@Composable
fun PasajeroApp(c: AppContainer, onExit: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomTabs(TABS, tab) { tab = it } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MapaPasajero(c, snackbar)
                1 -> HistorialPasajero(c)
                2 -> MensajesScreen(c, "PASAJERO", "Pasajero")
                else -> PerfilPasajero(onExit)
            }
        }
    }
}

fun etaDetallado(v: VehiculoVivo, r: RouteUi, paradaIdx: Int): String {
    if (r.path.total <= 0.0 || r.paradaMeters.isEmpty()) return "—"
    val safeIdx = paradaIdx.coerceIn(0, r.paradaMeters.size - 1)
    val rem = r.paradaMeters[safeIdx] - v.metros
    if (rem < -30) return "Ya pasó por tu paradero"
    val distTxt = if (rem >= 1000) "${(rem / 100.0).roundToInt() / 10.0} km" else "${rem.roundToInt()} m"
    val min = ceil(rem.coerceAtLeast(0.0) / r.path.total * r.info.totalSeconds / 60.0).toInt()
    return if (min <= 0) "Llegando · a $distTxt" else "A $distTxt (llega en $min min)"
}

@Composable
fun MapaPasajero(c: AppContainer, snackbar: SnackbarHostState) {
    val route by c.fleet.route.collectAsState()
    val vehiculos by c.fleet.vehiculos.collectAsState()
    val perm = rememberLocationPermission()
    val scope = rememberCoroutineScope()
    var paradaSel by remember { mutableIntStateOf(0) }
    var vehSel by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { if (!perm.granted) perm.request() }

    val r = route
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Cargando ruta de colectivos…", color = Gris, modifier = Modifier.padding(top = 12.dp), fontSize = 18.sp)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_colectigo), contentDescription = "ColectiGo", modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Text(marca(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Recorrido de Colectivos", color = Gris, fontSize = 15.sp)
            Text(r.nombre, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tinta)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            r.paradas.forEachIndexed { i, p ->
                FilterChip(selected = i == paradaSel, onClick = { paradaSel = i }, label = { Text(p.nombre, fontSize = 16.sp) })
            }
        }

        RouteMap(
            route = r, vehiculos = vehiculos,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            myLocation = perm.granted, selectedParada = paradaSel,
            onParadaClick = { paradaSel = it }
        )

        Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = Color.White, shadowElevation = 10.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Colectivos en Ruta", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${vehiculos.size} activos", color = Verde, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vehiculos.forEachIndexed { i, v ->
                        VehicleCard(i + 1, v, etaDetallado(v, r, paradaSel), selected = vehSel == v.id) { vehSel = v.id }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val v = vehiculos.firstOrNull { it.id == vehSel }
                        val parada = r.paradas.getOrNull(paradaSel)?.nombre ?: "Paradero"
                        scope.launch {
                            when {
                                v == null -> snackbar.showSnackbar("Selecciona un colectivo de la lista para solicitar")
                                v.asientosLibres == 0 -> snackbar.showSnackbar("Este colectivo no tiene asientos disponibles")
                                else -> {
                                    c.db.crearReserva("Pasajero Demo", v.id, parada, TARIFA_DEMO)
                                    ReservationRepository().create(
                                        passengerName = "Pasajero Demo",
                                        driverName = "Conductor ${formatPatente(v.patente)}",
                                        vehicleId = v.id,
                                        patente = v.patente,
                                        origin = r.nombre,
                                        paradaNombre = parada,
                                        monto = TARIFA_DEMO
                                    ) { err ->
                                        scope.launch {
                                            if (err == null) {
                                                snackbar.showSnackbar("🛎️ ¡Solicitud enviada a Firebase en tiempo real a ${formatPatente(v.patente)}!")
                                            } else {
                                                snackbar.showSnackbar("Error Firebase: ${err.localizedMessage}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Solicitar y Reservar Viaje", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            }
        }
    }
}

@Composable
fun HistorialPasajero(c: AppContainer) {
    val viajes = remember { c.db.viajesPasajero() }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Historial de Viajes")
        if (viajes.isEmpty()) {
            Text("Aún no tienes viajes registrados.", color = Gris, modifier = Modifier.padding(20.dp), fontSize = 18.sp)
        }
        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(viajes) { v ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPatente(v.patente), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(clp(v.monto), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Verde)
                    }
                    Text(v.fecha, color = Gris, fontSize = 15.sp)
                    Text("Estado: ${v.estado.lowercase().replaceFirstChar { it.uppercase() }}", color = Gris, fontSize = 15.sp)
                    v.estrellas?.let { Text("★".repeat(it) + "☆".repeat(5 - it), color = Ambar, fontSize = 20.sp) }
                }
            }
        }
    }
}

@Composable
fun PerfilPasajero(onExit: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Perfil del Pasajero")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox {
                Text("Cuenta de Usuario", color = Gris, fontSize = 15.sp)
                Text("Modo Pasajero (Sin registro necesario)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            CardBox {
                Text("Medio de Pago Configurado", color = Gris, fontSize = 15.sp)
                Text("Rut Pay / efectivo (Red de Pruebas)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Cerrar Sesión", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
