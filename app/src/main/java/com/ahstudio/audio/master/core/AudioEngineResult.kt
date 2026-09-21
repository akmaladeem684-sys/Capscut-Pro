package com.ahstudio.audio.master.core

enum class AudioEngineError {
    DECODE_FAILED,
    INVALID_CLIP,
    SOURCE_NOT_FOUND,
    OVERLAP_REJECTED,
    RECORD_FAILED,
    PERMISSION_DENIED,
    EXPORT_FAILED,
    INVALID_PROJECT
}

sealed class AudioEngineResult<out T> {
    data class Success<out T>(val data: T) : AudioEngineResult<T>()
    data class Failure(val error: AudioEngineError, val message: String, val cause: Throwable? = null) : AudioEngineResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw cause ?: IllegalStateException("AudioEngineError $error: $message")
    }
}
