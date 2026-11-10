package cl.conatacoch.colectigo.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudReservation(
    val id: String,
    val passengerName: String,
    val driverName: String,
    val vehicleId: Int,
    val patente: String,
    val origin: String,
    val paradaNombre: String,
    val monto: Int,
    val estado: String,
    val fecha: String,
    val passengerUid: String
)

class ReservationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val reservations = firestore.collection("reservas")

    fun ensureAuthenticated(onComplete: (Exception?) -> Unit) {
        if (auth.currentUser != null) {
            onComplete(null)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    fun observeVehicle(
        vehicleId: Int,
        onReservations: (List<CloudReservation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = observe(
        query = { reservations.whereEqualTo("vehicleId", vehicleId) },
        filter = { it.estado == "PENDIENTE" },
        onReservations = onReservations,
        onError = onError
    )

    fun observePassenger(
        onReservations: (List<CloudReservation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = observe(
        query = {
            val userId = auth.currentUser?.uid
                ?: throw IllegalStateException("No hay una sesión Firebase activa.")
            reservations.whereEqualTo("passengerUid", userId)
        },
        filter = { true },
        onReservations = onReservations,
        onError = onError
    )

    private fun observe(
        query: () -> Query,
        filter: (CloudReservation) -> Boolean,
        onReservations: (List<CloudReservation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val lock = Any()
        var removed = false
        var snapshotRegistration: ListenerRegistration? = null
        lateinit var authListener: FirebaseAuth.AuthStateListener

        fun attachSnapshot() {
            synchronized(lock) {
                if (removed || snapshotRegistration != null || auth.currentUser == null) return
                snapshotRegistration = query()
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            onError(error)
                            return@addSnapshotListener
                        }
                        val rows = snapshot?.documents.orEmpty()
                            .mapNotNull { document ->
                                val data = document.data ?: return@mapNotNull null
                                CloudReservation(
                                    id = document.id,
                                    passengerName = data["passengerName"] as? String ?: "Pasajero",
                                    driverName = data["driverName"] as? String ?: "Por asignar",
                                    vehicleId = (data["vehicleId"] as? Number)?.toInt() ?: 0,
                                    patente = data["patente"] as? String ?: "",
                                    origin = data["origin"] as? String ?: "Ruta ColectiGo",
                                    paradaNombre = data["paradaNombre"] as? String
                                        ?: data["destination"] as? String
                                        ?: "Paradero",
                                    monto = (data["monto"] as? Number)?.toInt() ?: 0,
                                    estado = data["estado"] as? String ?: "PENDIENTE",
                                    fecha = data["fecha"] as? String ?: "",
                                    passengerUid = data["passengerUid"] as? String ?: ""
                                )
                            }
                            .filter(filter)
                            .sortedByDescending { it.fecha }
                        onReservations(rows)
                    }
            }
        }

        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) attachSnapshot()
        }
        auth.addAuthStateListener(authListener)
        attachSnapshot()

        return object : ListenerRegistration {
            override fun remove() {
                synchronized(lock) {
                    removed = true
                    snapshotRegistration?.remove()
                    snapshotRegistration = null
                }
                auth.removeAuthStateListener(authListener)
            }
        }
    }

    fun create(
        passengerName: String,
        driverName: String,
        vehicleId: Int,
        patente: String,
        origin: String,
        paradaNombre: String,
        monto: Int,
        onComplete: (Exception?) -> Unit
    ) {
        ensureAuthenticated { authError ->
            if (authError != null) {
                onComplete(authError)
                return@ensureAuthenticated
            }

            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .format(Date())
            val userId = auth.currentUser?.uid
            if (userId == null) {
                onComplete(IllegalStateException("Firebase no devolvió una sesión autenticada."))
                return@ensureAuthenticated
            }
            val data = mapOf(
                "passengerName" to passengerName,
                "passengerUid" to userId,
                "driverName" to driverName,
                "vehicleId" to vehicleId,
                "patente" to patente,
                "origin" to origin,
                "destination" to paradaNombre,
                "paradaNombre" to paradaNombre,
                "monto" to monto,
                "estado" to "PENDIENTE",
                "fecha" to now,
                "createdAt" to now
            )
            reservations.add(data)
                .addOnSuccessListener { onComplete(null) }
                .addOnFailureListener { onComplete(it) }
        }
    }

    fun updateStatus(
        reservationId: String,
        status: String,
        onComplete: (Exception?) -> Unit
    ) {
        ensureAuthenticated { authError ->
            if (authError != null) {
                onComplete(authError)
                return@ensureAuthenticated
            }
            reservations.document(reservationId)
                .update("estado", status)
                .addOnSuccessListener { onComplete(null) }
                .addOnFailureListener { onComplete(it) }
        }
    }
}
