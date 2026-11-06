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
import cl.conatacoch.colectigo.data.ColectiDb
import cl.conatacoch.colectigo.ui.theme.Gris
import cl.conatacoch.colectigo.ui.theme.Tinta
import cl.conatacoch.colectigo.ui.theme.VerdeClaro

/** Canal de mensajes con la dirigencia (RF08, RF09, RF17). Sirve para pasajero y conductor. */
@Composable
fun MensajesScreen(db: ColectiDb, canal: String, miNombre: String) {
    var refresh by remember { mutableIntStateOf(0) }
    val msgs = remember(refresh) { db.mensajes(canal) }
    var texto by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTitle("Mensajes")
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(msgs) { m ->
                val mio = m.emisor == miNombre
                CardBox(color = if (mio) VerdeClaro else Color.White) {
                    Text(m.emisor, fontWeight = FontWeight.Bold, color = Tinta)
                    Text(m.contenido, color = Tinta, modifier = Modifier.padding(top = 4.dp))
                    Text(m.fecha, color = Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it },
                placeholder = { Text("Escribe un mensaje") }, modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (texto.isNotBlank()) {
                    db.enviarMensaje(canal, miNombre, texto.trim())
                    texto = ""
                    refresh++
                }
            }) { Text("Enviar") }
        }
    }
}
