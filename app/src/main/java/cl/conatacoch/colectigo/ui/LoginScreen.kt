package cl.conatacoch.colectigo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.conatacoch.colectigo.AppContainer
import cl.conatacoch.colectigo.R
import cl.conatacoch.colectigo.data.ConductorInfo
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Rojo
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde

sealed interface Session {
    data object None : Session
    data object Pasajero : Session
    data class Conductor(val info: ConductorInfo) : Session
}

fun marca(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Tinta)) { append("Colecti") }
    withStyle(SpanStyle(color = Verde)) { append("Go") }
}

@Composable
fun LoginScreen(c: AppContainer, onSession: (Session) -> Unit) {
    var rut by remember { mutableStateOf("") }
    var patente by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.ic_colectigo), contentDescription = "Logo ColectiGo", modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text(marca(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Tu colectivo, en tiempo real", color = Gris, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onSession(Session.Pasajero) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Soy pasajero · continuar sin registrarme", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(32.dp))
        Text("Soy conductor", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = rut, onValueChange = { rut = it; error = null },
            label = { Text("Rut") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = patente, onValueChange = { patente = it; error = null },
            label = { Text("Patente") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = Rojo, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val info = c.db.loginConductor(normalizar(rut), normalizar(patente))
                if (info == null) error = "Rut o patente no encontrados, o la cuenta aún no está validada por CONATACOCH."
                else onSession(Session.Conductor(info))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Ingresar como conductor", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(16.dp))
        Text(
            "Demo: Rut 12.345.678-5 · Patente AB·CD-12\n(El panel de Administrador es la web, no esta app.)",
            color = Gris, fontSize = 13.sp
        )
    }
}
