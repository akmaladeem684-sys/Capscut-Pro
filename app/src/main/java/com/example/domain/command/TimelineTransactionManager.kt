package com.example.domain.command

import com.example.domain.model.TimelineTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class TimelineTransactionManager(
    initialTracks: List<TimelineTrack> = emptyList()
) {
    private val undoStack = ArrayDeque<TimelineCommand>()
    private val redoStack = ArrayDeque<TimelineCommand>()

    private val _tracksState = MutableStateFlow(initialTracks)
    val tracksState: StateFlow<List<TimelineTrack>> = _tracksState.asStateFlow()

    fun executeCommand(command: TimelineCommand): Boolean {
        val currentTracks = _tracksState.value
        val newTracks = try {
            command.execute(currentTracks)
        } catch (e: Exception) {
            return false
        }

        if (newTracks != currentTracks) {
            undoStack.push(command)
            redoStack.clear()
            _tracksState.value = newTracks
            return true
        }
        return false
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false

        val command = undoStack.peek() ?: return false
        val currentTracks = _tracksState.value
        val newTracks = try {
            command.undo(currentTracks)
        } catch (e: Exception) {
            return false
        }

        if (newTracks != currentTracks) {
            undoStack.pop()
            redoStack.push(command)
            _tracksState.value = newTracks
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false

        val command = redoStack.peek() ?: return false
        val currentTracks = _tracksState.value
        val newTracks = try {
            command.execute(currentTracks)
        } catch (e: Exception) {
            return false
        }

        if (newTracks != currentTracks) {
            redoStack.pop()
            undoStack.push(command)
            _tracksState.value = newTracks
            return true
        }
        return false
    }

    fun setTracksDirectly(tracks: List<TimelineTrack>) {
        undoStack.clear()
        redoStack.clear()
        _tracksState.value = tracks
    }

    fun getCurrentSnapshot(): List<TimelineTrack> = _tracksState.value

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()
}
