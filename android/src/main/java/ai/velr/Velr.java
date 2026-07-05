package ai.velr;

import ai.velr.internal.Binary;
import ai.velr.internal.Native;
import ai.velr.internal.NativeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous embedded Velr database connection.
 *
 * <p>A {@code Velr} instance owns the native database handle and any child handles opened from it:
 * result streams, result tables, transactions, and explain traces. Close the connection when it is
 * no longer needed, preferably with try-with-resources. Closing a connection closes any still-open
 * child handles first.
 *
 * <p>The driver is synchronous. Queries return streaming handles for multi-table results and table
 * handles for single-table results. Arrow bindings and vector embedder registration are available
 * directly on the connection and, for Arrow input, inside explicit transactions.
 */
public final class Velr implements AutoCloseable, ParentHandle {
    private long handle;
    private final HandleSet children = new HandleSet();

    private Velr(long handle) {
        this.handle = handle;
    }

    /**
     * Open an in-memory Velr database.
     *
     * @return an open Velr connection
     */
    public static Velr open() {
        return open((String) null);
    }

    /**
     * Open a Velr database.
     *
     * <p>Pass {@code null} to open an in-memory database. Passing a path opens or creates the
     * database file at that location.
     *
     * @param path database path, or {@code null} for an in-memory database
     * @return an open Velr connection
     */
    public static Velr open(String path) {
        long handle =
                Native.open(NativeSupport.optionalUtf8(path, "database path"));
        return new Velr(NativeSupport.requireHandle(handle, "velr_open failed"));
    }

    /**
     * Open an existing Velr database in read-only mode.
     *
     * @param path database path
     * @return an open read-only Velr connection
     */
    public static Velr openReadonly(String path) {
        long handle = Native.openReadonly(NativeSupport.utf8(path, "database path"));
        return new Velr(
                NativeSupport.requireHandle(handle, "velr_open_existing_readonly failed"));
    }

    /**
     * Return whether this connection has been closed.
     *
     * @return {@code true} after {@link #close()} has completed
     */
    public boolean isClosed() {
        return handle == 0;
    }

    /**
     * Return the schema version stored in the database file.
     *
     * @return the database schema version
     */
    public int schemaVersion() {
        int version = Native.schemaVersion(ptr());
        if (version == Integer.MIN_VALUE) {
            throw VelrException.fromNative("velr_schema_version failed");
        }
        return version;
    }

    /**
     * Return the schema version supported by the bundled Velr runtime.
     *
     * @return the current Velr schema version
     */
    public int currentSchemaVersion() {
        int version = Native.currentSchemaVersion(ptr());
        if (version == Integer.MIN_VALUE) {
            throw VelrException.fromNative("velr_current_schema_version failed");
        }
        return version;
    }

    /**
     * Return whether the database should be migrated before normal use.
     *
     * @return {@code true} when {@link #migrate()} has work to perform
     */
    public boolean needsMigration() {
        int value = Native.needsMigration(ptr());
        if (value < 0) {
            throw VelrException.fromNative("velr_needs_migration failed");
        }
        return value != 0;
    }

    /**
     * Migrate the database schema to the version supported by the bundled Velr runtime.
     *
     * @return a report describing the migration status and applied steps
     */
    public MigrationReport migrate() {
        byte[] bytes = Native.migrate(ptr());
        return Binary.decodeMigrationReport(
                NativeSupport.requireBytes(bytes, "velr_migrate failed"));
    }

    /**
     * Execute an openCypher statement and stream every result table it produces.
     *
     * <p>Use this method for statements that may produce multiple tables. Close the returned stream
     * when finished, or use try-with-resources.
     *
     * @param cypher openCypher statement
     * @return a stream of result tables
     */
    public Stream exec(String cypher) {
        return exec(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options and stream every result table it produces.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     * @return a stream of result tables
     */
    public Stream exec(String cypher, QueryOptions options) {
        long stream =
                Native.exec(
                        ptr(),
                        NativeSupport.utf8(cypher, "openCypher"),
                        NativeSupport.maxRows(options),
                        NativeSupport.paramsJson(options));
        Stream out = new Stream(NativeSupport.requireHandle(stream, "velr_exec_start failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Execute an openCypher statement that is expected to produce exactly one result table.
     *
     * <p>Use {@link #exec(String)} when a statement may produce multiple tables. Close the returned
     * table when finished, or use try-with-resources.
     *
     * @param cypher openCypher statement
     * @return the single result table
     */
    public Table execOne(String cypher) {
        return execOne(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options that is expected to produce exactly one
     * result table.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     * @return the single result table
     */
    public Table execOne(String cypher, QueryOptions options) {
        long table =
                Native.execOne(
                        ptr(),
                        NativeSupport.utf8(cypher, "openCypher"),
                        NativeSupport.maxRows(options),
                        NativeSupport.paramsJson(options));
        Table out = new Table(NativeSupport.requireHandle(table, "velr_exec_one failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Execute an openCypher statement and discard any result tables.
     *
     * @param cypher openCypher statement
     */
    public void run(String cypher) {
        run(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options and discard any result tables.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     */
    public void run(String cypher, QueryOptions options) {
        try (Stream stream = exec(cypher, options)) {
            Table table;
            while ((table = stream.nextTable()) != null) {
                table.close();
            }
        }
    }

    /**
     * Execute an openCypher statement and collect the single result table as object maps.
     *
     * <p>Column names become map keys. Values are the Java-native rendering returned by {@link
     * Cell#asObject()}.
     *
     * @param cypher openCypher statement
     * @return collected rows
     */
    public List<Map<String, Object>> query(String cypher) {
        return query(cypher, QueryOptions.defaults());
    }

    /**
     * Execute an openCypher statement with query options and collect the single result table as
     * object maps.
     *
     * @param cypher openCypher statement
     * @param options query options such as parameters and row limits
     * @return collected rows
     */
    public List<Map<String, Object>> query(String cypher, QueryOptions options) {
        try (Table table = execOne(cypher, options)) {
            return table.toMaps();
        }
    }

    /**
     * Begin an explicit transaction.
     *
     * <p>Commit or roll back the returned transaction. Closing a transaction without committing or
     * rolling back closes the native transaction handle without committing.
     *
     * @return an open transaction
     */
    public VelrTx beginTx() {
        long tx = Native.beginTx(ptr());
        VelrTx out = new VelrTx(NativeSupport.requireHandle(tx, "velr_tx_begin failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Run a callback inside a transaction.
     *
     * <p>The transaction is committed when the callback returns normally and the transaction is
     * still open. If the callback throws, or if commit is not reached, the transaction is rolled
     * back when it is still open.
     *
     * @param callback transaction callback
     * @param <T> callback result type
     * @return the callback result
     * @throws Exception if the callback or commit/rollback logic throws
     */
    public <T> T transaction(TransactionCallback<T> callback) throws Exception {
        VelrTx tx = beginTx();
        boolean committed = false;
        try {
            T result = callback.run(tx);
            if (!tx.isClosed()) {
                tx.commit();
            }
            committed = true;
            return result;
        } finally {
            if (!committed && !tx.isClosed()) {
                tx.rollback();
            }
        }
    }

    /**
     * Build an execution plan for an openCypher statement.
     *
     * @param cypher openCypher statement
     * @return an explain trace
     */
    public ExplainTrace explain(String cypher) {
        long trace = Native.explain(ptr(), NativeSupport.utf8(cypher, "openCypher"));
        ExplainTrace out =
                new ExplainTrace(NativeSupport.requireHandle(trace, "velr_explain failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Execute and profile an openCypher statement, returning the resulting explain trace.
     *
     * @param cypher openCypher statement
     * @return an explain-analyze trace
     */
    public ExplainTrace explainAnalyze(String cypher) {
        long trace = Native.explainAnalyze(ptr(), NativeSupport.utf8(cypher, "openCypher"));
        ExplainTrace out =
                new ExplainTrace(
                        NativeSupport.requireHandle(trace, "velr_explain_analyze failed"), this);
        registerChild(out);
        return out;
    }

    /**
     * Bind Arrow IPC file / Feather v2 bytes as a logical table.
     *
     * <p>The byte array is borrowed only for the duration of the call. Velr decodes the IPC file
     * and owns the resulting Arrow arrays before this method returns. Arrow IPC stream bytes are not
     * accepted by this method.
     *
     * @param logical logical table name to expose to queries
     * @param ipcBytes Arrow IPC file / Feather v2 bytes
     */
    public void bindArrowIpc(String logical, byte[] ipcBytes) {
        if (ipcBytes == null || ipcBytes.length == 0) {
            throw new IllegalArgumentException("Arrow IPC bytes cannot be empty");
        }
        NativeSupport.checkStatus(
                Native.bindArrowIpc(ptr(), NativeSupport.utf8(logical, "logical table name"), ipcBytes),
                "velr_bind_arrow_ipc failed");
    }

    /**
     * Bind single-chunk Arrow C Data Interface columns as a logical table.
     *
     * <p>For each column, the {@code ArrowArray} address is transferred to Velr by this call. Do
     * not use or release transferred arrays after calling this method. The {@code ArrowSchema}
     * address is not consumed and may be released after this method returns.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrow(String logical, List<ArrowColumn> columns) {
        NativeSupport.ArrowBindings bindings = NativeSupport.arrowBindings(columns);
        NativeSupport.checkStatus(
                Native.bindArrow(
                        ptr(),
                        NativeSupport.utf8(logical, "logical table name"),
                        bindings.names,
                        bindings.schemas,
                        bindings.arrays),
                "velr_bind_arrow failed");
    }

    /**
     * Bind single-chunk Arrow C Data Interface columns as a logical table.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrow(String logical, ArrowColumn... columns) {
        bindArrow(logical, Arrays.asList(columns));
    }

    /**
     * Bind chunked Arrow C Data Interface columns as a logical table.
     *
     * <p>Each column may contain one or more chunks. For every chunk, the {@code ArrowArray}
     * address is transferred to Velr by this call. Do not use or release transferred arrays after
     * calling this method. The {@code ArrowSchema} address is not consumed and may be released after
     * this method returns.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrowChunks(String logical, List<ArrowColumn> columns) {
        NativeSupport.ArrowChunkBindings bindings = NativeSupport.arrowChunkBindings(columns);
        NativeSupport.checkStatus(
                Native.bindArrowChunks(
                        ptr(),
                        NativeSupport.utf8(logical, "logical table name"),
                        bindings.names,
                        bindings.schemas,
                        bindings.arrays),
                "velr_bind_arrow_chunks failed");
    }

    /**
     * Bind chunked Arrow C Data Interface columns as a logical table.
     *
     * @param logical logical table name to expose to queries
     * @param columns one or more Arrow columns
     */
    public void bindArrowChunks(String logical, ArrowColumn... columns) {
        bindArrowChunks(logical, Arrays.asList(columns));
    }

    /**
     * Register a named vector embedding callback.
     *
     * <p>Reference the same name from {@code CREATE VECTOR INDEX ... OPTIONS { indexConfig:
     * { embedder: 'name' } }}. Velr calls the embedder when indexed source values change and when
     * vector queries need query text to be embedded.
     *
     * @param name embedder name used by vector index options
     * @param embedder callback that returns one vector per input
     */
    public void registerVectorEmbedder(String name, VectorEmbedder embedder) {
        if (embedder == null) {
            throw new NullPointerException("vector embedder cannot be null");
        }
        NativeSupport.checkStatus(
                Native.registerVectorEmbedder(
                        ptr(), NativeSupport.utf8(name, "vector embedder name"), embedder),
                "velr_register_vector_embedder failed");
    }

    /**
     * Close this connection and any still-open child handles.
     */
    @Override
    public void close() {
        long ptr = handle;
        if (ptr == 0) {
            return;
        }
        children.closeAll();
        handle = 0;
        Native.close(ptr);
    }

    /** Driver-internal child-handle registration hook. */
    @Override
    public void registerChild(ChildHandle child) {
        children.register(child);
    }

    /** Driver-internal child-handle registration hook. */
    @Override
    public void unregisterChild(ChildHandle child) {
        children.unregister(child);
    }

    long ptr() {
        if (handle == 0) {
            throw new VelrException("Velr connection is closed");
        }
        return handle;
    }

    static List<Map<String, Object>> rowsToMaps(Table table) {
        List<String> names = table.columnNames();
        List<Map<String, Object>> out = new ArrayList<>();
        try (Rows rows = table.rows()) {
            List<Cell> row;
            while ((row = rows.next()) != null) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < row.size(); i++) {
                    String name = i < names.size() ? names.get(i) : Integer.toString(i);
                    map.put(name, row.get(i).asObject());
                }
                out.add(map);
            }
        }
        return out;
    }
}
