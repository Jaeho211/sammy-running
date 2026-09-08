package com.sammy.running.health

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.sammy.running.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Typed SDK 1.1.0 adapter; never substitutes fixtures if Samsung Health fails. */
class SdkSamsungHealthRepository(context: Context) : SamsungHealthRepository {
    private val store by lazy { HealthDataService.getStore(context) }
    private val exercisePermission = Permission.of(DataTypes.EXERCISE, AccessType.READ)
    private val routePermission = Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ)
    private val required = setOf(exercisePermission, routePermission)
    override val isDemo = false

    override fun errorMessage(error: Exception): String {
        val code = (error as? HealthDataException)?.errorCode
        val message = when (code) {
            ErrorCode.ERR_INVALID_CALLER, ErrorCode.ERR_ACCESS_CONTROL -> "Samsung Health의 개발용 Data Read 설정을 확인해 주세요."
            ErrorCode.ERR_NO_USER_PERMISSION -> "Samsung Health에서 운동 기록 읽기 권한을 허용해 주세요."
            ErrorCode.ERR_PLATFORM_NOT_INSTALLED -> "Samsung Health를 설치해 주세요."
            ErrorCode.ERR_OLD_VERSION_PLATFORM -> "Samsung Health를 최신 버전으로 업데이트해 주세요."
            ErrorCode.ERR_PLATFORM_DISABLED, ErrorCode.ERR_PLATFORM_NOT_INITIALIZED -> "Samsung Health를 열어 초기 설정을 완료해 주세요."
            ErrorCode.ERR_CHILD_ACCOUNT_ACCESS -> "이 Samsung Health 계정은 SDK 데이터 접근이 제한되어 있어요."
            else -> "Samsung Health 연결을 완료하지 못했어요. 앱을 열어 동기화 상태를 확인하고 다시 시도해 주세요."
        }
        // Only the numeric SDK code is displayed; error payloads may contain private source data.
        return message + (code?.let { " (SDK $it)" } ?: "")
    }

    override suspend fun permissions(): AccessState = withContext(Dispatchers.IO) {
        val granted = store.getGrantedPermissions(required)
        AccessState(exercisePermission in granted, routePermission in granted)
    }

    override suspend fun requestPermissions(activity: Activity): AccessState {
        val granted = withContext(Dispatchers.IO) { store.getGrantedPermissions(required) }
        val missing = required - granted
        if (missing.isNotEmpty()) store.requestPermissions(missing, activity)
        return permissions()
    }

    override suspend fun recentRuns(): RecentRuns = withContext(Dispatchers.IO) {
        val access = permissions()
        check(access.exercise) { "달리기 기록 읽기 권한을 허용해 주세요." }
        val end = Instant.now()
        val filter = InstantTimeFilter.of(end.minus(90, ChronoUnit.DAYS), end)
        val runs = mutableListOf<RawRun>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        do {
            val request = DataTypes.EXERCISE.readDataRequestBuilder
                .setInstantTimeFilter(filter).setOrdering(Ordering.DESC)
                .setPageSize(50).setPageToken(token).build()
            val response = store.readData(request)
            response.dataList.forEach { point ->
                val sessions = point.getValue(DataType.ExerciseType.SESSIONS).orEmpty()
                sessions.forEach sessionLoop@{ session ->
                    if (session.exerciseType != DataType.ExerciseType.PredefinedExerciseType.RUNNING &&
                        session.exerciseType != DataType.ExerciseType.PredefinedExerciseType.TRACK_RUNNING) return@sessionLoop
                    val route = if (access.route) session.route.orEmpty().map {
                        RouteSample(it.timestamp, GeoPoint(it.latitude.toDouble(), it.longitude.toDouble()))
                    } else emptyList()
                    runs.add(RawRun(
                        sessionId = "${point.uid}:${session.startTime}:${session.exerciseType}",
                        startTime = session.startTime, endTime = session.endTime, zoneOffset = point.zoneOffset,
                        distanceMeters = session.distance?.toDouble()?.takeIf { it.isFinite() && it >= 0 },
                        durationSeconds = session.duration.toMillis() / 1000.0,
                        averageSpeedMetersPerSecond = session.meanSpeed?.toDouble(),
                        heartRate = HeartRate(session.meanHeartRate?.toDouble().positiveOrNull(), session.maxHeartRate?.toDouble().positiveOrNull())
                            .takeIf { it.average != null || it.max != null },
                        cadence = session.meanCadence?.toDouble().positiveOrNull()?.let { Cadence(it) },
                        route = route,
                    ))
                }
            }
            token = response.pageToken
            check(token == null || seenTokens.add(token!!)) { "기록 페이지를 불러오지 못했어요. 다시 시도해 주세요." }
        } while (token != null)
        RecentRuns(runs.distinctBy { it.sessionId }.sortedByDescending { it.startTime }, access.route)
    }
}
