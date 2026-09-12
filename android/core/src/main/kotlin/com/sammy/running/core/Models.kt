package com.sammy.running.core

import java.time.Instant
import java.time.ZoneOffset

data class GeoPoint(val lat: Double, val lng: Double) {
    val isValid get() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}
data class RouteSample(val timestamp: Instant, val point: GeoPoint)
/** activeSeconds excludes known pauses; elapsedSeconds includes them. Never infer pause locations. */
data class DistanceSample(val elapsedSeconds: Double, val activeSeconds: Double, val distanceMeters: Double)
data class Split(val distanceMeters: Double, val durationSeconds: Double)
data class HeartRate(val average: Double? = null, val max: Double? = null)
data class Cadence(val average: Double)
data class RawRun(
    val sessionId: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset?,
    val distanceMeters: Double?,
    val durationSeconds: Double,
    val averageSpeedMetersPerSecond: Double? = null,
    val heartRate: HeartRate? = null,
    val cadence: Cadence? = null,
    val route: List<RouteSample> = emptyList(),
    val distanceSamples: List<DistanceSample> = emptyList(),
    val providedSplits: List<Split> = emptyList(),
)

/** JSON contract only: SDK IDs and raw samples never enter this object. */
data class RunJson(
    val schemaVersion: Int = 1,
    val id: String,
    val date: String,
    val startTime: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val averagePaceSecondsPerKm: Double?,
    val splits: List<Split>?,
    val route: List<GeoPoint>?,
)

fun Double?.positiveOrNull(): Double? = this?.takeIf { it.isFinite() && it > 0 }
fun paceSecondsPerKm(distance: Double?, duration: Double): Double? =
    if (distance.positiveOrNull() != null && duration.positiveOrNull() != null)
        (duration * 1000 / distance!!).positiveOrNull() else null
