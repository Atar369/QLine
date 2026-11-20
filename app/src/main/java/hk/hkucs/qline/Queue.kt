package hk.hkucs.qline

data class Queue(
    val name: String = "",
    val currentTicketNumber: Long = 0,
    val nextTicketNumber: Long = 1,
    val minutesPerPerson: Long = 5
) {
    fun toMap() = mapOf(
        "name" to name,
        "currentTicketNumber" to currentTicketNumber,
        "nextTicketNumber" to nextTicketNumber,
        "minutesPerPerson" to minutesPerPerson
    )

    companion object {
        fun seed(name: String) = Queue(name = name)
    }
}