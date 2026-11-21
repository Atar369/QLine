package hk.hkucs.qline

import com.google.firebase.Timestamp

data class QueueReport(
    val restaurantId: String = "",
    val userId: String = "",
    val storagePath: String = "",
    val estimatedPeople: Long? = null,
    val waitMinutes: Long? = null,
    val createdAt: Timestamp? = null
) {
    fun toMap() = mapOf(
        "restaurantId" to restaurantId,
        "userId" to userId,
        "storagePath" to storagePath,
        "estimatedPeople" to estimatedPeople,
        "waitMinutes" to waitMinutes,
        "createdAt" to createdAt
    )
}
