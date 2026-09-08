package com.sammy.running

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as MapPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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
                if (model.selectedId != null) { job?.cancel(); model.selectedId = null; showList() }
                else finish()
            }
        })
        val selected = model.runs.find { it.sessionId == model.selectedId }
        if (selected != null) showDetail(selected)
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
        text("아직 기록을 GitHub에 게시하지 않습니다. Publish는 다음 단계에서 연결됩니다.", 13)
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
        if (!model.routeAllowed) {
            text("경로 권한이 없어 지도는 표시하지 않아요.", 14)
            button("경로 읽기 권한 요청") { load(true) }
        }
        if (model.runs.isEmpty()) text("최근 90일 달리기가 없어요. Watch 기록이 Samsung Health에 동기화되었는지 확인해 주세요.")
        model.runs.forEach { run ->
            val local = run.startTime.atOffset(run.zoneOffset ?: ZoneOffset.UTC)
            val label = "${local.format(DateTimeFormatter.ofPattern("MM월 dd일  HH:mm"))}\n" +
                "${distance(run.distanceMeters)}  ·  ${duration(run.durationSeconds)}  ·  ${pace(run.averageSpeedMetersPerSecond.positiveOrNull()?.let { 1000 / it } ?: paceSecondsPerKm(run.distanceMeters, run.durationSeconds))}"
            button(label) { model.selectedId = run.sessionId; showDetail(run) }
        }
    }

    private fun showDetail(run: RawRun) {
        job?.cancel()
        page("Run Details", "경로와 구간을 준비하고 있어요.")
        job = lifecycleScope.launch {
            val preview = withContext(Dispatchers.Default) { RunMapper().preview(run, model.toleranceMeters) }
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
        text("경로 단순화: ${model.toleranceMeters.toInt()}m", 13)
        val tolerance = SeekBar(this).apply {
            max = 7; progress = model.toleranceMeters.toInt() - 3
            contentDescription = "경로 단순화 허용 오차 3미터에서 10미터"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) model.toleranceMeters = value + 3.0 }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) { showDetail(run) }
            })
        }
        content.addView(tolerance)
        text("1 km 구간", 22, ink, true)
        text(preview.splitResult.note, 13)
        preview.splitResult.splits.forEachIndexed { index, split ->
            text("${index + 1}구간  ${distance(split.distanceMeters)}    ${duration(split.durationSeconds)}")
        }
        text("게시 기능은 다음 단계에서 연결됩니다.", 13)
        button("Publish · 준비 중") {}.isEnabled = false
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
