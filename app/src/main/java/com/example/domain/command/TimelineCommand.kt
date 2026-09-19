package com.example.domain.command

import com.example.domain.model.TimelineTrack

interface TimelineCommand {
    val description: String
    fun execute(tracks: List<TimelineTrack>): List<TimelineTrack>
    fun undo(tracks: List<TimelineTrack>): List<TimelineTrack>
}
