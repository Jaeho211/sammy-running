package com.sammy.running.core

import java.time.Duration
import kotlin.math.abs
import kotlin.math.max

data class SplitResult(val splits: List<Split>, val note: String)

class SplitCalculator(private val maxSampleGapSeconds: Double = 30.0) {
    init { require(maxSampleGapSeconds.isFinite() && maxSampleGapSeconds > 0) }

    fun calculate(run: RawRun): SplitResult {
        if (run.providedSplits.isNotEmpty() && run.providedSplits.all {
                it.distanceMeters.positiveOrNull() != null && it.durationSeconds.positiveOrNull() != null
            }) return SplitResult(run.providedSplits, "기록에서 제공한 구간")
        if (run.distanceSamples.isNotEmpty()) {
            val splits = fromSamples(run.distanceSamples)
            return SplitResult(splits, if (splits.isEmpty()) "거리 시계열이 불완전하여 구간을 계산할 수 없어요." else "원본 거리·운동 시간으로 계산한 구간")
        }
        // SDK 1.1.0 exposes no pause intervals. Do not spread pauses across the run.
        val elapsed = Duration.between(run.startTime, run.endTime).toMillis() / 1000.0
        if (abs(elapsed - run.durationSeconds) > 1.0)
            return SplitResult(emptyList(), "일시정지 시점을 알 수 없어 구간을 계산할 수 없어요.")
        val route = run.route
        if (route.size < 2 || route.any { !it.point.isValid } ||
            abs(Duration.between(run.startTime, route.first().timestamp).toMillis()) > 1000 ||
            abs(Duration.between(route.last().timestamp, run.endTime).toMillis()) > 1000)
            return SplitResult(emptyList(), "전체 시간의 GPS 또는 거리 시계열이 없어 구간을 계산할 수 없어요.")
        var distance = 0.0
        val samples = route.mapIndexed { index, point ->
            if (index > 0) distance += RouteSimplifier.distance(route[index - 1].point, point.point)
            val time = Duration.between(route.first().timestamp, point.timestamp).toMillis() / 1000.0
            DistanceSample(time, time, distance)
        }
        val summary = run.distanceMeters.positiveOrNull()
        if (summary == null || abs(distance - summary) > max(50.0, summary * 0.05))
            return SplitResult(emptyList(), "GPS 거리와 요약 거리 차이가 커서 구간을 생략했어요.")
        val splits = fromSamples(samples)
        return SplitResult(splits, if (splits.isEmpty()) "GPS 시계열에 누락 또는 잘못된 값이 있어 구간을 생략했어요." else "원본 GPS로 계산한 구간 · GPS 오차로 요약 거리와 다를 수 있어요.")
    }

    fun fromSamples(samples: List<DistanceSample>): List<Split> {
        if (samples.size < 2 || samples.any {
                !it.elapsedSeconds.isFinite() || !it.activeSeconds.isFinite() || !it.distanceMeters.isFinite() ||
                    it.elapsedSeconds < 0 || it.activeSeconds < 0 || it.distanceMeters < 0 || it.activeSeconds > it.elapsedSeconds + 0.001
            }) return emptyList()
        val first = samples.first()
        if (first.distanceMeters != 0.0 || first.activeSeconds != 0.0 || first.elapsedSeconds != 0.0) return emptyList()
        for ((a, b) in samples.zipWithNext()) {
            val elapsed = b.elapsedSeconds - a.elapsedSeconds
            val active = b.activeSeconds - a.activeSeconds
            val distance = b.distanceMeters - a.distanceMeters
            if (elapsed <= 0 || active < 0 || active > elapsed + 0.001 || distance < 0 ||
                (distance > 0 && (active <= 0 || elapsed > maxSampleGapSeconds))) return emptyList()
        }
        val result = mutableListOf<Split>()
        var boundary = 1000.0
        var previousTime = 0.0
        var previousDistance = 0.0
        for ((a, b) in samples.zipWithNext()) {
            while (b.distanceMeters >= boundary && b.distanceMeters > a.distanceMeters) {
                val ratio = (boundary - a.distanceMeters) / (b.distanceMeters - a.distanceMeters)
                val crossing = a.activeSeconds + ratio * (b.activeSeconds - a.activeSeconds)
                if (crossing <= previousTime) return emptyList()
                result.add(Split(1000.0, crossing - previousTime))
                previousTime = crossing
                previousDistance = boundary
                boundary += 1000.0
            }
        }
        val last = samples.last()
        if (last.distanceMeters > previousDistance + 0.001) {
            if (last.activeSeconds <= previousTime) return emptyList()
            result.add(Split(last.distanceMeters - previousDistance, last.activeSeconds - previousTime))
        }
        return result
    }
}
