package cl.conatacoch.colectigo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaColectiGo = lightColorScheme(
    primary = Verde,
    onPrimary = Color.White,
    secondary = VerdeOscuro,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Tinta,
    surface = Color.White,
    onSurface = Tinta,
    outline = Borde
)

@Composable
fun ColectiGoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EsquemaColectiGo,
        typography = Typography,
        content = content
    )
}
