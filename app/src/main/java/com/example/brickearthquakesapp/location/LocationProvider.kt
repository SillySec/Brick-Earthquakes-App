package com.example.brickearthquakesapp.location

data class UserLocation(val latitude: Double, val longitude: Double)

sealed interface LocationResult {
    data class Success(val location: UserLocation) : LocationResult

    /** The user has not granted (or has revoked) the location permission. */
    data object PermissionMissing : LocationResult

    /** Location is switched off in the device settings. */
    data object ServicesDisabled : LocationResult

    /** Permission and settings are fine but no position could be determined (timeout, no fix, error). */
    data object Unavailable : LocationResult
}

/**
 * One-shot location lookup. It never asks for the permission itself (that is the UI's job)
 * and never tracks the device continuously.
 */
interface LocationProvider {
    fun isPermissionGranted(): Boolean

    /**
     * A current or recent position. Expected failures are returned as a [LocationResult],
     * not thrown; coroutine cancellation is propagated.
     */
    suspend fun currentLocation(): LocationResult
}
