package ai.velr;

/**
 * One Velr property value passed to a vector embedding callback.
 *
 * <p>The field exposes the same value through multiple renderings: typed {@link VelrValue}, a
 * Java-native scalar object when available, canonical JSON, display text, and raw storage bytes.
 */
public final class VectorEmbeddingField {
    private final String name;
    private final VectorValueType valueType;
    private final VectorStorageType storageType;
    private final VelrValue velrValue;

    VectorEmbeddingField(
            String name,
            int valueType,
            int storageType,
            Object value,
            String valueJson,
            String display,
            byte[] bytes) {
        this.name = name;
        this.valueType = VectorValueType.fromNative(valueType);
        this.storageType = VectorStorageType.fromNative(storageType);
        this.velrValue =
                VelrValue.fromNative(valueType, storageType, value, valueJson, display, bytes);
    }

    /**
     * Return the property name for indexed entity values.
     *
     * <p>Query payloads may be unnamed.
     *
     * @return property name, or an empty/unnamed value for query payloads
     */
    public String name() {
        return name;
    }

    /**
     * Return the typed Velr value kind.
     *
     * @return value kind
     */
    public VectorValueType valueType() {
        return valueType;
    }

    /**
     * Return the underlying storage kind used for exact reconstruction.
     *
     * @return storage kind
     */
    public VectorStorageType storageType() {
        return storageType;
    }

    /**
     * Return the typed Velr property value.
     *
     * @return Velr value
     */
    public VelrValue velrValue() {
        return velrValue;
    }

    /**
     * Java rendering of the value.
     *
     * <p>Simple values are returned as {@code null}, {@code Boolean}, {@code Long}, {@code Double},
     * {@code String}, or {@code byte[]}. Rich Velr values such as temporal, spatial, list, and vector
     * values are returned as their canonical JSON string; use {@link #valueJson()} and {@link
     * #valueType()} when preserving the exact Velr type matters.
     *
     * @return Java-native value or canonical JSON text
     */
    public Object value() {
        return velrValue.asObject();
    }

    /**
     * Return the canonical JSON rendering of the Velr value.
     *
     * @return canonical JSON text
     */
    public String valueJson() {
        return velrValue.valueJson();
    }

    /**
     * Return the display rendering suitable for text embedders.
     *
     * @return display text
     */
    public String display() {
        return velrValue.display();
    }

    /**
     * Return raw text or blob storage bytes when available.
     *
     * @return storage bytes, or an empty array when the value has no byte-backed storage payload
     */
    public byte[] bytes() {
        return velrValue.storageBytes();
    }
}
