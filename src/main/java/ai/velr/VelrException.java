package ai.velr;

import ai.velr.internal.Native;
import java.nio.charset.StandardCharsets;

/** Base unchecked exception raised by the Velr Java/Kotlin driver. */
public class VelrException extends RuntimeException {
    /**
     * Create a Velr exception with a message.
     *
     * @param message error message
     */
    public VelrException(String message) {
        super(message);
    }

    /**
     * Create a Velr exception with a message and cause.
     *
     * @param message error message
     * @param cause underlying cause
     */
    public VelrException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Convert the current native error into a Java exception.
     *
     * @param fallback fallback message used when the native layer has no error text
     * @return exception containing the native error text or fallback
     */
    public static VelrException fromNative(String fallback) {
        byte[] bytes = Native.takeLastError();
        if (bytes == null) {
            return new VelrException(fallback);
        }
        String message = new String(bytes, StandardCharsets.UTF_8);
        return new VelrException(message.isEmpty() ? fallback : message);
    }
}
