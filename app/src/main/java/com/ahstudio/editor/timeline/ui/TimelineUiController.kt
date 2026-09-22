package com.ahstudio.editor.timeline.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.engine.CommandHistory
import com.ahstudio.editor.timeline.engine.EngineListener
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import com.ahstudio.editor.timeline.playback.PlaybackController
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class Hit {
    object None : Hit()
    data class Ruler(val timeMicros: Long) : Hit()
    object Empty : Hit()
    data class ClipBody(val clipId: String) : Hit()
    data class ClipHandle(val clipId: String, val side: Side) : Hit()
    enum class Side { START, END }
}

data class DragPreview(
    val clipIds: Set<String>,
    val primaryId: String,
    val deltaMicros: Long,
    val trackShift: Int,
    val originRow: Map<String, Int>,
)

data class TrimPreview(val clipId: String, val startMicros: Long, val durationMicros: Long)
data class ReorderPreview(val fromIndex: Int, val insertionIndex: Int)

/** Optional visual bridge — plug your real thumbnail/waveform decoders here. */
interface ClipVisualProvider {
    fun waveformFor(clip: Clip): FloatArray? = null
    fun thumbnailCount(clip: Clip): Int = 0
}

class TimelineUiController(
    val engine: TimelineEngine,
    val clock: MasterTimelineClock,
    val playback: PlaybackController,
    val viewport: TimelineViewport,
    val snap: SnapEngine,
    val planner: ClipPlanner,
    val history: CommandHistory,
    private val scope: CoroutineScope,
    var visualProvider: ClipVisualProvider? = null,
) : EngineListener {

    // ---- Compose-observed state (bridged from the engines — never duplicated) ----
    var snapshot by mutableStateOf(engine.snapshot); private set
    var selection by mutableStateOf<Set<String>>(emptySet()); private set
    var playheadMicros by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    var scrollX by mutableFloatStateOf(0f); private set
    var scrollY by mutableFloatStateOf(0f); private set
    var tracksAreaHeightPx by mutableFloatStateOf(0f)

    var dragPreview by mutableStateOf<DragPreview?>(null); private set
    var trimPreview by mutableStateOf<TrimPreview?>(null); private set
    var snapLines by mutableStateOf<List<Long>>(emptyList()); private set
    var isScrubbing by mutableStateOf(false); private set
    var reorderPreview by mutableStateOf<ReorderPreview?>(null); private set
    var flashMessage by mutableStateOf<String?>(null)

    val playheadXPx: Float get() = viewport.playheadXPx
    val fps: Double get() = snapshot.settings.fps

    /** Content duration = max(project end, 60s) + 15s tail so the end is reachable. */
    fun contentDurationMicros(): Long =
        maxOf(engine.durationMicros(), 60_000_000L) + 15_000_000L
    fun contentWidthPx(): Float = viewport.contentPxAtTime(contentDurationMicros())

    init {
        engine.addListener(this)
        clock.addListener { micros, _ -> playheadMicros = micros }
        history.listener = object : com.ahstudio.editor.timeline.engine.HistoryListener {
            override fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean) {
                this@TimelineUiController.canUndo = canUndo; this@TimelineUiController.canRedo = canRedo
            }
        }
    }

    override fun onChanged(s: com.ahstudio.editor.timeline.engine.TimelineSnapshot, sel: Set<String>) {
        snapshot = s; selection = sel
    }

    fun report(msg: String) { flashMessage = msg }

    // ---------------- §7: touching the timeline ALWAYS pauses playback ----------------
    private var flingJob: Job? = null

    fun onTimelineTouchBegan() {
        playback.pause()
        flingJob?.cancel(); flingJob = null
    }

    // ---------------- scroll plumbing ----------------
    private fun clampScrollX(v: Float): Float {
        if (viewport.viewportWidthPx <= 0f) return v
        val min = -viewport.playheadXPx
        val max = viewport.contentPxAtTime(contentDurationMicros()) - viewport.playheadXPx
        return v.coerceIn(min, max)
    }

    fun setScrollYRaw(v: Float) { scrollY = v.coerceIn(0f, maxOf(0f, tracksContentHeightPx() - tracksAreaHeightPx)) }
    fun tracksContentHeightPx(): Float = snapshot.tracks.size * rowHeightPxCompat
    private val rowHeightPxCompat: Float get() = 56f * densityScale
    var densityScale: Float = 1f

    fun shiftScrollX(dx: Float): Float {
        val old = scrollX; scrollX = clampScrollX(old + dx); return scrollX - old
    }
    fun scrollToTime(micros: Long) { scrollX = clampScrollX(viewport.scrollPxForTime(micros)) }

    /** §5 auto-scroll: playhead fixed, content moves. Called once per frame while playing. */
    fun followPlayhead() { if (isPlaying) scrollToTime(playheadMicros) }

    fun fling(vx: Float, vy: Float) {
        flingJob?.cancel()
        if (abs(vx) < 60f && abs(vy) < 60f) { setScrollYRaw(scrollY); return }
        flingJob = scope.launch {
            val decay = exponentialDecay<Float>(frictionMultiplier = 1.6f)
            val ax = Animatable(0f); val ay = Animatable(0f)
            var lastX = 0f
            val jx = launch {
                if (abs(vx) > 60f) {
                    ax.animateDecay(-vx, decay) {
                        val delta = value - lastX
                        lastX = value
                        shiftScrollX(delta)
                    }
                }
            }
            var lastY = 0f
            val jy = launch {
                if (abs(vy) > 60f) {
                    ay.animateDecay(-vy, decay) {
                        val delta = value - lastY
                        lastY = value
                        setScrollYRaw(scrollY + delta)
                    }
                }
            }
            jx.join(); jy.join()
        }
    }

    // ---------------- coordinate → time → snap → engine (§9 pipeline) ----------------
    fun timeUnderPointer(screenX: Float): Long =
        viewport.timeAtContentPx(screenX + scrollX)

    fun snapTime(t: Long, exclude: Set<String> = emptySet(), thresholdPx: Float = 10f): Pair<Long, List<Long>> {
        val r = snap.query(t, planner.snapThresholdMicros(thresholdPx), exclude, playheadMicros)
        return if (r.snapped) r.micros to r.matched else t to emptyList()
    }

    private fun quantizeFrame(t: Long): Long = TimelineTime(t).quantizeToFrame(fps).micros

    // ---------------- scrubbing (§8) ----------------
    fun scrubTo(timeMicros: Long, snapEnabled: Boolean = true) {
        isScrubbing = true
        var t = quantizeFrame(timeMicros.coerceAtLeast(0L))
        if (snapEnabled) t = snapTime(t).first
        playback.requestScrubSeek(t)   // clock immediate, sink frame-aligned
        playheadMicros = clock.timeMicros
    }
    fun scrubEnded() { isScrubbing = false }
    fun tapSeek(timeMicros: Long) { playback.seekTo(quantizeFrame(timeMicros.coerceAtLeast(0L))) }

    // ---------------- hit testing ----------------
    fun hitTest(screenX: Float, screenY: Float, m: TimelineMetrics): Hit {
        if (screenY < m.rulerHeightPx) return Hit.Ruler(timeUnderPointer(screenX))
        val contentY = screenY - m.rulerHeightPx + scrollY
        val row = (contentY / m.rowHeightPx).toInt()
        val tracks = snapshot.tracks
        if (row < 0 || row >= tracks.size) return Hit.Empty
        val track = tracks[row]
        if (!track.visible || track.locked) return Hit.Empty
        val t = timeUnderPointer(screenX)
        val clip = engine.clipAtTime(track.id, t) ?: return Hit.Empty
        val contentX = screenX + scrollX
        val x0 = viewport.contentPxAtTime(clip.startMicros)
        val x1 = viewport.contentPxAtTime(clip.endMicros)
        if (clip.id in selection) {
            if (contentX - x0 <= m.handlePx) return Hit.ClipHandle(clip.id, Hit.Side.START)
            if (x1 - contentX <= m.handlePx) return Hit.ClipHandle(clip.id, Hit.Side.END)
        }
        return Hit.ClipBody(clip.id)
    }

    // ---------------- selection (§11 — never moves the playhead) ----------------
    fun onTapClip(clipId: String) { engine.setSelection(setOf(clipId)) }
    fun onTapEmpty() { engine.clearSelection() }
    fun onLongPressClip(clipId: String) { engine.toggleSelection(clipId) }
    fun selectTrack(trackId: String) { engine.selectTrack(trackId) }

    // ---------------- clip drag (§6, §12) ----------------
    private var dragAnchor = 0L

    fun beginClipDrag(primaryId: String) {
        val ids = if (primaryId in selection && selection.size > 1) selection else setOf(primaryId)
        val originRow = ids.mapNotNull { id ->
            engine.clip(id)?.let { engine.trackIndex(it.trackId) }?.takeIf { it >= 0 }?.let { id to it }
        }.toMap()
        val c = engine.clip(primaryId) ?: return
        dragAnchor = c.startMicros
        dragPreview = DragPreview(ids, primaryId, 0L, 0, originRow)
    }

    /** Max track shift allowed for whole group (P3 bug fix applied). */
    fun allowedTrackShift(preview: DragPreview, shift: Int): Int {
        if (shift == 0) return 0
        val n = snapshot.tracks.size
        val kindsByRow = preview.originRow.entries
            .groupBy({ it.value }) { engine.clip(it.key)?.kind }
            .mapValues { (_, v) -> v.filterNotNull().toSet() }
        var s = shift
        while (s != 0) {
            val ok = kindsByRow.all { (row, kinds) ->
                val target = row + s
                target in 0 until n &&
                    !snapshot.tracks[target].locked &&
                    kinds.all { snapshot.tracks[target].kind.accepts(it) }
            }
            if (ok) return s
            s += if (s > 0) -1 else 1
        }
        return 0
    }

    fun updateClipDrag(deltaMicros: Long, rawShift: Int, snapEnabled: Boolean, snapThresholdPx: Float = 10f) {
        val p = dragPreview ?: return
        var delta = deltaMicros
        var snappedTo: List<Long> = emptyList()
        if (snapEnabled) {
            val target = dragAnchor + delta
            val r = snap.query(target, planner.snapThresholdMicros(snapThresholdPx), p.clipIds, playheadMicros)
            if (r.snapped) { delta = r.micros - dragAnchor; snappedTo = r.matched }
        }
        val shift = allowedTrackShift(p, rawShift)
        dragPreview = p.copy(deltaMicros = delta, trackShift = shift)
        snapLines = snappedTo
    }

    fun commitClipDrag() {
        val p = dragPreview
        dragPreview = null; snapLines = emptyList()
        p ?: return
        val primary = engine.clip(p.primaryId) ?: return
        engine.begin("Move clips")
        try {
            for (id in p.clipIds) {
                val c = engine.clip(id) ?: continue
                val row = p.originRow[id] ?: continue
                val targetRow = (row + p.trackShift).coerceIn(0, snapshot.tracks.size - 1)
                val targetTrack = snapshot.tracks[targetRow]
                val desired = if (id == p.primaryId) primary.startMicros + p.deltaMicros
                              else c.startMicros + p.deltaMicros
                val placement = planner.resolveMove(
                    id, targetTrack.id, desired, p.clipIds,
                    snapEnabled = id == p.primaryId,
                )
                engine.moveClip(id, placement.startMicros, placement.trackId)
            }
            engine.commit()
        } catch (e: Exception) { engine.cancel(); report(e.message ?: "Move failed") }
    }

    fun cancelClipDrag() { dragPreview = null; snapLines = emptyList() }

    /** Effective start for rendering during drag (called per clip). */
    fun effectiveClipStart(clip: Clip): Long =
        clip.startMicros + (dragPreview?.takeIf { clip.id in it.clipIds }?.deltaMicros ?: 0L)

    // ---------------- trimming (§6) ----------------
    fun beginTrim(clipId: String, side: Hit.Side) {
        val c = engine.clip(clipId) ?: return
        trimPreview = TrimPreview(clipId, c.startMicros, c.durationMicros)
    }

    fun updateTrim(clipId: String, side: Hit.Side, desiredTimeMicros: Long, snapThresholdPx: Float) {
        val c = engine.clip(clipId) ?: return
        val snapped = snapTime(desiredTimeMicros, setOf(clipId), snapThresholdPx).first
        val (s, d) = if (side == Hit.Side.START)
            planner.resolveTrim(clipId, snapped, c.endMicros)
        else
            planner.resolveTrim(clipId, c.startMicros, snapped)
        trimPreview = TrimPreview(clipId, s, d)
    }

    fun commitTrim() {
        val t = trimPreview
        trimPreview = null
        t ?: return
        try { engine.trimClip(t.clipId, t.startMicros, t.durationMicros) }
        catch (e: Exception) { report(e.message ?: "Trim failed") }
    }
    fun cancelTrim() { trimPreview = null }

    fun effectiveTrim(clip: Clip): Clip? = trimPreview?.takeIf { it.clipId == clip.id }?.let {
        clip.copy(startMicros = it.startMicros, durationMicros = it.durationMicros,
                  sourceInMicros = clip.sourceInMicros + ((it.startMicros - clip.startMicros) * clip.speed).toLong())
    }

    // ---------------- high-level edit actions ----------------
    fun splitAtPlayhead() {
        val t = playheadMicros
        val selected = selection.mapNotNull { engine.clip(it) }
        val targets = if (selected.isNotEmpty()) selected
            else snapshot.tracks.filter { !it.locked }
                .mapNotNull { engine.clipAtTime(it.id, t) }
        val splitable = targets.filter { t > it.startMicros && t < it.endMicros }
        if (splitable.isEmpty()) { report("Nothing to split at playhead"); return }
        engine.begin("Split")
        try { splitable.forEach { engine.splitClip(it.id, t) }; engine.commit() }
        catch (e: Exception) { engine.cancel(); report(e.message ?: "Split failed") }
    }

    fun deleteSelection() {
        if (selection.isEmpty()) return
        engine.begin("Delete clips")
        try { selection.toList().forEach { id -> engine.clip(id)?.let { if (!engine.trackById(it.trackId)!!.locked) engine.removeClip(id) } }; engine.commit() }
        catch (e: Exception) { engine.cancel() }
    }

    fun duplicateSelection() {
        if (selection.isEmpty()) return
        engine.begin("Duplicate clips")
        try {
            selection.forEach { id ->
                val c = engine.clip(id) ?: return@forEach
                val placement = planner.resolveMove(id, c.trackId, c.endMicros, setOf(id), snapEnabled = false)
                engine.duplicateClip(id, placement.startMicros)
            }
            engine.commit()
        } catch (e: Exception) { engine.cancel(); report(e.message ?: "Duplicate failed") }
    }

    fun addMarkerAtPlayhead() { engine.addMarker(playheadMicros, TimeFormatter.clock(TimelineTime(playheadMicros), fps, false)) }

    fun undo() { history.undo(engine) }
    fun redo() { history.redo(engine) }

    // ---------------- track reorder (§13) ----------------
    fun beginTrackReorder(index: Int) { reorderPreview = ReorderPreview(index, index) }
    fun updateTrackReorder(absoluteY: Float, rowHeightPx: Float) {
        val rp = reorderPreview ?: return
        val insertion = (absoluteY / rowHeightPx).roundToInt().coerceIn(0, snapshot.tracks.size)
        reorderPreview = rp.copy(insertionIndex = insertion)
    }
    fun commitTrackReorder() {
        val rp = reorderPreview
        reorderPreview = null
        rp ?: return
        var target = rp.insertionIndex
        if (rp.fromIndex < target) target -= 1
        if (target != rp.fromIndex) engine.moveTrack(snapshot.tracks[rp.fromIndex].id, target)
    }
    fun cancelTrackReorder() { reorderPreview = null }

    // ---------------- zoom (§5, pinch anchored on playhead) ----------------
    fun setZoomAroundPlayhead(anchorMicros: Long, factor: Float) {
        viewport.setZoomAroundTime(anchorMicros, factor)
        scrollToTime(anchorMicros)
    }

    // ---------------- edge auto-scroll during gestures ----------------
    fun edgeAutoScroll(pointerX: Float, viewportWidthPx: Float): Float {
        val m = 64f; val maxSpeed = 22f
        return when {
            pointerX < m -> shiftScrollX(-maxSpeed * (m - pointerX) / m)
            pointerX > viewportWidthPx - m -> shiftScrollX(maxSpeed * (pointerX - (viewportWidthPx - m)) / m)
            else -> 0f
        }
    }
}
