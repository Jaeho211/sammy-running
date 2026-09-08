package com.sammy.running.core

import java.time.OffsetDateTime

/** Artificial fixtures for demo UI and JVM tests; the Samsung repository never reads these. */
object Fixtures {
    fun runs(): List<RawRun> {
        val start = OffsetDateTime.parse("2026-09-07T08:02:31+09:00")
        val outdoor = RawRun(
            sessionId = "fixture-outdoor", startTime = start.toInstant(), endTime = start.plusSeconds(1431).toInstant(),
            zoneOffset = start.offset, distanceMeters = 3241.0, durationSeconds = 1431.0,
            heartRate = HeartRate(132.0, 158.0), cadence = Cadence(156.0),
            providedSplits = listOf(Split(1000.0, 461.0), Split(1000.0, 444.0), Split(1000.0, 418.0), Split(241.0, 108.0)),
            route = (0..240).map { i ->
                val angle = i * Math.PI * 2 / 240
                RouteSample(start.toInstant().plusMillis(i * 1431000L / 240),
                    GeoPoint(37.52 + 0.004 * kotlin.math.sin(angle), 127.04 + 0.006 * kotlin.math.cos(angle)))
            },
        )
        val indoor = outdoor.copy(sessionId = "fixture-indoor", startTime = start.minusDays(2).toInstant(),
            endTime = start.minusDays(2).plusSeconds(900).toInstant(), distanceMeters = 2000.0,
            durationSeconds = 900.0, heartRate = null, cadence = null, route = emptyList(),
            providedSplits = emptyList())
        val paused = outdoor.copy(sessionId = "fixture-pause", startTime = start.minusDays(3).toInstant(),
            endTime = start.minusDays(3).plusSeconds(1491).toInstant(), providedSplits = emptyList(),
            route = outdoor.route.map { it.copy(timestamp = it.timestamp.minusSeconds(3 * 86400L)) })
        val incomplete = indoor.copy(sessionId = "fixture-incomplete", startTime = start.minusDays(5).toInstant(),
            endTime = start.minusDays(5).plusSeconds(900).toInstant(), distanceMeters = null)
        return listOf(outdoor, indoor, paused, incomplete)
    }
}
