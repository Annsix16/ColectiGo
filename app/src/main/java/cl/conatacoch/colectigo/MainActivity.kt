package cl.conatacoch.colectigo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cl.conatacoch.colectigo.ui.ConductorApp
import cl.conatacoch.colectigo.ui.LoginScreen
import cl.conatacoch.colectigo.ui.PasajeroApp
import cl.conatacoch.colectigo.ui.Session
import cl.conatacoch.colectigo.ui.theme.ColectiGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ColectiGoApp).container
        setContent {
            ColectiGoTheme {
                var session by remember { mutableStateOf<Session>(Session.None) }
                when (val s = session) {
                    Session.None -> LoginScreen(container) { session = it }
                    Session.Pasajero -> PasajeroApp(container) { session = Session.None }
                    is Session.Conductor -> ConductorApp(container, s.info) { session = Session.None }
                }
            }
        }
    }
}
