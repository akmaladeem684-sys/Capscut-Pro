from pathlib import Path

VM = Path("app/src/main/java/com/example/ui/StudioViewModel.kt")
text = VM.read_text(encoding="utf-8")

old_import = "import com.example.engine.export.VideoExporter\n"
new_import = old_import + "import com.example.engine.export.ProfessionalExportEngine\n"
if "ProfessionalExportEngine" not in text:
    text = text.replace(old_import, new_import, 1)

old_property = "  val videoExporter = VideoExporter(application)\n"
new_property = old_property + "  val professionalExportEngine = ProfessionalExportEngine(application)\n"
if "val professionalExportEngine" not in text:
    text = text.replace(old_property, new_property, 1)

old_method = '''  fun startExport(config: ExportConfig) {
    viewModelScope.launch {
      val tempFile = videoExporter.exportProject(
        projectName = _activeProjectName.value,
        timeline = timelineEngine.timeline.value,
        config = config
      )
      if (tempFile != null) {
        val saveResult = com.example.engine.media.GalleryMediaSaver.saveVideoToGallery(
          context = getApplication(),
          sourceFile = tempFile,
          title = _activeProjectName.value
        )

        val finalFile = saveResult.file

        repository.recordExport(
          projectId = _activeProjectId.value,
          title = "${_activeProjectName.value}.mp4",
          filePath = finalFile.absolutePath,
          durationMs = timelineEngine.timeline.value.totalDurationMs,
          resolution = config.resolution.label,
          fps = config.frameRate.fps,
          fileSizeBytes = finalFile.length()
        )

        videoExporter.updateSuccessFile(finalFile)
      }
    }
  }
'''

new_method = '''  fun startExport(config: ExportConfig) {
    viewModelScope.launch(Dispatchers.IO) {
      val outputFile = File(
        getApplication<Application>().cacheDir,
        "ah_studio_${System.currentTimeMillis()}.mp4"
      )
      val result = professionalExportEngine.export(
        projectName = _activeProjectName.value,
        timeline = timelineEngine.timeline.value,
        config = config,
        outputFile = outputFile,
        requireAudio = timelineEngine.timeline.value.audioClips.isNotEmpty() ||
          timelineEngine.timeline.value.videoClips.any { it.hasAudio }
      )
      result.getOrNull()?.let { verifiedFile ->
        val saveResult = com.example.engine.media.GalleryMediaSaver.saveVideoToGallery(
          context = getApplication(),
          sourceFile = verifiedFile,
          title = _activeProjectName.value
        )
        val finalFile = saveResult.file
        repository.recordExport(
          projectId = _activeProjectId.value,
          title = "${_activeProjectName.value}.mp4",
          filePath = finalFile.absolutePath,
          durationMs = timelineEngine.timeline.value.totalDurationMs,
          resolution = config.resolution.label,
          fps = config.frameRate.fps,
          fileSizeBytes = finalFile.length()
        )
        videoExporter.updateSuccessFile(finalFile)
        verifiedFile.delete()
      }
    }
  }
'''

if old_method in text:
    text = text.replace(old_method, new_method, 1)

VM.write_text(text, encoding="utf-8")
print("Phase 6 export integration applied")
