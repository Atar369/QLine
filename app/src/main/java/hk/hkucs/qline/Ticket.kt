package hk.hkucs.qline

data class Ticket(
    val queueId: String = "",
    val number: Long = 0,
    val userId: String = "",
    val status: String = WAITING
) {
    fun toMap() = mapOf(
        "queueId" to queueId,
        "number" to number,
        "userId" to userId,
        "status" to status
    )

    companion object {
        const val WAITING = "WAITING"
        const val CALLED = "CALLED"
        const val DONE = "DONE"
        const val CANCELLED = "CANCELLED"
    }
}