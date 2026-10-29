package com.example.brickearthquakesapp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FusedLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    override fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // isPermissionGranted() is checked first
    override suspend fun currentLocation(): LocationResult {
        if (!isPermissionGranted()) return LocationResult.PermissionMissing
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        if (locationManager != null && !locationManager.isLocationEnabled) {
            return LocationResult.ServicesDisabled
        }

        // Accepts a cached fix up to 5 minutes old and requests a location
        // with an approximately 10-second duration limit.
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
            .setDurationMillis(TIMEOUT_MILLIS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            val location = client.getCurrentLocation(request, cancellation.token).await(cancellation)
            if (location == null) {
                LocationResult.Unavailable
            } else {
                LocationResult.Success(UserLocation(location.latitude, location.longitude))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Permission revoked while the request was in flight.
            LocationResult.PermissionMissing
        } catch (e: Exception) {
            LocationResult.Unavailable
        }
    }

    /** Bridges a Play Services [Task] to a suspend call; cancelling the coroutine cancels the request. */
    private suspend fun Task<Location?>.await(cancellation: CancellationTokenSource): Location? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
            addOnCanceledListener { continuation.cancel() }
        }

    private companion object {
        const val MAX_AGE_MILLIS = 5 * 60 * 1000L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
