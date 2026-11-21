package hk.hkucs.qline

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.Places
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val HongKongCenter = LatLng(22.3193, 114.1694)
private const val SearchRadiusMeters = 4_000.0

@OptIn(FlowPreview::class)
@Composable
fun NearbyRestaurantsMap(
    partners: Map<String, PartnerRestaurant>,
    onRestaurantSelected: (Restaurant) -> Unit
) {
    val context = LocalContext.current
    val availablePartners = partners.toMap()
    val apiKey = remember(context) {
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        ).metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
    }
    val placesClient: PlacesClient? = remember(apiKey) {
        if (apiKey.isBlank()) null else Places.createClient(context)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(HongKongCenter, 13f)
    }
    val restaurants = remember { mutableStateListOf<Restaurant>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    val locationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    fun centerOnCurrentLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            locationMessage = "Location permission is needed to center the map; you can also move the map manually."
            return
        }
        locationMessage = null
        locationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location == null) {
                locationMessage = "Could not get your location. Move the map to search another area."
            } else {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    14f
                )
            }
        }.addOnFailureListener {
            locationMessage = "Could not get your location: ${it.localizedMessage ?: "try moving the map manually."}"
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) centerOnCurrentLocation()
        else locationMessage = "Location permission was denied. Move the map manually to search."
    }

    LaunchedEffect(cameraPositionState, placesClient, availablePartners) {
        val client = placesClient ?: return@LaunchedEffect
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position.target }
            .filter { !it.first }
            .map { it.second }
            .distinctUntilChanged()
            .debounce(500)
            .collectLatest { center ->
                loading = true
                error = null
                val requestGeneration = ++generation
                val request = SearchNearbyRequest.builder(
                    CircularBounds.newInstance(center, SearchRadiusMeters),
                    listOf(
                        Place.Field.ID,
                        Place.Field.DISPLAY_NAME,
                        Place.Field.FORMATTED_ADDRESS,
                        Place.Field.LOCATION,
                        Place.Field.PRIMARY_TYPE,
                        Place.Field.RATING,
                        Place.Field.USER_RATING_COUNT
                    )
                )
                    .setIncludedTypes(listOf("restaurant"))
                    .setMaxResultCount(20)
                    .build()
                client.searchNearby(request)
                    .addOnSuccessListener { response ->
                        if (generation == requestGeneration) {
                            val placeResults = response.places.mapNotNull { place ->
                                val id = place.id ?: return@mapNotNull null
                                val location = place.location ?: return@mapNotNull null
                                val partner = availablePartners[id]?.takeIf { it.active }
                                if (availablePartners.containsKey(id) && partner == null) return@mapNotNull null
                                Restaurant(
                                    id = id,
                                    name = partner?.name?.takeIf(String::isNotBlank)
                                        ?: place.displayName
                                        ?: "Restaurant",
                                    cuisine = partner?.cuisine?.takeIf(String::isNotBlank)
                                        ?: place.primaryType?.replace('_', ' ')?.replaceFirstChar(Char::uppercase)
                                        ?: "Restaurant",
                                    address = partner?.address?.takeIf(String::isNotBlank)
                                        ?: place.formattedAddress.orEmpty(),
                                    latitude = partner?.latitude?.takeIf { it != 0.0 } ?: location.latitude,
                                    longitude = partner?.longitude?.takeIf { it != 0.0 } ?: location.longitude,
                                    isPartner = partner != null,
                                    rating = place.rating,
                                    ratingCount = place.userRatingCount
                                )
                            }
                            val placeIds = placeResults.mapTo(mutableSetOf()) { it.id }
                            val partnerResults = availablePartners.mapNotNull { (id, partner) ->
                                if (!partner.active || id in placeIds ||
                                    partner.latitude == 0.0 || partner.longitude == 0.0
                                ) {
                                    null
                                } else if (distanceMeters(center, LatLng(partner.latitude, partner.longitude)) <= SearchRadiusMeters) {
                                    partner.asRestaurant(id)
                                } else {
                                    null
                                }
                            }
                            val results = (placeResults + partnerResults)
                                .sortedWith(compareByDescending<Restaurant> { it.isPartner }.thenBy { it.name })
                            restaurants.clear()
                            restaurants.addAll(results)
                            loading = false
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (generation == requestGeneration) {
                            loading = false
                            error = "Could not load nearby places: ${exception.localizedMessage ?: "check the Maps and Places API setup."}"
                        }
                    }

            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nearby restaurants", style = MaterialTheme.typography.titleLarge)
        if (apiKey.isBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Add MAPS_API_KEY to local.properties and enable Maps SDK for Android and Places API to load the map.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                restaurants.forEach { restaurant ->
                    Marker(
                        state = rememberUpdatedMarkerState(
                            position = LatLng(restaurant.latitude, restaurant.longitude)
                        ),
                        title = restaurant.name,
                        snippet = if (restaurant.isPartner) "QLine partner" else "Community queue reports",
                        onClick = {
                            onRestaurantSelected(restaurant)
                            true
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val hasPermission =
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED
                        if (hasPermission) centerOnCurrentLocation()
                        else locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) { Text("Use my location") }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("${restaurants.size} found", style = MaterialTheme.typography.labelMedium)
            }
            locationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && restaurants.isEmpty() && error == null) {
                Text(
                    "No restaurants found here. Move the map to search another area.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            restaurants.forEach { restaurant ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                restaurant.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (restaurant.isPartner) "QLine partner" else "Community reports",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            restaurant.address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        val rating = restaurant.rating
                        if (rating != null) {
                            Text(
                                "★ ${"%.1f".format(rating)} · ${restaurant.ratingCount ?: 0} reviews",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        OutlinedButton(
                            onClick = { onRestaurantSelected(restaurant) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (restaurant.isPartner) "View live queue" else "View community reports")
                        }
                    }
                }
            }
        }
    }
}

private fun distanceMeters(first: LatLng, second: LatLng): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val haversine = sin(latitudeDelta / 2).pow(2) +
        cos(firstLatitude) * cos(secondLatitude) * sin(longitudeDelta / 2).pow(2)
    return 2 * earthRadiusMeters * asin(sqrt(haversine))
}
