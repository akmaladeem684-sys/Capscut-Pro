package com.example.data.repository

import android.util.Log
import com.example.domain.model.EditorToolItem
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class EditorToolsRepository {
  companion object {
    private const val TAG = "EditorToolsRepository"
    private const val COLLECTION_NAME = "editor_tools"
  }

  private val firestore: FirebaseFirestore? by lazy {
    try {
      val app = runCatching { com.example.StudioApplication.instance }.getOrNull()
      if (app != null && FirebaseApp.getApps(app).isNotEmpty()) {
        FirebaseFirestore.getInstance()
      } else {
        null
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to get Firestore instance: ${t.message}")
      null
    }
  }

  /**
   * Loads editor tools directly from Firestore 'editor_tools' collection in real-time.
   * Emits live updates whenever documents are added, updated, or re-ordered.
   */
  fun getEditorToolsStream(): Flow<List<EditorToolItem>> {
    val db = firestore ?: return flowOf(getDefaultEditorTools())
    return callbackFlow {
      // Send initial fallback while connecting
      trySend(getDefaultEditorTools())

      val listener = try {
        db.collection(COLLECTION_NAME)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              Log.w(TAG, "Firestore snapshot listener error: ${error.message}")
              trySend(getDefaultEditorTools())
              return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
              val items = snapshot.documents.mapNotNull { doc ->
                try {
                  val id = doc.id
                  val name = doc.getString("name") ?: ""
                  val actionKey = doc.getString("actionKey") ?: ""
                  val category = doc.getString("category") ?: "general"
                  val orderNum = (doc.getLong("order") ?: doc.getString("order")?.toLongOrNull() ?: 0L).toInt()
                  val isActive = doc.getBoolean("isActive") ?: true

                  if (name.isNotBlank()) {
                    EditorToolItem(
                      id = id,
                      name = name,
                      actionKey = actionKey,
                      category = category,
                      order = orderNum,
                      isActive = isActive
                    )
                  } else {
                    null
                  }
                } catch (e: Exception) {
                  Log.w(TAG, "Error parsing editor tool doc ${doc.id}: ${e.message}")
                  null
                }
              }.sortedBy { it.order }

              if (items.isNotEmpty()) {
                Log.d(TAG, "Loaded ${items.size} editor tools from Firestore '$COLLECTION_NAME'")
                trySend(items)
              } else {
                trySend(getDefaultEditorTools())
              }
            } else {
              Log.d(TAG, "Firestore '$COLLECTION_NAME' empty, using defaults")
              trySend(getDefaultEditorTools())
            }
          }
      } catch (t: Throwable) {
        Log.e(TAG, "Error attaching Firestore snapshot listener: ${t.message}", t)
        trySend(getDefaultEditorTools())
        null
      }

      awaitClose {
        listener?.remove()
      }
    }
  }

  /**
   * Seed / Initial Fallback matching the 40 Firestore editor_tools collection documents
   * to guarantee instant UI rendering before snapshot loads or when offline.
   */
  fun getDefaultEditorTools(): List<EditorToolItem> {
    return listOf(
      EditorToolItem("tool_effects", "Effects", "TOOL_EFFECTS", "effects", 1, true),
      EditorToolItem("tool_filters", "Filters", "TOOL_FILTERS", "filters", 2, true),
      EditorToolItem("tool_animations", "Animations", "TOOL_ANIMATIONS", "animation", 3, true),
      EditorToolItem("tool_transitions", "Transitions", "TOOL_TRANSITIONS", "transitions", 4, true),
      EditorToolItem("tool_text_styles", "Text Styles", "TOOL_TEXT_STYLES", "text", 5, true),
      EditorToolItem("tool_text_templates", "Text Templates", "TOOL_TEXT_TEMPLATES", "text", 6, true),
      EditorToolItem("tool_fonts", "Fonts", "TOOL_FONTS", "text", 7, true),
      EditorToolItem("tool_audio_music", "Audio / Music", "TOOL_AUDIO_MUSIC", "audio", 8, true),
      EditorToolItem("tool_sfx", "Sound Effects", "TOOL_SFX", "audio", 9, true),
      EditorToolItem("tool_voice_tts", "Voice / TTS", "TOOL_VOICE_TTS", "audio", 10, true),
      EditorToolItem("tool_video_templates", "Video Templates", "TOOL_VIDEO_TEMPLATES", "templates", 11, true),
      EditorToolItem("tool_stickers", "Stickers", "TOOL_STICKERS", "stickers", 12, true),
      EditorToolItem("tool_emojis", "Emojis", "TOOL_EMOJIS", "stickers", 13, true),
      EditorToolItem("tool_overlays", "Overlays", "TOOL_OVERLAYS", "layers", 14, true),
      EditorToolItem("tool_masks", "Masks", "TOOL_MASKS", "layers", 15, true),
      EditorToolItem("tool_frames", "Frames", "TOOL_FRAMES", "canvas", 16, true),
      EditorToolItem("tool_backgrounds", "Backgrounds", "TOOL_BACKGROUNDS", "canvas", 17, true),
      EditorToolItem("tool_lut_presets", "LUT Presets", "TOOL_LUT_PRESETS", "color", 18, true),
      EditorToolItem("tool_chroma_presets", "Chroma Key Presets", "TOOL_CHROMA_PRESETS", "cutout", 19, true),
      EditorToolItem("tool_blend_presets", "Blend / Compositing Presets", "TOOL_BLEND_PRESETS", "layers", 20, true),
      EditorToolItem("tool_light_effects", "Light Effects", "TOOL_LIGHT_EFFECTS", "effects", 21, true),
      EditorToolItem("tool_particle_effects", "Particle Effects", "TOOL_PARTICLE_EFFECTS", "effects", 22, true),
      EditorToolItem("tool_film_vhs", "Film / VHS Assets", "TOOL_FILM_VHS", "effects", 23, true),
      EditorToolItem("tool_shapes", "Shapes", "TOOL_SHAPES", "graphics", 24, true),
      EditorToolItem("tool_captions", "Captions / Subtitle Styles", "TOOL_CAPTIONS", "text", 25, true),
      EditorToolItem("tool_ai_tools", "AI Tools", "TOOL_AI_TOOLS", "ai", 26, true),
      EditorToolItem("tool_ai_effects", "AI Effects", "TOOL_AI_EFFECTS", "ai", 27, true),
      EditorToolItem("tool_face_effects", "Face Effects", "TOOL_FACE_EFFECTS", "effects", 28, true),
      EditorToolItem("tool_body_effects", "Body Effects", "TOOL_BODY_EFFECTS", "effects", 29, true),
      EditorToolItem("tool_beauty_retouch", "Beauty / Retouch Presets", "TOOL_BEAUTY_RETOUCH", "adjust", 30, true),
      EditorToolItem("tool_3d_effects", "3D Effects", "TOOL_3D_EFFECTS", "effects", 31, true),
      EditorToolItem("tool_motion_presets", "Motion Presets", "TOOL_MOTION_PRESETS", "animation", 32, true),
      EditorToolItem("tool_keyframe_presets", "Keyframe Presets", "TOOL_KEYFRAME_PRESETS", "animation", 33, true),
      EditorToolItem("tool_color_presets", "Color Presets", "TOOL_COLOR_PRESETS", "color", 34, true),
      EditorToolItem("tool_blur_presets", "Blur Presets", "TOOL_BLUR_PRESETS", "effects", 35, true),
      EditorToolItem("tool_glitch_presets", "Glitch Presets", "TOOL_GLITCH_PRESETS", "effects", 36, true),
      EditorToolItem("tool_export_presets", "Export Presets", "TOOL_EXPORT_PRESETS", "export", 37, true),
      EditorToolItem("tool_trending_packs", "Trending Packs", "TOOL_TRENDING_PACKS", "packs", 38, true),
      EditorToolItem("tool_effect_packs", "Effect Packs", "TOOL_EFFECT_PACKS", "packs", 39, true),
      EditorToolItem("tool_asset_packs", "Asset Packs", "TOOL_ASSET_PACKS", "packs", 40, true)
    )
  }
}
