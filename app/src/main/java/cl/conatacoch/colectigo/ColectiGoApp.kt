package cl.conatacoch.colectigo

import android.app.Application
import android.content.Context
import cl.conatacoch.colectigo.data.ColectiDb
import cl.conatacoch.colectigo.data.DirectionsRepository
import cl.conatacoch.colectigo.data.FleetRepository
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
}

class ColectiGoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
