package ai.velr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One Arrow C Data Interface column to bind under a logical table name.
 *
 * <p>An {@code ArrowColumn} describes one named column and one or more Arrow C Data Interface
 * chunks. It does not own the native Arrow structs; ownership follows the rules documented on
 * {@link #cData(String, long, long)} and {@link #chunks(String, List)}.
 */
public final class ArrowColumn {
    private final String name;
    private final List<Chunk> chunks;

    private ArrowColumn(String name, List<Chunk> chunks) {
        if (name == null) {
            throw new NullPointerException("Arrow column name cannot be null");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Arrow column name cannot be empty");
        }
        if (chunks == null) {
            throw new NullPointerException("Arrow chunks cannot be null");
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Arrow chunks cannot be empty");
        }
        ArrayList<Chunk> copy = new ArrayList<Chunk>(chunks.size());
        for (Chunk chunk : chunks) {
            if (chunk == null) {
                throw new NullPointerException("Arrow chunk cannot be null");
            }
            copy.add(chunk);
        }
        this.name = name;
        this.chunks = Collections.unmodifiableList(copy);
    }

    /**
     * Create a column from Arrow C Data Interface struct addresses.
     *
     * <p>The schema address points to an {@code ArrowSchema} struct borrowed for the duration of
     * {@code bindArrow}. The array address points to an {@code ArrowArray} struct whose payload is
     * transferred to Velr by {@code bindArrow}. After a successful or failed bind call, callers
     * should close wrapper objects that own the struct memory, but must not call the ArrowArray
     * release callback.
     *
     * @param name column name exposed to Cypher as a field on rows from {@code BIND(...)}
     * @param schemaAddress memory address of an ArrowSchema struct
     * @param arrayAddress memory address of an ArrowArray struct
     * @return a single-chunk Arrow column descriptor
     */
    public static ArrowColumn cData(String name, long schemaAddress, long arrayAddress) {
        return new ArrowColumn(name, Collections.singletonList(chunk(schemaAddress, arrayAddress)));
    }

    /**
     * Create one Arrow C Data Interface chunk from struct addresses.
     *
     * @param schemaAddress memory address of an ArrowSchema struct
     * @param arrayAddress memory address of an ArrowArray struct
     * @return a chunk descriptor for use with {@link #chunks(String, Chunk...)}
     */
    public static Chunk chunk(long schemaAddress, long arrayAddress) {
        return new Chunk(schemaAddress, arrayAddress);
    }

    /**
     * Create a chunked column from Arrow C Data Interface struct addresses.
     *
     * <p>Each chunk follows the same ownership rules as {@link #cData(String, long, long)}. Schemas
     * are borrowed for the duration of the bind call. Array payloads are transferred to Velr.
     *
     * @param name column name exposed to Cypher as a field on rows from {@code BIND(...)}
     * @param chunks one or more Arrow C Data Interface chunks
     * @return a chunked Arrow column descriptor
     */
    public static ArrowColumn chunks(String name, Chunk... chunks) {
        if (chunks == null) {
            throw new NullPointerException("Arrow chunks cannot be null");
        }
        return new ArrowColumn(name, Arrays.asList(chunks));
    }

    /**
     * Create a chunked column from Arrow C Data Interface struct addresses.
     *
     * <p>Each chunk follows the same ownership rules as {@link #cData(String, long, long)}. Schemas
     * are borrowed for the duration of the bind call. Array payloads are transferred to Velr.
     *
     * @param name column name exposed to Cypher as a field on rows from {@code BIND(...)}
     * @param chunks one or more Arrow C Data Interface chunks
     * @return a chunked Arrow column descriptor
     */
    public static ArrowColumn chunks(String name, List<Chunk> chunks) {
        return new ArrowColumn(name, chunks);
    }

    /**
     * Return the column name exposed to Cypher.
     *
     * @return column name
     */
    public String name() {
        return name;
    }

    /**
     * Return the number of Arrow chunks in this column.
     *
     * @return chunk count
     */
    public int chunkCount() {
        return chunks.size();
    }

    /**
     * Return the immutable chunk list.
     *
     * @return chunks in bind order
     */
    public List<Chunk> chunks() {
        return chunks;
    }

    /**
     * Return the schema address for the first chunk.
     *
     * @return ArrowSchema address for chunk zero
     */
    public long schemaAddress() {
        return schemaAddress(0);
    }

    /**
     * Return the array address for the first chunk.
     *
     * @return ArrowArray address for chunk zero
     */
    public long arrayAddress() {
        return arrayAddress(0);
    }

    /**
     * Return the schema address for a chunk.
     *
     * @param chunkIndex zero-based chunk index
     * @return ArrowSchema address
     */
    public long schemaAddress(int chunkIndex) {
        return chunks.get(chunkIndex).schemaAddress();
    }

    /**
     * Return the array address for a chunk.
     *
     * @param chunkIndex zero-based chunk index
     * @return ArrowArray address
     */
    public long arrayAddress(int chunkIndex) {
        return chunks.get(chunkIndex).arrayAddress();
    }

    /**
     * One Arrow C Data Interface chunk in a column.
     *
     * <p>The schema address is borrowed for the duration of the bind call. The array payload is
     * transferred to Velr by the bind call.
     */
    public static final class Chunk {
        private final long schemaAddress;
        private final long arrayAddress;

        private Chunk(long schemaAddress, long arrayAddress) {
            if (schemaAddress == 0) {
                throw new IllegalArgumentException("Arrow schema address cannot be zero");
            }
            if (arrayAddress == 0) {
                throw new IllegalArgumentException("Arrow array address cannot be zero");
            }
            this.schemaAddress = schemaAddress;
            this.arrayAddress = arrayAddress;
        }

        /**
         * Return the ArrowSchema struct address.
         *
         * @return ArrowSchema address
         */
        public long schemaAddress() {
            return schemaAddress;
        }

        /**
         * Return the ArrowArray struct address.
         *
         * @return ArrowArray address
         */
        public long arrayAddress() {
            return arrayAddress;
        }
    }
}
