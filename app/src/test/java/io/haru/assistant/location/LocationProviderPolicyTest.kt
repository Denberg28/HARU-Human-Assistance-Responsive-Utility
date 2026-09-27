package io.haru.assistant.location

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationProviderPolicyTest {
    @Test
    fun finePermissionUsesNetworkAndGpsWhenAvailable() {
        assertEquals(
            listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
            ),
            LocationProviderPolicy.providers(
                gpsEnabled = true,
                networkEnabled = true,
                hasFineLocation = true,
            ),
        )
    }

    @Test
    fun coarsePermissionAvoidsGpsOnlyPath() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER),
            LocationProviderPolicy.providers(
                gpsEnabled = true,
                networkEnabled = true,
                hasFineLocation = false,
            ),
        )
    }

    @Test
    fun gpsOnlyNeedsFinePermission() {
        assertEquals(
            emptyList<String>(),
            LocationProviderPolicy.providers(
                gpsEnabled = true,
                networkEnabled = false,
                hasFineLocation = false,
            ),
        )
    }

    @Test
    fun coordinateValidationRejectsImpossibleValues() {
        assertTrue(LocationProviderPolicy.isValidCoordinate(14.6, 121.0))
        assertFalse(LocationProviderPolicy.isValidCoordinate(91.0, 121.0))
        assertFalse(LocationProviderPolicy.isValidCoordinate(14.6, 181.0))
    }
}
