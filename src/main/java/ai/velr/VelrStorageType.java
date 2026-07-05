package ai.velr;

/** SQLite-compatible storage kind used for exact Velr property reconstruction. */
public enum VelrStorageType {
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

    static VelrStorageType fromNative(int value) {
        VelrStorageType[] values = values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("unknown Velr storage type: " + value);
        }
        return values[value];
    }

    VectorStorageType toVectorStorageType() {
        return VectorStorageType.values()[ordinal()];
    }
}
