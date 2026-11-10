package cl.conatacoch.colectigo

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import cl.conatacoch.colectigo.data.ColectiDb
import cl.conatacoch.colectigo.data.DirectionsRepository
import cl.conatacoch.colectigo.data.FleetRepository
import cl.conatacoch.colectigo.data.ReservationRepository
import cl.conatacoch.colectigo.location.LocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Contenedor simple de dependencias (inyección manual, sin librerías extra). */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = ColectiDb(context)
    val directions = DirectionsRepository(BuildConfig.MAPS_API_KEY)
    val location = LocationTracker(context)
    val fleet = FleetRepository(db, directions, scope)
    val reservations = ReservationRepository()
}

class ColectiGoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.d("ColectiGoApp", "🔥 Firebase inicializado correctamente. Proyecto: ${FirebaseApp.getInstance().name}")
        } catch (e: Exception) {
            Log.e("ColectiGoApp", "Error al inicializar Firebase", e)
        }
        container = AppContainer(this)
        container.reservations.ensureAuthenticated { error ->
            if (error != null) Log.e("ColectiGoApp", "Error al autenticar la sesión de demostración", error)
        }
    }
}
