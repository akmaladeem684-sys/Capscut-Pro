package com.ahstudio.audio.master.persistence

import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioSourceModel
import com.ahstudio.audio.master.model.AudioTrackModel
import com.ahstudio.audio.master.model.MasterAudioProject
import org.json.JSONArray
import org.json.JSONObject

object AudioProjectSerializer {
    fun toJson(project: MasterAudioProject): String {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("sampleRate", project.sampleRate)
        root.put("channels", project.channels)

        val sourcesArr = JSONArray()
        for (src in project.sources.values) {
            val o = JSONObject()
            o.put("id", src.id)
            o.put("uri", src.uri)
            o.put("kind", src.kind.name)
            o.put("durationSec", src.durationSec)
            o.put("nativeSampleRate", src.nativeSampleRate)
            o.put("nativeChannels", src.nativeChannels)
            src.title?.let { o.put("title", it) }
            sourcesArr.put(o)
        }
        root.put("sources", sourcesArr)

        val tracksArr = JSONArray()
        for (track in project.tracks) {
            val t = JSONObject()
            t.put("id", track.id)
            t.put("index", track.index)
            val settings = JSONObject()
            settings.put("name", track.settings.name)
            settings.put("volume", track.settings.volume)
            settings.put("gainDb", track.settings.gainDb)
            settings.put("pan", track.settings.pan)
            settings.put("mute", track.settings.mute)
            settings.put("solo", track.settings.solo)
            t.put("settings", settings)

            val clipsArr = JSONArray()
            for (clip in track.clips) {
                val c = JSONObject()
                c.put("id", clip.id)
                c.put("trackId", clip.trackId)
                c.put("sourceId", clip.sourceId)
                c.put("timelineStartSec", clip.timelineStartSec)
                c.put("timelineDurationSec", clip.timelineDurationSec)
                c.put("sourceStartSec", clip.sourceStartSec)
                c.put("sourceDurationSec", clip.sourceDurationSec)
                c.put("volume", clip.volume)
                c.put("gainDb", clip.gainDb)
                val transform = JSONObject()
                transform.put("speed", clip.transform.speed)
                transform.put("pitchSemitones", clip.transform.pitchSemitones)
                transform.put("reverse", clip.transform.reverse)
                c.put("transform", transform)
                clipsArr.put(c)
            }
            t.put("clips", clipsArr)
            tracksArr.put(t)
        }
        root.put("tracks", tracksArr)
        return root.toString(2)
    }

    fun jsonToProject(json: String): MasterAudioProject {
        val root = JSONObject(json)
        val id = root.getString("id")
        val sampleRate = root.getInt("sampleRate")
        val channels = root.getInt("channels")

        val sourcesMap = mutableMapOf<String, AudioSourceModel>()
        val sourcesArr = root.getJSONArray("sources")
        for (i in 0 until sourcesArr.length()) {
            val o = sourcesArr.getJSONObject(i)
            val s = AudioSourceModel(
                id = o.getString("id"),
                uri = o.getString("uri"),
                kind = com.ahstudio.audio.master.model.AudioSourceKind.valueOf(o.optString("kind", "FILE")),
                durationSec = o.getDouble("durationSec"),
                nativeSampleRate = o.optInt("nativeSampleRate", sampleRate),
                nativeChannels = o.optInt("nativeChannels", channels),
                title = o.optString("title", null),
            )
            sourcesMap[s.id] = s
        }

        val tracksList = mutableListOf<AudioTrackModel>()
        val tracksArr = root.getJSONArray("tracks")
        for (i in 0 until tracksArr.length()) {
            val t = tracksArr.getJSONObject(i)
            val settingsObj = t.getJSONObject("settings")
            val settings = com.ahstudio.audio.master.model.AudioTrackSettings(
                name = settingsObj.optString("name", "Track ${i + 1}"),
                volume = settingsObj.optDouble("volume", 1.0).toFloat(),
                gainDb = settingsObj.optDouble("gainDb", 0.0).toFloat(),
                pan = settingsObj.optDouble("pan", 0.0).toFloat(),
                mute = settingsObj.optBoolean("mute", false),
                solo = settingsObj.optBoolean("solo", false),
            )

            val clipsList = mutableListOf<AudioClipModel>()
            val clipsArr = t.getJSONArray("clips")
            for (j in 0 until clipsArr.length()) {
                val c = clipsArr.getJSONObject(j)
                val transformObj = c.optJSONObject("transform")
                val transform = com.ahstudio.audio.master.model.AudioClipTransform(
                    speed = transformObj?.optDouble("speed", 1.0)?.toFloat() ?: 1.0f,
                    pitchSemitones = transformObj?.optDouble("pitchSemitones", 0.0)?.toFloat() ?: 0.0f,
                    reverse = transformObj?.optBoolean("reverse", false) ?: false,
                )
                val clip = AudioClipModel(
                    id = c.getString("id"),
                    trackId = c.getString("trackId"),
                    sourceId = c.getString("sourceId"),
                    timelineStartSec = c.getDouble("timelineStartSec"),
                    timelineDurationSec = c.getDouble("timelineDurationSec"),
                    sourceStartSec = c.getDouble("sourceStartSec"),
                    sourceDurationSec = c.getDouble("sourceDurationSec"),
                    volume = c.optDouble("volume", 1.0).toFloat(),
                    gainDb = c.optDouble("gainDb", 0.0).toFloat(),
                    transform = transform,
                )
                clipsList.add(clip)
            }

            tracksList.add(AudioTrackModel(
                id = t.getString("id"),
                index = t.getInt("index"),
                settings = settings,
                clips = clipsList,
            ))
        }

        return MasterAudioProject(
            id = id,
            sampleRate = sampleRate,
            channels = channels,
            tracks = tracksList,
            sources = sourcesMap,
        )
    }
}
