package com.universal.engine.errors

/** Controlled error taxonomy. Nothing in the engine throws raw exceptions across module borders. */
sealed class EngineError(
    message: String,
    cause: Throwable? = null,
    /** Recovery hint executed by the owning engine component. */
    val recovery: Recovery = Recovery.NONE
) : Exception(message, cause) {

    enum class Recovery { NONE, RETRY, REINITIALIZE_CONTEXT, FLUSH_DECODER, RECONFIGURE_DECODER, SKIP_EFFECT, DOWNGRADE_FEATURE, ABORT }

    class RenderError(message: String, cause: Throwable? = null, recovery: Recovery = Recovery.RETRY)
        : EngineError(message, cause, recovery)

    class DecodeError(message: String, codecName: String?, cause: Throwable? = null, recovery: Recovery = Recovery.FLUSH_DECODER)
        : EngineError("decode[$codecName]: $message", cause, recovery)

    class EncodeError(message: String, codecName: String?, cause: Throwable? = null, recovery: Recovery = Recovery.ABORT)
        : EngineError("encode[$codecName]: $message", cause, recovery)

    class EglError(message: String, eglError: Int, cause: Throwable? = null)
        : EngineError("egl: $message (0x${eglError.toUInt().toString(16)})", cause, Recovery.REINITIALIZE_CONTEXT)

    class ShaderError(message: String, source: String?, log: String?) :
        EngineError("shader: $message\nlog=$log", null, Recovery.SKIP_EFFECT) {
        val shaderSource: String? = source
    }

    class ResourceError(message: String, cause: Throwable? = null) : EngineError(message, cause, Recovery.RETRY)

    class TimestampError(message: String) : EngineError("timestamp: $message")

    class AudioError(message: String, cause: Throwable? = null, recovery: Recovery = Recovery.ABORT)
        : EngineError("audio: $message", cause, recovery)

    class UnsupportedFeatureError(feature: String, detail: String = "") :
        EngineError("unsupported: $feature $detail".trim(), null, Recovery.DOWNGRADE_FEATURE)

    class CancelledError : EngineError("operation cancelled")
}

/** Consumers of async engine callbacks receive this instead of exceptions on worker threads. */
data class EngineResult<out T>(
    val value: T? = null,
    val error: EngineError? = null
) {
    val isSuccess: Boolean get() = error == null
    companion object {
        fun <T> ok(v: T) = EngineResult(v)
        fun <T> err(e: EngineError) = EngineResult<T>(error = e)
    }
}
