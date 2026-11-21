package hk.hkucs.qline

import com.google.firebase.Timestamp

data class QueueReport(
    val restaurantId: String = "",
    val userId: String = "",
    val estimatedPeople: Long? = null,
    val waitMinutes: Long? = null,
    val createdAt: Timestamp? = null
) {
    fun toMap() = mapOf(
        "restaurantId" to restaurantId,
        "userId" to userId,
        "estimatedPeople" to estimatedPeople,
        "waitMinutes" to waitMinutes,
        "createdAt" to createdAt
    )
}
