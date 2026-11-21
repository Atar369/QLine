package hk.hkucs.qline

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isPartner: Boolean,
    val rating: Double? = null,
    val ratingCount: Int? = null
)

data class PartnerRestaurant(
    val name: String = "",
    val cuisine: String = "Restaurant",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val active: Boolean = true
) {
    fun asRestaurant(id: String) = Restaurant(
        id = id,
        name = name,
        cuisine = cuisine,
        address = address,
        latitude = latitude,
        longitude = longitude,
        isPartner = true
    )
}

data class StaffMembership(val restaurantIds: List<String> = emptyList())
