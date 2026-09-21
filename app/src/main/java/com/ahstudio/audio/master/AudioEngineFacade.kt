package com.ahstudio.audio.master

import android.content.Context
import com.ahstudio.audio.master.cache.DecodedAudioCache
import com.ahstudio.audio.master.clips.AudioClipOperations
import com.ahstudio.audio.master.clips.AudioClipReader
import com.ahstudio.audio.master.commands.AudioEditCommand
import com.ahstudio.audio.master.commands.AudioUndoRedoAdapter
import com.ahstudio.audio.master.core.AudioClockAdapter
import com.ahstudio.audio.master.core.AudioEngineResult
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioThreadController
import com.ahstudio.audio.master.diagnostics.AudioGlitchDetector
import com.ahstudio.audio.master.diagnostics.AudioPerformanceMonitor
import com.ahstudio.audio.master.export.AudioExportConfig
import com.ahstudio.audio.master.export.AudioExportEngine
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioSourceModel
import com.ahstudio.audio.master.model.AudioTrackModel
import com.ahstudio.audio.master.model.MasterAudioProject
import com.ahstudio.audio.master.persistence.AudioProjectSerializer
import com.ahstudio.audio.master.playback.AudioOutputEngine
import com.ahstudio.audio.master.playback.AudioPlaybackState
import com.ahstudio.audio.master.recording.AndroidAudioRecorder
import com.ahstudio.audio.master.recording.RecordingConfig
import com.ahstudio.audio.master.recording.RecordingSession
import com.ahstudio.audio.master.timeline.AudioTimelineController
import com.ahstudio.audio.master.waveform.WaveformEngine
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class AudioEngineFacade(
    val context: Context,
    val format: AudioFormat = AudioFormat.DEFAULT,
) {
    val threads = AudioThreadController()
    val cache = DecodedAudioCache(context, format)
    val undoRedo = AudioUndoRedoAdapter()
    val timelineController = AudioTimelineController(undoRedo)
    val clipReader = AudioClipReader(cache)
    val mixer = MasterAudioMixer(1024)
    val performance = AudioPerformanceMonitor()
    val glitches = AudioGlitchDetector()
    val outputEngine = AudioOutputEngine(format, mixer, performance, glitches)
    val recorder = AndroidAudioRecorder(context, threads.ioScope)
    val exportEngine = AudioExportEngine(context, cache)
    val waveformEngine = WaveformEngine(cache, context.cacheDir, threads.bgScope)

    init {
        cache.sourceProvider = { id -> timelineController.current.sourceById(id) }
        timelineController.addListener { project ->
            mixer.setProject(project, format)
        }
    }

    fun setProject(project: MasterAudioProject) {
        timelineController.setProject(project)
        cache.preload(project.sources.values)
    }

    fun execute(command: AudioEditCommand): AudioEngineResult<MasterAudioProject> =
        timelineController.submit(command)

    fun undo(): Boolean = timelineController.undo()
    fun redo(): Boolean = timelineController.redo()

    fun play(startSec: Double = outputEngine.currentPositionSec()) {
        outputEngine.play(startSec, clipReader)
    }

    fun pause() { outputEngine.pause() }

    fun seekTo(sec: Double) { outputEngine.seekTo(sec, clipReader) }

    val playbackState: AudioPlaybackState get() = outputEngine.state
    val playbackPositionSec: Double get() = outputEngine.currentPositionSec()

    fun startRecording(config: RecordingConfig = RecordingConfig()): AudioEngineResult<RecordingSession> =
        recorder.start(config)

    suspend fun stopRecording(): AudioEngineResult<RecordingSession> = recorder.finish()

    fun export(config: AudioExportConfig) = exportEngine.export(timelineController.current, config)

    fun serializeProject(): String = AudioProjectSerializer.toJson(timelineController.current)
    fun deserializeProject(json: String) {
        val p = AudioProjectSerializer.jsonToProject(json)
        setProject(p)
    }

    fun release() {
        outputEngine.stopAndRelease()
        cache.release()
        threads.release()
    }
}
