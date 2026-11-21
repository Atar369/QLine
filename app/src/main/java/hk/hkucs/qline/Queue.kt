package hk.hkucs.qline

data class Queue(
    val name: String = "",
    val currentTicketNumber: Long = 0,
    val nextTicketNumber: Long = 1,
    val minutesPerPerson: Long = 5,
    val isOpen: Boolean = true
) {
    fun toMap() = mapOf(
        "name" to name,
        "currentTicketNumber" to currentTicketNumber,
        "nextTicketNumber" to nextTicketNumber,
        "minutesPerPerson" to minutesPerPerson,
        "isOpen" to isOpen
    )

    companion object {
        fun seed(name: String) = Queue(name = name)
    }
}