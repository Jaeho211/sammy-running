package com.sammy.running.core

import com.google.gson.GsonBuilder
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class RunPreview(val run: RawRun, val route: List<GeoPoint>, val splitResult: SplitResult, val pace: Double?)

class RunMapper(private val calculator: SplitCalculator = SplitCalculator()) {
    fun preview(raw: RawRun, toleranceMeters: Double = 5.0): RunPreview {
        val splitResult = calculator.calculate(raw)
        // Invalid coordinates invalidate the route rather than silently connecting across missing points.
        val points = raw.route.map { it.point }
        val route = if (points.all { it.isValid }) RouteSimplifier.simplify(points, toleranceMeters) else emptyList()
        val pace = raw.averageSpeedMetersPerSecond.positiveOrNull()?.let { (1000 / it).positiveOrNull() }
            ?: paceSecondsPerKm(raw.distanceMeters, raw.durationSeconds)
        return RunPreview(raw, route, splitResult, pace)
    }

    fun map(raw: RawRun, toleranceMeters: Double = 5.0): RunJson {
        val distance = requireNotNull(raw.distanceMeters?.takeIf { it.isFinite() && it >= 0 }) { "Distance unavailable" }
        require(raw.durationSeconds.positiveOrNull() != null)
        val preview = preview(raw, toleranceMeters)
        // Preserve a known offset; UTC is explicit when the source did not retain a local offset.
        val local = raw.startTime.atOffset(raw.zoneOffset ?: ZoneOffset.UTC)
        return RunJson(
            id = local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")),
            date = local.toLocalDate().toString(), startTime = local.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            distanceMeters = distance,
            durationSeconds = raw.durationSeconds, averagePaceSecondsPerKm = preview.pace,
            splits = preview.splitResult.splits.takeIf { it.isNotEmpty() },
            heartRate = raw.heartRate?.let { HeartRate(it.average.positiveOrNull(), it.max.positiveOrNull()) }
                ?.takeIf { it.average != null && it.max != null },
            cadence = raw.cadence?.takeIf { it.average.positiveOrNull() != null },
            route = preview.route.takeIf { it.size >= 2 },
        )
    }

    fun toJson(run: RunJson): String = GsonBuilder().setPrettyPrinting().create().toJson(run)
}
