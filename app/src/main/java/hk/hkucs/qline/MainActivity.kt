package hk.hkucs.qline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import hk.hkucs.qline.ui.theme.QLineTheme
import com.google.firebase.firestore.FirebaseFirestore
// import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private var message by mutableStateOf("Loading...")

    private var myTicketNumber by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser

        val db = FirebaseFirestore.getInstance()

        if (currentUser != null) {
            db.collection("users")
                .document(currentUser.uid)
                .set(
                    mapOf(
                        "email" to currentUser.email
                    )
                )
            message =
                """
                Signed in as:
                ${currentUser.email}
                """.trimIndent()
        }

        db.collection("queues")
            .document("clinicA")
            .addSnapshotListener { queueDoc, error ->

                if (queueDoc != null) {

                    val queueName =
                        queueDoc.getString("name") ?: "Unknown"

                    val currentTicketNumber =
                        queueDoc.getLong("currentTicketNumber") ?: 0

                    val ticketInfo =
                        if (myTicketNumber != null)
                            "\n\nYour Ticket: $myTicketNumber\nPeople Ahead: ${maxOf(0, myTicketNumber!! - currentTicketNumber)}"
                        else
                            ""

                    message =
                        """
                        Queue: $queueName
                        Current Number: $currentTicketNumber
                        $ticketInfo
                        """.trimIndent()
                }
            }

        enableEdgeToEdge()
        setContent {
            QLineTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding)
                    ) {

                        Text(text = message)

                        Button(
                            onClick = {

                                db.collection("queues")
                                    .document("clinicA")
                                    .get()
                                    .addOnSuccessListener { queueDoc ->

                                        val nextTicketNumber =
                                            queueDoc.getLong("nextTicketNumber") ?: 1

                                        val uid = FirebaseAuth.getInstance().currentUser!!.uid

                                        db.collection("tickets")
                                            .whereEqualTo("userId", uid)
                                            .whereEqualTo("queueId", "clinicA")
                                            .get()
                                            .addOnSuccessListener { result ->

                                                if (!result.isEmpty) {

                                                    val existingTicket =
                                                        result.documents[0]
                                                            .getLong("number")

                                                    myTicketNumber = existingTicket

                                                    message =
                                                        "You already have ticket #$existingTicket"

                                                } else {
                                                    myTicketNumber = nextTicketNumber
                                                    db.collection("tickets")
                                                        .document("ticket_$nextTicketNumber")
                                                        .set(
                                                            mapOf(
                                                                "queueId" to "clinicA",
                                                                "number" to nextTicketNumber,
                                                                "userId" to uid,
                                                                "userEmail" to auth.currentUser!!.email
                                                            )
                                                        )

                                                    db.collection("users")
                                                        .document(uid)
                                                        .update(
                                                            mapOf(
                                                                "activeQueueId" to "clinicA",
                                                                "activeTicketNumber" to nextTicketNumber
                                                            )
                                                        )

                                                    db.collection("queues")
                                                        .document("clinicA")
                                                        .update(
                                                            "nextTicketNumber",
                                                            nextTicketNumber + 1
                                                        )

                                                    myTicketNumber = nextTicketNumber
                                                }
                                            }
                                    }
                            }
                        ) {
                            Text("Take Ticket")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    QLineTheme {
        Greeting("Android")
    }
}