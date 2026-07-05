/**
 * Java and Kotlin bindings for the embedded Velr property-graph database.
 *
 * <p>The main entry point is {@link ai.velr.Velr}. Connections are synchronous and own child
 * handles such as transactions, streams, tables, rows, savepoints, and explain traces. Close
 * handles when they are no longer needed, preferably with try-with-resources in Java or
 * {@code use} in Kotlin.
 *
 * <p>Query result values are exposed as {@link ai.velr.Cell} objects. Vector embedding callbacks
 * receive typed Velr property values through {@link ai.velr.VectorEmbeddingInput} and
 * {@link ai.velr.VectorEmbeddingField}. Arrow C Data Interface and Arrow IPC binding are available
 * through {@link ai.velr.ArrowColumn}, {@link ai.velr.Velr#bindArrow(String, java.util.List)}, and
 * {@link ai.velr.Velr#bindArrowIpc(String, byte[])}.
 */
package ai.velr;
