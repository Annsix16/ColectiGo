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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import cl.conatacoch.colectigo.AppContainer
import cl.conatacoch.colectigo.R
import cl.conatacoch.colectigo.data.ConductorInfo
import cl.conatacoch.colectigo.location.BiometricHelper
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Rojo
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.Verde

sealed interface Session {
    data object None : Session
    data object Pasajero : Session
    data class Conductor(val info: ConductorInfo) : Session
    data object Admin : Session
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
    val ctx = LocalContext.current

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Image(painterResource(R.drawable.ic_colectigo), contentDescription = "Logo ColectiGo", modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text(marca(), fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("Tu colectivo, en tiempo real", color = Gris, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { onSession(Session.Pasajero) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Soy Pasajero · Continuar", fontWeight = FontWeight.Bold, fontSize = 20.sp) }

        Spacer(Modifier.height(28.dp))
        Text("Ingreso Conductor", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.fillMaxWidth(), color = Tinta)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = rut, onValueChange = { rut = it; error = null },
            label = { Text("RUT Conductor", fontSize = 16.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = patente, onValueChange = { patente = it; error = null },
            label = { Text("Patente (ej: ABCD12)", fontSize = 16.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = Rojo, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = {
                val info = c.db.loginConductor(normalizar(rut), normalizar(patente))
                if (info == null) error = "Rut o patente no encontrados, o la cuenta aún no está validada."
                else onSession(Session.Conductor(info))
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Ingresar como Conductor", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val activity = ctx as? FragmentActivity
                if (activity != null) {
                    val helper = BiometricHelper(activity)
                    helper.authenticate(
                        onSuccess = {
                            val defaultDriver = c.db.loginConductor("123456785", "ABCD12")
                            if (defaultDriver != null) onSession(Session.Conductor(defaultDriver))
                        },
                        onError = { err -> error = err }
                    )
                } else {
                    val defaultDriver = c.db.loginConductor("123456785", "ABCD12")
                    if (defaultDriver != null) onSession(Session.Conductor(defaultDriver))
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("🔑 Ingreso Biométrico (Huella/Rostro)", fontWeight = FontWeight.Bold, fontSize = 17.sp) }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { onSession(Session.Admin) }) {
            Text("⚙️ Panel Administrador CONATACOCH", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Verde)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Demo: RUT 12.345.678-5 · Patente AB·CD-12",
            color = Gris, fontSize = 14.sp
        )
    }
}
