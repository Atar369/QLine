package hk.hkucs.qline

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

private const val MAX_REPORT_ESTIMATE = 10_000L
private const val MAX_LOCATION_AGE_MILLIS = 2 * 60 * 1000L

private class QueueReportCooldownException(val minutesRemaining: Long) : Exception()

@Composable
fun QueueReportsPanel(restaurant: Restaurant) {
    val context = LocalContext.current
    val reports = remember(restaurant.id) { mutableStateListOf<QueueReport>() }
    var reportLoadError by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var peopleEstimate by remember(restaurant.id) { mutableStateOf("") }
    var waitEstimate by remember(restaurant.id) { mutableStateOf("") }
    var uploadBusy by remember(restaurant.id) { mutableStateOf(false) }
    var message by remember(restaurant.id) { mutableStateOf<String?>(null) }
    val locationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun shareReport(uid: String) {
        val reportId = UUID.randomUUID().toString()
        val firestore = FirebaseFirestore.getInstance()
        val reportRef = firestore.collection("queueReports").document(reportId)
        val rateLimitRef = firestore.collection("queueReportRateLimits")
            .document(uid)
            .collection("restaurants")
            .document(restaurant.id)
        val report = QueueReport(
            restaurantId = restaurant.id,
            userId = uid,
            estimatedPeople = peopleEstimate.toLongOrNull(),
            waitMinutes = waitEstimate.toLongOrNull()
        )

        firestore.runTransaction { transaction ->
            val rateLimit = transaction.get(rateLimitRef)
            val lastReportedAt = rateLimit.getTimestamp("lastReportedAt")
            if (lastReportedAt != null) {
                val remainingMillis =
                    QUEUE_REPORT_COOLDOWN_MILLIS -
                        (System.currentTimeMillis() - lastReportedAt.toDate().time)
                if (remainingMillis > 0) {
                    throw QueueReportCooldownException(
                        (remainingMillis + 59_999L) / 60_000L
                    )
                }
            }

            transaction.set(
                reportRef,
                report.toMap() + ("createdAt" to FieldValue.serverTimestamp())
            )
            transaction.set(
                rateLimitRef,
                mapOf(
                    "reportId" to reportId,
                    "lastReportedAt" to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener {
            uploadBusy = false
            peopleEstimate = ""
            waitEstimate = ""
            message = "Queue report shared."
        }.addOnFailureListener { exception ->
            uploadBusy = false
            message = if (exception is QueueReportCooldownException) {
                "You can report this restaurant again in about ${exception.minutesRemaining} minutes."
            } else {
                "Could not save your report: ${exception.localizedMessage ?: "try again."}"
            }
        }
    }

    fun verifyLocationAndShare() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            uploadBusy = false
            message = "Allow location access to share a report."
            return
        }

        try {
            locationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location == null) {
                    uploadBusy = false
                    message = "Could not verify your location. Turn on device location and try again."
                    return@addOnSuccessListener
                }

                val locationAgeMillis = System.currentTimeMillis() - location.time
                if (locationAgeMillis !in 0L..MAX_LOCATION_AGE_MILLIS) {
                    uploadBusy = false
                    message = "Your location is out of date. Try sharing again."
                    return@addOnSuccessListener
                }
                if (!location.hasAccuracy() ||
                    !location.accuracy.isFinite() ||
                    location.accuracy > MAX_QUEUE_REPORT_LOCATION_ACCURACY_METERS
                ) {
                    uploadBusy = false
                    message = "Turn on precise location to verify you are near the restaurant."
                    return@addOnSuccessListener
                }

                val distance = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    restaurant.latitude,
                    restaurant.longitude,
                    distance
                )
                if (!isWithinQueueReportRadius(distance[0].toDouble(), location.accuracy)) {
                    uploadBusy = false
                    message = "You must be within 250 m of the restaurant to share a report."
                    return@addOnSuccessListener
                }

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    uploadBusy = false
                    message = "Sign in before sharing a queue report."
                } else {
                    shareReport(uid)
                }
            }.addOnFailureListener { exception ->
                uploadBusy = false
                message = "Could not verify your location: ${exception.localizedMessage ?: "try again."}"
            }
        } catch (_: SecurityException) {
            uploadBusy = false
            message = "Location permission is needed to share a report."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            verifyLocationAndShare()
        } else {
            uploadBusy = false
            message = "Allow location access to share a report."
        }
    }

    DisposableEffect(restaurant.id) {
        val registration = FirebaseFirestore.getInstance().collection("queueReports")
            .whereEqualTo("restaurantId", restaurant.id)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    reportLoadError = "Could not load queue reports: ${exception.localizedMessage ?: "try again."}"
                } else {
                    reportLoadError = null
                    reports.clear()
                    snapshot?.documents?.mapNotNullTo(reports) {
                        it.toObject(QueueReport::class.java)
                    }
                }
            }
        onDispose { registration.remove() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Community queue reports", style = MaterialTheme.typography.titleMedium)
        Text(
            if (restaurant.isPartner) {
                "The live queue is managed by this partner. Share an on-site estimate for extra context."
            } else {
                "Reports are customer-submitted estimates, not an official queue. You must be near the restaurant to report."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = peopleEstimate,
                onValueChange = { peopleEstimate = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.weight(1f),
                label = { Text("People in line") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = waitEstimate,
                onValueChange = { waitEstimate = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.weight(1f),
                label = { Text("Wait (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Button(
            onClick = {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                when {
                    uid == null -> message = "Sign in before sharing a queue report."
                    peopleEstimate.isBlank() && waitEstimate.isBlank() ->
                        message = "Enter the people in line, the wait estimate, or both."
                    (peopleEstimate.toLongOrNull() ?: 0L) > MAX_REPORT_ESTIMATE ||
                        (waitEstimate.toLongOrNull() ?: 0L) > MAX_REPORT_ESTIMATE ->
                        message = "Estimates must be 10,000 or less."
                    else -> {
                        uploadBusy = true
                        message = null
                        val hasFineLocation = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCoarseLocation = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasFineLocation || hasCoarseLocation) {
                            verifyLocationAndShare()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                }
            },
            enabled = !uploadBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uploadBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Share queue report")
        }
        Text(
            "Text-only reports require location within 250 m. One report per restaurant every 15 minutes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        reportLoadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (reports.isEmpty() && reportLoadError == null) {
            Text("No customer reports yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        reports.forEach { report ->
            QueueReportCard(report)
        }
    }
}

@Composable
private fun QueueReportCard(report: QueueReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val estimates = listOfNotNull(
                report.estimatedPeople?.let { "$it people" },
                report.waitMinutes?.let { "$it min estimated wait" }
            )
            Text(
                estimates.joinToString(" · ").ifEmpty { "No wait estimate provided" },
                style = MaterialTheme.typography.titleSmall
            )
            report.createdAt?.let { timestamp ->
                val time = DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                val isStale = System.currentTimeMillis() - timestamp.toDate().time > 2 * 60 * 60 * 1000L
                Text(
                    if (isStale) "Older report · $time" else "Reported $time",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } ?: Text("Just reported", style = MaterialTheme.typography.bodySmall)
        }
    }
}
