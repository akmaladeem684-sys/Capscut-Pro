package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.ui.TimelineMetrics
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.ui.components.timeline.TrackLaneManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicTrackCapacityTest {

    @Test
    fun `test dynamic lane allocation for 1 to 20 tracks`() {
        for (count in 1..20) {
            val textClips = (1..count).map { i ->
                TextClip(
                    id = "text_$i",
                    text = "Text $i",
                    timelineStartMs = 0L,
                    durationMs = 3000L,
                    trackIndex = i
                )
            }
            val timeline = Timeline(textClips = textClips)
            val lanes = TrackLaneManager.computeLanes(timeline)

            // Main track (lane 0) + count text tracks
            assertEquals("Expected ${count + 1} total lanes for count = $count", count + 1, lanes.size)

            // Verify each lane has its own distinct lane index
            val laneIndices = lanes.map { it.laneIndex }
            assertEquals((0..count).toList(), laneIndices)

            // Verify no track overlaps or collapses
            lanes.drop(1).forEachIndexed { index, lane ->
                assertTrue(lane.label.startsWith("Text"))
                assertEquals(1, lane.clips.size)
            }
        }
    }

    @Test
    fun `test timeline metrics Y position calculation for 20 tracks`() {
        val metrics = TimelineMetrics(
            mainRowHeightPx = 58f,
            subRowHeightPx = 36f,
            mainToSubGapPx = 8f,
            subTrackGapPx = 4f
        )

        val trackCount = 20
        var expectedY = 0f

        for (i in 0 until trackCount) {
            val topY = metrics.trackTopPx(i)
            assertEquals("Track $i Y position mismatch", expectedY, topY, 0.01f)
            val indexAtY = metrics.trackIndexAtY(topY + 18f, trackCount)
            assertEquals("Track index at Y mismatch for row $i", i, indexAtY)

            if (i == 0) {
                expectedY += 58f + 8f
            } else {
                expectedY += 36f + 4f
            }
        }
    }
}
