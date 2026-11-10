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
import cl.conatacoch.colectigo.data.ReservationRepository
import cl.conatacoch.colectigo.ui.theme.FondoSuave
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.GrisClaro
import cl.conatacoch.colectigo.ui.theme.Rojo
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TABS_CONDUCTOR = listOf("Mapa", "Reservas", "Historial", "Mensajes", "Perfil")

@Composable
fun ConductorApp(c: AppContainer, info: ConductorInfo, onExit: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var compartir by remember { mutableStateOf(false) }
    val perm = rememberLocationPermission()
    val snackbar = remember { SnackbarHostState() }

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
                1 -> ReservasConductor(c, info, snackbar)
                2 -> IngresosConductor(c, info)
                3 -> MensajesScreen(c.db, CANAL_CONDUCTOR, info.nombre)
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
    val reservationRepo = remember { ReservationRepository() }

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

        if (info.requiereAlertaLicencia()) {
            Box(
                Modifier.fillMaxWidth().background(Rojo).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "⚠️ Alerta: Tu Licencia de Conducir (${info.tipoLicencia}) vence en ${info.diasHastaVencimientoLicencia()} días (${info.vencimientoLicencia}). Renóvala con tiempo.",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Asientos disponibles (Se sinc. en nube)", color = Gris, fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${mine?.asientosLibres ?: 0}", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                            Text("  / ${info.capacidad}", color = Gris, fontSize = 22.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val nuevo = ((mine?.asientosLibres ?: 0) - 1).coerceAtLeast(0)
                            c.fleet.setAsientos(info.vehiculoId, nuevo)
                            reservationRepo.updateVehicleSeats(info.vehiculoId, nuevo) { _ -> }
                        },
                        modifier = Modifier.size(60.dp), shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("−", fontSize = 28.sp) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val nuevo = ((mine?.asientosLibres ?: 0) + 1).coerceIn(0, info.capacidad)
                            c.fleet.setAsientos(info.vehiculoId, nuevo)
                            reservationRepo.updateVehicleSeats(info.vehiculoId, nuevo) { _ -> }
                        },
                        modifier = Modifier.size(60.dp), shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("+", fontSize = 28.sp) }
                }
            }

            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compartir mi ubicación en vivo", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            if (compartir && !permisoOk) "Falta el permiso de ubicación" else "Los pasajeros verán tu colectivo en el mapa",
                            color = if (compartir && !permisoOk) Rojo else Gris, fontSize = 14.sp
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
                        Text("Último pago recibido", color = Gris, fontSize = 15.sp)
                        val p = pagos.firstOrNull()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(p?.let { clp(it.monto) } ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            p?.let { Text("  hace ${it.haceMin} min", color = Gris, modifier = Modifier.padding(bottom = 2.dp)) }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Ingresos de hoy", color = Gris, fontSize = 15.sp)
                        Text(clp(ingresos.hoy), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ultimoMsg?.let {
                CardBox {
                    Text(it.emisor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(it.contenido, color = Gris, fontSize = 15.sp)
                }
            }
        }

        Button(
            onClick = { confirmar = true },
            colors = ButtonDefaults.buttonColors(containerColor = Rojo, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(72.dp),
            shape = RoundedCornerShape(22.dp)
        ) { Text("EMERGENCIA SOS", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
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
                    scope.launch { snackbar.showSnackbar("Alerta registrada y enviada a la Dirigencia") }
                }) { Text("Activar", color = Rojo, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun ReservasConductor(c: AppContainer, info: ConductorInfo, snackbar: SnackbarHostState) {
    var tick by remember { mutableIntStateOf(0) }
    val reservas = remember(tick) { c.db.reservasPendientes(info.vehiculoId) }
    val scope = rememberCoroutineScope()
    val reservationRepo = remember { ReservationRepository() }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Solicitudes de Reserva")
        Text("Pasajeros solicitando viaje en tiempo real", color = Gris, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        if (reservas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay reservas pendientes.", color = Gris, fontSize = 18.sp)
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reservas) { r ->
                    CardBox {
                        Text("Pasajero: ${r.pasajeroNombre}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Tinta)
                        Text("Paradero: ${r.paradaNombre}", fontSize = 16.sp, color = Gris)
                        Text("Monto: ${clp(r.monto)} · ${r.fecha}", fontSize = 15.sp, color = Tinta)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    c.db.responderReserva(r.id, aceptar = false, vehiculoId = info.vehiculoId)
                                    tick++
                                    scope.launch { snackbar.showSnackbar("Reserva de ${r.pasajeroNombre} rechazada") }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Rechazar", color = Rojo, fontWeight = FontWeight.Bold, fontSize = 17.sp) }

                            Button(
                                onClick = {
                                    c.db.responderReserva(r.id, aceptar = true, vehiculoId = info.vehiculoId)
                                    reservationRepo.updateStatus(r.id.toString(), "ACEPTADA") { _ -> }
                                    tick++
                                    scope.launch { snackbar.showSnackbar("Reserva aceptada. Asientos actualizados") }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Aceptar", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IngresosConductor(c: AppContainer, info: ConductorInfo) {
    val pagos = remember { c.db.pagosConductor(info.vehiculoId) }
    val ing = remember { c.db.ingresos(info.vehiculoId) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Historial de ingresos")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardBox(Modifier.weight(1f)) { Text("Hoy", color = Gris, fontSize = 15.sp); Text(clp(ing.hoy), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            CardBox(Modifier.weight(1f)) { Text("Esta semana", color = Gris, fontSize = 15.sp); Text(clp(ing.semana), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pagos) { p ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clp(p.monto), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(p.fecha, color = Gris, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PerfilConductor(info: ConductorInfo, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Perfil del Conductor")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox {
                Text(info.nombre, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Tinta)
                Text("RUT ${formatRut(info.rut)}", color = Gris, fontSize = 16.sp)
                Text("Licencia Clase ${info.tipoLicencia} (Vence: ${info.vencimientoLicencia})", color = if (info.requiereAlertaLicencia()) Rojo else Tinta, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (info.afiliado) "Afiliación CONATACOCH vigente" else "Afiliación no vigente", color = Verde, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            CardBox {
                Text("Datos de Documentación del Vehículo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("${formatPatente(info.patente)} · ${info.marca} ${info.modelo}", color = Tinta, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Comuna: ${info.comuna}", color = Gris, fontSize = 15.sp)
                Text("Permiso de Circulación Vence: ${info.permisoCirculacionVenc}", color = Gris, fontSize = 15.sp)
                Text("Revisión Técnica Vence: ${info.revisionTecnicaVenc} (${info.plantaRevision})", color = Gris, fontSize = 15.sp)
                Text("Seguro Obligatorio (SOAP): ${info.seguroObligatorioVenc}", color = Gris, fontSize = 15.sp)
            }

            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Cerrar sesión", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
