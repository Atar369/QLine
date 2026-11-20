package hk.hkucs.qline

data class Queue(
    val name: String = "",
    val currentTicketNumber: Long = 0,
    val nextTicketNumber: Long = 1
)