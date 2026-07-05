package ai.velr;

/** Why Velr is requesting vectors from a registered embedder. */
public enum VectorEmbeddingPurpose {
    /** Embedding source values from a graph entity for index maintenance. */
    INDEX_ENTITY,
    /** Embedding a query payload supplied to db.index.vector.queryNodes. */
    QUERY;

    static VectorEmbeddingPurpose fromNative(int value) {
        switch (value) {
            case 0:
                return INDEX_ENTITY;
            case 1:
                return QUERY;
            default:
                throw new IllegalArgumentException("unknown vector embedding purpose: " + value);
        }
    }
}
