package cl.conatacoch.colectigo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.conatacoch.colectigo.AppContainer
import cl.conatacoch.colectigo.data.ConductorInfo
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Rojo
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde

private val TABS_ADMIN = listOf("Conductores", "Crear Nuevo", "Salir")

@Composable
fun AdminApp(c: AppContainer, onExit: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val conductores = remember(refresh) { c.db.adminConductores() }

    Scaffold(
        bottomBar = { BottomTabs(TABS_ADMIN, tab) { tab = it } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ListaConductoresAdmin(conductores, onDelete = { id ->
                    c.db.adminEliminarConductor(id)
                    refresh++
                })
                1 -> CrearConductorAdmin(onCrear = { n, r, lic, tLic, venc, pat, mar, mod, cap, com ->
                    c.db.adminCrearConductor(n, r, lic, tLic, venc, pat, mar, mod, cap, com)
                    refresh++
                    tab = 0
                })
                else -> onExit()
            }
        }
    }
}

@Composable
fun ListaConductoresAdmin(conductores: List<ConductorInfo>, onDelete: (Int) -> Unit) {
    var itemEliminar by remember { mutableStateOf<ConductorInfo?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Panel de Administrador")
        Text(
            "Gestión de Flota, Licencias y Documentación",
            color = Gris, fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conductores) { item ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.nombre, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Tinta)
                            Text("RUT: ${formatRut(item.rut)} · Licencia: ${item.tipoLicencia}", color = Gris, fontSize = 15.sp)
                            Text("Vencimiento Licencia: ${item.vencimientoLicencia}", color = if (item.requiereAlertaLicencia()) Rojo else Verde, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Vehículo: ${formatPatente(item.patente)} (${item.marca} ${item.modelo})", color = Tinta, fontSize = 15.sp)
                            Text("Comuna: ${item.comuna} · Rev. Técnica: ${item.revisionTecnicaVenc}", color = Gris, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { itemEliminar = item },
                            colors = ButtonDefaults.buttonColors(containerColor = Rojo, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Eliminar", fontSize = 14.sp) }
                    }
                }
            }
        }
    }

    itemEliminar?.let { item ->
        AlertDialog(
            onDismissRequest = { itemEliminar = null },
            title = { Text("¿Eliminar Conductor?") },
            text = { Text("Se eliminará a ${item.nombre} y el vehículo asociado ${formatPatente(item.patente)}.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.usuarioId)
                    itemEliminar = null
                }) { Text("Eliminar", color = Rojo, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { itemEliminar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun CrearConductorAdmin(onCrear: (String, String, String, String, String, String, String, String, Int, String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var rut by remember { mutableStateOf("") }
    var tipoLicencia by remember { mutableStateOf("A2") }
    var vencimiento by remember { mutableStateOf("2026-11-30") }
    var patente by remember { mutableStateOf("") }
    var marca by remember { mutableStateOf("Hyundai") }
    var modelo by remember { mutableStateOf("Accent") }
    var comuna by remember { mutableStateOf("Iquique") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenTitle("Nuevo Conductor y Vehículo")

        OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre Completo") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = rut, onValueChange = { rut = it }, label = { Text("RUT (ej: 123456785)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = tipoLicencia, onValueChange = { tipoLicencia = it }, label = { Text("Tipo Licencia (A1/A2/A3)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = vencimiento, onValueChange = { vencimiento = it }, label = { Text("Vencimiento (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = patente, onValueChange = { patente = it }, label = { Text("Patente Vehículo") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = comuna, onValueChange = { comuna = it }, label = { Text("Comuna") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (nombre.isNotBlank() && rut.isNotBlank() && patente.isNotBlank()) {
                    onCrear(nombre, normalizar(rut), tipoLicencia, tipoLicencia, vencimiento, normalizar(patente), marca, modelo, 4, comuna)
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Guardar Conductor y Vehículo", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}
