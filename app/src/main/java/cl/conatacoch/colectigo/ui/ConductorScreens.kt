package cl.conatacoch.colectigo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.conatacoch.colectigo.AppContainer
import cl.conatacoch.colectigo.data.CANAL_CONDUCTOR
import cl.conatacoch.colectigo.data.ConductorInfo
import cl.conatacoch.colectigo.ui.theme.FondoSuave
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.GrisClaro
import cl.conatacoch.colectigo.ui.theme.Rojo
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TABS_CONDUCTOR = listOf("Mapa", "Historial", "Mensajes", "Perfil")

@Composable
fun ConductorApp(c: AppContainer, info: ConductorInfo, onExit: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var compartir by remember { mutableStateOf(false) }
    val perm = rememberLocationPermission()
    val snackbar = remember { SnackbarHostState() }

    // Rastreo en tiempo real (Play Services Location). Vive aquí para seguir activo al cambiar de pestaña.
    LaunchedEffect(compartir, perm.granted) {
        if (compartir && perm.granted) {
            c.location.updates().collect { loc ->
                c.fleet.publishRealLocation(info.vehiculoId, LatLng(loc.latitude, loc.longitude))
            }
        }
    }
    DisposableEffect(compartir) { onDispose { c.fleet.clearRealLocation(info.vehiculoId) } }

    Scaffold(
        containerColor = FondoSuave,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomTabs(TABS_CONDUCTOR, tab) { tab = it } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> PanelConductor(c, info, compartir, perm.granted, snackbar) { on ->
                    compartir = on
                    if (on && !perm.granted) perm.request()
                }
                1 -> IngresosConductor(c, info)
                2 -> MensajesScreen(c.db, CANAL_CONDUCTOR, info.nombre)
                else -> PerfilConductor(info, onExit)
            }
        }
    }
}

@Composable
fun PanelConductor(
    c: AppContainer, info: ConductorInfo, compartir: Boolean, permisoOk: Boolean,
    snackbar: SnackbarHostState, onToggle: (Boolean) -> Unit
) {
    val route by c.fleet.route.collectAsState()
    val vehiculos by c.fleet.vehiculos.collectAsState()
    val mine = vehiculos.firstOrNull { it.id == info.vehiculoId }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val pagos = remember(tick) { c.db.pagosConductor(info.vehiculoId, 1) }
    val ingresos = remember(tick) { c.db.ingresos(info.vehiculoId) }
    val ultimoMsg = remember { c.db.mensajes(CANAL_CONDUCTOR).lastOrNull() }
    var confirmar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Conductor", color = Gris, fontSize = 14.sp)
                Text(formatPatente(info.patente), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tinta)
            }
            if (compartir && permisoOk) Pill("Activo") else Pill("Sin GPS", GrisClaro, Gris)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Asientos disponibles", color = Gris)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${mine?.asientosLibres ?: 0}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            Text("  / ${info.capacidad}", color = Gris, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = { c.fleet.setAsientos(info.vehiculoId, (mine?.asientosLibres ?: 0) - 1) },
                        modifier = Modifier.size(60.dp), shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("−", fontSize = 28.sp) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { c.fleet.setAsientos(info.vehiculoId, (mine?.asientosLibres ?: 0) + 1) },
                        modifier = Modifier.size(60.dp), shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("+", fontSize = 28.sp) }
                }
            }

            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compartir mi ubicación", fontWeight = FontWeight.Bold)
                        Text(
                            if (compartir && !permisoOk) "Falta el permiso de ubicación" else "Los pasajeros verán tu vehículo en vivo",
                            color = if (compartir && !permisoOk) Rojo else Gris, fontSize = 13.sp
                        )
                    }
                    Switch(checked = compartir, onCheckedChange = onToggle)
                }
            }

            val r = route
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                if (r == null) CircularProgressIndicator()
                else RouteMap(
                    route = r, vehiculos = vehiculos, modifier = Modifier.fillMaxSize(),
                    myLocation = permisoOk, followVehiculo = info.vehiculoId
                )
            }

            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Último pago recibido", color = Gris)
                        val p = pagos.firstOrNull()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(p?.let { clp(it.monto) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            p?.let { Text("  hace ${it.haceMin} min", color = Gris, modifier = Modifier.padding(bottom = 2.dp)) }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Ingresos de hoy", color = Gris)
                        Text(clp(ingresos.hoy), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ultimoMsg?.let {
                CardBox {
                    Text(it.emisor, fontWeight = FontWeight.Bold)
                    Text(it.contenido, color = Gris)
                }
            }
        }

        Button(
            onClick = { confirmar = true },
            colors = ButtonDefaults.buttonColors(containerColor = Rojo, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(72.dp),
            shape = RoundedCornerShape(22.dp)
        ) { Text("EMERGENCIA", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Activar alerta de emergencia?") },
            text = { Text("Se avisará a la Dirigencia y a tu contacto de emergencia con tu ubicación.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    val pos = mine?.pos ?: route?.paradas?.firstOrNull()?.pos ?: LatLng(0.0, 0.0)
                    c.db.registrarAlerta(info.usuarioId, pos.latitude, pos.longitude)
                    // TODO backend: enviar la alerta (conductor, hora, ubicación) en < 3 s a la dirigencia (RF18).
                    scope.launch { snackbar.showSnackbar("Alerta registrada y enviada a la Dirigencia (simulado)") }
                }) { Text("Activar", color = Rojo, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun IngresosConductor(c: AppContainer, info: ConductorInfo) {
    val pagos = remember { c.db.pagosConductor(info.vehiculoId) }
    val ing = remember { c.db.ingresos(info.vehiculoId) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Historial de ingresos")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardBox(Modifier.weight(1f)) { Text("Hoy", color = Gris); Text(clp(ing.hoy), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            CardBox(Modifier.weight(1f)) { Text("Esta semana", color = Gris); Text(clp(ing.semana), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pagos) { p ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clp(p.monto), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(p.fecha, color = Gris)
                    }
                }
            }
        }
    }
}

@Composable
fun PerfilConductor(info: ConductorInfo, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Perfil")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardBox {
                Text(info.nombre, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Rut ${formatRut(info.rut)}", color = Gris)
                Text("Licencia clase ${info.licencia}", color = Gris)
                Text(if (info.afiliado) "Afiliación vigente" else "Afiliación no vigente", color = Verde, fontWeight = FontWeight.Bold)
            }
            CardBox {
                Text("Vehículo (solo lectura)", fontWeight = FontWeight.Bold)
                Text("${formatPatente(info.patente)} · ${info.marca} ${info.modelo}", color = Tinta)
                Text("Capacidad: ${info.capacidad} asientos", color = Gris)
                Text(
                    "Los datos del vehículo están vinculados a la póliza de seguro. Para modificarlos, contacta a CONATACOCH.",
                    color = Gris, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Cerrar sesión", fontWeight = FontWeight.Bold)
            }
        }
    }
}
