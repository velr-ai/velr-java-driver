package ai.velr;

import ai.velr.internal.Native;
import ai.velr.internal.NativeSupport;
import java.nio.charset.StandardCharsets;

/**
 * Explain or explain-analyze trace returned by Velr.
 *
 * <p>An explain trace owns native resources and should be closed when no longer needed.
 */
public final class ExplainTrace implements AutoCloseable, ChildHandle {
    private long handle;
    private final ParentHandle parent;

    ExplainTrace(long handle, ParentHandle parent) {
        this.handle = handle;
        this.parent = parent;
    }

    /**
     * Return the compact textual trace.
     *
     * @return compact explain output
     */
    public String compact() {
        byte[] bytes =
                NativeSupport.requireBytes(
                        Native.explainCompact(ptr()), "velr_explain_trace_compact failed");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Return the compact textual trace.
     *
     * @return compact explain output
     */
    public String toCompactString() {
        return compact();
    }

    /**
     * Close this explain trace.
     */
    @Override
    public void close() {
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        handle = 0;
        Native.explainClose(ptr);
        parent.unregisterChild(this);
    }

    private long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr explain trace is closed");
        }
        return handle;
    }
}
