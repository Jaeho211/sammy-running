package com.sammy.running.core

import kotlin.math.*

object RouteSimplifier {
    const val DEFAULT_TOLERANCE_METERS = 5.0
    private const val EARTH = 6371008.8

    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dlat = lat2 - lat1
        val dlon = Math.toRadians(b.lng - a.lng)
        val h = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        return 2 * EARTH * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val aLat = Math.toRadians(a.lat)
        val bLat = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lng - a.lng)
        return atan2(sin(dLon) * cos(bLat), cos(aLat) * sin(bLat) - sin(aLat) * cos(bLat) * cos(dLon))
    }

    /** Spherical distance to the finite great-circle segment, including dateline routes. */
    internal fun segmentDistance(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val length = distance(a, b)
        if (length < 0.001) return distance(p, a)
        val angular = distance(a, p) / EARTH
        val delta = bearing(a, p) - bearing(a, b)
        val along = atan2(sin(angular) * cos(delta), cos(angular)) * EARTH
        if (along <= 0) return distance(p, a)
        if (along >= length) return distance(p, b)
        return abs(asin((sin(angular) * sin(delta)).coerceIn(-1.0, 1.0))) * EARTH
    }

    fun simplify(points: List<GeoPoint>, toleranceMeters: Double = DEFAULT_TOLERANCE_METERS): List<GeoPoint> {
        require(toleranceMeters.isFinite() && toleranceMeters > 0)
        require(points.all { it.isValid })
        if (points.size < 3) return points.toList()
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.add(0 to points.lastIndex)
        while (pending.isNotEmpty()) {
            val (start, end) = pending.removeLast()
            var furthest = -1
            var maximum = toleranceMeters
            for (i in start + 1 until end) {
                val distance = segmentDistance(points[i], points[start], points[end])
                if (distance > maximum) { maximum = distance; furthest = i }
            }
            if (furthest >= 0) {
                keep[furthest] = true
                pending.add(start to furthest)
                pending.add(furthest to end)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }
}
