package ai.velr;

/** SQLite-compatible storage kind for exact vector field reconstruction. */
public enum VectorStorageType {
    /** Null storage. */
    NULL,
    /** 64-bit integer storage. */
    INT64,
    /** Double-precision floating-point storage. */
    DOUBLE,
    /** UTF-8 text storage. */
    TEXT,
    /** Binary blob storage. */
    BLOB;

    static VectorStorageType fromNative(int value) {
        VectorStorageType[] values = values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("unknown vector storage type: " + value);
        }
        return values[value];
    }
}
