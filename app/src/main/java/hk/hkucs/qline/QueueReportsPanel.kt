package hk.hkucs.qline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.nio.ByteBuffer
import java.io.IOException
import java.util.UUID

private const val MAX_REPORT_IMAGE_BYTES = 5L * 1024L * 1024L
private const val MAX_REPORT_IMAGE_DIMENSION = 1200

@Composable
fun QueueReportsPanel(restaurant: Restaurant) {
    val context = LocalContext.current
    val reports = remember(restaurant.id) { mutableStateListOf<QueueReport>() }
    var reportLoadError by remember(restaurant.id) { mutableStateOf<String?>(null) }
    var selectedImage by remember(restaurant.id) { mutableStateOf<android.net.Uri?>(null) }
    var peopleEstimate by remember(restaurant.id) { mutableStateOf("") }
    var waitEstimate by remember(restaurant.id) { mutableStateOf("") }
    var uploadBusy by remember(restaurant.id) { mutableStateOf(false) }
    var message by remember(restaurant.id) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImage = uri
        message = null
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
                "The live queue is managed by this partner. Share a photo to give other guests extra context."
            } else {
                "This restaurant is not a QLine partner. Reports are customer-submitted estimates, not an official queue."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { picker.launch("image/*") }, enabled = !uploadBusy) {
            Text(if (selectedImage == null) "Choose queue photo" else "Choose a different photo")
        }
        selectedImage?.let {
            Text("Photo selected", color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = peopleEstimate,
                onValueChange = { peopleEstimate = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                label = { Text("People in line") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = waitEstimate,
                onValueChange = { waitEstimate = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                label = { Text("Wait (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Button(
            onClick = {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val imageUri = selectedImage
                val mimeType = imageUri?.let(context.contentResolver::getType)
                val supportedImageType = mimeType in setOf("image/jpeg", "image/png", "image/webp") ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mimeType in setOf("image/heic", "image/heif"))
                if (uid == null) {
                    message = "Sign in before sharing a queue report."
                } else if (imageUri == null) {
                    message = "Choose a queue photo first."
                } else if (!supportedImageType) {
                    message = "Choose a JPEG, PNG, or WebP image (HEIC is supported on Android 9 and newer)."
                } else if ((peopleEstimate.toLongOrNull() ?: 0L) > 10000L ||
                    (waitEstimate.toLongOrNull() ?: 0L) > 10000L
                ) {
                    message = "Estimates must be 10,000 or less."
                } else {
                    uploadBusy = true
                    message = null
                    val reportId = UUID.randomUUID().toString()
                    val storagePath = "queue-reports/${restaurant.id}/$uid/$reportId"
                    val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)
                    val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
                    storageRef.putFile(imageUri, metadata)
                        .addOnSuccessListener {
                            val report = QueueReport(
                                restaurantId = restaurant.id,
                                userId = uid,
                                storagePath = storagePath,
                                estimatedPeople = peopleEstimate.toLongOrNull(),
                                waitMinutes = waitEstimate.toLongOrNull()
                            )
                            FirebaseFirestore.getInstance().collection("queueReports")
                                .document(reportId)
                                .set(report.toMap() + ("createdAt" to FieldValue.serverTimestamp()))
                                .addOnSuccessListener {
                                    uploadBusy = false
                                    selectedImage = null
                                    peopleEstimate = ""
                                    waitEstimate = ""
                                    message = "Queue report shared."
                                }
                                .addOnFailureListener { exception ->
                                    storageRef.delete().addOnCompleteListener { cleanup ->
                                        uploadBusy = false
                                        val cleanupNotice = if (cleanup.isSuccessful) "" else
                                            " The uploaded photo could not be removed."
                                        message =
                                            "Could not save your report: ${exception.localizedMessage ?: "try again."}$cleanupNotice"
                                    }
                                }
                        }
                        .addOnFailureListener { exception ->
                            uploadBusy = false
                            message = "Could not upload the photo: ${exception.localizedMessage ?: "check Firebase Storage setup."}"
                        }
                }
            },
            enabled = !uploadBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uploadBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Share queue report")
        }
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
    var bitmap by remember(report.storagePath) { mutableStateOf<Bitmap?>(null) }
    var imageError by remember(report.storagePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(report.storagePath) {
        FirebaseStorage.getInstance().reference.child(report.storagePath)
            .getBytes(MAX_REPORT_IMAGE_BYTES)
            .addOnSuccessListener { bytes ->
                bitmap = decodeReportImage(bytes)
                if (bitmap == null) imageError = "Could not display this photo."
            }
            .addOnFailureListener { exception ->
                imageError = "Could not load this photo: ${exception.localizedMessage ?: "try again."}"
            }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Customer-submitted queue photo",
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentScale = ContentScale.Crop
                )
            } ?: if (imageError != null) {
                Text(imageError!!, color = MaterialTheme.colorScheme.error)
            } else {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
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

private fun decodeReportImage(bytes: ByteArray): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val largestDimension = maxOf(info.size.width, info.size.height)
                val scale = minOf(1f, MAX_REPORT_IMAGE_DIMENSION.toFloat() / largestDimension)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt()
                )
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (largestDimension / sampleSize > MAX_REPORT_IMAGE_DIMENSION) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
