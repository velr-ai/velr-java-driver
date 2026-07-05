package ai.velr;

import java.util.List;

/**
 * Callback used by Velr vector indexes to produce embeddings.
 *
 * <p>Velr calls the registered embedder for index maintenance and vector queries. The callback
 * receives a batch of typed inputs and must return one finite vector per input. Every returned
 * vector must contain exactly {@link VectorEmbeddingInput#dimensions()} elements.
 */
@FunctionalInterface
public interface VectorEmbedder {
    /**
     * Embed a batch of vector inputs.
     *
     * <p>The returned array must contain one finite vector per input. Every vector must contain
     * exactly the dimension count requested by the index.
     *
     * @param inputs source rows or query payloads to embed
     * @return one vector per input
     * @throws Exception when the embedding model or callback fails
     */
    float[][] embed(List<VectorEmbeddingInput> inputs) throws Exception;
}
