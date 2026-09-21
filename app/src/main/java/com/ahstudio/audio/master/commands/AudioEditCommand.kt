package com.ahstudio.audio.master.commands

import com.ahstudio.audio.master.model.MasterAudioProject

interface AudioEditCommand {
    fun apply(project: MasterAudioProject): MasterAudioProject
    fun affectedTrackId(): String? = null
}

class AudioUndoRedoAdapter(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<AudioEditCommand>()
    private val redoStack = ArrayDeque<AudioEditCommand>()

    fun push(cmd: AudioEditCommand) {
        undoStack.addLast(cmd)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: MasterAudioProject): MasterAudioProject? {
        if (undoStack.isEmpty()) return null
        val cmd = undoStack.removeLast()
        redoStack.addLast(cmd)
        var p = MasterAudioProject(
            id = current.id,
            sampleRate = current.sampleRate,
            channels = current.channels,
            tracks = current.tracks,
            sources = current.sources,
            mix = current.mix,
            masterDsp = current.masterDsp
        )
        for (c in undoStack) {
            p = c.apply(p)
        }
        return p
    }

    fun redo(current: MasterAudioProject): MasterAudioProject? {
        if (redoStack.isEmpty()) return null
        val cmd = redoStack.removeLast()
        undoStack.addLast(cmd)
        return cmd.apply(current)
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun clear() { undoStack.clear(); redoStack.clear() }
}
