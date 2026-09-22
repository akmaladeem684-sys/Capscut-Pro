package com.ahstudio.editor.timeline.engine

interface TimelineCommand {
    val label: String
    fun apply(e: TimelineEngine)
    fun revert(e: TimelineEngine)
}

/**
 * Full-snapshot command. Immutable snapshots share structure (Kotlin data-class copies),
 * so storing before/after is O(1) refs; apply/revert is one ref swap + index rebuild.
 * This guarantees NO timeline mutation can bypass the command system.
 */
class SnapshotCommand(
    override val label: String,
    val before: TimelineSnapshot, val beforeSelection: Set<String>,
    val after: TimelineSnapshot, val afterSelection: Set<String>,
) : TimelineCommand {
    override fun apply(e: TimelineEngine) { e.restoreInternal(after, afterSelection) }
    override fun revert(e: TimelineEngine) { e.restoreInternal(before, beforeSelection) }
}

interface HistoryListener { fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean) }

class CommandHistory(private val limit: Int = 200) {
    private val undoStack = ArrayDeque<TimelineCommand>()
    private val redoStack = ArrayDeque<TimelineCommand>()
    var listener: HistoryListener? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(cmd: TimelineCommand) {
        undoStack.addLast(cmd)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        notifyChanged()
    }

    fun undo(engine: TimelineEngine): Boolean {
        val c = undoStack.removeLastOrNull() ?: return false
        c.revert(engine); redoStack.addLast(c); notifyChanged(); return true
    }

    fun redo(engine: TimelineEngine): Boolean {
        val c = redoStack.removeLastOrNull() ?: return false
        c.apply(engine); undoStack.addLast(c); notifyChanged(); return true
    }

    fun clear() { undoStack.clear(); redoStack.clear(); notifyChanged() }
    private fun notifyChanged() { listener?.onHistoryChanged(canUndo, canRedo) }
}
