package cl.conatacoch.colectigo.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudMessage(
    val id: String,
    val audience: String,
    val content: String,
    val senderName: String,
    val senderRole: String,
    val senderUid: String,
    val createdAt: String
)

class MessageRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val messages = firestore.collection("mensajes")
    val currentUid: String?
        get() = auth.currentUser?.uid

    fun observe(
        role: String,
        onMessages: (List<CloudMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val lock = Any()
        val registrations = mutableListOf<ListenerRegistration>()
        val snapshots = mutableListOf<Map<String, CloudMessage>>()
        var removed = false

        fun attach() {
            synchronized(lock) {
                if (removed || registrations.isNotEmpty()) return
                val uid = auth.currentUser?.uid ?: return
                val queries: List<Query> = listOf(
                    messages.whereEqualTo("audience", role),
                    messages.whereEqualTo("audience", "TODOS"),
                    messages.whereEqualTo("senderUid", uid)
                )
                repeat(queries.size) { snapshots.add(emptyMap()) }
                queries.forEachIndexed { index, query ->
                    registrations.add(
                        query.addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                onError(error)
                                return@addSnapshotListener
                            }
                            val rows = snapshot?.documents.orEmpty().mapNotNull { document ->
                                val data = document.data ?: return@mapNotNull null
                                CloudMessage(
                                    id = document.id,
                                    audience = data["audience"] as? String ?: "TODOS",
                                    content = data["content"] as? String ?: "",
                                    senderName = data["senderName"] as? String ?: "ColectiGo",
                                    senderRole = data["senderRole"] as? String ?: "ADMIN",
                                    senderUid = data["senderUid"] as? String ?: "",
                                    createdAt = when (val createdAt = data["createdAt"]) {
                                        is Timestamp -> format(createdAt.toDate())
                                        is String -> format(createdAt)
                                        else -> ""
                                    }
                                )
                            }.associateBy { it.id }
                            synchronized(lock) {
                                if (removed) return@addSnapshotListener
                                snapshots[index] = rows
                                onMessages(
                                    snapshots.flatMap { it.values }
                                        .distinctBy { it.id }
                                        .sortedBy { it.createdAt }
                                )
                            }
                        }
                    )
                }
            }
        }

        val authListener = FirebaseAuth.AuthStateListener {
            if (it.currentUser != null) attach()
        }
        auth.addAuthStateListener(authListener)
        attach()

        return object : ListenerRegistration {
            override fun remove() {
                synchronized(lock) {
                    removed = true
                    registrations.forEach { it.remove() }
                    registrations.clear()
                }
                auth.removeAuthStateListener(authListener)
            }
        }
    }

    fun send(
        role: String,
        senderName: String,
        content: String,
        onComplete: (Exception?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(IllegalStateException("No hay una sesión Firebase activa."))
            return
        }
        val data = mapOf(
            "audience" to "ADMIN",
            "content" to content.trim(),
            "senderName" to senderName,
            "senderRole" to role,
            "senderUid" to user.uid,
            "createdAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .format(Date())
        )
        messages.add(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { onComplete(it) }
    }

    private fun format(date: Date): String =
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(date)

    private fun format(value: String): String {
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(value)
        }.getOrNull() ?: return value
        return format(parsed)
    }
}
