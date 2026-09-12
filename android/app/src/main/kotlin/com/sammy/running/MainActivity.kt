package com.sammy.running

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sammy.running.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as MapPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    private val model: RunViewModel by viewModels()
    private lateinit var content: LinearLayout
    private var map: MapView? = null
    private var job: Job? = null
    private val ink = Color.rgb(28, 55, 49)
    private val green = Color.rgb(27, 112, 83)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = cacheDir.resolve("map")
        Configuration.getInstance().osmdroidTileCache = cacheDir.resolve("map/tiles")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (model.showingSettings) {
                    job?.cancel()
                    model.settings.clearPendingAuthorization()
                    model.showingSettings = false
                    if (model.hasLoaded) showList() else showWelcome()
                }
                else if (model.selectedId != null) { job?.cancel(); model.selectedId = null; showList() }
                else finish()
            }
        })
        val selected = model.runs.find { it.sessionId == model.selectedId }
        val pendingAuthorization = if (!model.settings.hasCredentials())
            runCatching { model.settings.pendingAuthorization() }.getOrNull() else null
        if (pendingAuthorization != null) resumeGitHubLogin(pendingAuthorization)
        else if (model.showingSettings) showSettings()
        else if (selected != null) showDetail(selected)
        else if (model.hasLoaded) showList() else showWelcome()
    }

    private fun page(title: String, subtitle: String) {
        map?.onDetach(); map = null
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(32))
            setBackgroundColor(Color.rgb(247, 247, 239))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        if (model.repository.isDemo) text("DEMO · 모든 기록과 경로는 가상입니다", 13, green)
        text(title, 30, ink, true)
        text(subtitle, 15)
    }

    private fun showWelcome() {
        page("Run Log", "Samsung Health 러닝 기록")
        text(if (model.repository.isDemo) "실외·실내·일시정지·불완전 기록을 미리 볼 수 있어요." else
            "Samsung Health에 동기화된 최근 90일 달리기를 읽어요. 경로 권한은 선택 사항입니다.")
        button(if (model.repository.isDemo) "가상 기록 보기" else "Samsung Health 연결") { load(true) }
        button("GitHub 설정") { showSettings() }
    }

    private fun load(requestPermission: Boolean) {
        job?.cancel()
        page("기록 불러오는 중", "잠시만 기다려 주세요.")
        content.addView(ProgressBar(this))
        job = lifecycleScope.launch {
            try {
                val access = if (requestPermission) model.repository.requestPermissions(this@MainActivity) else model.repository.permissions()
                if (!access.exercise) {
                    model.runs = emptyList(); model.hasLoaded = false
                    page("읽기 권한이 필요해요", "Samsung Health에서 운동 기록 읽기를 허용해 주세요.")
                    button("권한 다시 요청") { load(true) }
                    return@launch
                }
                val result = model.repository.recentRuns()
                model.runs = result.runs
                model.routeAllowed = result.routeAllowed
                model.hasLoaded = true
                model.selectedId = null
                showList()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                model.runs = emptyList(); model.hasLoaded = false
                page("연결을 확인해 주세요", model.repository.errorMessage(error))
                button("다시 연결") { load(true) }
            }
        }
    }

    private fun showList() {
        page("최근 달리기", "기록을 선택하면 경로와 구간을 볼 수 있어요.")
        button("새로고침") { load(false) }
        button("GitHub 설정") { showSettings() }
        if (!model.routeAllowed) {
            text("경로 권한이 없어 지도는 표시하지 않아요.", 14)
            button("경로 읽기 권한 요청") { load(true) }
        }
        if (model.runs.isEmpty()) text("최근 90일 달리기가 없어요. Watch 기록이 Samsung Health에 동기화되었는지 확인해 주세요.")
        model.runs.forEach { run ->
            val local = run.startTime.atOffset(run.zoneOffset ?: ZoneOffset.UTC)
            val label = "${local.format(DateTimeFormatter.ofPattern("MM월 dd일  HH:mm"))}\n" +
                "${distance(run.distanceMeters)}  ·  ${duration(run.durationSeconds)}  ·  ${pace(run.averageSpeedMetersPerSecond.positiveOrNull()?.let { 1000 / it } ?: paceSecondsPerKm(run.distanceMeters, run.durationSeconds))}" +
                if (model.publishedRuns.contains(run)) "\nPublished ✓" else ""
            button(label) { model.publishMessage = null; model.selectedId = run.sessionId; showDetail(run) }
        }
    }

    private fun showDetail(run: RawRun) {
        job?.cancel()
        page("Run Details", "경로와 구간을 준비하고 있어요.")
        job = lifecycleScope.launch {
            val preview = withContext(Dispatchers.Default) { RunMapper().preview(run) }
            if (model.selectedId != run.sessionId) return@launch
            renderDetail(preview)
        }
    }

    private fun renderDetail(preview: RunPreview) {
        val run = preview.run
        val local = run.startTime.atOffset(run.zoneOffset ?: ZoneOffset.UTC)
        page("달리기 상세", local.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm:ss XXX")))
        button("← 최근 기록") { model.selectedId = null; showList() }
        text(distance(run.distanceMeters), 36, green, true)
        text("시간 ${duration(run.durationSeconds)}   ·   평균 ${pace(preview.pace)}", 18)
        if (run.zoneOffset == null) text("기록에 시간대가 없어 UTC로 표시합니다.", 13)
        run.heartRate?.let { rate ->
            rate.average.positiveOrNull()?.let { text("평균 심박  ${it.roundToLong()} bpm") }
            rate.max.positiveOrNull()?.let { text("최대 심박  ${it.roundToLong()} bpm") }
        }
        run.cadence?.average.positiveOrNull()?.let { text("평균 케이던스  ${it.roundToLong()} spm") }
        text("달린 경로", 22, ink, true)
        if (preview.route.size < 2) text("표시할 GPS 경로가 없어요. 실내 기록이거나 경로 권한·데이터가 없을 수 있어요.")
        else addMap(preview.route)
        text("1 km 구간", 22, ink, true)
        text(preview.splitResult.note, 13)
        preview.splitResult.splits.forEachIndexed { index, split ->
            text("${index + 1}구간  ${distance(split.distanceMeters)}    ${duration(split.durationSeconds)}")
        }
        val published = model.publishedRuns.contains(run)
        model.publishMessage?.let { text(it, 13, if (published) green else Color.rgb(170, 60, 45)) }
        val publishButton = button(when {
            published -> "Published ✓"
            model.publishingId == run.sessionId -> "게시 중…"
            else -> "GitHub에 Publish"
        }) { publish(preview) }
        publishButton.isEnabled = !published && model.publishingId == null
        if (!published) text("선택한 기록의 GPS 경로와 요약을 repository에 게시합니다.", 13)
    }

    private fun publish(preview: RunPreview) {
        val run = preview.run
        model.publishingId = run.sessionId
        model.publishMessage = null
        renderDetail(preview)
        job = lifecycleScope.launch {
            try {
                val mapper = RunMapper()
                val mapped = withContext(Dispatchers.Default) { mapper.map(run) }
                val result = withContext(Dispatchers.IO) {
                    val config = model.settings.configWithValidToken(model.deviceAuth, BuildConfig.GITHUB_APP_CLIENT_ID)
                    model.publisher.publish(config, mapped, mapper.toJson(mapped))
                }
                when (result) {
                    is PublishResult.Published -> {
                        model.publishedRuns.mark(run, result.path)
                        model.publishMessage = if (result.alreadyExisted) "원격에 동일한 기록이 있어 Published 상태를 복구했습니다."
                            else "게시했습니다. GitHub Actions가 웹을 갱신합니다."
                    }
                    is PublishResult.Collision -> model.publishMessage = "같은 시작 시각의 다른 파일이 이미 있어 덮어쓰지 않았습니다: ${result.path}"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { model.publishMessage = error.message ?: "게시하지 못했습니다. 다시 시도해 주세요." }
            finally {
                model.publishingId = null
                if (model.selectedId == run.sessionId && !isFinishing && !isDestroyed) renderDetail(preview)
            }
        }
    }

    private fun showSettings() {
        job?.cancel()
        model.showingSettings = true
        page("GitHub 설정", "GitHub App으로 안전하게 로그인합니다.")
        text("Repository  Jaeho211/sammy-running\nBranch  main", 16)
        if (model.settings.hasCredentials()) {
            text("GitHub 연결됨 ✓", 18, green, true)
            text("Access token과 refresh token은 Android Keystore 키로 암호화해 이 기기에만 저장합니다.", 13)
            button("GitHub 로그아웃") {
                model.settings.clearCredentials()
                Toast.makeText(this, "GitHub 연결을 해제했습니다.", Toast.LENGTH_SHORT).show()
                showSettings()
            }
        } else {
            text("버튼을 누르면 인증 코드를 복사하고 GitHub 로그인 페이지를 엽니다. PAT를 직접 입력할 필요가 없습니다.", 13)
            button("GitHub로 로그인") { startGitHubLogin() }
        }
        button("← 돌아가기") {
            model.showingSettings = false
            if (model.hasLoaded) showList() else showWelcome()
        }
    }

    private fun startGitHubLogin() {
        job?.cancel()
        model.showingSettings = true
        page("GitHub 로그인", "인증 코드를 요청하고 있어요.")
        content.addView(ProgressBar(this))
        job = lifecycleScope.launch {
            try {
                val authorization = withContext(Dispatchers.IO) { model.deviceAuth.requestCode(BuildConfig.GITHUB_APP_CLIENT_ID) }
                model.settings.savePendingAuthorization(authorization)
                pollGitHubLogin(authorization, openBrowser = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showGitHubLoginError(error) }
        }
    }

    private fun resumeGitHubLogin(authorization: DeviceAuthorization) {
        job?.cancel()
        model.showingSettings = true
        job = lifecycleScope.launch {
            try { pollGitHubLogin(authorization, openBrowser = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showGitHubLoginError(error) }
        }
    }

    private suspend fun pollGitHubLogin(authorization: DeviceAuthorization, openBrowser: Boolean) {
        page("GitHub 로그인", "아래 코드를 GitHub에서 승인해 주세요.")
        text(authorization.userCode, 34, green, true)
        text("코드를 클립보드에 복사했습니다. 인증 후 앱으로 돌아오면 자동으로 완료됩니다.", 14)
        button("GitHub 인증 페이지 열기") { openBrowser(authorization.verificationUri) }
        button("취소") { model.settings.clearPendingAuthorization(); showSettings() }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", authorization.userCode))
        if (openBrowser) openBrowser(authorization.verificationUri)

        var interval = authorization.intervalSeconds
        val deadline = SystemClock.elapsedRealtime() + authorization.expiresInSeconds * 1000
        var lastNetworkError: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(interval * 1000)
            val result = try {
                withContext(Dispatchers.IO) {
                    model.deviceAuth.poll(BuildConfig.GITHUB_APP_CLIENT_ID, authorization.deviceCode)
                }
            } catch (error: IOException) {
                lastNetworkError = error.message
                continue
            }
            when (result) {
                DevicePollResult.Pending -> Unit
                DevicePollResult.SlowDown -> interval += 5
                DevicePollResult.Expired -> error("인증 코드가 만료되었습니다. 다시 로그인해 주세요.")
                DevicePollResult.Denied -> error("GitHub 로그인이 취소되었습니다.")
                is DevicePollResult.Authorized -> {
                    model.settings.saveTokens(result.tokens)
                    model.settings.clearPendingAuthorization()
                    job = null
                    Toast.makeText(this, "GitHub 로그인이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    showSettings()
                    return
                }
            }
        }
        error(lastNetworkError?.let { "네트워크 연결을 복구하지 못했습니다: $it" }
            ?: "인증 코드가 만료되었습니다. 다시 로그인해 주세요.")
    }

    private fun showGitHubLoginError(error: Exception) {
        model.settings.clearPendingAuthorization()
        job = null
        page("GitHub 로그인 실패", error.message ?: "로그인을 완료하지 못했습니다.")
        button("다시 시도") { startGitHubLogin() }
        button("← 설정") { showSettings() }
    }

    private fun openBrowser(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "브라우저를 열지 못했습니다: $url", Toast.LENGTH_LONG).show() }
    }

    private fun addMap(points: List<GeoPoint>) {
        val mapView = RouteMapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            contentDescription = "달린 경로 지도. 초록색 시작점과 빨간색 종료점."
        }
        map = mapView
        val positions = points.map { MapPoint(it.lat, it.lng) }
        mapView.overlays.add(Polyline().apply {
            setPoints(positions); outlinePaint.color = green; outlinePaint.strokeWidth = dp(4).toFloat()
        })
        listOf(Triple(positions.first(), "시작", green), Triple(positions.last(), "종료", Color.rgb(205, 86, 61))).forEach { (point, label, color) ->
            mapView.overlays.add(Marker(mapView).apply {
                position = point; title = label; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(2), Color.WHITE); setSize(dp(18), dp(18)) }
            })
        }
        content.addView(mapView, LinearLayout.LayoutParams(-1, dp(260)))
        mapView.controller.setCenter(positions.first()); mapView.controller.setZoom(15.0)
        mapView.post { if (map === mapView) mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(positions), false, dp(28), 18.0, null) }
        text("© OpenStreetMap contributors · 지도 배경은 인터넷 연결이 필요해요.", 12)
        mapView.onResume()
    }

    private fun text(value: String, size: Int = 16, color: Int = ink, bold: Boolean = false) {
        content.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color)
            setPadding(0, dp(8), 0, dp(8))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        })
    }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(ink); minHeight = dp(52)
        setOnClickListener { action() }; content.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun distance(meters: Double?) = meters?.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.KOREA, "%.2f km", it / 1000) } ?: "거리 없음"
    private fun duration(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0) return "시간 없음"
        val total = seconds.roundToLong()
        return if (total >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
        else String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }
    private fun pace(seconds: Double?) = seconds?.let { "${duration(it)}/km" } ?: "페이스 없음"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onPause() { map?.onPause(); super.onPause() }
    override fun onDestroy() { map?.onDetach(); map = null; super.onDestroy() }
}
