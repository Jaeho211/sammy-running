package com.sammy.running.core

import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.test.*

class DomainTest {
    private fun samples(distance: Double = 2250.0): List<DistanceSample> =
        (0..90).map { DistanceSample(it * 5.0, it * 5.0, distance * it / 90) }

    @Test fun `interpolates boundaries and keeps final partial distance`() {
        val splits = SplitCalculator().fromSamples(samples())
        assertEquals(listOf(1000.0, 1000.0, 250.0), splits.map { it.distanceMeters })
        assertEquals(listOf(200.0, 200.0, 50.0), splits.map { it.durationSeconds })
    }

    @Test fun `known stationary pause adds no active duration`() {
        val source = samples(2000.0)
        val paused = source.take(46) + listOf(DistanceSample(345.0, 225.0, 1000.0)) +
            source.drop(46).map { it.copy(elapsedSeconds = it.elapsedSeconds + 120) }
        val splits = SplitCalculator().fromSamples(paused)
        assertEquals(2, splits.size)
        assertEquals(450.0, splits.sumOf { it.durationSeconds }, 0.001)
    }

    @Test fun `missing moving samples do not invent splits`() {
        assertTrue(SplitCalculator().fromSamples(samples().filterIndexed { index, _ -> index !in 20..40 }).isEmpty())
    }

    @Test fun `rejects malformed time distance and missing origin`() {
        val calculator = SplitCalculator()
        assertTrue(calculator.fromSamples(samples().reversed()).isEmpty())
        assertTrue(calculator.fromSamples(samples().drop(1)).isEmpty())
        assertTrue(calculator.fromSamples(samples().toMutableList().apply { this[10] = this[9] }).isEmpty())
        assertTrue(calculator.fromSamples(samples().toMutableList().apply { this[10] = this[10].copy(distanceMeters = Double.NaN) }).isEmpty())
        assertTrue(calculator.fromSamples(samples().toMutableList().apply { this[10] = this[10].copy(activeSeconds = 10000.0) }).isEmpty())
    }

    @Test fun `provided splits have priority`() {
        val raw = Fixtures.runs().first().copy(distanceSamples = samples())
        assertEquals(raw.providedSplits, SplitCalculator().calculate(raw).splits)
    }

    @Test fun `indoor and unknown pause timing omit splits`() {
        val runs = Fixtures.runs()
        assertTrue(SplitCalculator().calculate(runs[1]).splits.isEmpty())
        assertTrue(SplitCalculator().calculate(runs[2]).splits.isEmpty())
    }

    @Test fun `continuous GPS produces kilometer splits before simplification`() {
        val start = Instant.parse("2026-09-07T00:00:00Z")
        val route = (0..300).map { RouteSample(start.plusSeconds(it.toLong()), GeoPoint(0.0, it * 0.00009)) }
        val raw = Fixtures.runs()[0].copy(startTime = start, endTime = start.plusSeconds(300),
            durationSeconds = 300.0, distanceMeters = 3002.0, route = route, providedSplits = emptyList())
        val a = RunMapper().preview(raw, 3.0)
        val b = RunMapper().preview(raw, 10.0)
        assertEquals(4, a.splitResult.splits.size)
        assertEquals(a.splitResult, b.splitResult)
        assertEquals(2, a.route.size)
        assertEquals(300.0, a.splitResult.splits.sumOf { it.durationSeconds }, 0.001)
        assertTrue(SplitCalculator().calculate(raw.copy(distanceMeters = 5000.0)).splits.isEmpty())
    }

    @Test fun `simplification preserves endpoints and tolerance`() {
        val points = Fixtures.runs().first().route.map { it.point }
        val simplified = RouteSimplifier.simplify(points, 5.0)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue(simplified.size < points.size / 2)
        var index = 0
        simplified.zipWithNext().forEach { (a, b) ->
            val end = points.indexOf(b).let { if (it <= index) points.lastIndex else it }
            for (i in index..end) assertTrue(RouteSimplifier.segmentDistance(points[i], a, b) <= 5.001)
            index = end
        }
    }

    @Test fun `empty repeated dateline and invalid routes`() {
        assertTrue(RouteSimplifier.simplify(emptyList()).isEmpty())
        val point = GeoPoint(37.0, 127.0)
        assertEquals(listOf(point, point), RouteSimplifier.simplify(List(100) { point }))
        assertEquals(2, RouteSimplifier.simplify(listOf(GeoPoint(0.0, 179.9), GeoPoint(0.0, 180.0), GeoPoint(0.0, -179.9))).size)
        assertFailsWith<IllegalArgumentException> { RouteSimplifier.simplify(listOf(GeoPoint(91.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { RouteSimplifier.simplify(listOf(point), Double.NaN) }
    }

    @Test fun `pace uses proper units and rejects zero invalid values`() {
        assertEquals(441.5303918543659, paceSecondsPerKm(3241.0, 1431.0)!!, 0.0001)
        assertNull(paceSecondsPerKm(0.0, 900.0))
        assertNull(paceSecondsPerKm(null, 900.0))
        assertNull(paceSecondsPerKm(Double.NaN, 900.0))
        assertNull(paceSecondsPerKm(1000.0, -1.0))
        assertEquals(400.0, RunMapper().preview(Fixtures.runs()[0].copy(averageSpeedMetersPerSecond = 2.5)).pace)
    }

    @Test fun `JSON matches v1 shape omits raw data and missing optional fields`() {
        val mapper = RunMapper()
        val raw = Fixtures.runs()[1].copy(heartRate = HeartRate(130.0, null))
        val mapped = mapper.map(raw)
        val json = JsonParser.parseString(mapper.toJson(mapped)).asJsonObject
        assertEquals("2026-09-05T080231", mapped.id)
        assertEquals("2026-09-05T08:02:31+09:00", mapped.startTime)
        listOf("title", "comment", "route", "splits", "heartRate", "cadence", "sessionId", "distanceSamples").forEach { assertFalse(json.has(it)) }
        assertFailsWith<IllegalArgumentException> { mapper.map(Fixtures.runs()[3]) }
    }

    @Test fun `exports fixtures for shared web contract validation`() {
        val mapper = RunMapper()
        val output = File("build/contract-fixtures").apply { mkdirs() }
        Fixtures.runs().filter { it.distanceMeters != null }.forEach { raw ->
            val mapped = mapper.map(raw)
            File(output, "${mapped.id}.json").writeText(mapper.toJson(mapped))
        }
    }
}
