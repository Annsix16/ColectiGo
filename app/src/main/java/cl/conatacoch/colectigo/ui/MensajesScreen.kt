package cl.conatacoch.colectigo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.conatacoch.colectigo.AppContainer
import cl.conatacoch.colectigo.data.CloudMessage
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.VerdeClaro
import kotlinx.coroutines.launch

@Composable
fun MensajesScreen(c: AppContainer, rol: String, miNombre: String) {
    var mensajes by remember { mutableStateOf<List<CloudMessage>>(emptyList()) }
    var texto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(c, rol) {
        val listener = c.messages.observe(
            role = rol,
            onMessages = {
                mensajes = it
                error = null
            },
            onError = { error = it.message ?: "No se pudieron cargar los mensajes." }
        )
        onDispose { listener.remove() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTitle("Mensajes")
        error?.let {
            Text(
                it,
                color = Color(0xFFB3261E),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                fontSize = 13.sp
            )
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mensajes, key = { it.id }) { message ->
                val mio = message.senderUid.isNotEmpty() &&
                    message.senderUid == c.messages.currentUid
                CardBox(color = if (mio) VerdeClaro else Color.White) {
                    Text(message.senderName, fontWeight = FontWeight.Bold, color = Tinta)
                    Text(message.content, color = Tinta, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        message.createdAt,
                        color = Gris,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                placeholder = { Text("Escribe un mensaje") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !enviando
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = texto.isNotBlank() && !enviando,
                onClick = {
                    enviando = true
                    error = null
                    c.messages.send(rol, miNombre, texto) { sendError ->
                        scope.launch {
                            enviando = false
                            if (sendError == null) texto = ""
                            else error = sendError.message ?: "No se pudo enviar el mensaje."
                        }
                    }
                }
            ) { Text(if (enviando) "…" else "Enviar") }
        }
    }
}
