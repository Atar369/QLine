package hk.hkucs.qline

import android.os.Bundle
import android.content.pm.PackageManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.libraries.places.api.Places
import hk.hkucs.qline.ui.theme.QLineTheme

private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val metadata = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
        val mapsApiKey = metadata?.getString("com.google.android.geo.API_KEY").orEmpty()
        if (mapsApiKey.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, mapsApiKey)
        }
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
        if (user == null) SignInScreen(auth) else QueueHome(user!!.email, user!!.uid, auth)
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
private fun QueueHome(email: String?, uid: String, auth: FirebaseAuth) {
    var membership by remember(uid) { mutableStateOf<StaffMembership?>(null) }
    var membershipLoaded by remember(uid) { mutableStateOf(false) }
    var membershipError by remember(uid) { mutableStateOf<String?>(null) }
    val partners = remember { mutableStateMapOf<String, PartnerRestaurant>() }
    var partnerError by remember { mutableStateOf<String?>(null) }
    val staffRestaurants = remember(uid) { mutableStateMapOf<String, Restaurant>() }
    var selectedRestaurantId by remember(uid) { mutableStateOf<String?>(null) }
    var selectedCustomerRestaurant by remember(uid) { mutableStateOf<Restaurant?>(null) }

    DisposableEffect(uid) {
        val registration = db.collection("staffMemberships").document(uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    membershipError = "Could not verify staff access: ${exception.localizedMessage ?: "try again."}"
                } else {
                    membershipError = null
                    membership = snapshot?.takeIf { it.exists() }?.toObject(StaffMembership::class.java)
                    membershipLoaded = true
                }
            }
        onDispose { registration.remove() }
    }
    DisposableEffect(Unit) {
        val registration = db.collection("partnerRestaurants")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    partnerError = "Could not load partner restaurants: ${exception.localizedMessage ?: "try again."}"
                } else {
                    partnerError = null
                    partners.clear()
                    snapshot?.documents?.forEach { document ->
                        document.toObject(PartnerRestaurant::class.java)?.let { partners[document.id] = it }
                    }
                }
            }
        onDispose { registration.remove() }
    }
    DisposableEffect(membership) {
        staffRestaurants.clear()
        val registrations = membership?.restaurantIds.orEmpty().distinct().map { restaurantId ->
            db.collection("partnerRestaurants").document(restaurantId).addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    partnerError = "Could not load staff restaurant: ${exception.localizedMessage ?: "try again."}"
                } else {
                    val restaurant = snapshot?.takeIf { it.exists() }
                        ?.toObject(PartnerRestaurant::class.java)
                        ?.takeIf { it.active }
                        ?.asRestaurant(restaurantId)
                    if (restaurant == null) staffRestaurants.remove(restaurantId)
                    else staffRestaurants[restaurantId] = restaurant
                    if (selectedRestaurantId !in staffRestaurants.keys) {
                        selectedRestaurantId = staffRestaurants.keys.firstOrNull()
                    }
                }
            }
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark()
                Spacer(Modifier.weight(1f))
                Text(
                    if (email.isNullOrBlank()) "Demo account" else email,
                    Modifier.weight(1f),
                    color = Muted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { auth.signOut() }, contentPadding = ButtonDefaults.ContentPadding) {
                    Text("Sign out", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!membershipLoaded && membershipError == null) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (membershipError != null) {
                Text(membershipError!!, color = MaterialTheme.colorScheme.error)
            } else if (membership != null) {
                Text("Approved staff account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Staff tools are limited to restaurants assigned by QLine.", color = Muted)
                if (staffRestaurants.isEmpty() && partnerError == null) {
                    Text("No active partner restaurant is assigned to this account.", color = Muted)
                } else {
                    val available = staffRestaurants.values.toList()
                    if (available.size > 1) {
                        available.forEach { restaurant ->
                            OutlinedButton(
                                onClick = { selectedRestaurantId = restaurant.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (selectedRestaurantId == restaurant.id) "Selected · ${restaurant.name}"
                                    else restaurant.name
                                )
                            }
                        }
                    }
                    val selectedStaffRestaurant = selectedRestaurantId?.let { staffRestaurants[it] }
                    if (selectedStaffRestaurant != null) StaffPanel(selectedStaffRestaurant)
                }
            } else {
                Text("Explore restaurants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                partnerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                NearbyRestaurantsMap(partners) { selectedCustomerRestaurant = it }
                selectedCustomerRestaurant?.let { restaurant ->
                    RestaurantDetails(restaurant) {
                        if (restaurant.isPartner) CustomerPanel(restaurant)
                        QueueReportsPanel(restaurant)
                    }
                }
            }
            partnerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Live updates • Official queues are managed by partner staff",
                Modifier.align(Alignment.CenterHorizontally),
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RestaurantDetails(restaurant: Restaurant, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(restaurant.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(restaurant.cuisine, color = Muted)
                Text(restaurant.address, color = Muted)
                Text(
                    if (restaurant.isPartner) "Official QLine partner" else "Community queue reports only",
                    color = Teal,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        content()
    }
}

@Composable
private fun CustomerPanel(restaurant: Restaurant) {
    var queue by remember(restaurant.id) { mutableStateOf<Queue?>(null) }
    var mine by remember(restaurant.id) { mutableStateOf<Ticket?>(null) }
    var queueError by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var ticketError by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var status by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var busy by remember(restaurant.id) { mutableStateOf(false) }
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid ?: return

    DisposableEffect(restaurant.id, uid) {
        val queueReg = db.collection("queues").document(restaurant.id).addSnapshotListener { snap, exception ->
            if (exception != null) {
                queueError = "Could not load queue: ${exception.localizedMessage ?: "try again."}"
            } else {
                queue = snap?.toObject(Queue::class.java)
                queueError = null
            }
        }
        val ticketReg = db.collection("tickets").document("${restaurant.id}_$uid")
            .addSnapshotListener { snap, exception ->
                if (exception != null) {
                    ticketError = "Could not load your ticket: ${exception.localizedMessage ?: "try again."}"
                } else {
                    mine = snap?.takeIf { it.exists() }?.toObject(Ticket::class.java)
                        ?.takeIf { it.status in ACTIVE_STATUSES }
                    ticketError = null
                }
            }
        onDispose { queueReg.remove(); ticketReg.remove() }
    }
    val current = queue?.currentTicketNumber ?: 0L
    val ahead = mine?.let { queue?.peopleAheadOf(it.number) ?: 0 } ?: 0
    val eta = ahead * (queue?.minutesPerPerson ?: 5)
    val isOpen = queue?.isOpen ?: true
    val ticket = mine

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        queueError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ticketError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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

        if (ticket != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (ticket.status == Ticket.CALLED || ticket.number == current) "It’s your turn" else "You’re in line",
                            color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (ticket.status == Ticket.CALLED || ticket.number == current) "Please make your way to the desk."
                            else "$ahead ${if (ahead == 1) "person" else "people"} ahead · about $eta min",
                            color = Ink
                        )
                    }
                    Button(
                        onClick = {
                            busy = true; status = null
                            cancelTicket(restaurant, uid) { message -> busy = false; status = message }
                        },
                        enabled = !busy && ticket.status == Ticket.WAITING,
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
    var queueError by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var ticketError by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var busy by remember(restaurant.id) { mutableStateOf(false) }
    DisposableEffect(restaurant.id) {
        val queueReg = db.collection("queues").document(restaurant.id).addSnapshotListener { snap, exception ->
            if (exception != null) {
                queueError = "Could not load queue: ${exception.localizedMessage ?: "try again."}"
            } else {
                queue = snap?.toObject(Queue::class.java)
                queueError = null
            }
        }
        val ticketsReg = db.collection("tickets").whereEqualTo("queueId", restaurant.id)
            .addSnapshotListener { snap, exception ->
                if (exception != null) {
                    ticketError = "Could not load tickets: ${exception.localizedMessage ?: "try again."}"
                } else {
                    tickets.clear()
                    snap?.documents?.mapNotNullTo(tickets) { it.toObject(Ticket::class.java) }
                    ticketError = null
                }
            }
        onDispose { queueReg.remove(); ticketsReg.remove() }
    }
    val current = queue?.currentTicketNumber ?: 0L
    val serving = tickets.firstOrNull { it.status == Ticket.CALLED }
    val next = tickets.filter { it.status == Ticket.WAITING && it.number > current }.minByOrNull { it.number }
    val waitingCount = tickets.count { it.status == Ticket.WAITING }
    val isOpen = queue?.isOpen ?: true

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        queueError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ticketError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
        Text("This account is provisioned for this QLine partner.", color = Muted, style = MaterialTheme.typography.bodySmall)
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
            transaction.update(
                queueRef,
                mapOf(
                    "nextTicketNumber" to number + 1,
                    "waitingNumbers" to queue.waitingNumbers + number
                )
            )
        } else {
            transaction.set(
                queueRef,
                Queue.seed(restaurant.name).copy(
                    nextTicketNumber = number + 1,
                    waitingNumbers = listOf(number)
                ).toMap()
            )
        }
        transaction.set(ticketRef, Ticket(restaurant.id, number, uid, Ticket.WAITING).toMap())
        TakeTicketResult(number, false)
    }.addOnSuccessListener { callback(it, null) }
        .addOnFailureListener { callback(null, "Could not take a ticket: ${it.localizedMessage ?: "try again."}") }
}

private fun cancelTicket(restaurant: Restaurant, uid: String, callback: (String) -> Unit) {
    val queueRef = db.collection("queues").document(restaurant.id)
    val ticketRef = db.collection("tickets").document("${restaurant.id}_$uid")
    db.runTransaction { transaction ->
        val queueSnapshot = transaction.get(queueRef)
        val snapshot = transaction.get(ticketRef)
        val ticket = snapshot.toObject(Ticket::class.java)
        if (ticket?.status == Ticket.WAITING) {
            val queue = queueSnapshot.toObject(Queue::class.java) ?: Queue.seed(restaurant.name)
            transaction.update(ticketRef, "status", Ticket.CANCELLED)
            transaction.update(queueRef, "waitingNumbers", queue.waitingNumbers.filterNot { it == ticket.number })
        }
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
                        nextTicketNumber = if (nextStillWaiting) maxOf(queue.nextTicketNumber, next.number + 1) else queue.nextTicketNumber,
                        waitingNumbers = if (nextStillWaiting) queue.waitingNumbers.filterNot { it == next.number } else queue.waitingNumbers
                    ).toMap()
                )
            } else if (nextStillWaiting) {
                transaction.update(
                    queueRef,
                    mapOf(
                        "currentTicketNumber" to next.number,
                        "waitingNumbers" to queue.waitingNumbers.filterNot { it == next.number }
                    )
                )
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
