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
import cl.conatacoch.colectigo.data.CANAL_PASAJERO
import cl.conatacoch.colectigo.data.RouteUi
import cl.conatacoch.colectigo.data.TARIFA_DEMO
import cl.conatacoch.colectigo.data.VehiculoVivo
import cl.conatacoch.colectigo.ui.theme.Ambar
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Tinta
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
                2 -> MensajesScreen(c.db, CANAL_PASAJERO, "Pasajero")
                else -> PerfilPasajero(onExit)
            }
        }
    }
}

fun etaTexto(v: VehiculoVivo, r: RouteUi, paradaIdx: Int): String {
    if (r.path.total <= 0.0) return "—"
    val rem = r.paradaMeters[paradaIdx] - v.metros
    if (rem < -30) return "Ya pasó por tu paradero"
    val min = ceil(rem.coerceAtLeast(0.0) / r.path.total * r.info.totalSeconds / 60.0).toInt()
    return if (min <= 0) "Llegando a tu paradero" else "Llega en $min min"
}

@Composable
fun MapaPasajero(c: AppContainer, snackbar: SnackbarHostState) {
    val route by c.fleet.route.collectAsState()
    val vehiculos by c.fleet.vehiculos.collectAsState()
    val perm = rememberLocationPermission()
    val scope = rememberCoroutineScope()
    var paradaSel by remember { mutableIntStateOf(2) }
    var vehSel by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { if (!perm.granted) perm.request() }

    val r = route
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Calculando recorrido…", color = Gris, modifier = Modifier.padding(top = 12.dp))
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_colectigo), contentDescription = "ColectiGo", modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(marca(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Recorrido", color = Gris, fontSize = 14.sp)
            Text(r.nombre, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tinta)
            if (!r.info.fromApi) {
                Text("Ruta aproximada (sin Directions API / sin conexión)", color = Gris, fontSize = 12.sp)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            r.paradas.forEachIndexed { i, p ->
                FilterChip(selected = i == paradaSel, onClick = { paradaSel = i }, label = { Text(p.nombre) })
            }
        }

        RouteMap(
            route = r, vehiculos = vehiculos,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            myLocation = perm.granted, selectedParada = paradaSel,
            onParadaClick = { paradaSel = it }
        )

        Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vehículos en servicio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${vehiculos.size} activos", color = Gris)
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vehiculos.forEachIndexed { i, v ->
                        VehicleCard(i + 1, v, etaTexto(v, r, paradaSel), selected = vehSel == v.id) { vehSel = v.id }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val v = vehiculos.firstOrNull { it.id == vehSel }
                        scope.launch {
                            when {
                                v == null -> snackbar.showSnackbar("Elige un vehículo de la lista")
                                v.asientosLibres == 0 -> snackbar.showSnackbar("Ese vehículo está lleno")
                                else -> {
                                    c.db.crearViaje(v.id, TARIFA_DEMO)
                                    snackbar.showSnackbar("Viaje solicitado en ${formatPatente(v.patente)}. Pago pendiente (Rut Pay)")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Solicitar viaje", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
fun HistorialPasajero(c: AppContainer) {
    val viajes = remember { c.db.viajesPasajero() }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Historial")
        if (viajes.isEmpty()) {
            Text("Aún no tienes viajes.", color = Gris, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(viajes) { v ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPatente(v.patente), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(clp(v.monto), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Text(v.fecha, color = Gris, fontSize = 14.sp)
                    Text(v.estado.lowercase().replaceFirstChar { it.uppercase() }, color = Gris, fontSize = 14.sp)
                    v.estrellas?.let { Text("★".repeat(it) + "☆".repeat(5 - it), color = Ambar, fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
fun PerfilPasajero(onExit: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Perfil")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardBox {
                Text("Cuenta", color = Gris, fontSize = 14.sp)
                Text("Modo invitado (sin registro)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            CardBox {
                Text("Medio de pago", color = Gris, fontSize = 14.sp)
                Text("Rut Pay (ambiente de pruebas)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Cerrar sesión", fontWeight = FontWeight.Bold)
            }
        }
    }
}
