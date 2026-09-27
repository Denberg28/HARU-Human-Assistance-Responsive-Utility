package io.haru.assistant.location

object LocationProviderPolicy {
    fun providers(
        gpsEnabled: Boolean,
        networkEnabled: Boolean,
        hasFineLocation: Boolean,
    ): List<String> =
        buildList {
            if (networkEnabled) {
                add(android.location.LocationManager.NETWORK_PROVIDER)
            }
            if (gpsEnabled && hasFineLocation) {
                add(android.location.LocationManager.GPS_PROVIDER)
            }
        }

    fun isValidCoordinate(
        latitude: Double,
        longitude: Double,
    ): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
}
