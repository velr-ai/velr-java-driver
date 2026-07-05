package ai.velr;

/** Public Velr property value kind. */
public enum VelrValueType {
    /** Velr null value. */
    NULL,
    /** Boolean value. */
    BOOL,
    /** 64-bit integer value. */
    INT64,
    /** Double-precision floating-point value. */
    DOUBLE,
    /** UTF-8 string value. */
    STRING,
    /** Calendar date value. */
    DATE,
    /** Local time value without a time zone. */
    LOCAL_TIME,
    /** Time value with zone information. */
    ZONED_TIME,
    /** Local date-time value without a time zone. */
    LOCAL_DATETIME,
    /** Date-time value with zone information. */
    ZONED_DATETIME,
    /** Duration value. */
    DURATION,
    /** Point value. */
    POINT,
    /** Geometry value. */
    GEOMETRY,
    /** Geography value. */
    GEOGRAPHY,
    /** List value. */
    LIST,
    /** Vector value. */
    VECTOR,
    /** Binary value. */
    BYTES;

    static VelrValueType fromNative(int value) {
        VelrValueType[] values = values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("unknown Velr value type: " + value);
        }
        return values[value];
    }

    VectorValueType toVectorValueType() {
        return VectorValueType.values()[ordinal()];
    }
}
