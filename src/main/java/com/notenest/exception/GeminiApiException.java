package com.notenest.exception;

/**
 * Thrown for catastrophic Gemini API failures that should surface as a
 * dedicated error page (e.g. missing API key, persistent upstream outage).
 * Normal transient failures should keep returning a fallback string from
 * GeminiService.call() so AJAX endpoints stay usable.
 */
public class GeminiApiException extends RuntimeException {

    public GeminiApiException(String message) {
        super(message);
    }

    public GeminiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
