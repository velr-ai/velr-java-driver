package ai.velr;

import java.util.Arrays;

/**
 * One typed Velr property value.
 *
 * <p>{@code VelrValue} preserves both the public Velr value kind and the underlying storage kind
 * used for exact reconstruction. Vector embedding callbacks receive values through {@link
 * VectorEmbeddingField#velrValue()} so embedders can choose between Java scalar accessors, canonical
 * JSON, display text, and raw storage bytes.
 */
public final class VelrValue {
    private static final byte[] EMPTY = new byte[0];

    private final VelrValueType type;
    private final VelrStorageType storageType;
    private final Object value;
    private final String valueJson;
    private final String display;
    private final byte[] storageBytes;

    private VelrValue(
            VelrValueType type,
            VelrStorageType storageType,
            Object value,
            String valueJson,
            String display,
            byte[] storageBytes) {
        if (type == null) {
            throw new NullPointerException("Velr value type cannot be null");
        }
        if (storageType == null) {
            throw new NullPointerException("Velr storage type cannot be null");
        }
        this.type = type;
        this.storageType = storageType;
        this.value = copyObject(value);
        this.valueJson = valueJson == null ? "null" : valueJson;
        this.display = display == null ? "" : display;
        this.storageBytes = storageBytes == null ? EMPTY : Arrays.copyOf(storageBytes, storageBytes.length);
    }

    static VelrValue fromNative(
            int valueType,
            int storageType,
            Object value,
            String valueJson,
            String display,
            byte[] storageBytes) {
        return new VelrValue(
                VelrValueType.fromNative(valueType),
                VelrStorageType.fromNative(storageType),
                value,
                valueJson,
                display,
                storageBytes);
    }

    /**
     * Create the Velr {@code null} value.
     *
     * @return a null Velr value
     */
    public static VelrValue nullValue() {
        return new VelrValue(VelrValueType.NULL, VelrStorageType.NULL, null, "null", "null", EMPTY);
    }

    /**
     * Create a Velr boolean value.
     *
     * @param value boolean value
     * @return a Velr boolean value
     */
    public static VelrValue bool(boolean value) {
        return new VelrValue(
                VelrValueType.BOOL,
                VelrStorageType.INT64,
                Boolean.valueOf(value),
                value ? "true" : "false",
                value ? "true" : "false",
                EMPTY);
    }

    /**
     * Create a Velr 64-bit integer value.
     *
     * @param value integer value
     * @return a Velr integer value
     */
    public static VelrValue int64(long value) {
        String text = Long.toString(value);
        return new VelrValue(
                VelrValueType.INT64, VelrStorageType.INT64, Long.valueOf(value), text, text, EMPTY);
    }

    /**
     * Create a Velr double value.
     *
     * @param value finite double value
     * @return a Velr double value
     */
    public static VelrValue doubleValue(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Velr double value must be finite");
        }
        String text = Double.toString(value);
        return new VelrValue(
                VelrValueType.DOUBLE,
                VelrStorageType.DOUBLE,
                Double.valueOf(value),
                text,
                text,
                EMPTY);
    }

    /**
     * Create a Velr string value.
     *
     * @param value string value
     * @return a Velr string value
     */
    public static VelrValue string(String value) {
        if (value == null) {
            throw new NullPointerException("Velr string value cannot be null");
        }
        return new VelrValue(
                VelrValueType.STRING,
                VelrStorageType.TEXT,
                value,
                quoteJsonString(value),
                value,
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Create a Velr bytes value.
     *
     * <p>{@code valueJson} is the canonical JSON rendering and {@code display} is the human-readable
     * rendering used by {@link #display()} and {@link #toString()}.
     *
     * @param value raw bytes
     * @param valueJson canonical JSON rendering
     * @param display display rendering
     * @return a Velr bytes value
     */
    public static VelrValue bytes(byte[] value, String valueJson, String display) {
        if (value == null) {
            throw new NullPointerException("Velr bytes value cannot be null");
        }
        return new VelrValue(
                VelrValueType.BYTES,
                VelrStorageType.BLOB,
                Arrays.copyOf(value, value.length),
                valueJson,
                display,
                value);
    }

    /**
     * Create a non-scalar Velr value from canonical renderings.
     *
     * <p>Use the dedicated scalar factories for null, boolean, integer, double, string, and bytes
     * values. This factory is intended for temporal, spatial, list, and vector values when the
     * canonical JSON/display/storage representation is already available.
     *
     * @param type Velr value kind
     * @param storageType underlying storage kind
     * @param valueJson canonical JSON rendering
     * @param display display rendering
     * @param storageBytes raw TEXT or BLOB storage payload when available
     * @return a Velr value
     */
    public static VelrValue canonical(
            VelrValueType type,
            VelrStorageType storageType,
            String valueJson,
            String display,
            byte[] storageBytes) {
        if (isScalar(type) || type == VelrValueType.STRING || type == VelrValueType.BYTES) {
            throw new IllegalArgumentException("use scalar factories for " + type);
        }
        return new VelrValue(type, storageType, valueJson, valueJson, display, storageBytes);
    }

    /**
     * Return the public Velr value kind.
     *
     * @return value kind
     */
    public VelrValueType type() {
        return type;
    }

    /**
     * Return the underlying storage kind used for exact reconstruction.
     *
     * @return storage kind
     */
    public VelrStorageType storageType() {
        return storageType;
    }

    /**
     * Return the Java-native value for simple kinds.
     *
     * <p>Scalars return {@code null}, {@code Boolean}, {@code Long}, {@code Double}, {@code String},
     * or {@code byte[]}. Temporal, spatial, list, and vector values return their canonical JSON text.
     *
     * @return Java-native value or canonical JSON text
     */
    public Object asObject() {
        return copyObject(value);
    }

    /**
     * Return whether this value is the Velr {@code null} value.
     *
     * @return {@code true} for {@link VelrValueType#NULL}
     */
    public boolean isNull() {
        return type == VelrValueType.NULL;
    }

    /**
     * Return whether this value is one of Velr's temporal types.
     *
     * @return {@code true} for date, time, datetime, and duration values
     */
    public boolean isTemporal() {
        switch (type) {
            case DATE:
            case LOCAL_TIME:
            case ZONED_TIME:
            case LOCAL_DATETIME:
            case ZONED_DATETIME:
            case DURATION:
                return true;
            default:
                return false;
        }
    }

    /**
     * Return whether this value is one of Velr's spatial types.
     *
     * @return {@code true} for point, geometry, and geography values
     */
    public boolean isSpatial() {
        return type == VelrValueType.POINT
                || type == VelrValueType.GEOMETRY
                || type == VelrValueType.GEOGRAPHY;
    }

    /**
     * Return this value as a boolean.
     *
     * @return boolean value
     */
    public boolean asBoolean() {
        require(VelrValueType.BOOL);
        return ((Boolean) value).booleanValue();
    }

    /**
     * Return this value as a 64-bit integer.
     *
     * @return integer value
     */
    public long asLong() {
        require(VelrValueType.INT64);
        return ((Long) value).longValue();
    }

    /**
     * Return this value as a double.
     *
     * @return double value
     */
    public double asDouble() {
        require(VelrValueType.DOUBLE);
        return ((Double) value).doubleValue();
    }

    /**
     * Return this value as a string.
     *
     * @return string value
     */
    public String asString() {
        require(VelrValueType.STRING);
        return (String) value;
    }

    /**
     * Return this value as bytes.
     *
     * @return a defensive copy of the byte value
     */
    public byte[] asBytes() {
        require(VelrValueType.BYTES);
        return Arrays.copyOf((byte[]) value, ((byte[]) value).length);
    }

    /**
     * Return the canonical JSON rendering of the Velr value.
     *
     * @return canonical JSON text
     */
    public String valueJson() {
        return valueJson;
    }

    /**
     * Return the display rendering suitable for text embedders and logs.
     *
     * @return display text
     */
    public String display() {
        return display;
    }

    /**
     * Return the raw TEXT or BLOB storage payload when the native storage representation has one.
     *
     * @return storage bytes, or an empty array when no byte-backed payload is available
     */
    public byte[] storageBytes() {
        return Arrays.copyOf(storageBytes, storageBytes.length);
    }

    /**
     * Return this date value as canonical text.
     *
     * @return date text
     */
    public String asDateText() {
        require(VelrValueType.DATE);
        return display;
    }

    /**
     * Return this local-time value as canonical text.
     *
     * @return local-time text
     */
    public String asLocalTimeText() {
        require(VelrValueType.LOCAL_TIME);
        return display;
    }

    /**
     * Return this zoned-time value as canonical text.
     *
     * @return zoned-time text
     */
    public String asZonedTimeText() {
        require(VelrValueType.ZONED_TIME);
        return display;
    }

    /**
     * Return this local-datetime value as canonical text.
     *
     * @return local-datetime text
     */
    public String asLocalDateTimeText() {
        require(VelrValueType.LOCAL_DATETIME);
        return display;
    }

    /**
     * Return this zoned-datetime value as canonical text.
     *
     * @return zoned-datetime text
     */
    public String asZonedDateTimeText() {
        require(VelrValueType.ZONED_DATETIME);
        return display;
    }

    /**
     * Return this duration value as canonical text.
     *
     * @return duration text
     */
    public String asDurationText() {
        require(VelrValueType.DURATION);
        return display;
    }

    /**
     * Return this point value as canonical GeoJSON.
     *
     * @return point GeoJSON
     */
    public String asPointGeoJson() {
        require(VelrValueType.POINT);
        return valueJson;
    }

    /**
     * Return this geometry value as canonical GeoJSON.
     *
     * @return geometry GeoJSON
     */
    public String asGeometryGeoJson() {
        require(VelrValueType.GEOMETRY);
        return valueJson;
    }

    /**
     * Return this geography value as canonical GeoJSON.
     *
     * @return geography GeoJSON
     */
    public String asGeographyGeoJson() {
        require(VelrValueType.GEOGRAPHY);
        return valueJson;
    }

    /**
     * Return this list value as canonical JSON.
     *
     * @return list JSON
     */
    public String asListJson() {
        require(VelrValueType.LIST);
        return valueJson;
    }

    /**
     * Return this vector value as canonical JSON.
     *
     * @return vector JSON
     */
    public String asVectorJson() {
        require(VelrValueType.VECTOR);
        return valueJson;
    }

    private void require(VelrValueType expected) {
        if (type != expected) {
            throw new VelrException("cannot convert " + type + " Velr value to " + expected);
        }
    }

    private static boolean isScalar(VelrValueType type) {
        return type == VelrValueType.NULL
                || type == VelrValueType.BOOL
                || type == VelrValueType.INT64
                || type == VelrValueType.DOUBLE;
    }

    private static Object copyObject(Object value) {
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            return Arrays.copyOf(bytes, bytes.length);
        }
        return value;
    }

    private static String quoteJsonString(String value) {
        StringBuilder out = new StringBuilder();
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }

    /**
     * Return the display rendering of this value.
     *
     * @return display rendering
     */
    @Override
    public String toString() {
        return display;
    }
}
