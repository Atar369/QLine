package hk.hkucs.qline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import hk.hkucs.qline.ui.theme.QLineTheme

private data class Restaurant(val id: String, val name: String, val cuisine: String)

private val restaurants = listOf(
    Restaurant("harbour-grill", "Harbour Grill", "Cantonese barbecue"),
    Restaurant("central-noodles", "Central Noodles", "Noodles and dumplings"),
    Restaurant("garden-table", "Garden Table", "Modern cafe")
)

private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QLineTheme(dynamicColor = false) {
                QLineApp()
            }
        }
    }
}

@Composable
private fun QLineApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (user == null) SignInScreen(auth) else QueueHome(user!!.email, auth)
    }
}

@Composable
private fun SignInScreen(auth: FirebaseAuth) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark()
        Spacer(Modifier.height(28.dp))
        Text("Your table, without the wait.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Join a restaurant queue and know when your table is ready.", color = Muted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; error = null },
            modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(), label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.length < 6) {
                    error = "Enter an email and a password with at least 6 characters."
                } else {
                    busy = true
                    val task = if (isSignUp) auth.createUserWithEmailAndPassword(email.trim(), password)
                    else auth.signInWithEmailAndPassword(email.trim(), password)
                    task.addOnCompleteListener {
                        busy = false
                        if (!it.isSuccessful) error = it.exception?.localizedMessage ?: "Could not sign in."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (isSignUp) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { isSignUp = !isSignUp; error = null },
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isSignUp) "Already have an account? Sign in" else "New to QLine? Create an account")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                busy = true
                auth.signInAnonymously().addOnCompleteListener {
                    busy = false
                    if (!it.isSuccessful) {
                        error = "Demo sign-in is unavailable. Enable Anonymous authentication in Firebase."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !busy,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
        ) { Text("Continue as demo") }
        Spacer(Modifier.height(20.dp))
        Text("Your place in line updates live.", Modifier.align(Alignment.CenterHorizontally), color = Muted)
    }
}

@Composable
private fun QueueHome(email: String?, auth: FirebaseAuth) {
    var selectedTab by remember { mutableStateOf(0) }
    var restaurantId by remember { mutableStateOf(restaurants.first().id) }
    val restaurant = restaurants.first { it.id == restaurantId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (email.isNullOrBlank()) "Demo account" else email,
                        Modifier.weight(1f),
                        color = Muted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { auth.signOut() }, contentPadding = ButtonDefaults.ContentPadding) {
                        Text("Sign out", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    listOf("Customer", "Staff").forEachIndexed { index, label ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(if (selectedTab == 0) "Choose a restaurant" else "Restaurant desk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                restaurants.forEach { option ->
                    FilterChip(
                        selected = restaurantId == option.id,
                        onClick = { restaurantId = option.id },
                        label = { Text(option.name) }
                    )
                }
            }
            if (selectedTab == 0) CustomerPanel(restaurant)
            else StaffPanel(restaurant)
            Text("Live updates • Queue data syncs across devices", Modifier.align(Alignment.CenterHorizontally), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CustomerPanel(restaurant: Restaurant) {
    var queue by remember(restaurant.id) { mutableStateOf<Queue?>(null) }
    val tickets = remember(restaurant.id) { mutableStateListOf<Ticket>() }
    var status by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var busy by remember(restaurant.id) { mutableStateOf(false) }
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid ?: return

    DisposableEffect(restaurant.id) {
        val queueReg = db.collection("queues").document(restaurant.id).addSnapshotListener { snap, _ ->
            queue = snap?.toObject(Queue::class.java)
        }
        val ticketsReg = db.collection("tickets").whereEqualTo("queueId", restaurant.id)
            .addSnapshotListener { snap, _ ->
                tickets.clear()
                snap?.documents?.mapNotNullTo(tickets) { it.toObject(Ticket::class.java) }
            }
        onDispose { queueReg.remove(); ticketsReg.remove() }
    }
    val mine = tickets.firstOrNull { it.userId == uid && it.status in ACTIVE_STATUSES }
    val current = queue?.currentTicketNumber ?: 0L
    val ahead = mine?.let { ticket ->
        tickets.count { it.status == Ticket.WAITING && it.number in (current + 1) until ticket.number }
    } ?: 0
    val eta = ahead * (queue?.minutesPerPerson ?: 5)
    val isOpen = queue?.isOpen ?: true

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Ink),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(restaurant.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(restaurant.cuisine, color = Color(0xFFB7C7D8))
                Spacer(Modifier.height(22.dp))
                Text("NOW SERVING", color = Color(0xFFB7C7D8), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(3.dp))
                Text(numberLabel(current), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("YOUR NUMBER", mine?.let { numberLabel(it.number) } ?: "—", Modifier.weight(1f), true)
                    MetricCard("AHEAD OF YOU", mine?.let { ahead.toString() } ?: "—", Modifier.weight(1f), true)
                }
            }
        }

        if (mine != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (mine.status == Ticket.CALLED || mine.number == current) "It’s your turn" else "You’re in line",
                            color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (mine.status == Ticket.CALLED || mine.number == current) "Please make your way to the desk."
                            else "$ahead ${if (ahead == 1) "person" else "people"} ahead · about $eta min",
                            color = Ink
                        )
                    }
                    Button(
                        onClick = {
                            busy = true; status = null
                            cancelTicket(restaurant, uid) { message -> busy = false; status = message }
                        },
                        enabled = !busy && mine.status == Ticket.WAITING,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
                    ) { Text(if (busy) "Please wait" else "Cancel") }
                }
            }
        } else {
            Text(
                if (isOpen) "Join the queue to save your place. We’ll keep your estimate up to date."
                else "This restaurant has paused its queue for now.",
                color = Muted
            )
            Button(
                onClick = {
                    busy = true; status = null
                    takeTicket(restaurant, uid) { result, message ->
                        busy = false
                        status = message ?: if (result?.alreadyActive == true) "You already have an active ticket here." else "Your ticket is ready."
                    }
                },
                enabled = !busy && isOpen,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (isOpen) "Join the queue" else "Queue paused", fontWeight = FontWeight.SemiBold)
            }
        }
        if (status != null) Text(status!!, color = if (status!!.startsWith("Could")) MaterialTheme.colorScheme.error else Color(0xFF32755C))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Enjoy your time nearby", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("Wait anywhere nearby and keep QLine open to see your live position.", color = Muted)
            }
        }
    }
}

@Composable
private fun StaffPanel(restaurant: Restaurant) {
    var queue by remember(restaurant.id) { mutableStateOf<Queue?>(null) }
    val tickets = remember(restaurant.id) { mutableStateListOf<Ticket>() }
    var message by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var busy by remember(restaurant.id) { mutableStateOf(false) }
    DisposableEffect(restaurant.id) {
        val queueReg = db.collection("queues").document(restaurant.id).addSnapshotListener { snap, _ ->
            queue = snap?.toObject(Queue::class.java)
        }
        val ticketsReg = db.collection("tickets").whereEqualTo("queueId", restaurant.id)
            .addSnapshotListener { snap, _ ->
                tickets.clear()
                snap?.documents?.mapNotNullTo(tickets) { it.toObject(Ticket::class.java) }
            }
        onDispose { queueReg.remove(); ticketsReg.remove() }
    }
    val current = queue?.currentTicketNumber ?: 0L
    val serving = tickets.firstOrNull { it.status == Ticket.CALLED }
    val next = tickets.filter { it.status == Ticket.WAITING && it.number > current }.minByOrNull { it.number }
    val waitingCount = tickets.count { it.status == Ticket.WAITING }
    val isOpen = queue?.isOpen ?: true

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(restaurant.name, color = Muted, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Text(if (isOpen) "Accepting guests" else "Queue paused", color = if (isOpen) Teal else Muted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("Current ticket", color = Muted)
                Text(serving?.let { numberLabel(it.number) } ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Ink)
                HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Up next", color = Muted)
                        Text(next?.let { numberLabel(it.number) } ?: "No one waiting", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text("$waitingCount waiting", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Button(
            onClick = {
                busy = true; message = null
                callNext(restaurant) { called, error ->
                    busy = false
                    message = error ?: if (called) "Next ticket called." else "The queue is clear."
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(if (busy) "Updating…" else "Call next", fontWeight = FontWeight.SemiBold) }
        OutlinedButton(
            onClick = {
                busy = true; message = null
                setQueueOpen(restaurant, !isOpen) { error ->
                    busy = false
                    message = error ?: if (isOpen) "Queue paused." else "Queue is open."
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(if (isOpen) "Pause queue" else "Open queue") }
        OutlinedButton(
            onClick = {
                busy = true; message = null
                resetQueue(restaurant) { error ->
                    busy = false
                    message = error ?: "Queue reset."
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Reset queue") }
        if (message != null) Text(message!!, color = Muted)
        Text("Staff tools are open for presentation and demo use.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = Teal) {
            BoxCenter { Text("Q", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
        }
        Text("QLine", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxCenter(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, content = { content() })
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(
        modifier.background(if (dark) Color(0xFF203950) else Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, color = if (dark) Color(0xFFB7C7D8) else Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = if (dark) Color.White else Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private data class TakeTicketResult(val number: Long, val alreadyActive: Boolean)

private val ACTIVE_STATUSES = setOf(Ticket.WAITING, Ticket.CALLED)

private fun takeTicket(restaurant: Restaurant, uid: String, callback: (TakeTicketResult?, String?) -> Unit) {
    val queueRef = db.collection("queues").document(restaurant.id)
    val ticketRef = db.collection("tickets").document("${restaurant.id}_$uid")
    db.runTransaction { transaction ->
        val queueSnapshot = transaction.get(queueRef)
        val ticketSnapshot = transaction.get(ticketRef)
        val existing = ticketSnapshot.toObject(Ticket::class.java)
        if (existing != null && existing.status in ACTIVE_STATUSES) {
            return@runTransaction TakeTicketResult(existing.number, true)
        }
        val queue = queueSnapshot.toObject(Queue::class.java) ?: Queue.seed(restaurant.name)
        if (!queue.isOpen) throw IllegalStateException("This restaurant has paused its queue.")
        val number = queue.nextTicketNumber
        if (queueSnapshot.exists()) {
            transaction.update(queueRef, "nextTicketNumber", number + 1)
        } else {
            transaction.set(
                queueRef,
                Queue.seed(restaurant.name).copy(nextTicketNumber = number + 1).toMap()
            )
        }
        transaction.set(ticketRef, Ticket(restaurant.id, number, uid, Ticket.WAITING).toMap())
        TakeTicketResult(number, false)
    }.addOnSuccessListener { callback(it, null) }
        .addOnFailureListener { callback(null, "Could not take a ticket: ${it.localizedMessage ?: "try again."}") }
}

private fun cancelTicket(restaurant: Restaurant, uid: String, callback: (String) -> Unit) {
    val ticketRef = db.collection("tickets").document("${restaurant.id}_$uid")
    db.runTransaction { transaction ->
        val snapshot = transaction.get(ticketRef)
        val ticket = snapshot.toObject(Ticket::class.java)
        if (ticket?.status == Ticket.WAITING) transaction.update(ticketRef, "status", Ticket.CANCELLED)
        null
    }.addOnSuccessListener { callback("Ticket cancelled.") }
        .addOnFailureListener { callback("Could not cancel: ${it.localizedMessage ?: "try again."}") }
}

private fun callNext(restaurant: Restaurant, callback: (Boolean, String?) -> Unit) {
    val queueRef = db.collection("queues").document(restaurant.id)
    val ticketQuery = db.collection("tickets").whereEqualTo("queueId", restaurant.id)
    ticketQuery.get().addOnSuccessListener { snapshot ->
        val tickets = snapshot.documents.mapNotNull { it.toObject(Ticket::class.java) }
        val called = tickets.firstOrNull { it.status == Ticket.CALLED }
        val next = tickets.filter { it.status == Ticket.WAITING }.minByOrNull { it.number }
        val calledRef = called?.let { db.collection("tickets").document("${it.queueId}_${it.userId}") }
        val nextRef = next?.let { db.collection("tickets").document("${it.queueId}_${it.userId}") }
        db.runTransaction { transaction ->
            val queueSnapshot = transaction.get(queueRef)
            val nextSnapshot = nextRef?.let(transaction::get)
            val calledSnapshot = calledRef?.let(transaction::get)
            val queue = queueSnapshot.toObject(Queue::class.java) ?: Queue.seed(restaurant.name)
            val nextStillWaiting = nextSnapshot?.toObject(Ticket::class.java)?.let {
                it.status == Ticket.WAITING && it.number > queue.currentTicketNumber
            } ?: false
            val calledStillActive = calledSnapshot?.toObject(Ticket::class.java)?.status == Ticket.CALLED
            if (calledStillActive) transaction.update(calledRef, "status", Ticket.DONE)
            if (nextStillWaiting) {
                transaction.update(nextRef, "status", Ticket.CALLED)
            }
            if (!queueSnapshot.exists()) {
                transaction.set(
                    queueRef,
                    Queue.seed(restaurant.name).copy(
                        currentTicketNumber = if (nextStillWaiting) next.number else queue.currentTicketNumber,
                        nextTicketNumber = if (nextStillWaiting) maxOf(queue.nextTicketNumber, next.number + 1) else queue.nextTicketNumber
                    ).toMap()
                )
            } else if (nextStillWaiting) {
                transaction.update(queueRef, "currentTicketNumber", next.number)
            }
            nextStillWaiting
        }.addOnSuccessListener { callback(it, null) }
            .addOnFailureListener { callback(false, "Could not update queue: ${it.localizedMessage ?: "try again."}") }
    }.addOnFailureListener { callback(false, "Could not load queue: ${it.localizedMessage ?: "try again."}") }
}

private fun resetQueue(restaurant: Restaurant, callback: (String?) -> Unit) {
    val queueRef = db.collection("queues").document(restaurant.id)
    val ticketQuery = db.collection("tickets").whereEqualTo("queueId", restaurant.id)
    ticketQuery.get().addOnSuccessListener { snapshot ->
        if (snapshot.size() > 499) {
            callback("Could not reset queue: too many tickets to reset in one operation.")
            return@addOnSuccessListener
        }
        val batch = db.batch()
        snapshot.documents.forEach { batch.update(it.reference, "status", Ticket.CANCELLED) }
        batch.set(queueRef, Queue.seed(restaurant.name).toMap())
        batch.commit().addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback("Could not reset queue: ${it.localizedMessage ?: "try again."}") }
    }.addOnFailureListener { callback("Could not load queue: ${it.localizedMessage ?: "try again."}") }
}

private fun setQueueOpen(restaurant: Restaurant, isOpen: Boolean, callback: (String?) -> Unit) {
    val queueRef = db.collection("queues").document(restaurant.id)
    db.runTransaction { transaction ->
        val snapshot = transaction.get(queueRef)
        if (snapshot.exists()) {
            transaction.update(queueRef, "isOpen", isOpen)
        } else {
            transaction.set(queueRef, Queue.seed(restaurant.name).copy(isOpen = isOpen).toMap())
        }
        null
    }.addOnSuccessListener { callback(null) }
        .addOnFailureListener { callback("Could not update queue: ${it.localizedMessage ?: "try again."}") }
}

private fun numberLabel(number: Long) = if (number == 0L) "—" else number.toString().padStart(3, '0')

private val Ink = Color(0xFF163149)
private val Muted = Color(0xFF718293)
private val Teal = Color(0xFF168C83)
private val Mint = Color(0xFFDDF3EB)
private val Line = Color(0xFFE7EDF1)
