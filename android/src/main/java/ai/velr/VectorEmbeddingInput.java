package ai.velr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One source row or query payload passed to a registered vector embedding callback.
 *
 * <p>For index maintenance, inputs describe graph entities and their indexed source fields. For
 * vector queries, inputs describe the query payload that must be embedded before search.
 */
public final class VectorEmbeddingInput {
    private final String indexName;
    private final int dimensions;
    private final VectorEmbeddingPurpose purpose;
    private final VectorEntityKind entityKind;
    private final Long entityId;
    private final List<VectorEmbeddingField> fields;

    VectorEmbeddingInput(
            String indexName,
            int dimensions,
            int purpose,
            int entityKind,
            boolean hasEntityId,
            long entityId,
            List<VectorEmbeddingField> fields) {
        this.indexName = indexName == null ? "" : indexName;
        this.dimensions = dimensions;
        this.purpose = VectorEmbeddingPurpose.fromNative(purpose);
        this.entityKind = VectorEntityKind.fromNative(entityKind);
        this.entityId = hasEntityId ? Long.valueOf(entityId) : null;
        this.fields =
                Collections.unmodifiableList(
                        new ArrayList<VectorEmbeddingField>(
                                fields == null
                                        ? Collections.<VectorEmbeddingField>emptyList()
                                        : fields));
    }

    /**
     * Return the vector index name requesting the embedding.
     *
     * @return vector index name
     */
    public String indexName() {
        return indexName;
    }

    /**
     * Return the number of dimensions the embedder must produce.
     *
     * @return required vector dimension count
     */
    public int dimensions() {
        return dimensions;
    }

    /**
     * Return why Velr is requesting this embedding.
     *
     * @return embedding purpose
     */
    public VectorEmbeddingPurpose purpose() {
        return purpose;
    }

    /**
     * Return the graph entity kind for index-maintenance inputs.
     *
     * <p>Query inputs do not represent a stored graph entity and return {@code null}.
     *
     * @return entity kind, or {@code null} for query inputs
     */
    public VectorEntityKind entityKind() {
        return entityKind;
    }

    /**
     * Return the graph entity identifier for index-maintenance inputs.
     *
     * <p>Query inputs do not represent a stored graph entity and return {@code null}.
     *
     * @return entity id, or {@code null} for query inputs
     */
    public Long entityId() {
        return entityId;
    }

    /**
     * Fields in the index source order. For query text, Velr passes one unnamed string field.
     *
     * @return immutable input fields
     */
    public List<VectorEmbeddingField> fields() {
        return fields;
    }

    /**
     * Join field display strings with newlines.
     *
     * <p>This helper is convenient for text embedding models when the desired input text is simply
     * the display rendering of each source field.
     *
     * @return joined display text
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (VectorEmbeddingField field : fields) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(field.display());
        }
        return out.toString();
    }
}
